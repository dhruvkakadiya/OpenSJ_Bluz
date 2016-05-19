package surojit.com.opensjbluez;

import android.hardware.Sensor;

import java.util.Set;
import java.util.TreeSet;

/**
 * Created by Surojit on 5/18/2016.
 */
public abstract class BaseSensor {

    String mSensorAddress;
    String mSensorType;
    String mSensorName;
    TreeSet<SensorDataEventListener> mSensorCallback;

    public abstract boolean SubscribeToData(SensorDataEventListener mDataEventListener);
    public abstract boolean UnsubscribeFromData(SensorDataEventListener mDataEventListener);
}
