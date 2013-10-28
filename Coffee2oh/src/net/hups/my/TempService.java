package net.hups.my;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
//import android.os.PowerManager;
//import android.content.Context;

public class TempService extends Service implements Runnable {
	private static final String TAG = "Coffeev2.0 temp";
	private static final double sim_base_temp = 26.0;

	private final IBinder binder = new MyBinder();
	private Handler handler = new Handler();
	private int counter = 0;
	double[] tempratureArr = new double[5];
	double[] rocArr = new double[5];
	public double[] tempROCs = new double[5];
	public int[] trigTemp = new int[5];
	long timeStart, timeNow, timeCurrent, lastTempratureUpdate = 0; // = System.currentTimeMillis() - timeStart;
	public double currentTemp = 20, tempChangeRate = 0, wantedRoC = 0, InputcurrentTemp=20;
	private boolean endOfRun = false;	
	private boolean simulateMode = false;
	public int outputPower = 50, speedupRate = 10;
	private int currentRunMode = 2;
	//private int numberOfRates = 5; 
	//TODO: Add dynamic rate amounts & config input 
	
	
	public static final String COUNTERKEY = "countervalue";
	public static final String TEMPKEY = "tempraturevalue";
	public static final String ROCKEY = "rocvalue";
	public static final String TIMEKEY = "timevalue";
	public static final String POWERKEY = "outputpower";	
	public static final String WROCKEY = "wantroc";
	
	public static final int RunModeStop = 1;
	public static final int RunModeAuto = 2;
	public static final int RunModeHold = 3;
	public static final int RunModeManual = 4;
	
	public static final long TempratureUpdateMs = 2000;
	
	public static final String MYOWNACTIONFILTER = "devs.own.intent.filter";			

	/** Command to the service to display a message */
	static final int MSG_SAY_HELLO = 1;	
	 
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return binder;
		// return the binder(myService reference) when service
		// connected using Service Connection
	}

	// Instance of the Service is now in MyBinder
	public class MyBinder extends Binder implements ITempSerivce {
		
		TempService getService() {

			return TempService.this;
		}

		@Override
		public void setCurrentTemprature(double inTemp) {
			currentTemp = inTemp;			
		}
		@Override
		public void resetRunParams() {
			outputPower = 50;		
			endOfRun = false;
			resetValues();
			
		}
		@Override
		public void simluateMode(boolean simMode, int speedUp) {
			simulateMode = simMode;
			speedupRate = speedUp;			
		}
		@Override		
		public void setPower(int powerLevel) {
			
		}
		@Override
		public void setRunMode(int runMode){
			currentRunMode = runMode;			
		}
				
		@Override
		public  void setMaxValue(int myinputTemp, boolean goesUp) {
			trigTemp[4] = myinputTemp;
			if (goesUp) {
				trigTemp[1]++;
				trigTemp[2]++;
				trigTemp[3]++;
			} else {
				trigTemp[1]--;
				trigTemp[2]--;
				trigTemp[3]--;
			}
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		// Service is just created
		// but does nothing now
		resetValues();
		
	}

	private void resetValues() {
		tempROCs[0] = 10;
		trigTemp[0] = 38;
		tempROCs[1] = 19;
		trigTemp[1] = 180;
		tempROCs[2] = 9;
		trigTemp[2] = 196;
		tempROCs[3] = 5.2;
		trigTemp[3] = 207;
		tempROCs[4] = 3.2;
		trigTemp[4] = 223;		
		
		currentTemp = 20;
		
		for (int i=0; i < tempratureArr.length; i++) {
			averageTemps(currentTemp, tempratureArr);
		}
		
	}

	public void startProcessing() {
		// I call the Runnable using Handler instance
		Log.v(TAG, "startCounter inside service");
		timeStart = System.currentTimeMillis();		
		handler.postDelayed(this, 1 * 1000); // after 1 sec it will call the
												// run() block
	}

	public void stopProcessing() {

		handler.removeCallbacks(this); // Make sure we stop handler call backs
										// when stop called
	}

	@Override
	public void run() {		
		processRunData();
	}
	
	public void setBTTemp(double inTemp) {
		InputcurrentTemp = inTemp;
	}

	// Broadcast the counter value so others know
	private void processRunData() {				
				
		counter = counter + 1; // increment counter
		
		//get temp
		currentTemp = getCurrentTemprature(currentTemp);
		tempChangeRate = rateOfChange();
		
		//Calc desired RoC
		if (!endOfRun) {
			switch (currentRunMode) {
			case RunModeAuto:
				wantedRoC = desiredROC(currentTemp);
				break;
			case RunModeHold:
				wantedRoC = 0;
				break;
			case RunModeStop:
				wantedRoC = -100;
				break;
			case RunModeManual:
				wantedRoC = 0;
				break;
			}
		}
		
		timeNow = System.currentTimeMillis();
		timeCurrent = timeNow - timeStart;
		
		if (simulateMode) {
			timeCurrent = (timeNow - timeStart) * speedupRate;
		}
		
		if (((currentRunMode != RunModeStop) && (currentRunMode != RunModeManual))) {
			if ((lastTempratureUpdate + TempratureUpdateMs) <= timeNow) {
				lastTempratureUpdate = timeNow;
				updatePowerSetting(tempChangeRate, wantedRoC);
			}
		}
		
		Intent intent = new Intent();
		// Bundle the values with Intent
		intent.putExtra(TEMPKEY, currentTemp);
		intent.putExtra(ROCKEY, tempChangeRate);
		intent.putExtra(TIMEKEY, timeCurrent);
		intent.putExtra(POWERKEY, outputPower);
		intent.putExtra(WROCKEY, wantedRoC);
		
		intent.setAction(MYOWNACTIONFILTER); // Define intent-filter
		sendBroadcast(intent); // finally broadcast
		handler.postDelayed(this, 1 * 1000); // Repeat the block for every 1 sec
											// and keep broadcasting until service destroyed
	}
	
	double getCurrentTemprature (double inputTemp) {
		
		double tempFromSource;
		
		if (simulateMode) {
			tempFromSource = DeltaT(inputTemp, outputPower, sim_base_temp)  * speedupRate;
			Log.v(TAG, "Ts" + String.format("%5.2f", tempFromSource));
			inputTemp = tempFromSource + inputTemp;
		}
		
		currentTemp = averageTemps(inputTemp, tempratureArr);	
		
		return inputTemp;
	}

		
	double rateOfChange () {
		double theRate;
		theRate = tempratureArr[tempratureArr.length - 1] - tempratureArr[0];
		theRate = (theRate / tempratureArr.length) * 60;
		
		theRate = averageTemps(theRate, rocArr);
		
		if (simulateMode) {
			theRate = theRate / speedupRate;
		}
		return theRate;
	}

	double desiredROC (double curTemp) {
		Log.v(TAG, "checking wanted ROC");
		endOfRun = true;
		double roc = -100;
		Log.v(TAG, "ROC CT" + String.format("%5.2f", curTemp));
				
		for (int i = 0; i < trigTemp.length; i++) {
			
			Log.v(TAG, "ROC " + String.format("%1d", i)
					+ " " + String.format("%3d", trigTemp[i]));
			
			if (curTemp < trigTemp[i]) {
				roc = tempROCs[i];
				endOfRun = false;
				break;
			}
		}
		return roc;
	}
	
	private void updatePowerSetting(double currentRoc, double desiredRoC) {
		double rocError = desiredRoC - currentRoc;
		
	    if (endOfRun) {
	    	outputPower = 0;
	    	return;
	    }
	    
	    if (currentRunMode == RunModeStop) {
	    	outputPower = 0;
	    	return;
	    }
	   
	    if (currentRunMode == RunModeManual) {
	    	return;
	    }
	    
	     if (currentRoc < -2) {
	    	 outputPower = outputPower + 2;
	     } else if (currentRoc < 0) {
			outputPower++;
	     } else if (rocError > (desiredRoC / 3)) {
	    	 outputPower = outputPower + 2;
	     }	else if (rocError > (desiredRoC / 10)) {
			outputPower++;
	     } else if (rocError < -(desiredRoC)) {
			outputPower = outputPower - 2;
	      }	else if (rocError < -(desiredRoC / 10)) {
			outputPower--;
	      }	else if (rocError < -25 ) {
				outputPower = outputPower - 5;
		  }

	    if (outputPower > 100) {
	    	outputPower = 100;
	    } else if (outputPower < 0) {
	    	outputPower = 0;
	    }
	}
	
	double averageTemps(double input, double[] tArray) {
		double tempCalc = 0;
		
		for (int i = 0; i < tArray.length - 1; i++) {
			tArray[i] = tArray[i + 1];
			tempCalc += tArray[i];		
		}
		tArray[tArray.length - 1] = input;
		tempCalc += input;
		tempCalc = tempCalc / tArray.length;		
		
		return tempCalc;
		
	}
	
	double DeltaT(double T, double P, double Tbase) {
	    double r=0.0175;
	    double k=0.0050;
	    return r*P - k*(T-Tbase);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

	}

}
