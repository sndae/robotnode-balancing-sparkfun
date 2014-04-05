package com.xmerx.balnodebottest;

import android.os.SystemClock;
import com.xmerx.balnodebottest.MainActivity;
import java.io.UnsupportedEncodingException;
import java.io.IOException;


public class BalanceTask {
	
	private static final double[] K = {-14.1421, -11.1145, 104.2777, 12.3453};
	private static final double Nbar = -14.1421;
	private static final double VoltsToCountsPerSec = 12.0/10667;
	
	private static final long MainLoopTimeMs = 30;
	
	private static Thread mainThread;
	private static boolean runThread = false;
	private static SensorHandler sensorHandler;
	private static FTDriver arduino;
	
	public double mv1 = 0.0, mv2 = 0.0, x = 0.0, v = 0.0;
	
	public interface CallBack {
		public void update(double val);
	}
	
	private CallBack mCallBack;
	
	public BalanceTask(SensorHandler hs, FTDriver serialDevice, CallBack cb) {
		sensorHandler = hs;
		arduino = serialDevice;
		mCallBack = cb;
		
		mainThread = new Thread() {
			@Override
			public void run()
			{
				int count = 1;
				int val = 1000;
				runThread = true;
				double dta = 0;
				byte[] data = new byte[64];
				long lastTime = SystemClock.uptimeMillis();
				
				// clear out the read buffer
				arduino.read(data);
				pause();
				
				while (runThread) {
					long controlTime = SystemClock.uptimeMillis();
					
					dta += updateMotorSpeeds(); // at least 10ms
					//dta += SystemClock.uptimeMillis() - controlTime;
					
					// update state variables (pitch angle and rate handled by sensorHandler)
					long now = SystemClock.uptimeMillis();
					long dt = now - lastTime;
					lastTime = now;
					v = Math.sqrt(mv1*mv1 + mv2*mv2);
					x += v*dt;
					
					// calculate control
					double Va = K[0]*x + K[1]*v + K[2]*sensorHandler.pitchAngle + K[3]*sensorHandler.pitchRate;
					int spd = (int)(Va*VoltsToCountsPerSec);
					
					// send command
					String cmd;
					if ((count++ % 5) == 0) {
						val *= -1;
						mCallBack.update(dta/5);
						dta = 0;
					}
					
					// this part will take at least 10ms
					cmd = "\n:W" + -1*val + " " + val + "\n";
					arduino.write(cmd.getBytes());
					pause();
					
					// by now, at least 20ms has past, loop time is 30ms
					
					// calc remaining time before next update
					//long remTime = MainLoopTimeMs + controlTime - SystemClock.uptimeMillis();
					// sleep if we can
					//if (remTime > 1) {
					//	try {
					//		Thread.sleep(remTime);
					//	}
					//	catch (InterruptedException e) {
					//		// whoops
					//	}	
					//}
				}
			}
		};
		mainThread.setDaemon(true);
		mainThread.start();
	}
	
	public void stop() {
		// shut down the thread
		runThread = false;
		try {
			mainThread.join();
		}
		catch (InterruptedException e) {}
	}
	
	
	// Used to wait inbetween write/read commands to usb
	private void pause() {
		try {
  			Thread.sleep(10);
  		}
  		catch (InterruptedException e) {}
	}
	
	private double dta = 0;
	
	private double updateMotorSpeeds()
	{
		byte[] data = new byte[64];
		String dataStr = "";
		int numBytes = 0;
		boolean isValid = false;
		
		double dt = 0;
		long m = SystemClock.uptimeMillis();
		
		// kick off a read by the arduino
		String cmd = "\n:R\n";
		dt = SystemClock.uptimeMillis() - m;
  		arduino.write(cmd.getBytes());
  		
  		pause();
  		
		int count = 0;
		
		while (count < 5) {
			numBytes += arduino.read(data);
			
			try {
				dataStr += new String(data, 0, numBytes, "US-ASCII");
			}
			catch (UnsupportedEncodingException e) {}
			
			if ((dataStr.startsWith(":R")) && (dataStr.endsWith("\n"))) {
				isValid = true;
				break;
			}
			
			count++;
		}
		pause();
		/*
		if (isValid) {
			String[] speeds = dataStr.substring(2, numBytes - 2).split(" ");
			if (speeds.length == 2) {
				// update speed
				mv1 = Double.parseDouble(speeds[0]);
				mv2 = Double.parseDouble(speeds[1]);
			}
		}
		*/
		
		return dt;
	}
}