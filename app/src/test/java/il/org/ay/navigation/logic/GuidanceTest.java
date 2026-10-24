package il.org.ay.navigation.logic;

import static il.org.ay.navigation.logic.PathMathTest.at;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import il.org.ay.navigation.data.CampusGraph;
import il.org.ay.navigation.data.Route;
import il.org.ay.navigation.model.Edge;
import il.org.ay.navigation.model.LatLng;
import il.org.ay.navigation.model.Node;

/** T17: the instruction under the camera. Points are metres east (x) and north (y). */
public class GuidanceTest {

    /** A route through the given points, in order. */
    private static Route route(double[]... xy) {
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < xy.length; i++) {
            LatLng p = at(xy[i][0], xy[i][1]);
            nodes.add(new Node(i + 1, p.lat, p.lng));
            if (i > 0) {
                edges.add(new Edge(i, i, i + 1, 0));
            }
        }
        return new CampusGraph(nodes, edges).findPath(1, xy.length);
    }

    @Test
    public void eastThenNorth_isALeftTurn() {
        Guidance g = Guidance.at(route(new double[]{0, 0}, new double[]{100, 0}, new double[]{100, 100}), at(20, 0));
        assertEquals(Guidance.Action.LEFT, g.action);
        assertEquals(80, g.metres, 0.1);
        assertEquals(1, g.step);
        assertEquals(2, g.steps);
    }

    @Test
    public void eastThenSouth_isARightTurn() {
        Guidance g = Guidance.at(route(new double[]{0, 0}, new double[]{100, 0}, new double[]{100, -100}), at(20, 0));
        assertEquals(Guidance.Action.RIGHT, g.action);
    }

    @Test
    public void nodesWhereThePathGoesOn_areSkipped_toTheRealTurn() {
        Route r = route(new double[]{0, 0}, new double[]{50, 5}, new double[]{100, 0}, new double[]{100, 100});
        Guidance g = Guidance.at(r, at(10, 1));
        assertEquals(Guidance.Action.LEFT, g.action);          // the bend at (50,5) is only ~11°
        assertEquals(40.3 + 50.2, g.metres, 0.5);              // to the corner at (100,0)
    }

    @Test
    public void lastStretch_saysTheDestinationIsAhead() {
        Guidance g = Guidance.at(route(new double[]{0, 0}, new double[]{100, 0}, new double[]{100, 100}), at(100, 30));
        assertEquals(Guidance.Action.ARRIVE, g.action);
        assertEquals(70, g.metres, 0.1);
        assertEquals(2, g.step);
    }

    @Test
    public void aSharpReturn_isTurnBack() {
        Guidance g = Guidance.at(route(new double[]{0, 0}, new double[]{100, 0}, new double[]{10, 8}), at(20, 0));
        assertEquals(Guidance.Action.TURN_BACK, g.action);
    }

    @Test
    public void classify_borders() {
        assertEquals(Guidance.Action.STRAIGHT, Guidance.classify(29));
        assertEquals(Guidance.Action.RIGHT, Guidance.classify(31));
        assertEquals(Guidance.Action.LEFT, Guidance.classify(-90));
        assertEquals(Guidance.Action.TURN_BACK, Guidance.classify(-151));
    }
}
