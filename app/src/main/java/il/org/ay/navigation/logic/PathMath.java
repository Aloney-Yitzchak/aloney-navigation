package il.org.ay.navigation.logic;

import androidx.annotation.NonNull;

import il.org.ay.navigation.model.LatLng;
import il.org.ay.navigation.model.Node;

/**
 * Snapping a GPS position to a path segment (T14, SDD 9.3):
 *
 * <pre>
 *   t  = clamp( (P − A)·(B − A) / |B − A|², 0, 1 )
 *   P' = A + t (B − A)
 * </pre>
 *
 * P' is the point of segment AB closest to P. The vectors are in metres on a flat
 * campus around A (the same flat-earth step as SDD 9.2), so the dot product is in m².
 * Pure Java: tested on the computer (PathMathTest).
 */
public final class PathMath {

    private PathMath() {
    }

    /** Where a point lands on a segment. */
    public static final class Projection {
        /** 0 = at A, 1 = at B. */
        public final double t;
        /** P', the closest point of the segment. */
        @NonNull
        public final LatLng point;
        /** |P − P'| in metres: how far the position is from the path. */
        public final double distance;

        Projection(double t, @NonNull LatLng point, double distance) {
            this.t = t;
            this.point = point;
            this.distance = distance;
        }
    }

    /** Projects P on segment AB. A segment of length zero projects to A. */
    @NonNull
    public static Projection project(@NonNull LatLng p, @NonNull LatLng a, @NonNull LatLng b) {
        // metres per degree around A
        double mLat = Math.toRadians(1) * Node.EARTH_RADIUS_M;
        double mLng = mLat * Math.cos(Math.toRadians(a.lat));
        // vectors from A, in metres
        double bx = (b.lng - a.lng) * mLng;
        double by = (b.lat - a.lat) * mLat;
        double px = (p.lng - a.lng) * mLng;
        double py = (p.lat - a.lat) * mLat;

        double lengthSquared = bx * bx + by * by;
        double t = lengthSquared == 0 ? 0 : (px * bx + py * by) / lengthSquared;
        t = Math.max(0, Math.min(1, t));

        LatLng onPath = new LatLng(a.lat + t * (b.lat - a.lat), a.lng + t * (b.lng - a.lng));
        double dx = px - t * bx;
        double dy = py - t * by;
        return new Projection(t, onPath, Math.sqrt(dx * dx + dy * dy));
    }

    /**
     * The direction from {@code from} to {@code to}, in degrees clockwise from true north
     * (SDD 9.4): θ = atan2(sin Δλ · cos φ₂, cos φ₁ · sin φ₂ − sin φ₁ · cos φ₂ · cos Δλ).
     */
    public static float bearing(@NonNull LatLng from, @NonNull LatLng to) {
        double phi1 = Math.toRadians(from.lat);
        double phi2 = Math.toRadians(to.lat);
        double dLambda = Math.toRadians(to.lng - from.lng);
        double y = Math.sin(dLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLambda);
        return HeadingMath.normalize((float) Math.toDegrees(Math.atan2(y, x)));
    }
}
