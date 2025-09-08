package com.samsung.microbit.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.samsung.microbit.BuildConfig;
import com.samsung.microbit.MBApp;
import com.samsung.microbit.R;
import com.samsung.microbit.ui.UIUtils;
import com.samsung.microbit.utils.FileUtils;
import com.samsung.microbit.utils.ProjectsHelper;

import java.io.File;
import java.io.FileOutputStream;

import static android.content.ContentValues.TAG;

/**
 * Displays MakeCode
 */

public class MakeCodeWebView extends Activity implements View.OnClickListener {

    private WebView webView;
    public static String makecodeUrl = "https://makecode.microbit.org/?androidapp=" + BuildConfig.VERSION_CODE;
    public static MakeCodeWebView activityHandle = null;

    boolean projectDownload = false;

    private static final int REQUEST_CODE_SAVEDATA = 1;
    private static final int REQUEST_CODE_CHOOSE_FILE = 2;
    private static final int REQUEST_CODE_FLASH = 3;
    private byte[] dataToSave = null;
    private ValueCallback<Uri[]> onShowFileChooser_filePathCallback;

    private boolean mRelaunchOnFinishNavigation = false;
    private String  mRelaunchURL = makecodeUrl;


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

        // TODO: EdgeToEdge - Remove once activities handle insets.
        // Call before the DecorView is accessed in setContentView
        getTheme().applyStyle(R.style.OptOutEdgeToEdgeEnforcement, /* force */ false);

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
        WebView.setWebContentsDebuggingEnabled(false);

        webView.addJavascriptInterface(new JavaScriptInterface(this), "AndroidFunction");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading( WebView view, String url) {
                Log.v(TAG, "shouldOverrideUrlLoading (legacy) " + url);
                return overrideUri( Uri.parse( url));
            }
            public boolean shouldOverrideUrlLoading( WebView view, WebResourceRequest request ) {
                Log.v(TAG, "shouldOverrideUrlLoading " + request);
                return overrideUri( request.getUrl());
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
                Log.v(TAG, "onLoadResource(" + url + ");");
            }

            @Override
            public void onPageFinished (WebView view, String url) {
                super.onPageFinished(view, url);
                Log.v(TAG, "onPageFinished(" + url + ");");
                onPageFinishedJS(view, url);
                
                if ( url.startsWith( makecodeUrl)) {
                    mRelaunchURL = makecodeUrl;
                    String hashEditor = "#editor";
                    if ( url.contains( hashEditor)) {
                        if ( mRelaunchURL.endsWith( "#")) {
                            mRelaunchURL = mRelaunchURL.substring( 0, mRelaunchURL.length() - 1);
                        }
                        mRelaunchURL = mRelaunchURL + hashEditor;
                    }
                    Log.v(TAG, "Remember relaunch URL " + mRelaunchURL);
                }
                
                if ( mRelaunchOnFinishNavigation) {
                    mRelaunchOnFinishNavigation = false;
                    webView.loadUrl( mRelaunchURL);
                }
            }
       }); //setWebViewClient

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
                return showFileChooser( webView, filePathCallback, fileChooserParams);

            }
        }); //setWebChromeClient

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

//                        // Replace existing file rather than creating *-n.hex
//                        // Append n to file until it doesn't exist
//                        int i = 0;
//
//                        while (hexToWrite.exists()) {
//                            hexName = hexName.replaceAll("-?\\d*\\.","-" + i + ".");
//                            hexToWrite = getProjectFile( hexName);
//                            i++;
//                        }

                        if ( !FileUtils.writeBytesToFile( hexToWrite, decode)) {
                            ProjectsHelper.importToProjectsToast(
                                    ProjectsHelper.enumImportResult.WriteFailed, MakeCodeWebView.this);
                            return;
                        }

                        boolean download = projectDownload;
                        projectDownload = false;

                        if ( download) {
                            openProjectActivity( hexToWrite);
                        } else {
                            Toast.makeText( MakeCodeWebView.this,
                                    "Saved to My Programs page", Toast.LENGTH_LONG).show();
                        }
                    }
                } catch ( Exception e) {
                    e.printStackTrace();
                }
            }
        }); // setDownloadListener

        //Check parameters Before load
        Intent intent = getIntent();

        boolean importExtra = intent.getBooleanExtra("import", false);
        if ( importExtra) {
            importInitialise();
        } else {
            importHex = null;
            importName = null;
        }

        webView.loadUrl(makecodeUrl);
        MBApp.getAppState().eventPairMakeCodeBegin();
    } // onCreate


    private boolean showFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
        onShowFileChooser_filePathCallback = filePathCallback;
        try {
            Intent intent = fileChooserParams.createIntent();
            int error = UIUtils.safelyStartActivityForResult( this, true, intent, REQUEST_CODE_CHOOSE_FILE);
            if ( error != 0) {
                onShowFileChooser_filePathCallback.onReceiveValue( null);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;

    }


    private boolean overrideUri( final Uri uri) {
        String url = uri.toString().toLowerCase();
        Log.v(TAG, "overrideUri: " + url);
        if ( url.contains("https://microbit.org/code")) {
            MBApp.getAppState().eventPairMakeCodeEnd();
            finish();
            return true;
        }

        String host = uri.getHost();
        String path = uri.getPath();
        host = host == null ? "" : host.toLowerCase();
        path = path == null ? "" : path.toLowerCase();

        if ( host.equals("makecode.microbit.org")) {
            if ( url.startsWith( makecodeUrl))
                return false;
            else if ( path.startsWith( "/oauth/login"))
                return false;
            else if ( path.equals( "/") && uri.getQueryParameter("authcallback") != null)
                return false;
        }
        else if ( host.equals( "makecode.com")) {
            if ( path.startsWith("/oauth/callback"))
                return false;
            else if ( path.startsWith("/auth/callback"))
                return false;
        }
        else if ( host.equals( "login.live.com"))
            return false;
        else if ( host.equals( "login.microsoftonline.com"))
            return false;
        else if ( host.equals( "www.pxt.io"))
            return false;
        else if ( host.equals( "trg-microbit.userpxt.io"))
            return false;
        else if ( host.equals( "pxt.azureedge.net"))
            return false;
        else if ( host.equals( "accounts.google.com"))
            return false;
        else if ( host.equals( "clever.com"))
            return false;
        else if ( host.equals( "github.com")) {
            if ( path.startsWith( "/login/oauth/"))
                return false;
            if ( path.equals( "/login"))
                return false;
            if ( path.startsWith( "/sessions/"))
                return false;
            if ( path.equals( "/logout"))
                return false;
            // When signing out of GitHub, relaunch MakeCode,
            // otherwise it takes 2 or 3 "backs" to return to MakeCode
            if ( path.equals( "/")) {
                mRelaunchOnFinishNavigation = true;
                return false;
            }
        }

        openUri( uri);
        return true;
    }

    void openUri( Uri uri) {
        Log.v(TAG, "openUri: " + uri);
        UIUtils.safelyStartActivityViewURI( this, true, uri);
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if ( requestCode == REQUEST_CODE_FLASH) {
            if ( resultCode != RESULT_OK) {
                return;
            }
        } else if ( requestCode == REQUEST_CODE_SAVEDATA) {
            if ( resultCode != RESULT_OK) {
                dataToSave = null;
                return;
            }
            if ( dataToSave != null && dataToSave.length > 0) {
                Uri uri = data.getData();
                if ( !FileUtils.writeBytesToUri( uri, dataToSave, this)) {
                    Toast.makeText(this, "Could not save file", Toast.LENGTH_LONG).show();
                }
            }
            dataToSave = null;
        } else if (requestCode == REQUEST_CODE_CHOOSE_FILE) {
            if ( resultCode != RESULT_OK) {
                onShowFileChooser_filePathCallback.onReceiveValue( null);
                return;
            }
            Uri[] uris = WebChromeClient.FileChooserParams.parseResult ( resultCode, data);
            onShowFileChooser_filePathCallback.onReceiveValue( uris);
        }
    }

    public File getProjectFile( String hexName)
    {
        return ProjectsHelper.projectFile( this, hexName);
    }

    @Override
    public void onBackPressed() {
        String url = webView.getUrl();
        if ( url != null && !url.startsWith( makecodeUrl) && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    public void onClick(final View v) {
        if(v.getId() == R.id.backBtn) {
            MBApp.getAppState().eventPairMakeCodeEnd();
            finish();
        }
    }

    public final static String ACTION_FLASH = "com.samsung.microbit.ACTION_FLASH";

    void openProjectActivity( File hexToWrite) {
        Intent i = new Intent(this, ProjectActivity.class);
        i.setAction( ACTION_FLASH);
        i.putExtra("path", hexToWrite.getAbsolutePath());
        startActivityForResult( i, REQUEST_CODE_FLASH);
    }

    public static String importHex  = null;
    public static String importName = null;
    private boolean importPosting = false;
    Handler importHandler = null;

    private void importInitialise() {
        if ( importHex != null) {
            importHex = importHex.replaceAll("\r\n", "\\\\n");
            importHex = importHex.replaceAll("\r", "\\\\n");
            importHex = importHex.replaceAll("\n", "\\\\n");

            //TODO - does MakeCode signal when ready?
            Looper looper = Looper.getMainLooper();
            importHandler = new Handler(looper);
            importHandler.postDelayed(importCallback, 2000);
        }
    }
    private final Runnable importCallback = new Runnable() {
        @Override
        public void run() {
            if ( importHex != null) {
                importPostMessage();
                importHandler.postDelayed( importCallback, 1000);
            } else {
                importHandler.removeCallbacks( importCallback);
                importHandler = null;
            }
        }
    };

    public void importPostMessage() {
        Log.d(TAG, "importPostMessage");

        if ( importHex == null) {
            return;
        }
        if ( importPosting) {
            return;
        }
        if ( webView == null) {
            return;
        }
        if ( importName == null || importName.isEmpty()) {
            importName = "import.hex";
        }

        importPosting = true;

        StringBuilder sb = new StringBuilder();
        String nl = "\n";
        sb.append( "javascript:(");
        sb.append(nl).append("function f() {");
        sb.append(nl).append(   "var ret = 'OK'");
        sb.append(nl).append(   "try {");
        sb.append(nl).append(       "var loading = document.getElementById('loading')");
        sb.append(nl).append(       "if ( loading && loading.parentElement) {");
        sb.append(nl).append(           "ret = 'loading'");
        sb.append(nl).append(       "} else {");
        sb.append(nl).append(           "var name = '").append(importName).append("'");
        sb.append(nl).append(           "var hex = '").append(importHex).append("'");
        sb.append(nl).append(           "var msg = {");
        sb.append(nl).append(               "type: 'importfile',");
        sb.append(nl).append(               "filename: name,");
        sb.append(nl).append(               "parts: [ hex ]");
        sb.append(nl).append(           "}");
        sb.append(nl).append(           "window.postMessage( msg, '*')");
        sb.append(nl).append(       "}");
        sb.append(nl).append(   "} catch( err) {");
        sb.append(nl).append(       "ret = err.message");
        sb.append(nl).append(   "}");
        sb.append(nl).append(   "return ret");
        sb.append(nl).append("}");
        sb.append(nl).append(")()");

        webView.evaluateJavascript( sb.toString(), new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String s) {
                Log.i(TAG, "importPostMessage: " + s);
                String loading = "\"loading\"";
                String ok = "\"OK\"";
                if ( s.equals( ok)) {
                    importHex = null;
                } else if ( !s.equals( loading)) {
                }
            }
        });
        importPosting = false;
    }

    public void onPageFinishedJS( WebView view, String url) {
        Log.v(TAG, "addListeners(" + url + ");");

        StringBuilder sb = new StringBuilder();
        String nl = "\n";
        sb.append( "javascript:(");
        sb.append(nl).append("function f() {");
        sb.append(nl).append(   "var ret = 'OK'");
        sb.append(nl).append(   "try {");
        sb.append(nl).append(       "var brands = document.getElementsByClassName(\"brand\")");
        sb.append(nl).append(       "for (let i = 0; brands != null && i < brands.length; i++) {");
        sb.append(nl).append(           "brands[i].addEventListener(\"click\",");
        sb.append(nl).append(               "function(e) {");
        sb.append(nl).append(                   "AndroidFunction.clickBrand();");
        sb.append(nl).append(                   "e.preventDefault();");
        sb.append(nl).append(                   "return false;");
        sb.append(nl).append(               "})");
        sb.append(nl).append(       "}");
        sb.append(nl).append(       "var downs = document.getElementsByClassName(\"download-button\")");
        sb.append(nl).append(       "for (let i = 0; downs != null && i < downs.length; i++) {");
        sb.append(nl).append(           "downs[i].addEventListener(\"click\",");
        sb.append(nl).append(               "function(e) {");
        sb.append(nl).append(                   "AndroidFunction.clickDownload();");
        sb.append(nl).append(                   "e.preventDefault();");
        sb.append(nl).append(                   "return false;");
        sb.append(nl).append(               "})");
        sb.append(nl).append(       "}");
        sb.append(nl).append(   "} catch( err) {");
        sb.append(nl).append(       "ret = err.message");
        sb.append(nl).append(   "}");
        sb.append(nl).append(   "return ret");
        sb.append(nl).append("}");
        sb.append(nl).append(")()");

        webView.evaluateJavascript( sb.toString(), new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String s) {
                Log.d(TAG, s);
            }
        });
    }}

/* Javascript Interface */
class JavaScriptInterface {
    MakeCodeWebView mContext;

    JavaScriptInterface( MakeCodeWebView c) {
        mContext = c;
    }

    @JavascriptInterface
    public void clickBrand() {
        try {
            MBApp.getAppState().eventPairMakeCodeEnd();
            MakeCodeWebView.activityHandle.finish();
        } catch(Exception e) {
            Log.v(TAG, e.toString());
        }
    }

    @JavascriptInterface
    public void clickDownload() {
        try {
            mContext.projectDownload = true;
        } catch(Exception e) {
            Log.v(TAG, e.toString());
        }
    }
}
