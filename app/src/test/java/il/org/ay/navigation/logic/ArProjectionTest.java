package il.org.ay.navigation.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.opengl.Matrix;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** T18: points in ARCore's world on the screen, with matrices built as ARCore builds them. */
@RunWith(RobolectricTestRunner.class)
public class ArProjectionTest {

    private static final int W = 1000;
    private static final int H = 2000;

    /** A camera at the origin looking along −z (view = identity), 60° field of view. */
    private static float[] projection() {
        float[] p = new float[16];
        Matrix.perspectiveM(p, 0, 60, (float) W / H, 0.1f, 100f);
        return p;
    }

    private static float[] identity() {
        float[] m = new float[16];
        Matrix.setIdentityM(m, 0);
        return m;
    }

    @Test
    public void straightAhead_isTheMiddleOfTheScreen() {
        float[] s = ArProjection.toScreen(identity(), projection(), 0, 0, -3, W, H);
        assertEquals(500, s[0], 0.5);
        assertEquals(1000, s[1], 0.5);
        assertEquals(3, s[2], 1e-3);                 // 3 m in front
    }

    @Test
    public void aPointOnTheGroundAhead_isBelowTheMiddle() {
        float[] s = ArProjection.toScreen(identity(), projection(), 0, -1.3f, -3, W, H);
        assertEquals(500, s[0], 0.5);
        assertEquals(true, s[1] > 1000);
    }

    @Test
    public void behindTheCamera_isNotOnTheScreen() {
        assertNull(ArProjection.toScreen(identity(), projection(), 0, 0, 3, W, H));
    }

    @Test
    public void anArrowAlongThePathAhead_pointsUp_andToTheRightPointsRight() {
        float[] from = {0, -1.3f, -3};
        assertEquals(0, ArProjection.screenAngle(identity(), projection(), from, new float[]{0, -1.3f, -4}, W, H), 0.5);
        assertEquals(90, ArProjection.screenAngle(identity(), projection(), from, new float[]{1, -1.3f, -3}, W, H), 15);
        assertEquals(270, ArProjection.screenAngle(identity(), projection(), from, new float[]{-1, -1.3f, -3}, W, H), 15);
    }
}
