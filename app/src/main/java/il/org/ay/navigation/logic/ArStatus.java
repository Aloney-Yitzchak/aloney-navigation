package il.org.ay.navigation.logic;

import androidx.annotation.NonNull;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

/**
 * T18b: why the camera screen runs on ARCore or on the compass (FR-11). Each answer of ARCore
 * (the availability check, or the exception when starting a session) becomes one reason,
 * shown on the screen's mode chip and written to the log ({@code adb logcat -s ArNav}).
 */
public enum ArStatus {
    /** ARCore runs: the arrow is anchored on the ground. */
    RUNNING(false),
    /** Still checking (ARCore's first check can take a moment). */
    CHECKING(false),
    /** The compass, because a test asked for it. */
    FORCED_COMPASS(false),
    /** Google Play Services for AR is not installed: it can be installed. */
    NOT_INSTALLED(true),
    /** Google Play Services for AR is too old: it can be updated. */
    APK_TOO_OLD(true),
    /** This app's ARCore library is older than the installed service. */
    SDK_TOO_OLD(false),
    /** The phone does not support ARCore. */
    DEVICE_NOT_SUPPORTED(false),
    /** The user declined installing it. */
    INSTALL_DECLINED(true),
    /** The check did not finish (no network the first time, or the service did not answer). */
    CHECK_FAILED(false),
    /** The camera is in use by another app, or could not be opened. */
    CAMERA_BUSY(false),
    /** Any other error when starting ARCore. */
    START_FAILED(false);

    /** Whether the screen can offer "התקנה" / "עדכון" for this reason. */
    public final boolean canInstall;

    ArStatus(boolean canInstall) {
        this.canInstall = canInstall;
    }

    /**
     * The answer of {@code ArCoreApk.checkAvailability}. SUPPORTED_INSTALLED is RUNNING only
     * once a session starts; until then the screen tries to start one.
     */
    @NonNull
    public static ArStatus fromAvailability(@NonNull ArCoreApk.Availability availability) {
        switch (availability) {
            case SUPPORTED_INSTALLED:
                return RUNNING;
            case SUPPORTED_NOT_INSTALLED:
                return NOT_INSTALLED;
            case SUPPORTED_APK_TOO_OLD:
                return APK_TOO_OLD;
            case UNSUPPORTED_DEVICE_NOT_CAPABLE:
                return DEVICE_NOT_SUPPORTED;
            case UNKNOWN_CHECKING:
                return CHECKING;
            default:   // UNKNOWN_TIMED_OUT, UNKNOWN_ERROR
                return CHECK_FAILED;
        }
    }

    /** The exception from {@code new Session()}, {@code configure()} or {@code resume()}. */
    @NonNull
    public static ArStatus fromException(@NonNull Exception e) {
        if (e instanceof UnavailableArcoreNotInstalledException) {
            return NOT_INSTALLED;
        }
        if (e instanceof UnavailableApkTooOldException) {
            return APK_TOO_OLD;
        }
        if (e instanceof UnavailableSdkTooOldException) {
            return SDK_TOO_OLD;
        }
        if (e instanceof UnavailableDeviceNotCompatibleException) {
            return DEVICE_NOT_SUPPORTED;
        }
        if (e instanceof UnavailableUserDeclinedInstallationException) {
            return INSTALL_DECLINED;
        }
        if (e instanceof CameraNotAvailableException || e instanceof SecurityException) {
            return CAMERA_BUSY;
        }
        return START_FAILED;
    }

    /** How long to wait before asking ARCore again, or -1 to stop asking. */
    public static long retryDelayMs(@NonNull ArCoreApk.Availability availability, int attempt) {
        if (!availability.isTransient() && availability != ArCoreApk.Availability.UNKNOWN_TIMED_OUT) {
            return -1;
        }
        return attempt < MAX_CHECKS ? 400L : -1;
    }

    /** Asks ARCore at most this many times (about 4 seconds in all). */
    public static final int MAX_CHECKS = 10;
}
