package br.edu.utfpr.minerador.preprocessor;

import br.edu.utfpr.minerador.preprocessor.comparator.VersionComparator;
import br.edu.utfpr.minerador.preprocessor.database.ConnectionFactory;
import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Rodrigo T. Kuroda
 */
public class Versions2 {

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

        conn.prepareStatement("DROP TABLE " + project + "_issues.issues_fix_version_2");
        conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS " + project + "_issues.issues_fix_version_2 ("
                + "  issue_id int(11) NOT NULL,"
                + "  fix_version varchar(255) NOT NULL,"
                + "  minor_fix_version varchar(255) NOT NULL,"
                + "  major_fix_version varchar(255) NOT NULL,"
                + "  UNIQUE KEY unq_issue_fix_version (issue_id,fix_version),"
                + "  KEY issue_id (issue_id),"
                + "  KEY fix_version (fix_version),"
                + "  KEY minor_fix_version (minor_fix_version),"
                + "  KEY major_fix_version (major_fix_version)"
                + ")").execute();

        conn.setAutoCommit(false);

        Map<Integer, List<String>> fixedIssuesIdFixVersion = retrieveIssues(project, conn);

        Set<String> distincMinorVersion;
        try (PreparedStatement issueFixVersionInsert = conn.prepareStatement(
                "INSERT INTO " + project
                + "_issues.issues_fix_version_2 (issue_id, fix_version, minor_fix_version, major_fix_version) VALUES (?, ?, ?, ?)")) {

            distincMinorVersion = new HashSet<>();
            for (Map.Entry<Integer, List<String>> entrySet : fixedIssuesIdFixVersion.entrySet()) {
                Integer issueId = entrySet.getKey();
                List<String> versions = entrySet.getValue();

                if (versions.isEmpty() || versions.get(0).isEmpty()) {
//                log.info("Issue " + issueId + " has no fix version.");
                } else {
//                log.info("Issue " + issueId + " is fixed in " + versions.size() + " versions.");

                    for (String version : versions) {
                        try {
                            issueFixVersionInsert.setInt(1, issueId);
                            issueFixVersionInsert.setString(2, version);

                            String minorVersion = getMinorVersion(version);
                            issueFixVersionInsert.setString(3, minorVersion);

//                        String majorVersion = getMajorVersion(version);
                            issueFixVersionInsert.setString(4, minorVersion);

                            distincMinorVersion.add(minorVersion);

                            issueFixVersionInsert.execute();
                        } catch (MySQLIntegrityConstraintViolationException e) {
//                        log.info("Issue " + issueId + " and version " + version + " already exists.");
                        }
                    }
                }
            }
        }

        List<String> minorVersionsOrdered = new ArrayList<>(distincMinorVersion);
//        log.info(Arrays.toString(minorVersionsOrdered.toArray()));

        Collections.sort(minorVersionsOrdered, new VersionComparator());

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

}
