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

import com.samsung.microbit.BuildConfig;
import com.samsung.microbit.R;
import com.samsung.microbit.utils.ProjectsHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static android.content.ContentValues.TAG;

/**
 * Displays MakeCode
 */
public class MakeCodeWebView extends Activity implements View.OnClickListener {

    private WebView webView;
    public static String makecodeUrl = "https://makecode.microbit.org/?androidapp=" + BuildConfig.VERSION_CODE;
    public static Activity activityHandle = null;

    Uri hexToFlash;

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

        setContentView(R.layout.activity_help_web_view);
        webView = (WebView) findViewById(R.id.generalView);

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

                try {
                    File hexToWrite;
                    FileOutputStream outputStream;
                    String hexName = "";
                    String fileName = "";
                    byte[] decode = {};

                    int colon = url.indexOf(':');
                    int semi  = url.indexOf(';');
                    int comma = url.indexOf(',');

                    String scheme = "";
                    String type   = "";
                    String format = "";

                    if ( colon > 0 && semi > colon && comma > semi)
                    {
                        scheme = url.substring( 0,colon);
                        type   = url.substring( colon + 1, semi);
                        format = url.substring( semi + 1, comma);
                    }

                    if ( scheme.equalsIgnoreCase("data")) {
                        if ( format.equalsIgnoreCase("base64")) {
                            decode = Base64.decode(url.substring(comma + 1), Base64.DEFAULT);
                            if ( decode != null && decode.length > 0) {
                                String typeLC = type.toLowerCase();
                                if ( typeLC.endsWith(".hex")) {
                                    hexName = type;
                                    type = "application/x-microbit-hex";
                                } else if ( typeLC.endsWith(".csv")) {
                                    fileName = type;
                                    type = "text/csv";
                                } else if ( typeLC.endsWith(".txt")) {
                                    fileName = type;
                                    type = "text/csv";
                                } else if ( typeLC.equalsIgnoreCase("text/plain")) {
                                    fileName = "makecode-text";
                                } else if ( typeLC.equalsIgnoreCase("image/png")) {
                                    fileName = "makecode-snapshot";
                                } else if ( typeLC.equalsIgnoreCase("application/zip")) {
                                    fileName = "makecode-projects";
                                } else {
                                    fileName = "makecode-data";
                                    type = mimetype;
                                }
                            }
                        }
                    }
//                    else if ( scheme.equalsIgnoreCase("blob")) {
//                        hexName = "blob";
//                        decode = new byte[]{0, 0, 0};
//                    }

                    if ( !fileName.isEmpty()) {
                        saveData( fileName, type, decode);
                    }
                    else if ( !hexName.isEmpty()) {
                        hexToWrite = getProjectFile(hexName);

                        /*
                        // Append n to file until it doesn't exist
                        int i = 0;

                        while (hexToWrite.exists()) {
                            hexName = hexName.replaceAll("-?\\d*\\.","-" + i + ".");
                            hexToWrite = getProjectFile( hexName);
                            i++;
                        }
                         */
                        // Replace existing file rather than creating *-n.hex
                        if (hexToWrite.exists()) {
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
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        //Check parameters Before load
        Intent intent = getIntent();
        webView.loadUrl(makecodeUrl);
    }

    private static final String EXTRA_BYTEARRAY = "com.samsung.microbit.BYTEARRAY";
    private static final int REQUEST_CODE_SAVEDATA = 1;

    private byte[] dataToSave = null;

    private void saveData( String name, String mimetype, byte[] data) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType( mimetype);
        intent.putExtra(Intent.EXTRA_TITLE, name);
        //intent.putExtra( EXTRA_BYTEARRAY, data);
        dataToSave = data;
        startActivityForResult( intent, REQUEST_CODE_SAVEDATA);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if ( requestCode == REQUEST_CODE_SAVEDATA) {
            if ( resultCode != RESULT_OK) {
                dataToSave = null;
                return;
            }
            OutputStream os = null;
            try {
                //byte[] dataToSave = data.getByteArrayExtra(EXTRA_BYTEARRAY);
                os = getContentResolver().openOutputStream( data.getData());
                if ( dataToSave != null && dataToSave.length > 0) {
                    os.write(dataToSave, 0, dataToSave.length);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    dataToSave = null;
                    if ( os != null) {
                        os.close();
                    }
                } catch (IOException e) { }
            }
        }
    }

    public File getProjectFile( String hexName)
    {
        return ProjectsHelper.projectFile( this, hexName);
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
            MakeCodeWebView.activityHandle.finish();
        } catch(Exception e) {
            Log.v(TAG, e.toString());
        }
    }
}
