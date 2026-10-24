package il.org.ay.navigation.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import il.org.ay.navigation.model.CampusRoom;
import il.org.ay.navigation.model.Favorite;

@Dao
public interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void add(Favorite favorite);

    @Query("SELECT * FROM favorites ORDER BY timestamp DESC")
    List<Favorite> getAll();

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE room_number = :number)")
    boolean isFavorite(String number);

    @Delete
    void remove(Favorite favorite);

    /** The starred rooms themselves, newest star first (JOIN favorites -> rooms). */
    @Query("SELECT r.* FROM rooms r JOIN favorites f ON f.room_number = r.number " +
           "ORDER BY f.timestamp DESC")
    List<CampusRoom> getRooms();

    /** Only the numbers, to draw a filled star next to each starred room in the results. */
    @Query("SELECT room_number FROM favorites")
    List<String> getNumbers();
}
