package il.org.ay.navigation.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import il.org.ay.navigation.R;
import il.org.ay.navigation.data.PhotoStore;
import il.org.ay.navigation.data.PhotoTasks;
import il.org.ay.navigation.model.PhotoTask;

/**
 * T20a: photographing one building for the model. Each photo is saved on this phone in the
 * building's folder (PhotoStore) and counted in Firestore, so the staff list shows the
 * progress of all phones. A tip under the name changes after every photo, for variety:
 * angle, distance, details, time of day (SDD 10, step 1).
 */
public class PhotoCaptureActivity extends BaseActivity {

    private static final String EXTRA_LABEL = "label";
    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_TARGET = "target";
    /** Big enough to crop and augment from, small enough for hundreds on a phone (≈200 KB each). */
    private static final Size PHOTO_SIZE = new Size(1280, 960);

    public static Intent intent(@NonNull Context context, @NonNull PhotoTask task) {
        return new Intent(context, PhotoCaptureActivity.class)
                .putExtra(EXTRA_LABEL, task.label)
                .putExtra(EXTRA_NAME, task.name)
                .putExtra(EXTRA_TARGET, task.target);
    }

    private String label;
    private int target;
    private PhotoStore store;
    private PhotoTasks tasks;
    private PreviewView preview;
    private MaterialButton shutter;
    private TextView count;
    private TextView tip;
    private ImageView last;
    private View deleteLast;
    @Nullable
    private ImageCapture capture;
    @Nullable
    private File lastPhoto;
    private int onPhone;
    private int tipIndex;
    private String[] tips;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String> askCamera =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    explainNoCamera();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_capture);
        label = getIntent().getStringExtra(EXTRA_LABEL);
        if (label == null) {
            finish();
            return;
        }
        target = getIntent().getIntExtra(EXTRA_TARGET, 40);
        store = PhotoStore.get(this);
        tasks = PhotoTasks.get();
        tips = getResources().getStringArray(R.array.capture_tips);

        preview = findViewById(R.id.capture_preview);
        shutter = findViewById(R.id.capture_shutter);
        count = findViewById(R.id.capture_count);
        tip = findViewById(R.id.capture_tip);
        last = findViewById(R.id.capture_last);
        deleteLast = findViewById(R.id.capture_delete_last);
        ((TextView) findViewById(R.id.capture_name)).setText(getIntent().getStringExtra(EXTRA_NAME));
        tip.setText(tips[0]);
        findViewById(R.id.capture_back).setOnClickListener(v -> finish());
        shutter.setOnClickListener(v -> takePhoto());
        deleteLast.setOnClickListener(v -> deleteLastPhoto());
        shutter.setEnabled(false);

        io.execute(() -> {
            int n = store.count(label);
            main.post(() -> showCount(n));
        });
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            askCamera.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview p = new Preview.Builder().build();
                p.setSurfaceProvider(preview.getSurfaceProvider());
                capture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setJpegQuality(85)
                        .setResolutionSelector(new ResolutionSelector.Builder()
                                .setResolutionStrategy(new ResolutionStrategy(PHOTO_SIZE,
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                                .build())
                        .build();
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, p, capture);
                shutter.setEnabled(true);
            } catch (Exception e) {
                explainNoCamera();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        ImageCapture c = capture;
        if (c == null) {
            return;
        }
        File file;
        try {
            file = store.newPhoto(label);
        } catch (IOException e) {
            Snackbar.make(preview, R.string.capture_failed, Snackbar.LENGTH_SHORT).show();
            return;
        }
        shutter.setEnabled(false);
        c.takePicture(new ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        shutter.setEnabled(true);
                        lastPhoto = file;
                        tasks.countPhoto(label, 1);
                        showCount(onPhone + 1);
                        if (onPhone == target) {
                            Snackbar.make(preview, R.string.capture_done, Snackbar.LENGTH_LONG).show();
                        }
                        flash();
                        nextTip();
                        showThumbnail(file);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        shutter.setEnabled(true);
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                        Snackbar.make(preview, R.string.capture_failed, Snackbar.LENGTH_SHORT).show();
                    }
                });
    }

    /** A photo that came out blurred, or with someone in it, is removed right away. */
    private void deleteLastPhoto() {
        File photo = lastPhoto;
        if (photo != null && store.delete(photo)) {
            tasks.countPhoto(label, -1);
            lastPhoto = null;
            showCount(onPhone - 1);
            last.setVisibility(View.INVISIBLE);
            deleteLast.setVisibility(View.INVISIBLE);
            Snackbar.make(preview, R.string.capture_deleted, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void showCount(int n) {
        onPhone = Math.max(0, n);
        count.setText(getString(R.string.capture_count, onPhone, target));
        count.setContentDescription(getString(R.string.capture_count, onPhone, target));
    }

    private void nextTip() {
        tipIndex = (tipIndex + 1) % tips.length;
        tip.setText(tips[tipIndex]);
    }

    private void flash() {
        View white = findViewById(R.id.capture_flash);
        white.setAlpha(0.8f);
        white.animate().alpha(0f).setDuration(250).start();
    }

    private void showThumbnail(File file) {
        io.execute(() -> {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 8;   // 1280 px → 160 px: enough for a 56 dp thumbnail
            Bitmap small = BitmapFactory.decodeFile(file.getPath(), o);
            main.post(() -> {
                if (isDestroyed() || small == null || file != lastPhoto) {
                    return;
                }
                last.setImageBitmap(small);
                last.setVisibility(View.VISIBLE);
                deleteLast.setVisibility(View.VISIBLE);
            });
        });
    }

    private void explainNoCamera() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ar_no_camera_title)
                .setMessage(R.string.permission_missing_camera)
                .setPositiveButton(R.string.action_close, (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    @VisibleForTesting
    int photosOnPhone() {
        return onPhone;
    }

    @VisibleForTesting
    boolean readyToShoot() {
        return shutter.isEnabled();
    }
}
