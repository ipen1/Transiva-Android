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
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

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
 * LocationService.java
 * Versi fix tanpa Google Play Services.
 * Aman untuk project yang belum punya dependency com.google.android.gms:play-services-location.
 *
 * Endpoint sesuai backend Transiva:
 * https://transiva.my.id/server/updateDriverLocation.php
 *
 * JSON yang dikirim:
 * username
 * order_id
 * latitude
 * longitude
 */

public class LocationService extends Service {

    public static final String ACTION_START = "com.transiva.app.START_LOCATION";
    public static final String ACTION_STOP = "com.transiva.app.STOP_LOCATION";

    private static final String CHANNEL_ID = "transiva_location_channel";
    private static final String CHANNEL_NAME = "Lokasi Transiva";
    private static final int NOTIFICATION_ID = 2026;

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String ENDPOINT = "server/updateDriverLocation.php";

    private static final long MIN_TIME_MS = 5000L;
    private static final float MIN_DISTANCE_METER = 3f;

    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 20000;

    private LocationManager locationManager;
    private SessionManager sessionManager;

    private boolean isRunning = false;
    private long lastSendTime = 0L;

    private final LocationListener gpsListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            handleLocation(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}

        @Override
        public void onProviderEnabled(String provider) {}

        @Override
        public void onProviderDisabled(String provider) {}
    };

    private final LocationListener networkListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            handleLocation(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}

        @Override
        public void onProviderEnabled(String provider) {}

        @Override
        public void onProviderDisabled(String provider) {}
    };

    @Override
    public void onCreate() {
        super.onCreate();

        sessionManager = new SessionManager(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        String action = intent == null ? ACTION_START : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopLocationService();
            return START_NOT_STICKY;
        }

        startForeground(
                NOTIFICATION_ID,
                buildNotification("Tracking lokasi Transiva aktif")
        );

        startLocationUpdates();

        return START_STICKY;
    }

    private void startLocationUpdates() {
        if (isRunning) return;

        if (!hasLocationPermission()) {
            stopSelf();
            return;
        }

        if (locationManager == null) {
            stopSelf();
            return;
        }

        try {
            isRunning = true;

            try {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        MIN_TIME_MS,
                        MIN_DISTANCE_METER,
                        gpsListener
                );
            } catch (Exception ignored) {}

            try {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        MIN_TIME_MS,
                        MIN_DISTANCE_METER,
                        networkListener
                );
            } catch (Exception ignored) {}

            Location last = getBestLastKnownLocation();

            if (last != null) {
                handleLocation(last);
            }

        } catch (Exception e) {
            isRunning = false;
            stopSelf();
        }
    }

    private Location getBestLastKnownLocation() {
        if (!hasLocationPermission() || locationManager == null) return null;

        Location gps = null;
        Location network = null;

        try {
            gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        } catch (Exception ignored) {}

        try {
            network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ignored) {}

        if (gps != null && network != null) {
            return gps.getTime() >= network.getTime() ? gps : network;
        }

        if (gps != null) return gps;

        return network;
    }

    private void handleLocation(Location location) {
        try {
            if (location == null) return;

            double lat = location.getLatitude();
            double lng = location.getLongitude();

            if (!isValidCoordinate(lat, lng)) return;

            sessionManager.saveLastLocation(
                    String.valueOf(lat),
                    String.valueOf(lng)
            );

            long now = System.currentTimeMillis();

            if (now - lastSendTime < MIN_TIME_MS) {
                return;
            }

            lastSendTime = now;

            sendLocationToServer(lat, lng);

        } catch (Exception ignored) {}
    }

    private void sendLocationToServer(double latitude, double longitude) {
        new Thread(() -> {

            HttpURLConnection conn = null;

            try {
                String username = sessionManager.getUsername();
                String orderId = sessionManager.get("current_order_id");

                if (username == null) username = "";
                if (orderId == null) orderId = "";

                if (username.trim().isEmpty()) {
                    sessionManager.put("last_location_error", "Username kosong");
                    return;
                }

                JSONObject body = new JSONObject();

                body.put("username", username);
                body.put("order_id", orderId);
                body.put("latitude", latitude);
                body.put("longitude", longitude);

                URL url = new URL(BASE_URL + ENDPOINT);

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
                conn.setRequestProperty("X-Transiva-Client", "Android-LocationService");

                BufferedWriter writer =
                        new BufferedWriter(
                                new OutputStreamWriter(
                                        conn.getOutputStream(),
                                        "UTF-8"
                                )
                        );

                writer.write(body.toString());
                writer.flush();
                writer.close();

                int status = conn.getResponseCode();

                InputStream stream =
                        status >= 200 && status < 400
                                ? conn.getInputStream()
                                : conn.getErrorStream();

                String response = readStream(stream);

                sessionManager.put("last_location_http_status", String.valueOf(status));
                sessionManager.put("last_location_response", response);
                sessionManager.put("last_location_sync_at", String.valueOf(System.currentTimeMillis()));

            } catch (Exception e) {
                sessionManager.put("last_location_error", e.getMessage());
                sessionManager.put("last_location_error_at", String.valueOf(System.currentTimeMillis()));
            } finally {
                try {
                    if (conn != null) conn.disconnect();
                } catch (Exception ignored) {}
            }

        }).start();
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

    private boolean isValidCoordinate(double lat, double lng) {
        return lat != 0
                && lng != 0
                && lat >= -90
                && lat <= 90
                && lng >= -180
                && lng <= 180;
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

    private Notification buildNotification(String text) {

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        PendingIntent openPendingIntent =
                PendingIntent.getActivity(
                        this,
                        2001,
                        openIntent,
                        PendingIntent.FLAG_IMMUTABLE |
                                PendingIntent.FLAG_UPDATE_CURRENT
                );

        Intent stopIntent = new Intent(this, LocationService.class);
        stopIntent.setAction(ACTION_STOP);

        PendingIntent stopPendingIntent =
                PendingIntent.getService(
                        this,
                        2002,
                        stopIntent,
                        PendingIntent.FLAG_IMMUTABLE |
                                PendingIntent.FLAG_UPDATE_CURRENT
                );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Transiva")
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
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

    private void createChannel() {
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

            channel.setDescription("Service lokasi aktif untuk driver Transiva");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            channel.enableVibration(false);
            channel.enableLights(false);

            manager.createNotificationChannel(channel);

        } catch (Exception ignored) {}
    }

    private void stopLocationService() {
        try {
            isRunning = false;

            if (locationManager != null) {
                try {
                    locationManager.removeUpdates(gpsListener);
                } catch (Exception ignored) {}

                try {
                    locationManager.removeUpdates(networkListener);
                } catch (Exception ignored) {}
            }

        } catch (Exception ignored) {}

        try {
            stopForeground(true);
        } catch (Exception ignored) {}

        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopLocationService();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void start(android.content.Context context) {
        try {
            Intent intent = new Intent(context, LocationService.class);
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
            Intent intent = new Intent(context, LocationService.class);
            intent.setAction(ACTION_STOP);
            context.startService(intent);
        } catch (Exception ignored) {}
    }
}
