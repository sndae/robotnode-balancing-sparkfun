package com.xmerx.balnode;

import android.os.SystemClock;
import android.util.Log;


public class BalanceTask2 {
	
	private static final String TAG = "BAL";
	
	private static final double[] K = {-14.1421, -11.1145, 104.2777, 12.3453};
	//private static final double Nbar = -14.1421;
	private static final double VoltsToCountsPerSec = 12.0/10667;
	private double dta = 0;
	private static final long MainLoopTimeMs = 20;
	private static final long READ_TIMEOUT_MS = 5;
	
	private static Thread mainThread;
	private static boolean runThread = false;
	private static SensorHandler sensorHandler;
	private static BTComm btComm;
	
	public double mv1 = 0.0, mv2 = 0.0, x = 0.0, v = 0.0;
	
	public interface CallBack {
		public void update(double val);
	}
	
	private CallBack mCallBack;
	
	public BalanceTask2(SensorHandler hs, BTComm btc, CallBack cb) {
		sensorHandler = hs;
		btComm = btc;
		mCallBack = cb;		
		
		mainThread = new Thread() {
			@Override
			public void run()
			{
				int count = 1, val = 1000;
				runThread = true;
				
				long lastTime = SystemClock.uptimeMillis();
				String cmd;
				
				while (runThread) {
					long controlTime = SystemClock.uptimeMillis();
					
					readMotorSpeeds();
			
					Log.d(TAG, "Updating speed");
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
					cmd = "\n:W " + -1*val + " " + val + "\n";
					btComm.Write(cmd);
					
					
					// calc remaining time before next update
					long remTime = SystemClock.uptimeMillis() - MainLoopTimeMs - controlTime;
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
		
		try {
			mainThread.join();
		}
		catch (InterruptedException e) {}
	}
	
	public void readMotorSpeeds() {
		Log.d(TAG, "Performing read");
		
		String res = btComm.WriteThenRead("\n:R\n", READ_TIMEOUT_MS);
		
		if (res != null) { // got a valid response
			// response format is ":R left_speed right_speed\n"
			String[] params = res.split(" ");
			if (params.length == 3) {
				if (params[0] == ":R") {
					try {
						// attempt to parse both speed values
						double v1 = Double.parseDouble(params[1]);
						double v2 = Double.parseDouble(params[2]);
						// update speeds only after both successfully parsed
						mv1 = v1;
						mv2 = v2;
						Log.d(TAG, "readMotorSpeeds: Read success");
					}
					catch (NumberFormatException e) {
						Log.d(TAG, "readMotorSpeeds: Unable to parse speeds");
					}
				}
				else {
					Log.d(TAG, "readMotorSpeeds: Incorrect response prefix");
				}
			}
			else {
				Log.d(TAG, "readMotorSpeeds: Incorrect number of parameters");
			}
		}
		else {
			Log.d(TAG, "readMotorSpeeds: Invalid response");
		}
	}
}
