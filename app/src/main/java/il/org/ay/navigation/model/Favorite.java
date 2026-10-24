package il.org.ay.navigation.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * A room the user starred (FR-02). Exists only on the phone, in Room.
 * If a room is removed from the campus data, its favorite is removed with it.
 */
@Entity(tableName = "favorites",
        foreignKeys = @ForeignKey(entity = CampusRoom.class,
                parentColumns = "number",
                childColumns = "room_number",
                onDelete = ForeignKey.CASCADE))
public class Favorite {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "room_number")
    public String roomNumber = "";

    public long timestamp;

    public Favorite() {
    }

    @Ignore
    public Favorite(@NonNull String roomNumber, long timestamp) {
        this.roomNumber = roomNumber;
        this.timestamp = timestamp;
    }
}
