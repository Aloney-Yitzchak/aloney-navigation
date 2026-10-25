package il.org.ay.navigation.logic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import il.org.ay.navigation.data.CampusGraph;
import il.org.ay.navigation.data.Route;
import il.org.ay.navigation.model.LatLng;
import il.org.ay.navigation.model.Node;

/**
 * Follows the user along a route (SDD 4, logic.NavigationManager). Each new position
 * from LocationService goes to {@link #update}; when the user has left the route, a new
 * route is calculated from where they are (T14, SRS UC-02 alternative 4a).
 *
 * <p>T17: the heading from HeadingService gives the arrow angle (SDD 9.4), and steps move
 * the position along the route between GPS fixes. Arrival (T19) is added later.
 */
public class NavigationManager {

    /** More than this far from the route = off it (SDD 9.3). */
    public static final double OFF_ROUTE_M = 15;

    /**
     * Positions in a row that must be off the route before recalculating. One jumpy GPS
     * fix next to a building should not replace a good route.
     */
    public static final int OFF_ROUTE_FIXES = 2;

    /** Closer than this to the next node, the arrow already points at the one after (T17). */
    public static final double LOOK_AHEAD_M = 4;

    /** Average step length in metres, for moving along the route between GPS fixes (T17). */
    public static final double STEP_M = 0.7;

    /** Within this of the destination's entrance = arrived (FR-06). */
    public static final double ARRIVAL_M = 5;

    /**
     * Positions in a row within {@link #ARRIVAL_M} before "you have arrived": one jumpy fix
     * should not announce it before the door (AT-06).
     */
    public static final int ARRIVAL_FIXES = 2;

    /** Farther than this from every path = not on the campus: no route is planned (T23). */
    public static final double OFF_CAMPUS_M = 150;

    /** Told when the route changes, so the screen can redraw it. */
    public interface Listener {
        void onRouteChanged(@NonNull Route route, boolean recalculated);

        /** The user left the campus, or came back. */
        default void onOffCampus(boolean offCampus) {
        }

        /** The state changed (SDD 6). */
        default void onStateChanged(@NonNull NavState state) {
        }
    }

    private final CampusGraph graph;
    private final int destinationId;
    @Nullable
    private final Listener listener;
    @Nullable
    private Route route;
    @Nullable
    private LatLng position;
    private int offRouteFixes;
    private int recalculations;
    private boolean offCampus;
    private NavState state = NavState.CALCULATING_ROUTE;   // created when a destination is chosen
    private int arrivalFixes;
    private long startMs;
    private NavState calibrationReturn = NavState.NAVIGATING;
    private double startDistance;
    private final HeadingFilter arrow = new HeadingFilter(0.25f);
    private float heading;
    private boolean hasHeading;

    public NavigationManager(@NonNull CampusGraph graph, int destinationId, @Nullable Listener listener) {
        this.graph = graph;
        this.destinationId = destinationId;
        this.listener = listener;
    }

    /**
     * A new position. The first one plans the route; later ones keep it, unless the user
     * has been off it for {@link #OFF_ROUTE_FIXES} positions in a row.
     */
    public void update(@NonNull LatLng p) {
        if (state == NavState.ARRIVED || state == NavState.WAITING_FOR_DESTINATION
                || state == NavState.MANUAL_CALIBRATION) {
            return;   // finished, cancelled, or the user is marking the position by hand
        }
        position = p;
        if (checkArrival(p)) {
            return;
        }
        if (setOffCampus(isOffCampus(p))) {
            return;  // far from every path (at home, say): routing from there means nothing
        }
        if (route == null) {
            setRoute(graph.findPath(p, destinationId), false);
            return;
        }
        if (route.distanceFrom(p) > OFF_ROUTE_M) {
            offRouteFixes++;
            if (offRouteFixes >= OFF_ROUTE_FIXES) {
                recalculations++;
                setRoute(graph.findPath(p, destinationId), true);
            }
        } else {
            offRouteFixes = 0;
        }
    }

    /**
     * The user marked where they are (FR-08, AT-08): plan again from there at once,
     * without waiting for positions off the route.
     */
    public void relocate(@NonNull LatLng p) {
        if (state == NavState.ARRIVED || state == NavState.WAITING_FOR_DESTINATION) {
            return;
        }
        setState(NavState.CALCULATING_ROUTE);   // "position confirmed" in the diagram
        position = p;
        arrivalFixes = ARRIVAL_FIXES - 1;        // a marked point is trusted: one is enough
        if (checkArrival(p)) {
            return;
        }
        if (setOffCampus(isOffCampus(p))) {
            return;
        }
        recalculations++;
        setRoute(graph.findPath(p, destinationId), true);
    }

    private boolean isOffCampus(LatLng p) {
        CampusGraph.Snap snap = graph.snapToPath(p);
        return snap != null && snap.projection.distance > OFF_CAMPUS_M;
    }

    /** Records the new state, tells the listener when it changed, and returns it. */
    private boolean setOffCampus(boolean value) {
        if (value != offCampus) {
            offCampus = value;
            offRouteFixes = 0;
            if (listener != null) {
                listener.onOffCampus(value);
            }
        }
        return value;
    }

    // ------------------------------------------------------------ T17: the arrow

    /** SDD 4: update(pos, heading), heading part. Degrees clockwise from true north. */
    public void setHeading(float degrees) {
        heading = degrees;
        hasHeading = true;
    }

    /**
     * One step detected (HeadingService, T12): move the position about one step forward along
     * the route, so the arrow and the distance keep up between GPS fixes. The next GPS fix
     * replaces this estimate.
     */
    public void onStep() {
        if (route != null && position != null && !offCampus && state == NavState.NAVIGATING) {
            position = route.advance(position, STEP_M);
        }
    }

    /**
     * SDD 4: getArrowAngle(). Where to point the arrow on the screen, in degrees clockwise
     * from straight ahead: α = (θ − heading + 360) mod 360, smoothed so it does not shake.
     * NaN while there is no route, position or heading.
     */
    public float getArrowAngle() {
        float theta = getTargetBearing();
        if (Float.isNaN(theta) || !hasHeading) {
            return Float.NaN;
        }
        return arrow.add(HeadingMath.normalize(theta - heading));
    }

    /**
     * θ in SDD 9.4: the bearing from the user to the point the arrow leads to (the next node,
     * or the one after when the next is within 4 m). NaN while there is nothing to lead to.
     * The compass arrow subtracts the heading; the ARCore arrow (T18) places an anchor that way.
     */
    public float getTargetBearing() {
        if (route == null || position == null || offCampus || state != NavState.NAVIGATING) {
            return Float.NaN;
        }
        Node target = route.lookAhead(position, LOOK_AHEAD_M);
        return PathMath.bearing(position, LatLng.of(target));
    }

    /** The instruction for the current position, or null while there is no route. */
    @Nullable
    public Guidance getGuidance() {
        return route == null || position == null || offCampus ? null : Guidance.at(route, position);
    }

    @Nullable
    public LatLng getPosition() {
        return position;
    }

    // ------------------------------------------------------------ T19: states and arrival

    /**
     * Arrived once {@link #ARRIVAL_FIXES} positions in a row are within {@link #ARRIVAL_M} of
     * the entrance. Only real positions count: steps (onStep) never announce arrival.
     */
    private boolean checkArrival(@NonNull LatLng p) {
        Node end = graph.node(destinationId);
        if (end == null) {
            return false;
        }
        if (p.distanceTo(LatLng.of(end)) <= ARRIVAL_M) {
            arrivalFixes++;
        } else {
            arrivalFixes = 0;
        }
        if (arrivalFixes >= ARRIVAL_FIXES) {
            setState(NavState.ARRIVED);
            return true;
        }
        return false;
    }

    /** "Wrong location?": the user is marking the position; positions wait until {@link #relocate}. */
    public void startCalibration() {
        if (state == NavState.NAVIGATING || state == NavState.CALCULATING_ROUTE) {
            calibrationReturn = state;
            setState(NavState.MANUAL_CALIBRATION);
        }
    }

    /** The calibration was closed without a point: carry on as before. */
    public void cancelCalibration() {
        if (state == NavState.MANUAL_CALIBRATION) {
            setState(calibrationReturn);
        }
    }

    /** "The user cancels": navigation ends without arriving. */
    public void cancel() {
        setState(NavState.WAITING_FOR_DESTINATION);
    }

    @NonNull
    public NavState getState() {
        return state;
    }

    /** SDD 4: isArrived(). */
    public boolean isArrived() {
        return state == NavState.ARRIVED;
    }

    /** Minutes since the first route was planned, at least 1. */
    public int minutesWalked() {
        if (startMs == 0) {
            return 0;
        }
        return Math.max(1, Math.round((System.currentTimeMillis() - startMs) / 60_000f));
    }

    /** The length of the first route, in metres. */
    public double startDistance() {
        return startDistance;
    }

    private void setState(@NonNull NavState newState) {
        if (newState != state) {
            state = newState;
            if (listener != null) {
                listener.onStateChanged(newState);
            }
        }
    }

    /** True while the last position was far from every campus path. */
    public boolean isOffCampus() {
        return offCampus;
    }

    private void setRoute(@Nullable Route newRoute, boolean recalculated) {
        offRouteFixes = 0;
        if (newRoute == null) {
            if (route == null) {
                setState(NavState.WAITING_FOR_DESTINATION);   // "no route" in the diagram
            }
            return;  // no way from here: keep the old route, if any
        }
        if (route == null) {
            startMs = System.currentTimeMillis();
            startDistance = newRoute.totalDistance();
        }
        route = newRoute;
        setState(NavState.NAVIGATING);
        if (listener != null) {
            listener.onRouteChanged(newRoute, recalculated);
        }
    }

    /** The current route, or null before the first position (or if there is no way there). */
    @Nullable
    public Route getRoute() {
        return route;
    }

    /** Metres left along the route from the last position; 0 before there is a route. */
    public double getDistanceLeft() {
        return route == null || position == null ? 0 : route.distanceLeft(position);
    }

    /** The node to walk towards now, or null before there is a route. */
    @Nullable
    public Node getNextNode() {
        return route == null || position == null ? null : route.nextNode(position);
    }

    /** How many times the route was recalculated because the user left it. */
    public int getRecalculations() {
        return recalculations;
    }
}
