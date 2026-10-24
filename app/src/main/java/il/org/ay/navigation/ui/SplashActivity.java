package il.org.ay.navigation.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Map;

import il.org.ay.navigation.R;
import il.org.ay.navigation.data.CampusRepository;
import il.org.ay.navigation.logic.PermissionState;

/**
 * Opening screen (T07, FR-12). Explains why location and camera are needed,
 * asks for them, and continues to search whatever the user answered (AT-12).
 * Starts the campus data sync in the background (T08); the app never waits for it.
 */
public class SplashActivity extends BaseActivity {

    private static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
    };

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    this::onPermissionsResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        findViewById(R.id.button_start).setOnClickListener(v -> requestPermissions());
        syncCampusData();
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
