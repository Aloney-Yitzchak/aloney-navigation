package il.org.ay.navigation.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Upsert;

import java.util.ArrayList;
import java.util.List;

import il.org.ay.navigation.model.CampusRoom;
import il.org.ay.navigation.model.Edge;
import il.org.ay.navigation.model.Node;

/**
 * The campus graph (nodes and edges) and the sync of the shared tables.
 * rooms, nodes and edges are a copy of Firestore; T08 calls {@link #replaceCampus}.
 */
@Dao
public abstract class GraphDao {

    @Query("SELECT * FROM nodes")
    public abstract List<Node> getNodes();

    @Query("SELECT * FROM edges")
    public abstract List<Edge> getEdges();

    @Query("SELECT COUNT(*) FROM nodes")
    public abstract int countNodes();

    @Upsert
    protected abstract void upsertNodes(List<Node> nodes);

    @Upsert
    protected abstract void upsertRooms(List<CampusRoom> rooms);

    @Insert
    protected abstract void insertEdges(List<Edge> edges);

    @Query("DELETE FROM edges")
    protected abstract void deleteEdges();

    @Query("DELETE FROM rooms WHERE number NOT IN (:keep)")
    protected abstract void deleteRoomsNotIn(List<String> keep);

    @Query("DELETE FROM nodes WHERE id NOT IN (:keep)")
    protected abstract void deleteNodesNotIn(List<Integer> keep);

    /**
     * Replaces the campus copy with a new version from Firestore, in one
     * transaction: either all of it is saved or none of it.
     *
     * <p>Nodes and rooms are updated in place (upsert) rather than deleted and
     * re-inserted, so the user's favorites and recent searches, which point at
     * rooms, are kept. Rooms that disappeared from the data are deleted, and
     * their favorites with them (ON DELETE CASCADE).
     */
    @Transaction
    public void replaceCampus(List<Node> nodes, List<Edge> edges, List<CampusRoom> rooms) {
        deleteEdges();
        upsertNodes(nodes);
        upsertRooms(rooms);
        insertEdges(edges);

        List<String> roomNumbers = new ArrayList<>();
        for (CampusRoom r : rooms) roomNumbers.add(r.number);
        deleteRoomsNotIn(roomNumbers);

        List<Integer> nodeIds = new ArrayList<>();
        for (Node n : nodes) nodeIds.add(n.id);
        deleteNodesNotIn(nodeIds);
    }
}
