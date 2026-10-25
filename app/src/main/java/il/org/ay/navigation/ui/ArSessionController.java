package il.org.ay.navigation.ui;

import android.media.Image;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.NotYetAvailableException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import il.org.ay.navigation.logic.ArAlignment;
import il.org.ay.navigation.logic.ArProjection;
import il.org.ay.navigation.logic.BuildingRecognizer;
import il.org.ay.navigation.logic.HeadingMath;

/**
 * The ARCore side of the camera screen (T18). Runs on the OpenGL thread, once per camera
 * frame: draws the camera image, keeps ARCore's world tied to north (ArAlignment), keeps an
 * anchor on the ground a few metres ahead along the path, and tells the screen where that
 * anchor is on the screen, so the arrow stays put in the world while the phone moves.
 */
class ArSessionController implements GLSurfaceView.Renderer {

    /** Where the screen should draw the arrow, or that it should not. */
    interface Listener {
        /** x, y: screen pixels; angle: degrees clockwise from up; scale: 1 = 3 m away. */
        void onArrow(float x, float y, float angle, float scale);

        void onNoArrow();
    }

    /** How far ahead the arrow is placed, along the direction to the next point. */
    static final float AHEAD_M = 3f;
    /** A phone held in front of the chest is about this high above the ground. */
    static final float PHONE_HEIGHT_M = 1.3f;
    /** A new anchor when the user is this close to the old one… */
    static final float REACHED_M = 1.5f;
    /** …or when the direction to the next point turned more than this since it was placed. */
    static final float TURNED_DEG = 25f;
    /** Every n-th camera frame goes to the building model. */
    static final int RECOGNIZER_EVERY = 4;

    private final Session session;
    private final ArBackgroundRenderer background = new ArBackgroundRenderer();
    private final ArAlignment alignment = new ArAlignment();
    private final BuildingRecognizer recognizer;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final float[] view = new float[16];
    private final float[] projection = new float[16];
    private int width;
    private int height;
    private long frames;

    /** Written by the screen, read on the GL thread. NaN = nothing to point at. */
    private volatile float targetBearing = Float.NaN;
    private volatile float compassHeading = Float.NaN;
    private volatile int displayRotation;

    @Nullable
    private Anchor anchor;
    private float anchorBearing;
    private volatile boolean tracking;

    ArSessionController(@NonNull Session session, @NonNull BuildingRecognizer recognizer, @NonNull Listener listener) {
        this.session = session;
        this.recognizer = recognizer;
        this.listener = listener;
    }

    /** The bearing (true north) from the user to the point the arrow should lead to. */
    void setTarget(float bearing) {
        targetBearing = bearing;
    }

    void setCompassHeading(float heading) {
        compassHeading = heading;
    }

    void setDisplayRotation(int rotation) {
        displayRotation = rotation;
    }

    boolean isTracking() {
        return tracking;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        session.setCameraTextureName(background.createOnGlThread());
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        width = w;
        height = h;
        GLES20.glViewport(0, 0, w, h);
        session.setDisplayGeometry(displayRotation, w, h);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        Frame frame;
        try {
            frame = session.update();
        } catch (Exception e) {
            return;   // the camera is not ready, or the screen is closing
        }
        background.draw(frame);
        sendToRecognizer(frame);

        Camera camera = frame.getCamera();
        tracking = camera.getTrackingState() == TrackingState.TRACKING;
        if (!tracking) {
            hideArrow();
            return;
        }
        Pose pose = camera.getPose();
        float[] z = pose.getZAxis();                 // the camera looks along −z
        float heading = compassHeading;
        if (!Float.isNaN(heading)) {
            alignment.update(-z[0], -z[1], -z[2], heading);
        }
        float bearing = targetBearing;
        if (!alignment.isReady() || Float.isNaN(bearing)) {
            hideArrow();
            return;
        }
        placeAnchorIfNeeded(pose, bearing);
        drawArrow(camera, bearing);
    }

    /** A new anchor 3 m ahead on the ground when there is none, it was reached, or the way turned. */
    private void placeAnchorIfNeeded(Pose camera, float bearing) {
        boolean need = anchor == null
                || anchor.getTrackingState() == TrackingState.STOPPED
                || Math.abs(HeadingMath.difference(anchorBearing, bearing)) > TURNED_DEG;
        if (!need) {
            Pose a = anchor.getPose();
            need = Math.hypot(a.tx() - camera.tx(), a.tz() - camera.tz()) < REACHED_M;
        }
        if (!need) {
            return;
        }
        if (anchor != null) {
            anchor.detach();
        }
        float[] d = alignment.direction(bearing);
        Pose ground = Pose.makeTranslation(
                camera.tx() + d[0] * AHEAD_M, camera.ty() - PHONE_HEIGHT_M, camera.tz() + d[1] * AHEAD_M);
        anchor = session.createAnchor(ground);
        anchorBearing = bearing;
    }

    /** Projects the anchor and a point 1 m further along the way; the arrow goes from one to the other. */
    private void drawArrow(Camera camera, float bearing) {
        if (anchor == null || anchor.getTrackingState() != TrackingState.TRACKING) {
            hideArrow();
            return;
        }
        camera.getViewMatrix(view, 0);
        camera.getProjectionMatrix(projection, 0, 0.1f, 100f);
        Pose a = anchor.getPose();
        float[] d = alignment.direction(bearing);
        float[] from = {a.tx(), a.ty(), a.tz()};
        float[] to = {a.tx() + d[0], a.ty(), a.tz() + d[1]};
        float[] screen = ArProjection.toScreen(view, projection, from[0], from[1], from[2], width, height);
        float angle = ArProjection.screenAngle(view, projection, from, to, width, height);
        if (screen == null || Float.isNaN(angle)
                || screen[0] < 0 || screen[0] > width || screen[1] < 0 || screen[1] > height) {
            hideArrow();   // behind the user or off the screen: the compass arrow takes over
            return;
        }
        float scale = Math.max(0.4f, Math.min(1.6f, AHEAD_M / screen[2]));
        float x = screen[0];
        float y = screen[1];
        main.post(() -> listener.onArrow(x, y, angle, scale));
    }

    private void hideArrow() {
        main.post(listener::onNoArrow);
    }

    /** Every few frames, the camera image goes to the building model (T22). */
    private void sendToRecognizer(Frame frame) {
        if (++frames % RECOGNIZER_EVERY != 0) {
            return;
        }
        try (Image image = frame.acquireCameraImage()) {
            recognizer.analyze(image);
        } catch (NotYetAvailableException | IllegalStateException e) {
            // no image this frame
        }
    }

    /** Releases the anchor; the session itself is closed by the screen. */
    void release() {
        if (anchor != null) {
            anchor.detach();
            anchor = null;
        }
    }
}
