package il.org.ay.navigation.logic;

import static il.org.ay.navigation.logic.PathMathTest.at;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import il.org.ay.navigation.model.LatLng;

/** T11: the Kalman smoothing of GPS fixes, reasoned in metres (x east, y north). */
public class LocationSmootherTest {

    private static final LatLng ORIGIN = at(0, 0);

    private static LatLng add(LocationSmoother s, double x, double y, double accuracy, long seconds) {
        LatLng p = at(x, y);
        return s.add(p.lat, p.lng, accuracy, seconds * 1000);
    }

    @Test
    public void firstFix_isTakenAsItIs() {
        LocationSmoother s = new LocationSmoother();
        assertNull(s.current());
        LatLng p = add(s, 10, 20, 8, 0);
        assertEquals(0, p.distanceTo(at(10, 20)), 1e-6);
        assertEquals(8, s.accuracy(), 1e-9);
    }

    @Test
    public void aJumpyFix_movesTheEstimateOnlyPartWay() {
        LocationSmoother s = new LocationSmoother();
        add(s, 0, 0, 5, 0);
        // one second later a fix 20 m north, as unsure as the estimate: about half way
        LatLng p = add(s, 0, 20, 5, 1);
        double moved = p.distanceTo(ORIGIN);
        assertTrue("moved " + moved, moved > 5 && moved < 15);
    }

    @Test
    public void aPreciseFix_countsMoreThanAVagueOne() {
        LocationSmoother precise = new LocationSmoother();
        add(precise, 0, 0, 10, 0);
        double withPrecise = add(precise, 0, 20, 3, 1).distanceTo(ORIGIN);

        LocationSmoother vague = new LocationSmoother();
        add(vague, 0, 0, 10, 0);
        double withVague = add(vague, 0, 20, 30, 1).distanceTo(ORIGIN);

        assertTrue(withPrecise > withVague);
    }

    @Test
    public void fixesWorseThan50Metres_areIgnored() {
        LocationSmoother s = new LocationSmoother();
        assertNull(add(s, 0, 0, 80, 0));                // nothing usable yet
        add(s, 0, 0, 5, 1);
        LatLng p = add(s, 0, 200, 60, 2);               // indoors: ±60 m
        assertEquals(0, p.distanceTo(ORIGIN), 1e-6);
    }

    @Test
    public void walkingSteadily_theEstimateFollows() {
        LocationSmoother s = new LocationSmoother();
        LatLng p = null;
        for (int t = 0; t <= 30; t++) {
            p = add(s, 1.4 * t, 0, 5, t);               // walking east at 1.4 m/s
        }
        assertTrue(p.distanceTo(at(42, 0)) < 3);        // within 3 m of where the user really is
    }

    @Test
    public void standingStill_theEstimateSettlesAndGrowsSure() {
        LocationSmoother s = new LocationSmoother();
        double[] noise = {4, -3, 5, -4, 2, -5, 3, -2, 4, -3};
        for (int t = 0; t < noise.length; t++) {
            add(s, noise[t], -noise[t], 8, t);          // fixes scattered around (0, 0)
        }
        assertTrue(s.current().distanceTo(ORIGIN) < 4);
        assertTrue(s.accuracy() < 8);
    }

    @Test
    public void calibrate_isTrusted_andLaterFixesMoveOnFromIt() {
        LocationSmoother s = new LocationSmoother();
        add(s, 0, 0, 5, 0);
        s.calibrate(at(30, 0), 1000);
        assertEquals(0, s.current().distanceTo(at(30, 0)), 1e-6);
        // a vague fix one second later back near the old place hardly moves it
        LatLng p = add(s, 0, 0, 25, 2);
        assertTrue(p.distanceTo(at(30, 0)) < 5);
    }

    @Test
    public void reset_startsAfresh() {
        LocationSmoother s = new LocationSmoother();
        add(s, 0, 0, 5, 0);
        s.reset();
        assertNull(s.current());
    }
}
