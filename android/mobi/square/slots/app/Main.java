package mobi.square.slots.app;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

import fr.pixtel.pxinapp.PXInapp;
import fr.pixtel.pxinapp.PXInappProduct;

import mobi.square.slots.api.AndroidApi;
import mobi.square.slots.api.AppWrapper;
import mobi.square.slots.api.Connection;
import mobi.square.slots.api.SlotsApi.LoginType;
import mobi.square.slots.app.Game;
import mobi.square.slots.containers.BankInfo;
import mobi.square.slots.error.StringCodeException;
import mobi.square.slots.handlers.AsyncJsonHandler;
import mobi.square.slots.logger.Log;
import mobi.square.slots.stages.Header;
import mobi.square.slots.tools.AtlasLoader;
import mobi.square.slots.tools.FontsFactory;

public class Main extends AndroidApplication implements AppWrapper, PXInapp.PaymentCallback {

	// Game
	private static Game instance = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Connection.setWrapper(this);
		//System.out.println("onCreate");
		
		if (savedInstanceState != null && instance != null) {
			//System.out.println("Fast init");
			instance = null;
			super.initialize(new com.badlogic.gdx.Game() {
				@Override
				public void create() {
					// Nothing need to do
				}}
			);
			return;
		}
		
		// PX API
		PXInapp.create(this, "A02498732917338113443204513861373695228A09407FE", false);
		PXInapp.setPaymentCallback(this);
		
		// Application
		AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
		config.useAccelerometer = false;
		config.useCompass = false;
		config.useGLSurfaceView20API18 = false;
		config.useWakelock = false;
		config.hideStatusBar = true;
		instance = new Game();
		super.initialize(instance, config);
	}

	@Override
	protected void onStart() {
		Connection.setWrapper(this);
		super.onStart();
	}

	@Override
	protected void onPause() {
		super.onPause();
		PXInapp.pause();
	}

	@Override
	protected void onResume() {
		Connection.setWrapper(this);
		super.onResume();
		PXInapp.resume();
	}

	@Override
	protected void onDestroy() {
		FontsFactory.dispose();
		AtlasLoader.disposeAll();
		Connection.dispose();
		super.onDestroy();
		PXInapp.destroy();
	}

	// Device ID

	@Override
	public String getDeviceId() {
		TelephonyManager tm = (TelephonyManager)super.getBaseContext().getSystemService(Context.TELEPHONY_SERVICE);
	    String tmDevice, tmSerial, androidId;
	    tmDevice = "" + tm.getDeviceId();
	    tmSerial = "" + tm.getSimSerialNumber();
	    androidId = "" + android.provider.Settings.Secure.getString(super.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
	    UUID deviceUuid = new UUID(androidId.hashCode(), ((long)tmDevice.hashCode() << 32) | tmSerial.hashCode());
	    return deviceUuid.toString();
	}

	// Settings

	@Override
	public boolean readSoundState() {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		return preferences.getBoolean("soundOn", true);
	}

	@Override
	public void writeSoundState(boolean state) {
		SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putBoolean("soundOn", state);
        editor.commit();
	}

	@Override
	public boolean readNotificationState() {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		return preferences.getBoolean("notificationsOn", true);
	}

	@Override
	public void writeNotificationState(boolean state) {
		SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putBoolean("notificationsOn", state);
        editor.commit();
	}

	@Override
	public String readDeviceId() {
		if (AndroidApi.ONLINE) {
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
			return preferences.getString("deviceId", null);
		} else return this.getDeviceId();
	}

	@Override
	public void writeDeviceId(String device_id) {
		SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		if (device_id != null) {
			editor.putString("deviceId", device_id);
		} else editor.remove("deviceId");
        editor.commit();
	}

	@Override
	public void readData(Map<String, String> strings, Map<String, Integer> integers) {
		Set<String> keys;
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		if (strings != null) {
			keys = strings.keySet();
			for (String key : keys) {
				strings.put(key, preferences.getString(key, strings.get(key)));
			}
		}
		if (integers != null) {
			keys = integers.keySet();
			for (String key : keys) {
				Integer value = integers.get(key);
				integers.put(key, preferences.getInt(key, value != null ? value.intValue() : 0));
			}
		}
	}

	@Override
	public void writeData(Map<String, String> strings, Map<String, Integer> integers) {
		Set<String> keys;
		SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		if (strings != null) {
			keys = strings.keySet();
			for (String key : keys) {
				editor.putString(key, strings.get(key));
			}
		}
		if (integers != null) {
			keys = integers.keySet();
			for (String key : keys) {
				editor.putInt(key, integers.get(key));
			}
		}
        editor.commit();
	}

	// PX API

	private Header ui_window = null;
	private GetBankItems bank_loader = null;

	@Override
	public void getBankItems(Header window, BankInfo[] items, GetBankItems handler) {
		this.ui_window = window;
		this.bank_loader = handler;
		
		for (BankInfo item : items) {
			int id = Integer.parseInt(item.getId());
			PXInappProduct p = PXInapp.getInappProduct(id);
			if (p != null) {
				item.setDiscount(p.discountRate);
				item.setPriceString(p.priceString);
			} else item.setPriceString("error");
		}
		
		if (bank_loader != null) {
			bank_loader.get_items(items);
			bank_loader = null;
		}
	}

	@Override
	public void purchase(String id) {
		int result = PXInapp.initiatePayment(Integer.parseInt(id));
		if (result < 0) {
			switch (result) {
				case PXInapp.RESULT_PAYMENT_IN_PROGRESS:
					ui_window.showMessage("title_error", "px_payment_in_progress");
					break;
				case PXInapp.RESULT_ALREADY_PURCHASED:
					ui_window.showMessage("title_error", "px_already_purchased");
					break;
				case PXInapp.RESULT_ERROR_UNINITIALIZED_LIBRARY:
					ui_window.showMessage("title_error", "px_uninitialized_library");
					break;
				case PXInapp.RESULT_ERROR_BAD_INAPPPRODUCT:
					ui_window.showMessage("title_error", "px_bad_inappproduct");
					break;
				case PXInapp.RESULT_FAILED:
					ui_window.showMessage("title_error", "px_purchase_failed");
					break;
				default:
					break;
			}
		}
	}

	@Override
	public void onPayment(PXInappProduct product, int result) {
		if (result < 0) {
			switch (result) {
				case PXInapp.PAYMENT_OFFER_NOT_AVAILABLE:
					ui_window.showMessage("title_error", "px_offer_not_available");
					break;
				case PXInapp.PAYMENT_INSUFFICIENT_CREDIT:
					ui_window.showMessage("title_error", "px_insufficient_credit");
					break;
				case PXInapp.PAYMENT_TIMEOUT:
				case PXInapp.PAYMENT_ERROR:
					ui_window.showMessage("title_error", "px_payment_error");
					break;
				default:
					break;
			}
		} else {
			try {
				Connection.getInstance().requestAddGold(billing_handler, String.valueOf(product.id), String.valueOf(System.currentTimeMillis()));
			} catch (StringCodeException e) {
				Log.log(e);
			}
		}
	}

	private AsyncJsonHandler billing_handler = new AsyncJsonHandler() {
		@Override
		public void onCompleted(String json) {
			try {
				Connection.getInstance().responseAddGold(json);
			} catch (StringCodeException e) {
				Log.log(e);
			}
			ui_window.updateUserMoney();
		}
	};

	// Authorization

	@Override
	public void authorize(LoginType type) {
		// Nothing need to do
	};

	// Clear

	@Override
	public void clearVars() {
		this.ui_window = null;
		this.bank_loader = null;
	}

}
