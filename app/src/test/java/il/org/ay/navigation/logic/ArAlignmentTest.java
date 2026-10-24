package il.org.ay.navigation.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** T18: ARCore's world tied to north by the compass. */
public class ArAlignmentTest {

    /** The forward vector of a level camera turned {@code yaw}° clockwise from ARCore's −z. */
    private static float[] forward(float yaw) {
        double r = Math.toRadians(yaw);
        return new float[]{(float) Math.sin(r), 0, (float) -Math.cos(r)};
    }

    @Test
    public void yaw_ofTheFourAxes() {
        assertEquals(0, ArAlignment.yaw(0, -1), 1e-4);     // −z = ARCore's "forward" at start
        assertEquals(90, ArAlignment.yaw(1, 0), 1e-4);     // +x = to the right
        assertEquals(180, ArAlignment.yaw(0, 1), 1e-4);
        assertEquals(270, ArAlignment.yaw(-1, 0), 1e-4);
    }

    @Test
    public void startedFacingEast_theOffsetIs90() {
        ArAlignment a = new ArAlignment();
        float[] f = forward(0);
        a.update(f[0], f[1], f[2], 90);                    // the compass says east
        assertTrue(a.isReady());
        assertEquals(90, a.offset(), 1e-3);
        // turning 30° right: ARCore says 30, the compass says 120; the offset stays 90
        f = forward(30);
        a.update(f[0], f[1], f[2], 120);
        assertEquals(90, a.offset(), 1e-3);
    }

    @Test
    public void aBearing_becomesADirectionInArWorld() {
        ArAlignment a = new ArAlignment();
        float[] f = forward(0);
        a.update(f[0], f[1], f[2], 90);                    // ARCore's −z is east
        float[] east = a.direction(90);
        assertEquals(0, east[0], 1e-4);                    // straight ahead: −z
        assertEquals(-1, east[1], 1e-4);
        float[] north = a.direction(0);
        assertEquals(-1, north[0], 1e-4);                  // to the left: −x
        assertEquals(0, north[1], 1e-4);
    }

    @Test
    public void compassShaking_isAveragedOut() {
        ArAlignment a = new ArAlignment();
        float[] f = forward(0);
        for (int i = 0; i < 400; i++) {
            a.update(f[0], f[1], f[2], i % 2 == 0 ? 80 : 100);   // ±10° around 90
        }
        assertEquals(90, a.offset(), 1.5);
    }

    @Test
    public void cameraPointingAtTheGround_isNotUsed() {
        ArAlignment a = new ArAlignment();
        assertFalse(a.update(0, -0.9f, -0.3f, 50));      // looking down at the feet
        assertFalse(a.isReady());
    }
}
