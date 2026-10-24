package il.org.ay.navigation.data;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

import il.org.ay.navigation.logic.PathMath;
import il.org.ay.navigation.model.LatLng;
import il.org.ay.navigation.model.Node;

/**
 * A walk through the campus: the nodes in order, from the start to the destination's
 * entrance, and its length (SDD 4, class Route). Made by {@link CampusGraph#findPath}.
 *
 * <p>While walking, the position is projected on the route's own segments (SDD 9.3, T14)
 * to know which node comes next, how far is left, and how far off the route the user is.
 */
public final class Route {

    /** Id of the first node when a route starts from a position rather than a node. */
    public static final int START = -1;

    private final List<Node> nodes;
    private final double distance;

    Route(@NonNull List<Node> nodes, double distance) {
        this.nodes = Collections.unmodifiableList(nodes);
        this.distance = distance;
    }

    /** The nodes in walking order; the first is the start, the last the destination. */
    @NonNull
    public List<Node> nodes() {
        return nodes;
    }

    /** Length of the walk in metres. */
    public double totalDistance() {
        return distance;
    }

    /** The destination: the room's entrance. */
    @NonNull
    public Node destination() {
        return nodes.get(nodes.size() - 1);
    }

    /** The node to walk towards from position {@code p}: the end of the route segment p is on. */
    @NonNull
    public Node nextNode(@NonNull LatLng p) {
        if (nodes.size() == 1) {
            return nodes.get(0);
        }
        return nodes.get(nearestSegment(p) + 1);
    }

    /** The index in {@link #nodes()} of {@link #nextNode(LatLng)}. */
    public int nextIndex(@NonNull LatLng p) {
        return nodes.size() == 1 ? 0 : nearestSegment(p) + 1;
    }

    /**
     * The node the arrow points at: the next node, or the one after it when the user is
     * within {@code closeM} of the next one, so the arrow does not spin on top of a junction.
     */
    @NonNull
    public Node lookAhead(@NonNull LatLng p, double closeM) {
        int i = nextIndex(p);
        if (i + 1 < nodes.size() && p.distanceTo(LatLng.of(nodes.get(i))) < closeM) {
            i++;
        }
        return nodes.get(i);
    }

    /** The point {@code metres} further along the route from where {@code p} lands on it. */
    @NonNull
    public LatLng advance(@NonNull LatLng p, double metres) {
        if (nodes.size() == 1) {
            return LatLng.of(nodes.get(0));
        }
        int i = nearestSegment(p);
        LatLng at = project(p, i).point;
        double left = metres;
        while (true) {
            LatLng end = LatLng.of(nodes.get(i + 1));
            double toEnd = at.distanceTo(end);
            if (left <= toEnd || i + 2 >= nodes.size()) {
                double t = toEnd == 0 ? 1 : Math.min(1, left / toEnd);
                return new LatLng(at.lat + t * (end.lat - at.lat), at.lng + t * (end.lng - at.lng));
            }
            left -= toEnd;
            at = end;
            i++;
        }
    }

    /** How far {@code p} is from the route, in metres (more than 15 = off the route, SDD 9.3). */
    public double distanceFrom(@NonNull LatLng p) {
        if (nodes.size() == 1) {
            return p.distanceTo(LatLng.of(nodes.get(0)));
        }
        int i = nearestSegment(p);
        return project(p, i).distance;
    }

    /** Metres still to walk from {@code p}, along the route, to the destination. */
    public double distanceLeft(@NonNull LatLng p) {
        if (nodes.size() == 1) {
            return p.distanceTo(LatLng.of(nodes.get(0)));
        }
        int i = nearestSegment(p);
        double left = project(p, i).point.distanceTo(LatLng.of(nodes.get(i + 1)));
        for (int j = i + 1; j + 1 < nodes.size(); j++) {
            left += nodes.get(j).distanceTo(nodes.get(j + 1));
        }
        return left;
    }

    /**
     * Index of the route segment nearest to {@code p}. Searched from the end, so on a tie
     * (standing exactly on a corner) the later segment wins and the user moves on.
     */
    private int nearestSegment(LatLng p) {
        int best = nodes.size() - 2;
        double bestDistance = Double.MAX_VALUE;
        for (int i = nodes.size() - 2; i >= 0; i--) {
            double d = project(p, i).distance;
            if (d < bestDistance - 1e-6) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    private PathMath.Projection project(LatLng p, int segment) {
        return PathMath.project(p, LatLng.of(nodes.get(segment)), LatLng.of(nodes.get(segment + 1)));
    }

    /** Walking time in whole minutes, at least 1, at {@code metresPerMinute}. */
    public int minutes(double metresPerMinute) {
        return Math.max(1, (int) Math.ceil(distance / metresPerMinute));
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Node n : nodes) {
            if (sb.length() > 0) {
                sb.append(" → ");
            }
            sb.append(n.id);
        }
        return sb + String.format(java.util.Locale.ROOT, " (%.1f m)", distance);
    }
}
