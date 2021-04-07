package com.samsung.microbit.service;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.samsung.microbit.ui.activity.NotificationActivity;

import org.microbit.android.partialflashing.PartialFlashingBaseService;

public class PartialFlashingService extends PartialFlashingBaseService {

    private static final String TAG = PartialFlashingService.class.getSimpleName();

    public PartialFlashingService() {
        super();
    }

    @Override
    protected Class<? extends Activity> getNotificationTarget() {
        return NotificationActivity.class;
    }

    @Override
    public void onDeviceConnecting(@NonNull BluetoothDevice device) {
        Log.v(TAG, "onDeviceConnecting");
    }

    @Override
    public void onDeviceConnected(@NonNull BluetoothDevice device) {
        Log.v(TAG, "onDeviceConnected");
    }

    @Override
    public void onDeviceFailedToConnect(@NonNull BluetoothDevice device, int reason) {
        Log.v(TAG, "onDeviceFailedToConnect");
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(BROADCAST_PF_ATTEMPT_DFU));
    }

    @Override
    public void onDeviceReady(@NonNull BluetoothDevice device) {

    }

    @Override
    public void onDeviceDisconnecting(@NonNull BluetoothDevice device) {

    }

    @Override
    public void onDeviceDisconnected(@NonNull BluetoothDevice device, int reason) {
        Log.v(TAG, "onDeviceDisconnected");
    }
}
