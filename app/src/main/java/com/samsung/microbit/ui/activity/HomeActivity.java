package com.samsung.microbit.ui.activity;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.samsung.microbit.MBApp;
import com.samsung.microbit.R;
import com.samsung.microbit.core.bluetooth.BluetoothUtils;
import com.samsung.microbit.data.constants.PermissionCodes;
import com.samsung.microbit.data.model.ConnectedDevice;
import com.samsung.microbit.service.IPCService;
import com.samsung.microbit.ui.PopUp;
import com.samsung.microbit.ui.UIUtils;
import com.samsung.microbit.utils.FileUtils;
import com.samsung.microbit.utils.ProjectsHelper;
import com.samsung.microbit.utils.Utils;

import pl.droidsonroids.gif.GifImageView;

import static com.samsung.microbit.BuildConfig.DEBUG;

/**
 * Represents a home screen. Allows to navigate to all functionality
 * that the app provides.
 */
public class HomeActivity extends AppCompatActivity implements View.OnClickListener, OnLongClickListener {
    private static final String TAG = HomeActivity.class.getSimpleName();

    public static final String FIRST_RUN = "firstrun";
    public static final String FIRST_RUN_300 = "firstrun300";
    public static final String FIRST_RUN_301 = "firstrun301";

    // share stats checkbox
    private CheckBox mShareStatsCheckBox;

    SharedPreferences mPrefs = null;

    // Hello animation
    private GifImageView gifAnimationHelloEmoji;

    private DrawerLayout mDrawer;

    /* Debug code*/
    private String urlToOpen;
    /* Debug code ends*/

    private String emailBodyString;

    /**
     * Provides simplified way to log informational messages.
     *
     * @param message Message to log.
     */
    private void logi(String message) {
        if(DEBUG) {
            Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        //handle orientation change to prevent re-creation of activity.
        super.onConfigurationChanged(newConfig);

        unbindDrawables();

        setContentView(R.layout.activity_home);
        setupDrawer();
        setupButtonsFontStyle();
        initGifImage();
    }

    /**
     * Initializes the gif image and sets a resource.
     */
    private void initGifImage() {
        gifAnimationHelloEmoji = (GifImageView) findViewById(R.id.homeHelloAnimationGifView);
        gifAnimationHelloEmoji.setImageResource(R.drawable.hello_emoji_animation);
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logi("onCreate() :: ");

        setContentView(R.layout.activity_home);

        if(savedInstanceState == null) {
            startService(new Intent(this, IPCService.class));
        }

        setupDrawer();
        setupButtonsFontStyle();

        checkMinimumPermissionsForThisScreen();

        /* Debug code*/
        MenuItem item = (MenuItem) findViewById(R.id.live);
        if(item != null) {
            item.setChecked(true);
        }

        initGifImage();
    }

    /**
     * Sets buttons font style by setting an appropriate typeface.
     */
    private void setupButtonsFontStyle() {
        Typeface typeface = MBApp.getApp().getTypeface();

        Button connectButton = (Button) findViewById(R.id.connect_device_btn);
        connectButton.setTypeface(typeface);
        Button flashButton = (Button) findViewById(R.id.flash_microbit_btn);
        flashButton.setTypeface(typeface);
        Button createCodeButton = (Button) findViewById(R.id.create_code_btn);
        createCodeButton.setTypeface(typeface);
        Button discoverButton = (Button) findViewById(R.id.discover_btn);
        discoverButton.setTypeface(typeface);
    }

    /**
     * Creates and setups side navigation menu.
     */
    private void setupDrawer() {

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setNavigationContentDescription(R.string.content_description_toolbar_home);
        ImageView imgToolbarLogo = (ImageView) findViewById(R.id.img_toolbar_logo);
        imgToolbarLogo.setContentDescription("Micro:bit");
        setSupportActionBar(toolbar);

        final boolean previousDrawerState = mDrawer != null && mDrawer.isDrawerOpen(GravityCompat.START);

        mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawer.setDrawerTitle(GravityCompat.START, "Menu"); // TODO - Accessibility for touching the drawer

        if(previousDrawerState) {
            mDrawer.openDrawer(GravityCompat.START);
        }


        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                (Activity) this, (DrawerLayout) mDrawer, R.string.navigation_drawer_open, R.string.navigation_drawer_close);

        boolean shareStats = false;
        mPrefs = getSharedPreferences("com.samsung.microbit", MODE_PRIVATE);
        if(mPrefs != null) {
            shareStats = mPrefs.getBoolean(getString(R.string.prefs_share_stats_status), true);
        }
        //TODO focusable view
        mDrawer.setDrawerListener(toggle);

        toggle.syncState();

        /* Todo [Hack]:
        * NavigationView items for selection by user using
        * onClick listener instead of overriding onNavigationItemSelected*/
        findViewById(R.id.homeHelloAnimationGifView).setOnLongClickListener(this);

        Button menuNavBtn = (Button) findViewById(R.id.btn_nav_menu);
        menuNavBtn.setTypeface(MBApp.getApp().getTypeface());
        findViewById(R.id.btn_nav_menu).setOnClickListener(this);

        Button aboutNavBtn = (Button) findViewById(R.id.btn_about);
        aboutNavBtn.setTypeface(MBApp.getApp().getTypeface());
        findViewById(R.id.btn_about).setOnClickListener(this);

        Button helpNavBtn = (Button) findViewById(R.id.btn_help);
        helpNavBtn.setTypeface(MBApp.getApp().getTypeface());
        findViewById(R.id.btn_help).setOnClickListener(this);

        Button privacyNavBtn = (Button) findViewById(R.id.btn_privacy_cookies);
        privacyNavBtn.setTypeface(MBApp.getApp().getTypeface());
        findViewById(R.id.btn_privacy_cookies).setOnClickListener(this);

        Button termsNavBtn = (Button) findViewById(R.id.btn_terms_conditions);
        termsNavBtn.setTypeface(MBApp.getApp().getTypeface());
        findViewById(R.id.btn_terms_conditions).setOnClickListener(this);

        Button sendFeedbackNavBtn = (Button) findViewById(R.id.btn_send_feedback);
        sendFeedbackNavBtn.setTypeface(MBApp.getApp().getTypeface());
        findViewById(R.id.btn_send_feedback).setOnClickListener(this);

        // Share stats checkbox
        TextView shareStatsCheckTitle = (TextView) findViewById(R.id.share_statistics_title);
        shareStatsCheckTitle.setTypeface(MBApp.getApp().getTypeface());
        TextView shareStatsDescription = (TextView) findViewById(R.id.share_statistics_description);
        shareStatsDescription.setTypeface(MBApp.getApp().getRobotoTypeface());
        mShareStatsCheckBox = (CheckBox) findViewById(R.id.share_statistics_status);
        mShareStatsCheckBox.setOnClickListener(this);
        // mShareStatsCheckBox.setChecked(shareStats);
    }

    /**
     * Creates email body to send statistics. Adds information about a device.
     *
     * @return Email body with device information.
     */
    private String prepareEmailBody() {
        if(emailBodyString != null) {
            return emailBodyString;
        }
        String emailBody = getString(R.string.email_body);
        String version = "0.1.0";
        try {
            version = MBApp.getApp().getPackageManager()
                    .getPackageInfo(MBApp.getApp().getPackageName(), 0).versionName;
        } catch(PackageManager.NameNotFoundException e) {
            Log.e(TAG, e.toString());
        }
        emailBodyString = String.format(emailBody,
                version,
                Build.MODEL,
                Build.VERSION.RELEASE,
                getString(R.string.privacy_policy_url));
        return emailBodyString;
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if(drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unbindDrawables();
    }

    private void unbindDrawables() {
        Utils.unbindDrawables(gifAnimationHelloEmoji);
        Utils.unbindDrawables(findViewById(R.id.connect_device_btn));
        Utils.unbindDrawables(findViewById(R.id.flash_microbit_btn));
        Utils.unbindDrawables(findViewById(R.id.create_code_btn));
        Utils.unbindDrawables(findViewById(R.id.discover_btn));

        Utils.unbindDrawables(findViewById(R.id.img_toolbar_logo));
        Utils.unbindDrawables(findViewById(R.id.toolbar));
        Utils.unbindDrawables(findViewById(R.id.nav_view));
        Utils.unbindDrawables(findViewById(R.id.drawer_layout));
        Utils.unbindDrawables(findViewById(R.id.btn_nav_menu));
        Utils.unbindDrawables(findViewById(R.id.btn_about));
        Utils.unbindDrawables(findViewById(R.id.btn_help));
        Utils.unbindDrawables(findViewById(R.id.btn_privacy_cookies));
        Utils.unbindDrawables(findViewById(R.id.btn_terms_conditions));
        Utils.unbindDrawables(findViewById(R.id.btn_send_feedback));
        Utils.unbindDrawables(findViewById(R.id.share_statistics_title));
        Utils.unbindDrawables(findViewById(R.id.share_statistics_description));
        Utils.unbindDrawables(findViewById(R.id.share_statistics_status));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        urlToOpen = getString(R.string.create_code_url);
        switch(id) {
            case R.id.live:
                item.setChecked(true);
                break;
            case R.id.stage:
                item.setChecked(true);
                urlToOpen = urlToOpen.replace("www", "stage");
                break;
            case R.id.test:
                item.setChecked(true);
                urlToOpen = urlToOpen.replace("www", "test");
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Pause animation
        gifAnimationHelloEmoji.setFreezesAnimation(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        /* function may be needed */
    }

    @Override
    public boolean onLongClick(final View v) {
        switch(v.getId()) {
            case R.id.homeHelloAnimationGifView: {
                AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogDarkButtons);
                builder.setTitle("Edit Editor URL");

                final EditText editorURL = new EditText(this);
                editorURL.setText(MakeCodeWebView.makecodeUrl);
                editorURL.setInputType(InputType.TYPE_CLASS_TEXT);
                builder.setView(editorURL);

                builder.setPositiveButton("Set", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MakeCodeWebView.setMakecodeUrl(editorURL.getText().toString());
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder.show();

                break;
            }
        }
        return false;
    }

    @Override
    public void onClick(final View v) {
        if(DEBUG) logi("onBtnClicked() :: ");

        // Drawer closes only after certain items are selected from the Navigation View
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);

        switch(v.getId()) {
//            case R.id.addDevice:
            case R.id.connect_device_btn: {
                Intent intent = new Intent(this, PairingActivity.class);
                startActivity(intent);
            }
            break;
            case R.id.create_code_btn: {
                Intent launchMakeCodeIntent = new Intent(this, MakeCodeWebView.class);
                startActivity(launchMakeCodeIntent);
            }
            break;
            case R.id.flash_microbit_btn:
                Intent i = new Intent(this, ProjectActivity.class);
                startActivity(i);
                break;
            case R.id.discover_btn:
                UIUtils.safelyStartActivityViewURL( this, true, getString(R.string.discover_url));
                break;

            // TODO: HACK - Navigation View items from drawer here instead of [onNavigationItemSelected]
            // NavigationView items
            case R.id.btn_nav_menu: {
                // Close drawer
                drawer.closeDrawer(GravityCompat.START);
            }
            break;
            case R.id.btn_about: {
                UIUtils.safelyStartActivityViewURL( this, true, getString(R.string.about_url));
                // Close drawer
                drawer.closeDrawer(GravityCompat.START);
            }
            break;
            case R.id.btn_help: {
                Intent launchHelpIntent = new Intent(this, HelpWebView.class);
                launchHelpIntent.putExtra("url", "file:///android_asset/help.html");
                startActivity(launchHelpIntent);
                // Close drawer
                drawer.closeDrawer(GravityCompat.START);
            }
            break;
            case R.id.btn_privacy_cookies: {
                UIUtils.safelyStartActivityViewURL( this, true, getString(R.string.privacy_policy_url));
                // Close drawer
                drawer.closeDrawer(GravityCompat.START);
            }
            break;
            case R.id.btn_terms_conditions: {
                UIUtils.safelyStartActivityViewURL( this, true, getString(R.string.terms_of_use_url));
                // Close drawer
                drawer.closeDrawer(GravityCompat.START);
            }
            break;

            case R.id.btn_send_feedback: {
                Intent feedbackIntent = new Intent(Intent.ACTION_SEND);
                feedbackIntent.setType("message/rfc822");
                feedbackIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.feedback_email_address)});
                feedbackIntent.putExtra(Intent.EXTRA_SUBJECT, "[User feedback] ");
                //Prepare the body of email
                String body = prepareEmailBody();
                feedbackIntent.putExtra(Intent.EXTRA_TEXT, Html.fromHtml(body));
                Intent mailer = Intent.createChooser(feedbackIntent, null);
                UIUtils.safelyStartActivity( this, true, mailer);
                // Close drawer
                if(drawer != null) {
                    drawer.closeDrawer(GravityCompat.START);
                }
            }
            break;
            case R.id.share_statistics_status: {
                toggleShareStatistics();
            }
            break;

            default:
                break;

        }//Switch Ends
    }

    /**
     * Allows to turn on/off sharing statistics ability.
     */
    private void toggleShareStatistics() {
        if(mShareStatsCheckBox == null) {
            return;
        }
        boolean shareStatistics = mShareStatsCheckBox.isChecked();

        mPrefs.edit().putBoolean(getString(R.string.prefs_share_stats_status), shareStatistics).apply();
        logi("shareStatistics = " + shareStatistics);
    }

    private boolean isFirstRun() {
        return mPrefs == null || mPrefs.getBoolean(FIRST_RUN, true);
    }

    private void setFirstRun( boolean yes) {
        if (mPrefs != null)
            mPrefs.edit().putBoolean(FIRST_RUN, yes).apply();
    }

    private boolean isFirstRun300() {
        return mPrefs.getBoolean(FIRST_RUN_300, true);
    }

    private void setFirstRun300( boolean yes) {
        mPrefs.edit().putBoolean(FIRST_RUN_300, yes).apply();
    }

    private boolean isFirstRun301() {
        return mPrefs.getBoolean(FIRST_RUN_301, true);
    }

    private void setFirstRun301( boolean yes) {
        mPrefs.edit().putBoolean(FIRST_RUN_301, yes).apply();
    }

    /**
     * Loads standard samples provided by Samsung. The samples can be used to
     * flash on a micro:bit board.
     */
    private void installSamples( boolean withThanks) {
        boolean firstRun    = isFirstRun();
        boolean firstRun300 = isFirstRun300();
        boolean firstRun301 = isFirstRun301();
        if ( firstRun) setFirstRun(false);
        if ( firstRun300) setFirstRun300(false);
        if ( firstRun301) setFirstRun301(false);

        if( firstRun || firstRun301 || firstRun300 && !ProjectsHelper.legacyStorage()) {
            //First Run. Install the Sample applications
            if ( withThanks) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        PopUp.show(getString(R.string.samples_are_about_to_be_copied),
                                "Thank you",
                                R.drawable.message_face, R.drawable.blue_btn,
                                PopUp.GIFF_ANIMATION_NONE,
                                PopUp.TYPE_ALERT,
                                null, null);
                        ProjectsHelper.installSamples(MBApp.getApp().getBaseContext());
                    }
                }).start();
            } else {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ProjectsHelper.installSamples( MBApp.getApp().getBaseContext());
                    }
                }).start();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch(requestCode) {
            case PermissionCodes.APP_STORAGE_PERMISSIONS_REQUESTED: {
                if ( ProjectsHelper.havePermissions(this)) {
                    installSamples( true);
                } else {
                    setFirstRun(false);
                    setFirstRun300(false);
                    setFirstRun301(false);
                    PopUp.show(getString(R.string.storage_permission_for_samples_error),
                            "",
                            R.drawable.error_face, R.drawable.red_btn,
                            PopUp.GIFF_ANIMATION_ERROR,
                            PopUp.TYPE_ALERT,
                            null, null);
                }
            }
            break;

        }
    }

    private void storageRequestPermission() {
        ProjectsHelper.requestPermissions(this, PermissionCodes.APP_STORAGE_PERMISSIONS_REQUESTED);
    }

    /**
     * Requests required external storage permissions.
     */
    View.OnClickListener diskStoragePermissionOKHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("diskStoragePermissionOKHandler");
            PopUp.hide();
            storageRequestPermission();
        }
    };

    /**
     * Provides action if a user canceled storage permission granting.
     */
    View.OnClickListener diskStoragePermissionCancelHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("diskStoragePermissionCancelHandler");
            PopUp.hide();
            PopUp.show(getString(R.string.storage_permission_for_samples_error),
                    "",
                    R.drawable.error_face, R.drawable.red_btn,
                    PopUp.GIFF_ANIMATION_ERROR,
                    PopUp.TYPE_ALERT,
                    null, null);
            setFirstRun(false);
            setFirstRun300(false);
            setFirstRun301(false);
        }
    };

    /**
     * Checks and requests for external storage permissions
     * if the app is started at the first time.
     */
    private void checkMinimumPermissionsForThisScreen() {
        //Check reading permissions & writing permission to populate the HEX files & show program list
        if( isFirstRun() || isFirstRun301() || isFirstRun300() && !ProjectsHelper.legacyStorage()) {
            if( !ProjectsHelper.havePermissions(this)) {
                PopUp.show(getString(R.string.storage_permission_for_samples),
                        getString(R.string.permissions_needed_title),
                        R.drawable.message_face, R.drawable.blue_btn, PopUp.GIFF_ANIMATION_NONE,
                        PopUp.TYPE_CHOICE,
                        diskStoragePermissionOKHandler,
                        diskStoragePermissionCancelHandler);
            } else {
                installSamples( false);
            }
        }
    }

    @Override
    public void onResume() {
        if(DEBUG) logi("onResume() :: ");
        super.onResume();
        if(gifAnimationHelloEmoji != null) {
            gifAnimationHelloEmoji.animate();
        }
    }

}
