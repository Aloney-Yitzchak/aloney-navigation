package il.org.ay.navigation.logic;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * Where raw positions come from. In the app it is Fused Location (FusedPositionSource);
 * tests give LocationService their own source, so no GPS is needed to test it.
 */
public interface PositionSource {

    /** One raw position, as the phone reports it. */
    final class Fix {
        public final double lat;
        public final double lng;
        /** Radius in metres the phone is fairly sure of. */
        public final float accuracy;
        public final long timeMs;

        public Fix(double lat, double lng, float accuracy, long timeMs) {
            this.lat = lat;
            this.lng = lng;
            this.accuracy = accuracy;
            this.timeMs = timeMs;
        }
    }

    interface Callback {
        void onFix(@NonNull Fix fix);
    }

    interface Factory {
        @NonNull
        PositionSource create(@NonNull Context context);
    }

    /** Starts sending fixes on the main thread. Needs the location permission. */
    void start(@NonNull Callback callback) throws SecurityException;

    /** Stops the fixes: the GPS can turn off (NFR-08). Safe to call twice. */
    void stop();
}
