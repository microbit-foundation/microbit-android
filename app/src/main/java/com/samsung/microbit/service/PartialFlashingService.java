package com.samsung.microbit.service;

import android.app.Activity;

import com.samsung.microbit.ui.activity.NotificationActivity;

import org.microbit.android.partialflashing.PartialFlashingBaseService;

public class PartialFlashingService extends PartialFlashingBaseService {

    @Override
    protected Class<? extends Activity> getNotificationTarget() {
        return NotificationActivity.class;
    }

    @Override
    protected boolean isDebug() {
        return com.samsung.microbit.BuildConfig.DEBUG;
    }
}
