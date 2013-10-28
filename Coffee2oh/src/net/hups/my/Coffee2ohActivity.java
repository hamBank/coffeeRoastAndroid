package net.hups.my;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.apache.commons.validator.routines.DoubleValidator;

import android.app.ActionBar;
import android.app.Activity;
//import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ZoomControls;
//import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class Coffee2ohActivity extends Activity implements SeekBar.OnSeekBarChangeListener {

	private static final String TAG = "Coffeev2.0";
	private TempService myService;

	long timeNow, timeStart;// = System.currentTimeMillis();
	private TextView dispRunTime;
	private TextView dispTemp, dispAlarmTemp;
	private TextView dispRoc, dispWRoC, dispMaxT;
	private ZoomControls alarmSet;
	//private ListView dispLogData;
	private SeekBar tempBar;
	private double maxTempValue = 0;
	private double runTemp = 24;
	private int alarmTemp = 223;
	private boolean currentSimulateMode = false;
	private int currentSimulateSpeed = 1, acceptTempVariance = 5;
	private double currentTempVarianceAllowed, currentTempVarianceAllowedNeg;
	
	//private String[] logValues = new String[] { "First Log message Attempt" } ;
	
	
	private static final double allowedTempVariance = 15;
	private static final double allowedTempVarianceNeg = 10;
	private static final int stringLengthToValidate = 7;
	
	// Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    
    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;
	
	//Our graph
	private GraphicalView mChartView;
	private XYMultipleSeriesDataset mDataset = new XYMultipleSeriesDataset();
	private XYMultipleSeriesRenderer mRenderer = new XYMultipleSeriesRenderer();
		
	 // Name of the connected device
    private String mConnectedDeviceName = null;
    // Array adapter for the conversation thread
    private ArrayAdapter<String> mConversationArrayAdapter;
    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothChatService mChatService = null;
    
    private ITempSerivce tempUpdService;
    
	private XYSeries liveGraphXYSeries = new XYSeries("live");
	
	//TODO: Assign variable number of temps
	double[] tempValues = new double[5];

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		  // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
		dispRunTime = (TextView) findViewById(R.id.dispRunTime);
		dispTemp = (TextView) findViewById(R.id.dispTemp);
		dispRoc = (TextView) findViewById(R.id.dispRoc);
		dispWRoC = (TextView) findViewById(R.id.dispTargRoc);
		dispMaxT = (TextView) findViewById(R.id.dispMaxTemp);
		alarmSet = (ZoomControls) findViewById(R.id.alarmSet);
		dispAlarmTemp = (TextView) findViewById(R.id.AlarmTemp);

//		dispLogData = (ListView) findViewById(R.id.listLogData);		
//		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
//				android.R.layout.simple_list_item_1, logValues);
//		setListAdapter(adapter);
		
		tempBar = (SeekBar) findViewById(R.id.tempBar);
		tempBar.setOnSeekBarChangeListener(this);
		tempBar.setMax(100);
		tempBar.setProgress(50);
		
		Log.v(TAG, "onCreate - add data");
		
		mDataset.addSeries(liveGraphXYSeries);
		Log.v(TAG, "onCreate - set render");
		
		mRenderer.setAxisTitleTextSize(14);
		mRenderer.setChartTitleTextSize(16);
		mRenderer.setLabelsTextSize(13);
		mRenderer.setLegendTextSize(13);
		mRenderer.setPointSize(5f);
		mRenderer.setMargins(new int[] { 10, 20, 8, 10 });
		//mRenderer.setXAxisMax(600);
		mRenderer.setYAxisMax(240);
		
		int[] colors = new int[] { Color.GREEN };
		PointStyle[] styles = new PointStyle[] { PointStyle.POINT};
		int length = colors.length;
		for (int i = 0; i < length; i++) {
			Log.v(TAG, "onCreate - add renderes");
			
			XYSeriesRenderer r = new XYSeriesRenderer();
			r.setColor(colors[i]);
			r.setPointStyle(styles[i]);
			mRenderer.addSeriesRenderer(r);
		}
		
		Button start = (Button) findViewById(R.id.ButtonStart);
		Button stop = (Button) findViewById(R.id.ButtonStop);
		Button reset = (Button) findViewById(R.id.buttonReset);
		Button acceptTemps = (Button) findViewById(R.id.buttonAcceptVariance);
		
		
		start.setOnClickListener(startClickHandler);
		stop.setOnClickListener(stopClickHandler);
		reset.setOnClickListener(resetClickHandler);
		acceptTemps.setOnClickListener(acceptTempsClickHandler);
		alarmSet.setOnZoomInClickListener(tempUpClickHandler);
		alarmSet.setOnZoomOutClickListener(tempDownClickHandler);
		
		Intent a = new Intent(this, TempService.class);
		startService(a);
		// Binding ..this block can also start service if not started already
		Intent bindIntent = new Intent(this, TempService.class);
		bindService(bindIntent, serviceConncetion, Context.BIND_AUTO_CREATE);
		// Register Broadcast Receiver
		IntentFilter filter = new IntentFilter(TempService.MYOWNACTIONFILTER);
		registerReceiver(myReceiver, filter);

		//Set our 'default' temp  - entire array is set so the average is what we expect. 
		for (int i = 0; i < 5; i++) tempValues[i] = runTemp;
		
		//dispTemp.setText("Temp: " + String.valueOf(tempValues[0]));

		Log.v(TAG, "onCreate - main");

		  if (mChartView == null) {
			  Log.v(TAG, "onCreate - before chart layout");
			  LinearLayout layout = (LinearLayout) findViewById(R.id.chart);
			  Log.v(TAG, "onCreate - before chart Factory");
			  	    
			mChartView = ChartFactory.getLineChartView(this, mDataset, mRenderer);
			Log.v(TAG, "onCreate - before chart addview");
			  layout.addView(mChartView, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));		    
		  } else {
		    mChartView.repaint();
		  }
		  
		  final EditText edittext = (EditText) findViewById(R.id.simulateSpeed);
		  edittext.setOnKeyListener(new OnKeyListener() {
		      public boolean onKey(View v, int keyCode, KeyEvent event) {
		          // If the event is a key-down event on the "enter" button
		          if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
		              (keyCode == KeyEvent.KEYCODE_ENTER)) {
		        	  // Perform action on key press
		        	  currentSimulateSpeed = Integer.parseInt(edittext.getText().toString());
		        	  tempUpdService.simluateMode(currentSimulateMode, currentSimulateSpeed);
		            return true;
		          }
		          return false;
		      }
		  });
		  final CheckBox checkbox = (CheckBox) findViewById(R.id.cSimulate);
		  if (checkbox != null) {
		  checkbox.setOnClickListener(new OnClickListener() {
		      public void onClick(View v) {
		          // Perform action on clicks, depending on whether it's now checked
		          if (((CheckBox) v).isChecked()) {
		        	  currentSimulateMode = true;
		          } else {
		        	  currentSimulateMode = false;
		          }
		          
	        	  tempUpdService.simluateMode(currentSimulateMode, currentSimulateSpeed);
		      }
		  });
		  }
		  
		  OnClickListener radio_listener = new OnClickListener() {
			    public void onClick(View v) {
			        // Perform action on clicks
			        RadioButton rb = (RadioButton) v;
			        Toast.makeText(Coffee2ohActivity.this, rb.getText(), Toast.LENGTH_SHORT).show();
			        switch (v.getId()) {
			        case R.id.runAuto:
			        	tempUpdService.setRunMode(TempService.RunModeAuto);
			        	break;
			        case R.id.runOff:
			        	tempUpdService.setRunMode(TempService.RunModeStop);
			        	break;
			        case R.id.runHold:
			        	tempUpdService.setRunMode(TempService.RunModeHold);
			        	break;
			        case R.id.runManual:
			        	tempUpdService.setRunMode(TempService.RunModeManual);
			        	break;
			        				        
			        
			        }
			    }
			};
		
		final RadioButton rbAuto = (RadioButton) findViewById(R.id.runAuto);
		final RadioButton rbStop = (RadioButton) findViewById(R.id.runOff);
		final RadioButton rbHold = (RadioButton) findViewById(R.id.runHold);
		final RadioButton rbManual = (RadioButton) findViewById(R.id.runManual);
		rbAuto.setOnClickListener(radio_listener);
		rbManual.setOnClickListener(radio_listener);	  
		rbStop.setOnClickListener(radio_listener);	  
		if (rbHold != null) {
			rbHold.setOnClickListener(radio_listener);		
		}
		} //End OnCreate
	
	 	@Override
	    public boolean onCreateOptionsMenu(Menu menu) {
	        MenuInflater inflater = getMenuInflater();
	        inflater.inflate(R.menu.option_menu, menu);
	        //return super.onCreateOptionsMenu(menu);
	        return true;
	    }
		
	 	 @Override
	     public boolean onOptionsItemSelected(MenuItem item) {
	         Intent serverIntent = null;
	         switch (item.getItemId()) {
	         case R.id.secure_connect_scan:
	             // Launch the DeviceListActivity to see devices and do scan
	             serverIntent = new Intent(this, DeviceListActivity.class);
	             startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
	             return true;
	         case R.id.insecure_connect_scan:
	             // Launch the DeviceListActivity to see devices and do scan
	             serverIntent = new Intent(this, DeviceListActivity.class);
	             startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
	             return true;
	         case R.id.discoverable:
	             // Ensure this device is discoverable by others
	             ensureDiscoverable();
	             return true;
	         }
	         return false;
	     }

	 	     
	 
	private OnClickListener startClickHandler = new OnClickListener() {

		@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
			// Now i can access any public methods defined inside myService
			// using myService instance
			Log.v(TAG, "onClick - before service start");
			timeStart = System.currentTimeMillis();
			myService.startProcessing();
		}
	};

	private OnClickListener stopClickHandler = new OnClickListener() {

		@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
			// call stop
			myService.stopProcessing();
		}
	};
	
	private OnClickListener tempUpClickHandler = new OnClickListener() {

		@Override
		public void onClick(View v) {
			alarmTemp++;	
			tempUpdService.setMaxValue(alarmTemp, true);
			dispAlarmTemp.setText(String.format("%3d", alarmTemp));			
		}
	};
	private OnClickListener tempDownClickHandler = new OnClickListener() {

		@Override
		public void onClick(View v) {
			alarmTemp--;	
			tempUpdService.setMaxValue(alarmTemp, false);
			dispAlarmTemp.setText(String.format("%3d", alarmTemp));			
		}
	};
	
	private OnClickListener resetClickHandler = new OnClickListener() {

		@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
			// call stop
			myService.stopProcessing();
			tempUpdService.resetRunParams();
			tempBar.setProgress(50);
			//for (int i = 0; i < 5; i++) tempValues[i] = -50.00;
			maxTempValue = 0;
			liveGraphXYSeries.clear();
		
		}
	};

	private OnClickListener acceptTempsClickHandler = new OnClickListener() {

		@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
			// call stop
			//for (int i = 0; i < 5; i++) tempValues[i] = -50.00;
			maxTempValue = 0;
			acceptTempVariance = 5;
					
		}
	};
	private ServiceConnection serviceConncetion = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			// TODO Auto-generated method stub
			myService = ((TempService.MyBinder) service).getService();
			Log.i(TAG, "Service connection established");	
			tempUpdService = (ITempSerivce) service;
			Log.i("INFO", "Service bound ");
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			// TODO Auto-generated method stub
		}
	};

	@Override
	public void onDestroy() {
		super.onDestroy();
		// Unregister the Broadcast receiver and unbind service
		unregisterReceiver(myReceiver);
		unbindService(serviceConncetion);
	}

	private BroadcastReceiver myReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			// Get Bundles
			Bundle extras = intent.getExtras();
			// Unwrap bundle object			
			runTemp = extras.getDouble(TempService.TEMPKEY); //Hopfully get the temp.
			double runRoc = extras.getDouble(TempService.ROCKEY); //Hopfully get the RoC.
			long currentTime= extras.getLong(TempService.TIMEKEY); //Hopfully get the time.
			int currentPower = extras.getInt(TempService.POWERKEY); //Hopfully get the Power.
			double wantedRoC = extras.getDouble(TempService.WROCKEY); // Desired RoC
			
			if (currentPower != tempBar.getProgress()) {
				tempBar.setProgress(currentPower);
				sendMessage("A" + String.format("%04d", currentPower));
			}
			
			if (runTemp > maxTempValue) {
				maxTempValue = runTemp;
				dispMaxT.setText("Max Temp: " + String.format("%5.2f", runTemp));
			}
			liveGraphXYSeries.add(currentTime / 10000, runTemp);
			mChartView.repaint();
			
					dispRunTime.setText("Time: " + 
					String.format("%d:%02d", 
						    TimeUnit.MILLISECONDS.toMinutes(currentTime),
						    TimeUnit.MILLISECONDS.toSeconds(currentTime) - 
						    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(currentTime)
						)));
			dispTemp.setText("Temp: " + String.format("%5.2f", runTemp));
			dispRoc.setText("Roc: " + String.format("%5.2f", runRoc) + "/min");
			dispWRoC.setText("Target RoC: " + String.format("%5.2f", wantedRoC));						

		}
	};

	@Override
	protected void onStart() {
		super.onStart();
		// Bind to LocalService
		Log.v(TAG, "onStart");
		Toast.makeText(this, "onStart", Toast.LENGTH_SHORT).show();
		 // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
        	Log.v(TAG, "BT adapter not enabled - try now");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, setup the chat session
        } else {
        	Log.v(TAG, "setup chat session");
            if (mChatService == null) setupChat();
        }
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

    @Override
    public synchronized void onResume() {
        super.onResume();
        Log.e(TAG, "+ ON RESUME +");

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
              // Start the Bluetooth chat services
            	Log.e(TAG, "starting BT service");
              mChatService.start();
            }
        }
    }
    
		
	@Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
    	Log.v(TAG, "seek callback" + progress + " " + fromTouch);
    }
    
    @Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		// TODO Auto-generated method stub
    	Log.v(TAG, "seek value updates " + String.format("%3d", seekBar.getProgress()));
    	myService.outputPower = seekBar.getProgress();	
	}
	
	private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        //mConversationView = (ListView) findViewById(R.id.in);
        //mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
//        mOutEditText = (EditText) findViewById(R.id.edit_text_out);
//        mOutEditText.setOnEditorActionListener(mWriteListener);

        // Initialize the send button with a listener that for click events
//        mSendButton = (Button) findViewById(R.id.button_send);
//        mSendButton.setOnClickListener(new OnClickListener() {
//            public void onClick(View v) {
//                // Send a message using content of the edit text widget
//                TextView view = (TextView) findViewById(R.id.edit_text_out);
//                String message = view.getText().toString();
//                sendMessage(message);
//            }
//        });

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(this, mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }
	
	 private void ensureDiscoverable() {
	        Log.d(TAG, "ensure discoverable");
	        if (mBluetoothAdapter.getScanMode() !=
	            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
	            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
	            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
	            startActivity(discoverableIntent);
	        }
	    }
	 
	 /**
	     * Sends a message.
	     * @param message  A string of text to send.
	     */
	    private void sendMessage(String message) {
	    	// Check that we're actually connected before trying anything
	        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
	        	Log.v(TAG, "BT status " + String.format("%d", mChatService.getState()));
	            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show();
	            return;
	        }

	        // Check that there's actually something to send
	        if (message.length() > 0) {
	            // Get the message bytes and tell the BluetoothChatService to write
	            byte[] send = message.getBytes();
	            mChatService.write(send);

	            // Reset out string buffer to zero and clear the edit text field
	            mOutStringBuffer.setLength(0);
	            //mOutEditText.setText(mOutStringBuffer);
	        }
	    }
	    
	    private final void setStatus(CharSequence subTitle) {
	        final ActionBar actionBar = getActionBar();
	        actionBar.setSubtitle(subTitle);
	    }

	    // The Handler that gets information back from the BluetoothChatService
	    private final Handler mHandler = new Handler() {
	    	//Pattern myPattern;
	    	DoubleValidator tempValid = new DoubleValidator();
	    	double processedTemp;
	    	int failedMatches = 0;
	    	String leftOvers = new String();	    	
	    	Pattern p = Pattern.compile("^.*t(\\d{3}\\.\\d*):.*$", Pattern.MULTILINE | Pattern.DOTALL | Pattern.MULTILINE);
	    	Pattern pr = Pattern.compile("(^.*:).*", Pattern.MULTILINE);
	    		    	
	        @Override
	        public void handleMessage(Message msg) {
	            switch (msg.what) {
	            case MESSAGE_STATE_CHANGE:
	                Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
	                switch (msg.arg1) {
	                case BluetoothChatService.STATE_CONNECTED:
	                    setStatus("Connected to " + mConnectedDeviceName);
	                    mConversationArrayAdapter.clear();
	                    break;
	                case BluetoothChatService.STATE_CONNECTING:
	                    setStatus("Connecting" );
	                    break;
	                case BluetoothChatService.STATE_LISTEN:
	                case BluetoothChatService.STATE_NONE:
	                    setStatus("Not Connected");
	                    break;
	                }
	                break;
	            case MESSAGE_WRITE:
	                byte[] writeBuf = (byte[]) msg.obj;
	                // construct a string from the buffer
	                String writeMessage = new String(writeBuf);
	                mConversationArrayAdapter.add("Me:  " + writeMessage);
	                break;
	            case MESSAGE_READ:
	                byte[] readBuf = (byte[]) msg.obj;
	                // construct a string from the valid bytes in the buffer
	                String readMessage = new String(readBuf, 0, msg.arg1);
	                //Call our processing engine
	                //TODO: only process when we have a CR.
	                readMessage = leftOvers + readMessage;
	                	                
					Matcher m = p.matcher(readMessage);
	
					if (m.find()) {
	
						// Try and pull out the string
	
						if (tempValid.isValid(m.group(1))) {
							// Is valid double
							processedTemp = tempValid.validate(m.group(1));
							Log.v(TAG, "value:"
									+ String.format("%f", processedTemp) + ":"
									+ m.group());
	
							if (acceptTempVariance > 0) {
								
								currentTempVarianceAllowed = 160;
								currentTempVarianceAllowedNeg = 160;
								acceptTempVariance--;
								
							} else {
								currentTempVarianceAllowed = allowedTempVariance;
								currentTempVarianceAllowedNeg = allowedTempVarianceNeg;
							}
							
							if ((tempValid.isInRange(processedTemp, runTemp
									- currentTempVarianceAllowedNeg, runTemp
									+ currentTempVarianceAllowed)) || 
									runTemp < 60) {
								tempUpdService.setCurrentTemprature(processedTemp);
								// Clear the string(s)
								leftOvers = "";
								readMessage = "";
							} else {
								Log.v(TAG, 
										"Value out of allowed range: "
												+ String.format("%f", processedTemp)
												+ " Should be +- "
												+ String.format("%f", allowedTempVariance)
												+ " of "
												+ String.format("%f", runTemp) );
							}
	
						} else {
							Log.v(TAG, "Value Not Num: " + readMessage);
						}
	
					} else {
	
						failedMatches++;
						leftOvers = readMessage;
						if (failedMatches > 10) {
							Log.v(TAG, "Failed 10 Matches now: "
											+ leftOvers
											+ " length "
											+ String.format("%d", leftOvers.length()));
							failedMatches = 0;
						}
					}
	
					if (leftOvers.length() > stringLengthToValidate) {
						Log.v(TAG, "Discarding : "
										+ leftOvers
										+ " length "
										+ String.format("%d", leftOvers.length())
										+ " left behind "
										+ leftOvers.substring(leftOvers.length()
												- stringLengthToValidate));
						leftOvers = leftOvers.substring(leftOvers.length()
								- stringLengthToValidate);
					} else {
						Matcher mr = pr.matcher(leftOvers);
						if (mr.find()) { // We have a end of message event - lets
											// prune
							leftOvers = leftOvers.substring(mr.end());
						}
					}
					// } else {
					// //not processing no CR
					// dispMessage.setText("StrV:" + leftOvers);
					// }

	                mConversationArrayAdapter.add(mConnectedDeviceName+":  " + readMessage);
	                break;
	            case MESSAGE_DEVICE_NAME:
	                // save the connected device's name
	                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
	                Toast.makeText(getApplicationContext(), "Connected to "
	                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
	                break;
	            case MESSAGE_TOAST:
	                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
	                               Toast.LENGTH_SHORT).show();
	                break;
	            }
	        }
	    };
	    
	    public void onActivityResult(int requestCode, int resultCode, Intent data) {
	        Log.d(TAG, "onActivityResult " + resultCode);
	        switch (requestCode) {
	        case REQUEST_CONNECT_DEVICE_SECURE:
	            // When DeviceListActivity returns with a device to connect
	            if (resultCode == Activity.RESULT_OK) {
	                connectDevice(data, true);
	            }
	            break;
	        case REQUEST_CONNECT_DEVICE_INSECURE:
	            // When DeviceListActivity returns with a device to connect
	            if (resultCode == Activity.RESULT_OK) {
	                connectDevice(data, false);
	            }
	            break;
	        case REQUEST_ENABLE_BT:
	            // When the request to enable Bluetooth returns
	            if (resultCode == Activity.RESULT_OK) {
	                // Bluetooth is now enabled, so set up a chat session
	                setupChat();
	            } else {
	                // User did not enable Bluetooth or an error occurred
	                Log.d(TAG, "BT not enabled");
	                Toast.makeText(this, "BT Not enabled - Leaving", Toast.LENGTH_SHORT).show();
	                finish();
	            }
	        }
	    }
	    
	    private void connectDevice(Intent data, boolean secure) {
	        // Get the device MAC address
	        String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
	        // Get the BluetoothDevice object
	        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
	        // Attempt to connect to the device
	        mChatService.connect(device, secure);
	    }


}
