package il.org.ay.navigation.model;

import androidx.annotation.NonNull;

import java.util.Locale;

/** A point on the campus in degrees (SDD 4: the LatLng in the class diagram). Immutable. */
public final class LatLng {

    public final double lat;
    public final double lng;

    public LatLng(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    public static LatLng of(@NonNull Node node) {
        return new LatLng(node.lat, node.lng);
    }

    /** Distance in metres (SDD 9.2). */
    public double distanceTo(@NonNull LatLng other) {
        return Node.distance(lat, lng, other.lat, other.lng);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "(%.7f, %.7f)", lat, lng);
    }
}
