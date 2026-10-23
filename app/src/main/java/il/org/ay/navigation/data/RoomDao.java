package il.org.ay.navigation.data;

import androidx.room.Dao;
import androidx.room.Query;

import java.util.List;

import il.org.ay.navigation.model.CampusRoom;
import il.org.ay.navigation.model.RoomWithEntrance;

/** Queries on the rooms table. Never call these on the UI thread. */
@Dao
public interface RoomDao {

    @Query("SELECT * FROM rooms ORDER BY number")
    List<CampusRoom> getAll();

    @Query("SELECT * FROM rooms WHERE number = :number")
    CampusRoom findByNumber(String number);

    /** FR-01: search by number, name or owner. */
    @Query("SELECT * FROM rooms " +
           "WHERE number LIKE '%' || :text || '%' " +
           "   OR name LIKE '%' || :text || '%' " +
           "   OR owner LIKE '%' || :text || '%' " +
           "ORDER BY number")
    List<CampusRoom> search(String text);

    /** Rubric 6.4: JOIN between rooms and nodes — a room with the coordinates of its entrance. */
    @Query("SELECT r.number, r.name, r.owner, r.entrance_node_id AS entranceNodeId, n.lat, n.lng " +
           "FROM rooms r JOIN nodes n ON r.entrance_node_id = n.id " +
           "WHERE r.number = :number")
    RoomWithEntrance findWithEntrance(String number);

    /** All mapped rooms with their entrances, for drawing them on the map. */
    @Query("SELECT r.number, r.name, r.owner, r.entrance_node_id AS entranceNodeId, n.lat, n.lng " +
           "FROM rooms r JOIN nodes n ON r.entrance_node_id = n.id " +
           "ORDER BY r.number")
    List<RoomWithEntrance> getAllWithEntrances();
}
