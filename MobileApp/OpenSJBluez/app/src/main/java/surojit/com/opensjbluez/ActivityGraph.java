package surojit.com.opensjbluez;

import android.annotation.TargetApi;
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
import android.graphics.Rect;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.firebase.client.ChildEventListener;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.MutableData;
import com.firebase.client.Transaction;
import com.firebase.client.ValueEventListener;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ActivityGraph extends AppCompatActivity {

    private static final String UART_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    private static final String UART_TX_CHARACTERISTIC = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";
    private String mSelectedDeviceMac;
    private BLEManager mBLEManager;
    private ScanCallback mScanCallback;
    private BluetoothDevice mConnectedDevice = null;
    private android.bluetooth.BluetoothGattCallback mGattCallback;
    private BluetoothGatt mConnectedGatt;
    ConcurrentLinkedQueue<String> mQueue;
    TextView lMainTxt;
    private boolean lThreadRun = true;
    private LineChart mChart;
    private Thread mDebugThread;
    private LineData mLineData = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_graph);
        lMainTxt = (TextView) findViewById(R.id.txtInfo);
        mQueue = new ConcurrentLinkedQueue<>();
        BluetoothManager lManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBLEManager = new BLEManager(lManager);

         setupChart();

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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        lMainTxt.setText("Subscribing to data");
                    }
                });

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
                                    if (!mDebugThread.isAlive()) {
                                        lThreadRun = true;
                                        mDebugThread.start();
                                    }
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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        lMainTxt.setText("Subscribed to data");
                    }
                });
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


        mDebugThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Firebase lFirebase = null;

                lFirebase = new Firebase(getString(R.string.FIREBASE_URL)).child(getString(R.string.firebase_opensj_blues_sensors_readings));
                lFirebase = lFirebase.child(mSelectedDeviceMac);

                while (lThreadRun) {
                    try {
                        if (mQueue.size() > 0) {

                            String lString = mQueue.poll();
                            lFirebase.push().setValue(lString);
                            String[] lParts = lString.trim().split(" ");

                            int tiltX = Integer.parseInt(lParts[0]);
                            int tiltY = Integer.parseInt(lParts[1]);
                            int tiltZ = Integer.parseInt(lParts[2]);
                            int lLight = Integer.parseInt(lParts[3]);

//                            dX.push(new Entry((float)tiltX,dX.size()));
//                            dY.push(new Entry((float)tiltY,dY.size()));
//                            dZ.push(new Entry((float)tiltZ,dZ.size()));
//                            dL.push(new Entry((float)lLight,dL.size()));
//                            lDataX.addEntry(new Entry((float)tiltX,lDataX.getEntryCount()));
//                            lDataY.addEntry(new Entry((float)tiltY,lDataY.getEntryCount()));
//                            lDataZ.addEntry(new Entry((float)tiltZ,lDataZ.getEntryCount()));
//                            lDataLumen.addEntry(new Entry((float)lLight,lDataLumen.getEntryCount()));
                            mLineData.addEntry(new Entry((float)tiltX,mLineData.getXValCount()),0);
                            mLineData.addEntry(new Entry((float)tiltY,mLineData.getXValCount()),1);
                            mLineData.addEntry(new Entry((float)tiltZ,mLineData.getXValCount()),2);
                            mLineData.addEntry(new Entry((float)lLight,mLineData.getXValCount()),3);

                            mLineData.addXValue(String.valueOf(mLineData.getXValCount()+1));
                            mLineData.notifyDataChanged();
//                            mChart.setData(mLineData);
//                            mChart.postInvalidate();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mChart.setVisibleXRangeMaximum(100);
                                    // mChart.setVisibleYRange(30, AxisDependency.LEFT);
                                    // move to the latest entry
                                    mChart.moveViewToX(mLineData.getXValCount() - 100);
                                    mChart.notifyDataSetChanged();
                                }
                            });

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
    }

    LineDataSet lDataX,lDataY,lDataZ,lDataLumen;
    LinkedList<Entry> dX = new LinkedList<>();
    LinkedList<Entry> dY = new LinkedList<>();
    LinkedList<Entry> dZ = new LinkedList<>();
    LinkedList<Entry> dL = new LinkedList<>();

    private void setupChart() {
        lDataX = new LineDataSet(dX,"X");
        lDataX.setColor(Color.RED);
        lDataX.setDrawCircles(false);


        lDataY = new LineDataSet(dY,"Y");
        lDataY.setDrawCircles(false);
        lDataY.setColor(Color.GREEN);

        lDataZ = new LineDataSet(dZ, "Z");
        lDataZ.setColor(Color.BLUE);
        lDataZ.setDrawCircles(false);

        lDataLumen = new LineDataSet(dL,"Lum");
        lDataLumen.setColor(Color.MAGENTA);
        lDataLumen.setDrawCircles(false);

        mLineData = new LineData();
        mLineData.addDataSet(lDataX);
        mLineData.addDataSet(lDataY);
        mLineData.addDataSet(lDataZ);
        mLineData.addDataSet(lDataLumen);

        mChart = (LineChart) findViewById(R.id.viewGraph);
//        mChart.setOnChartGestureListener(this);
//        mChart.setOnChartValueSelectedListener(this);
        mChart.setDrawGridBackground(true);
        mChart.setData(mLineData);

        // no description text
        mChart.setDescription("X,Y,Z and luminosity values from SJOne sensor board");
        mChart.setNoDataTextDescription("No sensor connected");

        // enable touch gestures
        mChart.setTouchEnabled(true);

        // enable scaling and dragging
        mChart.setDragEnabled(true);
        mChart.setScaleEnabled(true);
         mChart.setScaleXEnabled(true);
         mChart.setScaleYEnabled(true);

        // if disabled, scaling can be done on x- and y-axis separately
        mChart.setPinchZoom(true);

        // set an alternative background color
        // mChart.setBackgroundColor(Color.GRAY);


        // x-axis limit line
//        LimitLine llXAxis = new LimitLine(1000f, "Reading number");
//        llXAxis.setLineWidth(4f);
//        llXAxis.enableDashedLine(10f, 10f, 0f);
//        llXAxis.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
//        llXAxis.setTextSize(10f);

        XAxis xAxis = mChart.getXAxis();

        //xAxis.setValueFormatter(new MyCustomXAxisValueFormatter());
//        xAxis.addLimitLine(llXAxis); // add x-axis limit line

//        LimitLine ll1 = new LimitLine(130f, "Upper Limit");
//        ll1.setLineWidth(4f);
//        ll1.enableDashedLine(10f, 10f, 0f);
//        ll1.setLabelPosition(LimitLabelPosition.RIGHT_TOP);
//        ll1.setTextSize(10f);
//        ll1.setTypeface(tf);
//
//        LimitLine ll2 = new LimitLine(-30f, "Lower Limit");
//        ll2.setLineWidth(4f);
//        ll2.enableDashedLine(10f, 10f, 0f);
//        ll2.setLabelPosition(LimitLabelPosition.RIGHT_BOTTOM);
//        ll2.setTextSize(10f);
//        ll2.setTypeface(tf);

        YAxis leftAxis = mChart.getAxisLeft();
        leftAxis.removeAllLimitLines(); // reset all limit lines to avoid overlapping lines

//        leftAxis.addLimitLine(ll1);
//        leftAxis.addLimitLine(ll2);
//        leftAxis.setAxisMaxValue(220f);
//        leftAxis.setAxisMinValue(-50f);
        //leftAxis.setYOffset(20f);
        leftAxis.enableGridDashedLine(10f, 10f, 0f);
        leftAxis.setDrawZeroLine(true);

        // limit lines are drawn behind data (and not on top)
        leftAxis.setDrawLimitLinesBehindData(true);

        mChart.getAxisRight().setEnabled(false);

        //mChart.getViewPortHandler().setMaximumScaleY(2f);
        //mChart.getViewPortHandler().setMaximumScaleX(2f);

//        mChart.setVisibleXRange(20);
//        mChart.setVisibleYRange(20f, AxisDependency.LEFT);
//        mChart.centerViewTo(20, 50, AxisDependency.LEFT);

        mChart.animateX(2500, Easing.EasingOption.EaseInOutQuart);
//        mChart.invalidate();

        // get the legend (only possible after setting data)
        Legend l = mChart.getLegend();

        // modify the legend ...
        // l.setPosition(LegendPosition.LEFT_OF_CHART);
        l.setForm(Legend.LegendForm.LINE);

        // // dont forget to refresh the drawing
         mChart.invalidate();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.getMenuInflater().inflate(R.menu.select_source,menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onStop() {
        ClearConnections();
        super.onStop();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        if(resultCode == RESULT_CANCELED) { return; }

        if(requestCode == 1)
        {
            //BLE device selected
            String lMac = data.getStringExtra("mac");
            mSelectedDeviceMac = lMac;
            lMainTxt.setText("Selected device: " + lMac);
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
        else if ( requestCode == 2 )
        {
            //Network device selected
            String lMac = data.getStringExtra("mac");
            mSelectedDeviceMac = lMac;

            lMainTxt.setText("Connecting to network sensor - " + lMac);
            Firebase lFb = new Firebase(getString(R.string.FIREBASE_URL)).child(getString(R.string.firebase_opensj_blues_sensors_readings));
            lFb = lFb.child(lMac);
            lFb.limitToLast(100).addChildEventListener(new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    if(dataSnapshot.exists())
                    {

                        String[] lParts = dataSnapshot.getValue().toString().trim().split(" ");

                        int tiltX = Integer.parseInt(lParts[0]);
                        int tiltY = Integer.parseInt(lParts[1]);
                        int tiltZ = Integer.parseInt(lParts[2]);
                        int lLight = Integer.parseInt(lParts[3]);

//                            dX.push(new Entry((float)tiltX,dX.size()));
//                            dY.push(new Entry((float)tiltY,dY.size()));
//                            dZ.push(new Entry((float)tiltZ,dZ.size()));
//                            dL.push(new Entry((float)lLight,dL.size()));
//                            lDataX.addEntry(new Entry((float)tiltX,lDataX.getEntryCount()));
//                            lDataY.addEntry(new Entry((float)tiltY,lDataY.getEntryCount()));
//                            lDataZ.addEntry(new Entry((float)tiltZ,lDataZ.getEntryCount()));
//                            lDataLumen.addEntry(new Entry((float)lLight,lDataLumen.getEntryCount()));
                        mLineData.addEntry(new Entry((float)tiltX,mLineData.getXValCount()),0);
                        mLineData.addEntry(new Entry((float)tiltY,mLineData.getXValCount()),1);
                        mLineData.addEntry(new Entry((float)tiltZ,mLineData.getXValCount()),2);
                        mLineData.addEntry(new Entry((float)lLight,mLineData.getXValCount()),3);

                        mLineData.addXValue(String.valueOf(mLineData.getXValCount()+1));
                        mLineData.notifyDataChanged();
//                            mChart.setData(mLineData);
//                            mChart.postInvalidate();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mChart.setVisibleXRangeMaximum(100);
                                // mChart.setVisibleYRange(30, AxisDependency.LEFT);
                                // move to the latest entry
                                mChart.moveViewToX(mLineData.getXValCount() - 100);
                                mChart.notifyDataSetChanged();
                            }
                        });

                    }
                }

                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {

                }

                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {

                }

                @Override
                public void onChildMoved(DataSnapshot dataSnapshot, String s) {

                }

                @Override
                public void onCancelled(FirebaseError firebaseError) {

                }
            });
        }
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

        Firebase lFirebase = new Firebase(getString(R.string.FIREBASE_URL)).child(getString(R.string.firebase_opensj_blues_sensors));
        lFirebase = lFirebase.child(mSelectedDeviceMac);
        lFirebase.setValue(Calendar.getInstance().getTime());

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
        ClearConnections();

        if(item.getItemId() == R.id.select_device_ble)
        {
                Intent i = new Intent(this, ActivitySettings.class);
                startActivityForResult(i, 1);
        }
        else if (item.getItemId() == R.id.select_device_network)
        {
            Intent i = new Intent(this, SelectNetworkDevice.class);
            startActivityForResult(i, 2);
        }

        return super.onOptionsItemSelected(item);
    }
}
