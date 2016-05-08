package surojit.com.opensjbluez;

import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;

/**
 * Created by Surojit on 5/7/2016.
 */
public class BLEManager extends ScanCallback {


    BluetoothManager mBluetoothManager;

    public BLEManager(BluetoothManager pBluetoothManager) {
        this.mBluetoothManager = pBluetoothManager;
    }

    public void startScan(ScanCallback pScanCallback)
    {
        mBluetoothManager.getAdapter().getBluetoothLeScanner().startScan(pScanCallback);
    }

    public void stopScan(ScanCallback pScanCallback)
    {
        mBluetoothManager.getAdapter().getBluetoothLeScanner().stopScan(pScanCallback);
    }
}
