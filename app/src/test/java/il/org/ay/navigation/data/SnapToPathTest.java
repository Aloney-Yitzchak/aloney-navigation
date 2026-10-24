package il.org.ay.navigation.data;

import static il.org.ay.navigation.logic.PathMathTest.at;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import il.org.ay.navigation.model.Edge;
import il.org.ay.navigation.model.LatLng;
import il.org.ay.navigation.model.Node;

/**
 * T14: snapping a position to the paths, routes that start from a position, and where
 * the user is along a route. Four paths around a block (x metres east, y north); the
 * west side leans out, so 1 → 2 → 3 (200 m) is shorter than 1 → 4 → 3 (≈ 222 m):
 *
 * <pre>
 *   4 (−20,100) ─── 3 (100,100)
 *    \               │
 *     1 (0,0) ────── 2 (100,0)         5 (300,0) has no paths
 * </pre>
 */
public class SnapToPathTest {

    private static Node node(int id, double x, double y) {
        LatLng p = at(x, y);
        return new Node(id, p.lat, p.lng);
    }

    private static CampusGraph square() {
        List<Node> nodes = Arrays.asList(node(1, 0, 0), node(2, 100, 0), node(3, 100, 100),
                node(4, -20, 100), node(5, 300, 0));
        List<Edge> edges = Arrays.asList(new Edge(1, 1, 2, 0), new Edge(2, 2, 3, 0),
                new Edge(3, 3, 4, 0), new Edge(4, 4, 1, 0));
        return new CampusGraph(nodes, edges);
    }

    private static List<Integer> ids(Route r) {
        List<Integer> out = new ArrayList<>();
        for (Node n : r.nodes()) {
            out.add(n.id);
        }
        return out;
    }

    @Test
    public void snap_findsTheNearestPath() {
        CampusGraph.Snap s = square().snapToPath(at(40, 8));
        assertEquals(Arrays.asList(1, 2), Arrays.asList(s.fromId, s.toId));
        assertEquals(0.4, s.projection.t, 1e-4);
        assertEquals(8, s.projection.distance, 0.02);
    }

    @Test
    public void routeFromAPosition_goesTowardsTheCloserEnd() {
        // 60 m along 1–2. To 3: via 2 = 40 + 100 = 140; via 1 = 60 + 222 = 282
        Route r = square().findPath(at(60, 5), 3);
        assertEquals(Arrays.asList(Route.START, 2, 3), ids(r));
        assertEquals(140, r.totalDistance(), 0.1);
        // the route starts on the path, not in the grass 5 m away
        assertEquals(0, r.nodes().get(0).distanceTo(node(0, 60, 0)), 0.02);
    }

    @Test
    public void routeFromAPosition_canGoBackwards_whenThatIsShorter() {
        // 10 m along 1–2. To 4: via 1 = 10 + 102 = 112; via 2 = 90 + 100 + 120 = 310
        Route r = square().findPath(at(10, -3), 4);
        assertEquals(Arrays.asList(Route.START, 1, 4), ids(r));
        assertEquals(10 + Math.hypot(20, 100), r.totalDistance(), 0.1);
    }

    @Test
    public void standingOnANode_startsFromThatNode() {
        Route r = square().findPath(at(100, 0), 3);
        assertEquals(Arrays.asList(2, 3), ids(r));
    }

    @Test
    public void unreachableDestination_hasNoRoute() {
        assertNull(square().findPath(at(50, 0), 5));
    }

    @Test
    public void nextNode_andDistanceLeft_alongTheRoute() {
        Route r = square().findPath(1, 3);                     // 1 → 2 → 3, 200 m
        assertEquals(Arrays.asList(1, 2, 3), ids(r));
        assertEquals(2, r.nextNode(at(50, 2)).id);
        assertEquals(150, r.distanceLeft(at(50, 2)), 0.1);
        assertEquals(3, r.nextNode(at(98, 40)).id);
        assertEquals(60, r.distanceLeft(at(98, 40)), 0.1);
        assertEquals(3, r.nextNode(at(100, 0)).id);           // on the corner: move on
        assertEquals(3, r.destination().id);
    }

    @Test
    public void distanceFrom_measuresHowFarOffTheRoute() {
        Route r = square().findPath(1, 3);
        assertEquals(0, r.distanceFrom(at(30, 0)), 0.02);
        assertEquals(20, r.distanceFrom(at(30, 20)), 0.02);
        assertEquals(25, r.distanceFrom(at(125, 50)), 0.02);
    }

    // ------------------------------------------------------------ T17

    @Test
    public void lookAhead_pointsPastANodeTheUserIsStandingOn() {
        Route r = square().findPath(1, 3);                     // 1 → 2 → 3
        assertEquals(2, r.lookAhead(at(50, 0), 4).id);
        assertEquals(3, r.lookAhead(at(97, 0), 4).id);         // 3 m before 2: already towards 3
        assertEquals(3, r.lookAhead(at(100, 99), 4).id);       // the end stays the end
    }

    @Test
    public void advance_movesAlongTheRoute_roundCorners() {
        Route r = square().findPath(1, 3);
        assertEquals(0, r.advance(at(10, 0), 30).distanceTo(at(40, 0)), 0.05);
        assertEquals(0, r.advance(at(90, 0), 30).distanceTo(at(100, 20)), 0.05);   // 10 m east, 20 m north
        assertEquals(0, r.advance(at(100, 90), 50).distanceTo(at(100, 100)), 0.05); // stops at the end
    }
}
