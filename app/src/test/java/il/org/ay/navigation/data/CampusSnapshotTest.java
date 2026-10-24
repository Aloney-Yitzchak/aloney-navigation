package il.org.ay.navigation.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import il.org.ay.navigation.model.CampusRoom;
import il.org.ay.navigation.model.Edge;
import il.org.ay.navigation.model.Node;

/** T08: the sync rejects incomplete data. */
public class CampusSnapshotTest {

    static CampusSnapshot good(long version) {
        return new CampusSnapshot(version,
                new ArrayList<>(Arrays.asList(new Node(1, 32.51, 35.003), new Node(2, 32.5101, 35.0031))),
                new ArrayList<>(Collections.singletonList(new Edge(1, 1, 2, 14.2))),
                new ArrayList<>(Collections.singletonList(new CampusRoom("20", "ספרייה", null, 2, "tashpaz"))));
    }

    @Test
    public void completeSnapshot_isAccepted() {
        assertTrue(good(1).isComplete());
    }

    @Test
    public void noVersion_isRejected() {
        assertFalse(good(0).isComplete());
    }

    @Test
    public void emptyCollections_areRejected() {
        CampusSnapshot s = good(1);
        s.nodes.clear();
        assertFalse(s.isComplete());
    }

    @Test
    public void edgeToMissingNode_isRejected() {
        CampusSnapshot s = good(1);
        s.edges.add(new Edge(2, 2, 99, 10));
        assertFalse(s.isComplete());
    }

    @Test
    public void roomWithMissingEntrance_isRejected() {
        CampusSnapshot s = good(1);
        s.rooms.add(new CampusRoom("21", "חדר הכלה", null, 99, "tashpaz"));
        assertFalse(s.isComplete());
    }

    @Test
    public void unmappedRoom_isAllowed() {
        CampusSnapshot s = good(1);
        s.rooms.add(new CampusRoom("21", "חדר הכלה", null, null, "tashpaz"));
        assertTrue(s.isComplete());
    }
}
