package il.org.ay.navigation.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

import il.org.ay.navigation.model.PhotoTask;

/**
 * T20a: the shared list of buildings to photograph, Firestore collection {@code photoTasks}.
 * Read and written only from the staff screens; the security rules allow writes only with
 * the staff claim, like the rest of the campus data (NFR-10).
 */
public abstract class PhotoTasks {

    /** Stops listening. */
    public interface Subscription {
        void remove();
    }

    private static PhotoTasks instance;

    public static synchronized PhotoTasks get() {
        if (instance == null) {
            instance = new FirestorePhotoTasks(FirebaseFirestore.getInstance());
        }
        return instance;
    }

    @VisibleForTesting
    public static synchronized void useForTesting(@Nullable PhotoTasks fake) {
        instance = fake;
    }

    /**
     * The tasks in their order, now and after every change by any phone. Works offline from
     * Firestore's cache once the list was seen; "other" is added if the list has none.
     */
    @NonNull
    public abstract Subscription listen(@NonNull CampusRepository.Listener<List<PhotoTask>> listener);

    /** Adds or changes a task (staff only). */
    public abstract void save(@NonNull PhotoTask task, @NonNull CampusRepository.Listener<CampusRepository.SaveResult> listener);

    /** Removes a task. Photos already taken stay on the phones and are still exported. */
    public abstract void delete(@NonNull String label, @NonNull CampusRepository.Listener<CampusRepository.SaveResult> listener);

    /**
     * One more photo of this label from this phone (+1), or one deleted right after it was
     * taken (-1). Queued by Firestore when offline.
     */
    public abstract void countPhoto(@NonNull String label, int delta);
}
