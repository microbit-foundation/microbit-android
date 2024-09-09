package com.samsung.microbit.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowMetrics;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.samsung.microbit.R;

import static com.samsung.microbit.BuildConfig.DEBUG;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.IdRes;

import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

public class UIUtils {
    private static final String TAG = UIUtils.class.getSimpleName();

    public void logi(String message) {
        if(DEBUG) {
            Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
        }
    }

    /**
     * Callback interface for client
     */
    public interface Client {
        Activity uiUtilsActivity();
    }

    Client mClient = null;

    public UIUtils ( Client client) {
        mClient = client;
    }

    @NonNull
    public Point displayWindowSize() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            WindowMetrics wm = null;
            wm = mClient.uiUtilsActivity().getWindowManager().getCurrentWindowMetrics();
            Rect r = wm.getBounds();
            return new Point( r.width(), r.height());
        }
        Point s = new Point();
        mClient.uiUtilsActivity().getWindowManager().getDefaultDisplay().getSize(s);
        return s;
    }

    public float labelFontSize()
    {
        Point s = displayWindowSize();

        float fh;
        float fw;
        if ( s.y > s.x) // portrait
        {
            fh = (float) (12 * s.y) / 480;
            fw = (float) (12 * s.x) / 320;
        }
        else
        {
            fh = (float) (12 * s.y) / 320;
            fw = (float) (12 * s.x) / 480;
        }
        float f = Math.min(fw, fh);
        logi( "displayLabelFontSize " + (double)f + ", " + (double)fw + ", " + (double)fh);
        return f;
    }


    public float headerFontSize()
    {
        Point s = displayWindowSize();

        float fh;
        float fw;
        if ( s.y > s.x) // portrait
        {
            fh = (float) (14 * s.y) / 480;
            fw = (float) (14 * s.x) / 320;
        }
        else
        {
            fh = (float) (14 * s.y) / 320;
            fw = (float) (14 * s.x) / 480;
        }
        float f = Math.min(fw, fh);
        logi( "displayHeaderFontSize " + (double)f + ", " + (double)fw + ", " + (double)fh);
        return f;
    }


    public float buttonFontSize()
    {
        Point s = displayWindowSize();

        float fh;
        float fw;
        if ( s.y > s.x) // portrait
        {
            fh = (float) (10 * s.y) / 480;
            fw = (float) (10 * s.x) / 320;
        }
        else
        {
            fh = (float) (10 * s.y) / 320;
            fw = (float) (10 * s.x) / 480;
        }
        float f = Math.min(fw, fh);
        logi( "displayButtonFontSize " + (double)f + ", " + (double)fw + ", " + (double)fh);
        return f;
    }

    public float tinyButtonFontSize()
    {
        Point s = displayWindowSize();

        float fh;
        float fw;
        if ( s.y > s.x) // portrait
        {
            fh = (float) (44 * s.y) / 1024;
            fw = (float) (44 * s.x) / 768;
        }
        else
        {
            fh = (float) (44 * s.y) / 768;
            fw = (float) (44 * s.x) / 1024;
        }
        float f = Math.min(fw, fh);
        if ( f < 22) f = 22;
        logi( "displayTinyButtonFontSize " + (double)f + ", " + (double)fw + ", " + (double)fh);
        return f;
    }

    public void setTypeface( View view, Typeface typeface) {
        if ( view != null) {
            if ( view instanceof Button) {
                logi( "Button " + ((Button) view).getText() + " = " + typeface.toString());
                ((Button) view).setTypeface(typeface);
            } else if ( view instanceof TextView) {
                logi( "TextView " + ((TextView) view).getText() + " = " + typeface.toString());
                ((TextView) view).setTypeface(typeface);
            }
        }
    }

    public void setFontSize( View view, float size) {
        if ( view != null) {
            if ( view instanceof Button) {
                logi("Button " + ((Button) view).getText() + " = " + size);
                ((Button) view).setTextSize( TypedValue.COMPLEX_UNIT_PX, size);
            } else if ( view instanceof TextView) {
                logi("TextView " + ((TextView) view).getText() + " = " + size);
                ((TextView) view).setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
            }
        }
    }

    @Nullable
    public <T extends View> T findViewById(@IdRes int id) {
        return mClient.uiUtilsActivity().findViewById(id);
    }

    public void setTypeface(@IdRes int id, Typeface typeface) {
        setTypeface( findViewById(id), typeface);
    }

    public void setFontSize( @IdRes int id, float size) {
        setFontSize( findViewById(id), size);
    }

    public void setTypefaces(ViewGroup parent, Typeface button, Typeface textview) {
        Class<Button> buttonClass = Button.class;

        logi("displaySetTypefaces");
        int count = parent.getChildCount();
        for ( int i = 0; i < count; i++) {
            View view = parent.getChildAt(i);
            if ( view instanceof Button) {
                setTypeface( view, button);
            } else if ( view instanceof TextView) {
                setTypeface( view, textview);
            } else if ( view instanceof ViewGroup) {
                setTypefaces( (ViewGroup) view, button, textview);
            }
        }
    }

    public void setFontSizes( ViewGroup parent, float button, float textview) {
        logi("displaySetFontSizes");
        int count = parent.getChildCount();
        for ( int i = 0; i < count; i++) {
            View view = parent.getChildAt(i);
            if ( view instanceof Button) {
                setFontSize( view, button);
            } else if ( view instanceof TextView) {
                setFontSize( view, textview);
            } else if ( view instanceof ViewGroup) {
                setFontSizes( (ViewGroup) view, button, textview);
            }
        }
    }

    public void setButtonClicks( ViewGroup parent, View.OnClickListener onClickListener) {
        logi("setButtonClicks");
        int count = parent.getChildCount();
        for ( int i = 0; i < count; i++) {
            View view = parent.getChildAt(i);
            if ( view instanceof ImageButton) {
                logi("ImageButton");
                view.setOnClickListener( onClickListener);
            } else if ( view instanceof Button) {
                logi("Button " + ((Button) view).getText());
                view.setOnClickListener( onClickListener);
            } else if ( view instanceof ViewGroup) {
                setButtonClicks( (ViewGroup) view, onClickListener);
            }
        }
    }

    public void setVisibility( @IdRes int id, int visibility) {
        View view = findViewById(id);
        if ( view != null) {
            view.setVisibility(visibility);
        }
    }

    public void setVisible( @IdRes int id, boolean show) {
        View view = findViewById(id);
        if ( view != null) {
            view.setVisibility( show ? View.VISIBLE : View.GONE);
        }
    }

    public void setEnabled( @IdRes int id, boolean enabled) {
        View view = findViewById(id);
        if ( view != null) {
            view.setEnabled( enabled);
        }
    }

    public void setBackground( @IdRes int id, Drawable drawable) {
        View view = findViewById(id);
        if ( view != null) {
            view.setBackground( drawable);
        }
    }

    public void gifAnimate( @IdRes int id) {
        View view = findViewById(id);
        if ( view != null && view instanceof GifImageView) {
            GifDrawable drawable = (GifDrawable) ((GifImageView)view).getDrawable();
            if ( drawable != null) {
                drawable.reset();
            }
            view.animate();
        }
    }

//    public static void safelyStartActivityPopup( Context context, String message, String title) {
//        PopUp.show( message, title,
//                R.drawable.error_face,
//                R.drawable.red_btn,
//                PopUp.GIFF_ANIMATION_ERROR,
//                PopUp.TYPE_ALERT,
//                null,
//                null);
//    }
//
//    public static void safelyStartActivityPopup( Context context, String title) {
//        safelyStartActivityPopup( context, context.getString(R.string.this_device_may_have_restrictions_in_place), title);
//    }
//
//    public static void safelyStartActivityPopupGeneric( Context context) {
//        safelyStartActivityPopup( context, context.getString(R.string.unable_to_start_activity));
//    }
//
//    public static void safelyStartActivityPopupOpenLink( Context context) {
//        safelyStartActivityPopup( context, context.getString(R.string.unable_to_open_link));
//    }

    public static void safelyStartActivityToast( Context context, String message, String title) {
        Toast.makeText( context, title + ".\n" + message, Toast.LENGTH_LONG).show();
    }

    public static void safelyStartActivityToast( Context context, String title) {
        safelyStartActivityToast( context, context.getString(R.string.this_device_may_have_restrictions_in_place), title);
    }

    public static void safelyStartActivityToastGeneric( Context context) {
        safelyStartActivityToast( context, context.getString(R.string.unable_to_start_activity));
    }

    public static void safelyStartActivityToastOpenLink( Context context) {
        safelyStartActivityToast( context, context.getString(R.string.unable_to_open_link));
    }

    // Wrap startActivity and startActivityForResult
    // Call resolveActivity and catch exception from startActivity
    // Return non-zero error on fail
    // When startActivityForResult fails, the caller likely
    // needs to add code similar to the cancel case in onActivityResult
    public static int safelyStartActivity( Context context, boolean report, Intent intent,
                                           boolean forResult, int requestCode, Bundle options) {
//        if ( report) {
//            safelyStartActivityToastGeneric( context);
//        }
//        return 4;

        int error = 0;
        ComponentName componentName = intent.resolveActivity( context.getPackageManager());
        if ( componentName == null) {
            Log.i(TAG,"startActivity - no component");
            error = 1;
        } else {
            try {
                if ( forResult) {
                    if ( !(context instanceof Activity)) {
                        error = 3;
                    } else {
                        ((Activity) context).startActivityForResult(intent, requestCode, options);
                    }
                } else {
                    context.startActivity(intent);
                }
            } catch (Exception e) {
                Log.i(TAG, "startActivity - exception");
                e.printStackTrace();
                error = 2;
            }
        }
        if ( report && error != 0) {
            safelyStartActivityToastGeneric( context);
        }
        return error;
    }

    public static int safelyStartActivity(Context context, boolean report, Intent intent, Bundle options) {
        return UIUtils.safelyStartActivity( context, report, intent, false, 0, options);
    }

    public static int safelyStartActivity(Context context, boolean report, Intent intent) {
        return UIUtils.safelyStartActivity( context, report, intent, null);
    }

    public static int safelyStartActivityForResult(Activity activity, boolean report, Intent intent, int requestCode, Bundle options) {
        return UIUtils.safelyStartActivity( activity, report, intent, true, requestCode, options);
    }

    public static int safelyStartActivityForResult(Activity activity, boolean report, Intent intent, int requestCode) {
        return UIUtils.safelyStartActivityForResult( activity, report, intent, requestCode, null);
    }

    public static int safelyStartActivityViewURI( Context context, boolean report, Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData( uri);
        int error = UIUtils.safelyStartActivity( context, false, intent);
        if ( report && error != 0) {
            safelyStartActivityToastOpenLink( context);
        }
        return error;
    }

    public static int safelyStartActivityViewURL( Context context, boolean report, String url) {
        return UIUtils.safelyStartActivityViewURI( context, report, Uri.parse( url));
    }
}
