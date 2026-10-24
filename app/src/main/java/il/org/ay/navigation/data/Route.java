package il.org.ay.navigation.data;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

import il.org.ay.navigation.model.Node;

/**
 * A walk through the campus: the nodes in order, from the start to the destination's
 * entrance, and its length (SDD 4, class Route). Made by {@link CampusGraph#findPath}.
 * Which node comes next while walking ({@code nextNode}) needs path snapping: T14.
 */
public final class Route {

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
