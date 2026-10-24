package il.org.ay.navigation.logic;

/**
 * Smooths the heading so the arrow does not shake (T12). Angles cannot be averaged as
 * numbers: the average of 350° and 10° is 0°, not 180°. So each heading is turned into
 * a unit vector (sin, cos), the vectors are smoothed, and the angle is read back:
 *
 * <pre>
 *   s ← s + a (sin h − s),   c ← c + a (cos h − c),   heading = atan2(s, c)
 * </pre>
 *
 * a is between 0 and 1: smaller = steadier but slower to follow a turn.
 * Also adds the magnetic declination: the compass points to magnetic north, the map to
 * true north (in Israel about 5° apart).
 */
public class HeadingFilter {

    /** Steady enough for walking, and still follows a turn in under a second at 50 readings a second. */
    public static final float SMOOTHING = 0.15f;

    private final float smoothing;
    private double sin;
    private double cos;
    private boolean started;
    private float declination;

    public HeadingFilter() {
        this(SMOOTHING);
    }

    public HeadingFilter(float smoothing) {
        this.smoothing = smoothing;
    }

    /** Adds a raw magnetic heading in degrees; returns the smoothed true heading. */
    public float add(float magneticHeading) {
        double rad = Math.toRadians(magneticHeading);
        if (!started) {
            sin = Math.sin(rad);
            cos = Math.cos(rad);
            started = true;
        } else {
            sin += smoothing * (Math.sin(rad) - sin);
            cos += smoothing * (Math.cos(rad) - cos);
        }
        return get();
    }

    /** The smoothed heading from true north, 0–360. */
    public float get() {
        return HeadingMath.normalize((float) Math.toDegrees(Math.atan2(sin, cos)) + declination);
    }

    /** Degrees east of true north that magnetic north lies (GeomagneticField.getDeclination()). */
    public void setDeclination(float degrees) {
        declination = degrees;
    }

    public boolean hasValue() {
        return started;
    }
}
