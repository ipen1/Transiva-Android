package com.transiva.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class TransivaFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "TransivaFCM";

    public static final String CHANNEL_ID = "transiva_order_channel";
    public static final String CHANNEL_NAME = "Order Transiva";

    private static final String PREF_NAME = "transiva";
    private static final String PREF_FCM_TOKEN = "fcm_token";

    private static final String ACTION_ACCEPT = "com.transiva.app.ACTION_ACCEPT_ORDER";
    private static final String ACTION_REJECT = "com.transiva.app.ACTION_REJECT_ORDER";
    private static final String ACTION_OPEN = "OPEN_TRANSIVA";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);

        if (token == null || token.trim().isEmpty()) return;

        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .edit()
                .putString(PREF_FCM_TOKEN, token)
                .apply();
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        createNotificationChannel();

        Map<String, String> data = remoteMessage.getData();

        if (data == null || data.isEmpty()) {
            return;
        }

        showOrderNotification(data);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String act = intent.getAction();

            if (ACTION_ACCEPT.equals(act) || ACTION_REJECT.equals(act)) {
                HashMap<String, String> data = new HashMap<>();

                for (String key : intent.getExtras() == null ? new String[]{} : intent.getExtras().keySet()) {
                    Object value = intent.getExtras().get(key);
                    if (value != null) {
                        data.put(key, String.valueOf(value));
                    }
                }

                String action = ACTION_ACCEPT.equals(act)
                        ? value(data, "action_accept", "driver_accept")
                        : value(data, "action_reject", "driver_reject");

                int notificationId = intent.getIntExtra("notification_id", 0);

                NotificationManagerCompat.from(this).cancel(notificationId);

                processNotificationAction(action, data, notificationId);
            }
        }

        return Service.START_NOT_STICKY;
    }

    private void showOrderNotification(Map<String, String> data) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        String title = value(data, "title", "Order Baru Transiva");
        String body = value(data, "body", value(data, "message", "Order baru masuk"));
        String orderDbId = value(data, "order_db_id", value(data, "id", "0"));
        String orderId = value(data, "order_id", orderDbId);
        String type = value(data, "type", "transiva_order");
        String hasAction = value(data, "has_action", "0");

        int notificationId = makeNotificationId(orderDbId, orderId);

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setAction(ACTION_OPEN);
        openIntent.putExtra("type", type);
        openIntent.putExtra("order_id", orderId);
        openIntent.putExtra("order_db_id", orderDbId);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this,
                notificationId + 10,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | pendingImmutableFlag()
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(Notification.DEFAULT_ALL)
                .setVibrate(new long[]{0, 500, 250, 500, 250, 900})
                .setAutoCancel(true)
                .setOngoing(false)
                .setContentIntent(openPendingIntent);

        if ("1".equals(hasAction)) {
            builder.addAction(
                    R.mipmap.ic_launcher,
                    "Tolak",
                    buildActionPendingIntent(ACTION_REJECT, data, notificationId, notificationId + 20)
            );

            builder.addAction(
                    R.mipmap.ic_launcher,
                    "Terima",
                    buildActionPendingIntent(ACTION_ACCEPT, data, notificationId, notificationId + 30)
            );
        }

        NotificationManagerCompat.from(this).notify(notificationId, builder.build());
    }

    private PendingIntent buildActionPendingIntent(
            String action,
            Map<String, String> data,
            int notificationId,
            int requestCode
    ) {
        Intent intent = new Intent(this, TransivaFirebaseMessagingService.class);
        intent.setAction(action);
        intent.putExtra("notification_id", notificationId);

        for (Map.Entry<String, String> entry : data.entrySet()) {
            intent.putExtra(entry.getKey(), entry.getValue());
        }

        return PendingIntent.getService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | pendingImmutableFlag()
        );
    }

    private void processNotificationAction(
            String action,
            Map<String, String> data,
            int notificationId
    ) {
        showProcessingNotification(notificationId, action);

        new Thread(() -> {
            String endpoint = value(
                    data,
                    "action_endpoint",
                    "https://transiva.my.id/server/notification_action.php"
            );

            boolean success = false;
            String message = "Aksi gagal diproses";

            try {
                JSONObject json = new JSONObject();
                json.put("action", action);
                json.put("order_db_id", value(data, "order_db_id", value(data, "id", "")));
                json.put("order_id", value(data, "order_id", ""));
                json.put("actor", value(data, "offered_driver", value(data, "actor", "")));
                json.put("offered_driver", value(data, "offered_driver", ""));
                json.put("username", value(data, "offered_driver", ""));
                json.put("driver_type", value(data, "driver_type", "bike"));
                json.put("action_token", value(data, "action_token", ""));

                String response = postJson(endpoint, json.toString());

                JSONObject result = new JSONObject(response);
                success = result.optBoolean("success", false);
                message = result.optString("message", success ? "Berhasil" : "Gagal");

            } catch (Exception e) {
                Log.e(TAG, "Gagal proses action", e);
                message = "Gagal koneksi ke server";
            }

            showResultNotification(notificationId, success, message, data);
            stopSelf();
        }).start();
    }

    private String postJson(String endpoint, String body) throws Exception {
        HttpURLConnection conn = null;

        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");

            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);

            OutputStream os = conn.getOutputStream();
            os.write(bytes);
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 300
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;

            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            br.close();
            return sb.toString();

        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void showProcessingNotification(int notificationId, String action) {
        if (!canPostNotification()) return;

        String text = "driver_accept".equals(action)
                ? "Memproses terima order..."
                : "Memproses tolak order...";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Transiva")
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setAutoCancel(false);

        NotificationManagerCompat.from(this).notify(notificationId, builder.build());
    }

    private void showResultNotification(
            int notificationId,
            boolean success,
            String message,
            Map<String, String> data
    ) {
        if (!canPostNotification()) return;

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setAction(ACTION_OPEN);
        openIntent.putExtra("type", value(data, "type", "transiva_order"));
        openIntent.putExtra("order_id", value(data, "order_id", value(data, "order_db_id", "")));
        openIntent.putExtra("order_db_id", value(data, "order_db_id", ""));
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this,
                notificationId + 40,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | pendingImmutableFlag()
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(success ? "Aksi Berhasil" : "Aksi Gagal")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openPendingIntent);

        NotificationManagerCompat.from(this).notify(notificationId, builder.build());
    }

    private boolean canPostNotification() {
        if (Build.VERSION.SDK_INT >= 33) {
            return ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED;
        }

        return true;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager == null) return;

        NotificationChannel old = manager.getNotificationChannel(CHANNEL_ID);
        if (old != null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );

        channel.setDescription("Notifikasi order dan tombol aksi Transiva");
        channel.enableVibration(true);
        channel.enableLights(true);
        channel.setLightColor(Color.WHITE);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        manager.createNotificationChannel(channel);
    }

    private int makeNotificationId(String orderDbId, String orderId) {
        String raw = orderDbId == null || orderDbId.trim().isEmpty() || "0".equals(orderDbId)
                ? orderId
                : orderDbId;

        try {
            return 700000 + Math.abs(Integer.parseInt(raw));
        } catch (Exception e) {
            return 700000 + Math.abs(String.valueOf(raw).hashCode() % 100000);
        }
    }

    private int pendingImmutableFlag() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.FLAG_IMMUTABLE;
        }
        return 0;
    }

    private String value(Map<String, String> data, String key, String fallback) {
        if (data == null || key == null) return fallback;

        String v = data.get(key);

        if (v == null || v.trim().isEmpty() || "null".equalsIgnoreCase(v.trim())) {
            return fallback;
        }

        return v;
    }
}
