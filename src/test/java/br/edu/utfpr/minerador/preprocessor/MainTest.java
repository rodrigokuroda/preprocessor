package br.edu.utfpr.minerador.preprocessor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Rodrigo T. Kuroda
 */
public class MainTest {

    public MainTest() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testMatchPattern() {
        final Pattern regex = Pattern.compile(Main.buildPatternByName("DEBRY"), Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher matcher = regex.matcher("DEBRY-467 - Unseal one package in derby.jar iapi.services.context that is also in derbytools.jar.");
        Assert.assertTrue(matcher.find());
        Assert.assertEquals("DEBRY-467", matcher.group().trim());
    }

    @Test
    public void testMatchPattern2() {
        String message = "    DERBY-2193-03: Adjust the import/export lob tests which assert different results based on the vm level.\n"
                + "     \n"
                + "     git-svn-id: https://svn.apache.org/repos/asf/db/derby/code/trunk@529322 13f79535-47bb-0310-9956-ffa450edef68\n"
                + " ";
        final Pattern regex = Pattern.compile(Main.buildPatternByName("DERBY"), Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher matcher = regex.matcher(message);
        Assert.assertTrue(matcher.find());
        Assert.assertEquals("DERBY-2193", matcher.group().trim());
    }

    @Test
    public void testMatchPattern3() {
        String message = "This commit is for DERBY- 2528 Set the correct collation type in SchemaDescriptor. The collation type will be UCS_BASIC for system schemas but it can be\n"
                + "    TERRITORY_BASED/UCS_BASIC for user schemas.";
        final Pattern regex = Pattern.compile(Main.buildPatternByName("DERBY"), Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher matcher = regex.matcher(message);
        Assert.assertTrue(matcher.find());
        Assert.assertEquals("DERBY-2528", matcher.group().replace(" ", ""));
    }

    @Test
    public void testMatchPattern4() {
        String message = "This commit is for DERBY -2528 Set the correct collation type in SchemaDescriptor. The collation type will be UCS_BASIC for system schemas but it can be\n"
                + "    TERRITORY_BASED/UCS_BASIC for user schemas.";
        final Pattern regex = Pattern.compile(Main.buildPatternByName("DERBY"), Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher matcher = regex.matcher(message);
        Assert.assertTrue(matcher.find());
        Assert.assertEquals("DERBY-2528", matcher.group().replace(" ", ""));
    }

    @Test
    public void testNotMatchPattern1() {
        final Pattern regex = Pattern.compile(Main.buildPatternByName("ARIES"), Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher matcher = regex.matcher("awdawdawd Aries-1. awdawd");
        Assert.assertTrue(matcher.find());
    }

    @Test
    public void testNotMatchPattern2() {
        final Pattern regex = Pattern.compile(Main.buildPatternByName("ARIES"), Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher matcher = regex.matcher("awdawdawd http://apache.org/aries-1.1 awdawd");
        Assert.assertFalse(matcher.find());
    }

    @Test
    public void testNotMatchPattern3() {
        final Pattern regex = Pattern.compile(Main.buildPatternByName("ARIES"), Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher matcher = regex.matcher("awdawdawd http://apache.org/aries-1.x awdawd");
        Assert.assertFalse(matcher.find());
    }

    @Test
    public void testNotMatchPattern4() {
        final Pattern regex = Pattern.compile(Main.buildPatternByName("ARIES"), Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher matcher = regex.matcher("awdawdawd http://apache.org/aries-1-1 awdawd");
        Assert.assertTrue(matcher.find());
    }

    @Test
    public void testNotMatchPattern5() {
        final Pattern regex = Pattern.compile(Main.buildPatternByName("ARIES"), Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher matcher = regex.matcher("awdawdawd http://apache.org/aries-1-x awdawd");
        Assert.assertFalse(matcher.find());
    }

    @Test
    public void testSplitVersion() {
        assertEquals("1.3", Main.getMajorVersion("1.3.2"));
    }

    @Test
    public void testReplaceURL() {
        assertEquals("    Fix build error introduced by CAMEL-1134\n ", Main.replaceUrl("    Fix build error introduced by CAMEL-1134\n"
                + "     \n"
                + "     git-svn-id: https://svn.apache.org/repos/asf/activemq/camel/branches/camel-1.x@722153 13f79535-47bb-0310-9956-ffa450edef68\n"
                + " "));
    }
}
