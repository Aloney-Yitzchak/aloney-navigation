package il.org.ay.navigation.logic;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Which way the phone points, and steps (T12, FR-04, SDD 4: logic.HeadingService).
 *
 * <ul>
 *   <li><b>Heading</b> from the Rotation Vector sensor, which fuses the compass, the gyroscope
 *       and the accelerometer: steadier than the compass alone (risk R5). Smoothed by
 *       HeadingFilter and turned to true north once a position is known.</li>
 *   <li><b>Accuracy</b>: when Android reports the compass as low or unreliable (a magnet,
 *       a metal fence), {@link #needsCalibration()} is true and the screen asks for a
 *       figure-eight movement.</li>
 *   <li><b>Steps</b> from the Step Detector, between GPS fixes. Android 10+ asks for the
 *       Physical Activity permission for it; without it steps are not counted and
 *       {@link #canCountSteps()} is false.</li>
 * </ul>
 *
 * Not an Android Service: the sensors need no background work. The screen calls
 * {@link #start()} in onResume and {@link #stop()} in onPause, so nothing runs while no
 * navigation screen is visible (NFR-08).
 */
public class HeadingService implements SensorEventListener {

    public interface Listener {
        /** A new smoothed heading, degrees clockwise from true north. */
        void onHeading(float degrees, boolean needsCalibration);

        /** One more step since start(). */
        default void onStep(int steps) {
        }
    }

    private final Context context;
    private final SensorManager sensors;
    private final HeadingFilter filter = new HeadingFilter();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final float[] rotation = new float[9];
    private int accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH;
    private int steps;
    private boolean running;

    public HeadingService(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.sensors = context.getSystemService(SensorManager.class);
    }

    /** Starts the sensors. Returns false when the phone has no rotation sensor. */
    public boolean start() {
        if (running) {
            return true;
        }
        Sensor rotationSensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rotationSensor == null) {
            return false;
        }
        sensors.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        Sensor stepSensor = sensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        if (stepSensor != null && canCountSteps()) {
            sensors.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        steps = 0;
        running = true;
        return true;
    }

    /** Stops all the sensors. Safe to call twice. */
    public void stop() {
        sensors.unregisterListener(this);
        running = false;
    }

    /** True when steps can be counted: the sensor exists and, from Android 10, the permission is granted. */
    public boolean canCountSteps() {
        if (sensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) == null) {
            return false;
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Where the user is, for the magnetic declination. Call once a position is known. */
    public void setPosition(double lat, double lng) {
        GeomagneticField field = new GeomagneticField((float) lat, (float) lng, 0f, System.currentTimeMillis());
        filter.setDeclination(field.getDeclination());
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotation, event.values);
            float heading = filter.add(HeadingMath.azimuth(rotation));
            for (Listener l : listeners) {
                l.onHeading(heading, needsCalibration());
            }
        } else if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            steps++;
            for (Listener l : listeners) {
                l.onStep(steps);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int newAccuracy) {
        if (sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            accuracy = newAccuracy;
        }
    }

    /** SDD 4: getHeading(). Degrees from true north; 0 before the first reading. */
    public float getHeading() {
        return filter.hasValue() ? filter.get() : 0;
    }

    /** SDD 4: getAccuracy(). One of SensorManager.SENSOR_STATUS_*. */
    public int getAccuracy() {
        return accuracy;
    }

    /** True when the compass should be calibrated with a figure-eight movement (risk R5). */
    public boolean needsCalibration() {
        return accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW;
    }

    /** SDD 4: countSteps(). Steps since start(). */
    public int countSteps() {
        return steps;
    }

    public boolean isRunning() {
        return running;
    }

    /** SDD 4: addListener. */
    public void addListener(@NonNull Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }
}
