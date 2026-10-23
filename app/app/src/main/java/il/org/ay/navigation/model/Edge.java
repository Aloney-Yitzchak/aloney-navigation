package il.org.ay.navigation.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A walkable path segment between two nodes. Paths are two-way, so each
 * segment is stored once. Room table {@code edges}; Firestore collection
 * {@code edges} (doc id = id, the row number in campus-data/edges.csv).
 */
@Entity(tableName = "edges",
        foreignKeys = {
                @ForeignKey(entity = Node.class, parentColumns = "id",
                        childColumns = "from_id", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Node.class, parentColumns = "id",
                        childColumns = "to_id", onDelete = ForeignKey.CASCADE)},
        indices = {@Index("from_id"), @Index("to_id")})
public class Edge {

    @PrimaryKey
    public int id;

    @ColumnInfo(name = "from_id")
    public int fromId;

    @ColumnInfo(name = "to_id")
    public int toId;

    /** Length in metres, computed from the node coordinates by campus-data/validate.py. */
    public double length;

    /** Required by Room and Firestore. */
    public Edge() {
    }

    @Ignore
    public Edge(int id, int fromId, int toId, double length) {
        this.id = id;
        this.fromId = fromId;
        this.toId = toId;
        this.length = length;
    }
}
