package il.org.ay.navigation.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import il.org.ay.navigation.R;
import il.org.ay.navigation.data.CampusRepository;
import il.org.ay.navigation.logic.BuildingRecognizer;
import il.org.ay.navigation.model.CampusRoom;

/**
 * The camera screen (T16, FR-04; layout activity_ar_nav.xml). The live camera fills the
 * screen (design finding 6), and every frame also goes to BuildingRecognizer (T22).
 * The camera is bound to this screen's lifecycle: it runs only while the screen is
 * visible (NFR-08). T17 draws the arrow and the instructions on top.
 */
public class ArNavActivity extends BaseActivity {

    public static final String EXTRA_ROOM = MapActivity.EXTRA_ROOM;

    /** Frames for the model: small is enough, and keeps the analysis fast. */
    static final Size ANALYSIS_SIZE = new Size(640, 480);

    private final BuildingRecognizer recognizer = new BuildingRecognizer();
    private final ExecutorService analysisThread = Executors.newSingleThreadExecutor();
    private PreviewView preview;
    @Nullable
    private Camera camera;
    private boolean torchOn;
    @Nullable
    private CampusRoom room;

    private final ActivityResultLauncher<String> cameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
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

        // T17 shows these when there is a route to follow
        for (int id : new int[]{R.id.ar_arrow, R.id.ar_detected, R.id.ar_sound, R.id.ar_gps_accuracy,
                R.id.ar_heading, R.id.ar_calibrate}) {
            findViewById(id).setVisibility(View.GONE);
        }
        ((View) findViewById(R.id.ar_instruction).getParent().getParent()).setVisibility(View.GONE);

        findViewById(R.id.ar_end).setOnClickListener(v -> finish());
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
            startCamera();
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
        findViewById(R.id.ar_destination_distance).setVisibility(View.GONE);   // T17
        findViewById(R.id.ar_time_left).setVisibility(View.GONE);              // T17
        if (r == null) {
            bar.setText(R.string.ar_choose_destination);
            if (card != null) {
                card.setVisibility(View.GONE);
            }
            return;
        }
        CharSequence name = RoomAdapter.displayName(bar, r);
        bar.setText(getString(R.string.ar_destination, name));   // finding 7: the real destination
        if (card != null) {
            card.setVisibility(View.VISIBLE);
        }
        ((TextView) findViewById(R.id.ar_room_number)).setText(r.number);
        ((TextView) findViewById(R.id.ar_room_name)).setText(name);
        TextView owner = findViewById(R.id.ar_room_owner);
        owner.setText(r.owner);
        owner.setVisibility(r.owner == null || r.owner.isEmpty() ? View.GONE : View.VISIBLE);
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
        super.onDestroy();
    }

    @VisibleForTesting
    BuildingRecognizer recognizer() {
        return recognizer;
    }
}
