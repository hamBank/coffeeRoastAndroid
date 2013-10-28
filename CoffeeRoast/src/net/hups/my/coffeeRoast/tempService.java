package net.hups.my.coffeeRoast;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class tempService extends Service {
	private static final String TAG = "tempService";

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {

		Log.d(TAG, "onCreate");

	}

	@Override
	public void onDestroy() {

		Log.d(TAG, "onDestroy");
	}

	@Override
	public void onStart(Intent intent, int startid) {

		Log.d(TAG, "onStart");
	}
}
