package il.org.ay.navigation.logic;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * T12: the heading from Android's rotation matrix. The matrices are written by hand:
 * column 0 = where the phone's right edge points, column 1 = its top edge, column 2 =
 * out of its screen, each in world axes (east, north, up).
 */
public class HeadingMathTest {

    private static float[] columns(float[] right, float[] top, float[] screen) {
        return new float[]{
                right[0], top[0], screen[0],
                right[1], top[1], screen[1],
                right[2], top[2], screen[2]};
    }

    private static final float[] EAST = {1, 0, 0};
    private static final float[] WEST = {-1, 0, 0};
    private static final float[] NORTH = {0, 1, 0};
    private static final float[] SOUTH = {0, -1, 0};
    private static final float[] UP = {0, 0, 1};
    private static final float[] DOWN = {0, 0, -1};

    @Test
    public void flatOnTheTable_topEdgeNorth_isZero() {
        assertEquals(0, HeadingMath.azimuth(columns(EAST, NORTH, UP)), 1e-4);
    }

    @Test
    public void flatOnTheTable_topEdgeEast_is90() {
        assertEquals(90, HeadingMath.azimuth(columns(SOUTH, EAST, UP)), 1e-4);
    }

    @Test
    public void upright_cameraLookingNorth_isZero() {
        // the screen faces the user (south), so the back camera looks north
        assertEquals(0, HeadingMath.azimuth(columns(EAST, UP, SOUTH)), 1e-4);
    }

    @Test
    public void upright_cameraLookingWest_is270() {
        assertEquals(270, HeadingMath.azimuth(columns(NORTH, UP, EAST)), 1e-4);
    }

    @Test
    public void tiltedHalfWay_cameraLookingSouthEast_is135() {
        float c = (float) Math.sqrt(0.5);
        // camera looks south-east and a little down; screen faces north-west and a little up
        float[] screen = {-c * 0.8f, c * 0.8f, 0.6f};
        float[] right = {c, c, 0};
        float[] top = {-c * 0.6f, c * 0.6f, -0.8f};       // screen × right; less level than the camera
        assertEquals(135, HeadingMath.azimuth(columns(right, top, screen)), 0.01);
    }

    @Test
    public void normalizeAndDifference() {
        assertEquals(350, HeadingMath.normalize(-10), 1e-4);
        assertEquals(10, HeadingMath.normalize(370), 1e-4);
        assertEquals(20, HeadingMath.difference(350, 10), 1e-4);    // across north, clockwise
        assertEquals(-20, HeadingMath.difference(10, 350), 1e-4);
        assertEquals(180, Math.abs(HeadingMath.difference(0, 180)), 1e-4);
    }
}
