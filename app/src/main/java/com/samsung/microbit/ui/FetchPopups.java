package com.samsung.microbit.ui;

import android.content.Context;
import android.util.Log;
import android.view.View;
import com.samsung.microbit.R;

import static com.samsung.microbit.BuildConfig.DEBUG;

public class FetchPopups {
    private static final String TAG = FetchPopups.class.getSimpleName();

    public void logi(String message) {
        if(DEBUG) {
            Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
        }
    }

    /**
     * Callback interface for client
     */
    public interface Client {
        Context fetchPopupsContext();
        void fetchPopupsCancelled();
        void fetchPopupsRequestBluetoothConnectPermissions();
    }

    Client mClient = null;

    public FetchPopups ( Client client) {
        mClient = client;
    }

    View.OnClickListener popupClickActivityCancelled = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("popupClickActivityCancelled");
            PopUp.hide();
            mClient.fetchPopupsCancelled();
        }
    };

    public void busy() {
        // Another download session is in progress.xml
        PopUp.show(mClient.fetchPopupsContext().getString(R.string.multple_flashing_session_msg),
                "",
                R.drawable.error_face, R.drawable.blue_btn,
                PopUp.GIFF_ANIMATION_FLASH,
                PopUp.TYPE_ALERT,
                popupClickActivityCancelled, popupClickActivityCancelled);
    }

    public void bluetoothOff() {
        PopUp.show(mClient.fetchPopupsContext().getString(R.string.bluetooth_off_cannot_continue), //message
                "",
                R.drawable.error_face, R.drawable.red_btn,
                PopUp.GIFF_ANIMATION_ERROR,
                PopUp.TYPE_ALERT,
                popupClickActivityCancelled, popupClickActivityCancelled);
    }

    public void bluetoothConnectPermissionError() {
        PopUp.show(mClient.fetchPopupsContext().getString(R.string.ble_permission_connect_error),
                mClient.fetchPopupsContext().getString(R.string.permissions_needed_title),
                R.drawable.error_face, R.drawable.red_btn,
                PopUp.GIFF_ANIMATION_ERROR,
                PopUp.TYPE_ALERT,
                popupClickActivityCancelled, popupClickActivityCancelled);
    }

    public void bluetoothConnectRequest() {
        PopUp.show(mClient.fetchPopupsContext().getString(R.string.ble_permission_connect),
                mClient.fetchPopupsContext().getString(R.string.permissions_needed_title),
                R.drawable.message_face, R.drawable.blue_btn, PopUp.GIFF_ANIMATION_NONE,
                PopUp.TYPE_CHOICE,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        logi("bluetoothPermissionOKHandler");
                        PopUp.hide();
                        mClient.fetchPopupsRequestBluetoothConnectPermissions();
                    }
                },
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        logi("bluetoothPermissionCancelHandler");
                        PopUp.hide();
                        bluetoothConnectPermissionError();
                    }
                });
    }

    public void fetchFailed( String message) {
        PopUp.show( message,
                mClient.fetchPopupsContext().getString(R.string.fetchFailed),
                R.drawable.error_face, R.drawable.red_btn,
                PopUp.GIFF_ANIMATION_ERROR,
                PopUp.TYPE_ALERT,
                popupClickActivityCancelled, popupClickActivityCancelled);
    }

    public void fetchProgress() {
        PopUp.show("",
                mClient.fetchPopupsContext().getString(R.string.fetching),
                R.drawable.flash_face,
                R.drawable.blue_btn,
                PopUp.GIFF_ANIMATION_FLASH,
                PopUp.TYPE_PROGRESS,
                popupClickActivityCancelled, popupClickActivityCancelled);
    }

}
