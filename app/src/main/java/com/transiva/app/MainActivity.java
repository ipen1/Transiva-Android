package com.transiva.app;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.*;
import android.provider.MediaStore;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.graphics.Color;
import androidx.core.app.*;
import androidx.core.content.FileProvider;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraImageUri;
    private static final int REQ_PERM = 10;
    private static final int REQ_FILE = 11;
    private static final String URL = "https://transiva.my.id/?app=1";
    private static final String CHANNEL_ID = "transiva_channel";
    private long lastBack = 0;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#06142E"));
        getWindow().setNavigationBarColor(Color.parseColor("#06142E"));
        createNotificationChannel();
        requestAppPermissions();
        buildLayout();
        configureWebView();
        webView.loadUrl(URL);
    }

    private void buildLayout() {
        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(-1, 8, Gravity.TOP);
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(progressBar, barParams);
        setContentView(root);
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.setDownloadListener((url, ua, cd, mime, len) -> downloadFile(url, ua, cd, mime));

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String host = u.getHost() == null ? "" : u.getHost();
                if (host.contains("transiva.my.id")) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, u));
                return true;
            }
            @Override public void onPageFinished(WebView v, String url) {
                progressBar.setVisibility(View.GONE);
                injectNotificationBridge();
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) showOfflinePage();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                progressBar.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
                progressBar.setProgress(p);
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                cb.invoke(origin, true, false);
            }
            @Override public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                try {
                    File photo = createImageFile();
                    cameraImageUri = FileProvider.getUriForFile(MainActivity.this, getPackageName()+".fileprovider", photo);
                    camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
                    camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception ex) { camera = null; }
                Intent content = new Intent(Intent.ACTION_GET_CONTENT);
                content.addCategory(Intent.CATEGORY_OPENABLE);
                content.setType("*/*");
                content.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*","video/*","application/pdf"});
                Intent chooser = Intent.createChooser(content, "Pilih file");
                if (camera != null) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
                startActivityForResult(chooser, REQ_FILE);
                return true;
            }
        });
    }

    private File createImageFile() throws IOException {
        String time = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return File.createTempFile("TRANSIVA_" + time + "_", ".jpg", getExternalCacheDir());
    }

    private void requestAppPermissions() {
        ArrayList<String> p = new ArrayList<>();
        p.add(Manifest.permission.CAMERA);
        p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        p.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33) {
            p.add(Manifest.permission.POST_NOTIFICATIONS);
            p.add(Manifest.permission.READ_MEDIA_IMAGES);
            p.add(Manifest.permission.READ_MEDIA_VIDEO);
        } else p.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        requestPermissions(p.toArray(new String[0]), REQ_PERM);
    }

    private void downloadFile(String url, String ua, String cd, String mime) {
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
        req.addRequestHeader("User-Agent", ua);
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, cd, mime));
        ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
        Toast.makeText(this, "Download dimulai", Toast.LENGTH_SHORT).show();
    }

    private void showOfflinePage() {
        String html = "<html><body style='background:#06142E;color:white;font-family:sans-serif;text-align:center;padding:35px'>"+
                "<img src='file:///android_res/drawable/splash_screen.png' style='width:80%;max-width:360px'><h2>Koneksi internet terputus</h2>"+
                "<p>Periksa koneksi Anda lalu coba lagi.</p><button style='padding:14px 22px;border-radius:14px;border:0;background:#ff8a00;color:white;font-weight:bold' onclick='location.href=\""+URL+"\"'>Coba Lagi</button></body></html>";
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Transiva", NotificationManager.IMPORTANCE_HIGH);
            ch.enableVibration(true);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private void injectNotificationBridge() {
        String js = "(function(){if(window.TransivaNotifReady)return;window.TransivaNotifReady=true;"+
                "window.transivaNotify=function(t,m){if(window.Android){Android.notify(String(t||'Transiva'),String(m||''));}};"+
                "window.transivaVibrate=function(ms){if(window.Android){Android.vibrate(parseInt(ms||300));}};})();";
        webView.evaluateJavascript(js, null);
    }

    public class AndroidBridge {
        @JavascriptInterface public void vibrate(int ms) {
            Vibrator v = (Vibrator)getSystemService(VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(ms);
        }
        @JavascriptInterface public void notify(String title, String message) {
            NotificationCompat.Builder b = new NotificationCompat.Builder(MainActivity.this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setVibrate(new long[]{0,300,150,300});
            NotificationManagerCompat.from(MainActivity.this).notify((int)System.currentTimeMillis(), b.build());
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQ_FILE) {
            Uri[] results = null;
            if (result == RESULT_OK) {
                if (data == null || data.getData() == null) {
                    if (cameraImageUri != null) results = new Uri[]{cameraImageUri};
                } else results = new Uri[]{data.getData()};
            }
            if (filePathCallback != null) filePathCallback.onReceiveValue(results);
            filePathCallback = null;
            cameraImageUri = null;
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) { webView.goBack(); return; }
        long now = System.currentTimeMillis();
        if (now - lastBack < 1600) { super.onBackPressed(); return; }
        lastBack = now;
        Toast.makeText(this, "Tekan sekali lagi untuk keluar", Toast.LENGTH_SHORT).show();
    }
}
