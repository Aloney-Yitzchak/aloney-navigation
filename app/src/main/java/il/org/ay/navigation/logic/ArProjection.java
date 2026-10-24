package il.org.ay.navigation.logic;

import androidx.annotation.Nullable;

/**
 * From a point in ARCore's world to a place on the screen (T18), with the camera's view and
 * projection matrices (4×4, column by column, as OpenGL and ARCore give them):
 *
 * <pre>
 *   clip   = projection · view · (x, y, z, 1)
 *   ndc    = clip.xyz / clip.w                 −1 … 1 across the screen
 *   screen = ((ndc.x + 1) / 2 · width, (1 − ndc.y) / 2 · height)
 * </pre>
 *
 * A point behind the camera has clip.w ≤ 0 and is not on the screen.
 * Pure Java: tested on the computer (ArProjectionTest).
 */
public final class ArProjection {

    private ArProjection() {
    }

    /** Screen x, y in pixels and the distance in front of the camera, or null when behind it. */
    @Nullable
    public static float[] toScreen(float[] view, float[] projection, float x, float y, float z,
                                   int width, int height) {
        float[] p = {x, y, z, 1};
        float[] eye = multiply(view, p);
        float[] clip = multiply(projection, eye);
        if (clip[3] <= 1e-6f) {
            return null;
        }
        float nx = clip[0] / clip[3];
        float ny = clip[1] / clip[3];
        return new float[]{(nx + 1) / 2 * width, (1 - ny) / 2 * height, clip[3]};
    }

    /**
     * The arrow's angle on the screen, degrees clockwise from "up": the direction from where
     * {@code from} lands to where {@code to} lands. NaN when either is behind the camera.
     */
    public static float screenAngle(float[] view, float[] projection, float[] from, float[] to,
                                    int width, int height) {
        float[] a = toScreen(view, projection, from[0], from[1], from[2], width, height);
        float[] b = toScreen(view, projection, to[0], to[1], to[2], width, height);
        if (a == null || b == null) {
            return Float.NaN;
        }
        return HeadingMath.normalize((float) Math.toDegrees(Math.atan2(b[0] - a[0], -(b[1] - a[1]))));
    }

    /** m · v for a 4×4 matrix stored column by column. */
    static float[] multiply(float[] m, float[] v) {
        float[] r = new float[4];
        for (int row = 0; row < 4; row++) {
            r[row] = m[row] * v[0] + m[4 + row] * v[1] + m[8 + row] * v[2] + m[12 + row] * v[3];
        }
        return r;
    }
}
