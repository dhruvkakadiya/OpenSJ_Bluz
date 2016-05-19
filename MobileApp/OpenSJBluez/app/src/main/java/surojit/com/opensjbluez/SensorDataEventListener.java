package surojit.com.opensjbluez;

/**
 * Created by Surojit on 5/18/2016.
 */
public interface SensorDataEventListener {

    void SensorDataUpdated(BaseSensor pSensor, String pData);
}
