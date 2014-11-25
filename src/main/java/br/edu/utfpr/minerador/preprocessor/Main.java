package br.edu.utfpr.minerador.preprocessor;

import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Rodrigo T. Kuroda
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static final String SQL_DELIMITER = ";";

    private static long TIMEOUT = 10 * 60 * 1000; // 10 min

    private static HikariDataSource getDatasource() {
        return getDatasource(null);
    }

    private static HikariDataSource getDatasource(final String databaseName) {
        HikariConfig config = new HikariConfig();
        config.setMaximumPoolSize(100);
        config.setDataSourceClassName("com.mysql.jdbc.jdbc2.optional.MysqlDataSource");
        config.addDataSourceProperty("serverName", "localhost");
        config.addDataSourceProperty("port", "3306");
        if (databaseName != null) {
            config.addDataSourceProperty("databaseName", databaseName);
        }
        config.addDataSourceProperty("user", "root");
        config.addDataSourceProperty("password", "root");
        config.setConnectionTestQuery("SELECT 1");

        return new HikariDataSource(config);
    }

    public static void main(String[] args) throws SQLException {
        if (args.length < 2) {
            log.warn("Enter the backupsPath and projectName.\n"
                    + "E.g. preprocessor.jar /backups project");
        }

        HikariDataSource mysqlDatasource = null;
        Connection mysqlConnection = null;
        try {
            mysqlDatasource = getDatasource();
            mysqlConnection = mysqlDatasource.getConnection();

            String backupsPath = args[0];
            String projectName = args[1];

            restoreDatabase(backupsPath, projectName, mysqlConnection);

            log.info("Executing optimization...");
            executeOptimizationScript(mysqlConnection, projectName);

            log.info("Linking issue to commit...");
            linkIssueToScmlog(mysqlConnection, projectName);

            log.info("Successfuly pre-processed!");

        } catch (Exception ex) {
            log.error("Error.", ex);
            System.exit(1);
        } finally {
            if (mysqlConnection != null) {
                mysqlConnection.close();
            }
            if (mysqlDatasource != null) {
                mysqlDatasource.close();
            }
        }
        System.exit(0);
    }

    private static void executeOptimizationScript(Connection mysqlConnection, String projectName) throws SQLException {

        String issueDatabaseName = projectName + "_issues";
        mysqlConnection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS `" + issueDatabaseName + "`.`issues_scmlog` ("
                + "  `id` int(11) NOT NULL AUTO_INCREMENT,"
                + "  `issue_id` int(11) NOT NULL,"
                + "  `scmlog_id` int(11) NOT NULL,"
                + "  PRIMARY KEY (`id`),"
                + "  UNIQUE KEY `unq_issue_scmlog` (`issue_id`,`scmlog_id`),"
                + "  KEY `issue_id` (`issue_id`),"
                + "  KEY `scmlog_id` (`scmlog_id`)"
                + ")").execute();

        final String preprocessingIssues = "preprocessing.sql";

        InputStream script = Main.class.getClassLoader().getResourceAsStream(preprocessingIssues);
        executeSqlScript(mysqlConnection, script, issueDatabaseName);
    }

    private static void restoreDatabase(final String backupsPath,
            final String projectName, final Connection mysqlConnection)
            throws IOException, InterruptedException, SQLException {

        File backupsDir = new File(backupsPath);
        File backups = new File(backupsDir, projectName + "_issues.sql");

        if (!backups.exists()) {
            throw new FileNotFoundException(backupsPath + "/" + projectName + "_issues.sql not found.");
        }

        File vcsBackup = new File(backupsDir, projectName + "_vcs2.sql");

        if (!backups.exists()) {
            throw new FileNotFoundException(backupsPath + "/" + projectName + "_vcs2.sql not found.");
        }

        String issueDatabaseName = projectName + "_issues";
        String vcsDatabaseName = projectName + "_vcs";

        log.info("Restoring " + backups.getName() + " to database " + issueDatabaseName + "..."
                + " and " + vcsBackup.getName() + " to database " + vcsDatabaseName + "...");

        mysqlConnection.setAutoCommit(false);
        try (Statement statement
                = mysqlConnection.createStatement()) {

            statement.execute("CREATE SCHEMA " + issueDatabaseName);
            statement.execute("CREATE SCHEMA " + vcsDatabaseName);
            mysqlConnection.commit();

            restore(backups, issueDatabaseName);
            restore(vcsBackup, vcsDatabaseName);

            log.info("Restored successfuly!");

        } catch (SQLException e) {
            log.info("Error to create schema.", e);
            mysqlConnection.rollback();
        }
        mysqlConnection.setAutoCommit(true);
    }

    private static void restore(File file, String databaseName) throws IOException, InterruptedException, SQLException {
        String command = "mysql --user=root --password=root " + databaseName + " < " + file.getAbsolutePath();
        Process process = Runtime.getRuntime().exec(new String[]{"cmd", "/c", command});
        log.info(command);

        Worker worker = new Worker(process);
        worker.start();
        try {
            worker.join(TIMEOUT);
            if (worker.exit == null) {
                log.info("Timeout for restore database " + databaseName);
                return;
            }
        } catch (InterruptedException ex) {
            worker.interrupt();
            Thread.currentThread().interrupt();
            throw ex;
        }

        process.destroy();
    }

    /**
     * Links issue to commit by commit message pattern
     *
     * @param pattern Pattern contained in commit message
     */
    private static void linkIssueToScmlog(Connection conn, String project) throws SQLException {

        final Statement statement = conn.createStatement();
        final ResultSet commitMessages = statement.executeQuery("SELECT id, message FROM " + project + "_vcs.scmlog");
        final Set<Commit> commits = new HashSet<>();

        log.info("Querying commits...");
        while (commitMessages.next()) {
            commits.add(new Commit(commitMessages.getInt("id"), commitMessages.getString("message")));
        }

        log.info("Commits: " + commits.size());

        int totalFound = 0;
        int totalRelated = 0;

        conn.setAutoCommit(false);

        ResultSet queryIfIssuesIsFromJira = conn.prepareStatement("SELECT 1 "
                + "FROM information_schema.tables "
                + "WHERE table_schema = '" + project + "_issues' "
                + "    AND table_name = 'issues_ext_bugzilla' "
                + "LIMIT 1").executeQuery();
        boolean isIssuesFromBugzilla = queryIfIssuesIsFromJira.next();

        final String selectIssueId;
        final String issueReferencePattern;
        if (isIssuesFromBugzilla) {
            issueReferencePattern = "(?i)(bug|issue|fixed|fix|bugzilla)+(\\s)*(id|for)?(:|-)?\\s*#?\\s*(\\d+)(,\\s*\\d+)*";
            selectIssueId = "SELECT id FROM " + project + "_issues.issues WHERE issue = ?";
        } else {
            issueReferencePattern = buildPatternByName(project);
            selectIssueId = "SELECT id FROM " + project + "_issues.issues WHERE id = ("
                    + "SELECT p.issue_id FROM " + project + "_issues.issues_ext_jira p WHERE UPPER(p.issue_key) = ?)";
        }

        final Pattern regex = Pattern.compile(issueReferencePattern, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        final Pattern regexNumber = Pattern.compile("\\d+");

        for (Commit commit : commits) {
            final Matcher matcher = regex.matcher(commit.getMessage());

            int mactherCount = 0;

            // para cada ocorrência do padrão
            while (matcher.find()) {
                String issueKey = matcher.group(); // e.g.: ARIES-1234

                if (isIssuesFromBugzilla) {
                    Matcher matcherNumber = regexNumber.matcher(issueKey);
                    if (matcherNumber.find()) {
                        issueKey = matcherNumber.group(); // e.g.: 1234
                    } else {
                        log.info("Not found issue for match pattern " + issueKey);
                    }
                }

                totalFound++;
                mactherCount++;
                PreparedStatement selectRelatedIssue = conn.prepareStatement(selectIssueId);
                selectRelatedIssue.setString(1, issueKey.toUpperCase());

                ResultSet executeQuery = selectRelatedIssue.executeQuery();

                if (executeQuery.next()) {
                    PreparedStatement queryToRelate = conn.prepareStatement(
                            "INSERT INTO " + project
                            + "_issues.issues_scmlog (issue_id, scmlog_id) VALUES (?, ?)");
                    final int issueId = executeQuery.getInt(1);
                    queryToRelate.setInt(1, issueId);
                    queryToRelate.setInt(2, commit.getId());

                    try {
                        queryToRelate.execute();
                        totalRelated++;
                    } catch (MySQLIntegrityConstraintViolationException e) {
                        log.info("Issue " + issueId + " and commit " + commit.getId() + " already exists.");
                    }
                }
            }

            log.info(mactherCount + " ocorrências para o commit " + commit.getId());
        }

        conn.commit();
        conn.setAutoCommit(true);

        log.info(totalFound + " ocorrências de " + issueReferencePattern);
        log.info(totalRelated + " relacionados");
    }

    private static void executeSqlScript(Connection conn, InputStream inputFile, String databaseName) throws SQLException {

        // Create scanner
        Scanner scanner = new Scanner(inputFile).useDelimiter(SQL_DELIMITER);

        // Loop through the SQL file statements
        Statement currentStatement = null;
        while (scanner.hasNext()) {

            // Get statement
            String rawStatement = scanner.next() + SQL_DELIMITER;
            try {
                // Execute statement
                currentStatement = conn.createStatement();
                currentStatement.execute(rawStatement.replace("{0}", databaseName.split("_")[0]));
            } catch (SQLException e) {
                log.warn("Error in script execution.", e);
            } finally {
                // Release resources
                if (currentStatement != null) {
                    currentStatement.close();
                }
                currentStatement = null;
            }
        }
    }

    static String buildPatternByName(String projectName) {
        String upper = projectName.toUpperCase();
        return "(?i)" + upper + "-(\\d+)(,\\s*\\d+)*";
    }

    private static class Worker extends Thread {

        private final Process process;
        private Integer exit;

        private Worker(Process process) {
            this.process = process;
        }

        @Override
        public void run() {
            try {
                exit = process.waitFor();
            } catch (InterruptedException e) {
                log.error("Error to wait for thread." + e);
            }
        }
    }

    private static class Commit {

        private final Integer id;
        private final String message;

        public Commit(Integer id, String message) {
            this.id = id;
            this.message = message;
        }

        public Integer getId() {
            return id;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + Objects.hashCode(this.id);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Commit other = (Commit) obj;
            return Objects.equals(this.id, other.id);
        }

    }
}
