package il.org.ay.navigation.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.Manifest;
import android.content.Context;
import android.os.SystemClock;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import il.org.ay.navigation.R;
import il.org.ay.navigation.data.AppDatabase;
import il.org.ay.navigation.logic.LocationService;
import il.org.ay.navigation.model.CampusRoom;
import il.org.ay.navigation.model.Edge;
import il.org.ay.navigation.model.Node;

/**
 * AT-11 (FR-11): on a phone without ARCore the camera screen shows the arrow by the compass.
 * "Without ARCore" is forced, so the test means the same on any emulator or phone.
 */
@RunWith(AndroidJUnit4.class)
public class ArNavCompassModeTest {

    @Rule
    public GrantPermissionRule permissions = GrantPermissionRule.grant(
            Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION);

    private ActivityScenario<ArNavActivity> scenario;

    @Before
    public void setUp() {
        ArNavActivity.forceCompassMode = true;
        LocationService.useSourceForTesting(context -> new MapNavigationTest.TestGps());
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase.get(context).graphDao().replaceCampus(
                Arrays.asList(new Node(1, 32.51150, 35.00370), new Node(2, 32.51150, 35.00450),
                        new Node(3, 32.51230, 35.00450)),
                Arrays.asList(new Edge(1, 1, 2, 0), new Edge(2, 2, 3, 0)),
                Arrays.asList(new CampusRoom("42", "מתמטיקה", null, 3, "תשפ\"ז")));
        scenario = ActivityScenario.launch(ArNavActivity.navigateTo(context, "42"));
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        UiObject2 notNow = device.wait(Until.findObject(By.text(context.getString(R.string.steps_not_now))), 5_000);
        if (notNow != null) {
            notNow.click();
        }
    }

    @After
    public void tearDown() {
        TestScreens.finish(scenario);
        ArNavActivity.forceCompassMode = false;
        LocationService.useSourceForTesting(null);
    }

    @Test
    public void withoutArcore_theCompassArrowIsShown() {
        long end = SystemClock.uptimeMillis() + 5_000;
        AtomicBoolean ready = new AtomicBoolean();
        while (!ready.get() && SystemClock.uptimeMillis() < end) {
            scenario.onActivity(a -> ready.set(a.navigation() != null));
            SystemClock.sleep(100);
        }
        MapNavigationTest.TestGps.at(32.51150, 35.00400);
        scenario.onActivity(a -> a.feedHeading(0));
        SystemClock.sleep(600);

        AtomicBoolean ar = new AtomicBoolean(true);
        AtomicInteger arrow = new AtomicInteger();
        AtomicInteger frames = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> chip = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<il.org.ay.navigation.logic.ArStatus> status =
                new java.util.concurrent.atomic.AtomicReference<>();
        scenario.onActivity(a -> {
            ar.set(a.usesArCore());
            chip.set(((android.widget.TextView) a.findViewById(R.id.ar_mode)).getText().toString());
            status.set(a.arStatus());
            arrow.set(a.findViewById(R.id.ar_arrow).getVisibility());
            frames.set(a.recognizer().frameCount());
        });
        assertFalse("ARCore was used although it is off", ar.get());
        assertEquals(View.VISIBLE, arrow.get());          // FR-11: the arrow by the compass
        // T18b: the screen says it is in compass mode, and why
        assertEquals(il.org.ay.navigation.logic.ArStatus.FORCED_COMPASS, status.get());
        assertEquals("מצפן", chip.get());
        ArNavActivityTest.screenshot("20-compass-mode");
    }
}
