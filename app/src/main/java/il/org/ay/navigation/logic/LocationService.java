package il.org.ay.navigation.logic;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import il.org.ay.navigation.data.CampusGraph;
import il.org.ay.navigation.data.CampusRepository;
import il.org.ay.navigation.model.LatLng;

/**
 * The user's position while navigating (T11, FR-03, SDD 4: logic.LocationService).
 *
 * <p>A <b>bound</b> Service (rubric 6.3): the map and camera screens bind to it while they
 * are open. When the last one unbinds, Android destroys the service and the GPS stops
 * (NFR-08). Each fix is smoothed (LocationSmoother) and snapped to the nearest path
 * (CampusGraph, SDD 9.3), then handed to the listeners.
 *
 * <p>Privacy (NFR-09): positions live only in memory, inside this service and its
 * listeners. Nothing is written to storage, logged, or sent anywhere.
 */
public class LocationService extends Service {

    /** What the screens receive for every fix. */
    public static final class Position {
        /** The smoothed position. */
        @NonNull
        public final LatLng smoothed;
        /** The same position moved onto the nearest path; equals {@code smoothed} before the map is loaded. */
        @NonNull
        public final LatLng onPath;
        /** Metres between the two: how far from any path the user is. */
        public final double fromPath;
        /** Radius in metres the smoother is fairly sure of. */
        public final double accuracy;

        Position(@NonNull LatLng smoothed, @NonNull LatLng onPath, double fromPath, double accuracy) {
            this.smoothed = smoothed;
            this.onPath = onPath;
            this.fromPath = fromPath;
            this.accuracy = accuracy;
        }
    }

    public interface Listener {
        void onPosition(@NonNull Position position);
    }

    /** Where the path graph comes from; tests give their own graph. */
    interface GraphLoader {
        void load(@NonNull Context context, @NonNull CampusRepository.Listener<CampusGraph> listener);
    }

    /** What screens get when they bind. */
    public class LocalBinder extends Binder {
        @NonNull
        public LocationService getService() {
            return LocationService.this;
        }
    }

    // replaced in tests
    static PositionSource.Factory sourceFactory = FusedPositionSource::new;
    static GraphLoader graphLoader = (context, listener) -> CampusRepository.get(context).loadGraph(listener);

    private final IBinder binder = new LocalBinder();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final LocationSmoother smoother = new LocationSmoother();
    private PositionSource source;
    @Nullable
    private CampusGraph graph;
    @Nullable
    private Position last;
    private boolean running;

    /** Starts the service if needed and binds to it; unbind with {@code context.unbindService}. */
    public static boolean bind(@NonNull Context context, @NonNull android.content.ServiceConnection connection) {
        return context.bindService(new Intent(context, LocationService.class), connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        source = sourceFactory.create(this);
        graphLoader.load(this, g -> graph = g);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        startUpdates();
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // the last screen left: stop the GPS now, even before Android destroys the service
        stopUpdates();
        listeners.clear();
        return false;
    }

    @Override
    public void onDestroy() {
        stopUpdates();
        super.onDestroy();
    }

    private void startUpdates() {
        if (running || !hasPermission()) {
            return;  // without the permission the screens still work, with no position (AT-12)
        }
        try {
            source.start(this::onFix);
            running = true;
        } catch (SecurityException e) {
            running = false;
        }
    }

    private void stopUpdates() {
        if (source != null) {
            source.stop();
        }
        running = false;
    }

    private boolean hasPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void onFix(@NonNull PositionSource.Fix fix) {
        LatLng smoothed = smooth(fix);
        if (smoothed != null) {
            publish(smoothed);
        }
    }

    /** SDD 4: smooth(Location). Null while there is no usable fix yet. */
    @Nullable
    public LatLng smooth(@NonNull PositionSource.Fix fix) {
        return smoother.add(fix.lat, fix.lng, fix.accuracy, fix.timeMs);
    }

    /** SDD 4: snapToPath(LatLng). The nearest point on a path, or {@code p} before the map is loaded. */
    @NonNull
    public LatLng snapToPath(@NonNull LatLng p) {
        CampusGraph g = graph;
        CampusGraph.Snap snap = g == null ? null : g.snapToPath(p);
        return snap == null ? p : snap.projection.point;
    }

    /** SDD 4: calibrate(LatLng). The user marked where they are (FR-08, T23). */
    public void calibrate(@NonNull LatLng p) {
        // wall-clock time, like Location.getTime() on the fixes that follow
        smoother.calibrate(p, System.currentTimeMillis());
        publish(p);
    }

    private void publish(@NonNull LatLng smoothed) {
        LatLng onPath = snapToPath(smoothed);
        last = new Position(smoothed, onPath, smoothed.distanceTo(onPath), smoother.accuracy());
        for (Listener l : listeners) {
            l.onPosition(last);
        }
    }

    /** SDD 4: addListener. The listener gets the last position right away, if there is one. */
    public void addListener(@NonNull Listener listener) {
        listeners.add(listener);
        if (last != null) {
            listener.onPosition(last);
        }
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    /** True while fixes are coming in: bound and allowed. */
    public boolean isRunning() {
        return running;
    }

    @Nullable
    public Position getLast() {
        return last;
    }
}
