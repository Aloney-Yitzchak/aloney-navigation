package il.org.ay.navigation.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A room on campus (a classroom, an office, the library).
 * Named CampusRoom so it doesn't clash with the Room library (decision #9).
 *
 * <p>The same class is the Room entity (table {@code rooms}) and the Firestore
 * document in the {@code rooms} collection: Firestore uses the camelCase field
 * names, Room uses the snake_case column names.
 */
@Entity(tableName = "rooms",
        foreignKeys = @ForeignKey(entity = Node.class,
                parentColumns = "id",
                childColumns = "entrance_node_id",
                onDelete = ForeignKey.SET_NULL),
        indices = @Index("entrance_node_id"))
public class CampusRoom {

    /** The physical room number as printed on the plan ("36", "ש36"). Stable across years. */
    @PrimaryKey
    @NonNull
    public String number = "";

    /** What is in the room this year, e.g. "ט2" or "ספרייה". */
    @Nullable
    public String name;

    /** Who is responsible for the room, e.g. "רכזת שכבה ט'". Optional. */
    @Nullable
    public String owner;

    /** The path node at the room's entrance; null until the room is mapped. */
    @Nullable
    @ColumnInfo(name = "entrance_node_id")
    public Integer entranceNodeId;

    /** The school year whose plan this assignment comes from, e.g. "tashpaz". */
    @Nullable
    @ColumnInfo(name = "plan_year")
    public String planYear;

    /** Required by Room and Firestore. */
    public CampusRoom() {
    }

    @Ignore
    public CampusRoom(@NonNull String number, @Nullable String name, @Nullable String owner,
                      @Nullable Integer entranceNodeId, @Nullable String planYear) {
        this.number = number;
        this.name = name;
        this.owner = owner;
        this.entranceNodeId = entranceNodeId;
        this.planYear = planYear;
    }

    /** A room can be a navigation target only once its entrance is mapped. */
    public boolean isMapped() {
        return entranceNodeId != null;
    }
}
