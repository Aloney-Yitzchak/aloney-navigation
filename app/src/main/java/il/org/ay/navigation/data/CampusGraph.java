package il.org.ay.navigation.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import il.org.ay.navigation.model.Edge;
import il.org.ay.navigation.model.Node;

/**
 * The campus paths as a graph (T13, FR-03, SDD 9.1): nodes are entrances and junctions,
 * edges are path segments that can be walked both ways, weighted by length in metres.
 *
 * <p>Stored as an adjacency list: for each node, the list of its neighbours and the
 * distance to each. {@link #findPath} is Dijkstra's algorithm with a priority queue,
 * O((V + E) log V). For the campus (117 nodes, 126 edges) one search takes well under
 * a millisecond (NFR-02 asks for under a second).
 *
 * <p>Pure Java with no Android code, so it is tested on the computer (CampusGraphTest).
 */
public final class CampusGraph {

    /** One step from a node: the neighbour and how far it is. */
    static final class Link {
        final int to;
        final double length;

        Link(int to, double length) {
            this.to = to;
            this.length = length;
        }
    }

    private final Map<Integer, Node> nodes = new HashMap<>();
    private final Map<Integer, List<Link>> adjacency = new HashMap<>();

    /**
     * Builds the graph. An edge whose length is missing (0) gets the distance between its
     * two nodes; an edge to a node that does not exist is skipped (the sync never saves
     * such data, CampusSnapshot.problems()).
     */
    public CampusGraph(@NonNull Collection<Node> nodeList, @NonNull Collection<Edge> edgeList) {
        for (Node n : nodeList) {
            nodes.put(n.id, n);
            adjacency.put(n.id, new ArrayList<>());
        }
        for (Edge e : edgeList) {
            Node a = nodes.get(e.fromId);
            Node b = nodes.get(e.toId);
            if (a == null || b == null || a == b) {
                continue;
            }
            double length = e.length > 0 ? e.length : a.distanceTo(b);
            adjacency.get(a.id).add(new Link(b.id, length));
            adjacency.get(b.id).add(new Link(a.id, length));  // paths are two-way
        }
    }

    public int nodeCount() {
        return nodes.size();
    }

    @Nullable
    public Node node(int id) {
        return nodes.get(id);
    }

    @NonNull
    public Collection<Node> nodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    /** The node closest to a point (straight line), or null for an empty graph. */
    @Nullable
    public Node nearestNode(double lat, double lng) {
        Node best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Node n : nodes.values()) {
            double d = Node.distance(lat, lng, n.lat, n.lng);
            if (d < bestDistance) {
                bestDistance = d;
                best = n;
            }
        }
        return best;
    }

    /**
     * The shortest walk from {@code fromId} to {@code toId}, or null when there is no
     * path between them or either node does not exist (AT-03).
     */
    @Nullable
    public Route findPath(int fromId, int toId) {
        if (!nodes.containsKey(fromId) || !nodes.containsKey(toId)) {
            return null;
        }
        // dist[n] = the shortest distance found so far from the start to n
        Map<Integer, Double> dist = new HashMap<>();
        // previous[n] = the node before n on that shortest walk, to rebuild the route
        Map<Integer, Integer> previous = new HashMap<>();
        // queue entries are {distance, node id}, smallest distance first
        PriorityQueue<double[]> queue = new PriorityQueue<>((x, y) -> Double.compare(x[0], y[0]));

        dist.put(fromId, 0.0);
        queue.add(new double[]{0, fromId});
        while (!queue.isEmpty()) {
            double[] head = queue.poll();
            int current = (int) head[1];
            if (head[0] > dist.get(current)) {
                continue;  // an old entry: a shorter way to this node was already handled
            }
            if (current == toId) {
                return rebuild(fromId, toId, previous, head[0]);
            }
            for (Link link : adjacency.get(current)) {
                double through = head[0] + link.length;
                Double known = dist.get(link.to);
                if (known == null || through < known) {
                    dist.put(link.to, through);
                    previous.put(link.to, current);
                    queue.add(new double[]{through, link.to});
                }
            }
        }
        return null;  // the queue ran out before reaching the destination
    }

    /** Same as {@link #findPath(int, int)}, with the nodes themselves (SDD 4). */
    @Nullable
    public Route findPath(@NonNull Node from, @NonNull Node to) {
        return findPath(from.id, to.id);
    }

    /** Follows "previous" back from the destination to the start, then reverses. */
    private Route rebuild(int fromId, int toId, Map<Integer, Integer> previous, double distance) {
        List<Node> path = new ArrayList<>();
        int at = toId;
        path.add(nodes.get(at));
        while (at != fromId) {
            at = previous.get(at);
            path.add(nodes.get(at));
        }
        Collections.reverse(path);
        return new Route(path, distance);
    }

    /** For tests and the data check: is every node reachable from every other? */
    public boolean isConnected() {
        if (nodes.isEmpty()) {
            return true;
        }
        int start = nodes.keySet().iterator().next();
        List<Integer> stack = new ArrayList<>(Arrays.asList(start));
        java.util.Set<Integer> seen = new java.util.HashSet<>(stack);
        while (!stack.isEmpty()) {
            int n = stack.remove(stack.size() - 1);
            for (Link l : adjacency.get(n)) {
                if (seen.add(l.to)) {
                    stack.add(l.to);
                }
            }
        }
        return seen.size() == nodes.size();
    }
}
