package com.xmerx.balnodebottest;

import com.physicaloid.lib.Physicaloid;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.SystemClock;
import android.util.Log;


public class BalanceTask {
	// Controller Parameters
	private static final double[] K = {-14.1421, -11.1145, 104.2777, 12.3453};
	
	// 12V results in 200RPM. At 3200 counts per sec, comes out to 10667 counts per sec at 12V
	private static final double VoltsToCountsPerSec = 10667/12;
	// 6 inches = 0.1524m. Wheel circumference (distance traveled per rev is PI*d = 0.1524*PI).
	// Encoder does 3200 counts per wheel revolution
	private static final double MetersPerCount = (0.1524 * Math.PI) / 3200;
	
	
	// Robot Parameters
	private static final double WheelBase = 0.15;
	
	
	// Balance loop runs at 20Hz (every 50ms)
	private static final long MainLoopTimeMs = 30;
	// Read seems to require about 10ms between sending the read command and when data is ready
	private static final long ReadWaitMs = 10;
	
	private static Thread balThread;
	private static Thread readThread;
	private static boolean runBalTask = false;
	private static boolean runReadTask = false;
	private static boolean doneRead = true;
	private static boolean isReadTaskReady = false;
	private static SensorHandler sensorHandler;
	private static Physicaloid arduino;
	
	private static int errors = 0;
	
	public double mvL = 0.0, mvR = 0.0, x = 0.0;
	
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
		
		// 2 second start delay for user to hold robot steady
		arduino.write("\n:W 0 0 99 \n".getBytes());
		try {
			Thread.sleep(2000);
		}
		catch (InterruptedException e) {}
		
		ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
		toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200); 
		
		// Wait for read task to complete setup
		Log.d("bal", "Waiting for read Task");
		while(!isReadTaskReady) {
			try {
				Thread.sleep(1);
			}
			catch (InterruptedException e) {}
		}
		
		int count = 0;
		
		// PID parameters
		double err = 0.0;
		double errD = 0.0;
		double errS = 0.0;
		double P = -35000.0;
		double I = 0.0;
		double D = 500.0;
		
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
			double dt = (now - lastTime)/1000.0;
			lastTime = now;
			
			// Convert speed from counts/sec to units of m/s
			mvL *= MetersPerCount;    // left speed
			mvR *= -1*MetersPerCount; // right speed (driven with opposite sign)
			
			// Calculate incremental dx and dy and incremental vx and vy
			double alpha = (1/2)*(mvR + mvL);
			double beta = (alpha*WheelBase)/(mvR - mvL);
			double gamma = (mvR - mvL)*(dt/WheelBase);
			double dx = beta*Math.sin(gamma);
			double dy = -1*beta*Math.cos(gamma);
			double vx = alpha*Math.cos(gamma);
		    double vy = alpha*Math.sin(gamma);
			
		    // Calculate x and v, the resultant of the incremental movement
			double sign = dx > 0 ? 1 : -1;
			//double stateXdot = (mvL + mvR)/2;
			//stateX = stateXdot*dt;
		    
			// Calculate control
			//double Va = K[0]*stateX + K[1]*stateXdot + K[2]*sensorHandler.pitchAngle + K[3]*sensorHandler.pitchRate;
			
			
			// Convert into a speed command for the RoboClaw
			//int spd = (int)(Va*VoltsToCountsPerSec);
			
			// TODO: Create a PID class (under controller package)
			int spd = (int)(P*sensorHandler.pitchAngle + I*errS + D*sensorHandler.pitchRate*dt);
			err = 0.75*sensorHandler.pitchAngle + 0.25*sensorHandler.pitchRate*dt;
			
			int spdR = spd;
			int spdL = -1*spd;
			errS += sensorHandler.pitchAngle;
			
			
			// Send speed command
			int check = spdR - spdL + 99;
			cmd = "\n:W" + spdR + " " + spdL + " " + check + "\n";
			
			long s = SystemClock.uptimeMillis();
			arduino.write(cmd.getBytes());
			s = SystemClock.uptimeMillis() - s;
			
			if (count == 33) {
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
				Thread.sleep(ReadWaitMs);
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
						Thread.sleep(2);
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
							mvL = m1;
							mvR = m2;
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
