package il.org.ay.navigation.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import il.org.ay.navigation.model.CampusRoom;
import il.org.ay.navigation.model.Edge;
import il.org.ay.navigation.model.Node;

/**
 * One complete version of the campus data as read from Firestore (T08).
 * Pure Java: built by FirestoreSource, checked here, then saved to Room.
 */
public class CampusSnapshot {

    public final long version;
    public final List<Node> nodes;
    public final List<Edge> edges;
    public final List<CampusRoom> rooms;

    public CampusSnapshot(long version, List<Node> nodes, List<Edge> edges, List<CampusRoom> rooms) {
        this.version = version;
        this.nodes = nodes;
        this.edges = edges;
        this.rooms = rooms;
    }

    /**
     * Problems that make this snapshot unsafe to save (empty list = complete).
     * The phone keeps its old copy rather than replace it with broken data.
     */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (version <= 0) problems.add("missing version");
        if (nodes.isEmpty()) problems.add("no nodes");
        if (rooms.isEmpty()) problems.add("no rooms");

        Set<Integer> nodeIds = new HashSet<>();
        for (Node n : nodes) {
            if (!nodeIds.add(n.id)) problems.add("node " + n.id + " twice");
            if (n.lat < -90 || n.lat > 90 || n.lng < -180 || n.lng > 180) {
                problems.add("node " + n.id + " has bad coordinates");
            }
        }
        for (Edge e : edges) {
            if (!nodeIds.contains(e.fromId) || !nodeIds.contains(e.toId)) {
                problems.add("edge " + e.id + " points to a missing node");
            }
            if (e.length <= 0) problems.add("edge " + e.id + " has no length");
        }
        Set<String> numbers = new HashSet<>();
        for (CampusRoom r : rooms) {
            if (!numbers.add(r.number)) problems.add("room " + r.number + " twice");
            if (r.entranceNodeId != null && !nodeIds.contains(r.entranceNodeId)) {
                problems.add("room " + r.number + " points to a missing entrance");
            }
        }
        return problems;
    }

    public boolean isComplete() {
        return problems().isEmpty();
    }
}
