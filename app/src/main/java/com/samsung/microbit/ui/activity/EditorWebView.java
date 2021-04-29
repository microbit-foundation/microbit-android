package com.samsung.microbit.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.cardview.widget.CardView;

import com.samsung.microbit.BuildConfig;
import com.samsung.microbit.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLDecoder;

import static android.content.ContentValues.TAG;

/**
 * Displays Editor
 */
public class EditorWebView extends Activity implements View.OnClickListener {

    private WebView webView;
    public static String makecodeUrl = "https://makecode.microbit.org/?androidapp=" + BuildConfig.VERSION_CODE;
    public static String pythonUrl = "https://python-editor-2-1-1.microbit.org/?androidapp=" + BuildConfig.VERSION_CODE;
    public static String url = makecodeUrl;
    public static Activity activityHandle = null;

    Uri hexToFlash;

    CardView makecodeCard;
    CardView pythonCard;
    ImageButton makecodeImageButton;
    ImageButton pythonImageButton;
    Button makecodeButton;
    Button pythonButton;

    public static void setMakecodeUrl(String url) {
        makecodeUrl = url;
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

        activityHandle = this;

        setContentView(R.layout.activity_editors_web_view);

        webView = (WebView) findViewById(R.id.generalView);

        makecodeCard = (CardView)findViewById(R.id.makecodeCard);
        pythonCard = (CardView)findViewById(R.id.pythonCard);

        makecodeButton = (Button) findViewById(R.id.makecodeButton);
        makecodeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openWebView(makecodeUrl);
            }
        });

        makecodeImageButton = (ImageButton)findViewById(R.id.makecodeImageButton);
        makecodeImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openWebView(makecodeUrl);
            }
        });

        pythonButton = (Button)findViewById(R.id.pythonButton);
        pythonButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openWebView(pythonUrl);
            }
        });

        pythonImageButton = (ImageButton)findViewById(R.id.pythonImageButton);
        pythonImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openWebView(pythonUrl);
            }
        });

        // Hide Python
        openWebView(makecodeUrl);

        /*
        makecodeCard.setVisibility(View.VISIBLE);
        pythonCard.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
         */

    }

    public void openWebView(String url) {

        makecodeCard.setVisibility(View.GONE);
        pythonCard.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings webSettings = webView.getSettings();

        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setDomStorageEnabled(true);
        webView.setWebContentsDebuggingEnabled(true);

        webView.addJavascriptInterface(new JavaScriptInterface(this), "AndroidFunction");
        webView.evaluateJavascript("javascript:(function f() { document.getElementsByClassName(\"mb_logo\")[0].addEventListener(\"click\", function(e) { AndroidFunction.returnToHome(); e.preventDefault(); return false; }) })()", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String s) {
                Log.d(TAG, s);
            }
        });
        webView.evaluateJavascript("javascript:(function f() { document.getElementsByClassName(\"brand\")[0].addEventListener(\"click\", function(e) { AndroidFunction.returnToHome(); e.preventDefault(); return false; }) })()", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String s) {
                Log.d(TAG, s);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Log.v(TAG, "url: " + url);
                if(url.contains("https://microbit.org/")) activityHandle.finish();
                if(url.contains("http://microbit.org/")) activityHandle.finish();
                if(url.contains("blob://")) Log.v(TAG, "blob uri");
                return false;
            }
            @Override
            public void onLoadResource (WebView view, String url) {
                super.onLoadResource(view, url);
                Log.v(TAG, "onLoadResource("+url+");");
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimetype,
                                        long contentLength) {

                webView.evaluateJavascript("javascript:(function f() { document.getElementsByClassName(\"close\")[0].click(); } )()", new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String s) {
                        Log.d(TAG, s);
                    }
                });

                File hexToWrite;
                FileOutputStream outputStream;
                String hexName = "init";
                byte[] decode = {};

                if(url.contains("data:")) {
                    if(url.contains("base64")) {
                        String[] data = url.split(",");
                        hexName = data[0].replace("data:", "").replace(";base64", "");
                        decode = Base64.decode(data[1], Base64.DEFAULT);
                    } else if(url.contains("octet")) {
                        String[] data = url.split(",");
                        hexName = "micropython_program.hex";
                        decode = URLDecoder.decode(data[1]).getBytes();
                    }
                } else if(url.contains("blob:")) {
                    hexName = "blob";
                    decode = new byte[]{0, 0, 0};
                }

                try {
                    hexToWrite = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + hexName);

                    /*
                    // Append n to file until it doesn't exist
                    int i = 0;

                    while (hexToWrite.exists()) {
                        hexName = hexName.replaceAll("-?\\d*\\.","-" + i + ".");
                        hexToWrite = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + hexName);
                        i++;
                    }
                     */
                    // Replace existing file rather than creating *-n.hex
                    if(hexToWrite.exists()) {
                        hexToWrite.delete();
                    }

                    // Create file
                    hexToWrite.createNewFile();
                    outputStream = new FileOutputStream(hexToWrite);
                    outputStream.write(decode);
                    outputStream.flush();

                    // Get file path
                    hexToFlash = Uri.fromFile(hexToWrite);

                    openProjectActivity();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        //Check parameters Before load
        Intent intent = getIntent();
        webView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if(webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    public void onClick(final View v) {
        if(v.getId() == R.id.backBtn) {
            finish();
        }
    }

    void openProjectActivity() {
        Intent i = new Intent(this, ProjectActivity.class);
        i.setData(hexToFlash);
        startActivity(i);
    }
}

/* Javascript Interface */
class JavaScriptInterface {
    Context mContext;

    JavaScriptInterface(Context c) {
        mContext = c;
    }

    @JavascriptInterface
    public void returnToHome() {
        try {
            EditorWebView.activityHandle.finish();
        } catch(Exception e) {
            Log.v(TAG, e.toString());
        }
    }
}
