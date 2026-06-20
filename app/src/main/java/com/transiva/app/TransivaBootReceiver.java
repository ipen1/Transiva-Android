package com.transiva.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class TransivaBootReceiver extends BroadcastReceiver {

    private static final String TAG = "TRANSIVA_BOOT";

    private static final String PREF_NAME = "transiva";
    private static final String KEY_DRIVER_ONLINE = "driver_online";
    private static final String KEY_MERCHANT_ONLINE = "merchant_online";

    @Override
    public void onReceive(Context context, Intent intent) {

        if (context == null || intent == null) {
            return;
        }

        String action = intent.getAction();

        Log.d(TAG, "Receiver aktif: " + action);

        boolean validAction =
                Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action) ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action);

        if (!validAction) {
            return;
        }

        boolean driverOnline =
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        .getBoolean(KEY_DRIVER_ONLINE, false);

        boolean merchantOnline =
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        .getBoolean(KEY_MERCHANT_ONLINE, false);

        if (!driverOnline && !merchantOnline) {
            Log.d(TAG, "User tidak online, service tidak dijalankan");
            return;
        }

        startForegroundServiceSafe(context);
    }

    private void startForegroundServiceSafe(Context context) {

        try {

            Intent serviceIntent =
                    new Intent(context, TransivaDriverForegroundService.class);

            serviceIntent.setAction(
                    TransivaDriverForegroundService.ACTION_START
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            Log.d(TAG, "Foreground service berhasil dijalankan ulang");

        } catch (Exception e) {
            Log.e(TAG, "Gagal menjalankan foreground service: " + e.getMessage());
        }
    }
}
