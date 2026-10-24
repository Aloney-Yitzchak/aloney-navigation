package il.org.ay.navigation.logic;

import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import il.org.ay.navigation.model.Recognition;

/**
 * Receives the camera frames for the building model (SDD 4, logic.BuildingRecognizer).
 *
 * <p>T16 builds the path: CameraX hands every frame to {@link #analyze} on a background
 * thread, and only the newest frame is kept, so a slow model never piles up frames. The
 * model itself (TensorFlow Lite) is trained in T21 and added here in T22; until then the
 * class counts the frames, which is how the camera path is tested.
 */
public class BuildingRecognizer implements ImageAnalysis.Analyzer {

    private volatile int frames;
    private volatile long firstFrameMs;
    private volatile long lastFrameMs;
    @Nullable
    private volatile Recognition last;

    @Override
    public void analyze(@NonNull ImageProxy image) {
        try {
            long now = SystemClock.elapsedRealtime();
            if (frames == 0) {
                firstFrameMs = now;
            }
            lastFrameMs = now;
            frames++;
            // T22: run the model on the image here and set `last`
        } finally {
            image.close();   // CameraX sends the next frame only after this one is closed
        }
    }

    /** SDD 4: getLast(). The last confident recognition, or null (always null until T22). */
    @Nullable
    public Recognition getLast() {
        return last;
    }

    /** Frames received so far. */
    public int frameCount() {
        return frames;
    }

    /** Frames per second since the first frame; 0 before two frames. */
    public double framesPerSecond() {
        long span = lastFrameMs - firstFrameMs;
        return frames < 2 || span <= 0 ? 0 : (frames - 1) * 1000.0 / span;
    }
}
