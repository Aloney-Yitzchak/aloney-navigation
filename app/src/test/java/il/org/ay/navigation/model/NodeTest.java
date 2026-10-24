package il.org.ay.navigation.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** SDD 9.2: distance with the equirectangular approximation, against values computed by hand. */
public class NodeTest {

    private static final double TOLERANCE_M = 0.01;

    @Test
    public void samePoint_isZero() {
        assertEquals(0, Node.distance(32.49, 34.95, 32.49, 34.95), TOLERANCE_M);
    }

    @Test
    public void oneThousandthDegreeNorth_is111m() {
        // R · π/180 · 0.001 = 111.195 m
        assertEquals(111.195, Node.distance(32.490, 34.95, 32.491, 34.95), TOLERANCE_M);
    }

    @Test
    public void oneThousandthDegreeEast_isShorterByCosLatitude() {
        // 111.195 · cos(32.49°) = 93.791 m
        assertEquals(93.791, Node.distance(32.49, 34.950, 32.49, 34.951), TOLERANCE_M);
    }

    @Test
    public void diagonal_isPythagoras() {
        double d = Node.distance(32.490, 34.950, 32.491, 34.951);
        assertEquals(Math.hypot(111.195, 93.791), d, TOLERANCE_M);
    }

    @Test
    public void distanceTo_isSymmetric() {
        Node a = new Node(1, 32.4901, 34.9502);
        Node b = new Node(2, 32.4907, 34.9511);
        assertEquals(a.distanceTo(b), b.distanceTo(a), 1e-9);
    }
}
