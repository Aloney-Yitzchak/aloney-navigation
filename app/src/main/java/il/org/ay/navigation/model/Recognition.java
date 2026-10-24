package il.org.ay.navigation.model;

import androidx.annotation.NonNull;

/** What the building model saw in one frame (SDD 4, model.Recognition). Filled by T22. */
public final class Recognition {
    /** The building label, e.g. "science". */
    @NonNull
    public final String label;
    /** How sure the model is, 0–1. */
    public final float confidence;
    /** The graph node at the building's entrance, for correcting the position (SDD 10.1). */
    public final int nodeId;

    public Recognition(@NonNull String label, float confidence, int nodeId) {
        this.label = label;
        this.confidence = confidence;
        this.nodeId = nodeId;
    }
}
