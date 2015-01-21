package br.edu.utfpr.minerador.preprocessor;

import br.edu.utfpr.minerador.preprocessor.comparator.VersionComparator;
import br.edu.utfpr.minerador.preprocessor.database.ConnectionFactory;
import br.edu.utfpr.minerador.preprocessor.model.Commit;
import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    private static final long TIMEOUT = 10 * 60 * 1000; // 10 min
    private static final String SQL_DELIMITER = ";";

    public static void main(String[] args) throws SQLException {
        if (args.length < 2) {
            log.warn("Enter the backupsPath and projectName.\n"
                    + "E.g. preprocessor.jar /backups project");
        }

        ConnectionFactory factory = new ConnectionFactory();

        try (Connection mysqlConnection = factory.getConnection()) {

            String backupsPath = args[0];
            String projectName = args[1];

            restoreDatabase(backupsPath, projectName, mysqlConnection);

            log.info("Executing optimization...");
            executeOptimizationScript(mysqlConnection, projectName);

            log.info("Linking issue to commit...");
            linkIssueToScmlog(mysqlConnection, projectName);

            log.info("Successfuly pre-processed!");

        } catch (SQLException | IOException | InterruptedException ex) {
            log.error("Error.", ex);
            System.exit(1);
        } finally {
            factory.close();
        }
        System.exit(0);
    }
    private static void executeOptimizationScript(Connection mysqlConnection, String projectName) throws SQLException {

        String issueDatabaseName = projectName + "_issues";
        mysqlConnection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS " + issueDatabaseName + ".issues_scmlog ("
                + "  id int(11) NOT NULL AUTO_INCREMENT,"
                + "  issue_id int(11) NOT NULL,"
                + "  scmlog_id int(11) NOT NULL,"
                + "  PRIMARY KEY (id),"
                + "  UNIQUE KEY unq_issue_scmlog (issue_id,scmlog_id),"
                + "  KEY issue_id (issue_id),"
                + "  KEY scmlog_id (scmlog_id)"
                + ")").execute();

        mysqlConnection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS " + issueDatabaseName + ".issues_fix_version ("
                + "  issue_id int(11) NOT NULL,"
                + "  fix_version varchar(255) NOT NULL,"
                + "  major_fix_version varchar(255) NOT NULL,"
                + "  UNIQUE KEY unq_issue_fix_version (issue_id,fix_version),"
                + "  KEY issue_id (issue_id),"
                + "  KEY fix_version (fix_version),"
                + "  KEY major_fix_version (major_fix_version)"
                + ")").execute();

        mysqlConnection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS " + issueDatabaseName + ".issues_fix_version_order ("
                + "  major_fix_version varchar(255) NOT NULL,"
                + "  version_order int(11) NOT NULL,"
                + "  UNIQUE KEY unq_major_fix_version_order (major_fix_version,version_order),"
                + "  KEY major_fix_version (major_fix_version),"
                + "  KEY version_order (version_order)"
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

        File vcsBackup = new File(backupsDir, projectName + "_vcs.sql");

        if (!backups.exists()) {
            throw new FileNotFoundException(backupsPath + "/" + projectName + "_vcs.sql not found.");
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
            Thread.sleep(10000);

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

        process.waitFor();
        process.destroy();
    }

    /**
     * Links issue to commit by commit message pattern
     *
     * @param pattern Pattern contained in commit message
     */
    private static void linkIssueToScmlog(Connection conn, String project) throws SQLException {

        final Statement statement = conn.createStatement();
        final ResultSet commitMessages = statement.executeQuery("SELECT id, message FROM " + project + "_vcs.scmlog WHERE num_files <= 20");
        final Set<Commit> commits = new HashSet<>();

        log.info("Querying commits...");
        while (commitMessages.next()) {
            commits.add(new Commit(commitMessages.getInt("id"), commitMessages.getString("message")));
        }

        int totalPatternOccurrences = 0;
        int totalPatternRelatedWithAnIssue = 0;

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
            selectIssueId
                    = "SELECT DISTINCT i.id, iej.fix_version FROM " + project + "_issues.issues i"
                    + "  JOIN " + project + "_issues.changes c ON c.issue_id = i.id"
                    + "  JOIN " + project + "_issues.issues_ext_jira iej ON iej.issue_id = i.id"
                    + " WHERE UPPER(iej.issue_key) = ?"
                    + "   AND i.resolution = 'Fixed'"
                    + "   AND c.field = 'Resolution'"
                    + "   AND c.new_value = i.resolution";
        }

        final int totalIssues;
        try (PreparedStatement countIssuesStatement = conn.prepareStatement("SELECT COUNT(1) FROM " + project + "_issues.issues");
                ResultSet countIssuesResult = countIssuesStatement.executeQuery();) {
            countIssuesResult.next();
            totalIssues = countIssuesResult.getInt(1);
        }

        final int totalCommits;
        try (PreparedStatement countCommitsStatement = conn.prepareStatement("SELECT COUNT(1) FROM " + project + "_vcs.scmlog");
                ResultSet countCommitsResult = countCommitsStatement.executeQuery();) {
            countCommitsResult.next();
            totalCommits = countCommitsResult.getInt(1);
        }

        final Pattern regex = Pattern.compile(issueReferencePattern, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        final Pattern regexNumber = Pattern.compile("\\d+");

        final Map<Integer, List<String>> fixedIssuesIdFixVersion = new HashMap<>();
        final Set<Integer> fixedIssuesSet = new HashSet<>();

        int totalCommitsWithOccurrences = 0;

        for (Commit commit : commits) {
            // remove "git-svn-id: https://svn.apache.org/*" from message
            // to avoid false positive matches of pattern
            // (e.g. git-svn-id: https://svn.apache.org/camel-1.1.0)
            final String commitMessage = replaceUrl(commit.getMessage());
            final Matcher matcher = regex.matcher(commitMessage);

            int matcherCount = 0;

            // para cada ocorrência do padrão
            while (matcher.find()) {

                String issueKey = matcher.group().replace(" ", ""); // e.g.: ARIES-1234

                if (isIssuesFromBugzilla) {
                    Matcher matcherNumber = regexNumber.matcher(issueKey);
                    if (matcherNumber.find()) {
                        issueKey = matcherNumber.group(); // e.g.: 1234
                    } else {
                        log.info("Not found issue for match pattern " + issueKey);
                    }
                }

                totalPatternOccurrences++;
                matcherCount++;
                PreparedStatement selectRelatedIssue = conn.prepareStatement(selectIssueId);
                selectRelatedIssue.setString(1, issueKey.toUpperCase());

                ResultSet executeQuery = selectRelatedIssue.executeQuery();

                if (executeQuery.next()) {
                    try (PreparedStatement queryToRelate = conn.prepareStatement(
                            "INSERT INTO " + project
                            + "_issues.issues_scmlog (issue_id, scmlog_id) VALUES (?, ?)")) {
                        final int issueId = executeQuery.getInt(1);
                        queryToRelate.setInt(1, issueId);
                        queryToRelate.setInt(2, commit.getId());

                        // adiciona as versões da issue corrigida
                        fixedIssuesIdFixVersion.put(issueId, Arrays.asList(executeQuery.getString(2).split(",")));
                        // adiciona a issue corrigida
                        fixedIssuesSet.add(issueId);
                        try {
                            queryToRelate.execute();
                            totalPatternRelatedWithAnIssue++;
                        } catch (MySQLIntegrityConstraintViolationException e) {
                            log.info("Issue " + issueId + " and commit " + commit.getId() + " already exists.");
                        }
                    }
                }
            }
            if (matcherCount > 0) {
                totalCommitsWithOccurrences++;
            } else {
                log.info(commitMessage);
            }
//            log.info(matcherCount + " ocorrências para o commit " + commit.getId());
        }

        PreparedStatement issueFixVersionInsert = conn.prepareStatement(
                "INSERT INTO " + project
                + "_issues.issues_fix_version (issue_id, fix_version, major_fix_version) VALUES (?, ?, ?)");

        int countIssuesWithFixVersion = 0;

        Set<String> distincMajorVersion = new HashSet<>();
        for (Map.Entry<Integer, List<String>> entrySet : fixedIssuesIdFixVersion.entrySet()) {
            Integer issueId = entrySet.getKey();
            List<String> versions = entrySet.getValue();

            if (versions.isEmpty() || versions.get(0).isEmpty()) {
                log.info("Issue " + issueId + " has no fix version.");
            } else {
//                log.info("Issue " + issueId + " is fixed in " + versions.size() + " versions.");

                for (String version : versions) {
                    try {
                        issueFixVersionInsert.setInt(1, issueId);
                        issueFixVersionInsert.setString(2, version);

                        String majorVersion = getMajorVersion(version);
                        issueFixVersionInsert.setString(3, majorVersion);

                        distincMajorVersion.add(majorVersion);

                        issueFixVersionInsert.execute();
                    } catch (MySQLIntegrityConstraintViolationException e) {
                        log.info("Issue " + issueId + " and version " + version + " already exists.");
                    }
                }

                countIssuesWithFixVersion++;
            }
        }

        issueFixVersionInsert.close();

        List<String> majorVersionsList = new ArrayList<>(distincMajorVersion);

        Collections.sort(majorVersionsList, new VersionComparator());

        PreparedStatement issueFixVersionOrderInsert = conn.prepareStatement(
                "INSERT INTO " + project
                + "_issues.issues_fix_version_order (major_fix_version, version_order) VALUES (?, ?)");
        int order = 1;
        for (String majorVersion : majorVersionsList) {
            try {
                issueFixVersionOrderInsert.setString(1, majorVersion);
                issueFixVersionOrderInsert.setInt(2, order++);

                issueFixVersionOrderInsert.execute();
            } catch (MySQLIntegrityConstraintViolationException e) {
                log.info("Issue " + majorVersion + " order " + order + " already exists.");
            }
        }

        conn.commit();
        conn.setAutoCommit(true);

        log.info("\n\n"
                + commits.size() + " of " + totalCommits + " (total) commits has less than or equal to 20 files\n"
                + totalCommitsWithOccurrences + " of " + commits.size() + " commits has at least one occurrence of pattern \"" + issueReferencePattern + "\"\n\n"
                + totalPatternOccurrences + " occurrences of pattern \"" + issueReferencePattern + "\" in commits' message was found\n"
                + totalPatternRelatedWithAnIssue + " of " + totalPatternOccurrences + " occurrences was related with an issue\n\n"
                + fixedIssuesSet.size() + " of " + totalIssues + " (total) issues was fixed\n"
                + countIssuesWithFixVersion + " of " + fixedIssuesSet.size() + " issues has 'fix version'\n\n"
        );
    }

    public static String getMajorVersion(String version) {
        String majorVersion;
        String[] versionsSplited = version.split("[.]");
        if (versionsSplited.length > 2) {
            majorVersion = versionsSplited[0] + "." + versionsSplited[1];
        } else {
            majorVersion = version;
        }
        return majorVersion;
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
        return "(?i)(" + upper + "\\s*[-]+\\s*\\d+(?=\\.(?!\\w)|-(?![a-zA-Z])|:|\\s|,|]|\\)|\\(|;|_))";
    }

    static String replaceUrl(String text) {
        return text.replaceAll("(\\s+git-svn-id:\\shttps://svn.apache.org/).*", "");
    }
}
