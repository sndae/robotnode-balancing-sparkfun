package com.xmerx.balnode;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
import android.hardware.SensorManager;
import android.widget.TextView;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import com.xmerx.balnode.R;


public class MainActivity extends Activity
{
	private final String TAG = MainActivity.class.getSimpleName();
	private SensorManager mSensorManager;
	private SensorHandler hRotate;
	private BTComm btComm;
	private TextView tvTerm;
	private BalanceTask2 balTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
     // need to set content view before we can interrogate R for handles
        setContentView(R.layout.activity_main);
    }
    
    public double val;

    private final BalanceTask2.CallBack mCallBack = new BalanceTask2.CallBack() {
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

	protected void onResume() {
		super.onResume();
		
        // Get an instance of the SensorManager
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        
        // get a handle to the TextView used to display rotation data
        TextView atvX = (TextView)findViewById(R.id.ax_axis);
		TextView atvY = (TextView)findViewById(R.id.ay_axis);
		TextView atvZ = (TextView)findViewById(R.id.az_axis);
		TextView gtvX = (TextView)findViewById(R.id.gx_axis);
		TextView gtvY = (TextView)findViewById(R.id.gy_axis);
		TextView gtvZ = (TextView)findViewById(R.id.gz_axis);
		
		// handle to terminal text view
		tvTerm = (TextView)findViewById(R.id.term);
		tvTerm.setMovementMethod(new ScrollingMovementMethod());
		
		// create our sensor handler
		hRotate = new SensorHandler(mSensorManager, atvX, atvY, atvZ, gtvX, gtvY, gtvZ);
		// and let's get started!
		hRotate.start();
		
		// create btcomm object
		btComm = new BTComm();
		
		// wait a second then end calibration
		try {
			Thread.sleep(1000);
		} 
		catch (InterruptedException e) {}
		
		hRotate.calibrate();
		// create balance task object
		balTask = new BalanceTask2(hRotate, btComm, mCallBack);
		
		Log.d(TAG, "onResume done");
	}
    
    protected void onPause()
    {
    	super.onPause();
    	
    	// stop balancing task (must happen first)
    	balTask.stop();
    	balTask = null;
    	// stop listening for rotation events
    	hRotate.stop();
    	hRotate = null;
    	// stop the serial device
    	btComm.stop();
    	btComm = null;
    }
    

   
    private void updateReceivedData() {
    	if (balTask != null) {
    		// this is started as a runnable on the main thread so it is possible that balTask
    		// is null by the time the UI get's to executing here.
    		String message = balTask.mv1 + " " + balTask.mv2 + " " + val;
        	tvTerm.setText(message);
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
