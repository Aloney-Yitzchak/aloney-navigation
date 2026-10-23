package il.org.ay.navigation.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** AT-12: refusing a permission explains why, and the rest of the app still works. */
public class PermissionStateTest {

    @Test
    public void allGranted_everythingWorks_noExplanation() {
        PermissionState s = new PermissionState(true, true);
        assertTrue(s.canNavigate());
        assertTrue(s.canUseAr());
        assertFalse(s.needsExplanation());
        assertEquals(PermissionState.Missing.NONE, s.missing());
    }

    @Test
    public void cameraRefused_mapNavigationStillAvailable() {
        PermissionState s = new PermissionState(true, false);
        assertTrue(s.canNavigate());
        assertFalse(s.canUseAr());
        assertTrue(s.needsExplanation());
        assertEquals(PermissionState.Missing.CAMERA, s.missing());
    }

    @Test
    public void locationRefused_searchStillAvailable() {
        PermissionState s = new PermissionState(false, true);
        assertTrue(s.canSearch());
        assertFalse(s.canNavigate());
        assertFalse(s.canUseAr());
        assertTrue(s.needsExplanation());
        assertEquals(PermissionState.Missing.LOCATION, s.missing());
    }

    @Test
    public void bothRefused_oneMessageAboutBoth() {
        PermissionState s = new PermissionState(false, false);
        assertTrue(s.canSearch());
        assertFalse(s.canNavigate());
        assertEquals(PermissionState.Missing.BOTH, s.missing());
    }
}
