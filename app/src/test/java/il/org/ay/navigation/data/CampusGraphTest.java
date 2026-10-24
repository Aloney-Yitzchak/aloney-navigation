package il.org.ay.navigation.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import il.org.ay.navigation.model.Edge;
import il.org.ay.navigation.model.Node;

/**
 * AT-03 (FR-03): Dijkstra on a small graph whose shortest paths were worked out by hand.
 *
 * Six nodes, nine two-way edges (metres), and node 7 with no edges at all:
 * 1–2 7, 1–3 9, 1–6 14, 2–3 10, 2–4 15, 3–4 11, 3–6 2, 4–5 6, 5–6 9.
 * By hand: 1→5 = 1–3–6–5 = 9 + 2 + 9 = 20 (not 1–6–5 = 23, not 1–3–4–5 = 26).
 */
public class CampusGraphTest {

    private static Node node(int id) {
        return new Node(id, 32.5 + id * 0.0001, 35.0);
    }

    private static CampusGraph sixNodes() {
        List<Node> nodes = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            nodes.add(node(i));
        }
        int[][] e = {{1, 2, 7}, {1, 3, 9}, {1, 6, 14}, {2, 3, 10}, {2, 4, 15},
                {3, 4, 11}, {3, 6, 2}, {4, 5, 6}, {5, 6, 9}};
        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < e.length; i++) {
            edges.add(new Edge(i + 1, e[i][0], e[i][1], e[i][2]));
        }
        return new CampusGraph(nodes, edges);
    }

    private static List<Integer> ids(Route route) {
        List<Integer> out = new ArrayList<>();
        for (Node n : route.nodes()) {
            out.add(n.id);
        }
        return out;
    }

    @Test
    public void shortestPath_takesTheDetour_whenItIsShorter() {
        Route r = sixNodes().findPath(1, 5);
        assertNotNull(r);
        assertEquals(Arrays.asList(1, 3, 6, 5), ids(r));
        assertEquals(20, r.totalDistance(), 1e-9);
    }

    @Test
    public void moreHandWorkedPaths() {
        CampusGraph g = sixNodes();
        assertEquals(Arrays.asList(1, 3, 4), ids(g.findPath(1, 4)));     // 20, not 1–2–4 = 22
        assertEquals(20, g.findPath(1, 4).totalDistance(), 1e-9);
        assertEquals(Arrays.asList(2, 3, 6), ids(g.findPath(2, 6)));     // 12
        assertEquals(Arrays.asList(4, 5), ids(g.findPath(4, 5)));        // 6
        assertEquals(Arrays.asList(1, 2), ids(g.findPath(1, 2)));        // 7
    }

    @Test
    public void pathsGoBothWays() {
        CampusGraph g = sixNodes();
        assertEquals(Arrays.asList(5, 6, 3, 1), ids(g.findPath(5, 1)));
        assertEquals(g.findPath(1, 5).totalDistance(), g.findPath(5, 1).totalDistance(), 1e-9);
    }

    @Test
    public void unreachableDestination_hasNoRoute() {
        assertNull(sixNodes().findPath(1, 7));
        assertFalse(sixNodes().isConnected());
    }

    @Test
    public void unknownNodes_haveNoRoute() {
        assertNull(sixNodes().findPath(1, 99));
        assertNull(sixNodes().findPath(99, 1));
    }

    @Test
    public void startIsDestination_zeroMetres() {
        Route r = sixNodes().findPath(3, 3);
        assertEquals(Arrays.asList(3), ids(r));
        assertEquals(0, r.totalDistance(), 0);
    }

    @Test
    public void missingLength_isTakenFromTheCoordinates() {
        Node a = new Node(1, 32.5, 35.0);
        Node b = new Node(2, 32.5001, 35.0);   // 0.0001° north ≈ 11.1 m
        CampusGraph g = new CampusGraph(Arrays.asList(a, b), Arrays.asList(new Edge(1, 1, 2, 0)));
        assertEquals(11.12, g.findPath(1, 2).totalDistance(), 0.01);
    }

    @Test
    public void edgesToMissingNodes_areSkipped() {
        CampusGraph g = new CampusGraph(Arrays.asList(node(1), node(2)),
                Arrays.asList(new Edge(1, 1, 2, 5), new Edge(2, 2, 42, 5)));
        assertEquals(5, g.findPath(1, 2).totalDistance(), 0);
        assertTrue(g.isConnected());
    }

    @Test
    public void nearestNode() {
        CampusGraph g = sixNodes();
        assertEquals(4, g.nearestNode(32.5004, 35.0).id);
        assertEquals(1, g.nearestNode(32.0, 35.0).id);
        assertNull(new CampusGraph(new ArrayList<>(), new ArrayList<>()).nearestNode(32.5, 35));
    }

    @Test
    public void walkingMinutes_roundUp_atLeastOne() {
        Route r = sixNodes().findPath(1, 5);   // 20 m
        assertEquals(1, r.minutes(80));
        assertEquals(2, r.minutes(10));                           // 20 m at 10 m/min
        assertEquals(3, sixNodes().findPath(1, 4).minutes(9));    // 20 m at 9 m/min = 2.2
    }
}
