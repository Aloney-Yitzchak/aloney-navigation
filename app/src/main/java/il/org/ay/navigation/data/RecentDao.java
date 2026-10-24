package il.org.ay.navigation.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

import il.org.ay.navigation.model.CampusRoom;
import il.org.ay.navigation.model.Recent;

@Dao
public abstract class RecentDao {

    /** FR-02: the list keeps the latest 10 searches. */
    public static final int MAX_RECENT = 10;

    @Insert
    protected abstract void insert(Recent recent);

    @Query("DELETE FROM recent WHERE room_number = :number")
    protected abstract void deleteRoom(String number);

    @Query("DELETE FROM recent WHERE id NOT IN " +
           "(SELECT id FROM recent ORDER BY timestamp DESC LIMIT :keep)")
    protected abstract void trimTo(int keep);

    @Query("SELECT * FROM recent ORDER BY timestamp DESC")
    public abstract List<Recent> getAll();

    /** The recently chosen rooms themselves, latest first (JOIN recent -> rooms). */
    @Query("SELECT r.* FROM rooms r JOIN recent c ON c.room_number = r.number " +
           "ORDER BY c.timestamp DESC")
    public abstract List<CampusRoom> getRooms();

    /** Saves a search (or moves it to the top) and keeps only the latest {@link #MAX_RECENT}. */
    @Transaction
    public void add(Recent recent) {
        deleteRoom(recent.roomNumber);
        insert(recent);
        trimTo(MAX_RECENT);
    }
}
