package com.transiva.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

import java.util.Locale;
import java.util.Map;

public class TransivaFirebaseService extends FirebaseMessagingService {

    private static final String TAG = "TRANSIVA_FCM";

    public static final String PREF_NAME = "transiva";
    public static final String PREF_NATIVE_SESSION = "transiva_native_session";
    public static final String PREF_FCM_TOKEN = "fcm_token";

    public static final String CHANNEL_ORDER_ID = "transiva_order_channel";
    public static final String CHANNEL_FINANCE_ID = "transiva_finance_channel";
    public static final String CHANNEL_CHAT_ID = "transiva_chat_channel";

    public static final int NOTIF_ID_ORDER = 1001;
    public static final int NOTIF_ID_FINANCE = 2001;
    public static final int NOTIF_ID_CHAT = 3001;

    private static final String DEFAULT_ACTION_ENDPOINT =
            "https://transiva.my.id/server/notification_action.php";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        saveTokenEverywhere(token);
    }

    private void saveTokenEverywhere(String token) {
        String clean = safe(token);
        try {
            getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(PREF_FCM_TOKEN, clean)
                    .putString("transiva_fcm_token", clean)
                    .apply();
        } catch (Exception ignored) {}

        try {
            getSharedPreferences(PREF_NATIVE_SESSION, MODE_PRIVATE)
                    .edit()
                    .putString(PREF_FCM_TOKEN, clean)
                    .putString("transiva_fcm_token", clean)
                    .apply();
        } catch (Exception ignored) {}
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);
        wakeDevice();

        Map<String, String> data = message.getData();

        String type = lower(firstNotEmpty(
                getValue(data, "type", ""),
                getValue(data, "event", ""),
                getValue(data, "screen", "")
        ));

        String title = getValue(data, "title", "Transiva");
        String body = firstNotEmpty(
                getValue(data, "body", ""),
                getValue(data, "message", ""),
                "Pesan baru masuk"
        );

        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null && title.equals("Transiva")) {
                title = message.getNotification().getTitle();
            }
            if (message.getNotification().getBody() != null && body.equals("Pesan baru masuk")) {
                body = message.getNotification().getBody();
            }
        }

        if (isFinanceType(type)) {
            showFinanceNotification(title, body, type, data);
        } else if (isChatType(type)) {
            showSimpleNotification(CHANNEL_CHAT_ID, "Chat Transiva", NOTIF_ID_CHAT, title, body, data, buildOpenIntent(data, MainActivity.class));
        } else {
            showOrderNotification(title, body, data);
        }
    }

    private boolean isFinanceType(String type) {
        return type.contains("deposit") || type.contains("withdraw") || type.contains("wd") || type.contains("saldo");
    }

    private boolean isChatType(String type) {
        return type.contains("chat") || type.contains("message");
    }

    private void showFinanceNotification(String title, String body, String type, Map<String, String> data) {
        createFinanceChannel();

        if (isEmpty(title) || "Transiva".equals(title)) {
            if (type.contains("deposit") && (type.contains("reject") || type.contains("failed") || type.contains("gagal"))) {
                title = "Deposit Ditolak";
            } else if (type.contains("deposit")) {
                title = "Deposit Berhasil";
            } else if (type.contains("withdraw") || type.contains("wd")) {
                if (type.contains("reject") || type.contains("failed") || type.contains("gagal")) {
                    title = "Withdraw Ditolak";
                } else {
                    title = "Withdraw Berhasil";
                }
            } else {
                title = "Info Saldo Transiva";
            }
        }

        Intent openIntent;
        String role = lower(firstNotEmpty(getValue(data, "role", ""), getSavedRole()));
        if (role.equals("driver") || type.contains("driver")) {
            openIntent = buildOpenIntent(data, DriverDashboardActivity.class);
        } else {
            openIntent = buildOpenIntent(data, CustomerDashboardActivity.class);
        }

        int req = makeRequestCode(NOTIF_ID_FINANCE, getValue(data, "id", "finance"), type);
        PendingIntent pi = PendingIntent.getActivity(this, req, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_FINANCE_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setOngoing(false)
                .setOnlyAlertOnce(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(new long[]{0, 350, 150, 350})
                .setLights(0xff0B7CFF, 800, 800);

        notifySafe(req, builder.build());
    }

    private void showSimpleNotification(String channelId, String channelName, int notifBase, String title, String body, Map<String, String> data, Intent openIntent) {
        createBasicChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
        int req = makeRequestCode(notifBase, getValue(data, "id", "simple"), title);
        PendingIntent pi = PendingIntent.getActivity(this, req, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(isEmpty(title) ? "Transiva" : title)
                .setContentText(isEmpty(body) ? "Pesan baru masuk" : body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body == null ? "" : body))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL);
        notifySafe(req, builder.build());
    }

    private void showOrderNotification(String title, String body, Map<String, String> data) {
        createOrderChannel();

        String screen = getValue(data, "screen", "driver_order");
        String orderDbId = firstNotEmpty(getValue(data, "order_db_id", ""), getValue(data, "order_id", ""), getValue(data, "id", ""));

        Intent contentOpenIntent = buildOpenMainActivityIntent(screen, orderDbId, "", "", "", "", "", "fcm_content_click", data);
        int contentReq = makeRequestCode(1100, orderDbId, screen);
        PendingIntent contentIntent = PendingIntent.getActivity(this, contentReq, contentOpenIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ORDER_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(isEmpty(title) ? "Transiva" : title)
                .setContentText(isEmpty(body) ? "Order baru masuk" : body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body == null ? "" : body))
                .setContentIntent(contentIntent)
                .setDeleteIntent(createDeleteIntent(orderDbId))
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
            String endpoint = firstNotEmpty(getValue(data, "action_endpoint", ""), DEFAULT_ACTION_ENDPOINT);
            String token = getValue(data, "action_token", "");
            String acceptAction = firstNotEmpty(getValue(data, "action_accept", ""), "driver_accept");
            String rejectAction = firstNotEmpty(getValue(data, "action_reject", ""), "driver_reject");
            String actor = firstNotEmpty(getValue(data, "offered_driver", ""), getValue(data, "actor", ""), getValue(data, "username", ""), getValue(data, "driver", ""));
            String driverType = firstNotEmpty(getValue(data, "driver_type", ""), "bike");

            if (!orderDbId.isEmpty() && !token.isEmpty()) {
                builder.addAction(R.mipmap.ic_launcher, "Terima", createActionIntent(makeRequestCode(2001, orderDbId, acceptAction), orderDbId, acceptAction, endpoint, token, actor, driverType));
                builder.addAction(R.mipmap.ic_launcher, "Tolak", createActionIntent(makeRequestCode(2002, orderDbId, rejectAction), orderDbId, rejectAction, endpoint, token, actor, driverType));
            }
        }

        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_SHOW_LIGHTS;
        notifySafe(NOTIF_ID_ORDER, notification);
    }

    private Intent buildOpenIntent(Map<String, String> data, Class<?> target) {
        Intent intent = new Intent(this, target);
        intent.setAction("OPEN_TRANSIVA_FCM");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (data != null) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                if (entry != null && entry.getKey() != null && entry.getValue() != null) intent.putExtra(entry.getKey(), entry.getValue());
            }
        }
        return intent;
    }

    private PendingIntent createActionIntent(int requestCode, String orderDbId, String action, String endpoint, String token, String actor, String driverType) {
        Intent intent = new Intent(this, TransivaNotificationActionReceiver.class);
        intent.setAction("com.transiva.app.NOTIFICATION_ACTION_" + safe(action));
        intent.putExtra("from_notification_action", "1");
        intent.putExtra("notification_id", NOTIF_ID_ORDER);
        intent.putExtra("order_db_id", safe(orderDbId));
        intent.putExtra("order_id", safe(orderDbId));
        intent.putExtra("id", safe(orderDbId));
        intent.putExtra("action", safe(action));
        intent.putExtra("action_endpoint", firstNotEmpty(endpoint, DEFAULT_ACTION_ENDPOINT));
        intent.putExtra("action_token", safe(token));
        intent.putExtra("actor", safe(actor));
        intent.putExtra("username", safe(actor));
        intent.putExtra("offered_driver", safe(actor));
        intent.putExtra("driver", safe(actor));
        intent.putExtra("driver_type", firstNotEmpty(driverType, "bike"));
        intent.putExtra("time", String.valueOf(System.currentTimeMillis()));
        return PendingIntent.getBroadcast(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent createDeleteIntent(String orderDbId) {
        Intent intent = new Intent(this, TransivaNotificationActionReceiver.class);
        intent.setAction("com.transiva.app.NOTIFICATION_DISMISSED");
        intent.putExtra("notification_id", NOTIF_ID_ORDER);
        intent.putExtra("order_db_id", safe(orderDbId));
        return PendingIntent.getBroadcast(this, makeRequestCode(3001, orderDbId, "delete"), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Intent buildOpenMainActivityIntent(String openScreen, String orderDbId, String action, String endpoint, String token, String actor, String driverType, String source, Map<String, String> originalData) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction("OPEN_TRANSIVA");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (originalData != null) {
            for (Map.Entry<String, String> entry : originalData.entrySet()) {
                if (entry != null && entry.getKey() != null && entry.getValue() != null) intent.putExtra(entry.getKey(), entry.getValue());
            }
        }
        String safeOrderId = safe(orderDbId);
        String safeActor = safe(actor);
        String safeDriverType = firstNotEmpty(driverType, "bike");
        intent.putExtra("open_screen", safe(openScreen));
        intent.putExtra("screen", safe(openScreen));
        intent.putExtra("order_db_id", safeOrderId);
        intent.putExtra("order_id", safeOrderId);
        intent.putExtra("id", safeOrderId);
        intent.putExtra("action", safe(action));
        intent.putExtra("action_endpoint", firstNotEmpty(endpoint, DEFAULT_ACTION_ENDPOINT));
        intent.putExtra("action_token", safe(token));
        intent.putExtra("actor", safeActor);
        intent.putExtra("username", safeActor);
        intent.putExtra("offered_driver", safeActor);
        intent.putExtra("driver", safeActor);
        intent.putExtra("driver_type", safeDriverType);
        intent.putExtra("source", safe(source));
        return intent;
    }

    private void createOrderChannel() { createRichChannel(CHANNEL_ORDER_ID, "Order Transiva", "Notifikasi order masuk Transiva", NotificationManager.IMPORTANCE_HIGH, true); }
    private void createFinanceChannel() { createRichChannel(CHANNEL_FINANCE_ID, "Saldo & Withdraw Transiva", "Notifikasi deposit, saldo, dan withdraw", NotificationManager.IMPORTANCE_HIGH, false); }
    private void createBasicChannel(String id, String name, int importance) { createRichChannel(id, name, name, importance, false); }

    private void createRichChannel(String id, String name, String description, int importance, boolean alarmStyle) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(id) != null) return;
        NotificationChannel channel = new NotificationChannel(id, name, importance);
        channel.setDescription(description);
        channel.enableVibration(true);
        channel.setVibrationPattern(alarmStyle ? new long[]{0, 500, 250, 500, 250, 900} : new long[]{0, 350, 150, 350});
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.enableLights(true);
        channel.setLightColor(0xffffffff);
        Uri soundUri = Settings.System.DEFAULT_NOTIFICATION_URI;
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(alarmStyle ? AudioAttributes.USAGE_NOTIFICATION_RINGTONE : AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        channel.setSound(soundUri, audioAttributes);
        manager.createNotificationChannel(channel);
    }

    private void notifySafe(int notificationId, Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Izin POST_NOTIFICATIONS belum diberikan");
                return;
            }
        }
        NotificationManagerCompat.from(this).notify(notificationId, notification);
    }

    private void wakeDevice() {
        PowerManager.WakeLock wakeLock = null;
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager == null) return;
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Transiva:FCMWakeLock");
            wakeLock.acquire(15000);
        } catch (Exception e) { Log.e(TAG, "WakeLock gagal: " + e.getMessage()); }
    }

    private String getSavedRole() {
        try {
            SharedPreferences sp = getSharedPreferences(PREF_NATIVE_SESSION, MODE_PRIVATE);
            String role = firstNotEmpty(sp.getString("role", ""), sp.getString("player_role", ""));
            if (!isEmpty(role)) return role;
        } catch (Exception ignored) {}
        try {
            SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
            return firstNotEmpty(sp.getString("role", ""), sp.getString("player_role", ""));
        } catch (Exception ignored) { return ""; }
    }

    private String getValue(Map<String, String> data, String key, String def) { if (data == null || key == null) return def; String value = data.get(key); return isEmpty(value) ? def : value.trim(); }
    private int makeRequestCode(int prefix, String orderDbId, String extra) { String raw = prefix + "_" + safe(orderDbId) + "_" + safe(extra); int hash = Math.abs(raw.hashCode()); return prefix * 100000 + (hash % 99999); }
    private String firstNotEmpty(String... values) { if (values == null) return ""; for (String value : values) if (!isEmpty(value)) return value.trim(); return ""; }
    private boolean isEmpty(String value) { return value == null || value.trim().isEmpty(); }
    private String safe(String value) { return value == null ? "" : value.trim(); }
    private String lower(String value) { return safe(value).toLowerCase(Locale.US); }
}
