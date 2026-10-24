package il.org.ay.navigation.ui;

import android.view.Gravity;
import android.view.View;

import java.util.Collections;

import com.android.ide.common.rendering.api.SessionParams;
import com.android.resources.LayoutDirection;

import org.junit.Rule;
import org.junit.Test;

import app.cash.paparazzi.DeviceConfig;
import app.cash.paparazzi.EnvironmentKt;
import app.cash.paparazzi.HtmlReportWriter;
import app.cash.paparazzi.Paparazzi;
import app.cash.paparazzi.SnapshotHandler;
import app.cash.paparazzi.SnapshotVerifier;
import il.org.ay.navigation.R;

/**
 * T30: renders every layout on the JVM (Paparazzi), in Hebrew and right-to-left,
 * so each one can be compared with its design in docs/design/.
 *
 *     ./gradlew recordPaparazziDebug     # writes app/src/test/snapshots/images/*.png
 *     ./gradlew verifyPaparazziDebug     # fails if a layout no longer matches its snapshot
 */
public class LayoutSnapshotTest {

    /** Pixel 5, Hebrew, right-to-left: what a student's phone looks like. */
    static DeviceConfig hebrewPhone() {
        DeviceConfig d = DeviceConfig.PIXEL_5;
        return d.copy(d.getScreenHeight(), d.getScreenWidth(), d.getXdpi(), d.getYdpi(), d.getOrientation(),
                d.getUiMode(), d.getNightMode(), d.getDensity(), d.getFontScale(), LayoutDirection.RTL, "he",
                d.getRatio(), d.getSize(), d.getKeyboard(), d.getTouchScreen(), d.getKeyboardState(),
                d.getSoftButtons(), d.getNavigation(), d.getScreenRound());
    }

    /** Same choice Paparazzi makes by itself: compare on verify, write the images on record. */
    static SnapshotHandler handler() {
        return Boolean.getBoolean("paparazzi.test.verify")
                ? new SnapshotVerifier(MAX_DIFFERENCE_PERCENT) : new HtmlReportWriter();
    }

    private static final double MAX_DIFFERENCE_PERCENT = 0.1;

    // supportsRtl = true: without it every view is laid out left-to-right, as in an app
    // whose manifest has no android:supportsRtl (ours has it)
    @Rule
    public Paparazzi paparazzi = new Paparazzi(EnvironmentKt.detectEnvironment(), hebrewPhone(),
            "Theme.AloneyNavigation", SessionParams.RenderingMode.NORMAL, true, MAX_DIFFERENCE_PERCENT,
            handler(), Collections.emptySet(), true);

    private View inflate(int layout) {
        return paparazzi.inflate(layout);
    }

    @Test
    public void splash() {
        View v = inflate(R.layout.activity_splash);
        SnapshotSamples.text(v, R.id.splash_version, "גרסה 1.0");
        paparazzi.snapshot(v);
    }

    @Test
    public void search() {
        View v = inflate(R.layout.activity_search);
        SnapshotSamples.fillSearch(v);
        paparazzi.snapshot(v);
    }

    @Test
    public void searchNothingFound() {
        View v = inflate(R.layout.activity_search);
        SnapshotSamples.fillSearchEmpty(v);
        paparazzi.snapshot(v);
    }

    @Test
    public void permissions() {
        View v = inflate(R.layout.activity_splash);
        v.findViewById(R.id.splash_content).setVisibility(View.GONE);
        v.findViewById(R.id.splash_permissions).setVisibility(View.VISIBLE);
        paparazzi.snapshot(v);
    }

    @Test
    public void map() {
        View v = inflate(R.layout.activity_map);
        SnapshotSamples.fillMap(v);
        paparazzi.snapshot(v);
    }

    @Test
    public void arNav() {
        View v = inflate(R.layout.activity_ar_nav);
        SnapshotSamples.fillAr(v);
        paparazzi.snapshot(v);
    }

    @Test
    public void arrivalDialog() {
        View card = inflate(R.layout.dialog_arrival);
        SnapshotSamples.fillArrival(card);
        paparazzi.snapshot(SnapshotSamples.overDimmedScreen(card, Gravity.CENTER));
    }

    @Test
    public void calibrationDialog() {
        View v = inflate(R.layout.dialog_calibration);
        SnapshotSamples.fillCalibration(v);
        paparazzi.snapshot(v);
    }

    @Test
    public void adminLogin() {
        paparazzi.snapshot(inflate(R.layout.activity_admin));
    }

    @Test
    public void adminRoomEdit() {
        View rooms = inflate(R.layout.activity_admin);
        SnapshotSamples.fillAdminRooms(rooms);
        View sheet = inflate(R.layout.sheet_room_edit);
        SnapshotSamples.fillRoomEdit(sheet);
        paparazzi.snapshot(SnapshotSamples.sheetOver(rooms, sheet));
    }

    @Test
    public void mapAlignment() {
        View v = inflate(R.layout.activity_map_alignment);
        SnapshotSamples.fillAlignment(v);
        paparazzi.snapshot(v);
    }
}
