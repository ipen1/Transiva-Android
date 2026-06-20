package com.transiva.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class TransivaBootReceiver extends BroadcastReceiver {

    private static final String TAG = "TRANSIVA_BOOT";

    @Override
    public void onReceive(Context context, Intent intent) {

        if (context == null || intent == null) {
            return;
        }

        String action = intent.getAction();

        Log.d(TAG, "Receiver aktif: " + action);

        if (
                Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action) ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
        ) {

            /*
             * Jangan paksa service aktif untuk semua user.
             * Idealnya hanya aktif jika driver sebelumnya sedang ONLINE.
             */
            boolean driverOnline =
                    context.getSharedPreferences("transiva", Context.MODE_PRIVATE)
                            .getBoolean("driver_online", false);

            if (!driverOnline) {
                Log.d(TAG, "Driver tidak online, service tidak dijalankan");
                return;
            }

            startDriverService(context);
        }
    }

    private void startDriverService(Context context) {

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

            Log.d(TAG, "Driver foreground service dijalankan ulang");

        } catch (Exception e) {

            Log.e(TAG, "Gagal menjalankan service: " + e.getMessage());

        }
    }
}
