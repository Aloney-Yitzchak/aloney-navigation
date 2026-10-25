package il.org.ay.navigation.data;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

import il.org.ay.navigation.model.PhotoTask;

/** {@link PhotoTasks} on Firestore. */
class FirestorePhotoTasks extends PhotoTasks {

    static final String COLLECTION = "photoTasks";
    static final int OTHER_ORDER = 1000;
    static final int DEFAULT_TARGET = 40;

    private final FirebaseFirestore db;

    FirestorePhotoTasks(FirebaseFirestore db) {
        this.db = db;
    }

    @NonNull
    @Override
    public Subscription listen(@NonNull CampusRepository.Listener<List<PhotoTask>> listener) {
        ListenerRegistration registration = db.collection(COLLECTION).orderBy("order")
                .addSnapshotListener((snapshot, error) -> {
                    if (snapshot == null) {
                        return;   // no permission or no network and nothing cached: keep what is shown
                    }
                    List<PhotoTask> tasks = new ArrayList<>();
                    boolean hasOther = false;
                    for (DocumentSnapshot d : snapshot.getDocuments()) {
                        PhotoTask t = toTask(d);
                        tasks.add(t);
                        hasOther |= t.isOther();
                    }
                    if (!hasOther && !snapshot.getMetadata().isFromCache()) {
                        // the first time: the "no building" class (SDD 10, step 1)
                        PhotoTask other = new PhotoTask(PhotoTask.OTHER, "אחר: שבילים, עצים, שמיים",
                                DEFAULT_TARGET, "כל מה שאינו מבנה: שבילים, דשא, עצים, שמיים, גדרות", OTHER_ORDER);
                        db.collection(COLLECTION).document(PhotoTask.OTHER).set(fields(other));
                    }
                    listener.onResult(tasks);
                });
        return registration::remove;
    }

    @Override
    public void save(@NonNull PhotoTask task,
                     @NonNull CampusRepository.Listener<CampusRepository.SaveResult> listener) {
        // merge: "taken" is counted by the phones and must not be reset by an edit
        db.collection(COLLECTION).document(task.label)
                .set(fields(task), com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(v -> listener.onResult(CampusRepository.SaveResult.SAVED))
                .addOnFailureListener(e -> listener.onResult(FirestoreSource.toSaveResult(e)));
    }

    @Override
    public void delete(@NonNull String label,
                       @NonNull CampusRepository.Listener<CampusRepository.SaveResult> listener) {
        db.collection(COLLECTION).document(label).delete()
                .addOnSuccessListener(v -> listener.onResult(CampusRepository.SaveResult.SAVED))
                .addOnFailureListener(e -> listener.onResult(FirestoreSource.toSaveResult(e)));
    }

    @Override
    public void countPhoto(@NonNull String label, int delta) {
        db.collection(COLLECTION).document(label).update("taken", FieldValue.increment(delta));
    }

    private static java.util.Map<String, Object> fields(PhotoTask t) {
        java.util.Map<String, Object> f = new java.util.HashMap<>();
        f.put("name", t.name);
        f.put("target", t.target);
        f.put("note", t.note);
        f.put("order", t.order);
        return f;
    }

    static PhotoTask toTask(DocumentSnapshot d) {
        PhotoTask t = new PhotoTask();
        t.label = d.getId();
        t.name = d.getString("name");
        t.note = d.getString("note");
        Long target = d.getLong("target");
        t.target = target == null ? DEFAULT_TARGET : target.intValue();
        Long order = d.getLong("order");
        t.order = order == null ? 0 : order.intValue();
        Long taken = d.getLong("taken");
        t.taken = taken == null ? 0 : taken.intValue();
        return t;
    }
}
