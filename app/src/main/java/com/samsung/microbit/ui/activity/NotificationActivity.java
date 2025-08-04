package com.samsung.microbit.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;

import com.samsung.microbit.R;

public class NotificationActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // TODO: EdgeToEdge - Remove once activities handle insets.
        // Call before the DecorView is accessed in setContentView
        getTheme().applyStyle(R.style.OptOutEdgeToEdgeEnforcement, /* force */ false);

        super.onCreate(savedInstanceState);

        // If this activity is the root activity of the task, the app is not running
        if(isTaskRoot()) {
            // Start the app before finishing
            final Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtras(getIntent().getExtras()); // copy all extras
            startActivity(intent);
        }

        // Now finish, which will drop you to the activity at which you were at the top of the task stack
        finish();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

}
