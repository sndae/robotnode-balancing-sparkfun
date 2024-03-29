package com.xmerx.balnodebottest;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.widget.TextView;

public class SensorHandler implements SensorEventListener
{
	private SensorManager mSensorManager;
	private Sensor mAccelSensor;
	private Sensor mGyroSensor;
	private TextView atvX;
	private TextView atvY;
	private TextView atvZ;
	private TextView gtvX;
	private TextView gtvY;
	private TextView gtvZ;
	
	private static final double GRAVITY = 9.81;
	private static final double HALF_PI = 1.570796326794897;
	
	public double pitchAngle = 0.0, pitchRate = 0.0;
	private double angleSum = 0, angleOffset = 0;
	private double pitchSum = 0, pitchOffset = 0;
	private int pitchCount = 0, angleCount = 0;
	
	public boolean isRunning = false;
	private boolean calibrate = false;
	
	public SensorHandler(SensorManager sm, TextView atvX, TextView atvY, TextView atvZ,
			TextView gtvX, TextView gtvY, TextView gtvZ)
	{
		// save the Sensor Manager
		mSensorManager = sm;
		// find the gravity sensor
		mAccelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
		// find the gyro sensor
		mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
		
        // save the TextViews
        this.atvX = atvX; this.atvY = atvY; this.atvZ = atvZ;
        this.gtvX = gtvX; this.gtvY = gtvY; this.gtvZ = gtvZ;
	}
	
    public void start() {
        // enable our sensor when the activity is resumed
    	// we'll adjust the delay when this is running on the robot
        mSensorManager.registerListener(this, mAccelSensor,	SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, mGyroSensor,	SensorManager.SENSOR_DELAY_UI);
        isRunning = true;
    }

    public void stop() {
    	isRunning = false;
        // make sure to turn our sensor off when the activity is paused
        mSensorManager.unregisterListener(this);
    }
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// get our orientation angle in x, y, and z
		float x = event.values[0];
		float y = event.values[1];
		float z = event.values[2];
		
		// update views with sensor values
		if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
			atvX.setText(Float.toString((float) (x - angleOffset)));
			atvY.setText(Float.toString(y));
			atvZ.setText(Float.toString(z));
			
			x -= angleOffset;
			
			// calculate the pitch angle based on the z axis
			if (Math.abs(x) > GRAVITY) {
				// just in case the sensor reports a value greater than gravity clip the angle 
				pitchAngle = (x > 0) ? HALF_PI: -1*HALF_PI;
			}
			else {
				pitchAngle = Math.asin(x/GRAVITY);
			}
			
			if (calibrate) {
				angleSum += x;
				angleCount++;
				
				if (angleCount > 100) {
					doneCalibrate();
				}
			}
		}
		else {
			gtvX.setText(Float.toString((float) (x - pitchOffset)));
			gtvY.setText(Float.toString(y));
			gtvZ.setText(Float.toString(z));
			
			pitchRate = x - pitchOffset;
			
			if (calibrate) {
				pitchSum += x;
				pitchCount++;
				
				if (pitchCount > 100) {
					doneCalibrate();
				}
			}
			
		}
	}

	public void calibrate() {
		calibrate = true;
	}
	
	public void doneCalibrate() {
		calibrate = false;
		angleOffset = angleSum / angleCount;
		pitchOffset = pitchSum / pitchCount;
	}
}
