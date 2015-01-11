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
        final Pattern regex = Pattern.compile(Main.buildPatternByName("ARIES"), Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher matcher = regex.matcher("awdawdawd Aries-1 awdawd");
        Assert.assertTrue(matcher.find());
        Assert.assertEquals("Aries-1", matcher.group().trim());
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
        Assert.assertFalse(matcher.find());
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
