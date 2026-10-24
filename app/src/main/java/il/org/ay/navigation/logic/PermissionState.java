package il.org.ay.navigation.logic;

/**
 * What the app can do with the permissions the user granted (FR-12, AT-12).
 * Pure Java so it can be unit-tested without a phone.
 */
public class PermissionState {

    private final boolean location;
    private final boolean camera;

    public PermissionState(boolean location, boolean camera) {
        this.location = location;
        this.camera = camera;
    }

    /** Navigation on the 2D map needs location only. */
    public boolean canNavigate() {
        return location;
    }

    /** The AR arrow needs both the camera and the location. */
    public boolean canUseAr() {
        return location && camera;
    }

    /** Search and room details work with no permissions at all. */
    public boolean canSearch() {
        return true;
    }

    /** True when at least one permission is missing and the user should see why it matters. */
    public boolean needsExplanation() {
        return !location || !camera;
    }

    /** Which explanation to show when something is missing (AT-12). */
    public enum Missing { NONE, LOCATION, CAMERA, BOTH }

    public Missing missing() {
        if (!location && !camera) return Missing.BOTH;
        if (!location) return Missing.LOCATION;
        if (!camera) return Missing.CAMERA;
        return Missing.NONE;
    }

    public boolean hasLocation() {
        return location;
    }

    public boolean hasCamera() {
        return camera;
    }
}
