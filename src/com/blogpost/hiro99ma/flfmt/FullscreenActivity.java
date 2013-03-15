package com.blogpost.hiro99ma.flfmt;

import java.io.IOException;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.nfc.tech.NfcF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.blogpost.hiro99ma.flfmt.util.SystemUiHider;
import com.blogpost.hiro99ma.nfc.FelicaLite;

/**
 * An example full-screen activity that shows and hides the system UI (i.e. status bar and navigation/system bar) with user interaction.
 *
 * @see SystemUiHider
 */
public class FullscreenActivity extends Activity {
	/**
	 * Whether or not the system UI should be auto-hidden after {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
	 */
	private static final boolean AUTO_HIDE = true;

	/**
	 * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after user interaction before hiding the system UI.
	 */
	private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

	/**
	 * If set, will toggle the system UI visibility upon interaction. Otherwise, will show the system UI visibility upon interaction.
	 */
	private static final boolean TOGGLE_ON_CLICK = true;

	/**
	 * The flags to pass to {@link SystemUiHider#getInstance}.
	 */
	private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

	/**
	 * The instance of the {@link SystemUiHider} for this activity.
	 */
	private SystemUiHider mSystemUiHider;


    private NfcAdapter mAdapter;
    private PendingIntent mPendingIntent;
    private IntentFilter[] mFilters;
    private String[][] mTechLists;

    private final String TAG = "FullActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_fullscreen);

		final View controlsView = findViewById(R.id.fullscreen_content_controls);
		final View contentView = findViewById(R.id.fullscreen_content);

		// Set up an instance of SystemUiHider to control the system UI for
		// this activity.
		mSystemUiHider = SystemUiHider.getInstance(this, contentView, HIDER_FLAGS);
		mSystemUiHider.setup();
		mSystemUiHider.setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener() {
			// Cached values.
			int mControlsHeight;
			int mShortAnimTime;

			@Override
			@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
			public void onVisibilityChange(boolean visible) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
					// If the ViewPropertyAnimator API is available
					// (Honeycomb MR2 and later), use it to animate the
					// in-layout UI controls at the bottom of the
					// screen.
					if (mControlsHeight == 0) {
						mControlsHeight = controlsView.getHeight();
					}
					if (mShortAnimTime == 0) {
						mShortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);
					}
					controlsView.animate().translationY(visible ? 0 : mControlsHeight).setDuration(mShortAnimTime);
				} else {
					// If the ViewPropertyAnimator APIs aren't
					// available, simply show or hide the in-layout UI
					// controls.
					controlsView.setVisibility(visible ? View.VISIBLE : View.GONE);
				}

				if (visible && AUTO_HIDE) {
					// Schedule a hide().
					delayedHide(AUTO_HIDE_DELAY_MILLIS);
				}
			}
		});

		// Set up the user interaction to manually show or hide the system UI.
		contentView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (TOGGLE_ON_CLICK) {
					mSystemUiHider.toggle();
				} else {
					mSystemUiHider.show();
				}
			}
		});

		// Upon interacting with UI controls, delay any scheduled hide()
		// operations to prevent the jarring behavior of controls going away
		// while interacting with the UI.
		findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);

		//NFC
		NfcManager mng = (NfcManager)this.getSystemService(Context.NFC_SERVICE);
		if (mng == null) {
			Log.e(TAG, "no NfcManager");
			return;
		}
		mAdapter = mng.getDefaultAdapter();
		if (mAdapter == null) {
			Log.e(TAG, "no NfcService");
			return;
		}
		//newがnullを返すことはない
		mPendingIntent = PendingIntent.getActivity(
						this,
						0,		//request code
						new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
						0);		//flagなし
		mFilters = new IntentFilter[] {
						new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
		};

		mTechLists = new String[][] {
						new String[] { NfcF.class.getName() },
		};
	}

	@Override
	public void onResume() {
		super.onResume();
		mAdapter.enableForegroundDispatch(this, mPendingIntent, mFilters, mTechLists);
	}

	@Override
	public void onPause() {
		if (this.isFinishing()) {
			mAdapter.disableForegroundDispatch(this);
		}
		super.onPause();
	}

	@Override
	public void onNewIntent(Intent intent) {
		boolean ret = false;

		super.onNewIntent(intent);

		String action = intent.getAction();
		if (!action.equals(NfcAdapter.ACTION_TECH_DISCOVERED)) {
			Log.d(TAG, "fail : no tech discovered");
			return;
		}

		Tag tag = (Tag)intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		if (tag == null) {
			Log.e(TAG, "fail : no tag");
			return;
		}

		//format
		try {
			FelicaLite felica = FelicaLite.get(tag);
			if (felica == null) {
				Log.e(TAG, "fail : no felica lite");
				return;
			}

			felica.connect();
			ret = felica.polling(FelicaLite.SC_FELICALITE);
			if (!ret) {
				Log.d(TAG, "polling fail");
			}
			ret = felica.ndefFormat();
			felica.close();

		} catch (IOException e) {
			Log.e(TAG, "fail : format");
			ret = false;
		} catch (RemoteException e) {
			Log.e(TAG, "fail : felica lite");
			ret = false;
		}

/*
		if (FelicaLite.isConnected()) {
			//既に誰かが使ってるけど、捨ててしまえ
			Log.d(TAG, "disconnect...");
			try {
				FelicaLite.close();
			} catch (IOException e) {
				Log.e(TAG, "fail : disconnect");
				return;
			}
		}

		//format
		try {
			NfcF nfcf = FelicaLite.connect(tag);
			if (nfcf != null) {
				ret = true;
			} else {
				Log.e(TAG, "cannot format");
			}

			if (ret) {
				ret = FelicaLite.ndefFormat();
			}

			FelicaLite.close();

		} catch (IOException e) {
			Log.e(TAG, "fail : format");
			ret = false;
		}
*/

		showMessage_(ret);
	}

	private void showMessage_(boolean ret) {
		if (ret) {
			Toast.makeText(this, "OK", Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(this, "fail...", Toast.LENGTH_LONG).show();
		}
	}


	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);

		// Trigger the initial hide() shortly after the activity has been
		// created, to briefly hint to the user that UI controls
		// are available.
		delayedHide(100);
	}

	/**
	 * Touch listener to use for in-layout UI controls to delay hiding the system UI. This is to prevent the jarring behavior of controls going away while interacting with activity UI.
	 */
	View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
		@Override
		public boolean onTouch(View view, MotionEvent motionEvent) {
			if (AUTO_HIDE) {
				delayedHide(AUTO_HIDE_DELAY_MILLIS);
			}
			return false;
		}
	};

	Handler mHideHandler = new Handler();
	Runnable mHideRunnable = new Runnable() {
		@Override
		public void run() {
			mSystemUiHider.hide();
		}
	};

	/**
	 * Schedules a call to hide() in [delay] milliseconds, canceling any previously scheduled calls.
	 */
	private void delayedHide(int delayMillis) {
		mHideHandler.removeCallbacks(mHideRunnable);
		mHideHandler.postDelayed(mHideRunnable, delayMillis);
	}
}
