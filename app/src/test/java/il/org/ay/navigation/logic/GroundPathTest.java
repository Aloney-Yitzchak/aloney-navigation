package il.org.ay.navigation.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import il.org.ay.navigation.model.LatLng;

/** T18c: the route ahead becomes chevrons in ARCore's world, in the right places and directions. */
public class GroundPathTest {

    private static final float EPS = 0.01f;

    @Test
    public void enu_northAndEast() {
        LatLng here = new LatLng(32.5115, 35.0037);
        float[] north = GroundPath.enu(here, new LatLng(32.5115 + 10 / 111_195.0, 35.0037));
        assertEquals(0, north[0], EPS);
        assertEquals(10, north[1], 0.05);
        float[] east = GroundPath.enu(here, new LatLng(32.5115, 35.0037 + 10 / (111_195.0 * Math.cos(Math.toRadians(32.5115)))));
        assertEquals(10, east[0], 0.05);
        assertEquals(0, east[1], EPS);
    }

    @Test
    public void toWorld_whenArcoreMinusZIsNorth() {
        float[] north = GroundPath.toWorld(0, 1, 0);
        assertEquals(0, north[0], EPS);
        assertEquals(-1, north[1], EPS);     // straight ahead
        float[] east = GroundPath.toWorld(1, 0, 0);
        assertEquals(1, east[0], EPS);       // to the right
        assertEquals(0, east[1], EPS);
    }

    @Test
    public void toWorld_whenArcoreMinusZIsEast() {
        float[] east = GroundPath.toWorld(1, 0, 90);
        assertEquals(0, east[0], EPS);
        assertEquals(-1, east[1], EPS);      // east is straight ahead
        float[] north = GroundPath.toWorld(0, 1, 90);
        assertEquals(-1, north[0], EPS);     // north is to the left
        assertEquals(0, north[1], EPS);
    }

    @Test
    public void toWorld_matchesTheAlignmentsDirection() {
        ArAlignment a = new ArAlignment();
        a.update(1, 0, 0, 30);                // camera along ARCore's +x, compass says 30°
        float offset = a.offset();
        for (float bearing : new float[]{0, 45, 120, 200, 300}) {
            double b = Math.toRadians(bearing);
            float[] w = GroundPath.toWorld((float) Math.sin(b), (float) Math.cos(b), offset);
            float[] d = a.direction(bearing);
            assertEquals(d[0], w[0], EPS);
            assertEquals(d[1], w[1], EPS);
        }
    }

    @Test
    public void chevrons_alongAPathThatTurnsRight() {
        // 3 m north, then east: chevrons at 1.5 m (north), 3 m (the corner) and 4.5 m (east)
        float[][] points = {{0, 1.5f}, {0, 3f}, {1.5f, 3f}};
        float[][] ahead = {{0, 2f}, {0.5f, 3f}, {2f, 3f}};
        List<GroundPath.Chevron> list = GroundPath.chevrons(points, ahead, new float[]{1.5f, 3f, 4.5f}, 0);
        assertEquals(3, list.size());
        GroundPath.Chevron first = list.get(0);
        assertEquals(0, first.x, EPS);
        assertEquals(-1.5f, first.z, EPS);
        assertEquals(0, first.yawDegrees(), 0.5);      // pointing straight ahead (−z)
        GroundPath.Chevron last = list.get(2);
        assertEquals(1.5f, last.x, EPS);
        assertEquals(-3f, last.z, EPS);
        assertEquals(1, last.dx, EPS);                 // pointing to the right (+x)
        assertEquals(-90, last.yawDegrees(), 0.5);     // rotateM by −90° turns −z into +x
    }

    @Test
    public void chevrons_notPastTheEndOfTheRoute() {
        float[][] points = {{0, 1.5f}, {0, 2f}};
        float[][] ahead = {{0, 2f}, {0, 2f}};          // the route ends at 2 m
        assertEquals(1, GroundPath.chevrons(points, ahead, new float[]{1.5f, 3f}, 0).size());
    }

    @Test
    public void alpha_fadesUnderThePhone_andFarAway() {
        assertEquals(0, GroundPath.alpha(0.3f), EPS);
        assertTrue(GroundPath.alpha(1f) > 0 && GroundPath.alpha(1f) < 0.9f);
        assertEquals(0.9f, GroundPath.alpha(4f), EPS);
        assertTrue(GroundPath.alpha(8.5f) < 0.9f);
        assertEquals(0, GroundPath.alpha(11f), EPS);
    }
}
