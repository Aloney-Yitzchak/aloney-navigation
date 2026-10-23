package il.org.ay.navigation.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.ParcelFileDescriptor;
import android.text.Layout;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

import il.org.ay.navigation.R;

/**
 * AT-12 (FR-12) on a real emulator, through the real Android permission dialogs.
 * Every test starts from a fresh install (clearPackageData), so the dialogs always appear.
 * The emulator's system language is English, which is exactly the case that broke RTL.
 * Screenshots go to /sdcard/Pictures/aloney and are uploaded by the GitHub workflow.
 */
@RunWith(AndroidJUnit4.class)
public class SplashActivityTest {

    private static final long TIMEOUT_MS = 10_000;
    private static final Pattern ALLOW = Pattern.compile(
            ".*:id/(permission_allow_foreground_only_button|permission_allow_button)");
    private static final Pattern DENY = Pattern.compile(".*:id/permission_deny_button");

    private UiDevice device;
    private ActivityScenario<SplashActivity> scenario;

    @Before
    public void setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        scenario = ActivityScenario.launch(SplashActivity.class);
    }

    @After
    public void tearDown() {
        scenario.close();
    }

    @Test
    public void splash_isRightToLeft_evenOnAnEnglishPhone() {
        screenshot("01-splash");
        scenario.onActivity(activity -> {
            View root = activity.findViewById(R.id.splash_root);
            assertEquals(View.LAYOUT_DIRECTION_RTL, root.getLayoutDirection());
            assertEquals(View.LAYOUT_DIRECTION_RTL,
                    activity.getWindow().getDecorView().getLayoutDirection());

            // the explanation line hugs the right edge (this was the bug: it hugged the left)
            TextView why = activity.findViewById(R.id.splash_why_location);
            Layout layout = why.getLayout();
            assertTrue("text should end at the right edge, but ends at " + layout.getLineRight(0)
                            + " of " + layout.getWidth(),
                    layout.getWidth() - layout.getLineRight(0) < 2);
            assertTrue("text should not start at the left edge", layout.getLineLeft(0) > 2);
        });
    }

    @Test
    public void allowBoth_opensSearch() {
        onView(withId(R.id.button_start)).perform(click());
        answer(ALLOW);   // location
        answer(ALLOW);   // camera
        onView(withId(R.id.search_placeholder)).check(matches(isDisplayed()));
        screenshot("02-search");
    }

    @Test
    public void denyCamera_explainsAndMapStillAvailable() {
        onView(withId(R.id.button_start)).perform(click());
        answer(ALLOW);   // location
        answer(DENY);    // camera
        onView(withText(R.string.permission_missing_camera)).inRoot(isDialog()).check(matches(isDisplayed()));
        screenshot("03-camera-denied");
        assertDialogIsRightToLeft();
        onView(withText(R.string.permission_continue)).inRoot(isDialog()).perform(click());
        onView(withId(R.id.search_placeholder)).check(matches(isDisplayed()));
    }

    @Test
    public void denyLocation_explainsAndSearchStillAvailable() {
        onView(withId(R.id.button_start)).perform(click());
        answer(DENY);    // location
        answer(ALLOW);   // camera
        onView(withText(R.string.permission_missing_location)).inRoot(isDialog()).check(matches(isDisplayed()));
        screenshot("04-location-denied");
        onView(withText(R.string.permission_continue)).inRoot(isDialog()).perform(click());
        onView(withId(R.id.search_placeholder)).check(matches(isDisplayed()));
    }

    @Test
    public void denyBoth_oneMessageAboutBoth() {
        onView(withId(R.id.button_start)).perform(click());
        answer(DENY);    // location
        answer(DENY);    // camera
        onView(withText(R.string.permission_missing_both)).inRoot(isDialog()).check(matches(isDisplayed()));
        screenshot("05-both-denied");
        onView(withText(R.string.permission_continue)).inRoot(isDialog()).perform(click());
        onView(withId(R.id.search_placeholder)).check(matches(isDisplayed()));
    }

    /** Clicks a button in the system permission dialog. */
    private void answer(Pattern button) {
        UiObject2 b = device.wait(Until.findObject(By.res(button)), TIMEOUT_MS);
        assertNotNull("permission dialog button not found: " + button, b);
        b.click();
        device.waitForIdle();
    }

    /** The dialog title must sit on the right: its right edge is further right than its left edge is from the left. */
    private void assertDialogIsRightToLeft() {
        onView(withText(R.string.permission_missing_title)).inRoot(isDialog()).check((view, e) -> {
            assertEquals(View.LAYOUT_DIRECTION_RTL, view.getLayoutDirection());
            View parent = (View) view.getParent();
            int[] v = new int[2], p = new int[2];
            view.getLocationOnScreen(v);
            parent.getLocationOnScreen(p);
            int gapLeft = v[0] - p[0];
            int gapRight = (p[0] + parent.getWidth()) - (v[0] + view.getWidth());
            TextView t = (TextView) view;
            if (t.getLayout() != null) {  // title text inside a full-width TextView
                gapLeft += (int) t.getLayout().getLineLeft(0);
                gapRight += t.getWidth() - (int) t.getLayout().getLineRight(0);
            }
            assertTrue("dialog title should be on the right (left gap " + gapLeft + ", right gap " + gapRight + ")",
                    gapRight < gapLeft);
        });
    }

    private void screenshot(String name) {
        device.waitForIdle();
        shell("mkdir -p /sdcard/Pictures/aloney");
        shell("screencap -p /sdcard/Pictures/aloney/" + name + ".png");
    }

    /** Runs a shell command on the emulator and waits for it to finish. */
    private static void shell(String command) {
        ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(command);
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
            byte[] buffer = new byte[1024];
            while (in.read(buffer) != -1) {
                // drain the output; the command is done when the stream ends
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
