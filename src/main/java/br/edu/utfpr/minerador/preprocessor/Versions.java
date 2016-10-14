package br.edu.utfpr.minerador.preprocessor;

import br.edu.utfpr.minerador.preprocessor.database.ConnectionFactory;
import br.edu.utfpr.minerador.preprocessor.model.Commit;
import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Relaciona todos os commits em outras tabelas com sufixo "_2":
 * issues_scmlog_2
 * issues_fix_version_2
 *
 * @author Rodrigo T. Kuroda
 */
public class Versions {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws SQLException {
        long start = System.currentTimeMillis();
        args = new String[]{"D:\\DB", "solr"};
        if (args.length < 2) {
            LOG.warn("Enter the backupsPath and projectName.\n"
                    + "E.g. preprocessor.jar /backups project");
        }

        ConnectionFactory factory = new ConnectionFactory();

        try (Connection mysqlConnection = factory.getConnection()) {

            String projectName = args[1];

            LOG.info("Linking issue to commit...");
            linkIssueToScmlog(mysqlConnection, projectName);

            LOG.info("Successfuly create version table!");

        } catch (Exception ex) {
            LOG.error("Error.", ex);
            System.exit(1);
        } finally {
            factory.close();
        }
        LOG.info("Finished in " + ((System.currentTimeMillis() - start) / 1000) + " secs.");
        System.exit(0);
    }

    /**
     * Links issue to commit by commit message pattern
     *
     * @param pattern Pattern contained in commit message
     */
    private static void linkIssueToScmlog(Connection conn, String project)
            throws SQLException {

        final long startTime = System.nanoTime();
        final Statement statement = conn.createStatement();
        final ResultSet commitMessages = statement.executeQuery("SELECT id, message FROM " + project + "_vcs.scmlog WHERE num_files <= 20");
        final Set<Commit> commits = new HashSet<>();

//        log.info("Querying commits...");
        while (commitMessages.next()) {
            commits.add(new Commit(commitMessages.getInt("id"), commitMessages.getString("message")));
        }

        int totalPatternOccurrences = 0;
        int totalPatternRelatedWithAnIssue = 0;
        int totalCommitsWithOccurrences = 0;

//        conn.prepareStatement("DROP TABLE " + project + "_issues.issues_scmlog_2").execute();
        conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS " + project + "_issues.issues_scmlog_2 ("
                + "  id int(11) NOT NULL AUTO_INCREMENT,"
                + "  issue_id int(11) NOT NULL,"
                + "  scmlog_id int(11) NOT NULL,"
                + "  PRIMARY KEY (id),"
                + "  UNIQUE KEY unq_issue_scmlog (issue_id,scmlog_id),"
                + "  KEY issue_id (issue_id),"
                + "  KEY scmlog_id (scmlog_id)"
                + ")").execute();

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
                    + " WHERE UPPER(iej.issue_key) = ?";
        }

        final Pattern regex = Pattern.compile(issueReferencePattern, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        final Pattern regexNumber = Pattern.compile("\\d+");

        final Set<Integer> fixedIssuesSet = new HashSet<>();

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
//                        log.info("Not found issue for match pattern " + issueKey);
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
                            + "_issues.issues_scmlog_2 (issue_id, scmlog_id) VALUES (?, ?)")) {
                        final int issueId = executeQuery.getInt(1);
                        queryToRelate.setInt(1, issueId);
                        queryToRelate.setInt(2, commit.getId());

                        // adiciona as versões da issue corrigida
//                        fixedIssuesIdFixVersion.put(issueId, Arrays.asList(executeQuery.getString(2).split(",")));
                        // adiciona a issue corrigida
                        fixedIssuesSet.add(issueId);
                        try {
                            queryToRelate.execute();
                            totalPatternRelatedWithAnIssue++;
                        } catch (MySQLIntegrityConstraintViolationException e) {
//                            log.info("Issue " + issueId + " and commit " + commit.getId() + " already exists.");
                        }
                    }
                }
            }
            if (matcherCount > 0) {
                totalCommitsWithOccurrences++;
            } else {
                LOG.info(commitMessage);
            }
        }

        conn.commit();
        conn.setAutoCommit(true);

        final long endTime = System.nanoTime();
        LOG.info("Total time: " + (endTime - startTime));

    }

    private static Map<Integer, List<String>> retrieveIssues(String project, Connection conn)
            throws SQLException {
        final Map<Integer, List<String>> fixedIssuesIdFixVersion = new HashMap<>();
        String selectIssues
                = "SELECT DISTINCT i.id, iej.fix_version FROM " + project + "_issues.issues i"
                //                + "  JOIN " + project + "_issues.changes c ON c.issue_id = i.id"
                + "  JOIN " + project + "_issues.issues_ext_jira iej ON iej.issue_id = i.id";
        //                + " WHERE UPPER(iej.issue_key) = ?"
        //                + "   AND i.resolution = 'Fixed'"
        //                + "   AND c.field = 'Resolution'"
        //                + "   AND c.new_value = i.resolution";
        PreparedStatement selectRelatedIssue = conn.prepareStatement(selectIssues);
        ResultSet executeQuery = selectRelatedIssue.executeQuery();
        while (executeQuery.next()) {
            final int issueId = executeQuery.getInt(1);
            // adiciona as versões da issue
            fixedIssuesIdFixVersion.put(issueId, Arrays.asList(executeQuery.getString(2).split(",")));
        }
        return fixedIssuesIdFixVersion;
    }

    // 1.1.1 > 1
    public static String getMajorVersion(String version) {
        String majorVersion;
        String[] versionsSplited = version.split("[.]");
        if (versionsSplited.length > 1) {
            majorVersion = versionsSplited[0];
        } else {
            majorVersion = version;
        }
        return majorVersion;
    }

    // 1.1.1 > 1.1
    public static String getMinorVersion(String version) {
        String minorVersion;
        String[] versionsSplited = version.split("[.]");
        if (versionsSplited.length > 2) {
            minorVersion = versionsSplited[0] + "." + versionsSplited[1];
        } else {
            minorVersion = version;
        }
        return minorVersion;
    }

    static String buildPatternByName(String projectName) {
        String upper = projectName.toUpperCase();
        return "(?i)(" + upper + "\\s*[-]+\\s*\\d+(?=\\.(?!\\w)|-(?![a-zA-Z])|:|\\s|,|]|\\)|\\(|;|_))";
    }

    static String replaceUrl(String text) {
        return text.replaceAll("(\\s+git-svn-id:\\shttps://svn.apache.org/).*", "");
    }
}
