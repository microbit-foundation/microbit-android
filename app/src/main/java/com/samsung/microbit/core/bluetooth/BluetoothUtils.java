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

    private static final String PREFERENCES_KEY = "Microbit_PairedDevices";
    private static final String PREFERENCES_PAIREDDEV_KEY = "PairedDeviceDevice";

    // sConnectedDevice used only as a cache - actual value stored in prefs
    // Use getCurrentMicrobit() to read, so as to read prefs if necessary
    private static ConnectedDevice sConnectedDevice = null;

    private static void logi(String message) {
        if(DEBUG) {
            Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
        }
    }

    private static SharedPreferences getPreferences(Context ctx) {
        logi("getPreferences() :: ctx.getApplicationContext() = " + ctx.getApplicationContext());
        return ctx.getApplicationContext().getSharedPreferences(PREFERENCES_KEY, Context.MODE_MULTI_PROCESS);
    }

    private static ConnectedDevice deviceFromPrefs(Context ctx) {
        SharedPreferences prefs = getPreferences( ctx);

        ConnectedDevice fromPrefs = null;

        if( prefs.contains(PREFERENCES_PAIREDDEV_KEY)) {
            String pairedDeviceString = prefs.getString(PREFERENCES_PAIREDDEV_KEY, null);
            Gson gson = new Gson();
            fromPrefs = gson.fromJson(pairedDeviceString, ConnectedDevice.class);
        }
        return fromPrefs;
    }

    private static void deviceToPrefs(Context ctx, ConnectedDevice toPrefs) {
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

    public static String parseCharacteristic(final BluetoothGattCharacteristic characteristic) {
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

    public static void setCurrentMicrobitFirmware(Context ctx, String firmware) {
        Log.v("BluetoothUtils", "Updating the microbit firmware version");
        getCurrentMicrobit( ctx);
        sConnectedDevice.mfirmware_version = firmware;
        saveCurrentMicrobit( ctx);
    }

    public static void setCurrentMicrobitConnectionStartTime(Context ctx, long time) {
        Log.e("BluetoothUtils", "Updating the microbit connection time");
        getCurrentMicrobit( ctx);
        sConnectedDevice.mlast_connection_time = time;
        saveCurrentMicrobit( ctx);
    }

    public static void setCurrentMicrobitHardwareVersion(Context ctx, int version) {
        Log.e("BluetoothUtils", "setCurrentMicrobitHardwareVersion " +version);
        getCurrentMicrobit( ctx);
        sConnectedDevice.mhardwareVersion = version;
        saveCurrentMicrobit( ctx);
    }

    public static void setCurrentMicrobitStatus(Context ctx, boolean status) {
        Log.e("BluetoothUtils", "setCurrentMicrobitStatus " + status);
        getCurrentMicrobit( ctx);
        sConnectedDevice.mStatus = status;
        saveCurrentMicrobit( ctx);
    }

    /**
     * Check if we can access system paired devices list
     * Do we have permission and is Bluetooth on?
     */
    public static boolean canCheckPairedList(Context ctx) {
        if ( havePermissionsFlashing( ctx)) {
            MBApp mbApp = MBApp.getApp();
            BluetoothManager manager = (BluetoothManager) mbApp.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager.getAdapter();
            return adapter.isEnabled();
        }
        return false;
    }

    /**
     * Check if address is in system paired devices list
     * if address is null return false
     * if we cannot check return false
     */
    public static boolean addressIsDefinitelyInPairedList(Context ctx, final String address) {
        if ( address == null) {
            return false;
        }
        if ( canCheckPairedList( ctx)) {
            MBApp mbApp = MBApp.getApp();
            BluetoothManager manager = (BluetoothManager) mbApp.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager.getAdapter();
            @SuppressLint("MissingPermission")
            Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
            for(BluetoothDevice bt : pairedDevices) {
                if(bt.getAddress().equals( address)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if address is not in system paired devices list
     * if address is null return true
     * if we cannot check return false
     */
    public static boolean addressIsDefinitelyNotInPairedList(Context ctx, final String address) {
        if ( address == null) {
            return true;
        }
        if ( canCheckPairedList( ctx)) {
            MBApp mbApp = MBApp.getApp();
            BluetoothManager manager = (BluetoothManager) mbApp.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager.getAdapter();
            @SuppressLint("MissingPermission")
            Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
            for(BluetoothDevice bt : pairedDevices) {
                if(bt.getAddress().equals( address)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static void saveCurrentMicrobit(Context ctx) {
        deviceToPrefs( ctx, sConnectedDevice);
    }

    private static ConnectedDevice loadCurrentMicrobit(Context ctx) {
        sConnectedDevice = deviceFromPrefs(ctx);
        if ( sConnectedDevice == null) {
            sConnectedDevice = new ConnectedDevice();
        }
        return sConnectedDevice;
    }

    public static void setCurrentMicroBit(Context ctx, ConnectedDevice newDevice) {
        sConnectedDevice = newDevice;
        saveCurrentMicrobit( ctx);
    }

    public static ConnectedDevice getCurrentMicrobit(Context ctx) {
        if ( sConnectedDevice == null) {
            sConnectedDevice = loadCurrentMicrobit( ctx);
        }
        return sConnectedDevice;
    }

    public static boolean getCurrentMicrobitIsValid(Context ctx) {
        getCurrentMicrobit( ctx);
        return sConnectedDevice != null
                && sConnectedDevice.mPattern != null
                && sConnectedDevice.mName != null
                && sConnectedDevice.mAddress != null;
    }

    public static boolean getCurrentMicrobitIsDefinitelyInPairedList(Context ctx) {
        getCurrentMicrobit(ctx);
        return addressIsDefinitelyInPairedList( ctx, sConnectedDevice.mAddress);
    }

    public static boolean getCurrentMicrobitIsDefinitelyNotInPairedList(Context ctx) {
        getCurrentMicrobit(ctx);
        return addressIsDefinitelyNotInPairedList( ctx, sConnectedDevice.mAddress);
    }

//    public static ConnectedDevice getPairedMicrobit(Context ctx) {
//        if ( getCurrentMicrobitIsDefinitelyNotInPairedList( ctx)) {
//            setCurrentMicroBit( ctx, new ConnectedDevice());
//        }
//        return sConnectedDevice;
//    }
}
