package com.blogpost.hiro99ma.flfmt;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.blogpost.hiro99ma.flfmt.util.SystemUiHider;
import com.blogpost.hiro99ma.nfc.NfcFactory;

/**
 * An example full-screen activity that shows and hides the system UI (i.e. status bar and navigation/system bar) with user interaction.
 * 
 * @see SystemUiHider
 */
public class UnndefActivity extends Activity {
	/**
	 * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after user interaction before hiding the system UI.
	 */
	private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

	/**
	 * The flags to pass to {@link SystemUiHider#getInstance}.
	 */
	private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

	/**
	 * The instance of the {@link SystemUiHider} for this activity.
	 */
	private SystemUiHider mSystemUiHider;

	//フォーマット可能なタイミングかどうか
	private boolean mFormatOK = false;

	//ダイアログ
	private AlertDialog mSuccessDlg;
	private AlertDialog mFailDlg;

	//時差でダイアログを消したい
	private Handler mCloseHanlder = new Handler();
	private Runnable mCloseRunnable = new Runnable() {
		@Override
		public void run() {
			mSuccessDlg.dismiss();
		}
	};
	private static final int DIALOG_CLOSE_DELAY_MILLIS = 500;

 
	private final String TAG = "UnndefActivity";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "onCreate");
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_unndef);

		final View controlsView = findViewById(R.id.unndef_content_controls);
		final View contentView = findViewById(R.id.unndef_content);
		final Button ndefButton = (Button)findViewById(R.id.ndef_button);

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

				if (visible) {
					// Schedule a hide().
					Log.d(TAG, "visible");
					delayedHide(AUTO_HIDE_DELAY_MILLIS);
					
					mFormatOK = true;
				} else {
					Log.d(TAG, "invisible");
					mFormatOK = false;
				}
			}
		});

		
		// Set up the user interaction to manually show or hide the system UI.
		contentView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				mSystemUiHider.toggle();
			}
		});

		// Upon interacting with UI controls, delay any scheduled hide()
		// operations to prevent the jarring behavior of controls going away
		// while interacting with the UI.
		ndefButton.setOnTouchListener(mDelayHideTouchListener);
		ndefButton.setOnClickListener(mOnClickNdef);
		
		mSuccessDlg = new AlertDialog.Builder(this)
				.setTitle(R.string.format_success_title)
				.setMessage(R.string.format_success).create();
		mFailDlg = new AlertDialog.Builder(this)
				.setTitle(R.string.format_fail_title)
				.setMessage(R.string.format_fail)
				.setPositiveButton(android.R.string.ok, null).create();
	}


	@Override
	public void onResume() {
		super.onResume();
		
		boolean ret = NfcFactory.nfcResume(this);
		if(!ret) {
			Log.e(TAG, "fail : resume");
			Toast.makeText(this, R.string.cannot_nfc, Toast.LENGTH_LONG).show();
			finish();
		}
	}

	
	@Override
	public void onPause() {
		NfcFactory.nfcPause(this);
		super.onPause();	
	}

	
	@Override
	public void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		
		if (!mFormatOK) {
			//画面にボタンが表示されている間だけ可能
			Toast.makeText(this, R.string.explain_format, Toast.LENGTH_SHORT).show();
			return;
		}
		
		boolean ret = NfcFactory.nfcActionRawFormat(intent);

		if (ret) {
			mSuccessDlg.show();
			mCloseHanlder.postDelayed(mCloseRunnable, DIALOG_CLOSE_DELAY_MILLIS);

			//延長
			delayedHide(AUTO_HIDE_DELAY_MILLIS);
		} else {
			mFailDlg.show();
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
			delayedHide(AUTO_HIDE_DELAY_MILLIS);
			view.performClick();
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
	
	
	//NDEFボタン
	View.OnClickListener mOnClickNdef = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			Intent intent = new Intent(UnndefActivity.this, NdefActivity.class);
			startActivity(intent);
		}
	};
}
