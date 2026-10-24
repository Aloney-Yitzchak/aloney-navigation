package il.org.ay.navigation.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.SensorEventBuilder;
import org.robolectric.shadows.ShadowSensor;
import org.robolectric.shadows.ShadowSensorManager;

import java.util.ArrayList;
import java.util.List;

/** T12: HeadingService with Robolectric's sensors: the heading, calibration, steps, start/stop. */
@RunWith(RobolectricTestRunner.class)
public class HeadingServiceTest {

    private Context context;
    private ShadowSensorManager shadow;
    private Sensor rotation;
    private Sensor stepDetector;
    private final List<Float> headings = new ArrayList<>();
    private final List<Boolean> calibrate = new ArrayList<>();

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        SensorManager sm = context.getSystemService(SensorManager.class);
        shadow = Shadows.shadowOf(sm);
        rotation = ShadowSensor.newInstance(Sensor.TYPE_ROTATION_VECTOR);
        stepDetector = ShadowSensor.newInstance(Sensor.TYPE_STEP_DETECTOR);
        shadow.addSensor(rotation);
        shadow.addSensor(stepDetector);
    }

    private HeadingService started() {
        HeadingService h = new HeadingService(context);
        h.addListener(new HeadingService.Listener() {
            @Override
            public void onHeading(float degrees, boolean needsCalibration) {
                headings.add(degrees);
                calibrate.add(needsCalibration);
            }
        });
        assertTrue(h.start());
        return h;
    }

    /** A rotation vector for a phone turned by {@code degrees} about the vertical, clockwise seen from above. */
    private void turnTo(HeadingService h, float degrees) {
        double half = Math.toRadians(-degrees) / 2;    // the quaternion turns counter-clockwise
        float[] q = {0, 0, (float) Math.sin(half), (float) Math.cos(half)};
        h.onSensorChanged(SensorEventBuilder.newBuilder().setSensor(rotation).setValues(q).build());
    }

    @Test
    public void flatPhone_headingFollowsTheTopEdge() {
        HeadingService h = started();
        turnTo(h, 0);
        assertEquals(0, h.getHeading(), 0.5);
        for (int i = 0; i < 60; i++) {
            turnTo(h, 90);
        }
        assertEquals(90, h.getHeading(), 0.5);
        assertEquals(61, headings.size());
    }

    @Test
    public void lowCompassAccuracy_asksForCalibration() {
        HeadingService h = started();
        turnTo(h, 0);
        assertFalse(calibrate.get(0));
        h.onAccuracyChanged(rotation, SensorManager.SENSOR_STATUS_ACCURACY_LOW);
        turnTo(h, 0);
        assertTrue(h.needsCalibration());
        assertTrue(calibrate.get(1));
    }

    @Test
    public void withoutThePhysicalActivityPermission_stepsAreNotCounted() {
        HeadingService h = started();
        assertFalse(h.canCountSteps());
        assertFalse(shadow.hasListener(h, stepDetector));
    }

    @Test
    public void withThePermission_stepsAreCounted() {
        Shadows.shadowOf((android.app.Application) context)
                .grantPermissions(android.Manifest.permission.ACTIVITY_RECOGNITION);
        HeadingService h = started();
        assertTrue(shadow.hasListener(h, stepDetector));
        h.onSensorChanged(SensorEventBuilder.newBuilder().setSensor(stepDetector).setValues(new float[]{1}).build());
        h.onSensorChanged(SensorEventBuilder.newBuilder().setSensor(stepDetector).setValues(new float[]{1}).build());
        assertEquals(2, h.countSteps());
    }

    @Test
    public void stop_releasesTheSensors() {
        HeadingService h = started();
        assertTrue(shadow.hasListener(h, rotation));
        h.stop();
        assertFalse(shadow.hasListener(h, rotation));    // NFR-08
        assertFalse(h.isRunning());
    }

    @Test
    public void noRotationSensor_startSaysNo() {
        shadow.removeSensor(rotation);
        assertFalse(new HeadingService(context).start());
    }
}
