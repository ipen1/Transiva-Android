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
    public static final String PREF_NATIVE_SESSION = "transiva_native_session";
    public static final String PREF_FCM_TOKEN = "fcm_token";

    public static final String ORDER_CHANNEL_ID = "transiva_order_channel";
    public static final String ORDER_CHANNEL_NAME = "Order Transiva";

    public static final String WALLET_CHANNEL_ID = "transiva_wallet_channel";
    public static final String WALLET_CHANNEL_NAME = "Saldo & Withdraw Transiva";

    public static final int NOTIF_ID_ORDER = 1001;
    public static final int NOTIF_ID_WALLET_BASE = 7000;

    private static final String DEFAULT_ACTION_ENDPOINT =
            "https://transiva.my.id/server/notification_action.php";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        saveTokenLocal(token);
    }

    private void saveTokenLocal(String token) {
        String safeToken = token == null ? "" : token.trim();
        try {
            getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(PREF_FCM_TOKEN, safeToken)
                    .putString("transiva_fcm_token", safeToken)
                    .apply();
        } catch (Exception ignored) {}

        try {
            getSharedPreferences(PREF_NATIVE_SESSION, MODE_PRIVATE)
                    .edit()
                    .putString(PREF_FCM_TOKEN, safeToken)
                    .putString("transiva_fcm_token", safeToken)
                    .apply();
        } catch (Exception ignored) {}
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);
        wakeDevice();

        Map<String, String> data = message.getData();

        String title = getValue(data, "title", "Transiva");
        String body = getValue(data, "body", "Pesan baru masuk");
        String msg = getValue(data, "message", "");
        if (!msg.isEmpty()) body = msg;

        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null && title.equals("Transiva")) {
                title = message.getNotification().getTitle();
            }
            if (message.getNotification().getBody() != null && body.equals("Pesan baru masuk")) {
                body = message.getNotification().getBody();
            }
        }

        String hasAction = getValue(data, "has_action", "");
        String type = getValue(data, "type", getValue(data, "event", ""));

        if ("1".equals(hasAction) || isOrderType(type, data)) {
            showOrderNotification(title, body, data);
        } else {
            showWalletOrInfoNotification(title, body, data);
        }
    }

    private void showOrderNotification(String title, String body, Map<String, String> data) {
        createOrderChannel();

        String screen = getValue(data, "screen", "driver_order");
        String orderDbId = firstNotEmpty(
                getValue(data, "order_db_id", ""),
                getValue(data, "order_id", ""),
                getValue(data, "id", "")
        );

        Intent contentOpenIntent = buildOpenMainActivityIntent(
                screen, orderDbId, "", "", "", "", "", "fcm_content_click", data
        );

        int contentReq = makeRequestCode(1100, orderDbId, screen);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                contentReq,
                contentOpenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ORDER_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(isEmpty(title) ? "Transiva" : title)
                .setContentText(isEmpty(body) ? "Pesan baru masuk" : body)
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
            String actor = firstNotEmpty(
                    getValue(data, "offered_driver", ""),
                    getValue(data, "actor", ""),
                    getValue(data, "username", ""),
                    getValue(data, "driver", "")
            );
            String driverType = firstNotEmpty(getValue(data, "driver_type", ""), "bike");

            if (!orderDbId.isEmpty() && !token.isEmpty()) {
                builder.addAction(
                        R.mipmap.ic_launcher,
                        "Terima",
                        createActionIntent(
                                makeRequestCode(2001, orderDbId, acceptAction),
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
                                makeRequestCode(2002, orderDbId, rejectAction),
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

        notifySafe(NOTIF_ID_ORDER, builder.build());
    }

    private void showWalletOrInfoNotification(String title, String body, Map<String, String> data) {
        createWalletChannel();

        String type = getValue(data, "type", getValue(data, "event", "wallet"));
        Intent openIntent = buildOpenWalletIntent(data);

        int notifId = NOTIF_ID_WALLET_BASE + Math.abs((type + System.currentTimeMillis()).hashCode() % 999);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                makeRequestCode(7100, String.valueOf(notifId), type),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, WALLET_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(isEmpty(title) ? "Transiva" : title)
                .setContentText(isEmpty(body) ? "Informasi saldo Transiva" : body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body == null ? "" : body))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setOngoing(false)
                .setOnlyAlertOnce(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(new long[]{0, 250, 150, 350})
                .setLights(0xffffffff, 700, 700);

        notifySafe(notifId, builder.build());
    }

    private Intent buildOpenWalletIntent(Map<String, String> data) {
        String role = firstNotEmpty(
                getValue(data, "role", ""),
                getValue(data, "target_role", ""),
                getValue(data, "user_role", ""),
                getValue(data, "receiver_role", ""),
                getValue(data, "account_role", "")
        ).toLowerCase();

        String screen = firstNotEmpty(
                getValue(data, "screen", ""),
                getValue(data, "open_screen", ""),
                getValue(data, "target_screen", "")
        ).toLowerCase();

        if (role.isEmpty()) {
            role = readLocalRole();
        }

        boolean customer = role.equals("customer") || role.equals("costumer") || screen.contains("customer") || screen.contains("costumer");
        boolean driver = role.equals("driver") || screen.contains("driver");

        String className;
        if (customer && !driver) {
            className = getPackageName() + ".CustomerDashboardActivity";
        } else if (driver) {
            className = getPackageName() + ".DriverDashboardActivity";
        } else {
            className = getPackageName() + ".MainActivity";
        }

        Intent intent = new Intent();
        intent.setClassName(getPackageName(), className);
        intent.setAction("OPEN_TRANSIVA_WALLET");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        if (data != null) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                if (entry != null && entry.getKey() != null && entry.getValue() != null) {
                    intent.putExtra(entry.getKey(), entry.getValue());
                }
            }
        }

        intent.putExtra("source", "fcm_wallet_click");
        intent.putExtra("open_screen", customer ? "customer_dashboard" : (driver ? "driver_dashboard" : "home"));
        intent.putExtra("screen", customer ? "customer_dashboard" : (driver ? "driver_dashboard" : "home"));
        return intent;
    }

    private String readLocalRole() {
        try {
            String role = getSharedPreferences(PREF_NAME, MODE_PRIVATE).getString("role", "");
            if (!isEmpty(role)) return role.trim();
            role = getSharedPreferences(PREF_NAME, MODE_PRIVATE).getString("player_role", "");
            if (!isEmpty(role)) return role.trim();
        } catch (Exception ignored) {}

        try {
            String role = getSharedPreferences(PREF_NATIVE_SESSION, MODE_PRIVATE).getString("role", "");
            if (!isEmpty(role)) return role.trim();
            role = getSharedPreferences(PREF_NATIVE_SESSION, MODE_PRIVATE).getString("player_role", "");
            if (!isEmpty(role)) return role.trim();
        } catch (Exception ignored) {}

        return "";
    }

    private boolean isOrderType(String type, Map<String, String> data) {
        String t = safe(type).toLowerCase();
        String screen = getValue(data, "screen", "").toLowerCase();
        return t.contains("order") || screen.contains("driver_order") || "1".equals(getValue(data, "has_action", ""));
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

        return PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent createDeleteIntent(String orderDbId) {
        Intent intent = new Intent(this, TransivaNotificationActionReceiver.class);
        intent.setAction("com.transiva.app.NOTIFICATION_DISMISSED");
        intent.putExtra("notification_id", NOTIF_ID_ORDER);
        intent.putExtra("order_db_id", safe(orderDbId));
        return PendingIntent.getBroadcast(
                this,
                makeRequestCode(3001, orderDbId, "delete"),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private Intent buildOpenMainActivityIntent(
            String openScreen,
            String orderDbId,
            String action,
            String endpoint,
            String token,
            String actor,
            String driverType,
            String source,
            Map<String, String> originalData
    ) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction("OPEN_TRANSIVA");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        if (originalData != null) {
            for (Map.Entry<String, String> entry : originalData.entrySet()) {
                if (entry != null && entry.getKey() != null && entry.getValue() != null) {
                    intent.putExtra(entry.getKey(), entry.getValue());
                }
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

    private void notifySafe(int id, Notification notification) {
        notification.flags |= Notification.FLAG_SHOW_LIGHTS;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Izin POST_NOTIFICATIONS belum diberikan");
                return;
            }
        }

        NotificationManagerCompat.from(this).notify(id, notification);
    }

    private void createOrderChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (manager.getNotificationChannel(ORDER_CHANNEL_ID) != null) return;

        NotificationChannel channel = new NotificationChannel(
                ORDER_CHANNEL_ID,
                ORDER_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Notifikasi order Transiva");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 500, 250, 500, 250, 900});
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.enableLights(true);
        channel.setLightColor(0xffffffff);
        Uri soundUri = Settings.System.DEFAULT_NOTIFICATION_URI;
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        channel.setSound(soundUri, audioAttributes);
        manager.createNotificationChannel(channel);
    }

    private void createWalletChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (manager.getNotificationChannel(WALLET_CHANNEL_ID) != null) return;

        NotificationChannel channel = new NotificationChannel(
                WALLET_CHANNEL_ID,
                WALLET_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Notifikasi saldo, deposit, dan withdraw Transiva");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 250, 150, 350});
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.enableLights(true);
        channel.setLightColor(0xffffffff);
        Uri soundUri = Settings.System.DEFAULT_NOTIFICATION_URI;
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        channel.setSound(soundUri, audioAttributes);
        manager.createNotificationChannel(channel);
    }

    private void wakeDevice() {
        PowerManager.WakeLock wakeLock = null;
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager == null) return;
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Transiva:FCMWakeLock"
            );
            wakeLock.acquire(15000);
        } catch (Exception e) {
            Log.e(TAG, "WakeLock gagal: " + e.getMessage());
        }
    }

    private String getValue(Map<String, String> data, String key, String def) {
        if (data == null || key == null) return def;
        String value = data.get(key);
        if (value == null || value.trim().isEmpty()) return def;
        return value.trim();
    }

    private int makeRequestCode(int prefix, String orderDbId, String extra) {
        String raw = prefix + "_" + safe(orderDbId) + "_" + safe(extra);
        int hash = Math.abs(raw.hashCode());
        return prefix * 100000 + (hash % 99999);
    }

    private String firstNotEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
