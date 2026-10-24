package il.org.ay.navigation.logic;

import static il.org.ay.navigation.logic.PathMathTest.at;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import il.org.ay.navigation.data.CampusGraph;
import il.org.ay.navigation.model.Edge;
import il.org.ay.navigation.model.LatLng;
import il.org.ay.navigation.model.Node;

/**
 * T11: LocationService with a fake GPS and a small map. Checks the Service rules:
 * positions only while bound and allowed (NFR-08, AT-12), smoothing, snapping to the path.
 */
@RunWith(RobolectricTestRunner.class)
public class LocationServiceTest {

    /** A GPS we drive by hand. */
    static class FakeSource implements PositionSource {
        Callback callback;
        int starts;
        int stops;

        @Override
        public void start(@NonNull Callback callback) {
            this.callback = callback;
            starts++;
        }

        @Override
        public void stop() {
            callback = null;
            stops++;
        }

        void send(double x, double y, float accuracy, long seconds) {
            LatLng p = at(x, y);
            callback.onFix(new Fix(p.lat, p.lng, accuracy, seconds * 1000));
        }
    }

    private final FakeSource gps = new FakeSource();
    private final List<LocationService.Position> received = new ArrayList<>();
    private PositionSource.Factory realFactory;
    private LocationService.GraphLoader realLoader;

    private static Node node(int id, double x, double y) {
        LatLng p = at(x, y);
        return new Node(id, p.lat, p.lng);
    }

    @Before
    public void setUp() {
        realFactory = LocationService.sourceFactory;
        realLoader = LocationService.graphLoader;
        LocationService.sourceFactory = context -> gps;
        // one path, 100 m going east
        CampusGraph graph = new CampusGraph(Arrays.asList(node(1, 0, 0), node(2, 100, 0)),
                Arrays.asList(new Edge(1, 1, 2, 0)));
        LocationService.graphLoader = (context, listener) -> listener.onResult(graph);
    }

    @After
    public void tearDown() {
        LocationService.sourceFactory = realFactory;
        LocationService.graphLoader = realLoader;
    }

    private void allowLocation() {
        Application app = ApplicationProvider.getApplicationContext();
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    private LocationService bind(ServiceController<LocationService> controller) {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), LocationService.class);
        LocationService.LocalBinder binder = (LocationService.LocalBinder) controller.get().onBind(intent);
        LocationService service = binder.getService();
        service.addListener(received::add);
        return service;
    }

    @Test
    public void bound_andAllowed_positionsAreSmoothedAndSnappedToThePath() {
        allowLocation();
        ServiceController<LocationService> controller = Robolectric.buildService(LocationService.class).create();
        LocationService service = bind(controller);
        assertTrue(service.isRunning());

        gps.send(40, 6, 5, 0);                         // 6 m north of the path
        assertEquals(1, received.size());
        LocationService.Position p = received.get(0);
        assertEquals(0, p.onPath.distanceTo(at(40, 0)), 0.05);
        assertEquals(6, p.fromPath, 0.05);
    }

    @Test
    public void lastScreenLeaves_theGpsStops() {
        allowLocation();
        ServiceController<LocationService> controller = Robolectric.buildService(LocationService.class).create();
        LocationService service = bind(controller);
        assertEquals(1, gps.starts);

        controller.get().onUnbind(new Intent());       // NFR-08
        assertFalse(service.isRunning());
        assertEquals(1, gps.stops);
        assertNull(gps.callback);
        controller.destroy();
    }

    @Test
    public void withoutPermission_noGps_andNoCrash() {
        ServiceController<LocationService> controller = Robolectric.buildService(LocationService.class).create();
        LocationService service = bind(controller);
        assertFalse(service.isRunning());               // AT-12: the app still works, without a position
        assertEquals(0, gps.starts);
    }

    @Test
    public void calibrate_isPublishedRightAway_andWinsOverAVagueFix() {
        allowLocation();
        ServiceController<LocationService> controller = Robolectric.buildService(LocationService.class).create();
        LocationService service = bind(controller);
        gps.send(10, 0, 20, 0);
        service.calibrate(at(80, 0));
        assertEquals(0, received.get(received.size() - 1).smoothed.distanceTo(at(80, 0)), 1e-6);
    }

    @Test
    public void aNewListener_getsTheLastPositionAtOnce() {
        allowLocation();
        ServiceController<LocationService> controller = Robolectric.buildService(LocationService.class).create();
        LocationService service = bind(controller);
        gps.send(50, 0, 5, 0);
        List<LocationService.Position> late = new ArrayList<>();
        service.addListener(late::add);
        assertEquals(1, late.size());
        assertNotNull(service.getLast());
    }

    @Test
    public void afterCalibration_laterFixesMoveFromTheMark() {
        allowLocation();
        ServiceController<LocationService> controller = Robolectric.buildService(LocationService.class).create();
        LocationService service = bind(controller);
        gps.send(5000, 0, 5, 0);                        // the GPS says: 5 km away (at home)
        service.calibrate(at(40, 0));                   // the user: "I am here, on the path"
        assertTrue(service.isCalibrated());
        gps.send(5010, 0, 5, 1);                        // walked 10 m east at home
        LatLng p = received.get(received.size() - 1).smoothed;
        assertTrue("still near the mark: " + p.distanceTo(at(40, 0)), p.distanceTo(at(40, 0)) < 12);
        assertTrue(p.distanceTo(at(5000, 0)) > 4000);

        service.clearCalibration();
        assertFalse(service.isCalibrated());
    }
}
