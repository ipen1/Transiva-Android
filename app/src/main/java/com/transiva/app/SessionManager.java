package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

public class SessionManager {

    private static final String PREF_NAME = "transiva_native_session";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveSession(String json) {
        try {
            JSONObject obj = new JSONObject(json == null ? "{}" : json);

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

        } catch (Exception ignored) {}
    }

    public JSONObject getSessionJson() {
        try {
            JSONObject obj = new JSONObject();

            obj.put("success", true);
            obj.put("id", getId());
            obj.put("user_id", getUserId());
            obj.put("username", getUsername());
            obj.put("name", getName());
            obj.put("role", getRole());
            obj.put("phone", getPhone());
            obj.put("token", getToken());
            obj.put("restaurant_id", getRestaurantId());
            obj.put("balance", getBalance());
            obj.put("saved_at", prefs.getLong("saved_at", 0));
            obj.put("raw_session", prefs.getString("raw_session", "{}"));

            return obj;

        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public String getSessionString() {
        return getSessionJson().toString();
    }

    public boolean isLoggedIn() {
        return !getId().isEmpty()
                || !getUsername().isEmpty()
                || !getToken().isEmpty();
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }

    public String getId() {
        return prefs.getString("id", "");
    }

    public String getUserId() {
        return prefs.getString("user_id", "");
    }

    public String getUsername() {
        return prefs.getString("username", "");
    }

    public String getName() {
        return prefs.getString("name", "");
    }

    public String getRole() {
        return prefs.getString("role", "");
    }

    public String getPhone() {
        return prefs.getString("phone", "");
    }

    public String getToken() {
        return prefs.getString("token", "");
    }

    public String getRestaurantId() {
        return prefs.getString("restaurant_id", "");
    }

    public String getBalance() {
        return prefs.getString("balance", "0");
    }

    public void saveFcmToken(String token) {
        prefs.edit()
                .putString("fcm_token", safe(token))
                .putLong("fcm_token_saved_at", System.currentTimeMillis())
                .apply();
    }

    public String getFcmToken() {
        return prefs.getString("fcm_token", "");
    }

    public void saveLastLocation(String latitude, String longitude) {
        prefs.edit()
                .putString("last_latitude", safe(latitude))
                .putString("last_longitude", safe(longitude))
                .putLong("last_location_at", System.currentTimeMillis())
                .apply();
    }

    public JSONObject getLastLocationJson() {
        try {
            JSONObject obj = new JSONObject();

            obj.put("success", true);
            obj.put("latitude", prefs.getString("last_latitude", ""));
            obj.put("longitude", prefs.getString("last_longitude", ""));
            obj.put("saved_at", prefs.getLong("last_location_at", 0));

            return obj;

        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public String getLastLocationString() {
        return getLastLocationJson().toString();
    }

    public void put(String key, String value) {
        if (key == null || key.trim().isEmpty()) return;

        prefs.edit()
                .putString(key, safe(value))
                .apply();
    }

    public String get(String key) {
        if (key == null || key.trim().isEmpty()) return "";
        return prefs.getString(key, "");
    }

    public void remove(String key) {
        if (key == null || key.trim().isEmpty()) return;

        prefs.edit()
                .remove(key)
                .apply();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
