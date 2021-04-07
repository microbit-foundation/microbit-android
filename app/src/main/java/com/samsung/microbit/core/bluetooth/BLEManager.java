package com.samsung.microbit.core.bluetooth;

import android.bluetooth.BluetoothGatt;
import android.content.Context;

import androidx.annotation.NonNull;

import no.nordicsemi.android.ble.BleManager;

class BLEManager extends BleManager {

    public BLEManager(@NonNull Context context) {
        super(context);
    }

    @NonNull
    @Override
    protected BleManagerGattCallback getGattCallback() {
        return new BLEManagerGattCallback();
    }

    private class BLEManagerGattCallback extends BleManagerGattCallback {

        @Override
        protected boolean isRequiredServiceSupported(@NonNull BluetoothGatt gatt) {
            return true;
        }

        @Override
        protected void initialize() {
            ensureBond().enqueue();
            disconnect().enqueue();
            super.initialize();
        }

        @Override
        protected void onDeviceDisconnected() {

        }
    }
}