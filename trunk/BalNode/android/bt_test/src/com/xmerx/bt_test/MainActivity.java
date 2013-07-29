package com.xmerx.bt_test;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import android.os.Bundle;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.view.Menu;

public class MainActivity extends Activity {

	private BluetoothAdapter btAdapter = null;
	private BluetoothSocket btSocket = null;
	private OutputStream outStream = null;
   
	// Well known SPP UUID
	private static final UUID MY_UUID =
      UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	  
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		btAdapter = BluetoothAdapter.getDefaultAdapter();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public void onResume() {
		BluetoothDevice myDevice = null;
		
		Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        if(pairedDevices.size() > 0)
        {
            for(BluetoothDevice device : pairedDevices)
            {
                if(device.getName().equals("linvor")) //Note, you will need to change this to match the name of your device
                {
                    myDevice = device;
                    break;
                }
            }
        }
        
        try {
			btSocket = myDevice.createRfcommSocketToServiceRecord(MY_UUID);
			btSocket.connect();
			outStream = btSocket.getOutputStream();
		} 
        catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		while (true) {
			try {
				outStream.write("tick\n".getBytes());
			} 
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			try {
				Thread.sleep(999);
			}
			catch (InterruptedException e) {}
		}
	}

}
