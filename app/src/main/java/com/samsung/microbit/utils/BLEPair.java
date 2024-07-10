package com.samsung.microbit.utils;

import static android.bluetooth.BluetoothAdapter.STATE_CONNECTED;
import static android.bluetooth.BluetoothAdapter.STATE_DISCONNECTED;

import static com.samsung.microbit.BuildConfig.DEBUG;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.samsung.microbit.R;
import com.samsung.microbit.ui.BluetoothChecker;
import com.samsung.microbit.ui.activity.PairingActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import pl.droidsonroids.gif.GifImageView;

public class BLEPair implements BluetoothAdapter.LeScanCallback {
    private static final String TAG = BLEPair.class.getSimpleName();
    private BLEPairCallback paramCallback;
    private Handler mainLooperHandler = null;

    private BluetoothAdapter bleAdapter = null;
    private BluetoothGatt bleGatt = null;

    private BluetoothLeScanner leScanner = null;
    private ScanCallback leScannerCallback = null;

    private static final long TIMEOUT_SCAN = 15000;
    private static final long TIMEOUT_PAIR_CONNECT = 20000;
    // Android 13 - Measured times: 10s for status 133; 5s for a good connection
    // If it really times out, check permissions are OK and BLE & location are on
    private static final long TIMEOUT_PAIR_CHECK = 2000;
    private static final int PAIR_CHECKS = 15; // TIMEOUT_PAIR_CHECK * PAIR_CHECKS = 30 seconds
    private static final int PAIR_RETRIES = 4;
    // Android 13 - I have seen 4 status 133s before a good connect and pair
    private int pairChecks = 0;
    private int pairTries = 0;
    private boolean scanning = false;
    private boolean pairing = false;
    private boolean waitingForDisconnect = false;
    private boolean wasNotBonded = false;

    public enum enumResult {
        None,
        Found,
        Connected,
        AlreadyPaired,
        Paired,
        TimeoutScan,
        TimeoutConnect,
        TimeoutPair,
        Next
    }
    public enumResult resultState = enumResult.None;
    public BluetoothDevice resultDevice;
    public int resultHardwareVersion = 0;

    private enum enumGattState {
        Disconnected,
        Connecting,
        Connected,
        WaitingForServices,
        ServicesDiscovered,
        Error,
        Next
    }
    private enumGattState gattState = enumGattState.Disconnected;

    /**
     * Callback interface for client
     */
    public interface BLEPairCallback {
        Activity BLEPairGetActivity();
        String BLEPairGetDeviceName();
        String BLEPairGetDeviceCode();
        void BLEPairResult();
    }

    public BLEPair( BLEPairCallback callback) {
        super();
        paramCallback = callback;
        mainLooperHandler = new Handler( Looper.getMainLooper());
    }

    public void stopScanAndPair() {
        logi("###>>>>>>>>>>>>>>>>>>>>> stopScanAndPair");
        stop();
        resultState = enumResult.None;
    }

    public Boolean startScan() {
        logi("###>>>>>>>>>>>>>>>>>>>>> startScan");
        stop();
        resultState = enumResult.None;
        if ( !scanStart()) {
            return false;
        }
        delayStart( delayCallbackScan, TIMEOUT_SCAN);
        return true;
    }


    public Boolean startPair() {
        logi("###>>>>>>>>>>>>>>>>>>>>> startPair");
        pairTries = PAIR_RETRIES;
        if ( !startPairTry()) {
            return false;
        }
        return true;
    }

    private Boolean startPairTry() {
        logi("startPairTry");
        pairChecks = 0;
        stop();
        if ( !pairConnect()) {
            return false;
        }
        delayStart( delayCallbackConnect, TIMEOUT_PAIR_CONNECT);
        return true;
    }

    private void stop() {
        logi("stop");
        delayStopAll();
        scanStop();
        pairStop();
    }

    private void signalResult( enumResult state) {
        logi("signalResult " + state);
        stop();
        resultState = state;
        paramCallback.BLEPairResult();
    }

    private void signalProgress( enumResult state) {
        logi("signalProgress " + state);
        resultState = state;
        paramCallback.BLEPairResult();
    }

    @SuppressLint("MissingPermission")
    private boolean bondedAndHardwareVersionDiscovered() {
        logi("bondedAndHardwareVersionDiscovered state " + resultDevice.getBondState() + " resultHardwareVersion " + resultHardwareVersion);
        if ( resultDevice.getBondState() != BluetoothDevice.BOND_BONDED) {
            wasNotBonded = true;
            return false;
        }
        // Wait for service discovery to set resultHardwareVersion
        if ( resultHardwareVersion <= 0) {
            return false;
        }
        return true;
    }

    @SuppressLint("MissingPermission")
    private boolean startWaitingForDisconnectIfBondedAndHardwareVersionDiscovered() {
        if ( !bondedAndHardwareVersionDiscovered()) {
            return false;
        }
        // Wait for micro:bit to disconnect
        waitingForDisconnect = true;
        delayStopAll();
        delayStart( delayCallbackWaitingForDisconnect, 6000);
        logi("Start waiting for disconnect");
        return true;
    }

    private void onDisconnect() {
        // If actually pairing, micro:bit will break the connection
        // Allow time for tick to appear
        logi("onDisconnect");
        if ( bondedAndHardwareVersionDiscovered()) {
            delayStopAll();
            delayStart( delayCallbackSignalResultPaired, 300);
        } else if ( waitingForDisconnect) {
            delayStopAll();
            delayStart( delayCallbackSignalResultPaired, 300);
        } else {
            logi("ERROR - disconnected");
            logi("Prepare delayed retry");
            gattState = enumGattState.Error;
            delayStart( delayCallbackConnect, 1000);
        }
    }
    private void logi(String message) {
        if(DEBUG) {
            Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
        }
    }

    private final Runnable delayCallbackScan = new Runnable() {
        @Override
        public void run() {
            logi("timerCallbackScan");
            if ( scanning) {
                signalResult( enumResult.TimeoutScan);
            }
        }
    };

    private final Runnable delayCallbackConnect = new Runnable() {
        @Override
        public void run() {
            logi("delayCallbackConnect");
            if (pairTries > 0) {
                pairTries -= 1;
                if (startPairTry()) {
                    return;
                }
            }
            signalResult(enumResult.TimeoutConnect);
        }
    };

    private final Runnable delayCallbackConnected = new Runnable() {
        @Override
        public void run() {
            logi("delayCallbackConnected");
            signalProgress(enumResult.Connected);
        }
    };

    private final Runnable delayCallbackDiscover = new Runnable() {
        @SuppressLint("MissingPermission")
        @Override
        public void run() {
            logi("delayCallbackDiscover");
            if ( pairing && bleGatt != null) {
                logi("Delayed call to discoverServices()");
                gattState = enumGattState.WaitingForServices;
                boolean success = bleGatt.discoverServices();
                if (!success) {
                    logi("ERROR_SERVICE_DISCOVERY_NOT_STARTED");
                    gattState = enumGattState.Error;
                    delayStartCheck();
                    return;
                }
            }
        }
    };

    private final Runnable delayCallbackBond = new Runnable() {
        @SuppressLint("MissingPermission")
        @Override
        public void run() {
            logi("delayCallbackBond");
            if ( pairing) {
                if ( resultDevice.getBondState() == BluetoothDevice.BOND_NONE) {
                    logi("Delayed call to createBond()");
                    boolean started = resultDevice.createBond();
                    if (!started) {
                        logi("delayed createBond() failed");
                    }
                }
            }
        }
    };

    private final Runnable delayCallbackCheck = new Runnable() {
        @Override
        public void run() {
            logi("delayCallbackCheck pairChecks " + pairChecks);
            if (pairing) {
                if ( startWaitingForDisconnectIfBondedAndHardwareVersionDiscovered()) {
                    return;
                }
                if ( pairChecks > 0) {
                    // We are connected and polling for bonding
                    pairChecks -= 1;
                    delayStartCheck();
                    return;
                }
                signalResult(enumResult.TimeoutPair);
            }
        }
    };

    private final Runnable delayCallbackWaitingForDisconnect = new Runnable() {
        @Override
        public void run() {
            logi("delayCallbackWaitingForDisconnect wasNotBonded = " + wasNotBonded);
            signalResult( wasNotBonded ? enumResult.Paired : enumResult.AlreadyPaired);
        }
    };

    private final Runnable delayCallbackSignalResultPaired = new Runnable() {
        @Override
        public void run() {
            logi("delayCallbackSignalResultPaired wasNotBonded = " + wasNotBonded);
            signalResult( wasNotBonded ? enumResult.Paired : enumResult.AlreadyPaired);
        }
    };

    private void delayStopAll()
    {
        logi("delayStopAll");
        mainLooperHandler.removeCallbacks( delayCallbackScan);
        mainLooperHandler.removeCallbacks( delayCallbackConnect);
        mainLooperHandler.removeCallbacks( delayCallbackConnected);
        mainLooperHandler.removeCallbacks( delayCallbackDiscover);
        mainLooperHandler.removeCallbacks( delayCallbackBond);
        mainLooperHandler.removeCallbacks( delayCallbackCheck);
        mainLooperHandler.removeCallbacks( delayCallbackWaitingForDisconnect);
        mainLooperHandler.removeCallbacks( delayCallbackSignalResultPaired);
    }

    private void delayStop( Runnable callback)
    {
        logi("delayStop " + callback);
        mainLooperHandler.removeCallbacks( callback);
    }

    private void delayStart( Runnable callback, long milliseconds)
    {
        logi("delayStart " + callback + " ms " + milliseconds);
        mainLooperHandler.removeCallbacks( callback);
        mainLooperHandler.postDelayed(callback, milliseconds);
    }

    private void delayStartCheck()
    {
        if ( pairing) {
            if (pairChecks == 0)
                pairChecks = PAIR_CHECKS;
            logi("delayStartCheck pairChecks " + pairChecks);
            delayStart(delayCallbackCheck, TIMEOUT_PAIR_CHECK);
        }
    }

    @SuppressLint("MissingPermission")
    private void scanStop() {
        logi("scanStop");
        if ( scanning) {
            scanning = false;
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                bleAdapter.stopLeScan(getOldScanCallback());
            } else {
                leScanner.stopScan( getScanCallback());
            }
        }
    }

    @SuppressLint("MissingPermission")
    private Boolean scanStart() {
        logi("scanStart");

        if ( bleAdapter == null) {
            final BluetoothManager bluetoothManager = (BluetoothManager) paramCallback.BLEPairGetActivity().getSystemService(Context.BLUETOOTH_SERVICE);
            bleAdapter = bluetoothManager.getAdapter();
            if ( bleAdapter == null) {
                return false;
            }
        }

        if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if ( leScanner == null) {
                leScanner = bleAdapter.getBluetoothLeScanner();
                if (leScanner == null) {
                    return false;
                }
            }
        }

        if ( scanning) {
            return true;
        }

        scanning = true;
        
        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            logi("scanStart old");
            if ( !bleAdapter.startLeScan(getOldScanCallback())) {
                scanning = false;
                return false;
            }
        } else {
            logi("scanStart new");
            List<ScanFilter> filters = new ArrayList<>();
            // TODO: play with ScanSettings further to ensure the Kit kat devices connectMaybeInit with higher success rate
            ScanSettings settings;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                settings = new ScanSettings.Builder().setLegacy(true).setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            } else {
                settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            }
            leScanner.startScan(filters, settings, getScanCallback());
        }

        return true;
    }

    private BluetoothAdapter.LeScanCallback getOldScanCallback() {
        return this;
    }

    /**
     * Gets newScanCallback. If it is null it creates a new one.
     *
     * @return Current scan callback or a new one.
     */
    private ScanCallback getScanCallback() {
        if ( leScannerCallback != null) {
            return leScannerCallback;
        }

        leScannerCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                Log.i("callbackType = ", String.valueOf(callbackType));
                Log.i("result = ", result.toString());
                BluetoothDevice btDevice = result.getDevice();
                final ScanRecord scanRecord = result.getScanRecord();
                if(scanRecord != null) {
                    onLeScan(btDevice, result.getRssi(), scanRecord.getBytes());
                }
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);
                for(ScanResult sr : results) {
                    Log.i("Scan result - Results ", sr.toString());
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Log.i("Scan failed", "Error Code : " + errorCode);
            }
        };

        return leScannerCallback;
    }

    /**
     * onLeScan is the old callback for BluetoothAdapter.startLeScan
     * The new callback, leScannerCallback, calls here
     */
    @SuppressLint("MissingPermission")
    @Override
    public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
        logi("onLeScan");

        if (!scanning) {
            return;
        }

        if (device == null) {
            return;
        }

        if ((paramCallback.BLEPairGetDeviceName().isEmpty()) || (device.getName() == null)) {
            logi("mLeScanCallback.onLeScan() ::   Cannot Compare " + device.getAddress() + " " + rssi + " " + Arrays.toString(scanRecord));
            Log.v(TAG, String.valueOf(device));
            Log.v(TAG, String.valueOf( paramCallback.BLEPairGetDeviceName()));
            return;
        }

        String s = device.getName().toLowerCase();
        //Replace all : to blank - Fix for #64
        //TODO Use pattern recognition instead
        s = s.replaceAll(":", "");
        if ( !paramCallback.BLEPairGetDeviceName().toLowerCase().startsWith(s)) {
            logi("mLeScanCallback.onLeScan() ::   Found - device.getName() == " + device.getName().toLowerCase()
                    + " , device address - " + device.getAddress());
            return;
        }

        logi("mLeScanCallback.onLeScan() ::   Found micro:bit -"
                + device.getName().toLowerCase() + " " + device.getAddress());

        // Stop scanning as device is found.
        scanStop();
        resultDevice = device;
        signalResult( enumResult.Found);
    }

    BluetoothGattCallback pairGattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if ( !pairing) {
                return;
            }

            if ( gatt.getDevice().getBondState() != BluetoothDevice.BOND_BONDED) {
                wasNotBonded = true;
            }

            logi("onConnectionStateChange " + newState + " status " + status);
            if ( status != BluetoothGatt.GATT_SUCCESS) {
                if ( newState == STATE_DISCONNECTED) {
                    onDisconnect();
                    return;
                }
                delayStopAll();
                pairStop();
                logi("ERROR - status");
                logi("Prepare for retry after a short delay");
                gattState = enumGattState.Error;
                resultState = enumResult.Found;
                delayStart( delayCallbackConnect, 1000);
                return;
            }

            if ( newState == STATE_CONNECTED) {
                logi("STATE_CONNECTED");
                gattState = enumGattState.Connected;
                delayStop( delayCallbackConnect);
                delayStart( delayCallbackConnected, 1);
                /*
                 * Nordic says to wait 1600ms for possible service changed before discovering
                 * https://github.com/NordicSemiconductor/Android-DFU-Library/blob/e0ab213a369982ae9cf452b55783ba0bdc5a7916/dfu/src/main/java/no/nordicsemi/android/dfu/DfuBaseService.java#L888
                 */
                if ( gatt.getDevice().getBondState() == BluetoothDevice.BOND_BONDED) {
                    logi("Already bonded - delay calling discoverServices()");
                    delayStart( delayCallbackDiscover, 1600);
                    // NOTE: This also works with shorted waiting time. The gatt.discoverServices() must be called after the indication is received which is
                    // about 600ms after establishing connection. Values 600 - 1600ms should be OK.
                } else {
                    logi("calling discoverServices()");
                    wasNotBonded = true;
                    gattState = enumGattState.WaitingForServices;
                    boolean success = gatt.discoverServices();
                    if (!success) {
                        logi("ERROR - discoverServices() failed");
                        logi("Prepare delayed retry");
                        gattState = enumGattState.Error;
                        delayStart( delayCallbackConnect, 1000);
                        return;
                    }
                }
                delayStartCheck();
            }
            else if( newState == STATE_DISCONNECTED) {
                // If actually pairing, micro:bit will break the connection
                logi("STATE_DISCONNECTED");
                onDisconnect();
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if ( !pairing) {
                return;
            }

            logi("onServicesDiscovered status " + status);
            if ( status != 0) {
                pairStop();
                logi("ERROR - status");
                logi("Prepare for retry after a short delay");
                gattState = enumGattState.Error;
                resultState = enumResult.Found;
                delayStart( delayCallbackConnect, 1000);
                return;
            }

            if ( gattState == enumGattState.WaitingForServices) {
                if (gatt.getService(UUID.fromString("0000fe59-0000-1000-8000-00805f9b34fb")) != null) {
                    logi("Hardware Type: V2");
                    resultHardwareVersion = 2;
                } else {
                    logi("Hardware Type: V1");
                    resultHardwareVersion = 1;
                }

                gattState = enumGattState.ServicesDiscovered;

                if ( startWaitingForDisconnectIfBondedAndHardwareVersionDiscovered())
                    return;

                if ( resultDevice.getBondState() == BluetoothDevice.BOND_NONE) {
                    logi("Delay calling createBond() to wait for bonding to start automatically");
                    delayStart( delayCallbackBond, 1500);
                }
            }
            delayStartCheck();
        }
    };

    @SuppressLint("MissingPermission")
    private void pairStop() {
        logi("pairStop");
        pairing = false;
        if ( bleGatt != null) {
            bleGatt.disconnect();
            bleGatt.close();
            bleGatt = null;
            paramCallback.BLEPairGetActivity().unregisterReceiver(pairReceiver);
            gattState = enumGattState.Disconnected;;
        }
    }

    @SuppressLint("MissingPermission")
    private Boolean pairConnect() {
        logi("pairConnect");

        if ( pairing) {
            return true;
        }

        pairStop();

        paramCallback.BLEPairGetActivity().registerReceiver( pairReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));

        waitingForDisconnect = false;
        resultHardwareVersion = 0;
        gattState = enumGattState.Connecting;
        pairing = true;
        bleGatt = connect( resultDevice, pairGattCallback);
        if ( bleGatt == null) {
            pairing = false;
            return false;
        }
        return true;
    }

    @SuppressLint("MissingPermission")
    private  BluetoothGatt connect( BluetoothDevice device, BluetoothGattCallback bluetoothGattCallback)
    {
        BluetoothGatt gatt;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            gatt = device.connectGatt(
                    paramCallback.BLEPairGetActivity(),
                    false,
                    bluetoothGattCallback,
                    BluetoothDevice.TRANSPORT_LE,
                    BluetoothDevice.PHY_LE_1M_MASK | BluetoothDevice.PHY_LE_2M_MASK);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            gatt = device.connectGatt(
                    paramCallback.BLEPairGetActivity(),
                    false,
                    bluetoothGattCallback,
                    BluetoothDevice.TRANSPORT_LE);
        } else {
            gatt = device.connectGatt(
                    paramCallback.BLEPairGetActivity(),
                    false,
                    bluetoothGattCallback);
        }

        return gatt;
    }

    private static final String MICROBIT_NAME_OLD = "BBC MicroBit";
    private static final String MICROBIT_NAME = "BBC micro:bit";

    public boolean nameIsMicrobit( String name)
    {
        return name.startsWith( MICROBIT_NAME) || name.startsWith( MICROBIT_NAME_OLD);
    }

    public boolean nameIsMicrobitWithCode( String name, String code)
    {
        return nameIsMicrobit( name) && name.toLowerCase().contains( code.toLowerCase());
    }

    /**
     * Occurs when a bond state has been changed and provides action to handle that.
     */
    private final BroadcastReceiver pairReceiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            logi("pairReceiver " + action);

            if ( BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device == null) {
                    Log.e(TAG, "pairReceiver - no device");
                    return;
                }
                final String name = device.getName();
                final String addr = device.getAddress();
                final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                final int prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);
                logi("pairReceiver -" + " name = " + name + " addr = " + addr + " state = " + state + " prevState = " + prevState);
                if ( state != BluetoothDevice.BOND_BONDED && prevState != BluetoothDevice.BOND_BONDED) {
                    wasNotBonded = true;
                }
                if (name == null || name.isEmpty() || addr.isEmpty()) {
                    return;
                }
                // Check the changed device is the one we are trying to pair
                if ( pairing && nameIsMicrobitWithCode(device.getName(), paramCallback.BLEPairGetDeviceCode())) {
                    if (state == BluetoothDevice.BOND_BONDED) {
                        startWaitingForDisconnectIfBondedAndHardwareVersionDiscovered();
                    }
                }
            }
        }
    };
}
