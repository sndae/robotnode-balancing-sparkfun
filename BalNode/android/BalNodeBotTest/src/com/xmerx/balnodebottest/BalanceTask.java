package com.xmerx.balnodebottest;

import com.physicaloid.lib.Physicaloid;

import android.os.SystemClock;
import android.util.Log;


public class BalanceTask {
	
	private static final double[] K = {-14.1421, -11.1145, 104.2777, 12.3453};
	private static final double VoltsToCountsPerSec = 12.0/10667;
	// Balance loop runs at 20Hz (every 50ms)
	private static final long MainLoopTimeMs = 500;
	
	private static Thread balThread;
	private static Thread readThread;
	private static boolean runBalTask = false;
	private static boolean runReadTask = false;
	private static boolean doneRead = true;
	private static boolean isReadTaskReady = false;
	private static SensorHandler sensorHandler;
	private static Physicaloid arduino;
	
	private static int errors = 0;
	
	public double mv1 = 0.0, mv2 = 0.0, x = 0.0, v = 0.0;
	
	public interface CallBack {
		public void update(double val);
	}
	
	private CallBack mCallBack;
	
	public BalanceTask(SensorHandler hs, Physicaloid serialDevice, CallBack cb) {
		sensorHandler = hs;
		arduino = serialDevice;
		mCallBack = cb;
		
		readThread = new Thread() {
			@Override
			public void run()
			{
				doRead();
			}
		};
		readThread.start();
		
		balThread = new Thread() {
			@Override
			public void run()
			{
				task();
			}
		};
		balThread.setDaemon(true);
		balThread.start();
	}
	
	
	
	private void task() {
		runBalTask = true;
		
		long lastTime = SystemClock.uptimeMillis();
		String cmd;
		
		// Wait for read task to complete setup
		Log.d("bal", "Waiting for read Task");
		while(!isReadTaskReady) {
			try {
				Thread.sleep(1);
			}
			catch (InterruptedException e) {}
		}
		
		int count = 0;
		
		// Balance task loop
		while (runBalTask) {
			long controlTime = SystemClock.uptimeMillis();
			
			// Kickoff an update of motor speeds
			Log.d("bal", "Requesting read");
			// Signal read task
			doneRead = false;
			// wait for it to finish
			while (!doneRead && runBalTask) {
				try {
					Thread.sleep(1);
				}
				catch (InterruptedException e) {}
			}
			
			Log.d("bal", "Updating speed");
			
			// Update state variables (pitch angle and rate handled by sensorHandler)
			long now = SystemClock.uptimeMillis();
			long dt = now - lastTime;
			lastTime = now;
			
			// speed needs to be in units of ?
			// convert from
			v = Math.sqrt(mv1*mv1 + mv2*mv2);
			x += v*dt;
			
			// Calculate control
			double Va = K[0]*x + K[1]*v + K[2]*sensorHandler.pitchAngle + K[3]*sensorHandler.pitchRate;
			// Convert into a speed command for the RoboClaw
			int spd = (int)(Va*VoltsToCountsPerSec);
			
			// Send speed command
			cmd = "\n:W" + -1*spd + " " + spd + "\n";
			cmd = "\n:W 1000 -1000\n";
			long s = SystemClock.uptimeMillis();
			arduino.write(cmd.getBytes());
			s = SystemClock.uptimeMillis() - s;
			
			if (count == 25) {
				count = 0;
				mCallBack.update(errors);
				errors = 0;
			}
			
			// Calc remaining time before next update
			long remTime = MainLoopTimeMs + controlTime - SystemClock.uptimeMillis();
			// Sleep if we can
			if (remTime > 1) {
				try {
					Thread.sleep(remTime);
				}
				catch (InterruptedException e) {}	
			}
			
			count++;
		}
	}
	
	private void doRead() {
		byte[] rbuf = new byte[64];
		int numBytes = 0;
		int test = 2;
		runReadTask = true;
		
		// signal balance task it can begin
		Log.d("ser", "Serial is Ready");
		isReadTaskReady = true;
		
		while (runReadTask) {
			// Synchronize with balance task which triggers
			// a read by setting doneRead to false
			while (doneRead && runReadTask) {
				try {
					Thread.sleep(1);
				}
				catch (InterruptedException e) {}
			}
			
			// clear out anything in read buffer
			arduino.read(rbuf);
			
			Log.d("ser", "Starting read");
			// Clear out anything in the read buffer
			numBytes = arduino.read(rbuf);
			// Send the read speed command
			arduino.write(":R\n".getBytes());
			// Wait a couple ms for response
			try {
				Thread.sleep(test);
			} 
			catch (InterruptedException e){}
			
			String readStr = "";
			boolean isPacketDone = false;
			
			// Read bytes into a string buffer until either we reach the packet 
			// terminator or timeout(run out of attempts)
			for (int attempts = 0; attempts < 3; attempts++) {
				numBytes = arduino.read(rbuf);
				
				// Process bytes received
				for (int i = 0; i < numBytes; i++) {
					if (rbuf[i] == (byte)'\n') {
						// reached end of a packet, exit and process
						isPacketDone = true;
					}
					else if (rbuf[i] == (byte)':') {
						// (re)start packet
						readStr = "" + (char)rbuf[i];
					}
					else {
						// append this char to our string buffer
						readStr += (char)rbuf[i];
					}
				}
				
				
				if (!isPacketDone) { // Still more bytes to receive, let's give them some time to arrive
					try {
						Thread.sleep(1);
					}
					catch (InterruptedException e) {}
				}
				else { // We can immediately exit loop
					break;
				}
			}
			
			Log.d("ser", "Read done got: " + readStr);
			
			if (isPacketDone) {				
				// Got a full packet, now decode
				if (readStr.startsWith(":R") && readStr.length() > 5) {
					// Heading present and at least the minimum length
					// Expect ":R speed1 speed2\r"
					String[] speeds = readStr.substring(3).split(" ");
					if (speeds.length == 2) {
						// We got two space separated values as expected
						try {
							double m1 = Double.parseDouble(speeds[0]);
							double m2 = Double.parseDouble(speeds[1]);
							mv1 = m1;
							mv2 = m2;
						}
						catch (NumberFormatException e){}
					}
				}
			}
			else {
				Log.d("ser", "read timeout");
				errors++;
			}
			
			doneRead = true;
		}
	}
	
	public void stop() {
		// Shut down the threads
		runBalTask = false;
		runReadTask = false;
		
		try {
			readThread.join();
			balThread.join();
		}
		catch (InterruptedException e) {}
	}
}
