package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;

import com.google.firebase.messaging.FirebaseMessaging;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraImageUri;

    private static final int REQ_PERM = 10;
    private static final int REQ_FILE = 11;

    private static final String URL =
            "https://transiva.my.id/?app=1";

    private static final String CHANNEL_ID =
            "transiva_channel";

    private long lastBack = 0;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        getWindow().setStatusBarColor(
                Color.parseColor("#06142E")
        );

        getWindow().setNavigationBarColor(
                Color.parseColor("#06142E")
        );

        createNotificationChannel();
        requestAppPermissions();

        buildLayout();
        configureWebView();

        webView.loadUrl(URL);

        initFirebase();
    }

    private void buildLayout() {

        FrameLayout root =
                new FrameLayout(this);

        webView =
                new WebView(this);

        progressBar =
                new ProgressBar(
                        this,
                        null,
                        android.R.attr.progressBarStyleHorizontal
                );

        progressBar.setMax(100);

        FrameLayout.LayoutParams barParams =
                new FrameLayout.LayoutParams(
                        -1,
                        8,
                        Gravity.TOP
                );

        root.addView(
                webView,
                new FrameLayout.LayoutParams(-1, -1)
        );

        root.addView(
                progressBar,
                barParams
        );

        setContentView(root);
    }

    private void configureWebView() {

        WebSettings s =
                webView.getSettings();

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

        if (Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(
                    WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            );
        }

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "Android"
        );

        webView.setDownloadListener(
                new DownloadListener() {
                    @Override
                    public void onDownloadStart(
                            String url,
                            String userAgent,
                            String contentDisposition,
                            String mimeType,
                            long contentLength
                    ) {
                        downloadFile(
                                url,
                                userAgent,
                                contentDisposition,
                                mimeType
                        );
                    }
                }
        );

        webView.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView v,
                            WebResourceRequest r
                    ) {

                        Uri u =
                                r.getUrl();

                        String host =
                                u.getHost() == null
                                        ? ""
                                        : u.getHost();

                        if (host.contains("transiva.my.id")) {
                            return false;
                        }

                        startActivity(
                                new Intent(
                                        Intent.ACTION_VIEW,
                                        u
                                )
                        );

                        return true;
                    }

                    @Override
                    public void onPageFinished(
                            WebView v,
                            String url
                    ) {

                        progressBar.setVisibility(
                                View.GONE
                        );

                        injectNotificationBridge();
                        sendSavedFcmTokenToWeb();
                    }

                    @Override
                    public void onReceivedError(
                            WebView v,
                            WebResourceRequest r,
                            WebResourceError e
                    ) {

                        if (r.isForMainFrame()) {
                            showOfflinePage();
                        }

                    }

                }
        );

        webView.setWebChromeClient(
                new WebChromeClient() {

                    @Override
                    public void onProgressChanged(
                            WebView v,
                            int p
                    ) {

                        progressBar.setVisibility(
                                p >= 100
                                        ? View.GONE
                                        : View.VISIBLE
                        );

                        progressBar.setProgress(p);
                    }

                    @Override
                    public void onGeolocationPermissionsShowPrompt(
                            String origin,
                            GeolocationPermissions.Callback cb
                    ) {

                        cb.invoke(
                                origin,
                                true,
                                false
                        );

                    }

                    @Override
                    public void onPermissionRequest(
                            final PermissionRequest request
                    ) {

                        runOnUiThread(
                                () -> request.grant(
                                        request.getResources()
                                )
                        );

                    }

                    @Override
                    public boolean onShowFileChooser(
                            WebView w,
                            ValueCallback<Uri[]> cb,
                            FileChooserParams params
                    ) {

                        if (filePathCallback != null) {
                            filePathCallback.onReceiveValue(null);
                        }

                        filePathCallback = cb;

                        Intent camera =
                                new Intent(
                                        MediaStore.ACTION_IMAGE_CAPTURE
                                );

                        try {

                            File photo =
                                    createImageFile();

                            cameraImageUri =
                                    FileProvider.getUriForFile(
                                            MainActivity.this,
                                            getPackageName()
                                                    + ".fileprovider",
                                            photo
                                    );

                            camera.putExtra(
                                    MediaStore.EXTRA_OUTPUT,
                                    cameraImageUri
                            );

                            camera.addFlags(
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            );

                        } catch (Exception ex) {

                            camera = null;

                        }

                        Intent content =
                                new Intent(
                                        Intent.ACTION_GET_CONTENT
                                );

                        content.addCategory(
                                Intent.CATEGORY_OPENABLE
                        );

                        content.setType("*/*");

                        content.putExtra(
                                Intent.EXTRA_MIME_TYPES,
                                new String[]{
                                        "image/*",
                                        "video/*",
                                        "application/pdf"
                                }
                        );

                        Intent chooser =
                                Intent.createChooser(
                                        content,
                                        "Pilih file"
                                );

                        if (camera != null) {
                            chooser.putExtra(
                                    Intent.EXTRA_INITIAL_INTENTS,
                                    new Intent[]{camera}
                            );
                        }

                        startActivityForResult(
                                chooser,
                                REQ_FILE
                        );

                        return true;
                    }

                }
        );
    }

    private void initFirebase() {

        FirebaseMessaging
                .getInstance()
                .getToken()
                .addOnCompleteListener(task -> {

                    if (!task.isSuccessful()) {

                        Log.e(
                                "FCM",
                                "Gagal mengambil token",
                                task.getException()
                        );

                        return;
                    }

                    String token =
                            task.getResult();

                    if (token == null || token.trim().isEmpty()) {
                        return;
                    }

                    Log.d(
                            "FCM_TOKEN",
                            token
                    );

                    getSharedPreferences(
                            "transiva",
                            MODE_PRIVATE
                    )
                            .edit()
                            .putString(
                                    "fcm_token",
                                    token
                            )
                            .apply();

                    sendFcmTokenToWeb(token);

                });

    }

    private void sendSavedFcmTokenToWeb() {

        String token =
                getSharedPreferences(
                        "transiva",
                        MODE_PRIVATE
                )
                        .getString(
                                "fcm_token",
                                ""
                        );

        if (token != null && !token.isEmpty()) {
            sendFcmTokenToWeb(token);
        }

    }

    private void sendFcmTokenToWeb(String token) {

        if (webView == null || token == null) {
            return;
        }

        String safeToken =
                token
                        .replace("\\", "\\\\")
                        .replace("'", "\\'");

        String js =
                "(function(){" +
                        "window.TRANSIVA_FCM_TOKEN='" + safeToken + "';" +
                        "if(typeof window.receiveFcmToken==='function'){" +
                        "window.receiveFcmToken('" + safeToken + "');" +
                        "}" +
                        "})();";

        webView.post(
                () -> webView.evaluateJavascript(
                        js,
                        null
                )
        );

    }

    private File createImageFile()
            throws IOException {

        String time =
                new SimpleDateFormat(
                        "yyyyMMdd_HHmmss",
                        Locale.US
                ).format(new Date());

        return File.createTempFile(
                "TRANSIVA_" + time + "_",
                ".jpg",
                getExternalCacheDir()
        );
    }

    private void requestAppPermissions() {

        ArrayList<String> p =
                new ArrayList<>();

        p.add(Manifest.permission.CAMERA);
        p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        p.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        if (Build.VERSION.SDK_INT >= 33) {

            p.add(Manifest.permission.POST_NOTIFICATIONS);
            p.add(Manifest.permission.READ_MEDIA_IMAGES);
            p.add(Manifest.permission.READ_MEDIA_VIDEO);

        } else {

            p.add(Manifest.permission.READ_EXTERNAL_STORAGE);

        }

        requestPermissions(
                p.toArray(new String[0]),
                REQ_PERM
        );

    }

    private void downloadFile(
            String url,
            String ua,
            String cd,
            String mime
    ) {

        DownloadManager.Request req =
                new DownloadManager.Request(
                        Uri.parse(url)
                );

        req.addRequestHeader(
                "User-Agent",
                ua
        );

        req.setNotificationVisibility(
                DownloadManager.Request
                        .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        );

        req.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                URLUtil.guessFileName(
                        url,
                        cd,
                        mime
                )
        );

        ((DownloadManager) getSystemService(DOWNLOAD_SERVICE))
                .enqueue(req);

        Toast.makeText(
                this,
                "Download dimulai",
                Toast.LENGTH_SHORT
        ).show();

    }

    private void showOfflinePage() {

        String html =
                "<html>" +
                        "<body style='background:#06142E;color:white;font-family:sans-serif;text-align:center;padding:35px'>" +
                        "<img src='file:///android_res/drawable/splash_screen.png' style='width:80%;max-width:360px'>" +
                        "<h2>Koneksi internet terputus</h2>" +
                        "<p>Periksa koneksi Anda lalu coba lagi.</p>" +
                        "<button style='padding:14px 22px;border-radius:14px;border:0;background:#ff8a00;color:white;font-weight:bold' onclick='location.href=\"" + URL + "\"'>" +
                        "Coba Lagi" +
                        "</button>" +
                        "</body>" +
                        "</html>";

        webView.loadDataWithBaseURL(
                null,
                html,
                "text/html",
                "UTF-8",
                null
        );

    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            NotificationChannel ch =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Transiva",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            ch.enableVibration(true);
            ch.setDescription(
                    "Notifikasi Transiva"
            );

            NotificationManager manager =
                    (NotificationManager) getSystemService(
                            NOTIFICATION_SERVICE
                    );

            if (manager != null) {
                manager.createNotificationChannel(ch);
            }

        }

    }

    private void injectNotificationBridge() {

        String js =
                "(function(){" +
                        "if(window.TransivaNotifReady)return;" +
                        "window.TransivaNotifReady=true;" +

                        "window.transivaNotify=function(t,m){" +
                        "if(window.Android){" +
                        "Android.notify(String(t||'Transiva'),String(m||''));" +
                        "}" +
                        "};" +

                        "window.transivaVibrate=function(ms){" +
                        "if(window.Android){" +
                        "Android.vibrate(parseInt(ms||300));" +
                        "}" +
                        "};" +

                        "window.getTransivaFcmToken=function(){" +
                        "return window.TRANSIVA_FCM_TOKEN||'';" +
                        "};" +
                        "})();";

        webView.evaluateJavascript(
                js,
                null
        );

    }

    public class AndroidBridge {

        @JavascriptInterface
        public void vibrate(int ms) {

            Vibrator v =
                    (Vibrator) getSystemService(
                            VIBRATOR_SERVICE
                    );

            if (v == null) {
                return;
            }

            if (Build.VERSION.SDK_INT >= 26) {

                v.vibrate(
                        VibrationEffect.createOneShot(
                                ms,
                                VibrationEffect.DEFAULT_AMPLITUDE
                        )
                );

            } else {

                v.vibrate(ms);

            }

        }

        @JavascriptInterface
        public void notify(
                String title,
                String message
        ) {

            showLocalNotification(
                    title,
                    message
            );

        }

        @JavascriptInterface
        public String getFcmToken() {

            return getSharedPreferences(
                    "transiva",
                    MODE_PRIVATE
            )
                    .getString(
                            "fcm_token",
                            ""
                    );

        }

    }

    private void showLocalNotification(
            String title,
            String message
    ) {

        if (title == null || title.trim().isEmpty()) {
            title = "Transiva";
        }

        if (message == null) {
            message = "";
        }

        Intent intent =
                new Intent(
                        MainActivity.this,
                        MainActivity.class
                );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        MainActivity.this,
                        1001,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE
                                | PendingIntent.FLAG_UPDATE_CURRENT
                );

        NotificationCompat.Builder b =
                new NotificationCompat.Builder(
                        MainActivity.this,
                        CHANNEL_ID
                )
                        .setSmallIcon(
                                R.mipmap.ic_launcher
                        )
                        .setContentTitle(title)
                        .setContentText(message)
                        .setStyle(
                                new NotificationCompat.BigTextStyle()
                                        .bigText(message)
                        )
                        .setPriority(
                                NotificationCompat.PRIORITY_MAX
                        )
                        .setDefaults(
                                Notification.DEFAULT_ALL
                        )
                        .setAutoCancel(true)
                        .setContentIntent(
                                pendingIntent
                        );

        NotificationManagerCompat
                .from(MainActivity.this)
                .notify(
                        (int) System.currentTimeMillis(),
                        b.build()
                );

    }

    @Override
    protected void onActivityResult(
            int request,
            int result,
            Intent data
    ) {

        super.onActivityResult(
                request,
                result,
                data
        );

        if (request == REQ_FILE) {

            Uri[] results =
                    null;

            if (result == RESULT_OK) {

                if (data == null || data.getData() == null) {

                    if (cameraImageUri != null) {
                        results =
                                new Uri[]{cameraImageUri};
                    }

                } else {

                    results =
                            new Uri[]{data.getData()};

                }

            }

            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(results);
            }

            filePathCallback = null;
            cameraImageUri = null;

        }

    }

    @Override
    public void onBackPressed() {

        if (webView != null && webView.canGoBack()) {

            webView.goBack();
            return;

        }

        long now =
                System.currentTimeMillis();

        if (now - lastBack < 1600) {

            super.onBackPressed();
            return;

        }

        lastBack = now;

        Toast.makeText(
                this,
                "Tekan sekali lagi untuk keluar",
                Toast.LENGTH_SHORT
        ).show();

    }

            }
