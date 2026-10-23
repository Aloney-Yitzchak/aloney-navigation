package il.org.ay.navigation.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * A point on the campus path graph: a room entrance or a junction between paths.
 * Whether it is an entrance, and its label, live only in campus-data/nodes.csv.
 * Room table {@code nodes}; Firestore collection {@code nodes} (doc id = id).
 */
@Entity(tableName = "nodes")
public class Node {

    /** Earth radius in metres (SDD 9.2). */
    public static final double EARTH_RADIUS_M = 6_371_000;

    @PrimaryKey
    public int id;

    public double lat;
    public double lng;

    /** Required by Room and Firestore. */
    public Node() {
    }

    @Ignore
    public Node(int id, double lat, double lng) {
        this.id = id;
        this.lat = lat;
        this.lng = lng;
    }

    /** Distance in metres to another node. */
    public double distanceTo(Node other) {
        return distance(lat, lng, other.lat, other.lng);
    }

    /**
     * Distance in metres between two points, treating the campus as flat
     * (equirectangular approximation, SDD 9.2). Accurate to well under a
     * centimetre over a few hundred metres.
     */
    public static double distance(double lat1, double lng1, double lat2, double lng2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = phi2 - phi1;
        double dLambda = Math.toRadians(lng2 - lng1);
        double x = dLambda * Math.cos((phi1 + phi2) / 2);
        return EARTH_RADIUS_M * Math.sqrt(x * x + dPhi * dPhi);
    }
}
