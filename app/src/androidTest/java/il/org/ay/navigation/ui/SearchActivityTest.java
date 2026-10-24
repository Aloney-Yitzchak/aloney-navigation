package il.org.ay.navigation.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.os.SystemClock;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import il.org.ay.navigation.R;
import il.org.ay.navigation.data.AppDatabase;
import il.org.ay.navigation.model.CampusRoom;
import il.org.ay.navigation.model.Node;

/**
 * AT-01 (FR-01) on the emulator: search by number, name and owner, best match first.
 * AT-02 (FR-02): a favourite and a recent search are still there after the app is closed.
 * Each test starts from a fresh install (clearPackageData) with four sample rooms.
 */
@RunWith(AndroidJUnit4.class)
public class SearchActivityTest {

    private static final long WAIT_MS = 5_000;
    private ActivityScenario<SearchActivity> scenario;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase.get(context).graphDao().replaceCampus(
                Arrays.asList(new Node(1, 31.5, 34.9),
                        new Node(2, 31.5001, 34.9001)),
                Arrays.asList(),
                Arrays.asList(
                        new CampusRoom("42", "מתמטיקה", "רכזת שכבה ט'", 1, "תשפ\"ז"),
                        new CampusRoom("142", "מעבדת ביולוגיה", "דנה כהן", 1, "תשפ\"ז"),
                        new CampusRoom("20", "ספרייה", null, 2, "תשפ\"ז"),
                        new CampusRoom("מז", "מזכירות ראשית", "קבלת קהל", 2, "תשפ\"ז")));
        scenario = ActivityScenario.launch(SearchActivity.class);
    }

    @After
    public void tearDown() {
        scenario.close();
    }

    @Test
    public void emptyBox_showsAllRooms() {
        waitForCount(4);
        onView(withText(R.string.search_results_all)).check(matches(isDisplayed()));
    }

    @Test
    public void byNumber_exactMatchFirst() {
        type("42");
        waitForCount(2);
        assertEquals("42", numberAt(0));
        assertEquals("142", numberAt(1));
        screenshot("06-search-number");
    }

    @Test
    public void byName() {
        type("ספרי");
        waitForCount(1);
        assertEquals("20", numberAt(0));
    }

    @Test
    public void byOwner() {
        type("דנה");
        waitForCount(1);
        assertEquals("142", numberAt(0));
    }

    @Test
    public void theWordRoomIsIgnored() {
        type("חדר 20");
        waitForCount(1);
        assertEquals("20", numberAt(0));
    }

    @Test
    public void nothingFound_saysSo_andClearingBringsEverythingBack() {
        type("xyz");
        waitForCount(0);
        onView(withId(R.id.search_empty)).check(matches(isDisplayed()));
        screenshot("07-search-nothing");
        onView(withId(R.id.search_text)).perform(clearText());
        waitForCount(4);
    }

    @Test
    public void favorite_isStillThereAfterClosingTheApp() {
        type("42");
        waitForCount(2);
        clickInRow(0, R.id.room_favorite);
        waitForChips(R.id.favorites_chips, 1);
        onView(withId(R.id.favorites_header)).check(matches(isDisplayed()));

        // AT-02: close the app and open it again
        scenario.close();
        scenario = ActivityScenario.launch(SearchActivity.class);
        waitForChips(R.id.favorites_chips, 1);
        onView(withText("מתמטיקה")).check(matches(isDisplayed()));
        screenshot("08-search-favorite");

        // and the star in its row is filled
        type("42");
        waitForCount(2);
        scenario.onActivity(a -> {
            RecyclerView list = a.findViewById(R.id.search_results);
            View star = list.findViewHolderForAdapterPosition(0).itemView.findViewById(R.id.room_favorite);
            assertEquals(a.getString(R.string.action_unfavorite), star.getContentDescription());
        });
    }

    @Test
    public void choosingARoom_addsItToRecentSearches_andTheyStayAfterClosing() {
        type("ספרי");
        waitForCount(1);
        clickInRow(0, 0);
        onView(withId(R.id.search_text)).perform(clearText());
        waitForCount(4);
        waitForChips(R.id.recent_chips, 1);
        onView(withId(R.id.recent_header)).check(matches(isDisplayed()));

        scenario.close();
        scenario = ActivityScenario.launch(SearchActivity.class);
        waitForChips(R.id.recent_chips, 1);
        screenshot("09-search-recent");
    }

    /** Clicks a view inside a result row; {@code id} 0 clicks the row itself. */
    private void clickInRow(int position, int id) {
        scenario.onActivity(a -> {
            RecyclerView list = a.findViewById(R.id.search_results);
            View row = list.findViewHolderForAdapterPosition(position).itemView;
            (id == 0 ? row : row.findViewById(id)).performClick();
        });
    }

    private void waitForChips(int groupId, int expected) {
        long end = SystemClock.uptimeMillis() + WAIT_MS;
        int count = -1;
        while (SystemClock.uptimeMillis() < end) {
            AtomicReference<Integer> n = new AtomicReference<>();
            scenario.onActivity(a -> n.set(((ViewGroup) a.findViewById(groupId)).getChildCount()));
            count = n.get();
            if (count == expected) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                return;
            }
            SystemClock.sleep(50);
        }
        assertEquals("number of chips", expected, count);
    }

    private void type(String text) {
        onView(withId(R.id.search_text)).perform(replaceText(text), closeSoftKeyboard());
    }

    /** Results arrive from a background thread after a short typing pause; wait for them. */
    private void waitForCount(int expected) {
        long end = SystemClock.uptimeMillis() + WAIT_MS;
        int count = -1;
        while (SystemClock.uptimeMillis() < end) {
            count = itemCount();
            if (count == expected) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                return;
            }
            SystemClock.sleep(50);
        }
        assertEquals("number of results", expected, count);
    }

    private int itemCount() {
        AtomicReference<Integer> n = new AtomicReference<>();
        scenario.onActivity(a -> {
            RecyclerView list = a.findViewById(R.id.search_results);
            n.set(list.getAdapter() == null ? -1 : list.getAdapter().getItemCount());
        });
        return n.get();
    }

    private String numberAt(int position) {
        AtomicReference<String> number = new AtomicReference<>();
        scenario.onActivity(a -> {
            RecyclerView list = a.findViewById(R.id.search_results);
            RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
            number.set(((TextView) holder.itemView.findViewById(R.id.room_number)).getText().toString());
        });
        return number.get();
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
