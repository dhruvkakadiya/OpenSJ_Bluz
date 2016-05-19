package surojit.com.opensjbluez;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.client.Firebase;
import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;


public class SJBluezMain extends AppCompatActivity  {


    private static final String UART_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    private static final String UART_TX_CHARACTERISTIC = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";
    TextView lMainTxt;
    SurfaceView lSfcView;
    private String mSelectedDeviceMac;
    private PermissionListener mPermissionListenener;
    private BLEManager mBLEManager;
    private ScanCallback mScanCallback;
    private BluetoothDevice mConnectedDevice = null;
    private android.bluetooth.BluetoothGattCallback mGattCallback;
    Paint mPaint = new Paint();
    private BluetoothGatt mConnectedGatt;
    private Handler mHandler = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Firebase.setAndroidContext(this.getApplicationContext());
        setContentView(R.layout.activity_sjbluez_main);
        lMainTxt = (TextView) findViewById(R.id.txtMain);
        BluetoothManager lManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBLEManager = new BLEManager(lManager);
        mPaint.setColor(Color.BLACK);
        mHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                lMainTxt.setText((String)msg.obj);
            }
        };

        mGattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);

                switch(newState)
                {
                    case BluetoothGatt.STATE_CONNECTED:
//                        lMainTxt.setText("Connecting to device");
                        mConnectedGatt = gatt;
                        gatt.discoverServices();
                        break;
                    case BluetoothGatt.STATE_CONNECTING:
//                        lMainTxt.setText("Connected");
                        break;
                    case BluetoothGatt.STATE_DISCONNECTED:
//                        lMainTxt.setText("Disconnected from device");
                        ConnectToSelectedDevice();
                        mConnectedGatt = null;
                        break;
                    case BluetoothGatt.STATE_DISCONNECTING:
//                        lMainTxt.setText("Disconnecting");

                        mConnectedGatt = null;
                        break;
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);

                List<BluetoothGattService> lServicesList =  gatt.getServices();
                boolean didFind = false;
                for (BluetoothGattService lService :
                        lServicesList) {
                    if ( lService.getUuid().compareTo(UUID.fromString(UART_SERVICE)) == 0 )
                    {
                        //Subscribe to this service
                        List<BluetoothGattCharacteristic> lServiceCharacteristics = lService.getCharacteristics();
                        for (BluetoothGattCharacteristic lChar :
                                lServiceCharacteristics) {
                            if ( lChar.getUuid().compareTo(UUID.fromString(UART_TX_CHARACTERISTIC)) == 0)
                            {
//                                if( (lChar.getProperties() & BluetoothGattCharacteristic.PRO)  > 0)
//                                {
                                    List<BluetoothGattDescriptor> lCharDescriptors = lChar.getDescriptors();
                                    Iterator<BluetoothGattDescriptor> iterator = lCharDescriptors.iterator();
                                    while(iterator.hasNext())
                                    {
                                        BluetoothGattDescriptor gattDescriptor = iterator.next();
                                        gattDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                        String lResult = gatt.writeDescriptor(gattDescriptor) ? "SUCCESS" : "FAILURE";
//                                        lMainTxt.setText ("Subscription - " + lResult);
                                        didFind = true;
                                        gatt.readCharacteristic(lChar);
                                    }

//                                    gatt.writeCharacteristic(lChar);
//                                    break;
//                                }
                            }
                        }
                    }
                }
                if(didFind)
                {
//                    lMainTxt.setText("Connected to device");
                    Log.i("Located","Found");
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicRead(gatt, characteristic, status);
                Canvas hardwareCanvas = lSfcView.getHolder().getSurface().lockHardwareCanvas();
                hardwareCanvas.drawARGB(255,255,255,255);
                hardwareCanvas.drawText(new String(characteristic.getValue()),0,0,mPaint);
                lSfcView.getHolder().unlockCanvasAndPost(hardwareCanvas);
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                Log.d("Change",characteristic.getStringValue(0));
            }

            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorRead(gatt, descriptor, status);
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                byte [] array =descriptor.getCharacteristic().getValue();
                if(array != null) {
                    String s = new String(array);
                    Log.i("TAG", s);
                }
                super.onDescriptorWrite(gatt, descriptor, status);
            }

            @Override
            public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
                super.onReliableWriteCompleted(gatt, status);
            }

            @Override
            public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                super.onReadRemoteRssi(gatt, rssi, status);
            }

            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                super.onMtuChanged(gatt, mtu, status);
            }
        };

        mPermissionListenener = new PermissionListener() {
            @Override
            public void onPermissionGranted() {
//                Toast.makeText(getApplicationContext(),"Thanks!",Toast.LENGTH_SHORT).show();
                    return;
            }


            @Override
            public void onPermissionDenied(ArrayList<String> arrayList) {
                for (String lPermission :
                        arrayList) {
                    Toast.makeText(getApplicationContext(),"Permission - " + lPermission + " is required",Toast.LENGTH_SHORT).show();
                }
                finish();
            }
        };
        TedPermission lTedPermission = new TedPermission(this.getApplicationContext());
        lTedPermission.setPermissionListener(mPermissionListenener);
        lTedPermission.setDeniedMessage("Permissions are needed for correct operation");
        lTedPermission.setPermissions(Manifest.permission.BLUETOOTH,Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.INTERNET,Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION);
        lTedPermission.check();
    }

    private void ConnectToSelectedDevice() {
        mConnectedDevice.connectGatt(getApplicationContext(), false, mGattCallback);
    }


    private void ClearConnections()
    {
        mConnectedDevice = null;
        mSelectedDeviceMac = "";
        if(mScanCallback!=null) {
            mBLEManager.stopScan(mScanCallback);
        }
        if(mConnectedGatt != null)
        {
            mConnectedGatt.disconnect();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.mnuItemSettings) {
            Intent i = new Intent(this, ActivitySettings.class);
            ClearConnections();
            startActivityForResult(i, 1);
        }
        else if (item.getItemId() == R.id.mnuItemViewGraph)
        {
            Intent i = new Intent(this, ActivityGraph.class);
            i.putExtra("mac",mSelectedDeviceMac);
            ClearConnections();
            startActivity(i);
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("NewApi")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1 && resultCode == 1) {
            String lMac = data.getStringExtra("mac");
            lMainTxt.setText("Selected device: " + lMac);
            mSelectedDeviceMac = lMac;
            mScanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);

                    if(result.getDevice().getAddress().compareTo(mSelectedDeviceMac) == 0)
                    {
                        mBLEManager.stopScan(this);
                        if(mConnectedDevice == null || mConnectedDevice.getAddress().compareTo(result.getDevice().getAddress()) != 0) {
                                mConnectedDevice = result.getDevice();
                                ConnectToSelectedDevice();
                            }
                    }


                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    super.onBatchScanResults(results);
                }

                @Override
                public void onScanFailed(int errorCode) {
                    lMainTxt.setText("Scan failed - " + errorCode);
                    super.onScanFailed(errorCode);
                }
            };
            mBLEManager.startScan(mScanCallback);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater lMenuInflater = getMenuInflater();
        lMenuInflater.inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);

    }

    @Override
    public void onStart() {
        super.onStart();

    }

    @Override
    public void onStop() {
        super.onStop();


    }

}
