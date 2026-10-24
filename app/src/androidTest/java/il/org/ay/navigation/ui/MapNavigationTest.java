package il.org.ay.navigation.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import il.org.ay.navigation.R;
import il.org.ay.navigation.data.AppDatabase;
import il.org.ay.navigation.data.Route;
import il.org.ay.navigation.logic.LocationService;
import il.org.ay.navigation.logic.NavigationManager;
import il.org.ay.navigation.logic.PositionSource;
import il.org.ay.navigation.model.CampusRoom;
import il.org.ay.navigation.model.Edge;
import il.org.ay.navigation.model.Node;

/**
 * T15 / AT-05 on the emulator, with a GPS driven by the test: choosing a room opens the
 * map with the route from where the user stands, the sheet shows the distance, and
 * walking off the route recalculates it. (The real AT-05 is done on campus with a phone.)
 *
 * The paths form a rectangle, 75 m east–west and 89 m north–south:
 *
 * <pre>
 *   4 ──── 3 (room 42)
 *   │      │
 *   1 ──── 2
 * </pre>
 */
@RunWith(AndroidJUnit4.class)
public class MapNavigationTest {

    @Rule
    public GrantPermissionRule location = GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION);

    /** The GPS of this test. */
    static class TestGps implements PositionSource {
        static Callback callback;

        @Override
        public void start(@NonNull Callback cb) {
            callback = cb;
        }

        @Override
        public void stop() {
            callback = null;
        }

        static void at(double lat, double lng) {
            long end = SystemClock.uptimeMillis() + 5_000;
            while (callback == null && SystemClock.uptimeMillis() < end) {
                SystemClock.sleep(50);
            }
            assertNotNull("the map did not start the GPS", callback);
            Fix fix = new Fix(lat, lng, 4f, System.currentTimeMillis());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> callback.onFix(fix));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
    }

    private ActivityScenario<MapActivity> scenario;

    @Before
    public void setUp() {
        LocationService.useSourceForTesting(context -> new TestGps());
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase.get(context).graphDao().replaceCampus(
                Arrays.asList(new Node(1, 32.51150, 35.00370), new Node(2, 32.51150, 35.00450),
                        new Node(3, 32.51230, 35.00450), new Node(4, 32.51230, 35.00370)),
                Arrays.asList(new Edge(1, 1, 2, 0), new Edge(2, 2, 3, 0), new Edge(3, 1, 4, 0), new Edge(4, 4, 3, 0)),
                Arrays.asList(new CampusRoom("42", "מתמטיקה", "רכזת שכבה ט'", 3, "תשפ\"ז")));
        scenario = ActivityScenario.launch(MapActivity.navigateTo(context, "42"));
    }

    @After
    public void tearDown() {
        scenario.close();
        LocationService.useSourceForTesting(null);
    }

    private NavigationManager nav() {
        AtomicReference<NavigationManager> n = new AtomicReference<>();
        long end = SystemClock.uptimeMillis() + 5_000;
        while (SystemClock.uptimeMillis() < end) {
            scenario.onActivity(a -> n.set(a.navigation()));
            if (n.get() != null) {
                return n.get();
            }
            SystemClock.sleep(50);
        }
        throw new AssertionError("the destination was not loaded");
    }

    @Test
    public void routeFromWhereTheUserStands_andTheDistanceInTheSheet() {
        NavigationManager nav = nav();
        onView(withText("מתמטיקה")).check(matches(isDisplayed()));
        onView(withText(R.string.map_waiting_for_location)).check(matches(isDisplayed()));

        // on path 1–2, 28 m from 1: via 2 = 47 + 89 = 136 m; via 1 = 28 + 89 + 75 = 192 m
        TestGps.at(32.51150, 35.00400);
        Route route = nav.getRoute();
        assertNotNull(route);
        assertEquals(2, route.nodes().get(1).id);        // towards 2, not back to 1
        assertEquals(3, route.destination().id);
        onView(withId(R.id.map_distance)).check(matches(isDisplayed()));
        SystemClock.sleep(2500);                          // tiles and the zoom to the route
        screenshot("12-map-route");
    }

    @Test
    public void walkingTheOtherWay_recalculatesTheRoute() {
        NavigationManager nav = nav();
        TestGps.at(32.51150, 35.00400);
        assertEquals(2, nav.getRoute().nodes().get(1).id);
        // the user walks north up path 1–4 instead, more than 15 m off the route
        TestGps.at(32.51190, 35.00372);
        TestGps.at(32.51210, 35.00372);
        assertEquals(1, nav.getRecalculations());
        assertEquals(4, nav.getRoute().nodes().get(1).id);   // now via 4
        assertTrue(nav.getDistanceLeft() > 0);
        SystemClock.sleep(1500);
        screenshot("13-map-recalculated");
    }

    @Test
    public void atHome_offCampus_thenMarkingThePosition_givesARoute() {
        NavigationManager nav = nav();
        TestGps.at(32.08, 34.78);                          // kilometres away
        assertTrue(nav.isOffCampus());
        onView(withText(R.string.map_off_campus)).check(matches(isDisplayed()));

        // T23 / AT-08: open the calibration, put the pin on path 1–2, confirm
        scenario.onActivity(MapActivity::openCalibrationForTest);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        AtomicReference<CalibrationDialog> dialog = new AtomicReference<>();
        long end = SystemClock.uptimeMillis() + 5_000;
        while (dialog.get() == null && SystemClock.uptimeMillis() < end) {
            scenario.onActivity(a -> dialog.set(a.calibrationDialog()));
            SystemClock.sleep(100);
        }
        assertNotNull("the calibration did not open", dialog.get());
        SystemClock.sleep(1500);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> dialog.get().moveTo(new il.org.ay.navigation.model.LatLng(32.51150, 35.00410)));
        SystemClock.sleep(1500);
        screenshot("14-calibration");
        onView(withId(R.id.calibration_confirm)).perform(androidx.test.espresso.action.ViewActions.click());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertTrue(!nav.isOffCampus());
        assertNotNull("a route from the marked point", nav.getRoute());
        assertEquals(2, nav.getRoute().nodes().get(1).id);

        // the GPS at home moves on, and the position moves with it, on campus
        TestGps.at(32.08, 34.78005);
        assertTrue(!nav.isOffCampus());
        SystemClock.sleep(2000);
        screenshot("15-calibrated");
    }

    private static void screenshot(String name) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        shell("mkdir -p /sdcard/Pictures/aloney");
        shell("screencap -p /sdcard/Pictures/aloney/" + name + ".png");
    }

    private static void shell(String command) {
        ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(command);
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
            byte[] buffer = new byte[1024];
            while (in.read(buffer) != -1) {
                // wait for the command to finish
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
