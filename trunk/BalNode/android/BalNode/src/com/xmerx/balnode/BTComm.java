package com.xmerx.balnode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.SystemClock;

public class BTComm {
	// Persistent bluetooth objects
	private BluetoothSocket btSocket = null;
	private OutputStream outStream = null;
	private InputStream inStream = null;
	// Android Bluetooth SPP UUID
	private static final UUID ANDROID_BT_UUID =
      UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	
	
	// Constructor will find the bluetooth device and open a socket to it.
	// We'll save references to the socket, outputstream, and inputstream.
	public BTComm() {
		BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
		BluetoothDevice myDevice = null;
		
		// User should already have paired with bluetooth device. Iterate through the list and
		// grab the device by name.
		Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equals("linvor")) {
                    myDevice = device;
                    break;
                }
            }
        }
        
        try {
			btSocket = myDevice.createRfcommSocketToServiceRecord(ANDROID_BT_UUID);
			btSocket.connect();
			outStream = btSocket.getOutputStream();
			inStream = btSocket.getInputStream();
		} 
        catch (IOException e) {}
	}
	
	public void Write(String strToWrite) {
		try {
			outStream.write(strToWrite.getBytes());
		}
		catch (IOException e) {}
	}
	
	public String Read(long timeoutMs) {
		String in = "";
		long start = SystemClock.uptimeMillis();
		boolean done = false;
		
		while (!done) {
			// Attempt to read what's in the input buffer. A \n terminates the current input
			try {
				int len = inStream.available();
				for (int i = 0; i < len; i++) {
					char inChar = (char)inStream.read();
					in += inChar;
					if (inChar == '\n') {
						done = true;
						break;
					}
				}
			}
			catch (IOException e) {}
			
			// If we're not done and time hasn't run out, sleep a bit
			if (!done) {
				long now = SystemClock.uptimeMillis();
				if ((now - start) < timeoutMs) {
					// still got time
					try {
						Thread.sleep(1);
					}
					catch (InterruptedException e) {}
				}
				else {
					// time has expired, exit the loop
					done = true;
				}
			}
			
		}
		
		// return null to indicate we didn't receive a full response
		if (!in.endsWith("\n")) {
			in = null;
		}
		return in;
	}
	
	public String WriteThenRead(String strToWrite, long timeoutMs) {
		Write(strToWrite);
		return Read(timeoutMs);
	}
	
	// Call prior to destroying object
	public void stop() {
		try {
			btSocket.close();
		} 
		catch (IOException e) {}
		
		outStream = null;
		inStream = null;
	}
}
