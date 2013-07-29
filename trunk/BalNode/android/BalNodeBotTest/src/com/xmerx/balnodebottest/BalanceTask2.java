package com.xmerx.balnodebottest;

import android.os.SystemClock;
import android.util.Log;


public class BalanceTask2 {
	
	private static final double[] K = {-14.1421, -11.1145, 104.2777, 12.3453};
	private static final double Nbar = -14.1421;
	private static final double VoltsToCountsPerSec = 12.0/10667;
	private double dta = 0;
	private static final long MainLoopTimeMs = 20;
	
	private static Thread mainThread;
	private static Thread serialThread;
	private static boolean runThread = false;
	private static boolean runSerial = false;
	private static boolean doneRead = true;
	private static boolean isSerialReady = false;
	private static SensorHandler sensorHandler;
	private static FTDriver arduino;
	
	public double mv1 = 0.0, mv2 = 0.0, x = 0.0, v = 0.0;
	
	public interface CallBack {
		public void update(double val);
	}
	
	private CallBack mCallBack;
	
	public BalanceTask2(SensorHandler hs, FTDriver serialDevice, CallBack cb) {
		sensorHandler = hs;
		arduino = serialDevice;
		mCallBack = cb;
		
		serialThread = new Thread() {
			@Override
			public void run()
			{
				byte[] rbuf = new byte[64];
				int numBytes = 0;
				runSerial = true;
				
				// clear out anything in read buffer
				arduino.read(rbuf);
				// signal balance task it can begin
				Log.d("ser", "Serial is Ready");
				isSerialReady = true;
				
				while (runSerial) {
					// synchronize with balance task
					while (doneRead && runSerial) {
						try {
							Thread.sleep(1);
						}
						catch (InterruptedException e) {}
					}
					Log.d("ser", "Starting read");
					
					String readStr = "";
					boolean isPacketDone = false;
					
					// read bytes into a string buffer until either we reach the packet 
					// terminator or timeout(run out of attempts)
					//while(!isPacketDone && runSerial) {
					for (int attempts = 0; attempts < 3; attempts ++) {
						numBytes = arduino.read(rbuf);
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
					}
					
					Log.d("ser", "Read done");
					
					if (isPacketDone) {
						// Got a full packet, now decode
						if (readStr.startsWith(":R") && readStr.length() > 5) {
							String[] speeds = readStr.substring(2).split(" ");
							if (speeds.length == 2) {
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
					}
					
					doneRead = true;
				}
			}
		};
		serialThread.start();
		
		mainThread = new Thread() {
			@Override
			public void run()
			{
				int count = 1;
				int val = 1000;
				runThread = true;
				
				long lastTime = SystemClock.uptimeMillis();
				String cmd;
				Log.d("bal", "Waiting for serial");
				while(!isSerialReady) {
					try {
						Thread.sleep(1); // give serial thread time to clear out buffer
					}
					catch (InterruptedException e) {}
				}
				
				while (runThread) {
					long controlTime = SystemClock.uptimeMillis();
					Log.d("bal", "Requesting read");
					// kickoff an update of motor speeds
					doneRead = false;
					try {
						Thread.sleep(1);
					}
					catch(InterruptedException e) {}
					
					cmd = "\n:R\n";
					arduino.write(cmd.getBytes());
					Log.d("bal", "Waiting for read completion");
					// wait for it to finish
					while (!doneRead && runThread) {
						try {
							Thread.sleep(1);
						}
						catch (InterruptedException e) {}
					}
					//doneRead = false;
					Log.d("bal", "Updating speed");
					// update state variables (pitch angle and rate handled by sensorHandler)
					long now = SystemClock.uptimeMillis();
					long dt = now - lastTime;
					lastTime = now;
					v = Math.sqrt(mv1*mv1 + mv2*mv2);
					x += v*dt;
					dta += dt;
					
					// calculate control
					double Va = K[0]*x + K[1]*v + K[2]*sensorHandler.pitchAngle + K[3]*sensorHandler.pitchRate;
					int spd = (int)(Va*VoltsToCountsPerSec);
					
					// send commands
					if ((count++ % 25) == 0) {
						val *= -1;
						mCallBack.update(dta/25);
						dta = 0;
					}
					
					// this part will take at least 10ms
					cmd = "\n:W" + -1*val + " " + val + "\n";
					arduino.write(cmd.getBytes());
					
					
					// calc remaining time before next update
					long remTime = MainLoopTimeMs + controlTime - SystemClock.uptimeMillis();
					// sleep if we can
					if (remTime > 1) {
						try {
							Thread.sleep(remTime);
						}
						catch (InterruptedException e) {
							// whoops
						}	
					}
				}
			}
		};
		mainThread.setDaemon(true);
		mainThread.start();
	}
	
	public void stop() {
		// shut down the threads
		runThread = false;
		runSerial = false;
		
		try {
			serialThread.join();
			mainThread.join();
		}
		catch (InterruptedException e) {}
	}
}
