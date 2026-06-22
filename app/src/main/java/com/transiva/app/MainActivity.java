package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
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

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

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

    private TransivaBridge transivaBridge;
    private ApiClient apiClient;
    private SessionManager sessionManager;

    private static final int REQ_PERM = 10;
    private static final int REQ_FILE = 11;

    private static final String HOME_URL = "https://transiva.my.id/?app=1";
    private static final String BASE_HOST = "transiva.my.id";

    public static final String PREF_NAME = "transiva";
    public static final String PREF_FCM_TOKEN = "fcm_token";

    public static final String CHANNEL_ID = "transiva_order_channel";
    public static final String CHANNEL_NAME = "Order Transiva";

    private long lastBack = 0L;
    private boolean pageReady = false;
    private String lastNativeActionKey = "";
    private long lastNativeActionTime = 0L;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        try {
            getWindow().setStatusBarColor(Color.parseColor("#06142E"));
            getWindow().setNavigationBarColor(Color.parseColor("#06142E"));
        } catch (Exception ignored) {}

        sessionManager = new SessionManager(this);

        createNotificationChannel();
        requestAppPermissions();
        requestIgnoreBatteryOptimization();

        buildLayout();
        configureWebView();

        String url = resolveStartUrl(getIntent());
        safeLoadUrl(url);

        initFirebase();

        // Intent notifikasi diproses setelah WebView selesai load di onPageFinished.
        // Ini mencegah tombol Terima terkirim dua kali saat aplikasi baru dibuka.
        startServicesIfLoggedIn();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (webView != null) {
                webView.onResume();
                webView.resumeTimers();
            }

            startServicesIfLoggedIn();
        } catch (Exception ignored) {}
    }

    @Override
    protected void onPause() {
        try {
            if (webView != null) {
                webView.onPause();
                webView.pauseTimers();
            }
        } catch (Exception ignored) {}

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
                filePathCallback = null;
            }
        } catch (Exception ignored) {}

        try {
            if (webView != null) {
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.removeAllViews();
                webView.destroy();
                webView = null;
            }
        } catch (Exception ignored) {}

        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private String resolveStartUrl(Intent intent) {
        try {
            if (intent != null) {
                String customUrl = intent.getStringExtra("url");
                if (customUrl != null && customUrl.startsWith("https://transiva.my.id")) {
                    return customUrl;
                }

                Uri data = intent.getData();
                if (data != null && data.toString().startsWith("https://transiva.my.id")) {
                    return data.toString();
                }
            }
        } catch (Exception ignored) {}

        return HOME_URL;
    }

    private void buildLayout() {
        FrameLayout root = new FrameLayout(this);

        webView = new WebView(this);

        progressBar = new ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
        );

        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.VISIBLE);

        FrameLayout.LayoutParams barParams =
                new FrameLayout.LayoutParams(-1, 8, Gravity.TOP);

        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(progressBar, barParams);

        setContentView(root);
    }

    private void configureWebView() {
        if (webView == null) return;

        try {
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
            s.setSupportZoom(false);
            s.setBuiltInZoomControls(false);
            s.setDisplayZoomControls(false);
            s.setSaveFormData(true);
            s.setCacheMode(WebSettings.LOAD_DEFAULT);
            s.setUserAgentString(s.getUserAgentString() + " TransivaAndroidHybrid/1.0");

            if (Build.VERSION.SDK_INT >= 21) {
                s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
            }

            CookieManager.getInstance().setAcceptCookie(true);

        } catch (Exception e) {
            Log.e("TRANSIVA", "WebSettings error", e);
        }

        try {
            transivaBridge = new TransivaBridge(this, webView);
            apiClient = new ApiClient(this, webView);

            webView.addJavascriptInterface(transivaBridge, "TransivaNative");
            webView.addJavascriptInterface(transivaBridge, "AndroidNotif");
            webView.addJavascriptInterface(transivaBridge, "Android");
            webView.addJavascriptInterface(apiClient, "TransivaApi");
            webView.addJavascriptInterface(new NativeControlBridge(), "TransivaControl");

        } catch (Exception e) {
            Log.e("TRANSIVA", "Bridge error", e);
        }

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(
                    String url,
                    String userAgent,
                    String contentDisposition,
                    String mimeType,
                    long contentLength
            ) {
                downloadFile(url, userAgent, contentDisposition, mimeType);
            }
        });

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                try {
                    Uri u = r.getUrl();
                    return handleExternalUrl(u);
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                try {
                    return handleExternalUrl(Uri.parse(url));
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                pageReady = true;

                try {
                    progressBar.setVisibility(View.GONE);
                } catch (Exception ignored) {}

                injectCompatibilityBridge();
                sendSavedFcmTokenToWeb();
                syncSessionFromWebAndStartServices();
                sendNativeReady();
                handleIntent(getIntent());
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                try {
                    if (Build.VERSION.SDK_INT >= 23 && r != null && r.isForMainFrame()) {
                        showOfflinePage();
                    }
                } catch (Exception ignored) {}
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onProgressChanged(WebView v, int p) {
                try {
                    progressBar.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
                    progressBar.setProgress(p);
                } catch (Exception ignored) {}
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(
                    String origin,
                    GeolocationPermissions.Callback cb
            ) {
                try {
                    cb.invoke(origin, true, false);
                } catch (Exception ignored) {}
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                try {
                    runOnUiThread(() -> {
                        try {
                            request.grant(request.getResources());
                        } catch (Exception ignored) {}
                    });
                } catch (Exception ignored) {}
            }

            @Override
            public boolean onShowFileChooser(
                    WebView w,
                    ValueCallback<Uri[]> cb,
                    FileChooserParams params
            ) {
                try {
                    if (filePathCallback != null) {
                        filePathCallback.onReceiveValue(null);
                    }

                    filePathCallback = cb;

                    Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                    try {
                        File photo = createImageFile();

                        cameraImageUri = FileProvider.getUriForFile(
                                MainActivity.this,
                                getPackageName() + ".fileprovider",
                                photo
                        );

                        camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
                        camera.addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        );

                    } catch (Exception ex) {
                        camera = null;
                    }

                    Intent content = new Intent(Intent.ACTION_GET_CONTENT);
                    content.addCategory(Intent.CATEGORY_OPENABLE);
                    content.setType("*/*");
                    content.putExtra(
                            Intent.EXTRA_MIME_TYPES,
                            new String[]{
                                    "image/*",
                                    "video/*",
                                    "application/pdf"
                            }
                    );

                    Intent chooser = Intent.createChooser(content, "Pilih file");

                    if (camera != null) {
                        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
                    }

                    startActivityForResult(chooser, REQ_FILE);
                    return true;

                } catch (Exception e) {
                    if (filePathCallback != null) {
                        filePathCallback.onReceiveValue(null);
                        filePathCallback = null;
                    }
                    Toast.makeText(MainActivity.this, "Tidak bisa membuka file picker", Toast.LENGTH_SHORT).show();
                    return true;
                }
            }
        });
    }

    private boolean handleExternalUrl(Uri u) {
        if (u == null) return false;

        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.US);
        String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.US);

        if (host.contains(BASE_HOST)) {
            return false;
        }

        if (scheme.equals("http") || scheme.equals("https")) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, u));
            } catch (Exception e) {
                Toast.makeText(this, "Tidak bisa membuka link", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        if (
                scheme.equals("tel") ||
                scheme.equals("mailto") ||
                scheme.equals("whatsapp") ||
                scheme.equals("intent") ||
                scheme.equals("geo")
        ) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, u));
            } catch (Exception e) {
                Toast.makeText(this, "Aplikasi tujuan tidak tersedia", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        return false;
    }

    private void safeLoadUrl(String url) {
        try {
            if (webView != null) {
                webView.loadUrl(url == null || url.trim().isEmpty() ? HOME_URL : url);
            }
        } catch (Exception e) {
            showOfflinePage();
        }
    }

    private void handleIntent(Intent intent) {
        if (intent == null || webView == null) return;

        try {
            BackgroundSyncService.syncNow(this);
        } catch (Exception ignored) {}

        final String fromAction = getIntentString(intent, "from_notification_action");
        final String openScreen = getIntentString(intent, "open_screen");

        final String orderId = getIntentString(intent, "order_id");
        final String orderDbId = getIntentString(intent, "order_db_id");
        final String id = getIntentString(intent, "id");

        final String action = getIntentString(intent, "action");
        final String actor = getIntentString(intent, "actor");
        final String username = getIntentString(intent, "username");
        final String offeredDriver = getIntentString(intent, "offered_driver");
        final String driverType = getIntentString(intent, "driver_type");
        final String actionToken = getIntentString(intent, "action_token");
        final String endpoint = getIntentString(intent, "action_endpoint");

        final String type = getIntentString(intent, "type");
        final String title = getIntentString(intent, "title");
        final String message = getIntentString(intent, "message");

        final boolean isNotificationAction = "1".equals(fromAction);

        if (isNotificationAction) {
            String mainOrderId = !orderDbId.isEmpty() ? orderDbId : (!orderId.isEmpty() ? orderId : id);
            String currentAction = action.isEmpty() ? "driver_accept" : action;
            String actionKey = mainOrderId + "|" + currentAction + "|" + actionToken;
            long now = System.currentTimeMillis();

            if (actionKey.equals(lastNativeActionKey) && (now - lastNativeActionTime) < 6000) {
                return;
            }

            lastNativeActionKey = actionKey;
            lastNativeActionTime = now;
        }

        webView.postDelayed(() -> {
            try {
                String js;

                if (isNotificationAction) {

                    String fixedOpenScreen = openScreen.isEmpty() ? "driver_trip" : openScreen;
                    String fixedAction = action.isEmpty() ? "driver_accept" : action;
                    String fixedDriverType = driverType.isEmpty() ? "bike" : driverType;
                    String fixedId = !orderDbId.isEmpty() ? orderDbId : (!orderId.isEmpty() ? orderId : id);
                    String fixedActor = !actor.isEmpty() ? actor : (!username.isEmpty() ? username : offeredDriver);
                    String fixedOfferedDriver = !offeredDriver.isEmpty() ? offeredDriver : fixedActor;

                    js =
                            "(function(){" +
                                    "var payload={" +
                                    "from_notification_action:'1'," +
                                    "open_screen:'" + escapeJs(fixedOpenScreen) + "'," +
                                    "order_db_id:'" + escapeJs(fixedId) + "'," +
                                    "order_id:'" + escapeJs(fixedId) + "'," +
                                    "id:'" + escapeJs(fixedId) + "'," +
                                    "action:'" + escapeJs(fixedAction) + "'," +
                                    "actor:'" + escapeJs(fixedActor) + "'," +
                                    "username:'" + escapeJs(fixedActor) + "'," +
                                    "offered_driver:'" + escapeJs(fixedOfferedDriver) + "'," +
                                    "driver_type:'" + escapeJs(fixedDriverType) + "'," +
                                    "action_token:'" + escapeJs(actionToken) + "'," +
                                    "action_endpoint:'" + escapeJs(endpoint) + "'" +
                                    "};" +

                                    "window.TRANSIVA_PENDING_NOTIFICATION_ACTION=payload;" +
                                    "try{localStorage.setItem('pendingNotificationAction',JSON.stringify(payload));}catch(e){}" +
                                    "try{localStorage.setItem('pendingRoute','driverTrip');}catch(e){}" +

                                    "if(typeof window.onTransivaNotificationAction==='function'){" +
                                    "window.onTransivaNotificationAction(payload);" +
                                    "}else if(typeof window.handleNotificationAction==='function'){" +
                                    "window.handleNotificationAction(payload);" +
                                    "}else if(typeof window.transivaNotificationAction==='function'){" +
                                    "window.transivaNotificationAction(payload);" +
                                    "}else if(window.TransivaNativeAction && typeof window.TransivaNativeAction.handle==='function'){" +
                                    "window.TransivaNativeAction.handle(payload);" +
                                    "}" +

                                    "})();";

                } else {

                    js =
                            "(function(){" +
                                    "window.TRANSIVA_PUSH_TYPE='" + escapeJs(type) + "';" +
                                    "window.TRANSIVA_PUSH_ORDER_ID='" + escapeJs(orderId) + "';" +
                                    "window.TRANSIVA_PUSH_TITLE='" + escapeJs(title) + "';" +
                                    "window.TRANSIVA_PUSH_MESSAGE='" + escapeJs(message) + "';" +
                                    "window.dispatchEvent(new CustomEvent('transiva-native',{detail:{channel:'TransivaNative',event:'push_opened',data:{" +
                                    "type:'" + escapeJs(type) + "'," +
                                    "order_id:'" + escapeJs(orderId) + "'," +
                                    "title:'" + escapeJs(title) + "'," +
                                    "message:'" + escapeJs(message) + "'" +
                                    "}}}));" +
                                    "if(typeof window.onTransivaPush==='function'){" +
                                    "window.onTransivaPush(window.TRANSIVA_PUSH_TYPE,window.TRANSIVA_PUSH_ORDER_ID);" +
                                    "}" +
                                    "})();";
                }

                webView.evaluateJavascript(js, null);

            } catch (Exception e) {
                Log.e("TRANSIVA", "handleIntent JS error", e);
            }
        }, pageReady ? 700 : 1800);
    }

    private String getIntentString(Intent intent, String key) {
        try {
            String value = intent.getStringExtra(key);
            return value == null ? "" : value.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private void initFirebase() {
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                try {
                    if (!task.isSuccessful()) {
                        Log.e("FCM", "Gagal mengambil token", task.getException());
                        return;
                    }

                    String token = task.getResult();

                    if (token == null || token.trim().isEmpty()) {
                        return;
                    }

                    getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                            .edit()
                            .putString(PREF_FCM_TOKEN, token)
                            .apply();

                    try {
                        if (sessionManager != null) sessionManager.saveFcmToken(token);
                    } catch (Exception ignored) {}

                    sendFcmTokenToWeb(token);

                } catch (Exception e) {
                    Log.e("FCM", "Token handling error", e);
                }
            });
        } catch (Exception e) {
            Log.e("FCM", "Firebase init error", e);
        }
    }

    private void sendSavedFcmTokenToWeb() {
        try {
            String token = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                    .getString(PREF_FCM_TOKEN, "");

            if ((token == null || token.isEmpty()) && sessionManager != null) {
                token = sessionManager.getFcmToken();
            }

            if (token != null && !token.isEmpty()) {
                sendFcmTokenToWeb(token);
            }
        } catch (Exception ignored) {}
    }

    private void sendFcmTokenToWeb(String token) {
        if (webView == null || token == null) return;

        String safeToken = escapeJs(token);

        String js =
                "(function(){" +
                        "window.TRANSIVA_FCM_TOKEN='" + safeToken + "';" +
                        "if(window.TransivaNative && TransivaNative.saveFcmToken){try{TransivaNative.saveFcmToken('" + safeToken + "');}catch(e){}}" +
                        "if(typeof window.receiveFcmToken==='function'){" +
                        "window.receiveFcmToken('" + safeToken + "');" +
                        "}" +
                        "if(typeof window.saveTransivaFcmToken==='function'){" +
                        "window.saveTransivaFcmToken('" + safeToken + "');" +
                        "}" +
                        "window.dispatchEvent(new CustomEvent('transiva-native',{detail:{channel:'TransivaNative',event:'fcm_token_ready',data:{success:true,token:'" + safeToken + "'}}}));" +
                        "})();";

        try {
            webView.post(() -> {
                try {
                    webView.evaluateJavascript(js, null);
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    private void requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;

        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);

            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }

        } catch (Exception e) {
            try {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(intent);
            } catch (Exception ignored) {}
        }
    }

    private void requestAppPermissions() {
        try {
            ArrayList<String> p = new ArrayList<>();

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

            requestPermissions(p.toArray(new String[0]), REQ_PERM);

        } catch (Exception ignored) {}
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;

        try {
            NotificationManager manager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

            if (manager == null) return;

            NotificationChannel old = manager.getNotificationChannel(CHANNEL_ID);

            if (old != null) return;

            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );

            ch.enableVibration(true);
            ch.setDescription("Notifikasi order dan pesan penting Transiva");
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            ch.enableLights(true);
            ch.setLightColor(Color.WHITE);

            manager.createNotificationChannel(ch);

        } catch (Exception ignored) {}
    }

    private void injectCompatibilityBridge() {
        if (webView == null) return;

        String js =
                "(function(){" +
                        "if(window.TransivaNotifReady)return;" +
                        "window.TransivaNotifReady=true;" +

                        "window.transivaNotify=function(t,m){" +
                        "try{" +
                        "if(window.AndroidNotif && AndroidNotif.showOrderNotification){" +
                        "AndroidNotif.showOrderNotification(String(t||'Transiva'),String(m||''));return;" +
                        "}" +
                        "if(window.Android && Android.notify){" +
                        "Android.notify(String(t||'Transiva'),String(m||''));return;" +
                        "}" +
                        "}catch(e){}" +
                        "};" +

                        "window.transivaVibrate=function(ms){" +
                        "try{" +
                        "if(window.TransivaNative && TransivaNative.vibrateMs){" +
                        "TransivaNative.vibrateMs(parseInt(ms||300));return;" +
                        "}" +
                        "if(window.Android && Android.vibrateMs){" +
                        "Android.vibrateMs(parseInt(ms||300));return;" +
                        "}" +
                        "if(window.Android && Android.vibrate){" +
                        "Android.vibrate(parseInt(ms||300));return;" +
                        "}" +
                        "}catch(e){}" +
                        "};" +

                        "window.getTransivaFcmToken=function(){" +
                        "return window.TRANSIVA_FCM_TOKEN||'';" +
                        "};" +

                        "window.requestTransivaFcmToken=function(){" +
                        "try{" +
                        "if(window.TransivaNative && TransivaNative.getFcmToken){" +
                        "var t=TransivaNative.getFcmToken();" +
                        "window.TRANSIVA_FCM_TOKEN=t||'';" +
                        "if(typeof window.receiveFcmToken==='function'){" +
                        "window.receiveFcmToken(window.TRANSIVA_FCM_TOKEN);" +
                        "}" +
                        "return t;" +
                        "}" +
                        "if(window.Android && Android.getFcmToken){" +
                        "var a=Android.getFcmToken();" +
                        "window.TRANSIVA_FCM_TOKEN=a||'';" +
                        "if(typeof window.receiveFcmToken==='function'){" +
                        "window.receiveFcmToken(window.TRANSIVA_FCM_TOKEN);" +
                        "}" +
                        "return a;" +
                        "}" +
                        "}catch(e){}" +
                        "return '';" +
                        "};" +

                        "window.saveTransivaNativeSession=function(player){" +
                        "try{" +
                        "var data=(typeof player==='string')?player:JSON.stringify(player||{});" +
                        "if(window.TransivaNative && TransivaNative.saveSession){" +
                        "TransivaNative.saveSession(data);" +
                        "}" +
                        "if(window.TransivaControl && TransivaControl.saveSessionAndStart){" +
                        "TransivaControl.saveSessionAndStart(data);" +
                        "}" +
                        "}catch(e){}" +
                        "};" +

                        "window.transivaStartNativeServices=function(){" +
                        "try{if(window.TransivaControl){TransivaControl.startAllServices();}}catch(e){}" +
                        "};" +

                        "window.transivaStopNativeServices=function(){" +
                        "try{if(window.TransivaControl){TransivaControl.stopAllServices();}}catch(e){}" +
                        "};" +

                        "window.transivaLogoutNative=function(){" +
                        "try{if(window.TransivaControl){TransivaControl.clearSessionAndStop();}}catch(e){}" +
                        "};" +

                        "})();";

        try {
            webView.evaluateJavascript(js, null);
        } catch (Exception ignored) {}
    }

    private void sendNativeReady() {
        try {
            JSONObject data = new JSONObject();
            data.put("success", true);
            data.put("base_url", HOME_URL);
            data.put("online", isOnline());
            data.put("time", System.currentTimeMillis());

            if (transivaBridge != null) {
                transivaBridge.sendToWeb("native_ready", data.toString());
            }
        } catch (Exception ignored) {}
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

            if (cm == null) return false;

            NetworkInfo info = cm.getActiveNetworkInfo();

            return info != null && info.isConnected();

        } catch (Exception e) {
            return false;
        }
    }


    private void startServicesIfLoggedIn() {
        try {
            if (sessionManager == null) {
                sessionManager = new SessionManager(this);
            }

            if (!sessionManager.isLoggedIn()) {
                return;
            }

            startLocationServiceSafe();
            startBackgroundSyncServiceSafe();

        } catch (Exception ignored) {}
    }

    private void startLocationServiceSafe() {
        try {
            Intent intent = new Intent(this, LocationService.class);
            intent.setAction(LocationService.ACTION_START);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }

        } catch (Exception e) {
            Log.e("TRANSIVA", "Gagal start LocationService", e);
        }
    }

    private void stopLocationServiceSafe() {
        try {
            Intent intent = new Intent(this, LocationService.class);
            intent.setAction(LocationService.ACTION_STOP);
            startService(intent);

        } catch (Exception e) {
            Log.e("TRANSIVA", "Gagal stop LocationService", e);
        }
    }

    private void startBackgroundSyncServiceSafe() {
        try {
            BackgroundSyncService.start(this);
        } catch (Exception e) {
            Log.e("TRANSIVA", "Gagal start BackgroundSyncService", e);
        }
    }

    private void stopBackgroundSyncServiceSafe() {
        try {
            BackgroundSyncService.stop(this);
        } catch (Exception e) {
            Log.e("TRANSIVA", "Gagal stop BackgroundSyncService", e);
        }
    }

    private void stopAllNativeServices() {
        try {
            stopLocationServiceSafe();
            stopBackgroundSyncServiceSafe();
        } catch (Exception ignored) {}
    }

    private void syncNowSafe() {
        try {
            BackgroundSyncService.syncNow(this);
        } catch (Exception ignored) {}
    }

    private void syncSessionFromWebAndStartServices() {
        if (webView == null) return;

        try {
            String js =
                    "(function(){" +
                            "try{" +
                            "var p=localStorage.getItem('player')||" +
                            "localStorage.getItem('user')||" +
                            "localStorage.getItem('session')||" +
                            "localStorage.getItem('transiva_user')||'';" +
                            "return p||'';" +
                            "}catch(e){return '';}" +
                            "})();";

            webView.evaluateJavascript(js, value -> {
                try {
                    if (value == null) return;

                    String data = value;

                    if (data.startsWith("\"") && data.endsWith("\"")) {
                        data = data.substring(1, data.length() - 1)
                                .replace("\\\"", "\"")
                                .replace("\\\\", "\\");
                    }

                    if (data == null || data.trim().isEmpty() || data.equals("null")) {
                        return;
                    }

                    sessionManager.saveSession(data);

                    if (sessionManager.isLoggedIn()) {
                        startServicesIfLoggedIn();
                    }

                } catch (Exception ignored) {}
            });

        } catch (Exception ignored) {}
    }

    public class NativeControlBridge {

        @JavascriptInterface
        public void startAllServices() {
            runOnUiThread(() -> startServicesIfLoggedIn());
        }

        @JavascriptInterface
        public void stopAllServices() {
            runOnUiThread(() -> stopAllNativeServices());
        }

        @JavascriptInterface
        public void syncNow() {
            syncNowSafe();
        }

        @JavascriptInterface
        public void saveSessionAndStart(String json) {
            try {
                if (sessionManager == null) {
                    sessionManager = new SessionManager(MainActivity.this);
                }

                sessionManager.saveSession(json);

                runOnUiThread(() -> startServicesIfLoggedIn());

            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void clearSessionAndStop() {
            try {
                if (sessionManager != null) {
                    sessionManager.clearSession();
                }

                runOnUiThread(() -> stopAllNativeServices());

            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public String getNativeStatus() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("success", true);
                obj.put("logged_in", sessionManager != null && sessionManager.isLoggedIn());
                obj.put("role", sessionManager == null ? "" : sessionManager.getRole());
                obj.put("username", sessionManager == null ? "" : sessionManager.getUsername());
                obj.put("background_sync_status", sessionManager == null ? "" : sessionManager.get("background_sync_status"));
                obj.put("background_sync_message", sessionManager == null ? "" : sessionManager.get("background_sync_message"));
                obj.put("last_latitude", sessionManager == null ? "" : sessionManager.get("last_latitude"));
                obj.put("last_longitude", sessionManager == null ? "" : sessionManager.get("last_longitude"));
                obj.put("time", System.currentTimeMillis());
                return obj.toString();

            } catch (Exception e) {
                return "{\"success\":false}";
            }
        }
    }


    public void showLocalNotification(String title, String message) {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }

            if (title == null || title.trim().isEmpty()) {
                title = "Transiva";
            }

            if (message == null || message.trim().isEmpty()) {
                message = "Pesan baru masuk";
            }

            Intent intent = new Intent(MainActivity.this, MainActivity.class);
            intent.setAction("OPEN_TRANSIVA");
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_SINGLE_TOP |
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
            );

            PendingIntent pendingIntent =
                    PendingIntent.getActivity(
                            MainActivity.this,
                            1001,
                            intent,
                            PendingIntent.FLAG_IMMUTABLE |
                                    PendingIntent.FLAG_UPDATE_CURRENT
                    );

            NotificationCompat.Builder b =
                    new NotificationCompat.Builder(MainActivity.this, CHANNEL_ID)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle(title)
                            .setContentText(message)
                            .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                            .setPriority(NotificationCompat.PRIORITY_MAX)
                            .setCategory(NotificationCompat.CATEGORY_ALARM)
                            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                            .setDefaults(Notification.DEFAULT_ALL)
                            .setVibrate(new long[]{0, 500, 250, 500, 250, 900})
                            .setAutoCancel(true)
                            .setContentIntent(pendingIntent);

            NotificationManagerCompat
                    .from(MainActivity.this)
                    .notify((int) System.currentTimeMillis(), b.build());

            vibrateDevice(450);

        } catch (Exception e) {
            Log.e("TRANSIVA", "Notification error", e);
        }
    }

    private void vibrateDevice(int ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);

            if (v == null) return;

            if (ms < 50) ms = 300;
            if (ms > 3000) ms = 3000;

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
        } catch (Exception ignored) {}
    }

    private void downloadFile(
            String url,
            String ua,
            String cd,
            String mime
    ) {
        try {
            DownloadManager.Request req =
                    new DownloadManager.Request(Uri.parse(url));

            req.addRequestHeader("User-Agent", ua);

            req.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            req.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    URLUtil.guessFileName(url, cd, mime)
            );

            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

            if (dm != null) {
                dm.enqueue(req);
                Toast.makeText(this, "Download dimulai", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Download manager tidak tersedia", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Toast.makeText(this, "Download gagal", Toast.LENGTH_SHORT).show();
        }
    }

    private void showOfflinePage() {
        try {
            String html =
                    "<html>" +
                            "<head><meta name='viewport' content='width=device-width,initial-scale=1'></head>" +
                            "<body style='background:#06142E;color:white;font-family:sans-serif;text-align:center;padding:35px'>" +
                            "<div style='margin-top:80px'>" +
                            "<h2>Koneksi internet terputus</h2>" +
                            "<p>Periksa koneksi Anda lalu coba lagi.</p>" +
                            "<button style='padding:14px 22px;border-radius:14px;border:0;background:#ff8a00;color:white;font-weight:bold' onclick='location.href=\"" + HOME_URL + "\"'>" +
                            "Coba Lagi" +
                            "</button>" +
                            "</div>" +
                            "</body>" +
                            "</html>";

            if (webView != null) {
                webView.loadDataWithBaseURL(
                        "https://transiva.my.id/",
                        html,
                        "text/html",
                        "UTF-8",
                        null
                );
            }
        } catch (Exception ignored) {}
    }

    private File createImageFile() throws IOException {
        String time = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date());

        File dir = getExternalCacheDir();

        if (dir == null) {
            dir = getCacheDir();
        }

        return File.createTempFile(
                "TRANSIVA_" + time + "_",
                ".jpg",
                dir
        );
    }

    private String escapeJs(String value) {
        if (value == null) return "";

        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }


    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_PERM) {
            try {
                startServicesIfLoggedIn();
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onActivityResult(
            int request,
            int result,
            Intent data
    ) {
        super.onActivityResult(request, result, data);

        if (request == REQ_FILE) {
            Uri[] results = null;

            try {
                if (result == RESULT_OK) {
                    if (data == null || data.getData() == null) {
                        if (cameraImageUri != null) {
                            results = new Uri[]{cameraImageUri};
                        }
                    } else {
                        results = new Uri[]{data.getData()};
                    }
                }
            } catch (Exception ignored) {}

            try {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(results);
                }
            } catch (Exception ignored) {}

            filePathCallback = null;
            cameraImageUri = null;
        }
    }

    @Override
    public void onBackPressed() {
        try {
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
                return;
            }
        } catch (Exception ignored) {}

        long now = System.currentTimeMillis();

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