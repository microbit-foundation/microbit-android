package com.samsung.microbit.ui.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.samsung.microbit.MBApp;
import com.samsung.microbit.R;
import com.samsung.microbit.core.bluetooth.BluetoothUtils;
import com.samsung.microbit.data.constants.EventCategories;
import com.samsung.microbit.data.constants.IPCConstants;
import com.samsung.microbit.data.constants.PermissionCodes;
import com.samsung.microbit.data.constants.RequestCodes;
import com.samsung.microbit.data.model.ConnectedDevice;
import com.samsung.microbit.data.model.ui.PairingActivityState;
import com.samsung.microbit.service.BLEService;
import com.samsung.microbit.ui.BluetoothChecker;
import com.samsung.microbit.ui.PopUp;
import com.samsung.microbit.ui.adapter.LEDAdapter;
import com.samsung.microbit.utils.BLEConnectionHandler;
import com.samsung.microbit.utils.Utils;
import com.samsung.microbit.utils.BLEPair;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import pl.droidsonroids.gif.GifImageView;

import static com.samsung.microbit.BuildConfig.DEBUG;

/**
 * Provides abilities to reconnect to previously connected device and
 * to establish a new connection by pairing to a new micro:bit board.
 * Pairing provides few steps which guides a user through pairing process.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class PairingActivity extends Activity implements View.OnClickListener, BLEPair.BLEPairCallback,
        BLEConnectionHandler.BLEConnectionManager {

    private static final String TAG = PairingActivity.class.getSimpleName();

    // @formatter:off
    private static final int DEVICE_CODE_ARRAY[] = {
            0, 0, 0, 0, 0,
            0, 0, 0, 0, 0,
            0, 0, 0, 0, 0,
            0, 0, 0, 0, 0,
            0, 0, 0, 0, 0};

    private static final String DEVICE_NAME_MAP_ARRAY[] = {
            "T", "A", "T", "A", "T",
            "P", "E", "P", "E", "P",
            "G", "I", "G", "I", "G",
            "V", "O", "V", "O", "V",
            "Z", "U", "Z", "U", "Z"};
    // @formatter:on

    /**
     * Allows to navigate through pairing process and
     * provide appropriate action.
     */
    private enum PAIRING_STATE {
        PAIRING_STATE_CONNECT_BUTTON,
        PAIRING_STATE_TRIPLE,
        PAIRING_STATE_STEP_1,
        PAIRING_STATE_STEP_2,
        PAIRING_STATE_SEARCHING,
        PAIRING_STATE_ERROR
    }

    private int activityState;

    private String PAIRING_STATE_KEY = "PAIRING_STATE_KEY";

    private PAIRING_STATE pairingState;
    private String newDeviceName = "";
    private String newDeviceCode = "";

    LinearLayout pairButtonView;
    LinearLayout pairTipView;
    View connectDeviceView;
    LinearLayout newDeviceView;
    LinearLayout pairSearchView;
    LinearLayout bottomPairButton;

    // Connected Device Status
    TextView deviceConnectionStatusView;

    private int currentOrientation;

    private List<Integer> requestPermissions = new ArrayList<>();

    private int requestingPermission = -1;

    private boolean justPaired;
    
    private BLEPair blePair = null;

    public final static String ACTION_RESET_TO_BLE = "com.samsung.microbit.ACTION_RESET_TO_BLE";
    public final static String ACTION_PAIR_BEFORE_FLASH = "com.samsung.microbit.ACTION_PAIR_BEFORE_FLASH";

    private String inAction = "";

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if(intent != null) {
            handleIncomingIntent(intent);
        }
    }

    private void handleIncomingIntent(Intent intent) {
        inAction = intent.getAction();
        if ( inAction == null) {
            inAction = "";
        }
        if ( inAction.equals(ACTION_RESET_TO_BLE)) {
            resetToBLEStart();
            return;
        }
        if ( inAction.equals(ACTION_PAIR_BEFORE_FLASH)) {
            pairBeforeFlashStart();
            return;
        }
    }

    private void resetToBLEStart() {
        displayScreen(PAIRING_STATE.PAIRING_STATE_TRIPLE);
    }

    private void resetToBLEFinish( int resultCode) {
        inAction = "";
        setResult( resultCode);
        finish();
    }

    private void pairBeforeFlashStart() {
        displayScreen(PAIRING_STATE.PAIRING_STATE_TRIPLE);
        checkBluetoothPermissions();
    }

    private void pairBeforeFlashFinish( int resultCode) {
        inAction = "";
        setResult( resultCode);
        finish();
    }

    public void onFinish( int resultCode) {
        logi("onFinish " + resultCode);
        if ( inAction.equals(ACTION_PAIR_BEFORE_FLASH)) {
            pairBeforeFlashFinish( resultCode);
            return;
        }
        displayScreen(PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON);
    }

    private BLEPair getBLEPair() {
        if ( blePair == null) {
            blePair = new BLEPair( this);
        }
        return blePair;
    }
    @Override
    public Activity BLEPairGetActivity() {
        return this;
    }
    @Override
    public String BLEPairGetDeviceName() {
        return newDeviceName;
    }
    @Override
    public String BLEPairGetDeviceCode() {
        return newDeviceCode;
    }
    @Override
    public void BLEPairResult() {
        BLEPair.enumResult result = getBLEPair().resultState;
        runOnUiThread(new Runnable() {
            @SuppressLint("MissingPermission")
            @Override
            public void run() {
                logi("BLEPairResult " + result);
                switch ( result) {
                    case Found:
                        updateOnScanFound();
//                        We need to connect even if bonded, to get resultHardwareVersion
//                        if ( getBLEPair().resultDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
//                            logi("scan found device already paired");
//                            handlePairingSuccessful();
//                        } else {
//                            if ( !getBLEPair().startPair()) {
//                                popupPairingFailed();
//                            }
//                        }
                        if ( !getBLEPair().startPair()) {
                            popupPairingFailed();
                        }
                        break;
                    case Connected:
                        updateOnConnected();
                        break;
                    case Paired:
                        handlePairingSuccessful();
                        break;
                    case TimeoutScan:
                        popupScanningTimeout();
                        break;
                    case TimeoutConnect:
                        popupPairingTimeout();
                        break;
                    case TimeoutPair:
                        popupPairingFailed();
                        break;
                    default:
                        break;
                }
            }
        });
    }

    private void stopScanning() {
        logi("###>>>>>>>>>>>>>>>>>>>>> stopScanning");
        getBLEPair().stopScanAndPair();
    }

    private void startScanning() {
        logi("###>>>>>>>>>>>>>>>>>>>>> startScanning");

        ((TextView) findViewById(R.id.search_microbit_step_3_title))
                .setText(getString(R.string.searchingTitle));

        getBLEPair().startScan();
    }

    /**
     * Provides actions after BLE permission has been granted:
     * check if bluetooth is disabled then enable it and
     * start the pairing steps.
     */
    private void proceedAfterBlePermissionGranted() {
        if(!BluetoothChecker.getInstance().isBluetoothON()) {
            setActivityState(PairingActivityState.STATE_ENABLE_BT_FOR_PAIRING);
            enableBluetooth();
            return;
        }
        if ( pairingNeedsLocationEnabled()) {
            setActivityState(PairingActivityState.STATE_ENABLE_LOCATION_FOR_PAIRING);
            enableLocation();
            return;
        }

        displayScreen(PAIRING_STATE.PAIRING_STATE_TRIPLE);
    }

    private boolean havePermission(String permission) {
        return ContextCompat.checkSelfPermission( this, permission) == PermissionChecker.PERMISSION_GRANTED;
    }

    private boolean havePermissionsLocationForeground() {
        boolean yes = true;
        if ( !havePermission( Manifest.permission.ACCESS_COARSE_LOCATION))
            yes = false;
        if ( !havePermission( Manifest.permission.ACCESS_FINE_LOCATION))
            yes = false;
        return yes;
    }

//    // REMOVE BACKGROUND
//    private boolean havePermissionsLocationBackground() {
//        boolean yes = true;
//        if (!havePermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
//            yes = false;
//        return yes;
//    }

    private boolean pairingNeedsLocationEnabled() {
        if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return false; // Not needed
        }
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        List<String> lp = lm.getProviders(true);
        for (String p : lp) {
            if (!p.equals(LocationManager.PASSIVE_PROVIDER)) {
                return false; // enabled
            }
        }
        return true;
    }

    private boolean havePermissionsPairing() {
        boolean yes = true;
        if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if ( !havePermission( Manifest.permission.BLUETOOTH_SCAN))
                yes = false;
            if ( !havePermission( Manifest.permission.BLUETOOTH_CONNECT))
                yes = false;
        }
        else if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if ( !havePermissionsLocationForeground())
                yes = false;
//            // REMOVE BACKGROUND
//            if (!havePermissionsLocationBackground())
//                yes = false;
        }
        else {
            if ( !havePermissionsLocationForeground())
                yes = false;
        }
        return yes;
    }

    private void requestPermissionsPairing() {
        if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] permissionsNeeded = {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT};
            requestPermission(permissionsNeeded, PermissionCodes.BLUETOOTH_PERMISSIONS_REQUESTED_API31);
        } else if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            String[] permissionsNeeded = {
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION};
            requestPermission(permissionsNeeded, PermissionCodes.BLUETOOTH_PERMISSIONS_REQUESTED_API30_FOREGROUND);
        } else if ( Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            String[] permissionsNeeded = {
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
//                    // REMOVE BACKGROUND
//                    , Manifest.permission.ACCESS_BACKGROUND_LOCATION
            };
            requestPermission(permissionsNeeded, PermissionCodes.BLUETOOTH_PERMISSIONS_REQUESTED_API29);
        } else {
            String[] permissionsNeeded = {
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION };
            requestPermission(permissionsNeeded, PermissionCodes.BLUETOOTH_PERMISSIONS_REQUESTED_API28);
        }
    }

//    // REMOVE BACKGROUND
//    private void requestPermissionsPairingAPI30Background() {
//        if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            String[] permissionsNeeded = { Manifest.permission.ACCESS_BACKGROUND_LOCATION };
//            requestPermission(permissionsNeeded, PermissionCodes.BLUETOOTH_PERMISSIONS_REQUESTED_API30_BACKGROUND);
//        }
//    }

    public void requestPermissionsPairingResult(int requestCode,
                                                @NonNull String permissions[],
                                                @NonNull int[] grantResults) {
        if ( havePermissionsPairing())
        {
            proceedAfterBlePermissionGranted();
            return;
        }

        switch(requestCode) {
            case PermissionCodes.BLUETOOTH_PERMISSIONS_REQUESTED_API30_FOREGROUND: {
                if ( havePermissionsLocationForeground()) {
//                    // REMOVE BACKGROUND
//                    requestPermissionsPairingAPI30Background();
                } else {
                    popupPermissionLocationError();
                }
                break;
            }
            case PermissionCodes.BLUETOOTH_PERMISSIONS_REQUESTED_API28:
            case PermissionCodes.BLUETOOTH_PERMISSIONS_REQUESTED_API29:
//                // REMOVE BACKGROUND
//            case PermissionCodes.BLUETOOTH_PERMISSIONS_REQUESTED_API30_BACKGROUND:
                popupPermissionLocationError();;
                break;
            case PermissionCodes.BLUETOOTH_PERMISSIONS_REQUESTED_API31: {
                popupPermissionBluetoothError();
                break;
            }
        }
    }

    private void popupPermissionLocationError() {
        PopUp.show(getString(R.string.location_permission_error),
                getString(R.string.permissions_needed_title),
                R.drawable.error_face, R.drawable.red_btn,
                PopUp.GIFF_ANIMATION_ERROR,
                PopUp.TYPE_ALERT,
                failedPermissionHandler, failedPermissionHandler);
    }

    private void popupPermissionBluetoothError() {
        PopUp.show(getString(R.string.ble_permission_error),
                getString(R.string.permissions_needed_title),
                R.drawable.error_face, R.drawable.red_btn,
                PopUp.GIFF_ANIMATION_ERROR,
                PopUp.TYPE_ALERT,
                failedPermissionHandler, failedPermissionHandler);
    }

    private void popupPermissionError() {
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            popupPermissionBluetoothError();
        } else {
            popupPermissionLocationError();
        }
    }

    private void popupPermissionPairing() {
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PopUp.show(getString(R.string.ble_permission),
                    getString(R.string.permissions_needed_title),
                    R.drawable.message_face, R.drawable.blue_btn, PopUp.GIFF_ANIMATION_NONE,
                    PopUp.TYPE_CHOICE,
                    bluetoothPermissionOKHandler,
                    bluetoothPermissionCancelHandler);
        } else {
            PopUp.show(getString(R.string.location_permission_pairing),
                    getString(R.string.permissions_needed_title),
                    R.drawable.message_face, R.drawable.blue_btn, PopUp.GIFF_ANIMATION_NONE,
                    PopUp.TYPE_CHOICE,
                    bluetoothPermissionOKHandler,
                    bluetoothPermissionCancelHandler);
        }
    }

    /**
     * Occurs after successfully finished pairing process and
     * redirects to the first screen of the pairing activity.
     */
    private View.OnClickListener successfulPairingHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("======successfulPairingHandler======");
            PopUp.hide();
            onFinish( RESULT_OK);
        }
    };

    /**
     * Occurs after pairing process failed and redirects to
     * Step 2 screen of the pairing process.
     */
    private View.OnClickListener failedPairingHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("======failedPairingHandler======");
            PopUp.hide();
            if ( inAction.equals(ACTION_PAIR_BEFORE_FLASH)) {
                pairBeforeFlashFinish( RESULT_CANCELED);
                return;
            }
            displayScreen(PAIRING_STATE.PAIRING_STATE_STEP_2);
        }
    };

    private View.OnClickListener failedPermissionHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("======failedPermissionHandler======");
            PopUp.hide();
            onFinish( RESULT_CANCELED);
        }
    };

    /**
     * Allows to do device scanning after unsuccessful one.
     */
    private View.OnClickListener retryScanning = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("======retryScanning======");
            PopUp.hide();
            startScanning();
            displayScreen(PAIRING_STATE.PAIRING_STATE_SEARCHING);
        }
    };

    /**
     * Occurs when GATT service has been closed and updates
     * information about a paired device.
     */
    private final BroadcastReceiver gattForceClosedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(BLEService.GATT_FORCE_CLOSED)) {
                logi("gattForceClosedReceiver");
                updatePairedDeviceCard();
            }
        }
    };

    private BroadcastReceiver connectionChangedReceiver = BLEConnectionHandler.bleConnectionChangedReceiver(this);

    @Override
    public void setActivityState(int baseActivityState) {
        activityState = baseActivityState;
    }

    @Override
    public void preUpdateUi() {
        logi("preUpdateUi");
        updatePairedDeviceCard();
    }

    @Override
    public int getActivityState() {
        return activityState;
    }

    @Override
    public void logi(String message) {
        if(DEBUG) {
            Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
        }
    }

    @Override
    public void checkTelephonyPermissions() {
        if(!requestPermissions.isEmpty()) {
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PermissionChecker.PERMISSION_GRANTED ||
                    (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PermissionChecker.PERMISSION_GRANTED)) {
                requestingPermission = requestPermissions.get(0);
                requestPermissions.remove(0);
                PopUp.show((requestingPermission == EventCategories.IPC_BLE_NOTIFICATION_INCOMING_CALL) ? getString(R.string
                                .telephony_permission) : getString(R.string.sms_permission),
                        getString(R.string.permissions_needed_title),
                        R.drawable.message_face, R.drawable.blue_btn, PopUp.GIFF_ANIMATION_NONE,
                        PopUp.TYPE_CHOICE,
                        notificationOKHandler,
                        notificationCancelHandler);
            }
        }
    }


    @Override
    public void addPermissionRequest(int permission) {
        requestPermissions.add(permission);
    }

    @Override
    public boolean arePermissionsGranted() {
        return requestPermissions.isEmpty();
    }

    /**
     * Allows to request permission either for read phone state or receive sms,
     * depending on what is requesting permission.
     */
    View.OnClickListener notificationOKHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("notificationOKHandler");
            PopUp.hide();
            if(requestingPermission == EventCategories.IPC_BLE_NOTIFICATION_INCOMING_CALL) {
                String[] permissionsNeeded = {Manifest.permission.READ_PHONE_STATE};
                requestPermission(permissionsNeeded, PermissionCodes.INCOMING_CALL_PERMISSIONS_REQUESTED);
            }
            if(requestingPermission == EventCategories.IPC_BLE_NOTIFICATION_INCOMING_SMS) {
                String[] permissionsNeeded = {Manifest.permission.RECEIVE_SMS};
                requestPermission(permissionsNeeded, PermissionCodes.INCOMING_SMS_PERMISSIONS_REQUESTED);
            }
        }
    };

    /**
     * Checks if more permission needed and requests it if true.
     */
    View.OnClickListener checkMorePermissionsNeeded = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if(!requestPermissions.isEmpty()) {
                checkTelephonyPermissions();
            } else {
                PopUp.hide();
            }
        }
    };

    /**
     * Occurs when a user canceled the telephony permissions granting and
     * shows a message about the app work flow.
     */
    View.OnClickListener notificationCancelHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("notificationCancelHandler");
            String msg = "Your program might not run properly";
            if(requestingPermission == EventCategories.IPC_BLE_NOTIFICATION_INCOMING_CALL) {
                msg = getString(R.string.telephony_permission_error);
            } else if(requestingPermission == EventCategories.IPC_BLE_NOTIFICATION_INCOMING_SMS) {
                msg = getString(R.string.sms_permission_error);
            }
            PopUp.hide();
            PopUp.show(msg,
                    getString(R.string.permissions_needed_title),
                    R.drawable.error_face, R.drawable.red_btn,
                    PopUp.GIFF_ANIMATION_ERROR,
                    PopUp.TYPE_ALERT,
                    checkMorePermissionsNeeded, checkMorePermissionsNeeded);
        }
    };

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        setContentView(R.layout.activity_connect);
        initViews();
        currentOrientation = getResources().getConfiguration().orientation;
        displayScreen(pairingState);
    }

    /**
     * Setup font styles by setting an appropriate typefaces.
     */
    private void setupFontStyle() {
        ImageView ohPrettyImg = (ImageView) findViewById(R.id.oh_pretty_emoji);
        ohPrettyImg.setVisibility(View.INVISIBLE);

        MBApp application = MBApp.getApp();

        Typeface defaultTypeface = application.getTypeface();
        Typeface robotoTypeface = application.getRobotoTypeface();

        deviceConnectionStatusView.setTypeface(defaultTypeface);

        // Connect Screen
        TextView appBarTitle = (TextView) findViewById(R.id.flash_projects_title_txt);
        appBarTitle.setTypeface(defaultTypeface);

        TextView pairBtnText = (TextView) findViewById(R.id.custom_pair_button_text);
        pairBtnText.setTypeface(defaultTypeface);

        Typeface boldTypeface = application.getTypefaceBold();

        TextView manageMicrobit = (TextView) findViewById(R.id.title_manage_microbit);
        manageMicrobit.setTypeface(boldTypeface);

        TextView manageMicorbitStatus = (TextView) findViewById(R.id.device_status_txt);
        manageMicorbitStatus.setTypeface(boldTypeface);

        // How to pair your micro:bit - Screen #1
        TextView pairTipTitle = (TextView) findViewById(R.id.pairTipTitle);
        pairTipTitle.setTypeface(boldTypeface);

        TextView stepOneTitle = (TextView) findViewById(R.id.pair_tip_step_1_step);
        stepOneTitle.setTypeface(boldTypeface);

        Button cancelPairButton = (Button) findViewById(R.id.cancel_tip_step_1_btn);
        cancelPairButton.setTypeface(robotoTypeface);

        Button nextPairButton = (Button) findViewById(R.id.ok_tip_step_1_btn);
        nextPairButton.setTypeface(robotoTypeface);


        // Step 2 - Enter Pattern
        TextView enterPatternTitle = (TextView) findViewById(R.id.enter_pattern_step_2_title);
        enterPatternTitle.setTypeface(boldTypeface);

        TextView stepTwoTitle = (TextView) findViewById(R.id.pair_enter_pattern_step_2);
        stepTwoTitle.setTypeface(boldTypeface);

        TextView stepTwoInstructions = (TextView) findViewById(R.id.pair_enter_pattern_step_2_instructions);
        stepTwoInstructions.setTypeface(robotoTypeface);

        Button cancelEnterPattern = (Button) findViewById(R.id.cancel_enter_pattern_step_2_btn);
        cancelEnterPattern.setTypeface(robotoTypeface);

        Button okEnterPatternButton = (Button) findViewById(R.id.ok_enter_pattern_step_2_btn);
        okEnterPatternButton.setTypeface(robotoTypeface);


        // Step 3 - Searching for micro:bit
        TextView searchMicrobitTitle = (TextView) findViewById(R.id.search_microbit_step_3_title);
        searchMicrobitTitle.setTypeface(boldTypeface);

        TextView stepThreeTitle = (TextView) findViewById(R.id.searching_microbit_step);
        stepThreeTitle.setTypeface(boldTypeface);

        TextView stepThreeInstructions = (TextView) findViewById(R.id.searching_microbit_step_instructions);
        stepThreeInstructions.setTypeface(robotoTypeface);

        Button cancelSearchMicroBit = (Button) findViewById(R.id.cancel_search_microbit_step_3_btn);
        cancelSearchMicroBit.setTypeface(robotoTypeface);


        TextView descriptionManageMicrobit = (TextView) findViewById(R.id.description_manage_microbit);
        descriptionManageMicrobit.setTypeface(robotoTypeface);

        TextView problemsMicrobit = (TextView) findViewById(R.id.connect_microbit_problems_message);
        problemsMicrobit.setTypeface(robotoTypeface);
    }

    /**
     * Initializes views, sets onClick listeners and sets font style.
     */
    private void initViews() {
        logi("initViews");
        deviceConnectionStatusView = findViewById(R.id.connected_device_status);
        bottomPairButton = (LinearLayout) findViewById(R.id.ll_pairing_activity_screen);
        pairButtonView = (LinearLayout) findViewById(R.id.pairButtonView);
        pairTipView = (LinearLayout) findViewById(R.id.pairTipView);
        connectDeviceView = findViewById(R.id.connectDeviceView);
        newDeviceView = (LinearLayout) findViewById(R.id.newDeviceView);
        pairSearchView = (LinearLayout) findViewById(R.id.pairSearchView);

        //Setup on click listeners.
        findViewById(R.id.pairButton).setOnClickListener(this);

        findViewById(R.id.viewPairStep1AnotherWay).setOnClickListener(this);
        findViewById(R.id.ok_tip_step_1_btn).setOnClickListener(this);
        findViewById(R.id.cancel_tip_step_1_btn).setOnClickListener(this);

        findViewById(R.id.ok_enter_pattern_step_2_btn).setOnClickListener(this);
        findViewById(R.id.cancel_enter_pattern_step_2_btn).setOnClickListener(this);

        findViewById(R.id.cancel_search_microbit_step_3_btn).setOnClickListener(this);

        setupFontStyle();
        logi("initViews End");
    }

    private void releaseViews() {
        deviceConnectionStatusView = null;
        bottomPairButton = null;
        pairButtonView = null;
        pairTipView = null;
        connectDeviceView = null;
        newDeviceView = null;
        pairSearchView = null;
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        logi("onResume");
        updatePairedDeviceCard();

        // Step 1 - How to pair
        findViewById(R.id.pair_tip_step_1_giff).animate();
    }

    @Override
    public void onPause() {
        logi("onPause() ::");
        super.onPause();

        // Step 1 - How to pair

        // Step 3 - Stop searching for micro:bit animation
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        logi("onCreate() ::");

        super.onCreate(savedInstanceState);

        MBApp application = MBApp.getApp();

        if(savedInstanceState == null) {
            logi("savedInstanceState == null");
            activityState = PairingActivityState.STATE_IDLE;
            pairingState = PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON;
            justPaired = false;

            LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(application);

            IntentFilter broadcastIntentFilter = new IntentFilter(IPCConstants.INTENT_BLE_NOTIFICATION);
            localBroadcastManager.registerReceiver(connectionChangedReceiver, broadcastIntentFilter);

            localBroadcastManager.registerReceiver(gattForceClosedReceiver, new IntentFilter(BLEService
                    .GATT_FORCE_CLOSED));
        } else {
            pairingState = (PAIRING_STATE) savedInstanceState.getSerializable(PAIRING_STATE_KEY);
        }

        // ************************************************
        //Remove title bar project_list
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        // setContentView takes just over 2s
        setContentView(R.layout.activity_connect);

        initViews();

        updatePairedDeviceCard();

        currentOrientation = getResources().getConfiguration().orientation;

        if (savedInstanceState == null && getIntent() != null) {
            handleIncomingIntent(getIntent());
        } else {
            // pin view
            displayScreen(pairingState);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(PAIRING_STATE_KEY, pairingState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        logi("onActivityResult");
        switch (requestCode) {
            case RequestCodes.REQUEST_ENABLE_BT:
                switch (resultCode) {
                    case RESULT_OK:
                        if (activityState == PairingActivityState.STATE_ENABLE_BT_FOR_PAIRING) {
                            proceedAfterBlePermissionGranted();
                        }
//                        else if (activityState == PairingActivityState.STATE_ENABLE_BT_FOR_CONNECT) {
//                            toggleConnection();
//                        }
                        break;
                    case RESULT_CANCELED:
                        //Change state back to Idle
                        setActivityState(PairingActivityState.STATE_IDLE);
                        PopUp.show(getString(R.string.bluetooth_off_cannot_continue), //message
                                "",
                                R.drawable.error_face, R.drawable.red_btn,
                                PopUp.GIFF_ANIMATION_ERROR,
                                PopUp.TYPE_ALERT,
                                failedPermissionHandler, failedPermissionHandler);
                        break;
                }
                break;
            case RequestCodes.REQUEST_ENABLE_LOCATION:
                if ( pairingNeedsLocationEnabled()) {
                    setActivityState(PairingActivityState.STATE_IDLE);
                    popupLocationNeeded();
                } else {
                    proceedAfterBlePermissionGranted();
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Displays a pattern entering grid, sets onClick listener for all its cells and
     * checks if pattern is valid.
     */
    private void displayLedGrid() {

        final GridView gridview = (GridView) findViewById(R.id.enter_pattern_step_2_gridview);
        gridview.setAdapter(new LEDAdapter(this, DEVICE_CODE_ARRAY));
        gridview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v,
                                    int position, long id) {
                toggleLED((ImageView) v, position);
                setCol(parent, position);

                checkPatternSuccess();
            }

        });

        checkPatternSuccess();
    }

    /**
     * Checks if entered pattern is success and allows to go to the next step
     * of the pairing process if true.
     */
    private void checkPatternSuccess() {
        final ImageView ohPrettyImage = (ImageView) findViewById(R.id.oh_pretty_emoji);
        if(DEVICE_CODE_ARRAY[20] == 1 && DEVICE_CODE_ARRAY[21] == 1
                && DEVICE_CODE_ARRAY[22] == 1 && DEVICE_CODE_ARRAY[23] == 1
                && DEVICE_CODE_ARRAY[24] == 1) {
            findViewById(R.id.ok_enter_pattern_step_2_btn).setVisibility(View.VISIBLE);
            ohPrettyImage.setImageResource(R.drawable.emoji_entering_pattern_valid_pattern);
        } else {
            findViewById(R.id.ok_enter_pattern_step_2_btn).setVisibility(View.INVISIBLE);
            ohPrettyImage.setImageResource(R.drawable.emoji_entering_pattern);
        }
    }

    private void generateName() {
        StringBuilder deviceNameBuilder = new StringBuilder();
        //Columns
        for(int col = 0; col < 5; col++) {
            //Rows
            for(int row = 0; row < 5; row++) {
                if(DEVICE_CODE_ARRAY[(col + (5 * row))] == 1) {
                    deviceNameBuilder.append(DEVICE_NAME_MAP_ARRAY[(col + (5 * row))]);
                    break;
                }
            }
        }

        newDeviceCode = deviceNameBuilder.toString();
        newDeviceName = "BBC microbit [" + deviceNameBuilder.toString() + "]";
        //Toast.makeText(this, "Pattern :"+newDeviceCode, Toast.LENGTH_SHORT).show();
    }

    /**
     * Sets on all cells in a column below the clicked cell.
     *
     * @param parent Grid of cells.
     * @param pos    Clicked cell position.
     */
    private void setCol(AdapterView<?> parent, int pos) {
        int index = pos - 5;
        ImageView v;

        while(index >= 0) {
            v = (ImageView) parent.getChildAt(index);
            v.setBackground(getApplication().getResources().getDrawable(R.drawable.white_red_led_btn));
            v.setTag(R.id.ledState, 0);
            v.setSelected(false);
            DEVICE_CODE_ARRAY[index] = 0;
            int position = (Integer) v.getTag(R.id.position);
            v.setContentDescription("" + position + getLEDStatus(index)); // TODO - calculate correct position
            index -= 5;
        }
        index = pos + 5;
        while(index < 25) {
            v = (ImageView) parent.getChildAt(index);
            v.setBackground(getApplication().getResources().getDrawable(R.drawable.red_white_led_btn));
            v.setTag(R.id.ledState, 1);
            v.setSelected(false);
            DEVICE_CODE_ARRAY[index] = 1;
            int position = (Integer) v.getTag(R.id.position);
            v.setContentDescription("" + position + getLEDStatus(index));
            index += 5;
        }

    }

    /**
     * Sets a clicked cell on/off.
     *
     * @param image An image of a clicked cell.
     * @param pos   Position of a clicked cell.
     * @return True, if cell is on and false otherwise.
     */
    private boolean toggleLED(ImageView image, int pos) {
        boolean isOn;
        //Toast.makeText(this, "Pos :" +  pos, Toast.LENGTH_SHORT).show();
        int state = (Integer) image.getTag(R.id.ledState);
        if(state != 1) {
            DEVICE_CODE_ARRAY[pos] = 1;
            image.setBackground(getApplication().getResources().getDrawable(R.drawable.red_white_led_btn));
            image.setTag(R.id.ledState, 1);
            isOn = true;

        } else {
            DEVICE_CODE_ARRAY[pos] = 0;
            image.setBackground(getApplication().getResources().getDrawable(R.drawable.white_red_led_btn));
            image.setTag(R.id.ledState, 0);
            isOn = false;
            // Update the code to consider the still ON LED below the toggled one
            if(pos < 20) {
                DEVICE_CODE_ARRAY[pos + 5] = 1;
            }
        }

        image.setSelected(false);
        int position = (Integer) image.getTag(R.id.position);
        image.setContentDescription("" + position + getLEDStatus(pos));
        return isOn;
    }

    /**
     * Allows to get the status of the currently selected LED at a given position.
     *
     * @param position Position of the cell.
     * @return String value that indicates if is LED on or off.
     */
    private String getLEDStatus(int position) {
        return getStatusString(DEVICE_CODE_ARRAY[position] == 1);
    }

    private Drawable getDrawableResource(int resID) {
        return ContextCompat.getDrawable(this, resID);
    }

    /**
     * Updates connection status UI according to current connection status.
     */
    private void updateConnectionStatus() {
        ConnectedDevice connectedDevice = BluetoothUtils.getPairedMicrobit(this);

        if(!connectedDevice.mStatus) {
            // Device is not connected
            deviceConnectionStatusView.setBackgroundResource(R.drawable.grey_text_view);
            deviceConnectionStatusView.setTextColor(Color.WHITE);
            deviceConnectionStatusView.setContentDescription("Micro:bit not connected " + connectedDevice.mName + "is " + getStatusString(connectedDevice.mStatus));

        } else {
            // Device is connected
            deviceConnectionStatusView.setBackgroundResource(R.drawable.white_btn_devices_status_connected);
            deviceConnectionStatusView.setTextColor(Color.BLACK);
            deviceConnectionStatusView.setContentDescription("Currently connected Micro:bit " + connectedDevice.mName + "is " + getStatusString(connectedDevice.mStatus));
        }
    }

    /**
     * Converts status state from boolean to its String representation.
     *
     * @param status Status to convert.
     * @return String representation of status.
     */
    public String getStatusString(boolean status) {
        return status ? "on" : "off";
    }

    /**
     * Updates bond and connection status UI.
     */
    private void updatePairedDeviceCard() {
        logi("updatePairedDeviceCard");

        ConnectedDevice connectedDevice = BluetoothUtils.getPairedMicrobit(this);
        if(connectedDevice.mName == null) {
            // No device is Paired
            deviceConnectionStatusView.setBackgroundResource(R.drawable.grey_text_view);
            deviceConnectionStatusView.setText("-");
        } else {
            deviceConnectionStatusView.setText(connectedDevice.mName);
            updateConnectionStatus();
        }
        logi("updatePairedDeviceCard End");
    }

    private void updateOnScanFound() {
        logi("updateOnScanFound");
        TextView textView = (TextView) findViewById(R.id.search_microbit_step_3_title);
        TextView tvSearchingStep = (TextView) findViewById(R.id.searching_microbit_step);
        TextView tvSearchingInstructions = (TextView) findViewById(R.id.searching_microbit_step_instructions);
        if (textView != null) {
            textView.setText(getString(R.string.searchingTitle));
            //findViewById(R.id.searching_progress_spinner).setVisibility(View.GONE);
            ((GifImageView) findViewById(R.id.searching_microbit_found_giffview))
                    .setImageResource(R.drawable.emoji_microbit_found);
//            if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
//                tvSearchingStep.setText(R.string.searching_microbit_found_message_one_line);
//            } else {
//                tvSearchingStep.setText(R.string.searching_microbit_found_message);
//            }
//            tvSearchingInstructions.setText(R.string.searching_tip_text_instructions);
        }
    }

    private void updateOnConnected() {
        logi("updateOnConnected");
        TextView textView = (TextView) findViewById(R.id.search_microbit_step_3_title);
        TextView tvSearchingStep = (TextView) findViewById(R.id.searching_microbit_step);
        TextView tvSearchingInstructions = (TextView) findViewById(R.id.searching_microbit_step_instructions);
        if (textView != null) {
            textView.setText(getString(R.string.searchingTitle));
            findViewById(R.id.searching_progress_spinner).setVisibility(View.GONE);
            ((GifImageView) findViewById(R.id.searching_microbit_found_giffview))
                    .setImageResource(R.drawable.emoji_microbit_found);
            if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                tvSearchingStep.setText(R.string.searching_microbit_found_message_one_line);
            } else {
                tvSearchingStep.setText(R.string.searching_microbit_found_message);
            }
            tvSearchingInstructions.setText(R.string.searching_tip_text_instructions);
        }
    }

    /**
     * Displays needed screen according to a pairing state and
     * allows to navigate through the connection screens.
     *
     * @param gotoState New pairing state.
     */
    private void displayScreen(PAIRING_STATE gotoState) {
        logi("displayScreen");
        //Reset all screens first
        pairTipView.setVisibility(View.GONE);
        newDeviceView.setVisibility(View.GONE);
        pairSearchView.setVisibility(View.GONE);
        connectDeviceView.setVisibility(View.GONE);

        logi("********** Connect: state from " + pairingState + " to " + gotoState);
        pairingState = gotoState;

        boolean mDeviceListAvailable = ((gotoState == PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON) ||
                (gotoState == PAIRING_STATE.PAIRING_STATE_ERROR));

        if(mDeviceListAvailable) {
            updatePairedDeviceCard();
            connectDeviceView.setVisibility(View.VISIBLE);
        }

        switch(gotoState) {
            case PAIRING_STATE_CONNECT_BUTTON:
                break;

            case PAIRING_STATE_ERROR:
                Arrays.fill(DEVICE_CODE_ARRAY, 0);
                findViewById(R.id.enter_pattern_step_2_gridview).setEnabled(true);
                newDeviceName = "";
                newDeviceCode = "";
                break;

            case PAIRING_STATE_TRIPLE:
                displayScreenTripleOrStep1(
                        R.drawable.reset_triple,
                        R.string.viewPairTriplePromptText);
                break;

            case PAIRING_STATE_STEP_1:
                displayScreenTripleOrStep1(
                        R.drawable.how_to_pair_microbit,
                        R.string.connect_tip_text);
                break;

            case PAIRING_STATE_STEP_2:
                newDeviceView.setVisibility(View.VISIBLE);
                findViewById(R.id.cancel_enter_pattern_step_2_btn).setVisibility(View.VISIBLE);
                findViewById(R.id.enter_pattern_step_2_title).setVisibility(View.VISIBLE);
                findViewById(R.id.oh_pretty_emoji).setVisibility(View.VISIBLE);

                displayLedGrid();
                break;

            case PAIRING_STATE_SEARCHING:
                if(pairSearchView != null) {
                    pairSearchView.setVisibility(View.VISIBLE);
                    TextView tvTitle = (TextView) findViewById(R.id.search_microbit_step_3_title);
                    TextView tvSearchingStep = (TextView) findViewById(R.id.searching_microbit_step);
                    tvSearchingStep.setContentDescription(tvSearchingStep.getText());
                    TextView tvSearchingInstructions = (TextView) findViewById(R.id.searching_microbit_step_instructions);
                    if(tvTitle != null) {
                        tvTitle.setText(R.string.searchingTitle);
                        findViewById(R.id.searching_progress_spinner).setVisibility(View.VISIBLE);
                        ((GifImageView) findViewById(R.id.searching_microbit_found_giffview))
                                .setImageResource(R.drawable.pairing_pin_screen_two);
                        if(currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                            tvSearchingStep.setText(R.string.searching_tip_step_text_one_line);
                        } else {
                            tvSearchingStep.setText(R.string.searching_tip_step_text);
                        }
                        tvSearchingInstructions.setText(R.string.searching_tip_text_instructions);
                    }
                    justPaired = true;
                } else {
                    justPaired = false;
                }
                break;
        }
        logi("displayScreen End");
    }

    private void displayScreenTripleOrStep1( int resIdGif, int resIdPrompt)
    {
        if ( inAction.equals(ACTION_RESET_TO_BLE)) {
            TextView title = (TextView) findViewById(R.id.pairTipTitle);
            title.setText(R.string.connect_tip_title_resetToBLE);
            TextView step = (TextView) findViewById(R.id.pair_tip_step_1_step);
            step.setText("");
            step.setContentDescription("");
        }

        GifImageView gif = (GifImageView) findViewById(R.id.pair_tip_step_1_giff);
        gif.setImageResource( resIdGif);

        TextView prompt = (TextView) findViewById(R.id.pair_tip_step_1_instructions);
        prompt.setText( resIdPrompt);
        prompt.setContentDescription(prompt.getText());

        pairTipView.setVisibility(View.VISIBLE);
        gif.animate();
    }

    /**
     * Starts activity to enable bluetooth.
     */
    private void enableBluetooth() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, RequestCodes.REQUEST_ENABLE_BT);
    }

    private void enableLocation() {
        PopUp.show( "Please enable Location Services", //message
                "Location Services Not Active", //title
                R.drawable.message_face, R.drawable.blue_btn, PopUp.GIFF_ANIMATION_NONE,
                PopUp.TYPE_CHOICE,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PopUp.hide();
                        //Unpair the device for secure BLE
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivityForResult( intent, RequestCodes.REQUEST_ENABLE_LOCATION);
                    }
                },//override click listener for ok button
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PopUp.hide();
                        setActivityState(PairingActivityState.STATE_IDLE);
                        popupLocationNeeded();
                    }
                });
    }

    public void popupLocationNeeded() {
        PopUp.show("Cannot continue without enabling location", //message
                "",
                R.drawable.error_face, R.drawable.red_btn,
                PopUp.GIFF_ANIMATION_ERROR,
                PopUp.TYPE_ALERT,
                failedPermissionHandler, failedPermissionHandler);
    }

    /**
     * Enables or disables connection with a currently paired micro:bit board.
     */
//    private void toggleConnection() {
//        ConnectedDevice currentDevice = BluetoothUtils.getPairedMicrobit(this);
//        Log.v(TAG, "currentDevice.toString()");
//
//        if(currentDevice.mAddress != null) {
//            boolean currentState = currentDevice.mStatus;
//
//            if(!currentState) {
//                setActivityState(PairingActivityState.STATE_CONNECTING);
//                requestPermissions.clear();
//                PopUp.show(getString(R.string.init_connection),
//                        "",
//                        R.drawable.message_face, R.drawable.blue_btn,
//                        PopUp.GIFF_ANIMATION_NONE,
//                        PopUp.TYPE_SPINNER,
//                        null, null);
//
//                ServiceUtils.sendConnectDisconnectMessage(true);
//            } else {
//                setActivityState(PairingActivityState.STATE_DISCONNECTING);
//                PopUp.show(getString(R.string.disconnecting),
//                        "",
//                        R.drawable.message_face, R.drawable.blue_btn,
//                        PopUp.GIFF_ANIMATION_NONE,
//                        PopUp.TYPE_SPINNER,
//                        null, null);
//
//                ServiceUtils.sendConnectDisconnectMessage(false);
//            }
//        }
//    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch(requestCode) {
            case PermissionCodes.BLUETOOTH_PERMISSIONS_REQUESTED_API28:
            case PermissionCodes.BLUETOOTH_PERMISSIONS_REQUESTED_API29:
            case PermissionCodes.BLUETOOTH_PERMISSIONS_REQUESTED_API30_FOREGROUND:
//                // REMOVE BACKGROUND
//            case PermissionCodes.BLUETOOTH_PERMISSIONS_REQUESTED_API30_BACKGROUND:
            case PermissionCodes.BLUETOOTH_PERMISSIONS_REQUESTED_API31: {
                requestPermissionsPairingResult(requestCode, permissions, grantResults);
                break;
            }
            case PermissionCodes.INCOMING_CALL_PERMISSIONS_REQUESTED: {
                if(grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    PopUp.show(getString(R.string.telephony_permission_error),
                            getString(R.string.permissions_needed_title),
                            R.drawable.error_face, R.drawable.red_btn,
                            PopUp.GIFF_ANIMATION_ERROR,
                            PopUp.TYPE_ALERT,
                            checkMorePermissionsNeeded, checkMorePermissionsNeeded);
                } else {
                    if(!requestPermissions.isEmpty()) {
                        checkTelephonyPermissions();
                    }
                }
                break;
             }
             case PermissionCodes.INCOMING_SMS_PERMISSIONS_REQUESTED: {
                if(grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    PopUp.show(getString(R.string.sms_permission_error),
                            getString(R.string.permissions_needed_title),
                            R.drawable.error_face, R.drawable.red_btn,
                            PopUp.GIFF_ANIMATION_ERROR,
                            PopUp.TYPE_ALERT,
                            checkMorePermissionsNeeded, checkMorePermissionsNeeded);
                } else {
                    if(!requestPermissions.isEmpty()) {
                        checkTelephonyPermissions();
                    }
                }
                break;
             }
        }
    }

    private void requestPermission(String[] permissions, final int requestCode) {
        ActivityCompat.requestPermissions(this, permissions, requestCode);
    }

    /**
     * Requests permission to use bluetooth.
     */
    View.OnClickListener bluetoothPermissionOKHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("bluetoothPermissionOKHandler");
            PopUp.hide();
            requestPermissionsPairing();
        }
    };

    /**
     * Occurs when a user canceled a location permission granting and
     * shows an information window.
     */
    View.OnClickListener bluetoothPermissionCancelHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("bluetoothPermissionCancelHandler");
            PopUp.hide();
            popupPermissionError();
        }
    };

    /**
     * Checks if bluetooth permission is granted. If it's not then ask to grant,
     * proceed with using bluetooth otherwise.
     */
    private void checkBluetoothPermissions() {
        Log.v(TAG, "checkBluetoothPermissions");
        if( !havePermissionsPairing()) {
            popupPermissionPairing();
        } else {
            Log.v(TAG, "skipped");
            proceedAfterBlePermissionGranted();
        }
    }

    @Override
    public void onClick(final View v) {
        switch(v.getId()) {
            // Pair a micro:bit
            case R.id.pairButton:
                logi("onClick() :: pairButton");
                checkBluetoothPermissions();
                break;

            case R.id.viewPairStep1AnotherWay:
                logi("onClick() :: viewPairStep1AnotherWay");
                displayScreen( pairingState == PAIRING_STATE.PAIRING_STATE_TRIPLE
                        ? PAIRING_STATE.PAIRING_STATE_STEP_1 : PAIRING_STATE.PAIRING_STATE_TRIPLE);
                break;

            // Proceed to Enter Pattern
            case R.id.ok_tip_step_1_btn:
                logi("onClick() :: ok_tip_screen_one_button");
                if ( inAction.equals(ACTION_RESET_TO_BLE)) {
                    resetToBLEFinish(Activity.RESULT_OK);
                    return;
                }
                displayScreen(PAIRING_STATE.PAIRING_STATE_STEP_2);
                break;

            // Confirm pattern and begin searching for micro:bit
            case R.id.ok_enter_pattern_step_2_btn:
                logi("onClick() :: ok_tip_screen_one_button");
                generateName();
                if(!BluetoothChecker.getInstance().checkBluetoothAndStart()) {
                    return;
                }
                startScanning();
                displayScreen(PAIRING_STATE.PAIRING_STATE_SEARCHING);
                break;

            case R.id.cancel_tip_step_1_btn:
                logi("onClick() :: cancel_tip_button");
                if ( inAction.equals(ACTION_RESET_TO_BLE)) {
                    resetToBLEFinish(Activity.RESULT_CANCELED);
                    return;
                }
                onFinish( RESULT_CANCELED);
                break;

            case R.id.cancel_enter_pattern_step_2_btn:
                logi("onClick() :: cancel_name_button");
                stopScanning();
                onFinish( RESULT_CANCELED);
                break;

            case R.id.cancel_search_microbit_step_3_btn:
                logi("onClick() :: cancel_search_button");
                stopScanning();
                onFinish( RESULT_CANCELED);
                break;

            //TODO: there is no ability to delete paired device on Connect screen, so add or remove the case.
            // Delete Microbit
            case R.id.deleteBtn:
                logi("onClick() :: deleteBtn");
                handleDeleteMicrobit();
                break;

            case R.id.backBtn:
                logi("onClick() :: backBtn");
                handleResetAll();
                break;

            default:
                Toast.makeText(MBApp.getApp(), "Default Item Clicked: " + v.getId(), Toast.LENGTH_SHORT).show();
                break;

        }
    }

    /**
     * Shows a dialog window that allows to unpair currently paired micro:bit board.
     */
    private void handleDeleteMicrobit() {
        PopUp.show(getString(R.string.deleteMicrobitMessage), //message
                getString(R.string.deleteMicrobitTitle), //title
                R.drawable.ic_trash, R.drawable.red_btn,
                PopUp.GIFF_ANIMATION_NONE,
                PopUp.TYPE_CHOICE, //type of popup.
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        logi("handleDeleteMicrobit OK");
                        PopUp.hide();
                        //Unpair the device for secure BLE
                        unPairDevice();
                        BluetoothUtils.setPairedMicroBit(MBApp.getApp(), null);
                        updatePairedDeviceCard();
                    }
                },//override click listener for ok button
                null);//pass null to use default listener
    }

    /**
     * Finds all bonded devices and tries to unbond it.
     */
    private void unPairDevice() {
        ConnectedDevice connectedDevice = BluetoothUtils.getPairedMicrobit(this);
        String addressToDelete = connectedDevice.mAddress;
        // Get the paired devices and put them in a Set
        BluetoothAdapter mBluetoothAdapter = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for(BluetoothDevice bt : pairedDevices) {
            logi("Paired device " + bt.getName());
            if(bt.getAddress().equals(addressToDelete)) {
                try {
                    Method m = bt.getClass().getMethod("removeBond", (Class[]) null);
                    m.invoke(bt, (Object[]) null);
                } catch(NoSuchMethodException | IllegalAccessException
                        | InvocationTargetException e) {
                    Log.e(TAG, e.toString());
                }
            }
        }
    }

    /**
     * Cancels pairing and returns to the first screen (Connect screen).
     * If it is Connect screen, finishes the activity and return to the home activity.
     */
    private void handleResetAll() {
        Arrays.fill(DEVICE_CODE_ARRAY, 0);
        stopScanning();
        if ( inAction.equals(ACTION_PAIR_BEFORE_FLASH)) {
            pairBeforeFlashFinish( RESULT_CANCELED);
            return;
        }
        if(pairingState == PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON) {
            finish();
        } else {
            displayScreen(PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON);
        }
    }

    /**
     * Shows a dialog windows that indicates that pairing has timed out and
     * allows to retry pairing or terminate it.
     */
    private void popupScanningTimeout() {
        logi("popupScanningTimeout() :: Start");
        PopUp.show(getString(R.string.pairingErrorMessage), //message
                getString(R.string.timeOut), //title
                R.drawable.error_face, //image icon res id
                R.drawable.red_btn,
                PopUp.GIFF_ANIMATION_ERROR,
                PopUp.TYPE_CHOICE, //type of popup.
                retryScanning,//override click listener for ok button
                failedPairingHandler);
    }

    private void popupPairingTimeout() {
        logi("popupPairingTimeout() :: Start");
        PopUp.show(getString(R.string.pairingErrorMessage), //message
                getString(R.string.timeOut), //title
                R.drawable.error_face, //image icon res id
                R.drawable.red_btn,
                PopUp.GIFF_ANIMATION_ERROR,
                PopUp.TYPE_CHOICE, //type of popup.
                retryScanning,//override click listener for ok button
                failedPairingHandler);
    }

    /**
     * Shows a dialog windows that indicates that pairing has failed and
     * allows to retry pairing or terminate it.
     */
    private void popupPairingFailed() {
        logi("popupPairingFailed() :: Start");
        PopUp.show(getString(R.string.pairing_failed_message), //message
                getString(R.string.pairing_failed_title), //title
                R.drawable.error_face, //image icon res id
                R.drawable.red_btn,
                PopUp.GIFF_ANIMATION_ERROR,
                PopUp.TYPE_CHOICE, //type of popup.
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PopUp.hide();
                        displayScreen(PAIRING_STATE.PAIRING_STATE_TRIPLE);
                    }
                },//override click listener for ok button
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PopUp.hide();
                        onFinish( RESULT_CANCELED);
                    }
                });
    }

    /**
     * Updates information about a new paired device, updates connection status UI
     * and shows a notification window about successful pairing.
     */
    private void handlePairingSuccessful() {
        logi("handlePairingSuccessful()");
        ConnectedDevice newDev = new ConnectedDevice(
                newDeviceCode.toUpperCase(),
                newDeviceCode.toUpperCase(),
                false,
                getBLEPair().resultDevice.getAddress(),
                0,
                null,
                System.currentTimeMillis(),
                getBLEPair().resultHardwareVersion);
        BluetoothUtils.setPairedMicroBit(MBApp.getApp(), newDev);
        updatePairedDeviceCard();
        // Pop up to show pairing successful
        PopUp.show(getString(R.string.pairing_successful_tip_message), // message
                getString(R.string.pairing_success_message_1), //title
                R.drawable.message_face, //image icon res id
                R.drawable.green_btn,
                PopUp.GIFF_ANIMATION_NONE,
                PopUp.TYPE_ALERT, //type of popup.
                successfulPairingHandler,
                successfulPairingHandler);

        Log.e(TAG, "Set just paired to true");
        if(justPaired) {
            justPaired = false;
            MBApp.getApp().setJustPaired(true);
        } else {
            MBApp.getApp().setJustPaired(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        getBLEPair().stopScanAndPair();

        pairTipView.setVisibility(View.GONE);
        newDeviceView.setVisibility(View.GONE);
        pairSearchView.setVisibility(View.GONE);
        connectDeviceView.setVisibility(View.GONE);

        releaseViews();

        Utils.unbindDrawables(findViewById(R.id.connected_device_status));
        Utils.unbindDrawables(findViewById(R.id.pairButtonView));

        Utils.unbindDrawables(findViewById(R.id.pairTipView));
        Utils.unbindDrawables(findViewById(R.id.connectDeviceView));
        Utils.unbindDrawables(findViewById(R.id.pairSearchView));
        Utils.unbindDrawables(findViewById(R.id.flash_projects_title_txt));
        Utils.unbindDrawables(findViewById(R.id.title_manage_microbit));
        Utils.unbindDrawables(findViewById(R.id.device_status_txt));
        Utils.unbindDrawables(findViewById(R.id.description_manage_microbit));
        Utils.unbindDrawables(findViewById(R.id.pairButton));
        Utils.unbindDrawables(findViewById(R.id.connect_microbit_problems_message));
        Utils.unbindDrawables(findViewById(R.id.pairTipTitle));
        Utils.unbindDrawables(findViewById(R.id.pair_tip_step_1_step));
        Utils.unbindDrawables(findViewById(R.id.pair_tip_step_1_instructions));

        Utils.unbindDrawables(findViewById(R.id.cancel_tip_step_1_btn));
        Utils.unbindDrawables(findViewById(R.id.ok_tip_step_1_btn));
        Utils.unbindDrawables(findViewById(R.id.enter_pattern_step_2_title));
        Utils.unbindDrawables(findViewById(R.id.pair_enter_pattern_step_2_instructions));
        Utils.unbindDrawables(findViewById(R.id.oh_pretty_emoji));
        Utils.unbindDrawables(findViewById(R.id.cancel_enter_pattern_step_2_btn));
        Utils.unbindDrawables(findViewById(R.id.ok_enter_pattern_step_2_btn));
        Utils.unbindDrawables(findViewById(R.id.search_microbit_step_3_title));
        Utils.unbindDrawables(findViewById(R.id.searching_microbit_step));
        Utils.unbindDrawables(findViewById(R.id.searching_microbit_step_instructions));
        Utils.unbindDrawables(findViewById(R.id.cancel_search_microbit_step_3_btn));
        Utils.unbindDrawables(findViewById(R.id.searching_progress_spinner));

        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(MBApp.getApp());

        localBroadcastManager.unregisterReceiver(gattForceClosedReceiver);
        localBroadcastManager.unregisterReceiver(connectionChangedReceiver);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if((keyCode == KeyEvent.KEYCODE_BACK)) {
            logi("onKeyDown() :: Cancel");
            handleResetAll();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        logi("onBackPressed() :: Cancel");
        handleResetAll();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_launcher, menu);
        return true;
    }
}
