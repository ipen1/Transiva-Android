package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONObject;

public class TransivaBridge {

    private final Activity activity;
    private final WebView webView;
    private final Handler mainHandler;
    private final SharedPreferences prefs;

    private static final String PREF_NAME = "transiva_native_session";
    private static final String CHANNEL_NAME = "TransivaNative";
    private static final String BASE_URL = "https://transiva.my.id/";

    public TransivaBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @JavascriptInterface
    public String ping() {
        return "pong";
    }

    @JavascriptInterface
    public String getChannelName() {
        return CHANNEL_NAME;
    }

    @JavascriptInterface
    public String getBaseUrl() {
        return BASE_URL;
    }

    @JavascriptInterface
    public String getDeviceInfo() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("success", true);
            obj.put("channel", CHANNEL_NAME);
            obj.put("base_url", BASE_URL);
            obj.put("brand", Build.BRAND);
            obj.put("model", Build.MODEL);
            obj.put("android", Build.VERSION.RELEASE);
            obj.put("sdk", Build.VERSION.SDK_INT);
            obj.put("app", "Transiva");
            obj.put("type", "hybrid-native");
            return obj.toString();
        } catch (Exception e) {
            return makeError("getDeviceInfo", e);
        }
    }

    @JavascriptInterface
    public void toast(final String message) {
        runOnUi(() -> {
            try {
                Toast.makeText(
                        activity,
                        safe(message, "Transiva"),
                        Toast.LENGTH_SHORT
                ).show();
            } catch (Exception ignored) {}
        });
    }

    @JavascriptInterface
    public void toastLong(final String message) {
        runOnUi(() -> {
            try {
                Toast.makeText(
                        activity,
                        safe(message, "Transiva"),
                        Toast.LENGTH_LONG
                ).show();
            } catch (Exception ignored) {}
        });
    }

    @JavascriptInterface
    public void notify(String title, String message) {
        showOrderNotification(title, message);
    }

    @JavascriptInterface
    public void showOrderNotification(String title, String message) {
        runOnUi(() -> {
            try {
                String t = safe(title, "Transiva");
                String m = safe(message, "Ada notifikasi baru");

                Toast.makeText(
                        activity,
                        t + "\n" + m,
                        Toast.LENGTH_LONG
                ).show();

                vibratePattern();

                sendToWeb(
                        "native_notification_shown",
                        makePayload("title", t, "message", m)
                );

            } catch (Exception e) {
                sendToWeb("native_notification_error", makeError("showOrderNotification", e));
            }
        });
    }

    @JavascriptInterface
    public void vibrate() {
        vibrateMs(300);
    }

    @JavascriptInterface
    public void vibrateMs(final int ms) {
        if (!CustomerAppSettings.isVibrationEnabled(activity)) return;
        runOnUi(() -> {
            try {
                Vibrator vibrator =
                        (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);

                if (vibrator == null) return;

                int duration = Math.max(50, Math.min(ms, 3000));

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                            VibrationEffect.createOneShot(
                                    duration,
                                    VibrationEffect.DEFAULT_AMPLITUDE
                            )
                    );
                } else {
                    vibrator.vibrate(duration);
                }

            } catch (Exception ignored) {}
        });
    }

    @JavascriptInterface
    public void vibratePattern() {
        if (!CustomerAppSettings.isVibrationEnabled(activity)) return;
        runOnUi(() -> {
            try {
                Vibrator vibrator =
                        (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);

                if (vibrator == null) return;

                long[] pattern = new long[]{0, 250, 120, 250, 120, 400};

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                            VibrationEffect.createWaveform(pattern, -1)
                    );
                } else {
                    vibrator.vibrate(pattern, -1);
                }

            } catch (Exception ignored) {}
        });
    }

    @JavascriptInterface
    public void saveSession(String json) {
        try {
            JSONObject obj = new JSONObject(safe(json, "{}"));

            prefs.edit()
                    .putString("raw_session", obj.toString())
                    .putString("id", obj.optString("id", ""))
                    .putString("user_id", obj.optString("user_id", obj.optString("id", "")))
                    .putString("username", obj.optString("username", ""))
                    .putString("name", obj.optString("name", ""))
                    .putString("role", obj.optString("role", ""))
                    .putString("phone", obj.optString("phone", ""))
                    .putString("token", obj.optString("token", ""))
                    .putString("restaurant_id", obj.optString("restaurant_id", ""))
                    .putString("balance", obj.optString("balance", "0"))
                    .putLong("saved_at", System.currentTimeMillis())
                    .apply();

            sendToWeb("session_saved", makeSuccess("Session tersimpan"));

        } catch (Exception e) {
            sendToWeb("session_error", makeError("saveSession", e));
        }
    }

    @JavascriptInterface
    public String getSession() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("success", true);
            obj.put("id", prefs.getString("id", ""));
            obj.put("user_id", prefs.getString("user_id", ""));
            obj.put("username", prefs.getString("username", ""));
            obj.put("name", prefs.getString("name", ""));
            obj.put("role", prefs.getString("role", ""));
            obj.put("phone", prefs.getString("phone", ""));
            obj.put("token", prefs.getString("token", ""));
            obj.put("restaurant_id", prefs.getString("restaurant_id", ""));
            obj.put("balance", prefs.getString("balance", "0"));
            obj.put("saved_at", prefs.getLong("saved_at", 0));
            obj.put("raw_session", prefs.getString("raw_session", "{}"));
            return obj.toString();
        } catch (Exception e) {
            return makeError("getSession", e);
        }
    }

    @JavascriptInterface
    public boolean isLoggedIn() {
        String id = prefs.getString("id", "");
        String username = prefs.getString("username", "");
        String token = prefs.getString("token", "");
        return !id.isEmpty() || !username.isEmpty() || !token.isEmpty();
    }

    @JavascriptInterface
    public void clearSession() {
        try {
            prefs.edit().clear().apply();
            sendToWeb("session_cleared", makeSuccess("Session dihapus"));
        } catch (Exception e) {
            sendToWeb("session_error", makeError("clearSession", e));
        }
    }

    @JavascriptInterface
    public void saveLastLocation(String latitude, String longitude) {
        try {
            prefs.edit()
                    .putString("last_latitude", safe(latitude, ""))
                    .putString("last_longitude", safe(longitude, ""))
                    .putLong("last_location_at", System.currentTimeMillis())
                    .apply();

            sendToWeb("location_saved", makeSuccess("Lokasi tersimpan"));

        } catch (Exception e) {
            sendToWeb("location_error", makeError("saveLastLocation", e));
        }
    }

    @JavascriptInterface
    public String getLastLocation() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("success", true);
            obj.put("latitude", prefs.getString("last_latitude", ""));
            obj.put("longitude", prefs.getString("last_longitude", ""));
            obj.put("saved_at", prefs.getLong("last_location_at", 0));
            return obj.toString();
        } catch (Exception e) {
            return makeError("getLastLocation", e);
        }
    }

    @JavascriptInterface
    public boolean isOnline() {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (cm == null) return false;

            NetworkInfo info = cm.getActiveNetworkInfo();

            return info != null && info.isConnected();

        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public String getNetworkStatus() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("success", true);
            obj.put("online", isOnline());
            return obj.toString();
        } catch (Exception e) {
            return makeError("getNetworkStatus", e);
        }
    }

    @JavascriptInterface
    public boolean hasLocationPermission() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;

            return activity.checkSelfPermission(
                    Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED;

        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public void saveFcmToken(String token) {
        try {
            token = safe(token, "").trim();

            if (token.isEmpty()) {
                sendToWeb("fcm_token_error", makeErrorMessage("Token FCM kosong"));
                return;
            }

            prefs.edit()
                    .putString("fcm_token", token)
                    .putLong("fcm_token_saved_at", System.currentTimeMillis())
                    .apply();

            sendToWeb("fcm_token_saved", makePayload("token", token));

        } catch (Exception e) {
            sendToWeb("fcm_token_error", makeError("saveFcmToken", e));
        }
    }

    @JavascriptInterface
    public String getFcmToken() {
        return prefs.getString("fcm_token", "");
    }

    @JavascriptInterface
    public void sendFcmTokenToWeb() {
        try {
            String token = prefs.getString("fcm_token", "");

            if (token.isEmpty()) {
                sendToWeb("fcm_token_empty", makeErrorMessage("Token FCM belum tersedia"));
                return;
            }

            String safeToken = escapeJs(token);

            runOnUi(() -> {
                try {
                    String js =
                            "try{" +
                                    "window.TRANSIVA_FCM_TOKEN='" + safeToken + "';" +
                                    "if(window.receiveFcmToken){window.receiveFcmToken('" + safeToken + "');}" +
                                    "if(window.saveTransivaFcmToken){window.saveTransivaFcmToken('" + safeToken + "');}" +
                                    "}catch(e){}";

                    webView.evaluateJavascript(js, null);
                } catch (Exception ignored) {}
            });

        } catch (Exception e) {
            sendToWeb("fcm_token_error", makeError("sendFcmTokenToWeb", e));
        }
    }

    @JavascriptInterface
    public void sendEvent(String eventName, String jsonData) {
        sendToWeb(
                safe(eventName, "native_event"),
                safeJson(jsonData)
        );
    }

    public void sendToWeb(final String eventName, final String jsonData) {
        runOnUi(() -> {
            try {
                if (webView == null) return;

                String js =
                        "window.dispatchEvent(new CustomEvent('transiva-native', {" +
                                "detail: {" +
                                "channel: '" + CHANNEL_NAME + "'," +
                                "event: '" + escapeJs(eventName) + "'," +
                                "data: " + safeJson(jsonData) +
                                "}" +
                                "}));";

                webView.evaluateJavascript(js, null);

            } catch (Exception ignored) {}
        });
    }

    private String makeSuccess(String message) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("success", true);
            obj.put("message", message);
            obj.put("time", System.currentTimeMillis());
            return obj.toString();
        } catch (Exception e) {
            return "{\"success\":true}";
        }
    }

    private String makePayload(String key, String value) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("success", true);
            obj.put(key, value);
            obj.put("time", System.currentTimeMillis());
            return obj.toString();
        } catch (Exception e) {
            return "{\"success\":true}";
        }
    }

    private String makePayload(String key1, String value1, String key2, String value2) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("success", true);
            obj.put(key1, value1);
            obj.put(key2, value2);
            obj.put("time", System.currentTimeMillis());
            return obj.toString();
        } catch (Exception e) {
            return "{\"success\":true}";
        }
    }

    private String makeError(String from, Exception e) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("success", false);
            obj.put("from", from);
            obj.put("message", e == null ? "Unknown error" : e.getMessage());
            obj.put("time", System.currentTimeMillis());
            return obj.toString();
        } catch (Exception ex) {
            return "{\"success\":false}";
        }
    }

    private String makeErrorMessage(String message) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("success", false);
            obj.put("message", message);
            obj.put("time", System.currentTimeMillis());
            return obj.toString();
        } catch (Exception e) {
            return "{\"success\":false}";
        }
    }

    private String safeJson(String json) {
        try {
            if (json == null || json.trim().isEmpty()) return "{}";

            String text = json.trim();

            if (text.startsWith("{")) {
                new JSONObject(text);
                return text;
            }

            JSONObject obj = new JSONObject();
            obj.put("value", text);
            return obj.toString();

        } catch (Exception e) {
            return "{}";
        }
    }

    private String safe(String value, String fallback) {
        if (value == null) return fallback;
        return value;
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

    private void runOnUi(Runnable runnable) {
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                runnable.run();
            } else {
                mainHandler.post(runnable);
            }
        } catch (Exception ignored) {}
    }
}