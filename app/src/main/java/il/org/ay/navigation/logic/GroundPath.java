package il.org.ay.navigation.logic;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import il.org.ay.navigation.model.LatLng;

/**
 * T18c: the arrows on the ground in AR mode, as a short path of chevrons along the route.
 *
 * <p>The route is in latitude/longitude; ARCore's world is in metres, with y up and −z where
 * the phone pointed when the session started. Three steps (SDD 9.4.1):
 * <ol>
 *   <li>{@link #enu}: a point of the route as metres east and north of the user
 *       (the flat-earth approximation of SDD 9.2, fine for a campus).</li>
 *   <li>{@link #toWorld}: east/north turned into ARCore's x/z with the north offset from
 *       {@link ArAlignment} (where ARCore's −z points, in degrees from north):
 *       <pre>x =  e·cos o − n·sin o,   z = −(n·cos o + e·sin o)</pre></li>
 *   <li>{@link #chevrons}: one chevron at each point, turned along the route there.</li>
 * </ol>
 * Pure Java: tested on the computer (GroundPathTest).
 */
public final class GroundPath {

    private static final double EARTH_RADIUS_M = 6_371_000;

    /** One chevron on the ground: where (relative to the user, in ARCore's x/z) and which way. */
    public static final class Chevron {
        public final float x;
        public final float z;
        /** The direction it points, a unit vector in ARCore's x/z. */
        public final float dx;
        public final float dz;
        /** Metres along the route from the user. */
        public final float along;

        Chevron(float x, float z, float dx, float dz, float along) {
            this.x = x;
            this.z = z;
            this.dx = dx;
            this.dz = dz;
            this.along = along;
        }

        /**
         * The angle for {@code Matrix.rotateM(m, angle, 0, 1, 0)} that turns a model pointing
         * along −z to point along (dx, dz).
         */
        public float yawDegrees() {
            return (float) Math.toDegrees(Math.atan2(-dx, -dz));
        }
    }

    private GroundPath() {
    }

    /** {east, north} in metres from {@code from} to {@code to}. */
    @NonNull
    public static float[] enu(@NonNull LatLng from, @NonNull LatLng to) {
        double lat = Math.toRadians((from.lat + to.lat) / 2);
        double east = Math.toRadians(to.lng - from.lng) * Math.cos(lat) * EARTH_RADIUS_M;
        double north = Math.toRadians(to.lat - from.lat) * EARTH_RADIUS_M;
        return new float[]{(float) east, (float) north};
    }

    /** East/north metres as ARCore's {x, z}, for a world whose −z points {@code offsetDeg} from north. */
    @NonNull
    public static float[] toWorld(float east, float north, float offsetDeg) {
        double o = Math.toRadians(offsetDeg);
        double cos = Math.cos(o);
        double sin = Math.sin(o);
        return new float[]{(float) (east * cos - north * sin), (float) -(north * cos + east * sin)};
    }

    /**
     * The chevrons for a path given as east/north points (metres from the user), each with the
     * point a little further along that it points to: {@code points[i]} and {@code ahead[i]}.
     * A pair closer than 5 cm (the end of the route) gets no chevron.
     */
    @NonNull
    public static List<Chevron> chevrons(@NonNull float[][] points, @NonNull float[][] ahead,
                                         @NonNull float[] along, float offsetDeg) {
        List<Chevron> list = new ArrayList<>();
        for (int i = 0; i < points.length; i++) {
            float[] p = toWorld(points[i][0], points[i][1], offsetDeg);
            float[] q = toWorld(ahead[i][0], ahead[i][1], offsetDeg);
            float dx = q[0] - p[0];
            float dz = q[1] - p[1];
            float length = (float) Math.hypot(dx, dz);
            if (length < 0.05f) {
                continue;
            }
            list.add(new Chevron(p[0], p[1], dx / length, dz / length, along[i]));
        }
        return list;
    }

    /** Where the chevrons are, metres along the route from the user. */
    public static final float[] ALONG = {1.5f, 2.7f, 3.9f, 5.1f, 6.3f};
    /** Each chevron points to the route this far after it. */
    public static final float STEP = 0.5f;

    /**
     * How solid a chevron is at {@code distance} metres from the phone: faint right under the
     * phone (so it does not cover the path), solid from 1.5 m, fading out after 7 m.
     */
    public static float alpha(float distance) {
        if (distance < 0.6f) {
            return 0f;
        }
        if (distance < 1.5f) {
            return 0.9f * (distance - 0.6f) / 0.9f;
        }
        if (distance < 7f) {
            return 0.9f;
        }
        return Math.max(0f, 0.9f * (1f - (distance - 7f) / 3f));
    }
}
