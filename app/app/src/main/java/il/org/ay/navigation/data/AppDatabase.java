package il.org.ay.navigation.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import il.org.ay.navigation.model.CampusRoom;
import il.org.ay.navigation.model.Edge;
import il.org.ay.navigation.model.Favorite;
import il.org.ay.navigation.model.Node;
import il.org.ay.navigation.model.Recent;

/**
 * The local Room database (SDD 8). rooms, nodes and edges are a copy of
 * Firestore; favorites and recent exist only on the phone.
 */
@Database(entities = {CampusRoom.class, Node.class, Edge.class, Favorite.class, Recent.class},
        version = 1, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    private static final String NAME = "campus.db";
    private static volatile AppDatabase instance;

    public abstract RoomDao roomDao();

    public abstract GraphDao graphDao();

    public abstract FavoriteDao favoriteDao();

    public abstract RecentDao recentDao();

    public static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, NAME).build();
                }
            }
        }
        return instance;
    }
}
