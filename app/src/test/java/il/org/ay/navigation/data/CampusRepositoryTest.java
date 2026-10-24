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

    // ------------------------------------------------------------ T10, FR-02

    /** Runs a repository call and waits for its answer. */
    private <T> T await(java.util.function.Consumer<CampusRepository.Listener<T>> call) throws InterruptedException {
        AtomicReference<T> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        call.accept(r -> { result.set(r); done.countDown(); });
        done.await(5, TimeUnit.SECONDS);
        return result.get();
    }

    private Boolean toggle(String number) throws InterruptedException {
        return this.<Boolean>await(l -> repo.toggleFavorite(number, l));
    }

    private void addRecent(String number) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        repo.addRecent(number, done::countDown);
        done.await(5, TimeUnit.SECONDS);
    }

    private static List<String> numbers(List<CampusRoom> rooms) {
        List<String> out = new java.util.ArrayList<>();
        for (CampusRoom r : rooms) out.add(r.number);
        return out;
    }

    /** Version 1 of the campus with twelve rooms, 1..12, all at node 1. */
    private void syncTwelveRooms(long version) throws InterruptedException {
        CampusSnapshot s = CampusSnapshotTest.good(version);
        s.rooms.clear();
        for (int i = 1; i <= 12; i++) {
            s.rooms.add(new CampusRoom(String.valueOf(i), "חדר " + i, null, 1, "tashpaz"));
        }
        remote.version = version;
        remote.snapshot = s;
        assertEquals(CampusRepository.SyncResult.UPDATED, sync());
    }

    @Test
    public void toggleFavorite_addsThenRemoves_newestFirst() throws Exception {
        syncTwelveRooms(1);
        assertEquals(Boolean.TRUE, toggle("3"));
        assertEquals(Boolean.TRUE, toggle("7"));
        assertEquals(java.util.Arrays.asList("7", "3"), numbers(await(repo::favorites)));
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("3", "7")), await(repo::favoriteNumbers));

        assertEquals(Boolean.FALSE, toggle("3"));
        assertEquals(java.util.Collections.singletonList("7"), numbers(await(repo::favorites)));
    }

    @Test
    public void recents_latestFirst_noDuplicates_atMostTen() throws Exception {
        syncTwelveRooms(1);
        for (int i = 1; i <= 12; i++) {
            addRecent(String.valueOf(i));
        }
        addRecent("5");  // chosen again: moves to the top, not a second row
        List<String> recent = numbers(await(repo::recents));
        assertEquals(10, recent.size());
        assertEquals(java.util.Arrays.asList("5", "12", "11", "10", "9", "8", "7", "6", "4", "3"), recent);
    }

    @Test
    public void favorites_surviveANewVersion_unlessTheirRoomIsGone() throws Exception {
        syncTwelveRooms(1);
        toggle("2");
        toggle("12");
        addRecent("12");

        // version 2: room 12 is no longer on the campus
        CampusSnapshot v2 = CampusSnapshotTest.good(2);
        v2.rooms.clear();
        for (int i = 1; i <= 11; i++) {
            v2.rooms.add(new CampusRoom(String.valueOf(i), "חדר " + i, null, 1, "tashpaz"));
        }
        remote.version = 2;
        remote.snapshot = v2;
        assertEquals(CampusRepository.SyncResult.UPDATED, sync());

        assertEquals(java.util.Collections.singletonList("2"), numbers(await(repo::favorites)));
        assertEquals(0, await(repo::recents).size());
    }

    @Test
    public void loadGraph_buildsTheRouteGraphFromRoom() throws Exception {
        remote.version = 1;
        remote.snapshot = CampusSnapshotTest.good(1);
        sync();
        CampusGraph graph = await(repo::loadGraph);
        assertEquals(2, graph.nodeCount());
        assertEquals(14.2, graph.findPath(1, 2).totalDistance(), 1e-9);
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
