package surojit.com.opensjbluez;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.yarolegovich.lovelydialog.LovelyInfoDialog;

import java.util.List;

public class ActivitySettings extends AppCompatActivity implements AdapterView.OnItemClickListener {

    BLEManager mBLEManager = null;
    Menu mMyContextMenu = null;
    @SuppressLint("NewApi")
    ScanCallback mScanCallbackLE = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            switch( callbackType )
            {
                case ScanSettings.CALLBACK_TYPE_ALL_MATCHES:
                    mDevicesAdapter.InsertDevice(result.getDevice().getAddress(),new SSBLEDevice(result.getDevice()));
                     break;
                case ScanSettings.CALLBACK_TYPE_MATCH_LOST:
                    break;
                case ScanSettings.CALLBACK_TYPE_FIRST_MATCH:

                break;

                default:
                    Log.e(getApplicationInfo().name,"Unknown callback type " + callbackType);
                    break;
            }
            super.onScanResult(callbackType, result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            new LovelyInfoDialog(getApplicationContext())
                    .setTopColorRes(R.color.colorPrimaryDark)
                    .setIcon(R.drawable.ic_info)
                    .setTitle("Scan failed")
                    .setMessage("BLE Scan failed with error code - " + errorCode)
                    .show();
            doStopScan();
            super.onScanFailed(errorCode);
        }
    };

    private boolean isScanning = false;
    private ListView mDevicesList;
    private DevicesAdapter mDevicesAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_settings);

        BluetoothManager lManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBLEManager = new BLEManager(lManager);

        mDevicesList = (ListView) findViewById(R.id.lstDevices);
        mDevicesAdapter = new DevicesAdapter(getApplicationContext());
        mDevicesList.setAdapter(mDevicesAdapter);
        mDevicesList.setOnItemClickListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_scan, menu);
        mMyContextMenu = menu;
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.mnuItemStartStopScan)
        {
            if(!isScanning)
            {
                doScan();
            }
            else
            {
                doStopScan();

            }
        }

        return super.onOptionsItemSelected(item);
    }

    private void doStopScan() {
        MenuItem lStartStopMenuItem =  mMyContextMenu.findItem(R.id.mnuItemStartStopScan);
        lStartStopMenuItem.setTitle("Scan");
        isScanning = false;
        mBLEManager.stopScan(mScanCallbackLE);
    }

    private void doScan() {

        MenuItem lStartStopMenuItem =  mMyContextMenu.findItem(R.id.mnuItemStartStopScan);
        lStartStopMenuItem.setTitle("Stop");
        isScanning = true;
        mBLEManager.startScan(mScanCallbackLE);
    }


    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        SSBLEDevice lDevice = (SSBLEDevice) mDevicesAdapter.getItem(i);
        Intent intent=new Intent();
        intent.putExtra("mac",lDevice.mDevice.getAddress());
        setResult(1,intent);
        finish();
    }
}
