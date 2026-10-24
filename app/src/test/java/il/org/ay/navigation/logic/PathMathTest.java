package il.org.ay.navigation.logic;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import il.org.ay.navigation.model.LatLng;

/** T14, SDD 9.3: the projection of a position on a path segment. */
public class PathMathTest {

    /** A point x metres east and y metres north of (32.5, 35.0). */
    public static LatLng at(double x, double y) {
        double mLat = Math.toRadians(1) * 6_371_000;
        return new LatLng(32.5 + y / mLat, 35.0 + x / (mLat * Math.cos(Math.toRadians(32.5))));
    }

    private static final LatLng A = at(0, 0);
    private static final LatLng B = at(100, 0);   // a 100 m path going east

    @Test
    public void besideTheMiddle_landsHalfWay() {
        PathMath.Projection p = PathMath.project(at(50, 10), A, B);
        assertEquals(0.5, p.t, 1e-6);
        assertEquals(10, p.distance, 0.01);
        assertEquals(0, p.point.distanceTo(at(50, 0)), 0.01);
    }

    @Test
    public void onThePath_isZeroMetresAway() {
        PathMath.Projection p = PathMath.project(at(30, 0), A, B);
        assertEquals(0.3, p.t, 1e-6);
        assertEquals(0, p.distance, 0.01);
    }

    @Test
    public void beforeA_isClampedToA() {
        PathMath.Projection p = PathMath.project(at(-30, 40), A, B);
        assertEquals(0, p.t, 0);
        assertEquals(50, p.distance, 0.01);           // 3-4-5 triangle
        assertEquals(0, p.point.distanceTo(A), 1e-6);
    }

    @Test
    public void pastB_isClampedToB() {
        PathMath.Projection p = PathMath.project(at(130, -40), A, B);
        assertEquals(1, p.t, 0);
        assertEquals(50, p.distance, 0.01);
    }

    @Test
    public void slantedPath() {
        // path from (0,0) to (60,80), 100 m long; (60,0) projects at t = 3600/10000 = 0.36
        PathMath.Projection p = PathMath.project(at(60, 0), A, at(60, 80));
        assertEquals(0.36, p.t, 1e-4);
        assertEquals(48, p.distance, 0.02);           // 60 · 80 / 100
    }

    @Test
    public void zeroLengthSegment_projectsToItsPoint() {
        PathMath.Projection p = PathMath.project(at(3, 4), A, A);
        assertEquals(0, p.t, 0);
        assertEquals(5, p.distance, 0.01);
    }

    @Test
    public void bearing_theFourDirections() {
        assertEquals(0, PathMath.bearing(A, at(0, 100)), 0.01);     // north
        assertEquals(90, PathMath.bearing(A, at(100, 0)), 0.01);    // east
        assertEquals(180, PathMath.bearing(A, at(0, -100)), 0.01);  // south
        assertEquals(270, PathMath.bearing(A, at(-100, 0)), 0.01);  // west
        assertEquals(45, PathMath.bearing(A, at(100, 100)), 0.05);  // north-east
    }
}
