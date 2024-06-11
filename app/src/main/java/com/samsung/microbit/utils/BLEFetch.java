package com.samsung.microbit.utils;

import static android.bluetooth.BluetoothAdapter.STATE_CONNECTED;
import static android.bluetooth.BluetoothAdapter.STATE_DISCONNECTED;

import static com.samsung.microbit.BuildConfig.DEBUG;

import androidx.annotation.NonNull;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.UUID;

public class BLEFetch implements MicroBitUtility.Client {
    private static final String TAG = BLEFetch.class.getSimpleName();
    private Handler mainLooperHandler = null;

    private BluetoothGatt mBleGatt = null;

    private static final long CONNECT_TIMEOUT = 20000;
    private static final int CONNECT_RETRIES = 1;
    private int connectTries = 0;

    public enum enumResult {
        None,
        Found,
        Connected,
        Discovered,
        ConnectTimeout,
        WorkTimeout,
        Error,
        Success,
        Next
    }
    public enumResult resultState = enumResult.None;

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
    private MicroBitUtility microbitUtility = null;
    private Boolean working = false;
    private static final long WORK_TIMEOUT = 5000;

    public MicroBitUtility.Result mWorkResult = MicroBitUtility.Result.None;

    /**
     * Callback interface for client
     */
    public interface Client {
        Activity bleFetchGetActivity();
        String bleFetchGetDeviceAddress();
        void bleFetchProgress( float progress);
        void bleFetchState();
    }
    private Client mClient;

    private void logi(String message) {
        if(DEBUG) {
            Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
        }
    }


    public BLEFetch( Client client) {
        super();
        mClient = client;
        microbitUtility = new MicroBitUtility( this);
        mainLooperHandler = new Handler( Looper.getMainLooper());
    }

    public void fetchCancel() {
        logi("fetchCancel");
        stop();
        resultState = enumResult.None;
    }

    public Boolean fetchStart() {
        logi("fetchStart");
        connectTries = CONNECT_RETRIES;
        return connectTry();
    }

    public ByteBuffer fetchData() {
        logi("fetchData");
        return microbitUtility.m_Data;
    }

    private Boolean connectTry() {
        logi("connectTry");
        stop();
        if ( !connectStart()) {
            return false;
        }
        delayStart( delayCallbackConnect, CONNECT_TIMEOUT);
        return true;
    }

    private void stop() {
        logi("stop");
        delayStopAll();
        connectStop();
    }

    private void signalResult( enumResult state) {
        logi("signalResult " + state);
        stop();
        resultState = state;
        mClient.bleFetchState();
    }

    private void signalState( enumResult state) {
        logi("signalState " + state);
        resultState = state;
        mClient.bleFetchState();
    }

    private void signalProgress( float progress) {
        logi("signalProgress " + progress);
        mClient.bleFetchProgress( progress);
    }

    private final Runnable delayCallbackConnect = new Runnable() {
        @Override
        public void run() {
            logi("delayCallbackConnect");
            if (connectTries > 0) {
                connectTries -= 1;
                if (connectTry()) {
                    return;
                }
            }
            signalResult(enumResult.ConnectTimeout);
        }
    };

    private final Runnable delayCallbackSignalState = new Runnable() {
        @Override
        public void run() {
            logi("delayCallbackSignalState");
            signalState( resultState);
        }
    };

    private final Runnable delayCallbackWorkTimeout = new Runnable() {
        @Override
        public void run() {
            logi("delayCallbackWorkTimeout");
            signalResult(enumResult.WorkTimeout);
        }
    };

    private final Runnable delayCallbackOnDescriptorWrite = new Runnable() {
        @Override
        public void run() {
            logi("delayCallbackOnDescriptorWrite");
            fetchOnDescriptorWrite();
        }
    };

    private final Runnable delayCallbackDiscover = new Runnable() {
        @SuppressLint("MissingPermission")
        @Override
        public void run() {
            logi("delayCallbackDiscover");
            if ( working && mBleGatt != null) {
                logi("Delayed call to discoverServices()");
                gattState = enumGattState.WaitingForServices;
                boolean success = mBleGatt.discoverServices();
                if (!success) {
                    logi("ERROR_SERVICE_DISCOVERY_NOT_STARTED");
                    gattState = enumGattState.Error;
                    return;
                }
            }
        }
    };

    private void delayStopAll()
    {
        logi("delayStopAll");
        mainLooperHandler.removeCallbacks( delayCallbackConnect);
        mainLooperHandler.removeCallbacks( delayCallbackDiscover);
        mainLooperHandler.removeCallbacks( delayCallbackSignalState);
        mainLooperHandler.removeCallbacks( delayCallbackWorkTimeout);
        mainLooperHandler.removeCallbacks( delayCallbackOnDescriptorWrite);
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

    BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if ( !working) {
                return;
            }

            logi("onConnectionStateChange " + newState + " status " + status);
            if ( status != BluetoothGatt.GATT_SUCCESS) {
                connectStop();
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
                resultState = enumResult.Connected;
                delayStop( delayCallbackConnect);
                delayStart( delayCallbackSignalState, 1);
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
            }
            else if( newState == STATE_DISCONNECTED) {
                logi("STATE_DISCONNECTED");
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if ( !working) {
                return;
            }
            logi("onServicesDiscovered status " + status);
            if ( status != BluetoothGatt.GATT_SUCCESS) {
                connectStop();
                logi("ERROR - status");
                logi("Prepare for retry after a short delay");
                gattState = enumGattState.Error;
                resultState = enumResult.Found;
                delayStart( delayCallbackConnect, 1000);
                return;
            }

            gattState = enumGattState.ServicesDiscovered;
            resultState = enumResult.Discovered;
            delayStop( delayCallbackConnect);
            delayStart( delayCallbackSignalState, 1);
            fetchOnDiscovered();
        }

        @Override
        public void onCharacteristicWrite( BluetoothGatt gatt,
                                           BluetoothGattCharacteristic characteristic,
                                           int status) {
            logi( "onCharacteristicWrite " + status);
            if ( status != BluetoothGatt.GATT_SUCCESS) {
                connectStop();
                gattState = enumGattState.Error;
                delayStart( delayCallbackConnect, 1000);
                return;
            }
        }

        @Override
        public void onCharacteristicRead(@NonNull BluetoothGatt gatt,
                                         @NonNull BluetoothGattCharacteristic characteristic,
                                         @NonNull byte[] value,
                                         int status) {
            logi( "onCharacteristicRead " + status);
            if ( status != BluetoothGatt.GATT_SUCCESS) {
                connectStop();
                gattState = enumGattState.Error;
                delayStart( delayCallbackConnect, 1000);
                return;
            }
            
            BluetoothGattCharacteristic musc = findCharacteristic(MICROBIT_UTILITY_SERVICE, MICROBIT_UTILITY_CTRL);
            
            if ( characteristic.getUuid().equals( musc.getUuid())) {
                fetchOnCharacteristicChanged( value, value.length);
            }
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt,
                                            @NonNull BluetoothGattCharacteristic characteristic,
                                            @NonNull byte[] value) {
            logi( "onCharacteristicChanged");
            BluetoothGattCharacteristic musc = findCharacteristic(MICROBIT_UTILITY_SERVICE, MICROBIT_UTILITY_CTRL);

            if ( characteristic.getUuid().equals( musc.getUuid())) {
                fetchOnCharacteristicChanged( value, value.length);
            }
        }

        @Override
        public void onDescriptorRead(@NonNull BluetoothGatt gatt,
                                     @NonNull BluetoothGattDescriptor descriptor,
                                     int status,
                                     @NonNull byte[] value) {
            logi( "onDescriptorRead " + status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt,
                                      BluetoothGattDescriptor descriptor,
                                      int status) {
            logi( "onDescriptorWrite " + status);
            if ( status != BluetoothGatt.GATT_SUCCESS) {
                connectStop();
                gattState = enumGattState.Error;
                delayStart( delayCallbackConnect, 1000);
                return;
            }
            delayStart( delayCallbackOnDescriptorWrite, 100);
        }
    };

    @SuppressLint("MissingPermission")
    private void connectStop() {
        logi("connectStop");
        working = false;
        if ( mBleGatt != null) {
            fetchOnDisconnect();
            mBleGatt.disconnect();
            mBleGatt.close();
            mBleGatt = null;
            gattState = enumGattState.Disconnected;
        }
    }

    @SuppressLint("MissingPermission")
    private boolean connectStart() {
        logi("connectStart");
        if ( working) {
            return true;
        }
        connectStop();

        Context context = mClient.bleFetchGetActivity();
        String address = mClient.bleFetchGetDeviceAddress();
        if ( context == null || address == null) {
            return false;
        }
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if ( manager == null) {
            return false;
        }
        BluetoothAdapter adapter = manager.getAdapter();
        if ( adapter == null) {
            return false;
        }
        if (!adapter.isEnabled()) {
            return false;
        }
        BluetoothDevice device = adapter.getRemoteDevice( address);
        if ( device == null) {
            return false;
        }

        gattState = enumGattState.Connecting;
        working = true;
        mBleGatt = connect( device, gattCallback);
        if ( mBleGatt == null) {
            working = false;
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
                    mClient.bleFetchGetActivity(),
                    false,
                    bluetoothGattCallback,
                    BluetoothDevice.TRANSPORT_LE,
                    BluetoothDevice.PHY_LE_1M_MASK | BluetoothDevice.PHY_LE_2M_MASK);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            gatt = device.connectGatt(
                    mClient.bleFetchGetActivity(),
                    false,
                    bluetoothGattCallback,
                    BluetoothDevice.TRANSPORT_LE);
        } else {
            gatt = device.connectGatt(
                    mClient.bleFetchGetActivity(),
                    false,
                    bluetoothGattCallback);
        }

        return gatt;
    }
    
    private BluetoothGattCharacteristic findCharacteristic(UUID uuidService, UUID uuidChr) {
        BluetoothGattService s = mBleGatt.getService( uuidService);
        return s == null ? null : s.getCharacteristic( uuidChr);
    }

    @SuppressLint("MissingPermission")
    private int writeCharacteristic( BluetoothGattCharacteristic c, byte[] data, int writeType) {
        logi( "writeCharacteristic " + c.getUuid() + " writeType " + writeType);
        //onWriteCharacteristicStatus = BLE_PENDING;
        if ( Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            c.setWriteType( writeType);
            c.setValue(data);
            int status = mBleGatt.writeCharacteristic(c) ? BluetoothGatt.GATT_SUCCESS : BLE_ERROR_UNKNOWN;
            logi( "writeCharacteristic (legacy) status " + status);
            return status;
        }

        int status = mBleGatt.writeCharacteristic( c, data, writeType);
        logi( "writeCharacteristic status " + status);
        return status;
    }

    @SuppressLint("MissingPermission")
    private boolean cccEnableNotify( BluetoothGattCharacteristic chr, boolean enable) {
        logi( "cccEnableNotify " + enable);
        BluetoothGattDescriptor ccc = chr.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
        if (ccc == null) {
            return false;
        }

        byte [] value = enable
                ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            int status = mBleGatt.writeDescriptor( ccc, value);
            if ( status != BluetoothStatusCodes.SUCCESS) {
                return false;
            }
        } else {
            ccc.setValue(value);
            if ( !mBleGatt.writeDescriptor(ccc)) {
                return false;
            }
        }

        return mBleGatt.setCharacteristicNotification( chr, enable);
    }
    
    public static final UUID MICROBIT_UTILITY_SERVICE   = UUID.fromString("E95D0001-251D-470A-A062-FA1922DFA9A8");
    public static final UUID MICROBIT_UTILITY_CTRL      = UUID.fromString("E95D0002-251D-470A-A062-FA1922DFA9A8");
    public static final int MICROBIT_PDU_SIZE  = 20;
    private final static int BLE_ERROR_UNKNOWN = Integer.MAX_VALUE;
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    @Override
    public MicroBitUtility.Result microbitUtilityWriteCharacteristic(byte[] data, int dataLength) {
        BluetoothGattCharacteristic c = findCharacteristic(MICROBIT_UTILITY_SERVICE, MICROBIT_UTILITY_CTRL);
        if ( c == null) {
            return MicroBitUtility.Result.NoService;
        }
        int status = writeCharacteristic( c, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        if( status != BluetoothGatt.GATT_SUCCESS) {
            return MicroBitUtility.Result.BleError;
        }
        return MicroBitUtility.Result.None;
    }

    private void fetchSignalError( MicroBitUtility.Result result) {
        logi("fetchSignalError " + result);
        mWorkResult = result;
        signalResult( enumResult.Error);
    }

    private void fetchSignalFinished( MicroBitUtility.Result result) {
        logi("fetchSignalFinished " + result);
        mWorkResult = result;
        signalResult( enumResult.Success);
    }
    
    private void fetchSignalProgress( float progress) {
        logi("fetchSignalProgress " + progress);
        signalProgress( progress);
    }

    private void fetchTimeoutStop() {
        delayStop( delayCallbackWorkTimeout);
    }

    private void fetchTimeoutStart() {
        delayStart( delayCallbackWorkTimeout, WORK_TIMEOUT);
    }

    private void fetchOnDisconnect()
    {
        fetchTimeoutStop();
        logi( "fetchOnDisconnect");

        BluetoothGattCharacteristic c = findCharacteristic(MICROBIT_UTILITY_SERVICE, MICROBIT_UTILITY_CTRL);
        if ( c != null) {
            cccEnableNotify( c,false);
        }
    }

    private void fetchOnDiscovered()
    {
        fetchTimeoutStop();
        logi( "fetchOnDiscovered");

        BluetoothGattService s = mBleGatt.getService(UUID.fromString("0000fe59-0000-1000-8000-00805f9b34fb"));
        BluetoothGattCharacteristic c = findCharacteristic(MICROBIT_UTILITY_SERVICE, MICROBIT_UTILITY_CTRL);

        if ( s == null) {
            fetchSignalError( MicroBitUtility.Result.V2Only);
        } else if ( c == null) {
            fetchSignalError( MicroBitUtility.Result.NoService);
        } else if ( !cccEnableNotify( c, true)) {
            fetchSignalError( MicroBitUtility.Result.BleError);
        } else {
            fetchTimeoutStart();
        }
    }

    private void fetchOnDescriptorWrite() {
        fetchTimeoutStop();
        logi( "fetchOnDescriptorWrite");
        MicroBitUtility.Result result = microbitUtility.startLogDownload();
        if ( result != MicroBitUtility.Result.None) {
            fetchSignalError( result);
            return;
        }
        fetchTimeoutStart();
    }

    private void fetchOnCharacteristicChanged( byte[] value, int length)
    {
        if ( !working)
            return;

        fetchTimeoutStop();
        logi( "fetchOnCharacteristicChanged");

        if ( value.length > MICROBIT_PDU_SIZE) {
            fetchSignalError( MicroBitUtility.Result.Protocol);
            return;
        }

        MicroBitUtility.Result result = microbitUtility.process( value, length);

        switch ( result)
        {
            case None:
                fetchSignalProgress( microbitUtility.progress());
                fetchTimeoutStart();
                break;

            case Finished:
                fetchSignalFinished( result);
                break;

            case NoData:
            case BleError:
            case V2Only:
            case NoService:
            case Protocol:
            case OutOfMemory:
            default:
                fetchSignalError( result);
                break;
        }
    }
}
