package com.samsung.microbit.ui.activity;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.samsung.microbit.MBApp;
import com.samsung.microbit.R;
import com.samsung.microbit.core.bluetooth.BluetoothUtils;
import com.samsung.microbit.data.constants.Constants;
import com.samsung.microbit.data.constants.EventCategories;
import com.samsung.microbit.data.constants.IPCConstants;
import com.samsung.microbit.data.constants.PermissionCodes;
import com.samsung.microbit.data.constants.RequestCodes;
import com.samsung.microbit.data.model.ConnectedDevice;
import com.samsung.microbit.data.model.Project;
import com.samsung.microbit.data.model.ui.FlashActivityState;
import com.samsung.microbit.service.BLEService;
import com.samsung.microbit.service.DfuService;
import com.samsung.microbit.service.PartialFlashingService;
import com.samsung.microbit.ui.BluetoothChecker;
import com.samsung.microbit.ui.PopUp;
import com.samsung.microbit.ui.adapter.ProjectAdapter;
import com.samsung.microbit.utils.BLEConnectionHandler;
import com.samsung.microbit.utils.FileUtils;
import com.samsung.microbit.utils.IOUtils;
import com.samsung.microbit.utils.ProjectsHelper;
import com.samsung.microbit.utils.ServiceUtils;
import com.samsung.microbit.utils.Utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import no.nordicsemi.android.dfu.DfuBaseService;
import no.nordicsemi.android.dfu.DfuServiceController;
import no.nordicsemi.android.dfu.DfuServiceInitiator;
import no.nordicsemi.android.error.GattError;

import static com.samsung.microbit.BuildConfig.DEBUG;
import static com.samsung.microbit.ui.PopUp.TYPE_ALERT;
import static com.samsung.microbit.ui.PopUp.TYPE_PROGRESS_NOT_CANCELABLE;
import static com.samsung.microbit.ui.PopUp.TYPE_SPINNER_NOT_CANCELABLE;
import static com.samsung.microbit.ui.activity.PopUpActivity.INTENT_ACTION_UPDATE_LAYOUT;
import static com.samsung.microbit.ui.activity.PopUpActivity.INTENT_ACTION_UPDATE_PROGRESS;
import static com.samsung.microbit.ui.activity.PopUpActivity.INTENT_EXTRA_MESSAGE;
import static com.samsung.microbit.ui.activity.PopUpActivity.INTENT_EXTRA_TITLE;
import static com.samsung.microbit.ui.activity.PopUpActivity.INTENT_EXTRA_TYPE;
import static com.samsung.microbit.ui.activity.PopUpActivity.INTENT_GIFF_ANIMATION_CODE;
import static com.samsung.microbit.utils.FileUtils.getFileSize;

// import com.samsung.microbit.core.GoogleAnalyticsManager;

/**
 * Represents the Flash screen that contains a list of project samples
 * and allows to flash them to a micro:bit or remove them from the list.
 */
public class ProjectActivity extends Activity implements View.OnClickListener, BLEConnectionHandler.BLEConnectionManager {
    private static final String TAG = ProjectActivity.class.getSimpleName();

    private List<Project> mProjectList = new ArrayList<>();
    private List<Project> mOldProjectList = new ArrayList<>();
    private ListView mProjectListView;
    private ListView mProjectListViewRight;
    private TextView mEmptyText;
    private HashMap<String, String> mPrettyFileNameMap = new HashMap<>();

    private Project mProgramToSend;

    private String m_HexFileSizeStats = "0";
    private String m_BinSizeStats = "0";
    private String m_MicroBitFirmware = "0.0";

    private DFUResultReceiver dfuResultReceiver;
    private pfResultReceiver pfResultReceiver;

    private List<Integer> mRequestPermissions = new ArrayList<>();

    private int mRequestingPermission = -1;

    private int mActivityState;

    Intent service;

    private BroadcastReceiver connectionChangedReceiver = BLEConnectionHandler.bleConnectionChangedReceiver(this);

    private Handler handler = new Handler();
    private int countOfReconnecting;
    private boolean sentPause;

    private boolean notAValidFlashHexFile;

    private boolean minimumPermissionsGranted;

    private boolean previousPartialFlashFailed = false;

    private int FLASH_TYPE_DFU = 0;
    private int FLASH_TYPE_PF  = 1;

    private int MICROBIT_V1 = 1;
    private int MICROBIT_V2 = 2;

    BLEService bleService;

    private final Runnable tryToConnectAgain = new Runnable() {

        @Override
        public void run() {
            if(sentPause) {
                countOfReconnecting++;
            }

            final LocalBroadcastManager localBroadcastManager = LocalBroadcastManager
                    .getInstance(ProjectActivity.this);

            if(countOfReconnecting == Constants.MAX_COUNT_OF_RE_CONNECTIONS_FOR_DFU) {
                countOfReconnecting = 0;
                Intent intent = new Intent(DfuService.BROADCAST_ACTION);
                intent.putExtra(DfuService.EXTRA_ACTION, DfuService.ACTION_ABORT);
                localBroadcastManager.sendBroadcast(intent);
            } else {
                final int nextAction;
                final long delayForNewlyBroadcast;

                if(sentPause) {
                    nextAction = DfuService.ACTION_RESUME;
                    delayForNewlyBroadcast = Constants.TIME_FOR_CONNECTION_COMPLETED;
                } else {
                    nextAction = DfuService.ACTION_PAUSE;
                    delayForNewlyBroadcast = Constants.DELAY_BETWEEN_PAUSE_AND_RESUME;
                }

                sentPause = !sentPause;

                Intent intent = new Intent(DfuService.BROADCAST_ACTION);
                intent.putExtra(DfuService.EXTRA_ACTION, nextAction);
                localBroadcastManager.sendBroadcast(intent);

                handler.postDelayed(this, delayForNewlyBroadcast);
            }
        }
    };

    /**
     * Allows to handle forced closing of the bluetooth service and
     * update information and UI about currently paired device.
     */
    private final BroadcastReceiver gattForceClosedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(BLEService.GATT_FORCE_CLOSED)) {
                setConnectedDeviceText();
            }
        }
    };

    /**
     * Listener for OK button on a permission requesting dialog.
     * Allows to request permission for incoming calls or incoming sms messages.
     */
    View.OnClickListener notificationOKHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("notificationOKHandler");
            PopUp.hide();
            if(mRequestingPermission == EventCategories.IPC_BLE_NOTIFICATION_INCOMING_CALL) {
                String[] permissionsNeeded = {Manifest.permission.READ_PHONE_STATE};
                requestPermission(permissionsNeeded, PermissionCodes.INCOMING_CALL_PERMISSIONS_REQUESTED);
            }
            if(mRequestingPermission == EventCategories.IPC_BLE_NOTIFICATION_INCOMING_SMS) {
                String[] permissionsNeeded = {Manifest.permission.RECEIVE_SMS};
                requestPermission(permissionsNeeded, PermissionCodes.INCOMING_SMS_PERMISSIONS_REQUESTED);
            }
        }
    };

    /**
     * Checks if there are required permissions need to be granted.
     * If true - request needed permissions.
     */
    View.OnClickListener checkMorePermissionsNeeded = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if(!mRequestPermissions.isEmpty()) {
                checkTelephonyPermissions();
            } else {
                PopUp.hide();
            }
        }
    };

    /**
     * Listener for Cancel button that dismisses permission granting.
     * Additionally shows a dialog window about dismissed permission
     * and allows to grant it.
     */
    View.OnClickListener notificationCancelHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("notificationCancelHandler");
            String msg = "Your program might not run properly";
            if(mRequestingPermission == EventCategories.IPC_BLE_NOTIFICATION_INCOMING_CALL) {
                msg = getString(R.string.telephony_permission_error);
            } else if(mRequestingPermission == EventCategories.IPC_BLE_NOTIFICATION_INCOMING_SMS) {
                msg = getString(R.string.sms_permission_error);
            }
            PopUp.hide();
            PopUp.show(msg,
                    getString(R.string.permissions_needed_title),
                    R.drawable.error_face, R.drawable.red_btn,
                    PopUp.GIFF_ANIMATION_ERROR,
                    TYPE_ALERT,
                    checkMorePermissionsNeeded, checkMorePermissionsNeeded);
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        startBluetooth();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void setActivityState(int baseActivityState) {
        mActivityState = baseActivityState;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setConnectedDeviceText();
            }
        });
    }

    @Override
    public void preUpdateUi() {
        setConnectedDeviceText();
    }

    @Override
    public int getActivityState() {
        return mActivityState;
    }

    @Override
    public void logi(String message) {
        if(DEBUG) {
            Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
        }
    }

    @Override
    public void checkTelephonyPermissions() {
        if(!mRequestPermissions.isEmpty()) {
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                    != PermissionChecker.PERMISSION_GRANTED ||
                    (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                            != PermissionChecker.PERMISSION_GRANTED)) {
                mRequestingPermission = mRequestPermissions.get(0);
                mRequestPermissions.remove(0);
                PopUp.show((mRequestingPermission == EventCategories.IPC_BLE_NOTIFICATION_INCOMING_CALL)
                                ? getString(R.string.telephony_permission)
                                : getString(R.string.sms_permission),
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
        mRequestPermissions.add(permission);
    }

    @Override
    public boolean arePermissionsGranted() {
        return mRequestPermissions.isEmpty();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        mProjectListView.setAdapter(null);

        if(mProjectListViewRight != null) {
            mProjectListViewRight.setAdapter(null);
        }

        setContentView(R.layout.activity_projects);
        initViews();
        setupFontStyle();
        setConnectedDeviceText();
        setupListAdapter();
    }

    /**
     * Setup font style by setting an appropriate typeface to needed views.
     */
    private void setupFontStyle() {
        // Title font
        TextView flashProjectsTitle = (TextView) findViewById(R.id.flash_projects_title_txt);
        flashProjectsTitle.setTypeface(MBApp.getApp().getTypeface());

        // Create projects
        TextView createProjectText = (TextView) findViewById(R.id.custom_button_text);
        createProjectText.setTypeface(MBApp.getApp().getRobotoTypeface());

        mEmptyText.setTypeface(MBApp.getApp().getTypeface());
    }

    private void initViews() {
        mProjectListView = (ListView) findViewById(R.id.projectListView);
        mEmptyText = (TextView) findViewById(R.id.project_list_empty);
        //Initializes additional list of projects for a landscape orientation.
        if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mProjectListViewRight = (ListView) findViewById(R.id.projectListViewRight);
        }
    }

    private void releaseViews() {
        mProjectListView = null;
        mProjectListViewRight = null;
        mEmptyText = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MBApp application = MBApp.getApp();

        if(savedInstanceState == null) {
            mActivityState = FlashActivityState.STATE_IDLE;

            LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(application);

            IntentFilter broadcastIntentFilter = new IntentFilter(IPCConstants.INTENT_BLE_NOTIFICATION);
            localBroadcastManager.registerReceiver(connectionChangedReceiver, broadcastIntentFilter);

            localBroadcastManager.registerReceiver(gattForceClosedReceiver, new IntentFilter(BLEService
                    .GATT_FORCE_CLOSED));
        }

        logi("onCreate() :: ");

        //Remove title bar
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_projects);
        initViews();
        setupFontStyle();

        minimumPermissionsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PermissionChecker.PERMISSION_GRANTED && (ContextCompat.checkSelfPermission(this, Manifest
                .permission.WRITE_EXTERNAL_STORAGE) == PermissionChecker.PERMISSION_GRANTED);

        checkMinimumPermissionsForThisScreen();
        setConnectedDeviceText();

        if(savedInstanceState == null && getIntent() != null) {
            handleIncomingIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if(intent != null) {
            handleIncomingIntent(intent);
        }
    }

    private void handleIncomingIntent(Intent intent) {
        boolean isOpenByOtherApp = false;
        String fullPathOfFile;
        String fileName;

        if(intent.getData() != null && intent.getData().getEncodedPath() != null) {
            isOpenByOtherApp = true;

            Uri uri = intent.getData();
            String encodedPath = uri.getEncodedPath();

            String scheme = uri.getScheme();
            if (scheme.equals("file")) {
                fullPathOfFile = URLDecoder.decode(encodedPath);
                fileName = fileNameForFlashing(fullPathOfFile);
                mProgramToSend = fileName == null ? null : new Project(fileName, fullPathOfFile, 0, null, false);
            } else if(scheme.equals("content")) {

                Cursor cursor = null;

                try {
                    cursor = getContentResolver().query(uri, null, null, null, null);

                    if (cursor != null && cursor.moveToFirst()) {
                        String selectedFileName = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document
                                .COLUMN_DISPLAY_NAME));

                        // If application is opened from My Files (Android 6.0), cursor don't contains
                        // COLUMN_DOCUMENT_ID, and stream not needed to copy to Download directory. It is already
                        // there
                        boolean isShareableApp = cursor.getColumnIndex(DocumentsContract.Document
                                .COLUMN_DOCUMENT_ID) != -1;

                        fullPathOfFile = new File(Environment.getExternalStoragePublicDirectory(Environment
                                .DIRECTORY_DOWNLOADS), selectedFileName).getAbsolutePath();

                        if (isShareableApp) {
                            try {
                                IOUtils.copy(getContentResolver().openInputStream(uri), new FileOutputStream(fullPathOfFile));
                            } catch (Exception e) {
                                Log.e(TAG, e.toString());
                            }
                        }

                        fileName = fileNameForFlashing(fullPathOfFile);

                        mProgramToSend = fileName == null ? null : new Project(fileName, fullPathOfFile, 0, null, false);
                    } else {
                        try {
                            AssetFileDescriptor fileDescriptor = getContentResolver().openAssetFileDescriptor(uri, "r");

                            if(fileDescriptor != null) {
                                long length = fileDescriptor.getLength();

                                fileDescriptor.close();

                                mProgramToSend = getLatestProjectFromFolder(length);
                            }
                        } catch(IOException e) {
                            Log.e(TAG, e.toString());
                        }
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }

            } else {
                Log.e(TAG, "Unknown schema: " + scheme);
                return;
            }
        }

        if (mProgramToSend != null) {
            if(!BluetoothChecker.getInstance().isBluetoothON()) {
                startBluetooth();
            } else {
                adviceOnMicrobitState();
                finish();
            }
        } else {
            if (isOpenByOtherApp) {
                Toast.makeText(this, "Not a micro:bit HEX file", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private Project getLatestProjectFromFolder(long lengthOfSearchingFile) {
        File downloadDirectory = Environment.getExternalStoragePublicDirectory(Environment
                .DIRECTORY_DOWNLOADS);

        FilenameFilter hexFilenameFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return filename.endsWith(".hex");
            }
        };

        File nowDownloadedFile = null;

        File downloadFiles[] = downloadDirectory.listFiles(hexFilenameFilter);

        if(downloadFiles != null) {
            for(File file : downloadFiles) {
                if(nowDownloadedFile == null) {
                    if(file.length() == lengthOfSearchingFile) {
                        nowDownloadedFile = file;
                    }
                } else if(file.length() == lengthOfSearchingFile && file.lastModified() > nowDownloadedFile.lastModified
                        ()) {
                    nowDownloadedFile = file;
                }
            }
        }

        String fullPathOfFile;
        if(nowDownloadedFile == null) {
            Log.e(TAG, "Can't find file");
            return null;
        } else {
            fullPathOfFile = nowDownloadedFile.getAbsolutePath();
            return new Project(fileNameForFlashing(fullPathOfFile), fullPathOfFile, 0, null, false);
        }
    }

    /**
     * Check file path. If file ends with .hex, then just return it name, else return {@code null}
     *
     * @param fullPathOfFile Path to file, that checks
     * @return
     */
    private String fileNameForFlashing(String fullPathOfFile) {
        String path[] = fullPathOfFile.split("/");
        setActivityState(FlashActivityState.STATE_ENABLE_BT_EXTERNAL_FLASH_REQUEST);
        if(path[path.length - 1].endsWith(".hex")) {
            return path[path.length - 1];
        } else {
            return null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(minimumPermissionsGranted) {
            updateProjectsListSortOrder(true);
        }
    }

    @Override
    protected void onDestroy() {

        handler.removeCallbacks(tryToConnectAgain);

        MBApp application = MBApp.getApp();

        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(application);

        localBroadcastManager.unregisterReceiver(gattForceClosedReceiver);
        localBroadcastManager.unregisterReceiver(connectionChangedReceiver);

        if(dfuResultReceiver != null) {
            localBroadcastManager.unregisterReceiver(dfuResultReceiver);
        }

        application.stopService(new Intent(application, DfuService.class));

        super.onDestroy();
        releaseViews();
    }

    private void requestPermission(String[] permissions, final int requestCode) {
        ActivityCompat.requestPermissions(this, permissions, requestCode);
    }

    /**
     * Listener for OK button that allows to request write/read
     * external storage permissions.
     */
    View.OnClickListener diskStoragePermissionOKHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("diskStoragePermissionOKHandler");
            PopUp.hide();
            String[] permissionsNeeded = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
            requestPermission(permissionsNeeded, PermissionCodes.APP_STORAGE_PERMISSIONS_REQUESTED);
        }
    };

    /**
     * Handler for OK button on More permission needed pop-up window that
     * closes the pop-up and updates the list of projects.
     */
    View.OnClickListener okMorePermissionNeededHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("okMorePermissionNeededHandler");
            PopUp.hide();
            mEmptyText.setVisibility(View.VISIBLE);
        }
    };

    /**
     * Shows a pop-up window "More permission needed" with message that
     * that files cannot be accessed and displayed.
     */
    private void showMorePermissionsNeededWindow() {
        PopUp.show(getString(R.string.storage_permission_for_programs_error),
                getString(R.string.permissions_needed_title),
                R.drawable.error_face, R.drawable.red_btn,
                PopUp.GIFF_ANIMATION_ERROR,
                TYPE_ALERT,
                okMorePermissionNeededHandler,
                okMorePermissionNeededHandler);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch(requestCode) {
            case PermissionCodes.APP_STORAGE_PERMISSIONS_REQUESTED: {
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    minimumPermissionsGranted = true;
                    updateProjectsListSortOrder(true);
                } else {
                    showMorePermissionsNeededWindow();
                }
            }
            break;
            case PermissionCodes.INCOMING_CALL_PERMISSIONS_REQUESTED: {
                if(grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    PopUp.show(getString(R.string.telephony_permission_error),
                            getString(R.string.permissions_needed_title),
                            R.drawable.error_face, R.drawable.red_btn,
                            PopUp.GIFF_ANIMATION_ERROR,
                            TYPE_ALERT,
                            checkMorePermissionsNeeded, checkMorePermissionsNeeded);
                } else {
                    if(!mRequestPermissions.isEmpty()) {
                        checkTelephonyPermissions();
                    }
                }
            }
            break;
            case PermissionCodes.INCOMING_SMS_PERMISSIONS_REQUESTED: {
                if(grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    PopUp.show(getString(R.string.sms_permission_error),
                            getString(R.string.permissions_needed_title),
                            R.drawable.error_face, R.drawable.red_btn,
                            PopUp.GIFF_ANIMATION_ERROR,
                            TYPE_ALERT,
                            checkMorePermissionsNeeded, checkMorePermissionsNeeded);
                } else {
                    if(!mRequestPermissions.isEmpty()) {
                        checkTelephonyPermissions();
                    }
                }
            }
            break;
        }
    }

    /**
     * Dismisses read/write external storage permissions request.
     */
    View.OnClickListener diskStoragePermissionCancelHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("diskStoragePermissionCancelHandler");
            PopUp.hide();
            showMorePermissionsNeededWindow();
        }
    };

    /**
     * Checks if needed permissions granted and updates the list,
     * shows a dialog windows to request them otherwise.
     */
    private void checkMinimumPermissionsForThisScreen() {
        if(!minimumPermissionsGranted) {
            PopUp.show(getString(R.string.storage_permission_for_programs),
                    getString(R.string.permissions_needed_title),
                    R.drawable.message_face, R.drawable.blue_btn, PopUp.GIFF_ANIMATION_NONE,
                    PopUp.TYPE_CHOICE,
                    diskStoragePermissionOKHandler,
                    diskStoragePermissionCancelHandler);
        } else {
            //We have required permission. Update the list directly
            updateProjectsListSortOrder(true);
        }
    }

    /**
     * Updates UI of current connection status and device name.
     */
    private void setConnectedDeviceText() {

        TextView connectedIndicatorText = (TextView) findViewById(R.id.connectedIndicatorText);
        connectedIndicatorText.setText(connectedIndicatorText.getText());
        connectedIndicatorText.setTypeface(MBApp.getApp().getRobotoTypeface());
        TextView deviceName = (TextView) findViewById(R.id.deviceName);
        deviceName.setContentDescription(deviceName.getText());
        deviceName.setTypeface(MBApp.getApp().getRobotoTypeface());
        deviceName.setOnClickListener(this);
        ImageView connectedIndicatorIcon = (ImageView) findViewById(R.id.connectedIndicatorIcon);

        //Override the connection Icon in case of active flashing
        if(mActivityState == FlashActivityState.FLASH_STATE_FIND_DEVICE
                || mActivityState == FlashActivityState.FLASH_STATE_VERIFY_DEVICE
                || mActivityState == FlashActivityState.FLASH_STATE_WAIT_DEVICE_REBOOT
                || mActivityState == FlashActivityState.FLASH_STATE_INIT_DEVICE
                || mActivityState == FlashActivityState.FLASH_STATE_PROGRESS
                ) {
            connectedIndicatorIcon.setImageResource(R.drawable.device_status_connected);
            connectedIndicatorText.setText(getString(R.string.connected_to));

            return;
        }
        ConnectedDevice device = BluetoothUtils.getPairedMicrobit(this);
        if(!device.mStatus) {
            connectedIndicatorIcon.setImageResource(R.drawable.device_status_disconnected);
            connectedIndicatorText.setText(getString(R.string.not_connected));
            if(device.mName != null) {
                deviceName.setText(device.mName);
            } else {
                deviceName.setText("");
            }
        } else {
            connectedIndicatorIcon.setImageResource(R.drawable.device_status_connected);
            connectedIndicatorText.setText(getString(R.string.connected_to));
            if(device.mName != null) {
                deviceName.setText(device.mName);
            } else {
                deviceName.setText("");
            }
        }
    }

    /**
     * Allows to rename file by given file path and a new file name.
     *
     * @param filePath Full path to the file.
     * @param newName  New name of the file.
     */
    public void renameFile(String filePath, String newName) {

        FileUtils.RenameResult renameResult = FileUtils.renameFile(filePath, newName);
        if(renameResult != FileUtils.RenameResult.SUCCESS) {
            AlertDialog alertDialog = new AlertDialog.Builder(this).create();
            alertDialog.setTitle("Alert");

            String message = "OOPS!";
            switch(renameResult) {
                case NEW_PATH_ALREADY_EXIST:
                    message = "Cannot rename, destination file already exists.";
                    break;

                case OLD_PATH_NOT_CORRECT:
                    message = "Cannot rename, source file not exist.";
                    break;

                case RENAME_ERROR:
                    message = "Rename operation failed.";
                    break;
            }

            alertDialog.setMessage(message);
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });

            alertDialog.show();
        } else {
            updateProjectsListSortOrder(true);
        }
    }

    /**
     * Allows to clear and reload projects list or just sort the list.
     *
     * @param reReadFS If true - clear and reload the list of projects.
     */
    public void updateProjectsListSortOrder(boolean reReadFS) {
        if(reReadFS) {
            mOldProjectList.clear();
            mOldProjectList.addAll(mProjectList);
            mProjectList.clear();
            ProjectsHelper.findProjectssAndPopulate(mPrettyFileNameMap, mProjectList);
        }

        int projectListSortOrder = Utils.getListSortOrder();
        int sortBy = (projectListSortOrder >> 1);
        int sortOrder = projectListSortOrder & 0x01;
        Utils.sortProjectList(mProjectList, sortBy, sortOrder);

        for(Project project : mProjectList) {
            int indexInProjectsBeforeReloading = mOldProjectList.indexOf(project);
            if(indexInProjectsBeforeReloading != -1) {
                Project oldProject = mOldProjectList.get(indexInProjectsBeforeReloading);
                project.inEditMode = oldProject.inEditMode;
                project.actionBarExpanded = oldProject.actionBarExpanded;
                project.runStatus = oldProject.runStatus;
            }
        }

        setupListAdapter();
    }

    /**
     * Sets a list adapter for a list view. If orientation is a landscape then the
     * list of items is split up on two lists that will be displayed in two different columns.
     */
    private void setupListAdapter() {
        ProjectAdapter projectAdapter;
        mEmptyText.setVisibility(View.GONE);
        if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            List<Project> leftList = new ArrayList<>();
            List<Project> rightList = new ArrayList<>();
            for(int i = 0; i < mProjectList.size(); i++) {
                if(i % 2 == 0) {
                    leftList.add(mProjectList.get(i));
                } else {
                    rightList.add(mProjectList.get(i));
                }
            }
            projectAdapter = new ProjectAdapter(this, leftList);
            ProjectAdapter projectAdapterRight = new ProjectAdapter(this, rightList);
            mProjectListViewRight.setAdapter(projectAdapterRight);
            if(projectAdapter.isEmpty() && projectAdapterRight.isEmpty()) {
                mEmptyText.setVisibility(View.VISIBLE);
            }
        } else {
            projectAdapter = new ProjectAdapter(this, mProjectList);
            if(projectAdapter.isEmpty()) {
                mEmptyText.setVisibility(View.VISIBLE);
            }
        }
        if(mProjectListView != null) {
            mProjectListView.setAdapter(projectAdapter);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if(requestCode == RequestCodes.REQUEST_ENABLE_BT) {
            if(resultCode == Activity.RESULT_OK) {
                if(mActivityState == FlashActivityState.STATE_ENABLE_BT_INTERNAL_FLASH_REQUEST ||
                        mActivityState == FlashActivityState.STATE_ENABLE_BT_EXTERNAL_FLASH_REQUEST) {
                    adviceOnMicrobitState();
                } else if(mActivityState == FlashActivityState.STATE_ENABLE_BT_FOR_CONNECT) {
                    setActivityState(FlashActivityState.STATE_IDLE);
                    toggleConnection();
                }
            }
            if(resultCode == Activity.RESULT_CANCELED) {
                setActivityState(FlashActivityState.STATE_IDLE);
                PopUp.show(getString(R.string.bluetooth_off_cannot_continue), //message
                        "",
                        R.drawable.error_face, R.drawable.red_btn,
                        PopUp.GIFF_ANIMATION_ERROR,
                        TYPE_ALERT,
                        null, null);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Starts activity to enable Bluetooth.
     */
    private void startBluetooth() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, RequestCodes.REQUEST_ENABLE_BT);
    }

    /**
     * Allows to enable or disable connection to a micro:bit board.
     */
    private void toggleConnection() {
        ConnectedDevice connectedDevice = BluetoothUtils.getPairedMicrobit(this);
        if(connectedDevice.mPattern != null) {
            if(connectedDevice.mStatus) {
                setActivityState(FlashActivityState.STATE_DISCONNECTING);
                PopUp.show(getString(R.string.disconnecting),
                        "",
                        R.drawable.flash_face, R.drawable.blue_btn,
                        PopUp.GIFF_ANIMATION_NONE,
                        PopUp.TYPE_SPINNER,
                        null, null);
                ServiceUtils.sendConnectDisconnectMessage(false);
            } else {
                mRequestPermissions.clear();
                setActivityState(FlashActivityState.STATE_CONNECTING);
                PopUp.show(getString(R.string.init_connection),
                        "",
                        R.drawable.flash_face, R.drawable.blue_btn,
                        PopUp.GIFF_ANIMATION_NONE,
                        PopUp.TYPE_SPINNER,
                        null, null);

                ServiceUtils.sendConnectDisconnectMessage(true);
            }
        }
    }

    /**
     * Sends a project to flash on a micro:bit board. If bluetooth is off then turn it on.
     *
     * @param project Project to flash.
     */
    public void sendProject(final Project project) {
        mProgramToSend = project;
        setActivityState(FlashActivityState.STATE_ENABLE_BT_INTERNAL_FLASH_REQUEST);
        if(!BluetoothChecker.getInstance().isBluetoothON()) {
            startBluetooth();
        } else {
            adviceOnMicrobitState();
        }
    }

    @Override
    public void onClick(final View v) {
        switch(v.getId()) {
            case R.id.createProject: {
                Intent launchMakeCodeIntent = new Intent(this, MakeCodeWebView.class);
                startActivity(launchMakeCodeIntent);
                /*
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(getString(R.string.my_scripts_url)));
                startActivity(intent);
                 */
            }
            break;

            case R.id.backBtn:
                Intent intentHomeActivity = new Intent(this, HomeActivity.class);
                intentHomeActivity.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intentHomeActivity);
                finish();
                break;

            case R.id.connectedIndicatorIcon:
                if(!BluetoothChecker.getInstance().isBluetoothON()) {
                    setActivityState(FlashActivityState.STATE_ENABLE_BT_FOR_CONNECT);
                    startBluetooth();
                } else {
                    toggleConnection();
                }
                break;
            case R.id.deviceName:
                // Toast.makeText(this, "Back to connectMaybeInit screen", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, PairingActivity.class);
                startActivity(intent);
                break;

        }
    }

    /**
     * Checks for requisite state of a micro:bit board. If all is good then
     * initiates flashing.
     */
    private void adviceOnMicrobitState() {
        ConnectedDevice currentMicrobit = BluetoothUtils.getPairedMicrobit(this);

        if(currentMicrobit.mPattern == null) {
            Intent intent = new Intent(this, PairingActivity.class);
            startActivity(intent);
            /*
            while(pairingFailed = PENDING)
            if PASS
            if FAIL cancel
            */
        } else {
            Log.v(TAG, "Start flash!");
            flashingChecks();
        }
    }

    private void flashingChecks() {
          ConnectedDevice currentMicrobit = BluetoothUtils.getPairedMicrobit(this);
//
//        if(mProgramToSend == null || mProgramToSend.filePath == null) {
//            PopUp.show(getString(R.string.internal_error_msg),
//                    "",
//                    R.drawable.error_face, R.drawable.red_btn,
//                    PopUp.GIFF_ANIMATION_ERROR,
//                    TYPE_ALERT,
//                    null, null);
//            return;
//        }

        if(mActivityState == FlashActivityState.FLASH_STATE_FIND_DEVICE
                || mActivityState == FlashActivityState.FLASH_STATE_VERIFY_DEVICE
                || mActivityState == FlashActivityState.FLASH_STATE_WAIT_DEVICE_REBOOT
                || mActivityState == FlashActivityState.FLASH_STATE_INIT_DEVICE
                || mActivityState == FlashActivityState.FLASH_STATE_PROGRESS

        ) {
            // Another download session is in progress.xml
            PopUp.show(getString(R.string.multple_flashing_session_msg),
                    "",
                    R.drawable.flash_face, R.drawable.blue_btn,
                    PopUp.GIFF_ANIMATION_FLASH,
                    TYPE_ALERT,
                    null, null);
            return;
        }

        if(mActivityState == FlashActivityState.STATE_ENABLE_BT_INTERNAL_FLASH_REQUEST ||
                mActivityState == FlashActivityState.STATE_ENABLE_BT_EXTERNAL_FLASH_REQUEST) {
            //Check final device from user and start flashing
            PopUp.show(getString(R.string.flash_start_message, currentMicrobit.mName), //message
                    getString(R.string.flashing_title), //title
                    R.drawable.flash_face, R.drawable.blue_btn, //image icon res id
                    PopUp.GIFF_ANIMATION_NONE,
                    PopUp.TYPE_CHOICE, //type of popup.
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ConnectedDevice currentMicrobit = BluetoothUtils.getPairedMicrobit(MBApp.getApp());
                            PopUp.hide();
                            initiateFlashing();
                        }
                    },//override click listener for ok button
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            PopUp.hide();
                            finish();
                        }
                    });//pass null to use default listeneronClick
        } else {
            initiateFlashing();
        }
    }

    /**
     * Prepares for flashing process.
     * <p/>
     * <p>>Unregisters DFU receiver, sets activity state to the find device state,
     * registers callbacks requisite for flashing and starts flashing.</p>
     */
    protected void initiateFlashing() {
        if(dfuResultReceiver != null) {
            LocalBroadcastManager.getInstance(MBApp.getApp()).unregisterReceiver(dfuResultReceiver);
            dfuResultReceiver = null;
        }

        /* Pop up for flash init */

        setActivityState(FlashActivityState.FLASH_STATE_FIND_DEVICE);
        registerCallbacksForFlashing();

        // Attempt Partial Flashing first
        startFlashing(FLASH_TYPE_PF);
    }

    /**
     * Convert a HEX char to int
     */
    int charToInt(char in) {
        // 0 - 9
        if(in - '0' >= 0 && in - '0' < 10) return (in - '0');

        // A - F
        return in - 55;
    }

    /**
     * Creates and starts service to flash a program to a micro:bit board.
     * int FLASH_TYPE_DFU
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    protected void startFlashing(int flashingType) {

        logi(">>>>>>>>>>>>>>>>>>> startFlashing called  >>>>>>>>>>>>>>>>>>>  ");
        //Reset all stats value
        m_BinSizeStats = "0";
        m_MicroBitFirmware = "0.0";
        m_HexFileSizeStats = getFileSize(mProgramToSend.filePath);


        PopUp.show(getString(R.string.dfu_status_starting_msg),
                "",
                R.drawable.flash_face, R.drawable.blue_btn,
                PopUp.GIFF_ANIMATION_FLASH,
                TYPE_SPINNER_NOT_CANCELABLE,
                null, null);

        ConnectedDevice currentMicrobit = BluetoothUtils.getPairedMicrobit(this);

        MBApp application = MBApp.getApp();
        int hardwareType = currentMicrobit.mhardwareVersion;


        // Create tmp hex for V1 or V2
        String[] ret = universalHexToDFU(mProgramToSend.filePath, hardwareType);
        String hexAbsolutePath = ret[0];
        int applicationSize = Integer.parseInt(ret[1]);

        if(applicationSize == -1) {
            // V1 Hex on a V2
            PopUp.show(getString(R.string.v1_hex_v2_hardware),
                    "",
                    R.drawable.message_face, R.drawable.red_btn,
                    PopUp.GIFF_ANIMATION_ERROR,
                    TYPE_ALERT,
                    null, null);
            return;
        }

        // If V2 create init packet
        String initPacketAbsolutePath = "-1";
        if(hardwareType == MICROBIT_V2) {
            try {
                initPacketAbsolutePath = createDFUInitPacket(applicationSize);
                String[] files = new String[]{initPacketAbsolutePath, hexAbsolutePath};
                createDFUZip(files);
            } catch (IOException e) {
                Log.v(TAG, "Failed to create init packet");
                e.printStackTrace();
            }
        }

        if(flashingType == FLASH_TYPE_DFU || hardwareType == MICROBIT_V2) {

            // Start DFU Service
            Log.v(TAG, "Start Full DFU");
            Log.v(TAG, "DFU hex: " + hexAbsolutePath);
            if(hardwareType == MICROBIT_V2) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    DfuServiceInitiator.createDfuNotificationChannel(this);
                }
                final DfuServiceInitiator starter = new DfuServiceInitiator(currentMicrobit.mAddress)
                        .setDeviceName(currentMicrobit.mName)
                        .setPacketsReceiptNotificationsEnabled(true)
                        .setKeepBond(true)
                        .setForceDfu(true)
                        .setZip(this.getCacheDir() + "/update.zip");
                final DfuServiceController controller = starter.start(this, DfuService.class);
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    DfuServiceInitiator.createDfuNotificationChannel(this);
                }
                final DfuServiceInitiator starter = new DfuServiceInitiator(currentMicrobit.mAddress)
                        .setDeviceName(currentMicrobit.mName)
                        .setKeepBond(true)
                        .setForceDfu(true)
                        .setPacketsReceiptNotificationsEnabled(true)
                        .setBinOrHex(DfuBaseService.TYPE_APPLICATION, hexAbsolutePath);
                final DfuServiceController controller = starter.start(this, DfuService.class);
            }

        } else if(flashingType == FLASH_TYPE_PF) {
            // Attempt a partial flash
            Log.v(TAG, "Send Partial Flashing Intent");
            if(service != null) {
                application.stopService(service);
            }
            service = new Intent(application, PartialFlashingService.class);
            service.putExtra("deviceAddress", currentMicrobit.mAddress);
            service.putExtra("filepath", hexAbsolutePath); // a path or URI must be provided.
            service.putExtra("pf", true); // Enable partial flashing
            application.startService(service);
        }
    }

    /**
     * Create zip for DFU
     */
    private String createDFUZip(String[] srcFiles ) throws IOException {
        byte[] buffer = new byte[1024];

        File zipFile = new File(this.getCacheDir() + "/update.zip");
        if (zipFile.exists()) {
            zipFile.delete();
        }
        zipFile.createNewFile();

        FileOutputStream fileOutputStream = new FileOutputStream(this.getCacheDir() + "/update.zip");
        ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);

        for (int i=0; i < srcFiles.length; i++) {

            File srcFile = new File(srcFiles[i]);
            FileInputStream fileInputStream = new FileInputStream(srcFile);
            zipOutputStream.putNextEntry(new ZipEntry(srcFile.getName()));

            int length;
            while ((length = fileInputStream.read(buffer)) > 0) {
                zipOutputStream.write(buffer, 0, length);
            }

            zipOutputStream.closeEntry();
            fileInputStream.close();

        }

        // close the ZipOutputStream
        zipOutputStream.close();

        return this.getCacheDir() + "/update.zip";
    }

    /**
     * Create DFU init packet from HEX
     * @param hexLength
     */
    private String createDFUInitPacket(int hexLength) throws IOException {
        ByteArrayOutputStream outputInitPacket;
        outputInitPacket = new ByteArrayOutputStream();

        Log.v(TAG, "DFU App Length: " + hexLength);

        outputInitPacket.write("microbit_app".getBytes()); // "microbit_app"
        outputInitPacket.write(new byte[]{0x1, 0, 0, 0});  // Init packet version
        outputInitPacket.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(hexLength).array());  // App size
        outputInitPacket.write(new byte[]{0, 0, 0, 0x0});  // Hash Size. 0: Ignore Hash
        outputInitPacket.write(new byte[]{
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0
        }); // Hash

        // Write to temp file
        File initPacket = new File(this.getCacheDir() + "/application.dat");
        if (initPacket.exists()) {
            initPacket.delete();
        }
        initPacket.createNewFile();

        FileOutputStream outputStream;
        outputStream = new FileOutputStream(initPacket);
        outputStream.write(outputInitPacket.toByteArray());
        outputStream.flush();

        // Should return from here
        return initPacket.getAbsolutePath();
    }

    /**
     * Process Universal Hex
     * @return
     */
    private String[] universalHexToDFU(String inputPath, int hardwareType) {
        FileInputStream fis;
        ByteArrayOutputStream outputHex;
        outputHex = new ByteArrayOutputStream();

        FileOutputStream outputStream;

        int application_size = 0;
        int next = 0;
        boolean records_wanted = false;
        boolean is_fat = false;
        ByteArrayOutputStream lastELA = new ByteArrayOutputStream();

        try {
            fis = new FileInputStream(inputPath);
            byte[] bs = new byte[Integer.valueOf(FileUtils.getFileSize(inputPath))];
            int i = 0;
            i = fis.read(bs);

            for (int b_x = 0; b_x < bs.length - 1; /* empty */) {
                // Get record from following bytes
                char b_type = (char) bs[b_x + 8];

                // Find next record start, or EOF
                next = 1;
                while ((b_x + next) < i && bs[b_x + next] != ':') {
                    next++;
                }

                // Switch type and determine what to do with this record
                switch (b_type) {
                    case 'A': // Block start
                        is_fat = true;

                        // Check data for id
                        if (bs[b_x + 9] == '9' && bs[b_x + 10] == '9' && bs[b_x + 11] == '0' && bs[b_x + 12] == '1') {
                            records_wanted = (hardwareType == MICROBIT_V1);
                        } else if (bs[b_x + 9] == '9' && bs[b_x + 10] == '9' && bs[b_x + 11] == '0' && bs[b_x + 12] == '3') {
                            records_wanted = (hardwareType == MICROBIT_V2);
                        }
                        break;
                    case 'E':
                        break;
                    case '4':
                        ByteArrayOutputStream currentELA = new ByteArrayOutputStream();
                        currentELA.write(bs, b_x, next);

                        // If ELA has changed write
                        if (!Arrays.equals(currentELA.toByteArray(), lastELA.toByteArray())) {
                            lastELA.reset();
                            lastELA.write(bs, b_x, next);
                            outputHex.write(bs, b_x, next);
                        }
                        break;
                    case '1':
                        // EOF
                        // Ensure KV storage is erased
                        if(hardwareType == MICROBIT_V1) {
                            String kv_address = ":020000040003F7\n";
                            String kv_data = ":1000000000000000000000000000000000000000F0\n";
                            outputHex.write(kv_address.getBytes());
                            outputHex.write(kv_data.getBytes());
                        }

                        // Write final block
                        outputHex.write(bs, b_x, next);
                        break;
                    case 'D': // V2 section of Universal Hex
                        // Remove D
                        bs[b_x + 8] = '0';
                        // Find first \n. PXT adds in extra padding occasionally
                        int first_cr = 0;
                        while(bs[b_x + first_cr] != '\n') {
                            first_cr++;
                        }

                        // Recalculate checksum
                        int checksum = (charToInt((char) bs[b_x + first_cr - 2]) * 16) + charToInt((char) bs[b_x + first_cr - 1]) + 0xD;
                        String checksum_hex = Integer.toHexString(checksum);
                        checksum_hex = "00" + checksum_hex.toUpperCase(); // Pad to ensure we have 2 characters
                        checksum_hex = checksum_hex.substring(checksum_hex.length() - 2);
                        bs[b_x + first_cr - 2] = (byte) checksum_hex.charAt(0);
                        bs[b_x + first_cr - 1] = (byte) checksum_hex.charAt(1);
                    case '5':
                    case '0':
                        // Copy record to hex
                        // Record starts at b_x, next long
                        // Calculate address of record
                        int b_ela = (charToInt((char) lastELA.toByteArray()[9]) << 12) | (charToInt((char) lastELA.toByteArray()[10]) << 8) | (charToInt((char) lastELA.toByteArray()[11]) << 4) | (charToInt((char) lastELA.toByteArray()[12]));
                        int b_raddr = (charToInt((char) bs[b_x + 3]) << 12) | (charToInt((char) bs[b_x + 4]) << 8) | (charToInt((char) bs[b_x + 5]) << 4) | (charToInt((char) bs[b_x + 6]));
                        int b_addr = b_ela << 16 | b_raddr;

                        int lower_bound = 0; int upper_bound = 0;
                        if(hardwareType == MICROBIT_V1) { lower_bound = 0x18000; upper_bound = 0x38000; }
                        if(hardwareType == MICROBIT_V2) { lower_bound = 0x27000; upper_bound = 0x71FFF; }

                        if ((records_wanted || !is_fat) && b_addr >= lower_bound && b_addr < upper_bound) {
                            outputHex.write(bs, b_x, next);
                            // Add to app size
                            application_size = application_size + charToInt((char) bs[b_x + 1]) * 16 + charToInt((char) bs[b_x + 2]);
                        }

                        break;
                    case 'C':
                    case 'B':
                        records_wanted = false;
                        break;
                    default:
                        Log.e(TAG, "Record type not recognised; TYPE: " + b_type);
                }

                // Record handled. Move to next ':'
                if ((b_x + next) >= i) {
                    break;
                } else {
                    b_x = b_x + next;
                }
            }

            byte[] output = outputHex.toByteArray();
            Log.v(TAG, "Finished parsing HEX. Writing application HEX for flashing");

            try {
                File hexToFlash = new File(this.getCacheDir() + "/application.hex");
                if (hexToFlash.exists()) {
                    hexToFlash.delete();
                }
                hexToFlash.createNewFile();

                outputStream = new FileOutputStream(hexToFlash);
                outputStream.write(output);
                outputStream.flush();

                // Should return from here
                Log.v(TAG, hexToFlash.getAbsolutePath());
                String[] ret = new String[2];
                ret[0] = hexToFlash.getAbsolutePath();
                ret[1] = Integer.toString(application_size);

                if(hardwareType == MICROBIT_V2 && is_fat == false) {
                    ret[1] = Integer.toString(-1); // Invalidate hex file
                }

                return ret;
            } catch (IOException e) {
                e.printStackTrace();
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Should not reach this
        return new String[]{"-1", "-1"};
    }

    /**
     * Registers callbacks that allows to handle flashing process
     * and react to flashing progress, errors and log some messages.
     */
    private void registerCallbacksForFlashing() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(DfuService.BROADCAST_PROGRESS);
        filter.addAction(DfuService.BROADCAST_ERROR);
        filter.addAction(DfuService.BROADCAST_LOG);
        dfuResultReceiver = new DFUResultReceiver();

        IntentFilter pfFilter = new IntentFilter();
        pfFilter.addAction(PartialFlashingService.BROADCAST_START);
        pfFilter.addAction(PartialFlashingService.BROADCAST_PROGRESS);
        pfFilter.addAction(PartialFlashingService.BROADCAST_PF_FAILED);
        pfFilter.addAction(PartialFlashingService.BROADCAST_PF_ATTEMPT_DFU);
        pfFilter.addAction(PartialFlashingService.BROADCAST_COMPLETE);
        pfResultReceiver = new pfResultReceiver();

        LocalBroadcastManager.getInstance(MBApp.getApp()).registerReceiver(pfResultReceiver, pfFilter);
        LocalBroadcastManager.getInstance(MBApp.getApp()).registerReceiver(dfuResultReceiver, filter);
    }

    /**
     * Listener for OK button that just hides a popup window.
     */
    View.OnClickListener popupOkHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("popupOkHandler");
            PopUp.hide();
        }
    };


    View.OnClickListener reconnectHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("reconnectOkHandler");
            PopUp.hide();
            toggleConnection();
        }
    };

    /**
     * Represents a broadcast receiver that allows to handle states of
     * partial flashing process.
     */
    class pfResultReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            MBApp application = MBApp.getApp();
            LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(application);

            String message = "Broadcast intent detected " + intent.getAction();
            logi("PFResultReceiver.onReceive :: " + message);
            if(intent.getAction().equals(PartialFlashingService.BROADCAST_PROGRESS)) {
                // Update UI
                Intent progressUpdate = new Intent();
                progressUpdate.setAction(INTENT_ACTION_UPDATE_PROGRESS);
                int currentProgess = intent.getIntExtra(PartialFlashingService.EXTRA_PROGRESS, 0);
                PopUp.updateProgressBar(currentProgess);
            } else if(intent.getAction().equals(PartialFlashingService.BROADCAST_COMPLETE)) {
                // Success Message
                Intent flashSuccess = new Intent();
                flashSuccess.setAction(INTENT_ACTION_UPDATE_LAYOUT);
                flashSuccess.putExtra(INTENT_EXTRA_TITLE, "Flash Complete");
                flashSuccess.putExtra(INTENT_EXTRA_MESSAGE, "");
                flashSuccess.putExtra(INTENT_GIFF_ANIMATION_CODE, 1);
                flashSuccess.putExtra(INTENT_EXTRA_TYPE, TYPE_ALERT);
                localBroadcastManager.sendBroadcast( flashSuccess );
            } else if(intent.getAction().equals(PartialFlashingService.BROADCAST_START)) {
                // Display progress
                PopUp.show("",
                        getString(R.string.send_project),
                        R.drawable.flash_face,
                        R.drawable.blue_btn,
                        PopUp.GIFF_ANIMATION_FLASH,
                        TYPE_PROGRESS_NOT_CANCELABLE,
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                //Do nothing. As this is non-cancellable pop-up

                            }
                        },//override click listener for ok button
                        null);//pass null to use default listener
            } else if(intent.getAction().equals(PartialFlashingService.BROADCAST_PF_ATTEMPT_DFU)) {
                Log.v(TAG, "Use Nordic DFU");
                startFlashing(FLASH_TYPE_DFU);
            } else if(intent.getAction().equals(PartialFlashingService.BROADCAST_PF_FAILED)) {

                Log.v(TAG, "Partial flashing failed");
                previousPartialFlashFailed = true;

                // If Partial Flashing Fails - DON'T ATTEMPT FULL DFU automatically
                // Set flag to avoid partial flash next time
                PopUp.show(getString(R.string.could_not_connect), //message
                        getString(R.string.could_not_connect_title),
                        R.drawable.error_face, R.drawable.red_btn,
                        PopUp.GIFF_ANIMATION_PAIRING,
                        TYPE_ALERT, //type of popup.
                        popupOkHandler,//override click listener for ok button
                        popupOkHandler);//pass null to use default listener
            }

        }
    }

    /**
     * Represents a broadcast receiver that allows to handle states of
     * flashing process.
     */
    class DFUResultReceiver extends BroadcastReceiver {

        private boolean isCompleted = false;
        private boolean inInit = false;
        private boolean inProgress = false;

        private View.OnClickListener okFinishFlashingHandler = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logi("popupOkHandler");
                PopUp.hide();
            }
        };

        @Override
        public void onReceive(Context context, Intent intent) {
            String message = "Broadcast intent detected " + intent.getAction();
            logi("DFUResultReceiver.onReceive :: " + message);
            if(intent.getAction().equals(DfuService.BROADCAST_PROGRESS)) {
                int state = intent.getIntExtra(DfuService.EXTRA_DATA, 0);
                if(state < 0) {
                    logi("DFUResultReceiver.onReceive :: state -- " + state);
                    switch(state) {
                        case DfuService.PROGRESS_STARTING:
                            setActivityState(FlashActivityState.FLASH_STATE_INIT_DEVICE);
                            PopUp.show(getString(R.string.dfu_status_starting_msg), //message
                                    getString(R.string.send_project), //title
                                    R.drawable.flash_face, R.drawable.blue_btn,
                                    PopUp.GIFF_ANIMATION_FLASH,
                                    PopUp.TYPE_SPINNER_NOT_CANCELABLE, //type of popup.
                                    new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            //Do nothing. As this is non-cancellable pop-up

                                        }
                                    },//override click listener for ok button
                                    null);//pass null to use default listener
                            break;
                        case DfuService.PROGRESS_COMPLETED:
                            if (!isCompleted) {
                                setActivityState(FlashActivityState.STATE_IDLE);

                                MBApp application = MBApp.getApp();

                                LocalBroadcastManager.getInstance(application).unregisterReceiver(dfuResultReceiver);
                                dfuResultReceiver = null;
                                /* Update Stats
                                GoogleAnalyticsManager.getInstance().sendFlashStats(
                                        ProjectActivity.class.getSimpleName(),
                                        true, mProgramToSend.name,
                                        m_HexFileSizeStats,
                                        m_BinSizeStats, m_MicroBitFirmware);
                                        */
                                ServiceUtils.sendConnectDisconnectMessage(false);

                                previousPartialFlashFailed = false;

                                PopUp.show(getString(R.string.flashing_success_message), //message
                                        getString(R.string.flashing_success_title), //title
                                        R.drawable.message_face, R.drawable.blue_btn,
                                        PopUp.GIFF_ANIMATION_NONE,
                                        TYPE_ALERT, //type of popup.
                                        okFinishFlashingHandler,//override click listener for ok button
                                        okFinishFlashingHandler);//pass null to use default listener
                            }

                            isCompleted = true;
                            inInit = false;
                            inProgress = false;

                            break;
                        case DfuService.PROGRESS_DISCONNECTING:
                            Log.e(TAG, "Progress disconnecting");
                            break;

                        case DfuService.PROGRESS_CONNECTING:
                            if ((!inInit) && (!isCompleted)) {
                                setActivityState(FlashActivityState.FLASH_STATE_INIT_DEVICE);
                                PopUp.show(getString(R.string.init_connection), //message
                                        getString(R.string.send_project), //title
                                        R.drawable.flash_face, R.drawable.blue_btn,
                                        PopUp.GIFF_ANIMATION_FLASH,
                                        PopUp.TYPE_SPINNER_NOT_CANCELABLE, //type of popup.
                                        new View.OnClickListener() {
                                            @Override
                                            public void onClick(View v) {
                                                //Do nothing. As this is non-cancellable pop-up
                                            }
                                        },//override click listener for ok button
                                        null);//pass null to use default listener

                                countOfReconnecting = 0;
                                sentPause = false;

                                long delayForCheckOnConnection = Constants.TIME_FOR_CONNECTION_COMPLETED;

                                if (notAValidFlashHexFile) {
                                    notAValidFlashHexFile = false;
                                    delayForCheckOnConnection += Constants.JUST_PAIRED_DELAY_ON_CONNECTION;
                                }

                                handler.postDelayed(tryToConnectAgain, delayForCheckOnConnection);
                            }

                            inInit = true;
                            isCompleted = false;
                            break;
                        case DfuService.PROGRESS_VALIDATING:
                            setActivityState(FlashActivityState.FLASH_STATE_VERIFY_DEVICE);
                            PopUp.show(getString(R.string.validating_microbit), //message
                                    getString(R.string.send_project), //title
                                    R.drawable.flash_face, R.drawable.blue_btn,
                                    PopUp.GIFF_ANIMATION_FLASH,
                                    PopUp.TYPE_SPINNER_NOT_CANCELABLE, //type of popup.
                                    new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            //Do nothing. As this is non-cancellable pop-up

                                        }
                                    },//override click listener for ok button
                                    null);//pass null to use default listener
                            break;

                        case DfuService.PROGRESS_ENABLING_DFU_MODE:
                            setActivityState(FlashActivityState.FLASH_STATE_WAIT_DEVICE_REBOOT);
                            PopUp.show(getString(R.string.waiting_reboot), //message
                                    getString(R.string.send_project), //title
                                    R.drawable.flash_face, R.drawable.blue_btn,
                                    PopUp.GIFF_ANIMATION_FLASH,
                                    PopUp.TYPE_SPINNER_NOT_CANCELABLE, //type of popup.
                                    new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            //Do nothing. As this is non-cancellable pop-up

                                        }
                                    },//override click listener for ok button
                                    null);//pass null to use default listener
                            break;
                        /*
                        case DfuService.PROGRESS_VALIDATING:
                            setActivityState(FlashActivityState.STATE_IDLE);

                            MBApp application = MBApp.getApp();

                            //Update Stats
                            GoogleAnalyticsManager.getInstance().sendFlashStats(
                                    ProjectActivity.class.getSimpleName(),
                                    false, mProgramToSend.name,
                                    m_HexFileSizeStats,
                                    m_BinSizeStats, m_MicroBitFirmware);
                            PopUp.show(getString(R.string.flashing_verifcation_failed), //message
                                    getString(R.string.flashing_verifcation_failed_title),
                                    R.drawable.error_face, R.drawable.red_btn,
                                    PopUp.GIFF_ANIMATION_ERROR,
                                    PopUp.TYPE_ALERT, //type of popup.
                                    popupOkHandler,//override click listener for ok button
                                    popupOkHandler);//pass null to use default listener

                            LocalBroadcastManager.getInstance(application).unregisterReceiver(dfuResultReceiver);
                            dfuResultReceiver = null;
                            break;
                        */
                        case DfuService.PROGRESS_ABORTED:
                            setActivityState(FlashActivityState.STATE_IDLE);

                            MBApp application = MBApp.getApp();

                            //Update Stats
                            /*
                            GoogleAnalyticsManager.getInstance().sendFlashStats(
                                    ProjectActivity.class.getSimpleName(),
                                    false, mProgramToSend.name,
                                    m_HexFileSizeStats,
                                    m_BinSizeStats, m_MicroBitFirmware);
                                    */
                            PopUp.show(getString(R.string.flashing_aborted), //message
                                    getString(R.string.flashing_aborted_title),
                                    R.drawable.error_face, R.drawable.red_btn,
                                    PopUp.GIFF_ANIMATION_ERROR,
                                    TYPE_ALERT, //type of popup.
                                    popupOkHandler,//override click listener for ok button
                                    popupOkHandler);//pass null to use default listener

                            LocalBroadcastManager.getInstance(application).unregisterReceiver(dfuResultReceiver);
                            dfuResultReceiver = null;
                            removeReconnectionRunnable();
                            break;
                        /*
                        case DfuService.PROGRESS_SERVICE_NOT_FOUND:
                            Log.e(TAG, "service not found");
                            setActivityState(FlashActivityState.STATE_IDLE);

                            application = MBApp.getApp();

                            //Update Stats
                            GoogleAnalyticsManager.getInstance().sendFlashStats(
                                    ProjectActivity.class.getSimpleName(),
                                    false, mProgramToSend.name,
                                    m_HexFileSizeStats,
                                    m_BinSizeStats, m_MicroBitFirmware);
                            PopUp.show(getString(R.string.flashing_aborted), //message
                                    getString(R.string.flashing_aborted_title),
                                    R.drawable.error_face, R.drawable.red_btn,
                                    PopUp.GIFF_ANIMATION_ERROR,
                                    PopUp.TYPE_ALERT, //type of popup.
                                    popupOkHandler,//override click listener for ok button
                                    popupOkHandler);//pass null to use default listener

                            LocalBroadcastManager.getInstance(application).unregisterReceiver(dfuResultReceiver);
                            dfuResultReceiver = null;
                            removeReconnectionRunnable();
                            break;
                            */
                        default:
                            Log.v(TAG, "No handler!: " + state);
                    }

                } else if((state > 0) && (state < 100)) {
                    if(!inProgress) {
                        setActivityState(FlashActivityState.FLASH_STATE_PROGRESS);

                        MBApp application = MBApp.getApp();

                        PopUp.hide();
                        PopUp.show(application.getString(R.string.flashing_progress_message),
                                String.format(application.getString(R.string.flashing_project), mProgramToSend.name),
                                R.drawable.flash_modal_emoji, 0,
                                PopUp.GIFF_ANIMATION_FLASH,
                                TYPE_PROGRESS_NOT_CANCELABLE, null, null);

                        inProgress = true;

                        removeReconnectionRunnable();
                    }

                    PopUp.updateProgressBar(state);

                }
            } else if(intent.getAction().equals(DfuService.BROADCAST_ERROR)) {
                int errorCode = intent.getIntExtra(DfuService.EXTRA_DATA, 0);

                if(errorCode == DfuService.ERROR_FILE_INVALID) {
                    notAValidFlashHexFile = true;
                }

                String error_message = GattError.parse(errorCode);

                if(errorCode == DfuService.ERROR_FILE_INVALID) {
                    error_message += getString(R.string.reset_microbit_because_of_hex_file_wrong);
                }

                logi("DFUResultReceiver.onReceive() :: Flashing ERROR!!  Code - [" + intent.getIntExtra(DfuService.EXTRA_DATA, 0)
                        + "] Error Type - [" + intent.getIntExtra(DfuService.EXTRA_ERROR_TYPE, 0) + "]");

                setActivityState(FlashActivityState.STATE_IDLE);

                MBApp application = MBApp.getApp();

                LocalBroadcastManager.getInstance(application).unregisterReceiver(dfuResultReceiver);
                dfuResultReceiver = null;
                //Update Stats
                /*
                GoogleAnalyticsManager.getInstance().sendFlashStats(
                        ProjectActivity.class.getSimpleName(),
                        false, mProgramToSend.name, m_HexFileSizeStats,
                        m_BinSizeStats, m_MicroBitFirmware);
                        */

                //Check for GATT ERROR - prompt user to enter bluetooth mode
                if(errorCode == 0x0085) {
                    PopUp.show(getString(R.string.connect_tip_text),
                            "Remember to enter bluetooth mode",
                            R.drawable.message_face, R.drawable.red_btn,
                            PopUp.GIFF_ANIMATION_PAIRING,
                            PopUp.TYPE_CHOICE,
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    PopUp.hide();
                                    flashingChecks();
                                }
                            },
                            popupOkHandler);
                } else {
                    PopUp.show(error_message + "\n\n" + getString(R.string.connect_tip_text), //message
                            getString(R.string.flashing_failed_title), //title
                            R.drawable.error_face, R.drawable.red_btn,
                            PopUp.GIFF_ANIMATION_ERROR,
                            TYPE_ALERT, //type of popup.
                            popupOkHandler,//override click listener for ok button
                            popupOkHandler);//pass null to use default listener
                }
                removeReconnectionRunnable();
            } else if(intent.getAction().equals(DfuService.BROADCAST_LOG)) {
                //Only used for Stats at the moment
                String data;
                int logLevel = intent.getIntExtra(DfuService.EXTRA_LOG_LEVEL, 0);
                /*
                switch(logLevel) {
                    case DfuService.LOG_LEVEL_BINARY_SIZE:
                        data = intent.getStringExtra(DfuService.EXTRA_DATA);
                        m_BinSizeStats = data;
                        break;
                    case DfuService.LOG_LEVEL_FIRMWARE:
                        data = intent.getStringExtra(DfuService.EXTRA_DATA);
                        m_MicroBitFirmware = data;
                        break;
                }
                */
            }
        }

    }

    private void removeReconnectionRunnable() {
        handler.removeCallbacks(tryToConnectAgain);
        countOfReconnecting = 0;
        sentPause = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
}
