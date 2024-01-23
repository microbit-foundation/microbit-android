package com.samsung.microbit.core.bluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.google.gson.Gson;
import com.samsung.microbit.MBApp;
import com.samsung.microbit.data.model.ConnectedDevice;

import java.util.Set;

import static com.samsung.microbit.BuildConfig.DEBUG;

import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

public class BluetoothUtils {
    private static final String TAG = BluetoothUtils.class.getSimpleName();

    public static final String PREFERENCES_KEY = "Microbit_PairedDevices";
    public static final String PREFERENCES_PAIREDDEV_KEY = "PairedDeviceDevice";

    // sConnectedDevice used only as a buffer - actual value stored in prefs
    private static ConnectedDevice sConnectedDevice = new ConnectedDevice();

    private static void logi(String message) {
        if(DEBUG) {
            Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
        }
    }

    public static SharedPreferences getPreferences(Context ctx) {

        logi("getPreferences() :: ctx.getApplicationContext() = " + ctx.getApplicationContext());
        return ctx.getApplicationContext().getSharedPreferences(PREFERENCES_KEY, Context.MODE_MULTI_PROCESS);
    }

    public static ConnectedDevice deviceFromPrefs(Context ctx) {
        SharedPreferences prefs = getPreferences( ctx);

        ConnectedDevice fromPrefs = null;

        if( prefs.contains(PREFERENCES_PAIREDDEV_KEY)) {
            String pairedDeviceString = prefs.getString(PREFERENCES_PAIREDDEV_KEY, null);
            Gson gson = new Gson();
            fromPrefs = gson.fromJson(pairedDeviceString, ConnectedDevice.class);
        }
        return fromPrefs;
    }

    public static void deviceToPrefs(Context ctx, ConnectedDevice toPrefs) {
        SharedPreferences prefs = ctx.getApplicationContext().getSharedPreferences(PREFERENCES_KEY,
                Context.MODE_MULTI_PROCESS);
        SharedPreferences.Editor editor = prefs.edit();
        if( toPrefs == null) {
            editor.clear();
        } else {
            Gson gson = new Gson();
            String jsonActiveDevice = gson.toJson( toPrefs);
            editor.putString(PREFERENCES_PAIREDDEV_KEY, jsonActiveDevice);
        }
        editor.apply();
    }

    private static boolean havePermission( Context ctx, String permission) {
        return ContextCompat.checkSelfPermission( ctx, permission) == PermissionChecker.PERMISSION_GRANTED;
    }

    private static boolean havePermissionsFlashing( Context ctx) {
        boolean yes = true;
        if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if ( !havePermission( ctx, Manifest.permission.BLUETOOTH_CONNECT))
                yes = false;
        }
        return yes;
    }

    public static String parse(final BluetoothGattCharacteristic characteristic) {
        final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
        final byte[] data = characteristic.getValue();
        if(data == null)
            return "";
        final int length = data.length;
        if(length == 0)
            return "";

        final char[] out = new char[length * 3 - 1];
        for(int j = 0; j < length; j++) {
            int v = data[j] & 0xFF;
            out[j * 3] = HEX_ARRAY[v >>> 4];
            out[j * 3 + 1] = HEX_ARRAY[v & 0x0F];
            if(j != length - 1)
                out[j * 3 + 2] = '-';
        }
        return new String(out);
    }

    public static boolean inZenMode(Context paramContext) {
        /*
         /**
         * Defines global zen mode.  ZEN_MODE_OFF, ZEN_MODE_IMPORTANT_INTERRUPTIONS,

         public static final String ZEN_MODE = "zen_mode";
         public static final int ZEN_MODE_OFF = 0;
         public static final int ZEN_MODE_IMPORTANT_INTERRUPTIONS = 1;
         public static final int ZEN_MODE_NO_INTERRUPTIONS = 2;
         public static final int ZEN_MODE_ALARMS = 3;
        */
        int zenMode = Settings.Global.getInt(paramContext.getContentResolver(), "zen_mode", 0);
        Log.i("MicroBit", "zen_mode : " + zenMode);
        return (zenMode != 0);
    }

    public static void updateFirmwareMicrobit(Context ctx, String firmware) {
        ConnectedDevice fromPrefs = deviceFromPrefs(ctx);
        if( fromPrefs != null) {
            Log.v("BluetoothUtils", "Updating the microbit firmware version");
            fromPrefs.mfirmware_version = firmware;
            deviceToPrefs(ctx, fromPrefs);
        }
    }

    public static void updateConnectionStartTime(Context ctx, long time) {
        ConnectedDevice fromPrefs = deviceFromPrefs(ctx);
        if( fromPrefs != null) {
            Log.e("BluetoothUtils", "Updating the microbit connection time");
            fromPrefs.mlast_connection_time = time;
            deviceToPrefs(ctx, fromPrefs);
        }
    }

    public static ConnectedDevice getPairedMicrobit(Context ctx) {
        SharedPreferences pairedDevicePref = ctx.getApplicationContext().getSharedPreferences(PREFERENCES_KEY,
                Context.MODE_MULTI_PROCESS);

        if(sConnectedDevice == null) {
            sConnectedDevice = new ConnectedDevice();
        }

        ConnectedDevice fromPrefs = deviceFromPrefs(ctx);
        if( fromPrefs == null) {
            sConnectedDevice.mPattern = null;
            sConnectedDevice.mName = null;
        } else {
            boolean pairedMicrobitInSystemList = false;
            sConnectedDevice = fromPrefs;
            //Check if the microbit is still paired with our mobile
            BluetoothAdapter mBluetoothAdapter = ((BluetoothManager) MBApp.getApp().getSystemService(Context
                    .BLUETOOTH_SERVICE)).getAdapter();
            if(mBluetoothAdapter.isEnabled() && havePermissionsFlashing( ctx)) {
                @SuppressLint("MissingPermission") Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
                for(BluetoothDevice bt : pairedDevices) {
                    if(bt.getAddress().equals(sConnectedDevice.mAddress)) {
                        pairedMicrobitInSystemList = true;
                        break;
                    }
                }
            } else {
                //Do not change the list until the Bluetooth is back ON again
                pairedMicrobitInSystemList = true;
            }

            if(!pairedMicrobitInSystemList) {
                Log.e("BluetoothUtils", "The last paired microbit is no longer in the system list. Hence removing it");
                //Return a NULL device & update preferences
                sConnectedDevice.mPattern = null;
                sConnectedDevice.mName = null;
                sConnectedDevice.mStatus = false;
                sConnectedDevice.mAddress = null;
                sConnectedDevice.mPairingCode = 0;
                sConnectedDevice.mfirmware_version = null;
                sConnectedDevice.mlast_connection_time = 0;

                setPairedMicroBit(ctx, null);
            }
        }
        return sConnectedDevice;
    }

    public static void setPairedMicroBit(Context ctx, ConnectedDevice newDevice) {
        deviceToPrefs( ctx, newDevice);
    }

}
