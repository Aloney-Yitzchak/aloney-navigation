package il.org.ay.navigation.logic;

import androidx.annotation.NonNull;

import il.org.ay.navigation.data.Route;
import il.org.ay.navigation.model.LatLng;
import il.org.ay.navigation.model.Node;

/**
 * The instruction shown under the camera (T17, FR-04): what to do at the next node, and
 * how far it is. The turn is the change of direction at that node, from the path coming
 * in to the path going out:
 *
 * <pre>
 *   turn = bearing(next → after) − bearing(before → next),  between −180° and 180°
 *   |turn| &lt; 30°  straight · 30°–150° left or right · over 150° turn back
 * </pre>
 */
public final class Guidance {

    public enum Action { STRAIGHT, LEFT, RIGHT, TURN_BACK, ARRIVE }

    /** A bend smaller than this is "keep going". */
    static final float STRAIGHT_DEG = 30;
    /** A bend larger than this is "turn back". */
    static final float BACK_DEG = 150;

    @NonNull
    public final Action action;
    /** Metres from the user to the node where the action happens. */
    public final double metres;
    /** 1-based number of the route segment the user is on, and how many there are. */
    public final int step;
    public final int steps;

    Guidance(@NonNull Action action, double metres, int step, int steps) {
        this.action = action;
        this.metres = metres;
        this.step = step;
        this.steps = steps;
    }

    /** The instruction for a user at {@code p} on {@code route}. */
    @NonNull
    public static Guidance at(@NonNull Route route, @NonNull LatLng p) {
        int segments = Math.max(1, route.nodes().size() - 1);
        int next = route.nextIndex(p);
        Node target = route.nodes().get(next);
        double metres = p.distanceTo(LatLng.of(target));
        // skip nodes where the path just goes on, to the first real turn (or the end)
        int i = next;
        Action action = Action.ARRIVE;
        while (i + 1 < route.nodes().size()) {
            LatLng before = i == 0 ? p : LatLng.of(route.nodes().get(i - 1));
            Action a = classify(turn(before, LatLng.of(route.nodes().get(i)), LatLng.of(route.nodes().get(i + 1))));
            if (a != Action.STRAIGHT) {
                action = a;
                break;
            }
            metres += route.nodes().get(i).distanceTo(route.nodes().get(i + 1));
            i++;
        }
        if (i + 1 >= route.nodes().size()) {
            action = Action.ARRIVE;
        }
        return new Guidance(action, metres, Math.min(next, segments), segments);
    }

    /** The bend at {@code b}, coming from {@code a} and going to {@code c}: −180°…180°, + = right. */
    static float turn(@NonNull LatLng a, @NonNull LatLng b, @NonNull LatLng c) {
        return HeadingMath.difference(PathMath.bearing(a, b), PathMath.bearing(b, c));
    }

    static Action classify(float turn) {
        float t = Math.abs(turn);
        if (t < STRAIGHT_DEG) {
            return Action.STRAIGHT;
        }
        if (t > BACK_DEG) {
            return Action.TURN_BACK;
        }
        return turn > 0 ? Action.RIGHT : Action.LEFT;
    }
}
