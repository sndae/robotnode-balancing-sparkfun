package com.xmerx.balnode;

import android.os.SystemClock;
import android.util.Log;


public class BalanceTask2 {
	
	private static final String TAG = "BAL";
	
	//private static final double[] K = {-14.1421, -11.1145, 104.2777, 12.3453};
	private static final double[] K = {-0.999999999999996,  -3.065762008794536,  78.158684420442711,   8.484667293604973};
	//private static final double Nbar = -14.1421;
	
	// 12V, 200RPM, 3200CPR
	private static final double VOLTS_TO_COUNTS_PER_SEC = 10667/12;
	// experimentally determined (actually, a bit higher, but this is good)
	private static final int MAX_QPPS = 12000;
	// 6 inches = 0.1524m, circumference per rev and CPR to get m/count
	private static final double METER_PER_COUNT = 0.00014961843;
	
	private double dta = 0;
	private static final long MainLoopTimeMs = 50;
	private static final long READ_TIMEOUT_MS = 40;
	
	private static Thread mainThread;
	private static boolean runThread = false;
	private static SensorHandler sensorHandler;
	private static BTComm btComm;
	
	public double mv1 = 0.0, mv2 = 0.0, x = 0.0, v = 0.0;
	
	private int fails = 0;
	
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
				double theta = 0;
				
				while (runThread) {
					long controlTime = SystemClock.uptimeMillis();
					
					readMotorSpeeds();
			
					//Log.d(TAG, "Updating speed");
					// update state variables (pitch angle and rate handled by sensorHandler)
					long now = SystemClock.uptimeMillis();
					long dt = now - lastTime;
					lastTime = now;
					double vL = mv1*METER_PER_COUNT;
					double vR = mv2*METER_PER_COUNT;
					v = Math.sqrt(vL*vL + vR*vR);
					x += v*dt;
					dta += dt;
					
					// calculate control
					//double Va = K[0]*x + K[1]*v + K[2]*sensorHandler.pitchAngle + K[3]*sensorHandler.pitchRate;
					//int spd = (int)(-Va*VOLTS_TO_COUNTS_PER_SEC);
					theta += sensorHandler.pitchAngle;
					int spd = (int)(-40000.0*sensorHandler.pitchAngle + 0.0*sensorHandler.pitchRate - 500.0*theta);
					
					// send commands
					if (spd > MAX_QPPS) {
						spd = MAX_QPPS;
					}
					if (spd < -MAX_QPPS) {
						spd = -MAX_QPPS;
					}
					
					// this part will take at least 10ms
					//cmd = "\n:W " + spd + " " + -1*spd + "\n";
					//btComm.Write(cmd);
					
					
					// calc remaining time before next update
					long remTime = SystemClock.uptimeMillis() - controlTime;
					
					remTime = remTime < MainLoopTimeMs ? MainLoopTimeMs - remTime : 0;
					// sleep if we can
					if (remTime > 0) {
						try {
							Thread.sleep(remTime);
						}
						catch (InterruptedException e) {
							// whoops
						}	
					}
					
					count++;
					dta += SystemClock.uptimeMillis() - controlTime;
					if (count == 25){
						mCallBack.update(dta/25);
						dta = 0;
						count = 0;
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
		
		// zero motor speeds
		btComm.Write(":W 0 0\n");
	}
	
	public void readMotorSpeeds() {
		Log.d(TAG, "Performing read");
		
		String res = btComm.WriteThenRead(":R\n", READ_TIMEOUT_MS);
		
		if (res != null) { // got a valid response
			// response format is ":R left_speed right_speed\n"
			String[] params = res.split(" ");
			if (params.length == 3) {
				if (params[0].equals(":R")) {
					try {
						// attempt to parse both speed values
						double v1 = Double.parseDouble(params[1]);
						double v2 = Double.parseDouble(params[2]);
						// update speeds only after both successfully parsed
						mv1 = -1*v1;
						mv2 = v2;
						Log.d(TAG, "readMotorSpeeds: Read success");
					}
					catch (NumberFormatException e) {
						fails++;
						Log.d(TAG, "readMotorSpeeds: Unable to parse speeds: " + res + " " + fails);
					}
				}
				else {
					fails++;
					Log.d(TAG, "readMotorSpeeds: Incorrect response prefix: " + res + " " + fails);
				}
			}
			else {
				fails++;
				Log.d(TAG, "readMotorSpeeds: Incorrect number of parameters: " + res + " " + fails);
			}
		}
		else {
			fails++;
			Log.d(TAG, "readMotorSpeeds: Invalid response: " + fails);
		}
	}
}
