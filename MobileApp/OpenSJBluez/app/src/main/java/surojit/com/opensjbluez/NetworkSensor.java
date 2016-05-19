package surojit.com.opensjbluez;

import com.firebase.client.Firebase;

/**
 * Created by Surojit on 5/18/2016.
 */
public class NetworkSensor extends BaseSensor {


    boolean isSubscribed = false;
    public NetworkSensor() {

    }

    @Override
    public boolean SubscribeToData(SensorDataEventListener mDataEventListener) {
        this.mSensorCallback.add(mDataEventListener);
        if(!isSubscribed)
        {
            doSubscribe();
        }
        return true;
    }

    private void doSubscribe() {

    }

    @Override
    public boolean UnsubscribeFromData(SensorDataEventListener mDataEventListener) {
        this.mSensorCallback.remove(mDataEventListener);
        return true;
    }
}
