package il.org.ay.navigation.logic;

/**
 * Ties ARCore's world to the compass (T18).
 *
 * <p>ARCore tracks the phone very steadily, but in its own world: x to the right and −z
 * forward of wherever the phone pointed when the session started, y up. It does not know
 * where north is. The compass knows north but shakes. So, while the phone is held up, the
 * app keeps comparing the two:
 *
 * <pre>
 *   arYaw  = atan2(forward.x, −forward.z)        the camera's direction in ARCore's world
 *   offset = heading − arYaw                      where ARCore's −z points, from true north
 * </pre>
 *
 * and smooths the offset slowly (it should not change at all; the compass noise averages
 * out). Then a bearing from the map becomes a direction in ARCore's world:
 * yaw = bearing − offset, direction = (sin yaw, 0, −cos yaw).
 *
 * <p>Pure Java: tested on the computer (ArAlignmentTest).
 */
public class ArAlignment {

    /** Slow on purpose: the offset is fixed, only the compass around it shakes. */
    public static final float SMOOTHING = 0.03f;
    /** Only frames with the camera this close to level are used (a phone held up). */
    public static final double MAX_TILT_DEG = 35;

    /**
     * T18d: north counts as known only after this many frames (about a second and a half of the
     * phone held up). The first compass readings shake most; arrows placed on them pointed the
     * wrong way until the next placement.
     */
    public static final int SETTLE_FRAMES = 45;

    private final HeadingFilter offset = new HeadingFilter(SMOOTHING);
    private int frames;

    /** The camera's direction in ARCore's world, degrees clockwise from ARCore's −z. */
    public static float yaw(float forwardX, float forwardZ) {
        return HeadingMath.normalize((float) Math.toDegrees(Math.atan2(forwardX, -forwardZ)));
    }

    /**
     * One frame: the camera's forward direction in ARCore's world, and the compass heading
     * (true north) at the same moment. Frames with the camera pointing up or down are skipped.
     *
     * @return true when the frame was used
     */
    public boolean update(float forwardX, float forwardY, float forwardZ, float compassHeading) {
        double level = Math.hypot(forwardX, forwardZ);
        double tilt = Math.toDegrees(Math.atan2(Math.abs(forwardY), level));
        if (level < 1e-6 || tilt > MAX_TILT_DEG) {
            return false;
        }
        offset.add(HeadingMath.normalize(compassHeading - yaw(forwardX, forwardZ)));
        frames++;
        return true;
    }

    /** North is known well enough to place arrows (T18d: after {@link #SETTLE_FRAMES} frames). */
    public boolean isReady() {
        return offset.hasValue() && frames >= SETTLE_FRAMES;
    }

    /** How many frames have been used so far. */
    public int frames() {
        return frames;
    }

    /** Where ARCore's −z points, in degrees from true north. */
    public float offset() {
        return offset.get();
    }

    /** A bearing from true north as a unit direction {x, z} in ARCore's world (y = 0). */
    public float[] direction(float bearing) {
        double yaw = Math.toRadians(bearing - offset.get());
        return new float[]{(float) Math.sin(yaw), (float) -Math.cos(yaw)};
    }
}
