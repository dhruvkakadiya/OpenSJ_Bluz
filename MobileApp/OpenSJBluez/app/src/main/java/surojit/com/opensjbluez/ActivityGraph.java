package surojit.com.opensjbluez;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

public class ActivityGraph extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_graph);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.getMenuInflater().inflate(R.menu.select_source,menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == 1)
        {
            //BLE device selected
        }
        else if ( requestCode == 2 )
        {
            //Network device selected

        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

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
