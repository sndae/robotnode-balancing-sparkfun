package com.xmerx.balnodetest;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
import android.hardware.SensorManager;
import android.widget.TextView;

import android.content.Context;
import android.hardware.usb.UsbManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends Activity
{
	private final String TAG = MainActivity.class.getSimpleName();
	private SensorManager mSensorManager;
	private HandlerSensors hRotate;
	private UsbManager mUsbManager;
	private UsbSerialDriver mSerialDevice;
	private TextView tvTerm;

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
		hRotate = new HandlerSensors(mSensorManager, atvX, atvY, atvZ, gtvX, gtvY, gtvZ);
		// and let's get started!
		hRotate.start();
		
		mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		// handle to terminal text view
		tvTerm = (TextView)findViewById(R.id.term);
		tvTerm.setMovementMethod(new ScrollingMovementMethod());
    }
    
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private SerialInputOutputManager mSerialIoManager;

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

        @Override
        public void onRunError(Exception e) {
            Log.d(TAG, "Runner stopped.");
        }

        @Override
        public void onNewData(final byte[] data) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.updateReceivedData(data);
                }
            });
        }
    };

    protected void onResume()
    {
    	super.onResume();
    	// resume listening to rotation events
    	hRotate.start();
    	
    	mSerialDevice = UsbSerialProber.acquire(mUsbManager);

        Log.d(TAG, "Resumed, mSerialDevice=" + mSerialDevice);
        if (mSerialDevice == null) {
            Log.d(TAG, "No serial device.");
        } else {
            try {
                mSerialDevice.open();
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                try {
                    mSerialDevice.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                mSerialDevice = null;
                return;
            }
        }
        onDeviceStateChange();
    }
    
    protected void onPause()
    {
    	super.onPause();
    	// stop listening for rotation events
    	hRotate.stop();
    	
        stopIoManager();
        if (mSerialDevice != null) {
            try {
                mSerialDevice.close();
            } catch (IOException e) {
                // Ignore.
            }
            mSerialDevice = null;
        }
    }
    
    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (mSerialDevice != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(mSerialDevice, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private void updateReceivedData(byte[] data) {
        String message = "";
		try {
			message = new String(data, "US-ASCII");
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
		}
        tvTerm.append(message);
        //mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
}