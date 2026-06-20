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

public class LocationService extends Service {

    public static final String ACTION_START = "com.transiva.app.START_LOCATION";
    public static final String ACTION_STOP = "com.transiva.app.STOP_LOCATION";

    private static final String CHANNEL_ID = "transiva_location_channel";
    private static final String CHANNEL_NAME = "Lokasi Transiva";
    private static final int NOTIFICATION_ID = 2026;

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String DEFAULT_ENDPOINT = "server/updateDriverLocation.php";

    private static final long UPDATE_INTERVAL = 5000L;
    private static final long FASTEST_INTERVAL = 3000L;
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 20000;

    private Handler handler;
    private SessionManager sessionManager;

    private com.google.android.gms.location.FusedLocationProviderClient fusedClient;
    private com.google.android.gms.location.LocationRequest locationRequest;
    private com.google.android.gms.location.LocationCallback locationCallback;

    private boolean isRunning = false;
    private long lastSendTime = 0L;

    @Override
    public void onCreate() {
        super.onCreate();

        handler = new Handler(Looper.getMainLooper());
        sessionManager = new SessionManager(this);

        createChannel();
        setupLocationClient();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        String action = intent == null ? ACTION_START : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopLocationService();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification("Tracking lokasi aktif"));
        startLocationUpdates();

        return START_STICKY;
    }

    private void setupLocationClient() {
        try {
            fusedClient =
                    com.google.android.gms.location.LocationServices
                            .getFusedLocationProviderClient(this);

            locationRequest =
                    com.google.android.gms.location.LocationRequest.create()
                            .setInterval(UPDATE_INTERVAL)
                            .setFastestInterval(FASTEST_INTERVAL)
                            .setPriority(
                                    com.google.android.gms.location.LocationRequest
                                            .PRIORITY_HIGH_ACCURACY
                            );

            locationCallback =
                    new com.google.android.gms.location.LocationCallback() {
                        @Override
                        public void onLocationResult(
                                com.google.android.gms.location.LocationResult result
                        ) {
                            if (result == null) return;

                            Location location = result.getLastLocation();

                            if (location != null) {
                                handleLocation(location);
                            }
                        }
                    };

        } catch (Exception ignored) {}
    }

    private void startLocationUpdates() {
        if (isRunning) return;

        if (!hasLocationPermission()) {
            stopSelf();
            return;
        }

        try {
            isRunning = true;

            fusedClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
            );

        } catch (Exception e) {
            isRunning = false;
            stopSelf();
        }
    }

    private void handleLocation(Location location) {
        try {
            double lat = location.getLatitude();
            double lng = location.getLongitude();

            sessionManager.saveLastLocation(
                    String.valueOf(lat),
                    String.valueOf(lng)
            );

            long now = System.currentTimeMillis();

            if (now - lastSendTime < UPDATE_INTERVAL) {
                return;
            }

            lastSendTime = now;

            sendLocationToServer(location);

        } catch (Exception ignored) {}
    }

    private void sendLocationToServer(Location location) {
        new Thread(() -> {

            HttpURLConnection conn = null;

            try {
                String userId = sessionManager.getId();
                String username = sessionManager.getUsername();
                String role = sessionManager.getRole();
                String orderId = sessionManager.get("current_order_id");

                if (userId == null) userId = "";
                if (username == null) username = "";
                if (role == null) role = "";
                if (orderId == null) orderId = "";

                JSONObject body = new JSONObject();

                body.put("id", userId);
                body.put("user_id", userId);
                body.put("username", username);
                body.put("role", role);
                body.put("order_id", orderId);
                body.put("latitude", location.getLatitude());
                body.put("longitude", location.getLongitude());
                body.put("lat", location.getLatitude());
                body.put("lng", location.getLongitude());
                body.put("accuracy", location.hasAccuracy() ? location.getAccuracy() : 0);
                body.put("speed", location.hasSpeed() ? location.getSpeed() : 0);
                body.put("bearing", location.hasBearing() ? location.getBearing() : 0);
                body.put("provider", location.getProvider());
                body.put("timestamp", System.currentTimeMillis());
                body.put("source", "android_native_location_service");

                String endpoint = resolveEndpoint(role);
                URL url = new URL(BASE_URL + endpoint);

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

    private String resolveEndpoint(String role) {
        return DEFAULT_ENDPOINT;
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

            if (fusedClient != null && locationCallback != null) {
                fusedClient.removeLocationUpdates(locationCallback);
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
}
