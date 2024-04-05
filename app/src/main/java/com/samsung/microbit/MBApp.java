package com.samsung.microbit;

import static com.samsung.microbit.BuildConfig.DEBUG;

import android.app.Application;
import android.graphics.Typeface;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

/**
 * Represents a custom class of the app.
 * Provides some resources that use along app modules,
 * such as app context, font styles and etc.
 */
public class MBApp extends Application implements DefaultLifecycleObserver {
    private static final String TAG = MBApp.class.getSimpleName();

    public void logi(String message) {
        if ( DEBUG) {
            Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
        }
    }

    private static MBApp app = null;

    private Typeface mTypeface;
    private Typeface mBoldTypeface;
    private Typeface mRobotoTypeface;

    private boolean justPaired;

    @Override
    public void onCreate() {
        super.onCreate();
        app = this;
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        initTypefaces();

        Log.d("MBApp", "App Created");
    }

    /**
     * Creates font styles from the assets and initializes typefaces.
     */
    private void initTypefaces() {
        mTypeface = Typeface.createFromAsset(getAssets(), "fonts/GT-Walsheim.otf");
        mBoldTypeface = Typeface.createFromAsset(getAssets(), "fonts/GT-Walsheim-Bold.otf");
        mRobotoTypeface = Typeface.createFromAsset(getAssets(), "fonts/Roboto-Regular.ttf");
    }

    public void setJustPaired(boolean justPaired) {
        this.justPaired = justPaired;
    }

    public boolean isJustPaired() {
        return justPaired;
    }

    public Typeface getTypeface() {
        return mTypeface;
    }

    public Typeface getTypefaceBold() {
        return mBoldTypeface;
    }

    public Typeface getRobotoTypeface() {
        return mRobotoTypeface;
    }

    public static MBApp getApp() {
        return app;
    }

    @Override
    public void onCreate(@NonNull LifecycleOwner owner) {
        logi("onCreate");
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        logi("onStart");
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        logi("onResume");
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        logi("onPause");
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        logi("onStop");
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        logi("onDestroy");
    }
}
