package surojit.com.opensjbluez;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;

import org.w3c.dom.Text;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by Surojit on 5/18/2016.
 */
public class NetworkDeviceAdapter extends BaseAdapter {

    Firebase mFirebase = null;
    Context mContext;
    LinkedList<BaseSensor> mSensorsList;


    public NetworkDeviceAdapter(Context pContext)
    {
        mContext = pContext;
        final String lFirebaseURL = mContext.getString(R.string.FIREBASE_URL);
        mFirebase = new Firebase(lFirebaseURL);
        mFirebase = mFirebase.child(pContext.getString(R.string.firebase_opensj_blues_sensors));
        loadData();
        mSensorsList = new LinkedList<>();
    }

    private void loadData() {
        mFirebase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists())
                {
                    Iterator i = dataSnapshot.getChildren().iterator();
                    while(i.hasNext())
                    {
                        DataSnapshot lChildSnapshot = (DataSnapshot) i.next();
                        BaseSensor lSensor = lChildSnapshot.getValue(BaseSensor.class);
                        mSensorsList.add(lSensor);
                    }
                }
                else
                {
                    mSensorsList.clear();
                }
                notifyDataSetChanged();
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                return;
            }
        });
    }

    @Override
    public int getCount() {

        if(mSensorsList.size() <= 0 )
        {
            return 1;
        }

        return mSensorsList.size();
    }

    @Override
    public Object getItem(int i) {
        if(mSensorsList.size() <=0)
        {
            return null;
        }

        return mSensorsList.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {

        if(mSensorsList.size() <= 0)
        {
            TextView lTextView = new TextView(viewGroup.getContext());
            lTextView.setText("No connected sensors detected");
            return lTextView;
        }



        if(view == null || view instanceof TextView)
        {
            LayoutInflater lInflater = LayoutInflater.from(viewGroup.getContext());
            view = lInflater.inflate(R.layout.device_row,viewGroup,false);
        }

        TextView lMain = (TextView) view.findViewById(R.id.txtMain);
        TextView lSub = (TextView) view.findViewById(R.id.txtMain);

        BaseSensor lSensor = mSensorsList.get(i);
        lMain.setText(lSensor.mSensorAddress);
        lSub.setText("");
        return null;
    }
}