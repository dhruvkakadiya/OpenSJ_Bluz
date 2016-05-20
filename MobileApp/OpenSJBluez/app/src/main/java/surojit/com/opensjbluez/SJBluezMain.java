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
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.client.Firebase;
import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;


public class SJBluezMain extends AppCompatActivity implements BallCallback {


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
    private BluetoothGatt mConnectedGatt;
    ConcurrentLinkedQueue<String> mQueue;
    Paint mPaint = new Paint();
    private Handler mHandler = null;
    public boolean lThreadRun = true;
    SurfaceHolder lSfcViewHolder;
    float xAccl =0;
    float yAccl =0;
    BouncingBall bouncingBall;

    @Override
    public float getXAcceleration() {

        String lPos = mQueue.poll();
        if(lPos == null)
        {
            xAccl = 0;
        }
        else
        {
            String []parts = lPos.split(" ");
            xAccl = (float) Integer.parseInt(parts[0]);
            xAccl = xAccl / 10.0f;
        }
        return  xAccl;
    }


    @Override
    public float getYAcceleration() {
        String lPos = mQueue.poll();
        if(lPos == null)
        {
            yAccl = 0;
        }
        else
        {
            String []parts = lPos.split(" ");
            yAccl = (float) Integer.parseInt(parts[1]);
            yAccl = yAccl / 10.0f;
        }
        return  yAccl;
    }

    enum Orientation_t {
        INVALID,
        UP,
        DOWN,
        LEFT,
        RIGHT
    }

    Thread mDebugThread = new Thread(new Runnable() {
        @Override
        public void run() {
            Rect mRect = new Rect();
            Orientation_t lOrientationValue = Orientation_t.INVALID;
            while (lThreadRun) {
                try {
                    if (mQueue.size() > 0) {

                        lSfcView.getDrawingRect(mRect);
                        Canvas hardwareCanvas = lSfcViewHolder.lockCanvas();
                        hardwareCanvas.drawARGB(255, 255, 255, 255);
                        String lString = mQueue.poll();

                        String[] lParts = lString.split(" ");
                        int tiltX = Integer.parseInt(lParts[0]);
                        int tiltY = Integer.parseInt(lParts[1]);
                        int tiltZ = Integer.parseInt(lParts[2]);

                        int lLight = Integer.parseInt(lParts[0]);
                        String lDirection = "";
                        while (true) {
                            lOrientationValue = Orientation_t.INVALID;
                            lDirection = "NEUTRAL";
                            if (tiltX >= 30) {
                                lOrientationValue = Orientation_t.LEFT;
                                lDirection = "LEFT";
                                break;
                            } else if (tiltX <= -30) {
                                lOrientationValue = Orientation_t.RIGHT;
                                lDirection = "RIGHT";
                                break;
                            } else if (tiltY <= -50) {
                                lOrientationValue = Orientation_t.DOWN;
                                lDirection = "DOWN";
                                break;
                            } else if (tiltY >= 50) {
                                lOrientationValue = Orientation_t.UP;
                                lDirection = "UP";
                                break;
                            }
                            break;
                        }
                        hardwareCanvas.drawText(lDirection,mRect.centerX() - mRect.centerX()/2,mRect.centerY(),mPaint);
                        hardwareCanvas.drawText(lString,mRect.centerX() - mRect.centerX()/2,mRect.centerY()+250,mPaint);
                        lSfcViewHolder.unlockCanvasAndPost(hardwareCanvas);
                    } else {
                        synchronized (mQueue) {
                            mQueue.wait();
                        }
                    }


                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    });

    private void setBouncingBall() {
        // initializing sensors

        // obtain screen width and height
        Display display = ((WindowManager) this.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        final float[] mWidthScreen = {display.getWidth()};
        final float[] mHeightScreen = {display.getHeight()};
        llyOut.post(new Runnable() {
            public void run() {
                bouncingBall.setWidth(llyOut.getWidth());
                bouncingBall.setHeight(llyOut.getHeight());
            }
        });

        // initializing the view that renders the ball
        bouncingBall = new BouncingBall(this);
        bouncingBall.setOvalCenter((int) (mWidthScreen[0] * 0.6), (int) (mHeightScreen[0] * 0.6));

        llyOut.addView(bouncingBall);
    }

    LinearLayout llyOut;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Firebase.setAndroidContext(this.getApplicationContext());
        setContentView(R.layout.activity_sjbluez_main);
        lMainTxt = (TextView) findViewById(R.id.txtMain);
        BluetoothManager lManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBLEManager = new BLEManager(lManager);
        mPaint.setColor(Color.BLACK);
        mPaint.setTextSize(100);
        mQueue = new ConcurrentLinkedQueue<>();
        lSfcView = (SurfaceView) findViewById(R.id.sfcView);
        lSfcView.setVisibility(View.GONE);

        llyOut = (LinearLayout) findViewById(R.id.llyout);
//        BouncingBall mmBB = new BouncingBall(this);
//        mBallThread = new BallThread(lSfcView.getHolder(),mmBB);
//        mmBB.setOvalCenter(320,240);
//        llyOut.addView(mmBB);
//        mBallThread.start();
//        mBallThread.setRunning(true);

//
//        lSfcViewHolder = lSfcView.getHolder();
//        lSfcViewHolder.addCallback(new SurfaceHolder.Callback() {
//            @Override
//            public void surfaceCreated(SurfaceHolder surfaceHolder) {
//
//
//            }
//
//            @Override
//            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
//
//            }
//
//            @Override
//            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
//
//            }
//        });
        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                lMainTxt.setText((String) msg.obj);
            }
        };

        mGattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, final int newState) {
                super.onConnectionStateChange(gatt, status, newState);

                switch (newState) {
                    case BluetoothGatt.STATE_CONNECTED:
                        mConnectedGatt = gatt;
                        gatt.discoverServices();
                        break;
                    case BluetoothGatt.STATE_CONNECTING:
                        break;
                    case BluetoothGatt.STATE_DISCONNECTED:
                        ConnectToSelectedDevice();
                        mConnectedGatt = null;
                        break;
                    case BluetoothGatt.STATE_DISCONNECTING:
                        mConnectedGatt = null;
                        break;
                }

                runOnUiThread(new Runnable() {
                    String connState[] = { "DISCONNECTED","CONNECTING", "CONNECTED", "DISCONNECTING",};

                    @Override
                    public void run() {
                        lMainTxt.setText("Connection status changed " + connState[newState]);
                    }
                });
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);

                List<BluetoothGattService> lServicesList = gatt.getServices();
                boolean didFind = false;
                for (BluetoothGattService lService :
                        lServicesList) {
                    if (lService.getUuid().compareTo(UUID.fromString(UART_SERVICE)) == 0) {
                        //Subscribe to this service
                        List<BluetoothGattCharacteristic> lServiceCharacteristics = lService.getCharacteristics();
                        for (BluetoothGattCharacteristic lChar :
                                lServiceCharacteristics) {
                            if (lChar.getUuid().compareTo(UUID.fromString(UART_TX_CHARACTERISTIC)) == 0) {
//                                if( (lChar.getProperties() & BluetoothGattCharacteristic.PRO)  > 0)
//                                {
                                List<BluetoothGattDescriptor> lCharDescriptors = lChar.getDescriptors();
                                Iterator<BluetoothGattDescriptor> iterator = lCharDescriptors.iterator();
                                while (iterator.hasNext()) {
                                    BluetoothGattDescriptor gattDescriptor = iterator.next();
                                    gattDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                    String lResult = gatt.writeDescriptor(gattDescriptor) ? "SUCCESS" : "FAILURE";
//                                        lMainTxt.setText ("Subscription - " + lResult);
                                    didFind = true;
                                    gatt.setCharacteristicNotification(lChar, true);
//                                    if (!mDebugThread.isAlive()) {
//                                        lThreadRun = true;
//                                        mDebugThread.start();
//                                    }
//                                        gatt.readCharacteristic(lChar);
                                }

//                                    gatt.writeCharacteristic(lChar);
//                                    break;
//                                }
                            }
                        }
                    }
                }
                if (didFind) {
//                    lMainTxt.setText("Connected to device");
                    Log.i("Located", "Found");
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicRead(gatt, characteristic, status);

            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                mQueue.add(characteristic.getStringValue(0));

                synchronized (mQueue) {
                    mQueue.notify();
                }
            }

            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorRead(gatt, descriptor, status);
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                byte[] array = descriptor.getCharacteristic().getValue();
                if (array != null) {
                    String s = new String(array);
                    Log.i("TAG", s);
                }
                Log.i("BLUEZ", "Descriptor write");
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
                    Toast.makeText(getApplicationContext(), "Permission - " + lPermission + " is required", Toast.LENGTH_SHORT).show();
                }
                finish();
            }
        };
        TedPermission lTedPermission = new TedPermission(this.getApplicationContext());
        lTedPermission.setPermissionListener(mPermissionListenener);
        lTedPermission.setDeniedMessage("Permissions are needed for correct operation");
        lTedPermission.setPermissions(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.INTERNET, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION);
        lTedPermission.check();
        setBouncingBall();
    }

    private void ConnectToSelectedDevice() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                lMainTxt.setText("Connecting to device ...");
                if (mConnectedDevice != null) {
                    mConnectedDevice.connectGatt(getApplicationContext(), false, mGattCallback);
                }
            }
        });

    }


    private void ClearConnections() {
        mConnectedDevice = null;
        mSelectedDeviceMac = "";
        if (mScanCallback != null) {
            mBLEManager.stopScan(mScanCallback);
        }
        if (mConnectedGatt != null) {
            mConnectedGatt.disconnect();
            mConnectedGatt.close();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.mnuItemSettings) {
            Intent i = new Intent(this, ActivitySettings.class);
            ClearConnections();
            startActivityForResult(i, 1);
        } else if (item.getItemId() == R.id.mnuItemViewGraph) {
            Intent i = new Intent(this, ActivityGraph.class);
            i.putExtra("mac", mSelectedDeviceMac);
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

                    if (result.getDevice().getAddress().compareTo(mSelectedDeviceMac) == 0) {
                        mBLEManager.stopScan(this);
                        if (mConnectedDevice == null || mConnectedDevice.getAddress().compareTo(result.getDevice().getAddress()) != 0) {
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
            this.runOnUiThread(new Runnable() {
                                   @Override
                                   public void run() {
                                       mBLEManager.startScan(mScanCallback);
                                   }
                               }
            );

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
