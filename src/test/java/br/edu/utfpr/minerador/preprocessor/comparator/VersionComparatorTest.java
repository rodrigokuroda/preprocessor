package br.edu.utfpr.minerador.preprocessor.comparator;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Rodrigo T. Kuroda
 */
public class VersionComparatorTest {

    private List<String> toOrder;
    private List<String> expected;

    @Before
    public void setUp() {
        toOrder = Arrays.asList("1.1", "2.0", "2.1", "2.11", "1.2", "1.10", "1.20", "1.21-M2", "1.21-M1", "1.1.10", "1.1.1", "1-win");
        expected = Arrays.asList("1.1", "1.1.1", "1.1.10", "1.2", "1.10", "1.20", "1.21-M1", "1.21-M2", "1-win", "2.0", "2.1", "2.11");
    }

    @After
    public void tearDown() {
        toOrder = null;
        expected = null;
    }

    @Test
    public void testCompare() {
        Collections.sort(toOrder, new VersionComparator());
        Assert.assertArrayEquals(expected.toArray(), toOrder.toArray());;
    }

}
