package br.edu.utfpr.minerador.preprocessor;

import org.junit.After;
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
    public void testBuildPattern() {
        assertEquals("(?i)ARIES-(\\d+)(,\\s*\\d+)*", Main.buildPatternByName("aries"));
    }

    @Test
    public void testSplitVersion() {
        assertEquals("1.3", Main.getMajorVersion("1.3.2"));
    }

}
