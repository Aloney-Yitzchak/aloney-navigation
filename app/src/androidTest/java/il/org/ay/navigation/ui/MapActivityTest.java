package il.org.ay.navigation.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.GroundOverlay;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polyline;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import il.org.ay.navigation.R;
import il.org.ay.navigation.data.AppDatabase;
import il.org.ay.navigation.model.CampusRoom;
import il.org.ay.navigation.model.Edge;
import il.org.ay.navigation.model.LatLng;
import il.org.ay.navigation.model.Node;

/**
 * T29 on the emulator: the campus map with its layers in the right order, the
 * OpenStreetMap credit, and a school-plan image placed by four corners (GroundOverlay).
 */
@RunWith(AndroidJUnit4.class)
public class MapActivityTest {

    private static final long WAIT_MS = 10_000;
    private ActivityScenario<MapActivity> scenario;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        // three points on the real campus, two paths between them
        AppDatabase.get(context).graphDao().replaceCampus(
                Arrays.asList(new Node(1, 32.51148, 35.00369), new Node(2, 32.51200, 35.00420),
                        new Node(3, 32.51260, 35.00380)),
                Arrays.asList(new Edge(1, 1, 2, 0), new Edge(2, 2, 3, 0)),
                Arrays.asList(new CampusRoom("42", "מתמטיקה", null, 3, "תשפ\"ז")));
        scenario = ActivityScenario.launch(MapActivity.class);
    }

    @After
    public void tearDown() {
        scenario.close();
    }

    private List<Overlay> overlays() {
        AtomicReference<List<Overlay>> o = new AtomicReference<>();
        scenario.onActivity(a -> o.set(new java.util.ArrayList<>(a.layers().overlays())));
        return o.get();
    }

    private int count(Class<?> type) {
        int n = 0;
        for (Overlay o : overlays()) {
            if (type.isInstance(o)) {
                n++;
            }
        }
        return n;
    }

    private void waitForPaths() {
        long end = SystemClock.uptimeMillis() + WAIT_MS;
        while (SystemClock.uptimeMillis() < end && count(Polyline.class) < 5) {
            SystemClock.sleep(100);
        }
        // each path is a grey edge and a white line, plus the route line
        assertEquals("2 paths × 2 + the route", 5, count(Polyline.class));
    }

    @Test
    public void campusPaths_areDrawn_withTheOsmCreditOnTop() {
        waitForPaths();
        List<Overlay> o = overlays();
        assertTrue("the credit is the top layer", o.get(o.size() - 1) instanceof CopyrightOverlay);
        assertEquals(0, count(GroundOverlay.class));          // no plan until T28
        scenario.onActivity(a -> assertEquals(View.GONE, a.findViewById(R.id.map_placeholder).getVisibility()));
        SystemClock.sleep(3000);                              // let the tiles arrive
        screenshot("10-map");
    }

    @Test
    public void planImage_isPlacedByFourCorners_underThePaths() {
        waitForPaths();
        LatLng tl = new LatLng(32.5130, 35.0030);
        LatLng tr = new LatLng(32.5132, 35.0048);   // the top edge slopes: the image is turned
        LatLng br = new LatLng(32.5112, 35.0050);
        LatLng bl = new LatLng(32.5110, 35.0032);
        scenario.onActivity(a -> {
            Bitmap plan = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888);
            plan.eraseColor(Color.rgb(46, 125, 50));
            a.layers().showPlan(plan, tl, tr, br, bl);
        });
        List<Overlay> o = overlays();
        assertTrue("the plan is the bottom layer", o.get(0) instanceof GroundOverlay);
        GroundOverlay g = (GroundOverlay) o.get(0);
        assertEquals(tr.lat, g.getTopRight().getLatitude(), 1e-9);
        assertEquals(bl.lng, g.getBottomLeft().getLongitude(), 1e-9);
        assertFalse(g.getTransparency() == 0);                 // half see-through
        SystemClock.sleep(2000);
        screenshot("11-map-plan");
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
