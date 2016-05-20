package surojit.com.opensjbluez;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;

public class SelectNetworkDevice extends ListActivity implements AdapterView.OnItemClickListener {

    NetworkDeviceAdapter mNetworkDevicesAdapter = null;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_network_device);
        mNetworkDevicesAdapter = new NetworkDeviceAdapter(this.getApplicationContext());
        this.setListAdapter(mNetworkDevicesAdapter);
        this.getListView().setOnItemClickListener(this);
    }


    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        Object lObj = mNetworkDevicesAdapter.getItem(i);
        if(lObj!=null)
        {
            Intent intent=new Intent();
            intent.putExtra("mac",(String)lObj);
            setResult(2,intent);
            finish();
        }
    }
}
