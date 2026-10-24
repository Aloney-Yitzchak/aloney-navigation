package il.org.ay.navigation.logic;

import static il.org.ay.navigation.logic.PathMathTest.at;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import il.org.ay.navigation.data.CampusGraph;
import il.org.ay.navigation.data.Route;
import il.org.ay.navigation.model.Edge;
import il.org.ay.navigation.model.LatLng;
import il.org.ay.navigation.model.Node;

/**
 * T14 / SRS UC-02 4a: leaving the route by more than 15 m recalculates it.
 * The same square as SnapToPathTest: 1 (0,0), 2 (100,0), 3 (100,100), 4 (0,100).
 */
public class NavigationManagerTest {

    private final List<Boolean> changes = new ArrayList<>();
    private final List<Boolean> offCampus = new ArrayList<>();

    private static Node node(int id, double x, double y) {
        LatLng p = at(x, y);
        return new Node(id, p.lat, p.lng);
    }

    private NavigationManager navigateTo(int destination) {
        CampusGraph g = new CampusGraph(
                Arrays.asList(node(1, 0, 0), node(2, 100, 0), node(3, 100, 100), node(4, 0, 100), node(9, 500, 500)),
                Arrays.asList(new Edge(1, 1, 2, 0), new Edge(2, 2, 3, 0), new Edge(3, 3, 4, 0), new Edge(4, 4, 1, 0)));
        return new NavigationManager(g, destination, new NavigationManager.Listener() {
            @Override
            public void onRouteChanged(Route route, boolean recalculated) {
                changes.add(recalculated);
            }

            @Override
            public void onOffCampus(boolean value) {
                offCampus.add(value);
            }
        });
    }

    @Test
    public void firstPosition_plansTheRoute() {
        NavigationManager nav = navigateTo(3);
        assertNull(nav.getRoute());
        nav.update(at(20, 0));
        assertEquals(Arrays.asList(false), changes);
        assertEquals(180, nav.getDistanceLeft(), 0.1);         // 80 + 100
        assertEquals(2, nav.getNextNode().id);
    }

    @Test
    public void oneJumpyFix_doesNotRecalculate() {
        NavigationManager nav = navigateTo(3);
        nav.update(at(20, 0));
        nav.update(at(40, 30));          // 30 m off, once
        nav.update(at(50, 2));           // back on the path
        nav.update(at(60, 30));          // off again, once
        assertEquals(0, nav.getRecalculations());
        assertEquals(1, changes.size());
    }

    @Test
    public void offTheRouteTwiceInARow_recalculatesFromWhereTheUserIs() {
        NavigationManager nav = navigateTo(3);
        nav.update(at(20, 0));                                  // route 1–2–3 via 2
        nav.update(at(3, 40));                                  // walked up the west path instead
        nav.update(at(0, 60));
        assertEquals(1, nav.getRecalculations());
        assertEquals(Arrays.asList(false, true), changes);
        Route r = nav.getRoute();
        // now via 4: 40 m up to 4, then 100 m east to 3
        assertEquals(4, r.nodes().get(1).id);
        assertEquals(140, nav.getDistanceLeft(), 0.1);
    }

    @Test
    public void exactly15MetresAway_isStillOnTheRoute() {
        NavigationManager nav = navigateTo(3);
        nav.update(at(20, 0));
        nav.update(at(50, 14.9));
        nav.update(at(50, 14.9));
        assertEquals(0, nav.getRecalculations());
        assertTrue(nav.getRoute().distanceFrom(at(50, 14.9)) <= NavigationManager.OFF_ROUTE_M);
    }

    @Test
    public void unreachableDestination_noRoute_noCrash() {
        NavigationManager nav = navigateTo(9);
        nav.update(at(20, 0));
        assertNull(nav.getRoute());
        assertEquals(0, nav.getDistanceLeft(), 0);
        assertNull(nav.getNextNode());
    }

    // ------------------------------------------------------------ T23

    @Test
    public void farFromEveryPath_noRoute_andOffCampusIsReported() {
        NavigationManager nav = navigateTo(3);
        nav.update(at(2000, 3000));                       // at home, kilometres away
        nav.update(at(2010, 3000));
        assertNull(nav.getRoute());
        assertTrue(nav.isOffCampus());
        assertEquals(Arrays.asList(true), offCampus);     // reported once, not on every fix
        assertEquals(0, nav.getRecalculations());
    }

    @Test
    public void backOnCampus_theRouteIsPlanned() {
        NavigationManager nav = navigateTo(3);
        nav.update(at(2000, 3000));
        nav.update(at(20, 0));
        assertEquals(Arrays.asList(true, false), offCampus);
        assertEquals(Arrays.asList(false), changes);
        assertEquals(180, nav.getDistanceLeft(), 0.1);
    }

    @Test
    public void relocate_plansFromTheMarkedPointAtOnce() {
        NavigationManager nav = navigateTo(3);
        nav.update(at(20, 0));                            // route via 2
        nav.relocate(at(0, 60));                          // AT-08: "I am on the west path"
        assertEquals(Arrays.asList(false, true), changes);
        assertEquals(4, nav.getRoute().nodes().get(1).id);
        assertEquals(140, nav.getDistanceLeft(), 0.1);    // 40 m to 4, 100 m to 3
    }

    @Test
    public void relocate_offCampus_plansNothing() {
        NavigationManager nav = navigateTo(3);
        nav.relocate(at(2000, 3000));
        assertNull(nav.getRoute());
        assertTrue(nav.isOffCampus());
    }
}
