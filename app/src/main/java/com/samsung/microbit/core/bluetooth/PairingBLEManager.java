package com.samsung.microbit.core.bluetooth;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.samsung.microbit.data.model.ConnectedDevice;

import java.util.UUID;

import no.nordicsemi.android.ble.BleManager;

public class PairingBLEManager extends BleManager {

    private static final UUID MICROBIT_DFU_SERVICE = UUID.fromString("e95d93b0-251d-470a-a062-fa1922dfa9a8");
    private static final UUID MICROBIT_SECURE_DFU_SERVICE = UUID.fromString("0000fe59-0000-1000-8000-00805f9b34fb");
    private static final UUID SERVICE_CHANGED = UUID.fromString("00002a05-0000-1000-8000-00805f9b34fb");
    private static final String TAG = PairingBLEManager.class.getSimpleName();

    private int hardwareType = 0;

    public PairingBLEManager(@NonNull Context context) {
        super(context);
    }

    public int getHardwareType() {
        return hardwareType;
    }

    @NonNull
    @Override
    protected BleManagerGattCallback getGattCallback() {
        return new pairingGattCallback();
    }

    private class pairingGattCallback extends BleManagerGattCallback {

        @Override
        protected boolean isRequiredServiceSupported(@NonNull BluetoothGatt gatt) {

            Log.v(TAG, "Check for services");

            final BluetoothGattService V1_DFU = gatt.getService(MICROBIT_DFU_SERVICE);
            if(V1_DFU != null) {
                Log.v(TAG, "Hardware Type: V1");
                hardwareType = 1;
            }

            final BluetoothGattService V2_DFU = gatt.getService(MICROBIT_SECURE_DFU_SERVICE);
            if(V2_DFU != null) {
                Log.v(TAG, "Hardware Type: V2");
                hardwareType = 2;
            }

            final BluetoothGattService serviceChanged = gatt.getService(SERVICE_CHANGED);
            if(V2_DFU != null) {
                Log.v(TAG, "Hardware Type: V2");
                hardwareType = 2;
            }

            if(hardwareType == 0) {
                Log.e(TAG, "Hardware Type: Not Detected!");
            } else {
                // Set Connected Device hardware type (this is likely to occur after onBonded)
                ConnectedDevice mb = BluetoothUtils.getPairedMicrobit(getContext());
                mb.mhardwareVersion = hardwareType;
                BluetoothUtils.setPairedMicroBit(getContext(), mb);
            }

            return true;
        }

        @Override
        protected void initialize() {
            super.initialize();
            ensureBond().enqueue();
        }

        @Override
        protected void onDeviceDisconnected() {
            // Do nothing
        }
    }
}
