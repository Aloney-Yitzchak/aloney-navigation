package il.org.ay.navigation.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import il.org.ay.navigation.R;
import il.org.ay.navigation.data.AppDatabase;
import il.org.ay.navigation.data.AuthManager;
import il.org.ay.navigation.data.CampusRemote;
import il.org.ay.navigation.data.CampusRepository;
import il.org.ay.navigation.data.CampusSnapshot;
import il.org.ay.navigation.model.CampusRoom;
import il.org.ay.navigation.model.Node;

/**
 * T24 / AT-10 (FR-10) on the emulator: a staff account signs in and changes a room's owner;
 * a wrong password or a student account does not get in; a save the rules refuse sends the
 * user back to signing in. Firebase is replaced by fakes: the rules themselves are tested
 * against the Firestore emulator in firestore-tests/rules.test.mjs.
 */
@RunWith(AndroidJUnit4.class)
public class AdminActivityTest {

    private static final long WAIT_MS = 5_000;

    /** Accounts: staff@ with the claim, student@ without; the password is "correct". */
    static class FakeAuth extends AuthManager {
        String signedIn;

        @Override
        public void signIn(@NonNull String email, @NonNull String password, @NonNull Callback callback) {
            if (!password.equals("correct") || !(email.equals("staff@a-y.org.il") || email.equals("student@a-y.org.il"))) {
                callback.onResult(Result.WRONG_CREDENTIALS);
            } else if (email.startsWith("student")) {
                callback.onResult(Result.NOT_STAFF);   // the real one signs out again
            } else {
                signedIn = email;
                callback.onResult(Result.STAFF);
            }
        }

        @Override
        public void checkStaff(@NonNull Callback callback) {
            callback.onResult(signedIn != null ? Result.STAFF : Result.NOT_STAFF);
        }

        @Nullable
        @Override
        public String email() {
            return signedIn;
        }

        @Override
        public void signOut() {
            signedIn = null;
        }
    }

    /** A cloud that is online, accepts or refuses edits, and has nothing newer to download. */
    static class FakeCloud implements CampusRemote {
        volatile CampusRepository.SaveResult answer = CampusRepository.SaveResult.SAVED;
        volatile String[] lastEdit;

        @Override
        public void fetchVersion(Callback<Long> callback) {
            callback.onResult(0L);
        }

        @Override
        public void fetchAll(Callback<CampusSnapshot> callback) {
            callback.onError(new Exception("not used"));
        }

        @Override
        public void updateRoom(String number, String name, String owner,
                               Callback<CampusRepository.SaveResult> callback) {
            lastEdit = new String[]{number, name, owner};
            callback.onResult(answer);
        }
    }

    private final FakeAuth auth = new FakeAuth();
    private final FakeCloud cloud = new FakeCloud();
    private ActivityScenario<AdminActivity> scenario;

    @Before
    public void setUp() {
        AuthManager.useForTesting(auth);
        CampusRepository.remoteForTesting = cloud;   // each test is a new process (clearPackageData)
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase.get(context).graphDao().replaceCampus(
                Arrays.asList(new Node(1, 31.5, 34.9), new Node(2, 31.5001, 34.9001)),
                Arrays.asList(),
                Arrays.asList(
                        new CampusRoom("42", "מתמטיקה", "רכזת שכבה ט'", 1, "tashpaz"),
                        new CampusRoom("20", "ספרייה", null, 2, "tashpaz")));
        scenario = ActivityScenario.launch(AdminActivity.class);
    }

    @After
    public void tearDown() {
        scenario.close();
        AuthManager.useForTesting(null);
    }

    @Test
    public void wrongPassword_staysOnSignIn_withAMessage() {
        signIn("staff@a-y.org.il", "wrong");
        assertVisible(R.id.admin_login_state);
        assertEquals(text(R.string.admin_error_credentials), textOf(R.id.admin_login_error));
        screenshot("21-admin-login-error");
    }

    @Test
    public void studentAccount_isNotLetIn() {
        signIn("student@a-y.org.il", "correct");
        assertVisible(R.id.admin_login_state);
        assertEquals(text(R.string.admin_error_not_staff), textOf(R.id.admin_login_error));
    }

    @Test
    public void staff_changesTheOwnerOfARoom() {   // AT-10, signed in: the update succeeds
        signIn("staff@a-y.org.il", "correct");
        assertVisible(R.id.admin_rooms_state);
        waitFor(() -> rowCount() == 2, "the two rooms");
        screenshot("22-admin-rooms");

        clickRow(0);   // "20" sorts before "42"
        BottomSheetDialog sheet = waitForSheet();
        scenario.onActivity(a -> ((TextView) sheet.findViewById(R.id.room_edit_owner)).setText("רכזת ספרייה"));
        screenshot("23-admin-edit");
        scenario.onActivity(a -> sheet.findViewById(R.id.room_edit_save).performClick());

        waitFor(() -> sheetOrNull() == null, "the sheet to close");
        assertEquals("20", cloud.lastEdit[0]);
        assertEquals("רכזת ספרייה", cloud.lastEdit[2]);
        waitFor(() -> "רכזת ספרייה".equals(ownerInRow(0)), "the new owner in the list");
    }

    @Test
    public void saveRefusedByTheRules_backToSignIn() {   // AT-10: the server has the last word
        signIn("staff@a-y.org.il", "correct");
        waitFor(() -> rowCount() == 2, "the two rooms");
        cloud.answer = CampusRepository.SaveResult.NOT_ALLOWED;
        clickRow(1);
        BottomSheetDialog sheet = waitForSheet();
        scenario.onActivity(a -> sheet.findViewById(R.id.room_edit_save).performClick());
        waitFor(() -> sheetOrNull() == null, "the sheet to close");
        assertVisible(R.id.admin_login_state);
        assertEquals(text(R.string.room_edit_not_allowed), textOf(R.id.admin_login_error));
        assertNull(auth.signedIn);
    }

    @Test
    public void signedInBefore_opensStraightOnTheRooms_andLogoutReturnsToSignIn() {
        scenario.close();
        auth.signedIn = "staff@a-y.org.il";
        scenario = ActivityScenario.launch(AdminActivity.class);
        assertVisible(R.id.admin_rooms_state);
        scenario.onActivity(a -> {
            com.google.android.material.appbar.MaterialToolbar bar = a.findViewById(R.id.toolbar);
            bar.getMenu().performIdentifierAction(R.id.action_logout, 0);
        });
        assertVisible(R.id.admin_login_state);
        assertNull(auth.signedIn);
    }

    // ------------------------------------------------------------ helpers

    private void signIn(String email, String password) {
        scenario.onActivity(a -> {
            ((TextView) a.findViewById(R.id.admin_email)).setText(email);
            ((TextView) a.findViewById(R.id.admin_password)).setText(password);
            a.findViewById(R.id.admin_login_button).performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void clickRow(int position) {
        scenario.onActivity(a -> {
            RecyclerView list = a.findViewById(R.id.admin_rooms);
            list.findViewHolderForAdapterPosition(position).itemView.performClick();
        });
    }

    private BottomSheetDialog waitForSheet() {
        waitFor(() -> sheetOrNull() != null, "the edit sheet");
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        BottomSheetDialog sheet = sheetOrNull();
        assertNotNull(sheet);
        return sheet;
    }

    @Nullable
    private BottomSheetDialog sheetOrNull() {
        AtomicReference<BottomSheetDialog> s = new AtomicReference<>();
        scenario.onActivity(a -> s.set(a.editSheet()));
        return s.get();
    }

    private int rowCount() {
        AtomicReference<Integer> n = new AtomicReference<>();
        scenario.onActivity(a -> {
            RecyclerView list = a.findViewById(R.id.admin_rooms);
            n.set(list.getAdapter() == null ? -1 : list.getAdapter().getItemCount());
        });
        return n.get();
    }

    @Nullable
    private String ownerInRow(int position) {
        AtomicReference<String> owner = new AtomicReference<>();
        scenario.onActivity(a -> {
            RecyclerView list = a.findViewById(R.id.admin_rooms);
            RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
            owner.set(holder == null ? null
                    : ((TextView) holder.itemView.findViewById(R.id.room_owner)).getText().toString());
        });
        return owner.get();
    }

    private void assertVisible(int id) {
        waitFor(() -> {
            AtomicReference<Boolean> shown = new AtomicReference<>();
            scenario.onActivity(a -> shown.set(a.findViewById(id).getVisibility() == View.VISIBLE));
            return shown.get();
        }, "view " + id);
    }

    private String textOf(int id) {
        AtomicReference<String> t = new AtomicReference<>();
        scenario.onActivity(a -> t.set(((TextView) a.findViewById(id)).getText().toString()));
        return t.get();
    }

    private String text(int res) {
        AtomicReference<String> t = new AtomicReference<>();
        scenario.onActivity(a -> t.set(a.getString(res)));
        return t.get();
    }

    private static void waitFor(Supplier<Boolean> condition, String what) {
        long end = SystemClock.uptimeMillis() + WAIT_MS;
        while (SystemClock.uptimeMillis() < end) {
            if (condition.get()) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                return;
            }
            SystemClock.sleep(50);
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
