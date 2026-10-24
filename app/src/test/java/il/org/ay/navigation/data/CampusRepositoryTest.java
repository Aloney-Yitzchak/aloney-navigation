package il.org.ay.navigation.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import il.org.ay.navigation.model.CampusRoom;

/**
 * T08 / AT-09: the sync rules, against a fake cloud and an in-memory Room database.
 * The database is built WITHOUT allowMainThreadQueries, like in the app.
 */
@RunWith(RobolectricTestRunner.class)
public class CampusRepositoryTest {

    /** A cloud we control: its version, its data, or no network at all. */
    static class FakeRemote implements CampusRemote {
        long version;
        CampusSnapshot snapshot;
        boolean offline;
        int fetchAllCalls;

        @Override
        public void fetchVersion(Callback<Long> callback) {
            if (offline) callback.onError(new Exception("no network"));
            else callback.onResult(version);
        }

        @Override
        public void fetchAll(Callback<CampusSnapshot> callback) {
            fetchAllCalls++;
            if (offline) callback.onError(new Exception("no network"));
            else callback.onResult(snapshot);
        }
    }

    private AppDatabase db;
    private FakeRemote remote;
    private CampusRepository repo;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        SharedPreferences prefs = context.getSharedPreferences("test-sync", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        remote = new FakeRemote();
        repo = new CampusRepository(db, remote, prefs, Runnable::run);
    }

    @After
    public void tearDown() {
        db.close();
    }

    private CampusRepository.SyncResult sync() throws InterruptedException {
        AtomicReference<CampusRepository.SyncResult> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        repo.sync(r -> { result.set(r); done.countDown(); });
        done.await(5, TimeUnit.SECONDS);
        return result.get();
    }

    private List<CampusRoom> search(String text) throws InterruptedException {
        AtomicReference<List<CampusRoom>> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        repo.search(text, r -> { result.set(r); done.countDown(); });
        done.await(5, TimeUnit.SECONDS);
        return result.get();
    }

    @Test
    public void newerCloudVersion_isDownloadedAndSaved() throws Exception {
        remote.version = 1;
        remote.snapshot = CampusSnapshotTest.good(1);
        assertEquals(CampusRepository.SyncResult.UPDATED, sync());
        assertEquals(1, repo.localVersion());
        assertEquals("ספרייה", search("ספרי").get(0).name);
    }

    @Test
    public void openScreens_hearAboutANewVersion_butNotAboutNoChange() throws Exception {
        int[] calls = {0};
        Runnable listener = () -> calls[0]++;
        repo.addChangeListener(listener);
        remote.version = 1;
        remote.snapshot = CampusSnapshotTest.good(1);
        sync();
        sync();  // up to date: nothing new to show
        assertEquals(1, calls[0]);
        repo.removeChangeListener(listener);
    }

    @Test
    public void search_theWordRoomIsIgnored() throws Exception {
        remote.version = 1;
        remote.snapshot = CampusSnapshotTest.good(1);
        sync();
        assertEquals(1, search("חדר 20").size());
    }

    @Test
    public void sameVersion_doesNotDownloadAgain() throws Exception {
        remote.version = 1;
        remote.snapshot = CampusSnapshotTest.good(1);
        sync();
        assertEquals(CampusRepository.SyncResult.UP_TO_DATE, sync());
        assertEquals(1, remote.fetchAllCalls);
    }

    @Test
    public void incompleteData_isRejectedAndOldCopyKept() throws Exception {
        remote.version = 1;
        remote.snapshot = CampusSnapshotTest.good(1);
        sync();
        CampusSnapshot broken = CampusSnapshotTest.good(2);
        broken.rooms.add(new CampusRoom("21", "חדר הכלה", null, 99, "tashpaz"));
        remote.version = 2;
        remote.snapshot = broken;
        assertEquals(CampusRepository.SyncResult.REJECTED_INCOMPLETE, sync());
        assertEquals(1, repo.localVersion());
        assertEquals(1, search("").size());
    }

    @Test
    public void airplaneMode_afterFirstSync_searchStillWorks() throws Exception {
        // AT-09: sync once, then lose the network
        remote.version = 1;
        remote.snapshot = CampusSnapshotTest.good(1);
        sync();
        remote.offline = true;
        assertEquals(CampusRepository.SyncResult.FAILED, sync());
        assertEquals(1, search("20").size());
        assertEquals(1, repo.localVersion());
    }

    @Test
    public void roomQueries_areRefusedOnTheMainThread() {
        // Room's guard is on (no allowMainThreadQueries), so the repository must use its executor
        assertThrows(IllegalStateException.class, () -> db.roomDao().getAll());
    }
}
