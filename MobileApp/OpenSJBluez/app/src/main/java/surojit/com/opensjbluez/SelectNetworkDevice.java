package surojit.com.opensjbluez;

import android.app.ListActivity;
import android.os.Bundle;

public class SelectNetworkDevice extends ListActivity {

    NetworkDeviceAdapter mNetworkDevicesAdapter = null;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_network_device);
        mNetworkDevicesAdapter = new NetworkDeviceAdapter(this.getApplicationContext());
        this.setListAdapter(mNetworkDevicesAdapter);
    }



}
