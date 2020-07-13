package com.samsung.microbit.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.view.View;
import android.webkit.DownloadListener;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.samsung.microbit.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Displays MakeCode
 */
public class MakeCodeWebView extends Activity implements View.OnClickListener {

    private WebView webView;
    public static String makecodeUrl = "https://makecode.microbit.org/v2?androidapp=19";

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
        webView.setWebContentsDebuggingEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimetype,
                                        long contentLength) {

                File hexToWrite;
                FileOutputStream outputStream;
                String[] data = url.split(",");
                String hexName = data[0].replace("data:","").replace(";base64", "");
                byte[] decode = Base64.decode(data[1], Base64.DEFAULT);

                try {
                    hexToWrite = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + hexName);

                    // Append n to file until it doesn't exist
                    int i = 0;

                    while (hexToWrite.exists()) {
                        hexName = hexName.replaceAll("-?\\d*\\.","-" + i + ".");
                        hexToWrite = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + hexName);
                        i++;
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
        webView.loadUrl(makecodeUrl);
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
