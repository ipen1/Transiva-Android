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

    public TransivaBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @JavascriptInterface
    public String getChannelName() {
        return CHANNEL_NAME;
    }

    @JavascriptInterface
    public String ping() {
        return "pong";
    }

    @JavascriptInterface
    public String getDeviceInfo() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("success", true);
            obj.put("channel", CHANNEL_NAME);
            obj.put("brand", Build.BRAND);
            obj.put("model", Build.MODEL);
            obj.put("android", Build.VERSION.RELEASE);
            obj.put("sdk", Build.VERSION.SDK_INT);
            obj.put("app", "Transiva");
            obj.put("type", "hybrid-native");
            return obj.toString();
        } catch (Exception e) {
            return error("getDeviceInfo", e);
        }
    }

    @JavascriptInterface
    public void toast(final String message) {
        runOnUi(() -> {
            try {
                Toast.makeText(
                        activity,
                        safeText(message, "Transiva"),
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
                        safeText(message, "Transiva"),
                        Toast.LENGTH_LONG
                ).show();
            } catch (Exception ignored) {}
        });
    }

    @JavascriptInterface
    public void vibrate() {
        vibrateMs(250);
    }

    @JavascriptInterface
    public void vibrateMs(final int ms) {
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
    public void saveSession(String json) {
        try {
            JSONObject obj = new JSONObject(safeText(json, "{}"));

            SharedPreferences.Editor editor = prefs.edit();

            editor.putString("raw_session", obj.toString());
            editor.putString("id", obj.optString("id", ""));
            editor.putString("username", obj.optString("username", ""));
            editor.putString("role", obj.optString("role", ""));
            editor.putString("token", obj.optString("token", ""));
            editor.putString("restaurant_id", obj.optString("restaurant_id", ""));
            editor.putLong("saved_at", System.currentTimeMillis());

            editor.apply();

            sendToWeb("session_saved", makeSuccess("Session tersimpan"));
        } catch (Exception e) {
            sendToWeb("session_error", error("saveSession", e));
        }
    }

    @JavascriptInterface
    public String getSession() {
        try {
            JSONObject obj = new JSONObject();

            obj.put("success", true);
            obj.put("id", prefs.getString("id", ""));
            obj.put("username", prefs.getString("username", ""));
            obj.put("role", prefs.getString("role", ""));
            obj.put("token", prefs.getString("token", ""));
            obj.put("restaurant_id", prefs.getString("restaurant_id", ""));
            obj.put("saved_at", prefs.getLong("saved_at", 0));
            obj.put("raw_session", prefs.getString("raw_session", "{}"));

            return obj.toString();
        } catch (Exception e) {
            return error("getSession", e);
        }
    }

    @JavascriptInterface
    public void clearSession() {
        try {
            prefs.edit().clear().apply();
            sendToWeb("session_cleared", makeSuccess("Session dihapus"));
        } catch (Exception e) {
            sendToWeb("session_error", error("clearSession", e));
        }
    }

    @JavascriptInterface
    public boolean isLoggedIn() {
        String token = prefs.getString("token", "");
        String username = prefs.getString("username", "");
        return !token.isEmpty() || !username.isEmpty();
    }

    @JavascriptInterface
    public void saveLastLocation(String latitude, String longitude) {
        try {
            prefs.edit()
                    .putString("last_latitude", safeText(latitude, ""))
                    .putString("last_longitude", safeText(longitude, ""))
                    .putLong("last_location_at", System.currentTimeMillis())
                    .apply();

            sendToWeb("location_saved", makeSuccess("Lokasi tersimpan"));
        } catch (Exception e) {
            sendToWeb("location_error", error("saveLastLocation", e));
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
            return error("getLastLocation", e);
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
            return error("getNetworkStatus", e);
        }
    }

    @JavascriptInterface
    public boolean hasLocationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;

        return activity.checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    @JavascriptInterface
    public void sendEvent(String eventName, String jsonData) {
        try {
            JSONObject obj = new JSONObject();

            obj.put("success", true);
            obj.put("event", safeText(eventName, "unknown"));
            obj.put("data", safeText(jsonData, "{}"));
            obj.put("time", System.currentTimeMillis());

            sendToWeb("native_event_received", obj.toString());
        } catch (Exception e) {
            sendToWeb("native_event_error", error("sendEvent", e));
        }
    }

    public void sendToWeb(final String eventName, final String jsonData) {
        runOnUi(() -> {
            try {
                if (webView == null) return;

                String safeEvent = escapeJs(safeText(eventName, "native_event"));
                String safeJson = safeJson(jsonData);

                String script =
                        "window.dispatchEvent(new CustomEvent('transiva-native', {" +
                                "detail: {" +
                                "channel: '" + CHANNEL_NAME + "'," +
                                "event: '" + safeEvent + "'," +
                                "data: " + safeJson +
                                "}" +
                                "}));";

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    webView.evaluateJavascript(script, null);
                } else {
                    webView.loadUrl("javascript:" + script);
                }
            } catch (Exception ignored) {}
        });
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

    private String error(String from, Exception e) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("success", false);
            obj.put("from", from);
            obj.put("message", e == null ? "Unknown error" : e.getMessage());
            return obj.toString();
        } catch (Exception ex) {
            return "{\"success\":false}";
        }
    }

    private String safeText(String value, String fallback) {
        if (value == null) return fallback;
        return value;
    }

    private String safeJson(String json) {
        try {
            if (json == null || json.trim().isEmpty()) {
                return "{}";
            }

            new JSONObject(json);
            return json;
        } catch (Exception e) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("value", safeText(json, ""));
                return obj.toString();
            } catch (Exception ex) {
                return "{}";
            }
        }
    }

    private String escapeJs(String text) {
        if (text == null) return "";

        return text
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
                    }
