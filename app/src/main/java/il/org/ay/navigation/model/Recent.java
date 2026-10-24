package il.org.ay.navigation.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A room the user searched for recently (FR-02). Exists only on the phone.
 * RecentDao keeps one row per room and only the latest 10.
 */
@Entity(tableName = "recent",
        foreignKeys = @ForeignKey(entity = CampusRoom.class,
                parentColumns = "number",
                childColumns = "room_number",
                onDelete = ForeignKey.CASCADE),
        indices = @Index("room_number"))
public class Recent {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    @ColumnInfo(name = "room_number")
    public String roomNumber = "";

    public long timestamp;

    public Recent() {
    }

    @Ignore
    public Recent(@NonNull String roomNumber, long timestamp) {
        this.roomNumber = roomNumber;
        this.timestamp = timestamp;
    }
}
