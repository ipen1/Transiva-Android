package com.transiva.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

/*
 * BackgroundSyncService.java
 *
 * Fungsi:
 * - Menjaga sinkronisasi ringan Transiva di background.
 * - Mengirim lokasi terakhir yang tersimpan oleh LocationService.
 * - Mengirim status online driver ke endpoint updateDriverLocation.php.
 * - Retry otomatis saat gagal / internet kembali.
 * - Tidak crash walaupun session kosong, endpoint error, atau internet putus.
 *
 * Cocok dengan:
 * - https://transiva.my.id
 * - server/updateDriverLocation.php
 * - SessionManager.java
 * - LocationService.java
 */

public class BackgroundSyncService extends Service {

    public static final String ACTION_START = "com.transiva.app.START_BACKGROUND_SYNC";
    public static final String ACTION_STOP = "com.transiva.app.STOP_BACKGROUND_SYNC";
    public static final String ACTION_SYNC_NOW = "com.transiva.app.SYNC_NOW";

    private static final String CHANNEL_ID = "transiva_background_sync_channel";
    private static final String CHANNEL_NAME = "Sinkronisasi Transiva";
    private static final int NOTIFICATION_ID = 3030;

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String UPDATE_DRIVER_LOCATION_ENDPOINT = "server/updateDriverLocation.php";

    private static final long NORMAL_SYNC_INTERVAL = 15000L;
    private static final long FAST_RETRY_INTERVAL = 7000L;
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 20000;

    private Handler handler;
    private SessionManager sessionManager;

    private boolean isRunning = false;
    private int failCount = 0;

    private final Runnable syncRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;

            try {
                doSync();
            } catch (Exception ignored) {}

            long nextDelay = failCount > 0 ? FAST_RETRY_INTERVAL : NORMAL_SYNC_INTERVAL;

            try {
                handler.postDelayed(this, nextDelay);
            } catch (Exception ignored) {}
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        handler = new Handler(Looper.getMainLooper());
        sessionManager = new SessionManager(this);

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        String action = intent == null ? ACTION_START : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopSyncService();
            return START_NOT_STICKY;
        }

        startForeground(
                NOTIFICATION_ID,
                buildNotification("Sinkronisasi Transiva aktif")
        );

        if (ACTION_SYNC_NOW.equals(action)) {
            new Thread(() -> {
                try {
                    doSync();
                } catch (Exception ignored) {}
            }).start();
        }

        startLoop();

        return START_STICKY;
    }

    private void startLoop() {
        if (isRunning) return;

        isRunning = true;

        try {
            handler.removeCallbacks(syncRunnable);
            handler.post(syncRunnable);
        } catch (Exception ignored) {}
    }

    private void doSync() {
        try {
            sessionManager.put("background_sync_running", "1");
            sessionManager.put("background_sync_last_attempt", String.valueOf(System.currentTimeMillis()));

            if (!isOnline()) {
                failCount++;
                sessionManager.put("background_sync_status", "offline");
                sessionManager.put("background_sync_message", "Internet offline");
                return;
            }

            if (!sessionManager.isLoggedIn()) {
                failCount = 0;
                sessionManager.put("background_sync_status", "idle");
                sessionManager.put("background_sync_message", "Belum login");
                return;
            }

            String role = safe(sessionManager.getRole()).toLowerCase();

            if (role.contains("driver")) {
                syncDriverLocation();
            } else {
                sessionManager.put("background_sync_status", "idle");
                sessionManager.put("background_sync_message", "Role bukan driver");
                failCount = 0;
            }

        } catch (Exception e) {
            failCount++;
            sessionManager.put("background_sync_status", "error");
            sessionManager.put("background_sync_message", safe(e.getMessage()));
            sessionManager.put("background_sync_error_at", String.valueOf(System.currentTimeMillis()));
        }
    }

    private void syncDriverLocation() {
        try {
            String latitude = sessionManager.get("last_latitude");
            String longitude = sessionManager.get("last_longitude");

            if (latitude.isEmpty() || longitude.isEmpty()) {
                Location lastKnown = getQuickLastKnownLocation();

                if (lastKnown != null) {
                    latitude = String.valueOf(lastKnown.getLatitude());
                    longitude = String.valueOf(lastKnown.getLongitude());

                    sessionManager.saveLastLocation(latitude, longitude);
                }
            }

            if (latitude.isEmpty() || longitude.isEmpty()) {
                failCount++;
                sessionManager.put("background_sync_status", "waiting_location");
                sessionManager.put("background_sync_message", "Lokasi terakhir belum tersedia");
                return;
            }

            JSONObject body = new JSONObject();

            body.put("username", sessionManager.getUsername());
            body.put("order_id", sessionManager.get("current_order_id"));
            body.put("latitude", latitude);
            body.put("longitude", longitude);

            JSONObject extra = new JSONObject();
            extra.put("id", sessionManager.getId());
            extra.put("user_id", sessionManager.getUserId());
            extra.put("role", sessionManager.getRole());
            extra.put("source", "android_background_sync");
            extra.put("timestamp", System.currentTimeMillis());

            body.put("extra", extra);

            JSONObject response = postJson(
                    UPDATE_DRIVER_LOCATION_ENDPOINT,
                    body
            );

            boolean ok = response.optBoolean("success", false);

            if (ok) {
                failCount = 0;
                sessionManager.put("background_sync_status", "success");
                sessionManager.put("background_sync_message", response.optString("message", "Sinkron berhasil"));
                sessionManager.put("background_sync_last_success", String.valueOf(System.currentTimeMillis()));
                sessionManager.put("background_sync_last_response", response.toString());
            } else {
                failCount++;
                sessionManager.put("background_sync_status", "server_failed");
                sessionManager.put("background_sync_message", response.optString("message", "Server menolak sync"));
                sessionManager.put("background_sync_last_response", response.toString());
            }

        } catch (Exception e) {
            failCount++;
            sessionManager.put("background_sync_status", "error");
            sessionManager.put("background_sync_message", safe(e.getMessage()));
        }
    }

    private Location getQuickLastKnownLocation() {
        try {
            if (!hasLocationPermission()) return null;

            android.location.LocationManager lm =
                    (android.location.LocationManager) getSystemService(LOCATION_SERVICE);

            if (lm == null) return null;

            Location gps = null;
            Location network = null;

            try {
                gps = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
            } catch (Exception ignored) {}

            try {
                network = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
            } catch (Exception ignored) {}

            if (gps != null && network != null) {
                return gps.getTime() >= network.getTime() ? gps : network;
            }

            if (gps != null) return gps;
            return network;

        } catch (Exception e) {
            return null;
        }
    }

    private JSONObject postJson(String endpoint, JSONObject body) {
        HttpURLConnection conn = null;

        try {
            URL url = new URL(BASE_URL + cleanEndpoint(endpoint));

            conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);

            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("X-Transiva-Channel", "TransivaNative");
            conn.setRequestProperty("X-Transiva-Client", "Android-BackgroundSync");

            BufferedWriter writer =
                    new BufferedWriter(
                            new OutputStreamWriter(
                                    conn.getOutputStream(),
                                    "UTF-8"
                            )
                    );

            writer.write(body == null ? "{}" : body.toString());
            writer.flush();
            writer.close();

            int status = conn.getResponseCode();

            InputStream stream =
                    status >= 200 && status < 400
                            ? conn.getInputStream()
                            : conn.getErrorStream();

            String raw = readStream(stream);

            JSONObject result;

            try {
                result = new JSONObject(raw);
            } catch (Exception e) {
                result = new JSONObject();
                result.put("success", false);
                result.put("message", raw == null || raw.isEmpty() ? "Response kosong" : raw);
            }

            result.put("http_status", status);
            result.put("endpoint", endpoint);

            return result;

        } catch (Exception e) {
            try {
                JSONObject error = new JSONObject();
                error.put("success", false);
                error.put("message", safe(e.getMessage()));
                error.put("endpoint", endpoint);
                return error;
            } catch (Exception ignored) {
                return new JSONObject();
            }
        } finally {
            try {
                if (conn != null) conn.disconnect();
            } catch (Exception ignored) {}
        }
    }

    private String readStream(InputStream stream) {
        try {
            if (stream == null) return "";

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(stream, "UTF-8")
                    );

            StringBuilder builder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            reader.close();

            return builder.toString();

        } catch (Exception e) {
            return "";
        }
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

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private String cleanEndpoint(String endpoint) {
        String e = safe(endpoint).trim();

        while (e.startsWith("/")) {
            e = e.substring(1);
        }

        return e;
    }

    private Notification buildNotification(String message) {

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        PendingIntent openPendingIntent =
                PendingIntent.getActivity(
                        this,
                        3031,
                        openIntent,
                        PendingIntent.FLAG_IMMUTABLE |
                                PendingIntent.FLAG_UPDATE_CURRENT
                );

        Intent stopIntent = new Intent(this, BackgroundSyncService.class);
        stopIntent.setAction(ACTION_STOP);

        PendingIntent stopPendingIntent =
                PendingIntent.getService(
                        this,
                        3032,
                        stopIntent,
                        PendingIntent.FLAG_IMMUTABLE |
                                PendingIntent.FLAG_UPDATE_CURRENT
                );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Transiva")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(openPendingIntent)
                .addAction(
                        R.mipmap.ic_launcher,
                        "Stop",
                        stopPendingIntent
                )
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;

        try {
            NotificationManager manager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

            if (manager == null) return;

            NotificationChannel old = manager.getNotificationChannel(CHANNEL_ID);

            if (old != null) return;

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_LOW
                    );

            channel.setDescription("Sinkronisasi background untuk Transiva");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            channel.enableVibration(false);
            channel.enableLights(false);

            manager.createNotificationChannel(channel);

        } catch (Exception ignored) {}
    }

    private void stopSyncService() {
        try {
            isRunning = false;

            if (handler != null) {
                handler.removeCallbacks(syncRunnable);
            }

            if (sessionManager != null) {
                sessionManager.put("background_sync_running", "0");
                sessionManager.put("background_sync_status", "stopped");
                sessionManager.put("background_sync_message", "Service dihentikan");
            }

        } catch (Exception ignored) {}

        try {
            stopForeground(true);
        } catch (Exception ignored) {}

        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopSyncService();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void start(android.content.Context context) {
        try {
            Intent intent = new Intent(context, BackgroundSyncService.class);
            intent.setAction(ACTION_START);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception ignored) {}
    }

    public static void stop(android.content.Context context) {
        try {
            Intent intent = new Intent(context, BackgroundSyncService.class);
            intent.setAction(ACTION_STOP);
            context.startService(intent);
        } catch (Exception ignored) {}
    }

    public static void syncNow(android.content.Context context) {
        try {
            Intent intent = new Intent(context, BackgroundSyncService.class);
            intent.setAction(ACTION_SYNC_NOW);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception ignored) {}
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
