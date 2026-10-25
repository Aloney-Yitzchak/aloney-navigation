package il.org.ay.navigation.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.ar.core.ArCoreApk.Availability;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;

import org.junit.Test;

/** T18b: every answer of ARCore becomes a reason the screen can show. */
public class ArStatusTest {

    @Test
    public void availability() {
        assertEquals(ArStatus.RUNNING, ArStatus.fromAvailability(Availability.SUPPORTED_INSTALLED));
        assertEquals(ArStatus.NOT_INSTALLED, ArStatus.fromAvailability(Availability.SUPPORTED_NOT_INSTALLED));
        assertEquals(ArStatus.APK_TOO_OLD, ArStatus.fromAvailability(Availability.SUPPORTED_APK_TOO_OLD));
        assertEquals(ArStatus.DEVICE_NOT_SUPPORTED, ArStatus.fromAvailability(Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE));
        assertEquals(ArStatus.CHECKING, ArStatus.fromAvailability(Availability.UNKNOWN_CHECKING));
        assertEquals(ArStatus.CHECK_FAILED, ArStatus.fromAvailability(Availability.UNKNOWN_TIMED_OUT));
        assertEquals(ArStatus.CHECK_FAILED, ArStatus.fromAvailability(Availability.UNKNOWN_ERROR));
    }

    @Test
    public void exceptionsWhenStarting() {
        assertEquals(ArStatus.NOT_INSTALLED, ArStatus.fromException(new UnavailableArcoreNotInstalledException()));
        assertEquals(ArStatus.APK_TOO_OLD, ArStatus.fromException(new UnavailableApkTooOldException()));
        assertEquals(ArStatus.SDK_TOO_OLD, ArStatus.fromException(new UnavailableSdkTooOldException()));
        assertEquals(ArStatus.DEVICE_NOT_SUPPORTED, ArStatus.fromException(new UnavailableDeviceNotCompatibleException()));
        assertEquals(ArStatus.CAMERA_BUSY, ArStatus.fromException(new CameraNotAvailableException()));
        assertEquals(ArStatus.CAMERA_BUSY, ArStatus.fromException(new SecurityException("no camera permission")));
        assertEquals(ArStatus.START_FAILED, ArStatus.fromException(new IllegalStateException()));
    }

    @Test
    public void onlyMissingOrOldArcore_canBeInstalled() {
        assertTrue(ArStatus.NOT_INSTALLED.canInstall);
        assertTrue(ArStatus.APK_TOO_OLD.canInstall);
        assertFalse(ArStatus.DEVICE_NOT_SUPPORTED.canInstall);
        assertFalse(ArStatus.CAMERA_BUSY.canInstall);
    }

    @Test
    public void stillChecking_isAskedAgain_butNotForever() {
        assertEquals(400, ArStatus.retryDelayMs(Availability.UNKNOWN_CHECKING, 1));
        assertEquals(400, ArStatus.retryDelayMs(Availability.UNKNOWN_TIMED_OUT, 1));
        assertEquals(-1, ArStatus.retryDelayMs(Availability.UNKNOWN_CHECKING, ArStatus.MAX_CHECKS));
        assertEquals(-1, ArStatus.retryDelayMs(Availability.SUPPORTED_INSTALLED, 1));
        assertEquals(-1, ArStatus.retryDelayMs(Availability.UNKNOWN_ERROR, 1));
    }
}
