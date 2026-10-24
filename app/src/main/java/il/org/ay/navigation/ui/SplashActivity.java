package il.org.ay.navigation.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Map;

import il.org.ay.navigation.R;
import il.org.ay.navigation.data.CampusRepository;
import il.org.ay.navigation.logic.PermissionState;

/**
 * Opening screen (T07, FR-12). Two states in one layout (T30, design finding 4):
 * the splash while the campus data syncs, then the permissions view that explains why
 * location and camera are needed. Continues to search whatever the user answered (AT-12).
 * The sync runs in the background (T08); the app never waits for it beyond the splash.
 */
public class SplashActivity extends BaseActivity {

    private static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
    };

    /** How long the splash stays before the permissions view. */
    private static final long SPLASH_MS = 1200;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    this::onPermissionsResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        TextView version = findViewById(R.id.splash_version);
        version.setText(getString(R.string.splash_version, versionName()));
        findViewById(R.id.button_start).setOnClickListener(v -> requestPermissions());
        findViewById(R.id.button_skip).setOnClickListener(v -> openSearch());
        syncCampusData();
        handler.postDelayed(this::afterSplash, SPLASH_MS);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** Permissions already granted: straight to search. Otherwise explain first. */
    private void afterSplash() {
        if (currentState().needsExplanation()) {
            showPermissions();
        } else {
            openSearch();
        }
    }

    private void showPermissions() {
        View splash = findViewById(R.id.splash_content);
        View permissions = findViewById(R.id.splash_permissions);
        permissions.setAlpha(0f);
        permissions.setVisibility(View.VISIBLE);
        permissions.animate().alpha(1f).setDuration(250).start();
        splash.animate().alpha(0f).setDuration(250).withEndAction(() -> splash.setVisibility(View.GONE)).start();
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /**
     * Copies a newer campus version from Firestore to Room. Without a network
     * this fails quietly and the app keeps working from the copy in Room (AT-09).
     */
    private void syncCampusData() {
        CampusRepository.get(this).sync(result -> { /* the search screen reads Room either way */ });
    }

    private void requestPermissions() {
        if (currentState().needsExplanation()) {
            permissionLauncher.launch(PERMISSIONS);
        } else {
            openSearch();
        }
    }

    private void onPermissionsResult(Map<String, Boolean> result) {
        PermissionState state = currentState();
        if (!state.needsExplanation()) {
            openSearch();
            return;
        }
        int message;
        switch (state.missing()) {
            case BOTH:
                message = R.string.permission_missing_both;
                break;
            case LOCATION:
                message = R.string.permission_missing_location;
                break;
            default:
                message = R.string.permission_missing_camera;
                break;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_missing_title)
                .setMessage(message)
                .setPositiveButton(R.string.permission_continue, (d, w) -> openSearch())
                .setCancelable(false)
                .show();
    }

    private PermissionState currentState() {
        return new PermissionState(isGranted(Manifest.permission.ACCESS_FINE_LOCATION),
                isGranted(Manifest.permission.CAMERA));
    }

    private boolean isGranted(String permission) {
        return ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void openSearch() {
        startActivity(new Intent(this, SearchActivity.class));
        finish();
    }
}
