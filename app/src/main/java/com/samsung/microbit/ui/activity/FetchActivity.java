package com.samsung.microbit.ui.activity;

import static com.samsung.microbit.BuildConfig.DEBUG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BulletSpan;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import com.samsung.microbit.MBApp;
import com.samsung.microbit.MBAppState;
import com.samsung.microbit.R;
import com.samsung.microbit.core.bluetooth.BluetoothUtils;
import com.samsung.microbit.data.constants.PermissionCodes;
import com.samsung.microbit.data.constants.RequestCodes;
import com.samsung.microbit.ui.BluetoothChecker;
import com.samsung.microbit.ui.FetchPopups;
import com.samsung.microbit.ui.PopUp;
import com.samsung.microbit.ui.UIUtils;
import com.samsung.microbit.ui.view.PatternDrawable;
import com.samsung.microbit.utils.BLEFetch;
import com.samsung.microbit.utils.FileUtils;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * Represents the Flash screen that contains a list of project samples
 * and allows to flash them to a micro:bit or remove them from the list.
 */
public class FetchActivity extends Activity implements View.OnClickListener, UIUtils.Client, FetchPopups.Client, BLEFetch.Client {
    private static final String TAG = FetchActivity.class.getSimpleName();

    public void logi(String message) {
        if (DEBUG) {
            Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
        }
    }

    private static final int FETCH_REQUEST_CODE_EXPORT = 1;
    private static final int FETCH_REQUEST_CODE_IMPORT = 2;
    private static final int FETCH_REQUEST_CODE_RESET_TO_BLE = 3;
    private static final int FETCH_REQUEST_CODE_PAIR = 4;
    private static final int FETCH_REQUEST_CODE_PAIR_ALREADY_RESET = 5;

    enum ePurpose {
        None,
        Select,
        Check,
        Pair,
        Fetch,
        Connect,
        Data,
        Error
    }

    enum eFetchChoice {
        After,
        During
    }

    ePurpose mPurpose = ePurpose.Select;
    eFetchChoice mFetchChoice = eFetchChoice.After;
    boolean pairSuccess = false;

    private WebView mWebView;
    private FetchWebJS mWebJavascriptInterface = null;

    private static final int REQUEST_CODE_SAVEDATA = 1;
    private static final int REQUEST_CODE_CHOOSE_FILE = 2;
    private static final int REQUEST_CODE_FLASH = 3;
    private byte[] dataToSave = null;
    private ValueCallback<Uri[]> mWebFileChooserCallback;

    /**
     * Helper - UIUtils
     */

    UIUtils mui = new UIUtils(this);

    @Override
    public Activity uiUtilsActivity() {
        return this;
    }

    /**
     * Helper - FetchPopups
     */

    FetchPopups mPopups = new FetchPopups(this);

    @Override
    public Context fetchPopupsContext() {
        return this;
    }

    @Override
    public void fetchPopupsCancelled() {
        activityCancelled();
    }

    @Override
    public void fetchPopupsRequestBluetoothConnectPermissions() {
        requestPermissions();
    }

    /**
     * Helper - BLEFetch
     */

    private BLEFetch mFetch = new BLEFetch(this);

    @Override
    public Activity bleFetchGetActivity() {
        return this;
    }

    @Override
    public String bleFetchGetDeviceAddress() {
        return BluetoothUtils.getCurrentMicrobit(this).mAddress;
    }

    @Override
    public void bleFetchProgress(float progress) {
        runOnUiThread( new Runnable() {
            @Override
            public void run() {
                PopUp.updateProgressBar((int) (progress * 100));
            }
        });
    }

    @Override
    public void bleFetchState() {
        runOnUiThread( new Runnable() {
            @Override
            public void run() {
                switch (mFetch.resultState) {
                    case None:
                    case Found:
                    case Connected:
                        break;
                    case Discovered:
                        mPopups.fetchProgress();
                        break;
                    case ConnectTimeout:
                        PopUp.hide();
                        purposeError(getString(R.string.fetch_connection_failed));
                        break;
                    case WorkTimeout:
                        PopUp.hide();
                        purposeError(getString(R.string.fetch_connection_lost));
                        break;
                    case NotBonded:
                        if ( mFetchChoice == eFetchChoice.During) {
                            PopUp.hide();
                            purposeError(getString(R.string.fetch_not_paired));
                        } else {
                            PopUp.hide();
                            purposePairWithReset();
                        }
                        break;
                    case Error:
                        PopUp.hide();
                        switch (mFetch.mWorkResult) {
                            default:
                            case None:
                                if ( BluetoothUtils.getCurrentMicrobitIsDefinitelyNotInPairedList( MBApp.getApp())) {
                                    if ( mFetchChoice == eFetchChoice.During) {
                                        purposeError(getString(R.string.fetch_not_paired));
                                    } else {
                                        purposePairWithReset();
                                    }
                                } else {
                                    purposeError(getString(R.string.fetch_connection_interrupted));
                                }
                                break;
                            case BleError:
                                purposeError(getString(R.string.fetch_connection_broken));
                                break;
                            case V2Only:
                                purposeError(getString(R.string.fetch_works_with_micro_bit_v2_only));
                                break;
                            case NoService:
                                purposeError(getString(R.string.fetch_update_the_project_to_add_the_bluetooth_service));
                                break;
                            case Protocol:
                                purposeError(getString(R.string.fetch_connection_error));
                                break;
                            case NoData:
                                purposeError(getString(R.string.fetch_no_data_in_micro_bit));
                                break;
                            case OutOfMemory:
                                purposeError(getString(R.string.fetch_out_of_memory));
                                break;
                        }
                        break;
                    case Success:
                        PopUp.hide();
                        purposeData();
                        break;
                }
            }
        });
    }

    /**
     * Activity overrides
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        logi("onCreate");
        super.onCreate(savedInstanceState);

        displayCreate();

        purposeStart();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        logi("onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
        displayConfigurationChanged(newConfig);
    }

    @Override
    protected void onDestroy() {
        logi("onDestroy");
        super.onDestroy();
        displayDestroy();
    }

    @Override
    protected void onStart() {
        logi("onStart");
        super.onStart();
    }

    @Override
    protected void onStop() {
        logi("onStop");
        super.onStop();
    }

    @Override
    protected void onResume() {
        logi("onResume");
        super.onResume();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        purposeStart();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case FETCH_REQUEST_CODE_RESET_TO_BLE:
                if (resultCode == RESULT_OK) {
                    if ( mFetchChoice == eFetchChoice.During) {
                        switch ( pairState()) {
                            case PairStateNone:
                                activityCancelled();
                                break;
                            case PairStateError:
                            case PairStateLaunch:
                            case PairStateSession:
                            case PairStateChecked:
                            default:
                                purposeConnect();
                                break;
                        }
                    } else {
                        switch ( pairState()) {
                            case PairStateNone:
                            case PairStateError:
                                activityCancelled();
                                break;
                            case PairStateLaunch:
                            case PairStateSession:
                                purposeCheck();
                                break;
                            case PairStateChecked:
                            default:
                                purposeConnect();
                                break;
                        }
                    }
                } else {
                    activityCancelled();
                }
                break;

            case FETCH_REQUEST_CODE_PAIR:
            case FETCH_REQUEST_CODE_PAIR_ALREADY_RESET:
                if (resultCode == RESULT_OK) {
                    connectWithChecksAfterPairing();
                } else {
                    activityCancelled();
                }
                break;

            case RequestCodes.REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                    connectWithChecksAfterBlePermissionGrantedAndBleEnabled();
                } else if (resultCode == Activity.RESULT_CANCELED) {
                    mPopups.bluetoothOff();
                }
                break;
            case REQUEST_CODE_SAVEDATA:
                if (resultCode != RESULT_OK) {
                    dataToSave = null;
                    return;
                }
                if (dataToSave != null && dataToSave.length > 0) {
                    Uri uri = data.getData();
                    if (!FileUtils.writeBytesToUri(uri, dataToSave, this)) {
                        Toast.makeText(this, "Could not save file", Toast.LENGTH_LONG).show();
                    }
                }
                dataToSave = null;
                break;
            case REQUEST_CODE_CHOOSE_FILE:
                if ( resultCode != RESULT_OK) {
                    mWebFileChooserCallback.onReceiveValue( null);
                    return;
                }
                Uri[] uris = WebChromeClient.FileChooserParams.parseResult ( resultCode, data);
                mWebFileChooserCallback.onReceiveValue( uris);
                break;
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        requestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * Calling other activities
     */

    boolean activityIsBusy() {
        return false;
    }

    void activityCancelled() {
        mFetch.fetchCancel();
        setResult(RESULT_CANCELED);
        finish();
    }

    private void activityComplete() {
        mFetch.fetchCancel();
        setResult(RESULT_OK);
        finish();
    }

    void activityRestart() {
    }

    private void goToPairingToPair() {
        Intent i = new Intent(this, PairingActivity.class);
        i.setAction(PairingActivity.ACTION_PAIR_BEFORE_FLASH);
        startActivityForResult(i, FETCH_REQUEST_CODE_PAIR);
    }

    private void goToPairingToPairAlreadyReset() {
        Intent i = new Intent(this, PairingActivity.class);
        i.setAction(PairingActivity.ACTION_PAIR_BEFORE_FLASH_ALREADY_RESET);
        startActivityForResult(i, FETCH_REQUEST_CODE_PAIR_ALREADY_RESET);
    }

    private void goToPairingResetToBLE() {
        Intent i = new Intent(this, PairingActivity.class);
        i.setAction(PairingActivity.ACTION_RESET_TO_BLE);
        startActivityForResult(i, FETCH_REQUEST_CODE_RESET_TO_BLE);
    }

    private void pairBeforeFlashFinish(int resultCode) {
        setResult(resultCode);
        finish();
    }

    /**
     * Clicks
     */

    @Override
    public void onBackPressed() {
        if ( mPurpose == ePurpose.Data) {
            displayHtmlGoBack();
        } else {
            activityCancelled();
        }
    }

    @Override
    public void onClick(final View v) {
        switch (v.getId()) {
            case R.id.fetchWebBarBack:
                onBackPressed();
                break;

            case R.id.fetchSelectChoiceAfter:
                mFetchChoice = eFetchChoice.After;
                displayUpdateControls();
                break;

            case R.id.fetchSelectChoiceDuring:
                mFetchChoice = eFetchChoice.During;
                displayUpdateControls();
                break;

            case R.id.fetchSelectOK:
                onClickSelectOK();
                break;

            case R.id.fetchSelectCancel:
            case R.id.viewProjectsPatternCancel:
                activityCancelled();
                break;

            case R.id.viewProjectsPatternDifferent:
            case R.id.viewProjectsSearchingDifferent:
                MBApp.getAppState().eventPairDifferent();
                displayShowSearchingDifferent(false);
                purposePairWithoutReset();
                break;

            case R.id.viewProjectsPatternOK:
                MBApp.getAppState().eventPairChecked();
                purposeFetch();
                break;

            case R.id.fetchSelectDuringMore:
                UIUtils.safelyStartActivityViewURL( this, true, getString(R.string.fetchDuringFindOutMoreUrl));
                break;
        }
    }

    private void onClickSelectOK() {
        if ( mFetchChoice == eFetchChoice.During) {
            switch ( pairState()) {
                case PairStateNone:
                    purposeError( getString( R.string.fetch_not_paired));
                    break;
                case PairStateError:
                case PairStateLaunch:
                case PairStateSession:
                case PairStateChecked:
                default:
                    purposeFetch();
                    break;
            }
        } else {
            switch ( pairState()) {
                case PairStateNone:
                case PairStateError:
                    purposePairWithoutReset();
                    break;
                case PairStateLaunch:
                case PairStateSession:
                    purposeCheck();
                    break;
                case PairStateChecked:
                default:
                    purposeFetch();
                    break;
            }
        }
    }

    /**
     * Activity modes
     */

    private void purposeStart() {
        if (activityIsBusy()) {
            mPopups.busy();
            return;
        }
        purposeSelect();
    }


    private void purposeSelect() {
        logi("purposeSelect");
        mPurpose = ePurpose.Select;
        mFetch.fetchCancel();
        displayUpdateControls();
    }

    private void purposePairWithReset() {
        logi("purposePairWithReset");
        mPurpose = ePurpose.Pair;
        mFetch.fetchCancel();
        displayUpdateControls();
        goToPairingToPair();
    }

    private void purposePairWithoutReset() {
        logi("purposePairWithoutReset");
        mPurpose = ePurpose.Pair;
        mFetch.fetchCancel();
        displayUpdateControls();
        goToPairingToPairAlreadyReset();
    }

    private void purposeCheck() {
        logi("purposeCheck");
        mPurpose = ePurpose.Check;
        mFetch.fetchCancel();
        displayUpdateControls();
    }

    private void purposeFetch() {
        logi("purposeFetch");
        mPurpose = ePurpose.Fetch;
        displayUpdateControls();
        connectWithChecks();
    }

    private void purposeConnect() {
        logi("purposeConnect");
        mPurpose = ePurpose.Connect;
        displayUpdateControls();
        mFetch.fetchStart();
    }

    private void purposeData() {
        logi("purposeData");

        mFetch.fetchCancel();

        if ( displayHtmlSave( mFetch.fetchData())) {
            mPurpose = ePurpose.Data;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    displayHtmlLoad();
                    displayUpdateControls();
                }
            });
        } else {
            purposeError(getString(R.string.fetch_failed_to_create_my_data_htm));
        }
    }

    private void purposeError(String message) {
        logi("purposeError");
        mFetch.fetchCancel();
        MBApp.getAppState().eventPairSendError();
        mPurpose = ePurpose.Error;
        mPopups.fetchFailed(message);
    }

    /**
     * Connect with checks for permissions, BLE enabled etc.
     */

    private boolean connectWithChecks() {
        Log.v(TAG, "connectWithChecks");

        if (activityIsBusy()) {
            mPopups.busy();
            return false;
        }

        if (havePermissions()) {
            if (BluetoothChecker.getInstance().isBluetoothON()) {
                connectWithChecksAfterBlePermissionGrantedAndBleEnabled();
                return true;
            }
            enableBluetooth();
        } else {
            mPopups.bluetoothConnectRequest();
        }
        return false;
    }

    private void connectWithChecksAfterBlePermissionGranted() {
        if (!BluetoothChecker.getInstance().isBluetoothON()) {
            enableBluetooth();
            return;
        }
        connectWithChecksAfterBlePermissionGrantedAndBleEnabled();
    }

    private void connectWithChecksAfterBlePermissionGrantedAndBleEnabled() {
        if ( mFetchChoice == eFetchChoice.During) {
            switch ( pairState()) {
                case PairStateNone:
                    PopUp.hide();
                    purposeError( getString( R.string.fetch_not_paired));
                    break;
                case PairStateError:
                case PairStateLaunch:
                case PairStateSession:
                case PairStateChecked:
                default:
                    purposeConnect();
                    break;
            }
        } else {
            switch ( pairState()) {
                default:
                case PairStateNone:
                case PairStateError:
                    //showPopupQuestionFlashToDevice( true);
                    purposePairWithoutReset();
                    break;
                case PairStateLaunch:
                case PairStateSession:
                    purposeCheck();
                    break;
                case PairStateChecked:
                    purposeConnect();
                    break;
            }
        }
    }

    private void connectWithChecksAfterPairing() {
        goToPairingResetToBLE();
    }

    private MBAppState.PairState pairState() {
//        if ( BluetoothUtils.getCurrentMicrobitIsDefinitelyNotInPairedList( MBApp.getApp())) {
//            return MBAppState.PairState.PairStateNone;
//        }
        return MBApp.getAppState().pairState();
    }

    /**
     * Permissions, enable BLE, etc.
     */

    @SuppressLint("MissingPermission")
    private void enableBluetooth() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        int error = UIUtils.safelyStartActivityForResult( this, false, enableBtIntent, RequestCodes.REQUEST_ENABLE_BT);
        if ( error != 0) {
            mPopups.bluetoothEnableRestricted();
        }
    }

    private boolean havePermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PermissionChecker.PERMISSION_GRANTED;
    }

    private boolean havePermissions() {
        boolean yes = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!havePermission(Manifest.permission.BLUETOOTH_CONNECT))
                yes = false;
        }
        return yes;
    }

    private void requestPermission(String[] permissions, final int requestCode) {
        ActivityCompat.requestPermissions(this, permissions, requestCode);
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] permissionsNeeded = {
                    Manifest.permission.BLUETOOTH_CONNECT};
            requestPermission(permissionsNeeded, PermissionCodes.BLUETOOTH_PERMISSIONS_REQUESTED_FLASHING_API31);
        }
    }

    private void requestPermissionsResult(int requestCode,
                                          @NonNull String permissions[],
                                          @NonNull int[] grantResults) {
        if (havePermissions()) {
            connectWithChecksAfterBlePermissionGranted();
            return;
        }

        switch (requestCode) {
            case PermissionCodes.BLUETOOTH_PERMISSIONS_REQUESTED_FLASHING_API31: {
                mPopups.bluetoothConnectPermissionError();
                break;
            }
        }
    }

    /**
     * Display
     */

    private void displayCreate() {
        //Remove title bar
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.fetch_main);
        displayHtmlDelete();
        displayInit();
    }

    public void displayConfigurationChanged(Configuration newConfig) {
        setContentView(R.layout.fetch_main);
        displayInit();
    }

    private void displayDestroy() {
        displayHtmlDelete();
    }

    private void displayInit() {
        displayHtmlInit();
        displayUpdate();
    }

    private void displayUpdate() {
        displayUpdateControls();
        displayFonts();
    }

    private void displayFonts() {
        MBApp application = MBApp.getApp();
        Typeface textTypeface = application.getTypeface();
        Typeface buttonTypeface = application.getRobotoTypeface();

        ViewGroup parent = findViewById(R.id.fetchMain);
        mui.setTypefaces(parent, buttonTypeface, textTypeface);
        mui.setFontSizes(parent, mui.buttonFontSize(), mui.labelFontSize());

        mui.setButtonClicks(parent, this);

        Typeface headerTypeface = MBApp.getApp().getTypefaceBold();
        float headerFontSize = mui.headerFontSize();

        mui.setTypeface(R.id.fetchSelectTitle, headerTypeface);
        mui.setTypeface(R.id.fetchSelectAfterTitle, headerTypeface);
        mui.setTypeface(R.id.viewProjectsPatternHeader, headerTypeface);
        mui.setTypeface(R.id.viewProjectsSearchingHeader, headerTypeface);

        mui.setFontSize(R.id.fetchSelectTitle, headerFontSize);
        mui.setFontSize(R.id.fetchSelectAfterTitle, headerFontSize);
        mui.setFontSize(R.id.viewProjectsPatternHeader, headerFontSize);
        mui.setFontSize(R.id.viewProjectsSearchingHeader, headerFontSize);

        displayShowSearchingDifferent(true);
    }

    private void displayShowSearchingDifferent(boolean show) {
        mui.setVisibility(R.id.viewProjectsSearchingDifferent, show ? View.VISIBLE : View.INVISIBLE);
    }

    private void displaySetDuringText() {
        MBApp application = MBApp.getApp();
        Typeface textTypeface = application.getTypeface();
        TextView textView = (TextView) findViewById(R.id.fetchSelectDuringText);
        if (textView == null) {
            return;
        }
        SpannableString text0 = new SpannableString(getString(R.string.fetchSelectDuring0));
        SpannableString text1 = new SpannableString(getString(R.string.fetchSelectDuring1));
        SpannableString text2 = new SpannableString(getString(R.string.fetchSelectDuring2));
        SpannableString nl = new SpannableString("\n");

        text1.setSpan(new BulletSpan(), 0, text1.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        text2.setSpan(new BulletSpan(), 0, text2.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

        SpannableStringBuilder ss = new SpannableStringBuilder(text0);
        ss.append(nl);
        ss.append(nl);
        ss.append(text1);
        ss.append(nl);
        ss.append(nl);
        ss.append(text2);
        ss.append(nl);
        textView.setText(ss);
    }

    private void displayUpdateControls() {
        displaySetDuringText();
        displayUpdateDeviceName();

        boolean web = mPurpose == ePurpose.Data;
        boolean select = mPurpose == ePurpose.Select || mPurpose == ePurpose.Fetch || mPurpose == ePurpose.None;
        boolean pattern = mPurpose == ePurpose.Check;
        boolean searching = mPurpose == ePurpose.Connect;

        mui.setVisible(R.id.fetchWeb, web);
        mui.setVisible(R.id.fetchSelect, select);
        mui.setVisible(R.id.viewProjectsPattern, pattern);
        mui.setVisible(R.id.viewProjectsSearching, searching);

        Drawable yes = AppCompatResources.getDrawable(this, R.drawable.white_btn);
        Drawable no = AppCompatResources.getDrawable(this, R.drawable.fetch_select_gray);
        switch (mFetchChoice) {
            case After:
                mui.setBackground(R.id.fetchSelectChoiceAfter, yes);
                mui.setBackground(R.id.fetchSelectChoiceDuring, no);
                mui.gifAnimate(R.id.fetchSelectAfterGif);
                mui.setVisible(R.id.fetchSelectAfter, true);
                mui.setVisible(R.id.fetchSelectDuring, false);
                break;
            case During:
                mui.setBackground(R.id.fetchSelectChoiceAfter, no);
                mui.setBackground(R.id.fetchSelectChoiceDuring, yes);
                mui.setVisible(R.id.fetchSelectDuring, true);
                mui.setVisible(R.id.fetchSelectAfter, false);
                break;
        }
    }

    private void displayUpdateDeviceName() {
        String deviceName = "     ";
        if (havePermissions()) {
            String pattern = BluetoothUtils.getCurrentMicrobit(this).mPattern;
            if (pattern != null) {
                deviceName = pattern;
            }
        }

        PatternDrawable patternDrawable = new PatternDrawable();
        ImageView patternGrid = (ImageView) findViewById(R.id.viewProjectsPatternGrid);
        TextView patternHeader = (TextView) findViewById(R.id.viewProjectsPatternHeader);
        patternGrid.setImageDrawable(patternDrawable);
        String headerText = getResources().getString(R.string.compare_your_pattern_with_NAME, deviceName);
        patternHeader.setText(headerText);
        patternDrawable.setDeviceName(deviceName);

        PatternDrawable searchDrawable = new PatternDrawable();
        ImageView searchGrid = (ImageView) findViewById(R.id.viewProjectsSearchingGrid);
        TextView searchHeader = (TextView) findViewById(R.id.viewProjectsSearchingHeader);
        searchGrid.setImageDrawable(searchDrawable);

        String searchText = getResources().getString(R.string.searching_for_microbit_NAME, deviceName);
        searchHeader.setText(searchText);
        searchDrawable.setDeviceName(deviceName);
    }

    private void openURL( String url) {
        logi( "openURL: " + url);
        UIUtils.safelyStartActivityViewURL( this, true, url);
    }

    /**
     * Display WebView
     */

    private void displayHtmlInit() {
        mWebView = findViewById(R.id.fetchWebView);
        if (mWebView == null) {
            return;
        }

        mWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        WebView.setWebContentsDebuggingEnabled(false);
        mWebJavascriptInterface = new FetchWebJS(this);
        mWebView.addJavascriptInterface(mWebJavascriptInterface, "AndroidFunction");

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                logi( "shouldOverrideUrlLoading: " + url);
                Uri uri = Uri.parse( url);
                String scheme = uri.getScheme();
                if ( scheme != null) {
                    logi( "scheme: " + scheme);
                    if ( scheme.compareToIgnoreCase( "file") == 0) {
                        String path = displayHtmlGetPath().toLowerCase();
                        if ( url.toLowerCase().contains( path)) {
                            return false;
                        }
                        openURL( url);
                        return true;
                    }
                }
                openURL( url);
                return true;
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
                Log.v(TAG, "onLoadResource(" + url + ");");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.v(TAG, "onPageFinished(" + url + ");");
                //onPageFinishedJS( view, url);
            }
        }); //setWebViewClient

        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
                return onShowFileChooser( webView, filePathCallback, fileChooserParams);
            }
        }); //setWebChromeClient

        mWebView.setDownloadListener(new DownloadListener() {
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimetype,
                                        long contentLength) {
                try {
                    if (url.startsWith("blob:")) {
                        onDownloadBlob(url);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }); // setDownloadListener

        displayHtmlLoad();
    }

    class FetchWebJS {
        FetchActivity mContext;

        FetchWebJS( FetchActivity c) {
            mContext = c;
        }

        @JavascriptInterface
        public void onDownloadBase64( String base64, String mimetype) {
            mContext.onDownloadBase64( base64, mimetype);
        }
    }

    private boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
        mWebFileChooserCallback = filePathCallback;
        try {
            Intent intent = fileChooserParams.createIntent();
            int error = UIUtils.safelyStartActivityForResult( this, true, intent, REQUEST_CODE_CHOOSE_FILE);
            if ( error != 0) {
                mWebFileChooserCallback.onReceiveValue( null);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void onDownloadBlob( String blob) {
        String js = "javascript:("
                + "function f() {"
                +   "var ret = 'OK';"
                +   "try {"
                +       "var iaHttp = new XMLHttpRequest();"
                +       "iaHttp.open('GET', '" + blob + "', true);"
                +       "iaHttp.responseType = 'blob';"
                +       "iaHttp.onload = function() {"
                +           "if (this.status == 200) {"
                +               "var blob = this.response;"
                +               "var type = blob.type;"
                +               "var rdr = new window.FileReader();"
                +               "rdr.onloadend = function() {"
                +                   "var b64 = btoa( rdr.result);"
                +                   "AndroidFunction.onDownloadBase64( b64, type);"
                +               "};"
                +               "rdr.readAsBinaryString(blob);"
                +           "}"
                +       "};"
                +       "iaHttp.send();"
                +   "} catch( err) {"
                +       "ret = err.message;"
                +   "}"
                +   "return ret;"
                + "}"
                + ")();";

        mWebView.evaluateJavascript( js, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String s) {
                logi( "onDownloadBlob " + s);
            }
        });
    }

    public void onDownloadBase64( String base64, String mimetype) {
        logi( "onDownloadBase64");
        byte[] bytes = Base64.decode(base64,Base64.DEFAULT);
        if ( mimetype.equals( "text/plain"))
            mimetype = "text/csv";
        String fileName = "microbit.csv";
        saveData( fileName, mimetype, bytes);
    }

    private void saveData( String name, String mimetype, byte[] data) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType( mimetype);
        intent.putExtra(Intent.EXTRA_TITLE, name);
        dataToSave = data;
        int error = UIUtils.safelyStartActivityForResult( this, true, intent, REQUEST_CODE_SAVEDATA);
        if ( error != 0) {
            dataToSave = null;
        }
    }

    private String displayHtmlGetPath() {
        return getCacheDir() + "/MY_DATA.HTM";
    }

    private String displayHtmlGetURL() {
        return "file://" + displayHtmlGetPath();
    }

    private void displayHtmlDelete() {
        File file = new File( displayHtmlGetPath());
        if ( file.exists()) {
            file.delete();
        }
    }

    private boolean displayHtmlSave( ByteBuffer data) {
        displayHtmlDelete();
        if (data.limit() == 0) {
            return true;
        }
        File file = new File( displayHtmlGetPath());
        return FileUtils.writeBytesToFile( file, data.array());
    }

    private void displayHtmlLoad() {
        File file = new File( displayHtmlGetPath());
        if ( file.exists())
        {
            mWebView.loadUrl( displayHtmlGetURL());
        }
    }

    private void displayHtmlGoBack() {
        if (displayHtmlGetURL().equals(mWebView.getUrl())) {
            activityComplete();
        } else if ( !mWebView.canGoBack()) {
            activityComplete();
        } else {
            mWebView.goBack();
        }
    }

}
