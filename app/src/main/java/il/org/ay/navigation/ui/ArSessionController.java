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
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingFailureReason;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.NotYetAvailableException;

import java.util.Collections;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import il.org.ay.navigation.logic.ArAlignment;
import il.org.ay.navigation.logic.ArProjection;
import il.org.ay.navigation.logic.BuildingRecognizer;
import il.org.ay.navigation.logic.GroundPath;
import il.org.ay.navigation.logic.HeadingMath;

/**
 * The ARCore side of the camera screen (T18, T18c). Runs on the OpenGL thread, once per camera
 * frame: draws the camera image, keeps ARCore's world tied to north (ArAlignment), and draws
 * the next metres of the route as chevrons lying on the ground (ArGroundArrows).
 *
 * <p>The chevrons are placed once, around an ARCore anchor on the floor where the user stands,
 * and stay fixed in the world: GPS noise does not move them. They are placed again when the
 * user has walked on, or when the route ahead turned.
 */
class ArSessionController implements GLSurfaceView.Renderer {

    /** What the screen should show (T18c); also shown in the mode chip's window. */
    enum State {
        /** ARCore has not produced a frame yet. */
        STARTING,
        /** ARCore lost track of the phone's movement (see {@link #trackingFailure()}). */
        NOT_TRACKING,
        /** ARCore tracks, but north is not known yet: the phone has not been held up. */
        NO_NORTH,
        /** No route to show (arrived, off campus, or no destination). */
        NO_ROUTE,
        /** The chevrons are placed, but none is on the screen: the phone looks elsewhere. */
        OFF_SCREEN,
        /** The chevrons are on the screen. */
        GROUND
    }

    interface Listener {
        /** Called on the main thread when the state changes. */
        void onState(@NonNull State state);
    }

    /** A new set of chevrons once the user is this far from where the last set was placed… */
    static final float WALKED_M = 2.5f;
    /** …or when the route ahead turned more than this since then… */
    static final float TURNED_DEG = 25f;
    /** …or when north (the alignment) moved more than this since then (T18d). */
    static final float NORTH_MOVED_DEG = 8f;
    /** Without a floor plane, the floor is this far below the phone (held in front of the chest). */
    static final float PHONE_HEIGHT_M = 1.3f;
    /** Every n-th camera frame goes to the building model. */
    static final int RECOGNIZER_EVERY = 4;

    private final Session session;
    private final ArBackgroundRenderer background = new ArBackgroundRenderer();
    private final ArGroundArrows arrows = new ArGroundArrows();
    private final ArAlignment alignment = new ArAlignment();
    private final BuildingRecognizer recognizer;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final float[] view = new float[16];
    private final float[] projection = new float[16];
    private int width;
    private int height;
    private long frames;

    /** Written by the screen, read on the GL thread. Null = nothing to lead to. */
    @Nullable
    private volatile float[][][] path;
    private volatile float compassHeading = Float.NaN;
    private volatile int displayRotation;

    @Nullable
    private Anchor anchor;
    private List<GroundPath.Chevron> chevrons = Collections.emptyList();
    private float placedBearing = Float.NaN;
    private float placedOffset = Float.NaN;

    private volatile State state = State.STARTING;
    private volatile String trackingFailure = "";
    private volatile boolean floorFromPlane;
    private volatile int visibleChevrons;

    ArSessionController(@NonNull Session session, @NonNull BuildingRecognizer recognizer, @NonNull Listener listener) {
        this.session = session;
        this.recognizer = recognizer;
        this.listener = listener;
    }

    /** The route ahead from {@code NavigationManager.getPathAhead(GroundPath.ALONG, GroundPath.STEP)}. */
    void setPath(@Nullable float[][][] pathAhead) {
        path = pathAhead;
    }

    void setCompassHeading(float heading) {
        compassHeading = heading;
    }

    void setDisplayRotation(int rotation) {
        displayRotation = rotation;
    }

    // ------------------------------------------------------------ for the mode chip's window

    @NonNull
    State state() {
        return state;
    }

    /** ARCore's reason when it does not track ("" otherwise): INSUFFICIENT_FEATURES, EXCESSIVE_MOTION… */
    @NonNull
    String trackingFailure() {
        return trackingFailure;
    }

    boolean isNorthAligned() {
        return alignment.isReady();
    }

    /** Frames used so far to find north (ready at ArAlignment.SETTLE_FRAMES). */
    int northFrames() {
        return alignment.frames();
    }

    float northOffset() {
        return alignment.isReady() ? alignment.offset() : Float.NaN;
    }

    boolean floorFromPlane() {
        return floorFromPlane;
    }

    int visibleChevrons() {
        return visibleChevrons;
    }

    // ------------------------------------------------------------ OpenGL

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        session.setCameraTextureName(background.createOnGlThread());
        arrows.createOnGlThread();
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
        if (camera.getTrackingState() != TrackingState.TRACKING) {
            TrackingFailureReason why = camera.getTrackingFailureReason();
            trackingFailure = why == null ? "" : why.name();
            setState(State.NOT_TRACKING);
            return;
        }
        trackingFailure = "";
        Pose pose = camera.getPose();
        float[] z = pose.getZAxis();                 // the camera looks along −z
        float heading = compassHeading;
        if (!Float.isNaN(heading)) {
            alignment.update(-z[0], -z[1], -z[2], heading);
        }
        if (!alignment.isReady()) {
            setState(State.NO_NORTH);
            return;
        }
        float[][][] ahead = path;
        if (ahead == null) {
            setState(State.NO_ROUTE);
            return;
        }
        placeIfNeeded(pose, ahead);
        drawChevrons(camera, pose);
    }

    /** New chevrons around a new anchor when there are none, the user walked on, or the route turned. */
    private void placeIfNeeded(Pose camera, float[][][] ahead) {
        float bearing = firstBearing(ahead);
        boolean need = anchor == null || anchor.getTrackingState() == TrackingState.STOPPED || chevrons.isEmpty()
                || (!Float.isNaN(bearing) && !Float.isNaN(placedBearing)
                && Math.abs(HeadingMath.difference(placedBearing, bearing)) > TURNED_DEG)
                || Math.abs(HeadingMath.difference(placedOffset, alignment.offset())) > NORTH_MOVED_DEG;
        if (!need) {
            Pose a = anchor.getPose();
            need = Math.hypot(a.tx() - camera.tx(), a.tz() - camera.tz()) > WALKED_M;
        }
        if (!need) {
            return;
        }
        if (anchor != null) {
            anchor.detach();
        }
        anchor = session.createAnchor(Pose.makeTranslation(camera.tx(), floorY(camera), camera.tz()));
        chevrons = GroundPath.chevrons(ahead[0], ahead[1], GroundPath.ALONG, alignment.offset());
        placedBearing = bearing;
        placedOffset = alignment.offset();
    }

    /** The bearing of the route at the first chevron, to notice a turn. */
    private static float firstBearing(float[][][] ahead) {
        float de = ahead[1][0][0] - ahead[0][0][0];
        float dn = ahead[1][0][1] - ahead[0][0][1];
        if (Math.hypot(de, dn) < 0.05) {
            return Float.NaN;
        }
        return HeadingMath.normalize((float) Math.toDegrees(Math.atan2(de, dn)));
    }

    /**
     * The floor: the highest horizontal surface ARCore found at least 50 cm below the phone,
     * or 1.3 m below the phone when it found none yet.
     */
    private float floorY(Pose camera) {
        float best = Float.NaN;
        for (Plane plane : session.getAllTrackables(Plane.class)) {
            if (plane.getTrackingState() != TrackingState.TRACKING
                    || plane.getType() != Plane.Type.HORIZONTAL_UPWARD_FACING
                    || plane.getSubsumedBy() != null) {
                continue;
            }
            float y = plane.getCenterPose().ty();
            if (y < camera.ty() - 0.5f && (Float.isNaN(best) || y > best)) {
                best = y;
            }
        }
        floorFromPlane = !Float.isNaN(best);
        return floorFromPlane ? best : camera.ty() - PHONE_HEIGHT_M;
    }

    private void drawChevrons(Camera camera, Pose pose) {
        if (anchor == null || anchor.getTrackingState() != TrackingState.TRACKING) {
            setState(State.NOT_TRACKING);
            return;
        }
        camera.getViewMatrix(view, 0);
        camera.getProjectionMatrix(projection, 0, 0.1f, 100f);
        Pose a = anchor.getPose();
        int visible = 0;
        arrows.begin();
        for (GroundPath.Chevron c : chevrons) {
            float x = a.tx() + c.x;
            float y = a.ty() + 0.02f;   // just above the floor
            float z = a.tz() + c.z;
            float distance = (float) Math.sqrt(sq(x - pose.tx()) + sq(y - pose.ty()) + sq(z - pose.tz()));
            float alpha = GroundPath.alpha(distance);
            if (alpha <= 0f) {
                continue;
            }
            arrows.draw(view, projection, x, y, z, c.yawDegrees(), alpha);
            float[] screen = ArProjection.toScreen(view, projection, x, y, z, width, height);
            if (screen != null && screen[0] >= 0 && screen[0] <= width && screen[1] >= 0 && screen[1] <= height) {
                visible++;
            }
        }
        arrows.end();
        visibleChevrons = visible;
        setState(visible > 0 ? State.GROUND : State.OFF_SCREEN);
    }

    private static float sq(float v) {
        return v * v;
    }

    private void setState(State s) {
        if (state != s) {
            state = s;
            main.post(() -> listener.onState(s));
        }
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
