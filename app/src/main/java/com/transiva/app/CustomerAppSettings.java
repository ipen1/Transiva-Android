package com.transiva.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public final class CustomerAppSettings {
    private static final String PREF = "customer_app_settings";
    private static final String KEY_DARK = "dark_mode";
    private static final String KEY_VIBRATE = "vibration_enabled";

    private CustomerAppSettings() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static boolean isDarkMode(Context context) { return prefs(context).getBoolean(KEY_DARK, false); }
    public static void setDarkMode(Context context, boolean enabled) { prefs(context).edit().putBoolean(KEY_DARK, enabled).apply(); }
    public static boolean isVibrationEnabled(Context context) { return prefs(context).getBoolean(KEY_VIBRATE, true); }
    public static void setVibrationEnabled(Context context, boolean enabled) { prefs(context).edit().putBoolean(KEY_VIBRATE, enabled).apply(); }

    public static void apply(Activity activity) {
        boolean dark = isDarkMode(activity);
        activity.getWindow().setStatusBarColor(Color.parseColor(dark ? "#071426" : "#0B7CFF"));
        activity.getWindow().setNavigationBarColor(Color.parseColor("#071426"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        View root = activity.findViewById(android.R.id.content);
        if (root != null) applyRecursive(root, dark);
    }

    private static void applyRecursive(View view, boolean dark) {
        if (!dark) return;
        if (view.getBackground() instanceof ColorDrawable) {
            int c = ((ColorDrawable) view.getBackground()).getColor();
            if (isLight(c)) view.setBackgroundColor(Color.parseColor("#101C2B"));
        }
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            int c = text.getCurrentTextColor();
            if (isDark(c)) text.setTextColor(Color.parseColor("#EAF2FF"));
            else if (isMuted(c)) text.setTextColor(Color.parseColor("#A9B8CC"));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyRecursive(group.getChildAt(i), true);
        }
    }

    private static boolean isLight(int c) {
        return Color.alpha(c) > 180 && Color.red(c) > 225 && Color.green(c) > 225 && Color.blue(c) > 225;
    }
    private static boolean isDark(int c) {
        return Color.alpha(c) > 180 && Color.red(c) < 75 && Color.green(c) < 100 && Color.blue(c) < 135;
    }
    private static boolean isMuted(int c) {
        return Color.alpha(c) > 180 && Color.red(c) < 155 && Color.green(c) < 170 && Color.blue(c) < 190;
    }
}
