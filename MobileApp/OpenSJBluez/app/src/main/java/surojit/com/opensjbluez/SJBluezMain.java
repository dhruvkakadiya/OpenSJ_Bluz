package surojit.com.opensjbluez;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.widget.TextView;


public class SJBluezMain extends AppCompatActivity {


    TextView lMainTxt;
    SurfaceView lSfcView;
    private String mSelectedDeviceMac;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sjbluez_main);
        lMainTxt = (TextView) findViewById(R.id.txtMain);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.mnuItemSettings) {
            Intent i = new Intent(this, ActivitySettings.class);
            startActivityForResult(i, 1);
        }
        else if (item.getItemId() == R.id.mnuItemViewGraph)
        {
            Intent i = new Intent(this, ActivitySettings.class);
            i.putExtra("mac",mSelectedDeviceMac);
            startActivity(i);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1 && resultCode == 1) {
            String lMac = data.getStringExtra("mac");
            lMainTxt.setText("Selected device: " + lMac);
            mSelectedDeviceMac = lMac;
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
