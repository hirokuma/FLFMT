package com.blogpost.hiro99ma.nfc;

import java.io.IOException;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.nfc.tech.NfcF;
import android.os.RemoteException;
import android.util.Log;

public class NfcFactory {

	private final static String TAG = "NfcFactory";
    
    private final static IntentFilter[] mFilters = new IntentFilter[] {
		new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
	};
    private final static String[][] mTechLists = new String[][] {
		new String[] { NfcF.class.getName() },
    };

	public static boolean NfcResume(Activity activity, Intent intent) {
		//NFC
		NfcManager mng = (NfcManager)activity.getSystemService(Context.NFC_SERVICE);
		if (mng == null) {
			Log.e(TAG, "no NfcManager");
			return false;
		}
		NfcAdapter adapter = mng.getDefaultAdapter();
		if (adapter == null) {
			Log.e(TAG, "no NfcService");
			return false;
		}
		
		//newがnullを返すことはない
		PendingIntent pendingIntent = PendingIntent.getActivity(
						activity,
						0,		//request code
						intent,
						0);		//flagなし
		
		adapter.enableForegroundDispatch(activity, pendingIntent, mFilters, mTechLists);
		
		return true;
	}
	
	public static void NfcPause(Activity activity) {
		NfcManager mng = (NfcManager)activity.getSystemService(Context.NFC_SERVICE);
		if (mng == null) {
			Log.e(TAG, "no NfcManager");
			return;
		}
		NfcAdapter adapter = mng.getDefaultAdapter();
		if (adapter == null) {
			Log.e(TAG, "no NfcService");
			return;
		}

		if (activity.isFinishing()) {
			adapter.disableForegroundDispatch(activity);
		}
	}

	
	/*
	 * IntentからTagを取得する
	 */
	private static Tag getTag(Intent intent) {
		//チェック
		String action = intent.getAction();
		if (action == null) {
			Log.e(TAG, "fail : null action");
			return null;
		}
		boolean match = false;
		for (IntentFilter filter : mFilters) {
			if (filter.matchAction(action)) {
				match = true;
				break;
			}
		}
		if (!match) {
			Log.e(TAG, "fail : no match intent-filter");
			return null;
		}

		 return (Tag)intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
	}
	
	
	public static boolean NfcAction(Intent intent, boolean proc) {
		boolean ret = false;

		//Tag取得
		Tag tag = getTag(intent);
		if (tag == null) {
			return false;
		}

		/***********************************************
		 * 以降に、自分がやりたい処理を書く
		 ***********************************************/
		
		//format
		FelicaLite felica = null;
		try {
			felica = FelicaLite.get(tag);
			if (felica == null) {
				Log.e(TAG, "fail : no felica lite");
				return false;
			}

			felica.connect();
			ret = felica.polling(FelicaLite.SC_FELICALITE);
			if (!ret) {
				Log.d(TAG, "polling fail");
			}
			
			if(ret) {
				if(proc) {
					ret = felica.ndefFormat();
				} else {
					ret = felica.unndefFormat();
				}
			}

		} catch (IOException e) {
			Log.e(TAG, "fail : format");
			ret = false;
		} catch (RemoteException e) {
			Log.e(TAG, "fail : felica lite");
			ret = false;
		}
		if (felica != null) {
			try {
				felica.close();
			} catch (IOException e) {
				Log.e(TAG, "fail : close");
				ret = false;
			}
		}
		
		return ret;
	}
	
}
