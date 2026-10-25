package il.org.ay.navigation.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.Locale;

/**
 * T20a: one building to photograph for the building model (SDD 10, step 1). Stored in
 * Firestore, collection {@code photoTasks}, document id = {@link #label}; staff add and edit
 * them on the staff screen, and every phone sees the same list.
 *
 * <p>The label is also the folder name in the exported zip and the line in ml/labels.txt, so
 * it is plain ASCII and never changes; the Hebrew name can be edited freely.
 */
public class PhotoTask {

    /** Paths, trees and sky: what the model must learn is "no building". Always on the list. */
    public static final String OTHER = "other";
    private static final String BUILDING_PREFIX = "building_";

    @NonNull
    public String label = "";
    /** What staff see, e.g. "בניין המדעים". */
    @Nullable
    public String name;
    /** How many photos are wanted (SDD 10: 30-50 per building). */
    public int target;
    /** Where to stand, what to include. */
    @Nullable
    public String note;
    /** Position in the list. "other" is last. */
    public int order;
    /** Photos reported by all phones together (each photo adds one). */
    public int taken;

    /** Required by Firestore. */
    public PhotoTask() {
    }

    public PhotoTask(@NonNull String label, @Nullable String name, int target, @Nullable String note, int order) {
        this.label = label;
        this.name = name;
        this.target = target;
        this.note = note;
        this.order = order;
    }

    public boolean isOther() {
        return OTHER.equals(label);
    }

    /** building_01, building_02, … : the first number not used by any task yet. */
    @NonNull
    public static String nextLabel(@NonNull Collection<PhotoTask> existing) {
        int n = 1;
        while (true) {
            String label = String.format(Locale.ROOT, "%s%02d", BUILDING_PREFIX, n);
            boolean used = false;
            for (PhotoTask t : existing) {
                if (t.label.equals(label)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return label;
            }
            n++;
        }
    }

    /** The order for a new building: after the other buildings, before "other". */
    public static int nextOrder(@NonNull Collection<PhotoTask> existing) {
        int max = 0;
        for (PhotoTask t : existing) {
            if (!t.isOther()) {
                max = Math.max(max, t.order);
            }
        }
        return max + 1;
    }
}
