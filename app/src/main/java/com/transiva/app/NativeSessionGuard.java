package com.transiva.app;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * NativeSessionGuard.java
 *
 * Helper kecil supaya semua komponen native memakai aturan session yang sama.
 * Pakai ini sebelum start LocationService, BackgroundSyncService, dan service foreground lain.
 */
public final class NativeSessionGuard {

    private static final String TAG = "TRANSIVA_GUARD";

    private NativeSessionGuard() {}

    public static boolean isLoggedIn(Context context) {
        try {
            return context != null && new SessionManager(context).isLoggedIn();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean canRunNativeServices(Context context) {
        try {
            return context != null && new SessionManager(context).canRunNativeServices();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean canRunDriverLocation(Context context) {
        try {
            return context != null && new SessionManager(context).canRunDriverLocation();
        } catch (Exception e) {
            return false;
        }
    }

    public static void clearAndStop(Context context, String reason) {
        if (context == null) return;

        try {
            SessionManager sessionManager = new SessionManager(context);
            sessionManager.markLoggedOut(reason == null ? "logout" : reason);
        } catch (Exception ignored) {}

        stopAllNativeServices(context);
    }

    public static void stopAllNativeServices(Context context) {
        if (context == null) return;

        try {
            Intent location = new Intent(context, LocationService.class);
            location.setAction(LocationService.ACTION_STOP);
            context.startService(location);
        } catch (Exception e) {
            Log.e(TAG, "Stop LocationService gagal", e);
        }

        try {
            BackgroundSyncService.stop(context);
        } catch (Exception e) {
            Log.e(TAG, "Stop BackgroundSyncService gagal", e);
        }

        try {
            Intent driverForeground = new Intent(context, TransivaDriverForegroundService.class);
            driverForeground.setAction(TransivaDriverForegroundService.ACTION_STOP);
            context.startService(driverForeground);
        } catch (Exception ignored) {}
    }

    public static boolean startAllowedServices(Context context) {
        if (context == null) return false;

        if (!canRunNativeServices(context)) {
            stopAllNativeServices(context);
            return false;
        }

        try {
            SessionManager sessionManager = new SessionManager(context);
            sessionManager.touchSession();

            String role = sessionManager.getRole();

            if ("driver".equals(role)) {
                startLocationService(context);
                BackgroundSyncService.start(context);
                startDriverForegroundService(context);
                return true;
            }

            if ("merchant".equals(role) || "admin".equals(role) || "wisata".equals(role)) {
                BackgroundSyncService.start(context);
                return true;
            }

        } catch (Exception e) {
            Log.e(TAG, "Start allowed services gagal", e);
        }

        return false;
    }

    private static void startLocationService(Context context) {
        try {
            Intent intent = new Intent(context, LocationService.class);
            intent.setAction(LocationService.ACTION_START);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Start LocationService gagal", e);
        }
    }

    private static void startDriverForegroundService(Context context) {
        try {
            Intent intent = new Intent(context, TransivaDriverForegroundService.class);
            intent.setAction(TransivaDriverForegroundService.ACTION_START);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Start DriverForegroundService gagal", e);
        }
    }
}
