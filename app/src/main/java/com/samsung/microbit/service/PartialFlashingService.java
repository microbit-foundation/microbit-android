package com.samsung.microbit.service;

import android.app.Activity;

import org.microbit.android.partialflashing.PartialFlashingBaseService;

import com.samsung.microbit.ui.activity.NotificationActivity;

public class PartialFlashingService extends PartialFlashingBaseService {

    @Override
    protected Class<? extends Activity> getNotificationTarget() {
        return NotificationActivity.class;
    }
}
