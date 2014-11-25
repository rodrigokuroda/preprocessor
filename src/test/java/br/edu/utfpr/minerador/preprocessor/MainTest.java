package br.edu.utfpr.minerador.preprocessor;

import br.edu.utfpr.minerador.preprocessor.Main;
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
        assertEquals("A(?i)RIES-\\d+", Main.buildPatternByName("aries"));
    }

}
