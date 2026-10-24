package il.org.ay.navigation.logic;

import android.content.Context;
import android.location.Location;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * Positions from Google's Fused Location: GPS plus Wi-Fi and cell, once a second while
 * navigating. High accuracy, because a campus path is only a few metres wide.
 */
public class FusedPositionSource implements PositionSource {

    static final long INTERVAL_MS = 1000;

    private final FusedLocationProviderClient client;
    private LocationCallback locationCallback;

    public FusedPositionSource(@NonNull Context context) {
        client = LocationServices.getFusedLocationProviderClient(context);
    }

    @Override
    public void start(@NonNull Callback callback) throws SecurityException {
        stop();
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
                .setMinUpdateIntervalMillis(INTERVAL_MS / 2)
                .build();
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                for (Location l : result.getLocations()) {
                    callback.onFix(new Fix(l.getLatitude(), l.getLongitude(),
                            l.hasAccuracy() ? l.getAccuracy() : 30f, l.getTime()));
                }
            }
        };
        client.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
    }

    @Override
    public void stop() {
        if (locationCallback != null) {
            client.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }
    }
}
