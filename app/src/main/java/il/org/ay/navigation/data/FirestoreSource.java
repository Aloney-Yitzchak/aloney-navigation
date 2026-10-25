package il.org.ay.navigation.data;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import il.org.ay.navigation.model.CampusRoom;
import il.org.ay.navigation.model.Edge;
import il.org.ay.navigation.model.Node;

/**
 * Reads the campus data from Firestore (SDD 8.1). Collections: nodes, edges,
 * rooms; the doc id is the key. meta/version says which version is in the cloud.
 * Always reads from the server: the offline copy is Room's job, not Firestore's cache.
 */
public class FirestoreSource implements CampusRemote {

    private final FirebaseFirestore db;

    public FirestoreSource(FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public void fetchVersion(Callback<Long> callback) {
        db.collection("meta").document("version").get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    Long number = doc.exists() ? doc.getLong("number") : null;
                    callback.onResult(number == null ? 0L : number);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void fetchAll(Callback<CampusSnapshot> callback) {
        Task<DocumentSnapshot> version = db.collection("meta").document("version").get(Source.SERVER);
        Task<QuerySnapshot> nodes = db.collection("nodes").get(Source.SERVER);
        Task<QuerySnapshot> edges = db.collection("edges").get(Source.SERVER);
        Task<QuerySnapshot> rooms = db.collection("rooms").get(Source.SERVER);
        Tasks.whenAllSuccess(version, nodes, edges, rooms)
                .addOnSuccessListener(results -> {
                    try {
                        Long number = version.getResult().getLong("number");
                        callback.onResult(new CampusSnapshot(number == null ? 0L : number,
                                toNodes(nodes.getResult()), toEdges(edges.getResult()),
                                toRooms(rooms.getResult())));
                    } catch (RuntimeException e) {
                        callback.onError(e);  // a field of the wrong type: treat as incomplete
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    /** How long to wait for the server before saying the change is waiting to be sent. */
    static final long SAVE_TIMEOUT_MS = 15_000;

    @Override
    public void updateRoom(String number, String name, String owner,
                           Callback<CampusRepository.SaveResult> callback) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", name);
        fields.put("owner", owner.isEmpty() ? null : owner);
        WriteBatch batch = db.batch();
        batch.update(db.collection("rooms").document(number), fields);
        batch.update(db.collection("meta").document("version"), "number", FieldValue.increment(1));

        // Firestore keeps a write that could not be sent and sends it when the network is back;
        // until then the task does not finish. After a while the screen says so, once.
        AtomicBoolean answered = new AtomicBoolean();
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable timeout = () -> {
            if (answered.compareAndSet(false, true)) {
                callback.onResult(CampusRepository.SaveResult.PENDING);
            }
        };
        handler.postDelayed(timeout, SAVE_TIMEOUT_MS);
        batch.commit()
                .addOnSuccessListener(v -> {
                    handler.removeCallbacks(timeout);
                    if (answered.compareAndSet(false, true)) {
                        callback.onResult(CampusRepository.SaveResult.SAVED);
                    }
                })
                .addOnFailureListener(e -> {
                    handler.removeCallbacks(timeout);
                    if (answered.compareAndSet(false, true)) {
                        callback.onResult(toSaveResult(e));
                    }
                });
    }

    static CampusRepository.SaveResult toSaveResult(Exception e) {
        if (e instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) e).getCode()) {
                case PERMISSION_DENIED:
                case UNAUTHENTICATED:
                    return CampusRepository.SaveResult.NOT_ALLOWED;   // the rules said no (AT-10)
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    return CampusRepository.SaveResult.NO_NETWORK;
                case NOT_FOUND:
                    return CampusRepository.SaveResult.NO_SUCH_ROOM;
                default:
                    break;
            }
        }
        return CampusRepository.SaveResult.FAILED;
    }

    static List<Node> toNodes(QuerySnapshot snapshot) {
        List<Node> list = new ArrayList<>();
        for (DocumentSnapshot d : snapshot.getDocuments()) {
            list.add(new Node(Integer.parseInt(d.getId()), required(d, "lat"), required(d, "lng")));
        }
        return list;
    }

    static List<Edge> toEdges(QuerySnapshot snapshot) {
        List<Edge> list = new ArrayList<>();
        for (DocumentSnapshot d : snapshot.getDocuments()) {
            list.add(new Edge(Integer.parseInt(d.getId()), (int) requiredLong(d, "fromId"),
                    (int) requiredLong(d, "toId"), required(d, "length")));
        }
        return list;
    }

    static List<CampusRoom> toRooms(QuerySnapshot snapshot) {
        List<CampusRoom> list = new ArrayList<>();
        for (DocumentSnapshot d : snapshot.getDocuments()) {
            Long entrance = d.getLong("entranceNodeId");
            list.add(new CampusRoom(d.getId(), d.getString("name"), d.getString("owner"),
                    entrance == null ? null : entrance.intValue(), d.getString("planYear")));
        }
        return list;
    }

    private static double required(DocumentSnapshot d, String field) {
        Double value = d.getDouble(field);
        if (value == null) throw new IllegalStateException(d.getReference().getPath() + " has no " + field);
        return value;
    }

    private static long requiredLong(DocumentSnapshot d, String field) {
        @Nullable Long value = d.getLong(field);
        if (value == null) throw new IllegalStateException(d.getReference().getPath() + " has no " + field);
        return value;
    }
}
