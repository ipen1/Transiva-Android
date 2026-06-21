package com.transiva.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class TransivaFirebaseService extends FirebaseMessagingService {

    private static final String TAG = "TRANSIVA_FCM";

    public static final String PREF_NAME = "transiva";
    public static final String PREF_FCM_TOKEN = "fcm_token";

    public static final String CHANNEL_ID = "transiva_order_channel";
    public static final String CHANNEL_NAME = "Order Transiva";

    private static final int NOTIF_ID_ORDER = 1001;

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);

        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .edit()
                .putString(PREF_FCM_TOKEN, token == null ? "" : token)
                .apply();
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);

        wakeDevice();

        Map<String, String> data = message.getData();

        String title = getValue(data, "title", "Transiva");
        String body = getValue(data, "body", "Pesan baru masuk");

        String msg = getValue(data, "message", "");
        if (!msg.isEmpty()) {
            body = msg;
        }

        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null && title.equals("Transiva")) {
                title = message.getNotification().getTitle();
            }

            if (message.getNotification().getBody() != null && body.equals("Pesan baru masuk")) {
                body = message.getNotification().getBody();
            }
        }

        showHighPriorityNotification(title, body, data);
    }

    private String getValue(Map<String, String> data, String key, String def) {
        if (data == null || key == null) return def;

        String value = data.get(key);
        if (value == null || value.trim().isEmpty()) return def;

        return value.trim();
    }

    private void showHighPriorityNotification(String title, String body, Map<String, String> data) {

        createNotificationChannel();

        String screen = getValue(data, "screen", "driver_order");
        String orderDbId = firstNotEmpty(
                getValue(data, "order_db_id", ""),
                getValue(data, "order_id", ""),
                getValue(data, "id", "")
        );

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setAction("OPEN_TRANSIVA");
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        if (data != null) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                if (entry != null && entry.getKey() != null && entry.getValue() != null) {
                    openIntent.putExtra(entry.getKey(), entry.getValue());
                }
            }
        }

        openIntent.putExtra("open_screen", screen);
        openIntent.putExtra("order_db_id", orderDbId);
        openIntent.putExtra("order_id", orderDbId);
        openIntent.putExtra("id", orderDbId);
        openIntent.putExtra("source", "fcm_content_click");

        int contentReq = makeRequestCode(1100, orderDbId, screen);

        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                contentReq,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title == null || title.trim().isEmpty() ? "Transiva" : title)
                        .setContentText(body == null || body.trim().isEmpty() ? "Pesan baru masuk" : body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body == null ? "" : body))
                        .setContentIntent(contentIntent)
                        .setAutoCancel(true)
                        .setOngoing(false)
                        .setOnlyAlertOnce(false)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setVibrate(new long[]{0, 500, 250, 500, 250, 900})
                        .setLights(0xffffffff, 1000, 1000)
                        .setFullScreenIntent(contentIntent, true);

        if (data != null && "1".equals(getValue(data, "has_action", ""))) {

            String endpoint = getValue(data, "action_endpoint", "");
            String token = getValue(data, "action_token", "");
            String acceptAction = getValue(data, "action_accept", "driver_accept");
            String rejectAction = getValue(data, "action_reject", "driver_reject");
            String actor = firstNotEmpty(
                    getValue(data, "offered_driver", ""),
                    getValue(data, "actor", ""),
                    getValue(data, "username", ""),
                    getValue(data, "driver", "")
            );
            String driverType = getValue(data, "driver_type", "bike");

            if (!orderDbId.isEmpty() && !token.isEmpty()) {

                int acceptReq = makeRequestCode(2001, orderDbId, acceptAction);
                int rejectReq = makeRequestCode(2002, orderDbId, rejectAction);

                builder.addAction(
                        R.mipmap.ic_launcher,
                        "Terima",
                        createActionIntent(
                                acceptReq,
                                orderDbId,
                                acceptAction,
                                endpoint,
                                token,
                                actor,
                                driverType
                        )
                );

                builder.addAction(
                        R.mipmap.ic_launcher,
                        "Tolak",
                        createActionIntent(
                                rejectReq,
                                orderDbId,
                                rejectAction,
                                endpoint,
                                token,
                                actor,
                                driverType
                        )
                );

            } else {
                Log.e(TAG, "Action tidak dibuat. orderDbId/action_token kosong. orderDbId=" + orderDbId);
            }
        }

        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_SHOW_LIGHTS;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Izin POST_NOTIFICATIONS belum diberikan");
                return;
            }
        }

        NotificationManagerCompat.from(this).notify(NOTIF_ID_ORDER, notification);
    }

    private PendingIntent createActionIntent(
            int requestCode,
            String orderDbId,
            String action,
            String endpoint,
            String token,
            String actor,
            String driverType
    ) {

        Intent intent = new Intent(this, TransivaNotificationActionReceiver.class);
        intent.setAction("TRANSIVA_NOTIFICATION_ACTION_" + action + "_" + orderDbId + "_" + System.currentTimeMillis());

        intent.putExtra("from_notification_action", "1");
        intent.putExtra("open_screen", "driver_accept".equals(action) ? "driver_trip" : "driver_order");

        intent.putExtra("order_db_id", safe(orderDbId));
        intent.putExtra("order_id", safe(orderDbId));
        intent.putExtra("id", safe(orderDbId));

        intent.putExtra("action", safe(action));
        intent.putExtra("action_endpoint", safe(endpoint));
        intent.putExtra("action_token", safe(token));

        intent.putExtra("actor", safe(actor));
        intent.putExtra("username", safe(actor));
        intent.putExtra("offered_driver", safe(actor));
        intent.putExtra("driver_type", safe(driverType).isEmpty() ? "bike" : safe(driverType));

        intent.putExtra("notification_id", NOTIF_ID_ORDER);
        intent.putExtra("source", "fcm_action_button");

        return PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager == null) {
            return;
        }

        NotificationChannel oldChannel = manager.getNotificationChannel(CHANNEL_ID);
        if (oldChannel != null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );

        channel.setDescription("Notifikasi order dan pesan penting Transiva");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 500, 250, 500, 250, 900});
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.enableLights(true);
        channel.setLightColor(0xffffffff);

        Uri soundUri = Settings.System.DEFAULT_NOTIFICATION_URI;

        AudioAttributes audioAttributes =
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();

        channel.setSound(soundUri, audioAttributes);
        manager.createNotificationChannel(channel);
    }

    private void wakeDevice() {
        PowerManager.WakeLock wakeLock = null;

        try {
            PowerManager powerManager =
                    (PowerManager) getSystemService(Context.POWER_SERVICE);

            if (powerManager == null) {
                return;
            }

            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Transiva:FCMWakeLock"
            );

            wakeLock.acquire(15000);

        } catch (Exception e) {
            Log.e(TAG, "WakeLock gagal: " + e.getMessage());
        }
    }

    private int makeRequestCode(int prefix, String orderDbId, String extra) {
        String raw = prefix + "_" + safe(orderDbId) + "_" + safe(extra);
        int hash = Math.abs(raw.hashCode());
        return prefix * 100000 + (hash % 99999);
    }

    private String firstNotEmpty(String... values) {
        if (values == null) return "";

        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }

        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
