package il.org.ay.navigation.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import il.org.ay.navigation.R;
import il.org.ay.navigation.data.CampusRepository;
import il.org.ay.navigation.logic.ArStatus;
import il.org.ay.navigation.logic.BuildingRecognizer;
import il.org.ay.navigation.logic.GroundPath;
import il.org.ay.navigation.logic.Guidance;
import il.org.ay.navigation.logic.HeadingService;
import il.org.ay.navigation.logic.LocationService;
import il.org.ay.navigation.logic.NavState;
import il.org.ay.navigation.logic.NavigationManager;
import il.org.ay.navigation.logic.WalkingEstimate;
import il.org.ay.navigation.model.CampusRoom;

/**
 * The camera screen (T16, FR-04; layout activity_ar_nav.xml). The live camera fills the
 * screen (design finding 6), and every frame also goes to BuildingRecognizer (T22).
 * The camera is bound to this screen's lifecycle: it runs only while the screen is
 * visible (NFR-08).
 *
 * <p>T17 (FR-04, AT-04): with a destination, positions from LocationService and the heading
 * from HeadingService go to NavigationManager, and a loop redraws the arrow, the instruction
 * and the distances {@link #FRAMES_PER_SECOND} times a second (NFR-01: at least 10).
 * Steps move the position between GPS fixes; their permission is asked here, once (decision #13).
 *
 * <p>T18: on a phone with ARCore installed, ARCore owns the camera (ArSessionController): the
 * camera image is drawn with OpenGL and the arrow is anchored on the ground a few metres ahead
 * along the path. Without ARCore, or while ARCore is still finding its bearings, the compass
 * arrow of T17 is shown (FR-11, AT-11).
 */
public class ArNavActivity extends BaseActivity {

    public static final String EXTRA_ROOM = MapActivity.EXTRA_ROOM;

    /** Frames for the model: small is enough, and keeps the analysis fast. */
    static final Size ANALYSIS_SIZE = new Size(640, 480);

    private final BuildingRecognizer recognizer = new BuildingRecognizer();
    private final ExecutorService analysisThread = Executors.newSingleThreadExecutor();
    private PreviewView preview;

    // ------------------------------------------------------------ T18: ARCore
    /** For tests: never use ARCore, as on a phone without it (AT-11). */
    @VisibleForTesting
    static boolean forceCompassMode;
    private static final String AR_TAG = "ArNav";
    /** T18b: why ARCore or the compass, for the mode chip. */
    private ArStatus arStatus = ArStatus.CHECKING;
    @Nullable
    private String arError;
    @Nullable
    private com.google.ar.core.Session arSession;
    @Nullable
    private android.opengl.GLSurfaceView arView;
    @Nullable
    private ArSessionController arController;
    /** T18c: what the ARCore side shows; the compass arrow is shown unless it is GROUND. */
    private ArSessionController.State groundState = ArSessionController.State.STARTING;
    private boolean installRequested;
    @Nullable
    private Camera camera;
    private boolean torchOn;
    @Nullable
    private CampusRoom room;
    private CharSequence roomName = "";

    /** NFR-01 asks for at least 10 updates a second. */
    static final int FRAMES_PER_SECOND = 20;
    private static final String PREFS = "ar";
    private static final String KEY_ASKED_STEPS = "asked_steps";

    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable frame = this::drawFrame;
    private android.widget.ImageView arrow;
    private View instructionCard;
    private HeadingService heading;
    @Nullable
    private NavigationManager nav;
    @Nullable
    private LocationService location;
    private float lastHeading = Float.NaN;
    private boolean compassPoor;
    private boolean declinationSet;
    private double accuracy = -1;
    private int frames;

    private final android.content.ServiceConnection connection = new android.content.ServiceConnection() {
        @Override
        public void onServiceConnected(android.content.ComponentName name, android.os.IBinder binder) {
            location = ((LocationService.LocalBinder) binder).getService();
            location.addListener(ArNavActivity.this::onPosition);
        }

        @Override
        public void onServiceDisconnected(android.content.ComponentName name) {
            location = null;
        }
    };

    private final ActivityResultLauncher<String> stepsPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {      // register the step sensor now that it is allowed
                    heading.stop();
                    heading.start();
                }
            });

    private final ActivityResultLauncher<String> cameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    chooseCameraMode();
                } else {
                    explainNoCamera();
                }
            });

    /** Opens the camera screen with a destination. */
    public static Intent navigateTo(@NonNull Context context, @Nullable String roomNumber) {
        Intent i = new Intent(context, ArNavActivity.class);
        if (roomNumber != null) {
            i.putExtra(EXTRA_ROOM, roomNumber);
        }
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar_nav);

        preview = new PreviewView(this);
        preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        // TextureView: scales to fill on every phone (the faster SurfaceView left bands on some)
        preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        preview.setBackgroundColor(android.graphics.Color.BLACK);
        FrameLayout box = findViewById(R.id.camera_preview);
        box.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // T18: the arrow on the ground, moved by ArSessionController every camera frame

        arrow = findViewById(R.id.ar_arrow);
        instructionCard = (View) findViewById(R.id.ar_instruction).getParent().getParent();
        // shown by the loop when there is something to show
        for (int id : new int[]{R.id.ar_arrow, R.id.ar_gps_accuracy, R.id.ar_heading, R.id.ar_calibrate}) {
            findViewById(id).setVisibility(View.GONE);
        }
        instructionCard.setVisibility(View.GONE);
        findViewById(R.id.ar_detected).setVisibility(View.GONE);   // T22: the building model
        findViewById(R.id.ar_sound).setVisibility(View.GONE);      // spoken instructions: not planned
        findViewById(R.id.ar_calibrate).setOnClickListener(v -> explainCompassCalibration());
        findViewById(R.id.ar_mode).setOnClickListener(v -> explainArMode());   // T18b
        heading = new HeadingService(this);
        heading.addListener(new HeadingService.Listener() {
            @Override
            public void onHeading(float degrees, boolean needsCalibration) {
                if (nav != null) {
                    nav.setHeading(degrees);
                }
                lastHeading = degrees;
                compassPoor = needsCalibration;
            }

            @Override
            public void onStep(int steps) {
                if (nav != null) {
                    nav.onStep();
                }
            }
        });

        findViewById(R.id.ar_end).setOnClickListener(v -> {
            if (nav != null) {
                nav.cancel();   // SDD 6: "the user cancels"
            }
            finish();
        });
        findViewById(R.id.ar_show_map).setOnClickListener(v -> openMap());
        findViewById(R.id.ar_flash).setOnClickListener(v -> toggleTorch());

        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(R.id.nav_ar);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_search) {
                startActivity(new Intent(this, SearchActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                finish();
            } else if (item.getItemId() == R.id.nav_map) {
                openMap();
            } else if (item.getItemId() == R.id.nav_staff) {
                startActivity(new Intent(this, AdminActivity.class));   // T24
            } else if (item.getItemId() != R.id.nav_ar) {
                Snackbar.make(preview, R.string.coming_soon, Snackbar.LENGTH_SHORT).setAnchorView(R.id.bottom_nav).show();
            }
            return false;
        });

        showDestination(null);
        String number = getIntent().getStringExtra(EXTRA_ROOM);
        if (number != null) {
            CampusRepository.get(this).findRoom(number, this::showDestination);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            chooseCameraMode();
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    /** The destination bar and card; without a destination, a hint to choose one. */
    private void showDestination(@Nullable CampusRoom r) {
        room = r;
        TextView bar = findViewById(R.id.ar_destination);
        View card = findViewById(R.id.ar_room_number).getParent() instanceof View
                ? (View) findViewById(R.id.ar_room_number).getParent() : null;
        findViewById(R.id.ar_destination_distance).setVisibility(View.GONE);   // the loop shows them
        findViewById(R.id.ar_time_left).setVisibility(View.GONE);
        if (r == null) {
            bar.setText(R.string.ar_choose_destination);
            if (card != null) {
                card.setVisibility(View.GONE);
            }
            return;
        }
        CharSequence name = RoomAdapter.displayName(bar, r);
        roomName = name;
        bar.setText(getString(R.string.ar_destination, name));   // finding 7: the real destination
        startNavigation(r);
        if (card != null) {
            card.setVisibility(View.VISIBLE);
        }
        ((TextView) findViewById(R.id.ar_room_number)).setText(r.number);
        ((TextView) findViewById(R.id.ar_room_name)).setText(name);
        TextView owner = findViewById(R.id.ar_room_owner);
        owner.setText(r.owner);
        owner.setVisibility(r.owner == null || r.owner.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------ T17: navigation

    private void startNavigation(@NonNull CampusRoom r) {
        if (r.entranceNodeId == null) {
            return;
        }
        CampusRepository.get(this).loadGraph(g -> {
            if (g.node(r.entranceNodeId) == null) {
                return;
            }
            nav = new NavigationManager(g, r.entranceNodeId, new NavigationManager.Listener() {
                @Override
                public void onRouteChanged(@NonNull il.org.ay.navigation.data.Route route, boolean recalculated) {
                }

                @Override
                public void onStateChanged(@NonNull NavState state) {
                    if (state == NavState.ARRIVED) {
                        ArrivalDialog.show(getSupportFragmentManager(), roomName, r, nav);   // T19, FR-06
                    }
                }
            });
            if (location != null && location.getLast() != null) {
                onPosition(location.getLast());
            }
        });
        askForStepsOnce();
    }

    private void onPosition(@NonNull LocationService.Position p) {
        accuracy = p.accuracy;
        if (!declinationSet) {
            heading.setPosition(p.smoothed.lat, p.smoothed.lng);   // magnetic → true north
            declinationSet = true;
        }
        if (nav != null) {
            nav.update(p.smoothed);
        }
    }

    /** One frame: the arrow, the instruction, the distances and the chips. */
    private void drawFrame() {
        frames++;
        handler.postDelayed(frame, 1000 / FRAMES_PER_SECOND);

        TextView gps = findViewById(R.id.ar_gps_accuracy);
        gps.setVisibility(accuracy > 0 ? View.VISIBLE : View.GONE);
        if (accuracy > 0) {
            gps.setText(getString(R.string.ar_gps_accuracy, Math.round(accuracy)));
        }
        TextView compass = findViewById(R.id.ar_heading);
        compass.setVisibility(Float.isNaN(lastHeading) ? View.GONE : View.VISIBLE);
        if (!Float.isNaN(lastHeading)) {
            String[] points = getResources().getStringArray(R.array.compass_points);
            compass.setText(points[Math.round(lastHeading / 45f) % 8]);
        }
        findViewById(R.id.ar_calibrate).setVisibility(compassPoor ? View.VISIBLE : View.GONE);

        if (nav == null) {
            return;
        }
        TextView instruction = findViewById(R.id.ar_instruction);
        if (nav.isOffCampus()) {
            arrow.setVisibility(View.GONE);
            instructionCard.setVisibility(View.VISIBLE);
            instruction.setText(R.string.map_off_campus);
            ((TextView) findViewById(R.id.ar_instruction_detail)).setText(R.string.ar_open_map);
            findViewById(R.id.ar_step).setVisibility(View.GONE);
            return;
        }
        if (nav.isArrived()) {
            arrow.setVisibility(View.GONE);
            instructionCard.setVisibility(View.VISIBLE);
            instruction.setText(R.string.arrival_title);
            ((android.widget.ImageView) findViewById(R.id.ar_turn_icon)).setImageResource(R.drawable.ic_flag);
            ((TextView) findViewById(R.id.ar_instruction_detail)).setText(roomName);
            findViewById(R.id.ar_step).setVisibility(View.GONE);
            return;
        }
        Guidance g = nav.getGuidance();
        if (g == null) {
            instructionCard.setVisibility(View.VISIBLE);
            instruction.setText(R.string.ar_waiting);
            ((TextView) findViewById(R.id.ar_instruction_detail)).setText(roomName);
            findViewById(R.id.ar_step).setVisibility(View.GONE);
            arrow.setVisibility(View.GONE);
            return;
        }
        float angle = nav.getArrowAngle();
        if (arController != null) {
            // T18c: the route ahead, for the chevrons on the ground
            arController.setPath(nav.getPathAhead(GroundPath.ALONG, GroundPath.STEP));
            arController.setCompassHeading(lastHeading);
        }
        // the chevrons on the ground when ARCore shows them; otherwise the compass arrow (FR-11)
        boolean onGround = arController != null && groundState == ArSessionController.State.GROUND;
        arrow.setVisibility(onGround || Float.isNaN(angle) ? View.GONE : View.VISIBLE);
        if (!Float.isNaN(angle)) {
            arrow.setRotation(angle);   // the arrow drawable points up = straight ahead
        }

        int metres = WalkingEstimate.roundedMetres(g.metres);
        int text;
        int icon;
        switch (g.action) {
            case LEFT:
                text = R.string.ar_turn_left;
                icon = R.drawable.ic_turn_left;
                break;
            case RIGHT:
                text = R.string.ar_turn_right;
                icon = R.drawable.ic_turn_right;
                break;
            case TURN_BACK:
                text = R.string.ar_turn_back;
                icon = R.drawable.ic_u_turn;
                break;
            default:
                text = R.string.ar_arrive;
                icon = R.drawable.ic_flag;
                break;
        }
        instructionCard.setVisibility(View.VISIBLE);
        instruction.setText(getString(text, metres));
        ((android.widget.ImageView) findViewById(R.id.ar_turn_icon)).setImageResource(icon);
        double left = nav.getDistanceLeft();
        int leftM = WalkingEstimate.roundedMetres(left);
        int minutes = WalkingEstimate.minutes(left);
        ((TextView) findViewById(R.id.ar_instruction_detail))
                .setText(getString(R.string.ar_instruction_detail, roomName, leftM, minutes));
        TextView step = findViewById(R.id.ar_step);
        step.setVisibility(View.VISIBLE);
        step.setText(getString(R.string.ar_step, g.step, g.steps));
        TextView distance = findViewById(R.id.ar_destination_distance);
        distance.setVisibility(View.VISIBLE);
        distance.setText(getString(R.string.map_distance, leftM));
        TextView time = findViewById(R.id.ar_time_left);
        time.setVisibility(View.VISIBLE);
        time.setText(getString(R.string.room_minutes, minutes));
    }

    /** Decision #13: the step permission is asked where steps are used, once. */
    private void askForStepsOnce() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q || heading.canCountSteps()) {
            return;
        }
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_ASKED_STEPS, false)) {
            return;
        }
        prefs.edit().putBoolean(KEY_ASKED_STEPS, true).apply();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.steps_title)
                .setMessage(R.string.steps_text)
                .setPositiveButton(R.string.steps_allow,
                        (d, w) -> stepsPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION))
                .setNegativeButton(R.string.steps_not_now, null)
                .show();
    }

    /** Risk R5: a figure-eight movement recalibrates the compass. */
    private void explainCompassCalibration() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ar_calibrate_title)
                .setMessage(R.string.ar_calibrate_text)
                .setPositiveButton(R.string.permission_continue, null)
                .show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocationService.bind(this, connection);   // the GPS only while this screen is visible
    }

    @Override
    protected void onStop() {
        unbindService(connection);
        location = null;
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        heading.start();          // the sensors only while visible (NFR-08)
        handler.post(frame);
        if (installRequested) {   // back from installing ARCore: start again in AR mode
            installRequested = false;
            recreate();
            return;
        }
        resumeAr();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(frame);
        heading.stop();
        if (arView != null) {
            arView.onPause();
        }
        if (arSession != null) {
            arSession.pause();    // ARCore releases the camera (NFR-08)
        }
        super.onPause();
    }

    @VisibleForTesting
    int framesDrawn() {
        return frames;
    }

    @VisibleForTesting
    @Nullable
    NavigationManager navigation() {
        return nav;
    }

    /** For tests on an emulator without a rotation sensor: a heading as if from the compass. */
    @VisibleForTesting
    void feedHeading(float degrees) {
        lastHeading = degrees;
        if (nav != null) {
            nav.setHeading(degrees);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview p = new Preview.Builder().build();
                p.setSurfaceProvider(preview.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setResolutionSelector(new ResolutionSelector.Builder()
                                .setResolutionStrategy(new ResolutionStrategy(ANALYSIS_SIZE,
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                                .build())
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(analysisThread, recognizer);
                provider.unbindAll();
                // bound to this screen: CameraX stops the camera when it is no longer visible (NFR-08)
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, p, analysis);
                MaterialButton flash = findViewById(R.id.ar_flash);
                flash.setVisibility(camera.getCameraInfo().hasFlashUnit() ? View.VISIBLE : View.GONE);
            } catch (Exception e) {
                // no back camera, or it is in use by another app
                explainNoCamera();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void toggleTorch() {
        if (camera != null) {
            torchOn = !torchOn;
            camera.getCameraControl().enableTorch(torchOn);
            ((MaterialButton) findViewById(R.id.ar_flash))
                    .setIconResource(torchOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
        }
    }

    /** Without the camera, the map does the same job (AT-12). */
    private void explainNoCamera() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ar_no_camera_title)
                .setMessage(R.string.permission_missing_camera)
                .setPositiveButton(R.string.ar_open_map, (d, w) -> openMap())
                .setNegativeButton(R.string.action_close, (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    private void openMap() {
        Intent map = room != null ? MapActivity.navigateTo(this, room.number) : new Intent(this, MapActivity.class);
        startActivity(map.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        finish();
    }

    @Override
    protected void onDestroy() {
        analysisThread.shutdown();
        if (arController != null) {
            arController.release();
        }
        if (arSession != null) {
            arSession.close();
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------ T18: ARCore or the compass

    /**
     * ARCore when it is installed on this phone; the CameraX screen with the compass arrow
     * otherwise (FR-11). ARCore that could be installed is offered, never forced on the user.
     */
    private void chooseCameraMode() {
        if (forceCompassMode) {
            setArStatus(ArStatus.FORCED_COMPASS, null);
            startCamera();
            return;
        }
        setArStatus(ArStatus.CHECKING, null);
        checkArCore(1);
    }

    /**
     * T18b: ARCore's first answer is often "still checking"; it is asked again a few times
     * before the screen settles on the compass, and every answer is logged.
     */
    private void checkArCore(int attempt) {
        com.google.ar.core.ArCoreApk.getInstance().checkAvailabilityAsync(this, availability -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            Log.i(AR_TAG, "ARCore availability (check " + attempt + "): " + availability);
            long retry = ArStatus.retryDelayMs(availability, attempt);
            if (retry >= 0) {
                handler.postDelayed(() -> checkArCore(attempt + 1), retry);
                return;
            }
            ArStatus status = ArStatus.fromAvailability(availability);
            if (status == ArStatus.RUNNING) {
                if (startAr()) {
                    return;   // ARCore runs, or resumeAr() already went back to the compass
                }
            } else {
                setArStatus(status == ArStatus.CHECKING ? ArStatus.CHECK_FAILED : status, null);
            }
            startCamera();
            if (arStatus.canInstall) {
                offerArInstall();
            }
        });
    }

    /** What the mode chip shows, and the log line that says why. */
    private void setArStatus(@NonNull ArStatus status, @Nullable Exception error) {
        arStatus = status;
        arError = error == null ? null : error.getClass().getSimpleName()
                + (error.getMessage() == null ? "" : ": " + error.getMessage());
        if (error != null) {
            Log.w(AR_TAG, "ARCore: " + status, error);
        } else {
            Log.i(AR_TAG, "ARCore: " + status);
        }
        Chip chip = findViewById(R.id.ar_mode);
        if (chip == null) {
            return;
        }
        boolean ar = status == ArStatus.RUNNING;
        chip.setText(ar ? R.string.ar_mode_ar : status == ArStatus.CHECKING ? R.string.ar_mode_checking : R.string.ar_mode_compass);
        chip.setChipIconResource(ar ? R.drawable.ic_view_in_ar : R.drawable.ic_explore);
        chip.setContentDescription(getString(R.string.ar_mode_description, chip.getText(), getString(reasonText(status))));
    }

    @androidx.annotation.StringRes
    static int reasonText(@NonNull ArStatus status) {
        switch (status) {
            case RUNNING:
                return R.string.ar_reason_running;
            case CHECKING:
                return R.string.ar_reason_checking;
            case FORCED_COMPASS:
                return R.string.ar_reason_forced;
            case NOT_INSTALLED:
                return R.string.ar_reason_not_installed;
            case APK_TOO_OLD:
                return R.string.ar_reason_apk_too_old;
            case SDK_TOO_OLD:
                return R.string.ar_reason_sdk_too_old;
            case DEVICE_NOT_SUPPORTED:
                return R.string.ar_reason_not_supported;
            case INSTALL_DECLINED:
                return R.string.ar_reason_declined;
            case CHECK_FAILED:
                return R.string.ar_reason_check_failed;
            case CAMERA_BUSY:
                return R.string.ar_reason_camera_busy;
            default:
                return R.string.ar_reason_failed;
        }
    }

    /** T18c: tracking, north, the floor, and which arrow is shown and why. */
    private String arDetails(ArSessionController c) {
        ArSessionController.State st = c.state();
        int why;
        switch (st) {
            case GROUND:
                why = R.string.ar_view_ground;
                break;
            case NOT_TRACKING:
                why = R.string.ar_view_not_tracking;
                break;
            case NO_NORTH:
                why = R.string.ar_view_no_north;
                break;
            case NO_ROUTE:
                why = R.string.ar_view_no_route;
                break;
            case OFF_SCREEN:
                why = R.string.ar_view_off_screen;
                break;
            default:
                why = R.string.ar_view_starting;
                break;
        }
        StringBuilder b = new StringBuilder(getString(why));
        b.append("\n\n").append(getString(R.string.ar_detail_tracking,
                st == ArSessionController.State.NOT_TRACKING || st == ArSessionController.State.STARTING
                        ? getString(R.string.ar_detail_no) + (c.trackingFailure().isEmpty() ? "" : " (" + c.trackingFailure() + ")")
                        : getString(R.string.ar_detail_yes)));
        b.append("\n").append(getString(R.string.ar_detail_north, c.isNorthAligned()
                ? getString(R.string.ar_detail_north_offset, Math.round(c.northOffset()))
                : getString(R.string.ar_detail_no)));
        b.append("\n").append(getString(R.string.ar_detail_floor, getString(
                c.floorFromPlane() ? R.string.ar_detail_floor_plane : R.string.ar_detail_floor_estimate)));
        b.append("\n").append(getString(R.string.ar_detail_chevrons, c.visibleChevrons()));
        return b.toString();
    }

    /** The mode chip was tapped: why AR or the compass, and what can be done. */
    private void explainArMode() {
        String message = getString(reasonText(arStatus));
        if (arError != null) {
            message += "\n\n" + getString(R.string.ar_reason_error, arError);
        }
        if (arController != null) {
            message += "\n\n" + arDetails(arController);   // T18c: what the AR view does right now
        }
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(arStatus == ArStatus.RUNNING ? R.string.ar_mode_title_ar : R.string.ar_mode_title_compass)
                .setMessage(message)
                .setPositiveButton(R.string.action_close, null);
        if (arStatus.canInstall) {
            dialog.setNeutralButton(arStatus == ArStatus.APK_TOO_OLD ? R.string.ar_update : R.string.ar_install,
                    (d, w) -> requestArInstall());
        }
        dialog.show();
    }

    /**
     * Starts ARCore in place of CameraX. Returns false if no session could be created (the
     * reason is in arStatus); true once it took over the camera view.
     */
    private boolean startAr() {
        try {
            com.google.ar.core.Session session = new com.google.ar.core.Session(this);
            com.google.ar.core.Config config = new com.google.ar.core.Config(session);
            config.setUpdateMode(com.google.ar.core.Config.UpdateMode.LATEST_CAMERA_IMAGE);
            config.setFocusMode(com.google.ar.core.Config.FocusMode.AUTO);
            config.setPlaneFindingMode(com.google.ar.core.Config.PlaneFindingMode.HORIZONTAL);   // T18c: the floor
            config.setLightEstimationMode(com.google.ar.core.Config.LightEstimationMode.DISABLED);
            session.configure(config);
            arSession = session;
        } catch (Exception e) {
            arSession = null;
            setArStatus(ArStatus.fromException(e), e);
            return false;   // the ARCore service is missing or failed: the compass mode works anyway
        }
        FrameLayout box = findViewById(R.id.camera_preview);
        box.removeView(preview);
        findViewById(R.id.ar_flash).setVisibility(View.GONE);   // ARCore controls the camera
        arView = new android.opengl.GLSurfaceView(this);
        arView.setPreserveEGLContextOnPause(true);
        arView.setEGLContextClientVersion(2);
        arView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        arController = new ArSessionController(arSession, recognizer, state -> {
            groundState = state;
            Log.i(AR_TAG, "AR view: " + state);
        });
        arController.setDisplayRotation(getWindowManager().getDefaultDisplay().getRotation());
        arView.setRenderer(arController);
        arView.setRenderMode(android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        box.addView(arView, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setArStatus(ArStatus.RUNNING, null);
        resumeAr();   // falls back to the compass, with the reason, if the camera does not open
        return true;
    }

    private void resumeAr() {
        if (arSession == null || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            return;
        }
        try {
            arSession.resume();
            if (arView != null) {
                arView.onResume();
            }
        } catch (Exception e) {
            // the camera is busy: fall back to the compass screen
            setArStatus(ArStatus.fromException(e), e);
            arSession.close();
            arSession = null;
            arController = null;
            FrameLayout box = findViewById(R.id.camera_preview);
            if (arView != null) {
                box.removeView(arView);
                arView = null;
            }
            box.addView(preview, 0);
            startCamera();
        }
    }

    /** ARCore can be installed from Google Play: a message with a button, not a surprise prompt. */
    private void offerArInstall() {
        Snackbar.make(preview, R.string.ar_install_offer, Snackbar.LENGTH_LONG)
                .setAnchorView(R.id.bottom_nav)
                .setAction(arStatus == ArStatus.APK_TOO_OLD ? R.string.ar_update : R.string.ar_install,
                        v -> requestArInstall())
                .show();
    }

    private void requestArInstall() {
        try {
            com.google.ar.core.ArCoreApk.InstallStatus status =
                    com.google.ar.core.ArCoreApk.getInstance().requestInstall(this, true);
            installRequested = status == com.google.ar.core.ArCoreApk.InstallStatus.INSTALL_REQUESTED;
        } catch (Exception e) {
            setArStatus(ArStatus.fromException(e), e);   // declined, or Google Play is not available
        }
    }

    @VisibleForTesting
    ArStatus arStatus() {
        return arStatus;
    }

    /** True when the screen runs on ARCore (T18). */
    @VisibleForTesting
    boolean usesArCore() {
        return arSession != null;
    }

    @VisibleForTesting
    BuildingRecognizer recognizer() {
        return recognizer;
    }
}
