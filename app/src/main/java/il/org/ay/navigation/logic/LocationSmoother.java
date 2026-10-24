package il.org.ay.navigation.logic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import il.org.ay.navigation.model.LatLng;

/**
 * Smooths GPS positions (T11, FR-03). A one-dimensional Kalman filter on latitude and
 * longitude: each new fix pulls the estimate towards it according to how sure each
 * side is. A fix that reports ±5 m moves the estimate more than one that reports ±30 m.
 *
 * <pre>
 *   variance += (walking speed · seconds since the last fix)²   the estimate ages
 *   gain      = variance / (variance + accuracy²)               0 = ignore fix, 1 = trust it
 *   estimate += gain · (fix − estimate)
 *   variance  = (1 − gain) · variance
 * </pre>
 *
 * Pure Java, no Android: tested on the computer (LocationSmootherTest).
 */
public class LocationSmoother {

    /** A brisk walk, in metres per second: how fast the true position can drift away. */
    public static final double WALKING_SPEED = 2.0;
    /** Fixes worse than this are ignored: indoors or just after starting the GPS. */
    public static final double WORST_ACCURACY_M = 50;
    /** Accuracy below this is not believed (phones report 1–3 m outdoors when it is more). */
    public static final double BEST_ACCURACY_M = 3;

    private double lat;
    private double lng;
    /** Variance of the estimate in m², or -1 before the first fix. */
    private double variance = -1;
    private long lastTimeMs;

    /**
     * Adds a fix and returns the smoothed position, or null while there is no usable fix.
     *
     * @param accuracyM the radius the phone reports (Location.getAccuracy()), in metres
     * @param timeMs    when the fix was taken: wall-clock milliseconds (Location.getTime())
     */
    @Nullable
    public LatLng add(double fixLat, double fixLng, double accuracyM, long timeMs) {
        if (accuracyM > WORST_ACCURACY_M || Double.isNaN(fixLat) || Double.isNaN(fixLng)) {
            return current();
        }
        double accuracy = Math.max(accuracyM, BEST_ACCURACY_M);
        if (variance < 0) {
            lat = fixLat;
            lng = fixLng;
            variance = accuracy * accuracy;
            lastTimeMs = timeMs;
            return current();
        }
        double seconds = Math.max(0, (timeMs - lastTimeMs) / 1000.0);
        lastTimeMs = Math.max(lastTimeMs, timeMs);
        variance += Math.pow(WALKING_SPEED * seconds, 2);

        double gain = variance / (variance + accuracy * accuracy);
        lat += gain * (fixLat - lat);
        lng += gain * (fixLng - lng);
        variance = (1 - gain) * variance;
        return current();
    }

    /**
     * The user marked where they are (manual calibration, FR-08): trust it fully.
     * Later fixes move on from this point.
     */
    public void calibrate(@NonNull LatLng p, long timeMs) {
        lat = p.lat;
        lng = p.lng;
        variance = BEST_ACCURACY_M * BEST_ACCURACY_M;
        lastTimeMs = timeMs;
    }

    /** The current estimate, or null before the first usable fix. */
    @Nullable
    public LatLng current() {
        return variance < 0 ? null : new LatLng(lat, lng);
    }

    /** How sure the estimate is, as a radius in metres (√variance); -1 before the first fix. */
    public double accuracy() {
        return variance < 0 ? -1 : Math.sqrt(variance);
    }

    /** Forgets everything: the next fix starts afresh. */
    public void reset() {
        variance = -1;
    }
}
