package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

public final class TransivaSession {

    private static final String PREF_NAME = "transiva";
    private static final String KEY_LOGGED_IN = "logged_in";
    private static final String KEY_USER_JSON = "user_json";
    private static final String KEY_ID = "id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_ROLE = "role";
    private static final String KEY_DRIVER_TYPE = "driver_type";
    private static final String KEY_BALANCE = "balance";
    private static final String KEY_RESTAURANT_ID = "restaurant_id";
    private static final String KEY_WISATA_OWNER_ID = "wisata_owner_id";

    private TransivaSession() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void saveUser(Context context, JSONObject user) {
        if (context == null || user == null) return;

        SharedPreferences.Editor e = prefs(context).edit();
        e.putBoolean(KEY_LOGGED_IN, true);
        e.putString(KEY_USER_JSON, user.toString());
        e.putInt(KEY_ID, user.optInt("id", 0));
        e.putString(KEY_USERNAME, user.optString("username", ""));
        e.putString(KEY_ROLE, user.optString("role", "customer"));
        e.putString(KEY_DRIVER_TYPE, user.optString("driver_type", "bike"));
        e.putInt(KEY_BALANCE, user.optInt("balance", 0));
        e.putString(KEY_RESTAURANT_ID, String.valueOf(user.opt("restaurant_id")));
        e.putString(KEY_WISATA_OWNER_ID, String.valueOf(user.opt("wisata_owner_id")));
        e.apply();
    }

    public static boolean isLoggedIn(Context context) {
        return prefs(context).getBoolean(KEY_LOGGED_IN, false);
    }

    public static String getRole(Context context) {
        return prefs(context).getString(KEY_ROLE, "customer");
    }

    public static String getUsername(Context context) {
        return prefs(context).getString(KEY_USERNAME, "");
    }

    public static String getUserJson(Context context) {
        return prefs(context).getString(KEY_USER_JSON, "{}");
    }

    public static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }
}
