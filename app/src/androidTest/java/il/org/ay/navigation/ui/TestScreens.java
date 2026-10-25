package il.org.ay.navigation.ui;

import android.os.SystemClock;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

/**
 * Closes a screen at the end of a test without {@link ActivityScenario#close()}.
 *
 * <p>close() opens a helper screen from the test APK in a second process. On the Android 14
 * emulator, when the orchestrator then clears the data before the next test, the system
 * removes that helper's task and with it the next test's process as it starts ("Killing …
 * remove task" in the device log): the test "crashes", or the run waits forever. Finishing
 * the screen itself needs no helper.
 */
final class TestScreens {

    private TestScreens() {
    }

    static void finish(ActivityScenario<?> scenario) {
        if (scenario == null || scenario.getState() == Lifecycle.State.DESTROYED) {
            return;
        }
        scenario.onActivity(android.app.Activity::finish);
        long end = SystemClock.uptimeMillis() + 5_000;
        while (scenario.getState() != Lifecycle.State.DESTROYED && SystemClock.uptimeMillis() < end) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            SystemClock.sleep(50);
        }
    }
}
