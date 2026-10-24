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
 * <p>The arrow angle (T17) and arrival (T19) are added here in later tasks.
 */
public class NavigationManager {

    /** More than this far from the route = off it (SDD 9.3). */
    public static final double OFF_ROUTE_M = 15;

    /**
     * Positions in a row that must be off the route before recalculating. One jumpy GPS
     * fix next to a building should not replace a good route.
     */
    public static final int OFF_ROUTE_FIXES = 2;

    /** Farther than this from every path = not on the campus: no route is planned (T23). */
    public static final double OFF_CAMPUS_M = 150;

    /** Told when the route changes, so the screen can redraw it. */
    public interface Listener {
        void onRouteChanged(@NonNull Route route, boolean recalculated);

        /** The user left the campus, or came back. */
        default void onOffCampus(boolean offCampus) {
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
        position = p;
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
        position = p;
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

    /** True while the last position was far from every campus path. */
    public boolean isOffCampus() {
        return offCampus;
    }

    private void setRoute(@Nullable Route newRoute, boolean recalculated) {
        offRouteFixes = 0;
        if (newRoute == null) {
            return;  // no way to the destination: keep the old route, if any
        }
        route = newRoute;
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
