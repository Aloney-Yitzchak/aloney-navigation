package il.org.ay.navigation.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import il.org.ay.navigation.data.AuthManager;
import il.org.ay.navigation.data.CampusRepository;
import il.org.ay.navigation.data.PhotoStore;
import il.org.ay.navigation.data.PhotoTasks;
import il.org.ay.navigation.model.PhotoTask;

/**
 * T20a on the emulator: a staff member adds a building to photograph, takes a photo of it
 * (the emulator's moving test pattern), and deletes it again. The photo is saved in the
 * building's folder on the phone and counted for all phones.
 */
@RunWith(AndroidJUnit4.class)
public class PhotoTasksTest {

    private static final long WAIT_MS = 10_000;

    @Rule
    public GrantPermissionRule camera = GrantPermissionRule.grant(Manifest.permission.CAMERA);

    /** The shared list, kept in memory; remembers what was saved and counted. */
    static class FakeTasks extends PhotoTasks {
        final List<PhotoTask> list = new ArrayList<>(Arrays.asList(
                new PhotoTask("building_01", "בניין המדעים", 3, "מהשביל הראשי", 1),
                new PhotoTask(PhotoTask.OTHER, "אחר: שבילים, עצים, שמיים", 40, null, 1000)));
        final List<String> counted = new ArrayList<>();
        @Nullable
        CampusRepository.Listener<List<PhotoTask>> listener;

        @NonNull
        @Override
        public Subscription listen(@NonNull CampusRepository.Listener<List<PhotoTask>> l) {
            listener = l;
            l.onResult(new ArrayList<>(list));
            return () -> listener = null;
        }

        @Override
        public void save(@NonNull PhotoTask task, @NonNull CampusRepository.Listener<CampusRepository.SaveResult> l) {
            list.removeIf(t -> t.label.equals(task.label));
            list.add(task);
            list.sort((a, b) -> Integer.compare(a.order, b.order));
            l.onResult(CampusRepository.SaveResult.SAVED);
            if (listener != null) {
                listener.onResult(new ArrayList<>(list));
            }
        }

        @Override
        public void delete(@NonNull String label, @NonNull CampusRepository.Listener<CampusRepository.SaveResult> l) {
            list.removeIf(t -> t.label.equals(label));
            l.onResult(CampusRepository.SaveResult.SAVED);
        }

        @Override
        public void countPhoto(@NonNull String label, int delta) {
            counted.add(label + (delta > 0 ? "+" : "-"));
        }
    }

    static class StaffAuth extends AuthManager {
        @Override
        public void signIn(@NonNull String email, @NonNull String password, @NonNull Callback callback) {
            callback.onResult(Result.STAFF);
        }

        @Override
        public void checkStaff(@NonNull Callback callback) {
            callback.onResult(Result.STAFF);
        }

        @Override
        public String email() {
            return "staff@a-y.org.il";
        }

        @Override
        public void signOut() {
        }
    }

    private final FakeTasks tasks = new FakeTasks();
    private PhotoStore store;

    @Before
    public void setUp() {
        PhotoTasks.useForTesting(tasks);
        AuthManager.useForTesting(new StaffAuth());
        store = PhotoStore.get(ApplicationProvider.getApplicationContext());
        store.deleteAll();
    }

    @After
    public void tearDown() {
        PhotoTasks.useForTesting(null);
        AuthManager.useForTesting(null);
    }

    @Test
    public void staffAddsABuilding_andItGetsTheNextLabel() {
        try (ActivityScenario<PhotoTasksActivity> scenario = ActivityScenario.launch(PhotoTasksActivity.class)) {
            waitFor(() -> rows(scenario) == 2, "the two tasks");
            screenshot("24-photo-tasks");
            scenario.onActivity(a -> a.findViewById(il.org.ay.navigation.R.id.photo_add_task).performClick());

            UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            UiObject2 name = device.wait(Until.findObject(By.res(pkg(), "photo_task_name_field")), WAIT_MS);
            assertNotNull("the dialog", name);
            name.setText("חדר האוכל");
            device.findObject(By.res(pkg(), "photo_task_note_field")).setText("מהחניה");
            device.findObject(By.text(text(il.org.ay.navigation.R.string.room_edit_save))).click();

            waitFor(() -> rows(scenario) == 3, "the new task in the list");
            PhotoTask added = tasks.list.get(1);   // after building_01, before "other"
            assertEquals("building_02", added.label);
            assertEquals("חדר האוכל", added.name);
            assertEquals(40, added.target);
        }
    }

    @Test
    public void takingAPhoto_savesItInTheBuildingsFolder_andCountsIt_andDeletingUndoesIt() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = PhotoCaptureActivity.intent(context, tasks.list.get(0));
        try (ActivityScenario<PhotoCaptureActivity> scenario = ActivityScenario.launch(intent)) {
            waitFor(() -> {
                AtomicReference<Boolean> ready = new AtomicReference<>();
                scenario.onActivity(a -> ready.set(a.readyToShoot()));
                return ready.get();
            }, "the camera");
            SystemClock.sleep(1000);   // a few frames of the test pattern
            scenario.onActivity(a -> a.findViewById(il.org.ay.navigation.R.id.capture_shutter).performClick());
            waitFor(() -> photos(scenario) == 1, "the photo to be saved");
            assertEquals(1, store.count("building_01"));
            assertEquals(Arrays.asList("building_01+"), tasks.counted);
            waitFor(() -> {
                AtomicReference<Boolean> shown = new AtomicReference<>();
                scenario.onActivity(a -> shown.set(a.findViewById(il.org.ay.navigation.R.id.capture_delete_last)
                        .getVisibility() == android.view.View.VISIBLE));
                return shown.get();
            }, "the thumbnail");
            screenshot("25-photo-capture");

            scenario.onActivity(a -> a.findViewById(il.org.ay.navigation.R.id.capture_delete_last).performClick());
            waitFor(() -> photos(scenario) == 0, "the photo to be deleted");
            assertEquals(0, store.count("building_01"));
            assertEquals(Arrays.asList("building_01+", "building_01-"), tasks.counted);
        }
    }

    // ------------------------------------------------------------ helpers

    private static String pkg() {
        return ApplicationProvider.getApplicationContext().getPackageName();
    }

    private static String text(int res) {
        Context c = ApplicationProvider.getApplicationContext();
        android.content.res.Configuration config = new android.content.res.Configuration(c.getResources().getConfiguration());
        config.setLocale(BaseActivity.HEBREW);
        return c.createConfigurationContext(config).getString(res);
    }

    private static int rows(ActivityScenario<PhotoTasksActivity> scenario) {
        AtomicReference<Integer> n = new AtomicReference<>();
        scenario.onActivity(a -> {
            RecyclerView list = a.findViewById(il.org.ay.navigation.R.id.photo_tasks);
            n.set(list.getAdapter() == null ? -1 : list.getAdapter().getItemCount());
        });
        return n.get();
    }

    private static int photos(ActivityScenario<PhotoCaptureActivity> scenario) {
        AtomicReference<Integer> n = new AtomicReference<>();
        scenario.onActivity(a -> n.set(a.photosOnPhone()));
        return n.get();
    }

    private static void waitFor(Supplier<Boolean> condition, String what) {
        long end = SystemClock.uptimeMillis() + WAIT_MS;
        while (SystemClock.uptimeMillis() < end) {
            if (condition.get()) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                return;
            }
            SystemClock.sleep(100);
        }
        assertTrue("waited for " + what, condition.get());
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
