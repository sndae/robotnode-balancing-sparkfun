package com.xmerx.balnodebottest;


import com.physicaloid.lib.Physicaloid;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
import android.hardware.SensorManager;
import android.widget.TextView;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;


public class MainActivity extends Activity
{
	private final String TAG = MainActivity.class.getSimpleName();
	private SensorManager mSensorManager;
	private SensorHandler hRotate;
	private Physicaloid mSerialDevice;
	private TextView tvTerm;
	private BalanceTask balTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
     // need to set content view before we can interrogate R for handles
        setContentView(R.layout.activity_main);
        
        // Get an instance of the SensorManager
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        
        // get a handle to the TextView used to display rotation data
        TextView atvX = (TextView)findViewById(R.id.ax_axis);
		TextView atvY = (TextView)findViewById(R.id.ay_axis);
		TextView atvZ = (TextView)findViewById(R.id.az_axis);
		TextView gtvX = (TextView)findViewById(R.id.gx_axis);
		TextView gtvY = (TextView)findViewById(R.id.gy_axis);
		TextView gtvZ = (TextView)findViewById(R.id.gz_axis);
		
		// create our sensor handler
		hRotate = new SensorHandler(mSensorManager, atvX, atvY, atvZ, gtvX, gtvY, gtvZ);
		
		// handle to terminal text view
		tvTerm = (TextView)findViewById(R.id.term);
		tvTerm.setMovementMethod(new ScrollingMovementMethod());
    }
    
    public double val;

    private final BalanceTask.CallBack mCallBack = new BalanceTask.CallBack() {
		@Override
		public void update(double val) {
			MainActivity.this.val = val;
		    MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.updateReceivedData();
                }
		    });
		}
	};

    protected void onResume()
    {
    	super.onResume();
    	tvTerm.append("Resume\n");
    	// resume listening to rotation events
    	hRotate.start();
		hRotate.calibrate();
    	
    	// bring the serial device back online
    	mSerialDevice = new Physicaloid(this);

        Log.d(TAG, "Resumed, mSerialDevice=" + mSerialDevice);
        if (mSerialDevice == null) {
            Log.d(TAG, "No serial device.");
            tvTerm.append("No Serial Device Found\n");
        } 
        else {
        	if (mSerialDevice.open()) {
        		
        		mSerialDevice.setBaudrate(38400);
        		
            	Log.d(TAG, "Started serial.");
            	// successfully opened serial connection, resume balance task
            	balTask = new BalanceTask(hRotate, mSerialDevice, mCallBack);
            }
            else {
            	tvTerm.append("Unable to Connect to Serial\n");
            }
        }
    }
    
    protected void onPause()
    {
    	super.onPause();
    	
    	// stop balancing task (must happen first)
    	if (balTask != null) {
    		balTask.stop();
    		balTask = null;
    	}
    	
    	// stop listening for rotation events
    	hRotate.stop();
 
    	// shut down serial device
        if (mSerialDevice != null) {
        	mSerialDevice.close();
            mSerialDevice = null;
        }
    }
    

   
    private void updateReceivedData() {
    	if (balTask != null) {
    		// this is started as a runnabled on the main thread so it is possible that balTask
    		// is null by the time the ui get's to executing here.
        	tvTerm.setText("" + val);
    	}
        //mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
}
