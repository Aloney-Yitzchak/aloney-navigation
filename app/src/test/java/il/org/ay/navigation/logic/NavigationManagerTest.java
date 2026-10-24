package il.org.ay.navigation.logic;

import static il.org.ay.navigation.logic.PathMathTest.at;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    private final List<NavState> states = new ArrayList<>();

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

            @Override
            public void onStateChanged(NavState state) {
                states.add(state);
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

    // ------------------------------------------------------------ T17

    @Test
    public void arrow_pointsAtTheNextNode_relativeToThePhone() {
        NavigationManager nav = navigateTo(3);
        nav.update(at(20, 0));                       // next node: 2, due east
        assertTrue(Float.isNaN(nav.getArrowAngle())); // no heading yet
        nav.setHeading(0);                            // phone facing north
        assertEquals(90, nav.getArrowAngle(), 0.5);   // the arrow points right
    }

    @Test
    public void arrow_facingTheNextNode_pointsStraightUp() {
        NavigationManager nav = navigateTo(3);
        nav.update(at(20, 0));
        nav.setHeading(90);                           // facing east, towards 2
        assertEquals(0, Math.abs(HeadingMath.difference(0, nav.getArrowAngle())), 0.5);
    }

    @Test
    public void nearTheCorner_theArrowAlreadyPointsAlongTheNextPath() {
        NavigationManager nav = navigateTo(3);
        nav.update(at(20, 0));
        nav.update(at(98, 0));                        // 2 m before node 2
        nav.setHeading(0);                            // facing north, the next path's direction
        assertEquals(0, Math.abs(HeadingMath.difference(0, nav.getArrowAngle())), 2);
    }

    @Test
    public void steps_moveThePositionAlongTheRoute() {
        NavigationManager nav = navigateTo(3);
        nav.update(at(20, 0));
        double before = nav.getDistanceLeft();
        for (int i = 0; i < 10; i++) {
            nav.onStep();
        }
        assertEquals(before - 10 * NavigationManager.STEP_M, nav.getDistanceLeft(), 0.05);
    }

    @Test
    public void guidance_onTheSquare() {
        NavigationManager nav = navigateTo(3);
        assertNull(nav.getGuidance());
        nav.update(at(20, 0));
        Guidance g = nav.getGuidance();
        assertEquals(Guidance.Action.LEFT, g.action);   // east, then north at node 2
        assertEquals(80, g.metres, 0.1);
    }

    // ------------------------------------------------------------ T19: SDD 6, the state diagram

    @Test
    public void destinationChosen_calculating_thenNavigating() {
        NavigationManager nav = navigateTo(3);
        assertEquals(NavState.CALCULATING_ROUTE, nav.getState());
        nav.update(at(20, 0));
        assertEquals(NavState.NAVIGATING, nav.getState());
    }

    @Test
    public void noRoute_backToWaitingForADestination() {
        NavigationManager nav = navigateTo(9);
        nav.update(at(20, 0));
        assertEquals(NavState.WAITING_FOR_DESTINATION, nav.getState());
    }

    @Test
    public void twoFixesWithin5Metres_arrived_andThatIsFinal() {
        NavigationManager nav = navigateTo(3);       // the entrance is node 3, at (100, 100)
        nav.update(at(100, 50));
        nav.update(at(100, 96));                     // 4 m away, once
        assertEquals(NavState.NAVIGATING, nav.getState());
        nav.update(at(101, 97));                     // and again
        assertEquals(NavState.ARRIVED, nav.getState());
        assertTrue(nav.isArrived());
        nav.update(at(100, 20));                     // walking away does not undo it
        assertEquals(NavState.ARRIVED, nav.getState());
        assertEquals(Arrays.asList(NavState.NAVIGATING, NavState.ARRIVED), states);
    }

    @Test
    public void sixMetresAway_isNotYetArrived() {
        NavigationManager nav = navigateTo(3);
        nav.update(at(100, 50));
        for (int i = 0; i < 5; i++) {
            nav.update(at(100, 94));                 // AT-06: "at the door, not before"
        }
        assertFalse(nav.isArrived());
    }

    @Test
    public void stepsAlone_neverAnnounceArrival() {
        NavigationManager nav = navigateTo(3);
        nav.update(at(100, 80));
        for (int i = 0; i < 100; i++) {
            nav.onStep();                            // 70 m of steps: past the door on paper
        }
        assertFalse(nav.isArrived());
    }

    @Test
    public void manualCalibration_pausesPositions_thenResumes() {
        NavigationManager nav = navigateTo(3);
        nav.update(at(20, 0));
        nav.startCalibration();
        assertEquals(NavState.MANUAL_CALIBRATION, nav.getState());
        nav.update(at(0, 60));                       // ignored while the user marks the spot
        assertEquals(2, nav.getRoute().nodes().get(1).id);
        nav.cancelCalibration();
        assertEquals(NavState.NAVIGATING, nav.getState());

        nav.startCalibration();
        nav.relocate(at(0, 60));                     // "position confirmed"
        assertEquals(NavState.NAVIGATING, nav.getState());
        assertEquals(4, nav.getRoute().nodes().get(1).id);
    }

    @Test
    public void markingThePositionAtTheDoor_arrivesAtOnce() {
        NavigationManager nav = navigateTo(3);
        nav.update(at(20, 0));
        nav.relocate(at(100, 97));                   // a marked point is trusted: no second fix
        assertTrue(nav.isArrived());
    }

    @Test
    public void userCancels_waitingForADestination() {
        NavigationManager nav = navigateTo(3);
        nav.update(at(20, 0));
        nav.cancel();
        assertEquals(NavState.WAITING_FOR_DESTINATION, nav.getState());
        nav.update(at(50, 0));                       // nothing more happens
        assertEquals(NavState.WAITING_FOR_DESTINATION, nav.getState());
    }

    // ------------------------------------------------------------ T18

    @Test
    public void targetBearing_isTheDirectionToTheNextNode_forTheArcoreAnchor() {
        NavigationManager nav = navigateTo(3);
        assertTrue(Float.isNaN(nav.getTargetBearing()));
        nav.update(at(20, 0));                      // next node 2, due east
        assertEquals(90, nav.getTargetBearing(), 0.5);
        nav.update(at(98, 0));                      // 2 m before the corner: already north
        assertEquals(0, Math.abs(HeadingMath.difference(0, nav.getTargetBearing())), 2);   // 2 m west of that path: 1.1°
    }

    @Test
    public void targetBearing_noneAfterArriving() {
        NavigationManager nav = navigateTo(3);
        nav.update(at(100, 50));
        nav.update(at(100, 99));
        nav.update(at(100, 99));
        assertTrue(Float.isNaN(nav.getTargetBearing()));
    }
}
