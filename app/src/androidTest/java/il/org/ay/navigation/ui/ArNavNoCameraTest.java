package il.org.ay.navigation.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertNotNull;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

import il.org.ay.navigation.R;

/** T16 / AT-12: the camera declined on the camera screen: an explanation and the map instead. */
@RunWith(AndroidJUnit4.class)
public class ArNavNoCameraTest {

    @Test
    public void cameraDeclined_offersTheMap() {
        ActivityScenario<ArNavActivity> scenario = ActivityScenario.launch(ArNavActivity.class);
        try {
            UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            UiObject2 deny = device.wait(Until.findObject(By.res(Pattern.compile(".*:id/permission_deny.*button"))), 10_000);
            assertNotNull("the camera permission prompt did not appear", deny);
            deny.click();
            device.waitForIdle();
            onView(withText(R.string.ar_no_camera_title)).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(R.string.ar_open_map)).inRoot(isDialog()).check(matches(isDisplayed()));
            ArNavActivityTest.screenshot("17-camera-declined");
        } finally {
            TestScreens.finish(scenario);
        }
    }
}
