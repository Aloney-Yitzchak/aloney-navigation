package il.org.ay.navigation.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import il.org.ay.navigation.model.CampusRoom;

/**
 * The one place screens get campus data from (SDD 4, data layer).
 *
 * <p>Firestore is the shared source; Room holds the copy the app actually uses,
 * so navigation works without a network after the first sync (AT-09, NFR-06).
 * {@link #sync} copies from Firestore only when the cloud version is newer, and
 * rejects incomplete data, keeping the old copy. Every Room query runs on a
 * background executor; results come back on the main thread.
 */
public class CampusRepository {

    private static final String TAG = "CampusRepository";
    private static final String PREFS = "campus_sync";
    private static final String KEY_VERSION = "version";

    public enum SyncResult {
        /** A newer version was downloaded and saved. */
        UPDATED,
        /** The phone already has the latest version. */
        UP_TO_DATE,
        /** The cloud data was incomplete; the phone kept its old copy. */
        REJECTED_INCOMPLETE,
        /** No network or a server error; the phone kept its old copy. */
        FAILED
    }

    public interface Listener<T> {
        void onResult(T value);
    }

    private static volatile CampusRepository instance;

    private final AppDatabase db;
    private final CampusRemote remote;
    private final SharedPreferences prefs;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Executor main;

    CampusRepository(AppDatabase db, CampusRemote remote, SharedPreferences prefs, Executor main) {
        this.db = db;
        this.remote = remote;
        this.prefs = prefs;
        this.main = main;
    }

    public static CampusRepository get(Context context) {
        if (instance == null) {
            synchronized (CampusRepository.class) {
                if (instance == null) {
                    Context app = context.getApplicationContext();
                    Handler handler = new Handler(Looper.getMainLooper());
                    instance = new CampusRepository(AppDatabase.get(app),
                            new FirestoreSource(FirebaseFirestore.getInstance()),
                            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE), handler::post);
                }
            }
        }
        return instance;
    }

    /** The version of the copy in Room (0 = never synced). */
    public long localVersion() {
        return prefs.getLong(KEY_VERSION, 0);
    }

    /** Brings Room up to date with Firestore if the cloud version is newer. */
    public void sync(@NonNull Listener<SyncResult> listener) {
        remote.fetchVersion(new CampusRemote.Callback<Long>() {
            @Override
            public void onResult(Long cloudVersion) {
                if (cloudVersion <= localVersion()) {
                    deliver(listener, SyncResult.UP_TO_DATE);
                    return;
                }
                remote.fetchAll(new CampusRemote.Callback<CampusSnapshot>() {
                    @Override
                    public void onResult(CampusSnapshot snapshot) {
                        save(snapshot, listener);
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.w(TAG, "fetchAll failed", e);
                        deliver(listener, SyncResult.FAILED);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                Log.w(TAG, "fetchVersion failed (offline?)", e);
                deliver(listener, SyncResult.FAILED);
            }
        });
    }

    private void save(CampusSnapshot snapshot, Listener<SyncResult> listener) {
        List<String> problems = snapshot.problems();
        if (!problems.isEmpty()) {
            Log.w(TAG, "rejected version " + snapshot.version + ": " + problems);
            deliver(listener, SyncResult.REJECTED_INCOMPLETE);
            return;
        }
        io.execute(() -> {
            try {
                db.graphDao().replaceCampus(snapshot.nodes, snapshot.edges, snapshot.rooms);
                prefs.edit().putLong(KEY_VERSION, snapshot.version).apply();
                deliver(listener, SyncResult.UPDATED);
            } catch (RuntimeException e) {
                Log.w(TAG, "saving version " + snapshot.version + " failed", e);
                deliver(listener, SyncResult.REJECTED_INCOMPLETE);
            }
        });
    }

    /** FR-01: rooms whose number, name or owner contains the text. */
    public void search(String text, @NonNull Listener<List<CampusRoom>> listener) {
        io.execute(() -> deliver(listener, db.roomDao().search(text.trim())));
    }

    /** All rooms, for a quick count or a list. */
    public void allRooms(@NonNull Listener<List<CampusRoom>> listener) {
        io.execute(() -> deliver(listener, db.roomDao().getAll()));
    }

    private <T> void deliver(Listener<T> listener, T value) {
        main.execute(() -> listener.onResult(value));
    }
}
