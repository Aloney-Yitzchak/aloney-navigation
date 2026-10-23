package il.org.ay.navigation.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

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
}
