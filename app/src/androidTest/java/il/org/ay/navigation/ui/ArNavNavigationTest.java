package il.org.ay.navigation.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import il.org.ay.navigation.R;
import il.org.ay.navigation.data.AppDatabase;
import il.org.ay.navigation.logic.LocationService;
import il.org.ay.navigation.model.CampusRoom;
import il.org.ay.navigation.model.Edge;
import il.org.ay.navigation.model.Node;

/**
 * T17 on the emulator: the arrow and the instruction over the camera, with the test GPS of
 * MapNavigationTest on the same rectangle of paths (room 42 at the north-east corner).
 * NFR-01: the screen is redrawn at least 10 times a second.
 */
@RunWith(AndroidJUnit4.class)
public class ArNavNavigationTest {

    @Rule
    public GrantPermissionRule permissions = GrantPermissionRule.grant(
            Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION);

    private ActivityScenario<ArNavActivity> scenario;

    @Before
    public void setUp() {
        LocationService.useSourceForTesting(context -> new MapNavigationTest.TestGps());
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase.get(context).graphDao().replaceCampus(
                Arrays.asList(new Node(1, 32.51150, 35.00370), new Node(2, 32.51150, 35.00450),
                        new Node(3, 32.51230, 35.00450), new Node(4, 32.51230, 35.00370)),
                Arrays.asList(new Edge(1, 1, 2, 0), new Edge(2, 2, 3, 0), new Edge(3, 1, 4, 0), new Edge(4, 4, 3, 0)),
                Arrays.asList(new CampusRoom("42", "מתמטיקה", "רכזת שכבה ט'", 3, "תשפ\"ז")));
        scenario = ActivityScenario.launch(ArNavActivity.navigateTo(context, "42"));
        // a fresh install asks once for the step permission (decision #13): not now
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        UiObject2 notNow = device.wait(Until.findObject(By.text(context.getString(R.string.steps_not_now))), 5_000);
        if (notNow != null) {
            notNow.click();
            device.waitForIdle();
        }
    }

    @After
    public void tearDown() {
        TestScreens.finish(scenario);
        LocationService.useSourceForTesting(null);
    }

    private int frames() {
        AtomicInteger n = new AtomicInteger();
        scenario.onActivity(a -> n.set(a.framesDrawn()));
        return n.get();
    }

    @Test
    public void atLeastTenUpdatesASecond() {
        SystemClock.sleep(1000);
        int before = frames();
        SystemClock.sleep(2000);
        int perSecond = (frames() - before) / 2;
        assertTrue("only " + perSecond + " updates a second", perSecond >= 10);   // NFR-01
    }

    @Test
    public void arrowAndInstruction_forTheNextTurn() {
        long end = SystemClock.uptimeMillis() + 5_000;
        while (SystemClock.uptimeMillis() < end) {
            AtomicReference<Object> nav = new AtomicReference<>();
            scenario.onActivity(a -> nav.set(a.navigation()));
            if (nav.get() != null) {
                break;
            }
            SystemClock.sleep(100);
        }
        MapNavigationTest.TestGps.at(32.51150, 35.00400);   // on path 1–2, heading for 2, then north
        scenario.onActivity(a -> a.feedHeading(90));          // the phone looks east, along the path
        SystemClock.sleep(600);                               // a few frames

        AtomicReference<String> instruction = new AtomicReference<>();
        AtomicReference<Integer> arrow = new AtomicReference<>();
        scenario.onActivity(a -> {
            instruction.set(((TextView) a.findViewById(R.id.ar_instruction)).getText().toString());
            arrow.set(a.findViewById(R.id.ar_arrow).getVisibility());
        });
        assertTrue(instruction.get(), instruction.get().startsWith("פנו שמאלה"));   // east, then north
        assertEquals(View.VISIBLE, (int) arrow.get());
        ArNavActivityTest.screenshot("18-ar-arrow");
    }
}
