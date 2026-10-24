package il.org.ay.navigation.logic;

/**
 * Which way the phone points (T12, FR-04), from the rotation matrix that Android builds
 * from the Rotation Vector sensor (SensorManager.getRotationMatrixFromVector).
 *
 * <p>The matrix R turns a direction on the phone into a direction in the world, where
 * x = east, y = north, z = up. Two directions on the phone matter:
 * <ul>
 *   <li>the back camera, (0, 0, −1): where the user looks on the camera screen, phone upright</li>
 *   <li>the top edge, (0, 1, 0): where the phone points when it lies flat, on the map screen</li>
 * </ul>
 * Whichever of the two is closer to horizontal gives the heading, so the same code works
 * upright and flat. Heading = atan2(east, north), in degrees clockwise from north.
 * Pure Java: tested on the computer (HeadingMathTest).
 */
public final class HeadingMath {

    private HeadingMath() {
    }

    /** Heading in degrees, 0–360, clockwise from (magnetic) north. R is 3×3, row by row. */
    public static float azimuth(float[] r) {
        // camera direction in the world = R · (0, 0, −1) = minus the third column
        float camEast = -r[2];
        float camNorth = -r[5];
        // top-edge direction in the world = R · (0, 1, 0) = the second column
        float topEast = r[1];
        float topNorth = r[4];
        double camFlat = Math.hypot(camEast, camNorth);
        double topFlat = Math.hypot(topEast, topNorth);
        double degrees = camFlat >= topFlat
                ? Math.toDegrees(Math.atan2(camEast, camNorth))
                : Math.toDegrees(Math.atan2(topEast, topNorth));
        return normalize((float) degrees);
    }

    /** Any angle to 0 (inclusive) – 360 (exclusive). */
    public static float normalize(float degrees) {
        float d = degrees % 360f;
        return d < 0 ? d + 360f : d;
    }

    /** The shortest turn from {@code from} to {@code to}, −180 to 180 (positive = clockwise). */
    public static float difference(float from, float to) {
        float d = normalize(to - from);
        return d > 180 ? d - 360 : d;
    }
}
