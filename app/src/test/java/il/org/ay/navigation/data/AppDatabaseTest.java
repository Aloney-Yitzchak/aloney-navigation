package il.org.ay.navigation.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;

import il.org.ay.navigation.model.CampusRoom;
import il.org.ay.navigation.model.Edge;
import il.org.ay.navigation.model.Favorite;
import il.org.ay.navigation.model.Node;
import il.org.ay.navigation.model.Recent;
import il.org.ay.navigation.model.RoomWithEntrance;

/** T04: the Room schema, the JOIN (rubric 6.4) and the sync transaction, on an in-memory database. */
@RunWith(RobolectricTestRunner.class)
public class AppDatabaseTest {

    private AppDatabase db;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        db.graphDao().replaceCampus(
                Arrays.asList(
                        new Node(1, 32.4900, 34.9500),
                        new Node(2, 32.4905, 34.9506)),
                Arrays.asList(new Edge(1, 1, 2, 79.0)),
                Arrays.asList(
                        new CampusRoom("36", "ט2", "מחנכת ט2", 2, "tashpaz"),
                        new CampusRoom("ש36", "ספרייה", null, null, "tashpaz")));
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void join_returnsRoomWithEntranceCoordinates() {
        RoomWithEntrance r = db.roomDao().findWithEntrance("36");
        assertNotNull(r);
        assertEquals("ט2", r.name);
        assertEquals(2, r.entranceNodeId);
        assertEquals(32.4905, r.lat, 1e-9);
        assertEquals(34.9506, r.lng, 1e-9);
    }

    @Test
    public void join_skipsRoomsThatAreNotMappedYet() {
        assertNull(db.roomDao().findWithEntrance("ש36"));
        assertEquals(1, db.roomDao().getAllWithEntrances().size());
    }

    @Test
    public void search_matchesNumberNameAndOwner() {
        assertEquals(2, db.roomDao().search("36").size());
        assertEquals("ש36", db.roomDao().search("ספרי").get(0).number);
        assertEquals("36", db.roomDao().search("מחנכת").get(0).number);
    }

    @Test
    public void replaceCampus_keepsFavoritesOfRoomsThatStay() {
        db.favoriteDao().add(new Favorite("36", 100));
        db.favoriteDao().add(new Favorite("ש36", 200));
        // next year: room 36 is reassigned and moves to a new entrance node, ש36 is gone
        db.graphDao().replaceCampus(
                Arrays.asList(new Node(5, 32.49, 34.95)),
                Arrays.asList(),
                Arrays.asList(new CampusRoom("36", "י1", null, 5, "tashpaf")));
        assertTrue(db.favoriteDao().isFavorite("36"));
        assertFalse(db.favoriteDao().isFavorite("ש36"));
        assertEquals(1, db.graphDao().countNodes());
        assertEquals("י1", db.roomDao().findByNumber("36").name);
        assertEquals(5, db.roomDao().findWithEntrance("36").entranceNodeId);
    }

    @Test
    public void replaceCampus_isAllOrNothing() {
        try {
            // edge to a node that doesn't exist breaks the foreign key
            db.graphDao().replaceCampus(
                    Arrays.asList(new Node(7, 32.49, 34.95)),
                    Arrays.asList(new Edge(1, 7, 99, 10)),
                    Arrays.asList());
        } catch (RuntimeException expected) {
            // the transaction is rolled back
        }
        assertEquals(2, db.graphDao().countNodes());
        assertNotNull(db.roomDao().findWithEntrance("36"));
    }

    @Test
    public void recent_keepsLatestTenWithoutDuplicates() {
        List<CampusRoom> rooms = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) rooms.add(new CampusRoom("r" + i, null, null, null, null));
        db.graphDao().replaceCampus(Arrays.asList(), Arrays.asList(), rooms);
        for (int i = 0; i < 15; i++) {
            db.recentDao().add(new Recent("r" + i, i));
        }
        db.recentDao().add(new Recent("r3", 100));
        List<Recent> recent = db.recentDao().getAll();
        assertEquals(RecentDao.MAX_RECENT, recent.size());
        assertEquals("r3", recent.get(0).roomNumber);
        assertFalse(recent.stream().anyMatch(r -> r.roomNumber.equals("r0")));
    }
}
