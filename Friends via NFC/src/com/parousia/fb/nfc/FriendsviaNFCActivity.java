package com.parousia.fb.nfc;

import java.io.IOException;
import java.net.MalformedURLException;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.SharedPreferences;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.widget.Toast;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.nfc.NfcAdapter.OnNdefPushCompleteCallback;

import com.facebook.android.DialogError;
import com.facebook.android.Facebook;
import com.facebook.android.Facebook.DialogListener;
import com.facebook.android.FacebookError;
import com.facebook.android.Util;
import com.parousia.fb.nfc.common.ApplicationConstants;

public class FriendsviaNFCActivity extends Activity implements CreateNdefMessageCallback,
OnNdefPushCompleteCallback{

	Facebook facebook = new Facebook(ApplicationConstants.APP_ID);
	private SharedPreferences mPrefs;

	NfcAdapter mNfcAdapter;
	PendingIntent mNfcPendingIntent;
	IntentFilter[] mNdefExchangeFilters;
	String mUserId;
	private boolean mWriteMode = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
		mNfcPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
				getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
		IntentFilter ndefDetected = new IntentFilter(
				NfcAdapter.ACTION_NDEF_DISCOVERED);

		try {
			ndefDetected.addDataType("text/plain");
		} catch (MalformedMimeTypeException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		mNdefExchangeFilters = new IntentFilter[] { ndefDetected };

		/*
		 * Get existing access_token if any
		 */
		mPrefs = getPreferences(MODE_PRIVATE);
		String access_token = mPrefs.getString("access_token", null);
		long expires = mPrefs.getLong("access_expires", 0);
		if (access_token != null) {
			facebook.setAccessToken(access_token);
		}
		if (expires != 0) {
			facebook.setAccessExpires(expires);
		}

		/*
		 * Only call authorize if the access_token has expired.
		 */
		// if (!facebook.isSessionValid()) {
		//
		// facebook.authorize(this, new String[] {}, new DialogListener() {
		// @Override
		// public void onComplete(Bundle values) {
		// SharedPreferences.Editor editor = mPrefs.edit();
		// editor.putString("access_token", facebook.getAccessToken());
		// editor.putLong("access_expires",
		// facebook.getAccessExpires());
		// editor.commit();
		// }
		//
		// @Override
		// public void onFacebookError(FacebookError error) {
		// }
		//
		// @Override
		// public void onError(DialogError e) {
		// }
		//
		// @Override
		// public void onCancel() {
		// }
		// });
		// }
	}

	@Override
	protected void onResume() {
		super.onResume();
		enableNDefExchangeMode();

		facebook.extendAccessTokenIfNeeded(this, null);
	}

	private void enableNDefExchangeMode() {
		mNfcAdapter.enableForegroundNdefPush(FriendsviaNFCActivity.this,
				getUidAsNDef(mUserId));
		mNfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent,
				mNdefExchangeFilters, null);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		if (!mWriteMode
				&& NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
			NdefMessage[] msgs = getNdefMessages(intent);
			fireFriendRequest(msgs[0]);
			Toast.makeText(this, "Sent friend request via NFC!",
					Toast.LENGTH_SHORT).show();
		}

	}

	private void fireFriendRequest(NdefMessage ndefMessage) {

		Bundle params = new Bundle();
		final String user = ndefMessage.getRecords()[0].toString();
		params.putString("to", user);

		AsyncTask<Bundle, String, String> task = new AsyncTask<Bundle, String, String>() {

			@Override
			protected String doInBackground(Bundle... params) {

				facebook.dialog(getApplicationContext(), "feed", params[0],
						new DialogListener() {

							@Override
							public void onFacebookError(FacebookError e) {
								Log.e("NFC", "Cannot add friend: " + user);
							}

							@Override
							public void onError(DialogError e) {
								// TODO Auto-generated method stub

							}

							@Override
							public void onComplete(Bundle values) {
								Log.d("NFC", "Added friend!");

							}

							@Override
							public void onCancel() {
								// TODO Auto-generated method stub

							}
						});
				return null;
			}

		};
		task.execute(params);

	}

	NdefMessage[] getNdefMessages(Intent intent) {

		NdefMessage[] msgs = null;
		String action = intent.getAction();
		if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
				|| NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
			Parcelable[] rawMsgs = intent
					.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
			if (rawMsgs != null) {
				msgs = new NdefMessage[rawMsgs.length];
				for (int i = 0; i < rawMsgs.length; i++) {
					msgs[i] = (NdefMessage) rawMsgs[i];
				}
			} else {
				byte[] empty = new byte[] {};
				NdefRecord record = new NdefRecord(NdefRecord.TNF_UNKNOWN,
						empty, empty, empty);
				NdefMessage msg = new NdefMessage(new NdefRecord[] { record });
				msgs = new NdefMessage[] { msg };
			}
		} else {
			Log.d("NFC", "Unkown intent");
			finish();
		}
		return msgs;
	}

	NdefMessage getUidAsNDef(String uUserId) {
		String userId = "";
		JSONObject json;
		try {
			json = Util.parseJson(facebook.request("me"));
			userId = json.getString("id");
		} catch (FacebookError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		byte[] textBytes = userId.getBytes();
		NdefRecord ndefRecord = new NdefRecord(NdefRecord.TNF_MIME_MEDIA,
				"text/plain".getBytes(), new byte[] {}, textBytes);
		return new NdefMessage(new NdefRecord[] { ndefRecord });
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		facebook.authorizeCallback(requestCode, resultCode, data);
	}

	@Override
	public void onNdefPushComplete(NfcEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public NdefMessage createNdefMessage(NfcEvent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
}