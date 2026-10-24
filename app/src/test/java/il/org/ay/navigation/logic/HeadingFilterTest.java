package il.org.ay.navigation.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** T12: smoothing headings as angles, not as numbers. */
public class HeadingFilterTest {

    @Test
    public void aroundNorth_theAverageIsNorth_notSouth() {
        HeadingFilter f = new HeadingFilter(0.5f);
        f.add(350);
        float h = f.add(10);
        assertEquals(0, Math.abs(HeadingMath.difference(0, h)), 0.01);   // a plain average would say 180
    }

    @Test
    public void shaking_isDamped() {
        HeadingFilter f = new HeadingFilter();
        float h = 0;
        for (int i = 0; i < 100; i++) {
            h = f.add(i % 2 == 0 ? 80 : 100);     // the compass jumps ±10° around 90
        }
        assertEquals(90, h, 2);
    }

    @Test
    public void aRealTurn_isFollowedWithinAFewReadings() {
        HeadingFilter f = new HeadingFilter();
        f.add(0);
        float h = 0;
        for (int i = 0; i < 25; i++) {            // half a second at 50 readings a second
            h = f.add(90);
        }
        assertTrue("only at " + h, h > 80);
    }

    @Test
    public void declination_turnsMagneticIntoTrueNorth() {
        HeadingFilter f = new HeadingFilter();
        f.setDeclination(5);
        assertEquals(5, f.add(0), 1e-4);
        f.setDeclination(-5);
        assertEquals(355, f.get(), 1e-4);
    }

    @Test
    public void beforeTheFirstReading_noValue() {
        HeadingFilter f = new HeadingFilter();
        assertFalse(f.hasValue());
        f.add(42);
        assertTrue(f.hasValue());
    }
}
