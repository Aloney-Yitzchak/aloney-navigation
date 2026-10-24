package il.org.ay.navigation.model;

import androidx.annotation.Nullable;

/** Result of the rooms ⋈ nodes JOIN (rubric 6.4): a room and where its entrance is. */
public class RoomWithEntrance {
    public String number;
    @Nullable
    public String name;
    @Nullable
    public String owner;
    public int entranceNodeId;
    public double lat;
    public double lng;
}
