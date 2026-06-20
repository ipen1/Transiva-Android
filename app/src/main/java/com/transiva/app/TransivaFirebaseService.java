package com.transiva.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class TransivaFirebaseService extends FirebaseMessagingService {

    private static final String TAG = "TRANSIVA_FCM";
    private static final String CHANNEL_ID = "transiva_order_channel";
    private static final String CHANNEL_NAME = "Order Transiva";
    private static final int NOTIF_ID_ORDER = 1001;

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);

        Log.d(TAG, "FCM Token baru: " + token);

        getSharedPreferences("TRANSIVA_PREF", MODE_PRIVATE)
                .edit()
                .putString("fcm_token", token)
                .apply();

        /*
         * Token ini sudah disimpan.
         * MainActivity bisa ambil lagi dan kirim ke web/server.
         */
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);

        Log.d(TAG, "FCM masuk");

        wakeDevice();

        String title = "Transiva";
        String body = "Pesan baru masuk";

        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null) {
                title = message.getNotification().getTitle();
            }

            if (message.getNotification().getBody() != null) {
                body = message.getNotification().getBody();
            }
        }

        Map<String, String> data = message.getData();

        if (data != null) {
            if (data.containsKey("title")) {
                title = data.get("title");
            }

            if (data.containsKey("body")) {
                body = data.get("body");
            }

            if (data.containsKey("message")) {
                body = data.get("message");
            }
        }

        showHighPriorityNotification(title, body, data);
    }

    private void showHighPriorityNotification(
            String title,
            String body,
            Map<String, String> data
    ) {

        createNotificationChannel();

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_NEW_TASK
        );

        if (data != null) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                intent.putExtra(entry.getKey(), entry.getValue());
            }
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setAutoCancel(true)
                        .setOngoing(false)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_CALL)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setVibrate(new long[]{0, 500, 250, 500, 250, 800})
                        .setContentIntent(pendingIntent)
                        .setFullScreenIntent(pendingIntent, true);

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager != null) {
            manager.notify(NOTIF_ID_ORDER, builder.build());
        }
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

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );

        channel.setDescription("Notifikasi order dan pesan penting Transiva");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 500, 250, 500, 250, 800});
        channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);

        Uri soundUri = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;

        AudioAttributes audioAttributes =
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();

        channel.setSound(soundUri, audioAttributes);

        manager.createNotificationChannel(channel);
    }

    private void wakeDevice() {
        try {
            PowerManager powerManager =
                    (PowerManager) getSystemService(Context.POWER_SERVICE);

            if (powerManager == null) {
                return;
            }

            PowerManager.WakeLock wakeLock =
                    powerManager.newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK,
                            "Transiva:FCMWakeLock"
                    );

            wakeLock.acquire(10000);

        } catch (Exception e) {
            Log.e(TAG, "WakeLock gagal: " + e.getMessage());
        }
    }
}
