package com.samsung.microbit;

import static com.samsung.microbit.BuildConfig.DEBUG;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.samsung.microbit.MBApp;
import com.samsung.microbit.data.constants.Constants;
import com.samsung.microbit.data.model.ConnectedDevice;

public class MBAppState {
    private static final String TAG = MBAppState.class.getSimpleName();
    
    public void logi(String message) {
        if ( DEBUG) {
            Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
        }
    }

    public enum PairState {
        PairStateNone,
        PairStateError,
        PairStateLaunch,
        PairStateSession,
        PairStateChecked
    }

    public boolean pairStateLaunch;
    public boolean pairStateSession;
    public boolean pairStateError;
    public boolean pairStateMakeCode;
    public boolean pairStateResetTriple;

    public MBAppState() {
        pairStateLaunch = true;
        pairStateSession = true;
        pairStateError = false;
        pairStateMakeCode = false;
        pairStateResetTriple = true;
    }

    public PairState pairState( ConnectedDevice device) {
        PairState ps = PairState.PairStateChecked;

        if ( device == null || device.mPattern == null)
            ps = PairState.PairStateNone;
        else if ( pairStateError)
            ps = PairState.PairStateError;
        else if ( pairStateLaunch)
            ps = PairState.PairStateLaunch;
        else if ( pairStateSession)
            ps = PairState.PairStateSession;
        else
            ps = PairState.PairStateChecked;

        logi( "pairState " + ps);
        return ps;
    }

    public void eventPairSuccess() {
        logi( "eventPairSuccess");
        pairStateLaunch = false;
        pairStateSession = false;
        pairStateError = false;
        prefsSave();
    }

    public void eventPairChecked() {
        logi( "eventPairChecked");
        pairStateLaunch = false;
        pairStateSession = false;
    }

    public void eventPairDifferent() {
        logi( "eventPairDifferent");
        pairStateSession = true;
    }

    public void eventPairBackground() {
        logi( "eventPairBackground");
        if ( !pairStateMakeCode)
            pairStateSession = true;
    }

    public void eventPairForeground() {
        logi( "eventPairForeground");
        if ( !pairStateMakeCode)
            pairStateSession = true;
    }

    public void eventPairError() {
        logi( "eventPairError");
        pairStateError = true;
        prefsSave();
    }

    public void eventPairMakeCodeBegin() {
        logi( "eventPairMakeCodeBegin");
        pairStateSession = true;
        pairStateMakeCode = true;
    }

    public void eventPairMakeCodeEnd() {
        logi( "eventPairMakeCodeEnd");
        pairStateMakeCode = false;
    }

    public void eventPairSendError() {
        logi( "eventPairSendError");
        pairStateError = true;
        prefsSave();
    }

    public void eventPairResetTriple( boolean yes)
    {
        logi( "eventPairResetTriple " + yes);
        pairStateResetTriple = yes;
        prefsSave();
    }

    public void eventPairResetMethodCheck( ConnectedDevice device)
    {
        logi( "eventPairResetMethodCheck");
        if ( device != null && device.mPattern != null)
        {
            if ( device.mhardwareVersion == 1) // MICROBIT_V1
            {
                eventPairResetTriple( false);
                return;
            }
        }
        eventPairResetTriple( true);
    }

    public static final String PREFERENCES_pairStateError = "Preferences.pairStateError";
    public static final String PREFERENCES_pairStateResetTriple = "Preferences.pairStateResetTriple";

    public void prefsLoad()
    {
        Context ctx = MBApp.getApp();
        SharedPreferences prefs = ctx.getSharedPreferences(Constants.PREFERENCES, Context.MODE_MULTI_PROCESS);
        if ( prefs == null) {
            logi( "prefsLoad failed");
            return;
        }
        pairStateError          = prefs.getBoolean( PREFERENCES_pairStateError, pairStateError);
        pairStateResetTriple    = prefs.getBoolean( PREFERENCES_pairStateResetTriple, pairStateResetTriple);
    }

    public void prefsSave()
    {
        Context ctx = MBApp.getApp();
        SharedPreferences prefs = ctx.getSharedPreferences(Constants.PREFERENCES, Context.MODE_MULTI_PROCESS);
        SharedPreferences.Editor prefsEdit = prefs == null ? null: prefs.edit();
        if ( prefsEdit == null) {
            logi( "prefsSave failed");
            return;
        }
        prefsEdit.putBoolean( PREFERENCES_pairStateError, pairStateError);
        prefsEdit.putBoolean( PREFERENCES_pairStateResetTriple, pairStateResetTriple);
        prefsEdit.apply();
    }
}
