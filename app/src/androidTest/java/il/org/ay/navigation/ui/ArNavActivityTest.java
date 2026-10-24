package il.org.ay.navigation.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import androidx.lifecycle.Lifecycle;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import il.org.ay.navigation.R;
import il.org.ay.navigation.data.AppDatabase;
import il.org.ay.navigation.model.CampusRoom;
import il.org.ay.navigation.model.Node;

/**
 * T16 on the emulator, whose back camera shows a moving test pattern (-camera-back emulated):
 * the live camera, the real destination in the bar (design finding 7), the frames reaching
 * BuildingRecognizer, and the camera stopping when the screen is not visible (NFR-08).
 */
@RunWith(AndroidJUnit4.class)
public class ArNavActivityTest {

    @Rule
    public GrantPermissionRule camera = GrantPermissionRule.grant(Manifest.permission.CAMERA);

    private ActivityScenario<ArNavActivity> scenario;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase.get(context).graphDao().replaceCampus(
                Arrays.asList(new Node(1, 32.5115, 35.0037)), Arrays.asList(),
                Arrays.asList(new CampusRoom("42", "מתמטיקה", "רכזת שכבה ט'", 1, "תשפ\"ז")));
        scenario = ActivityScenario.launch(ArNavActivity.navigateTo(context, "42"));
        // a fresh install asks once for the step permission (T17, decision #13): not now
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        UiObject2 notNow = device.wait(Until.findObject(By.text(context.getString(R.string.steps_not_now))), 5_000);
        if (notNow != null) {
            notNow.click();
            device.waitForIdle();
        }
    }

    @After
    public void tearDown() {
        scenario.close();
    }

    private int frames() {
        AtomicInteger n = new AtomicInteger();
        scenario.onActivity(a -> n.set(a.recognizer().frameCount()));
        return n.get();
    }

    private int waitForFrames(int atLeast) {
        long end = SystemClock.uptimeMillis() + 15_000;
        int n = 0;
        while (SystemClock.uptimeMillis() < end && (n = frames()) < atLeast) {
            SystemClock.sleep(200);
        }
        return n;
    }

    @Test
    public void framesReachTheRecognizer_andTheBarShowsTheRealDestination() {
        int n = waitForFrames(10);
        assertTrue("only " + n + " frames reached the recognizer", n >= 10);
        onView(withId(R.id.ar_destination)).check(matches(withText("ניווט אל: מתמטיקה")));
        screenshot("16-camera");
    }

    @Test
    public void inTheBackground_theCameraStops() {
        waitForFrames(5);
        scenario.moveToState(Lifecycle.State.CREATED);    // another screen on top
        SystemClock.sleep(1500);                          // frames in flight may still land
        int before = frames();
        SystemClock.sleep(2000);
        assertEquals("frames kept coming with the screen hidden", before, frames());

        scenario.moveToState(Lifecycle.State.RESUMED);    // back: the camera starts again
        int after = waitForFrames(before + 5);
        assertTrue(after >= before + 5);
    }

    static void screenshot(String name) {
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
