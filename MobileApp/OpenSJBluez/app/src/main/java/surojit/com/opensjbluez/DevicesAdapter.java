package surojit.com.opensjbluez;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.LinkedHashMap;

/**
 * Created by Surojit on 5/8/2016.
 */
public class DevicesAdapter extends BaseAdapter {

    Object mMapLock = new Object();
    LinkedHashMap<String,SSBLEDevice> mDevicesMap = new LinkedHashMap<>(10);
//    private Set<DataSetObserver> dataSetObservers;
    private Context mContext;

    public DevicesAdapter(Context applicationContext) {
        mContext = applicationContext;
    }

    void InsertDevice(String pDeviceIdentifier, SSBLEDevice pBLEDevice)
    {
        synchronized (mMapLock)
        {
            mDevicesMap.put(pDeviceIdentifier,pBLEDevice);
        }
        notifyDataSetChanged();
    }

    void RemoveDevice(String pDeviceIdentifier)
    {
        synchronized (mMapLock)
        {
            mDevicesMap.remove(pDeviceIdentifier);
        }
        notifyDataSetChanged();
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public boolean isEnabled(int i) {
        return true;
    }

//    @Override
//    public void registerDataSetObserver(DataSetObserver dataSetObserver) {
//        this.dataSetObservers.add(dataSetObserver);
//    }
//
//    @Override
//    public void unregisterDataSetObserver(DataSetObserver dataSetObserver) {
//        this.dataSetObservers.remove(dataSetObserver);
//    }

    @Override
    public int getCount() {
        if(mDevicesMap.size() <= 0)
        {
            return 1;
        }

        return mDevicesMap.size();
    }

    @Override
    public Object getItem(int i) {
        synchronized (mMapLock)
        {
            return mDevicesMap.values().toArray()[i];
        }
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {

        if(view ==  null)
        {
            LayoutInflater lInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = lInflater.inflate(R.layout.device_row,viewGroup,false);
        }


        TextView txtMain = (TextView) view.findViewById(R.id.txtMain);
        TextView txtSub = (TextView) view.findViewById(R.id.txtSub);
        ImageView imgDevice = (ImageView) view.findViewById(R.id.imgDevice);

        if(mDevicesMap.size() <= 0)
        {
                imgDevice.setVisibility(View.INVISIBLE);
            txtMain.setText("No devices discovered");
            txtSub.setVisibility(View.INVISIBLE);
        }
        else {
            SSBLEDevice lDevice = (SSBLEDevice) getItem(i);
            imgDevice.setVisibility(View.VISIBLE);
            txtSub.setVisibility(View.VISIBLE);

            String mName = lDevice.mDevice.getName();
            if(mName == null || mName.length() <=0)
            {
                mName = "Unnamed";
            }

            txtMain.setText(mName);
            txtSub.setText(lDevice.mDevice.getAddress());
        }

        return view;
    }

    @Override
    public int getItemViewType(int i) {
        return 1;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }
}
