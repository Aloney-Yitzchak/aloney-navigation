package il.org.ay.navigation.logic;

/**
 * Distance and walking time as the user reads them (T15, the map's bottom sheet).
 * Exact metres from a GPS route look more precise than they are, so longer distances
 * are rounded: to 5 m above 20 m, to 10 m above 200 m.
 */
public final class WalkingEstimate {

    /** An unhurried walk between buildings, in metres per minute (≈ 1.3 m/s). */
    public static final double METRES_PER_MINUTE = 80;

    private WalkingEstimate() {
    }

    /** Metres to show. */
    public static int roundedMetres(double metres) {
        if (metres <= 20) {
            return (int) Math.round(metres);
        }
        int step = metres <= 200 ? 5 : 10;
        return (int) (Math.round(metres / step) * step);
    }

    /** Whole minutes to show, at least 1. */
    public static int minutes(double metres) {
        return Math.max(1, (int) Math.ceil(metres / METRES_PER_MINUTE));
    }
}
