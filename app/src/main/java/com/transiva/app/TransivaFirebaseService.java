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

/**
 * FULL FIX TransivaFirebaseService
 *
 * Fungsi:
 * 1. Menyimpan token FCM terbaru ke SharedPreferences.
 * 2. Menampilkan notifikasi order masuk dengan tombol Terima / Tolak.
 * 3. Tombol Terima / Tolak memakai BroadcastReceiver agar notif otomatis tertutup.
 * 4. Mendukung notifikasi realtime background untuk:
 *    - saldo deposit masuk / ditolak
 *    - withdraw berhasil / ditolak / pending
 *    - update saldo driver
 *    - nota / riwayat transaksi
 * 5. Klik notifikasi finance membuka halaman native yang sesuai.
 *
 * Pastikan manifest punya:
 * <service android:name=".TransivaFirebaseService" android:exported="false">
 *   <intent-filter>
 *     <action android:name="com.google.firebase.MESSAGING_EVENT" />
 *   </intent-filter>
 * </service>
 *
 * Pastikan receiver action notif sudah ada:
 * <receiver android:name=".TransivaNotificationActionReceiver" android:exported="false" />
 */
public class TransivaFirebaseService extends FirebaseMessagingService {

    private static final String TAG = "TRANSIVA_FCM";

    public static final String PREF_NAME = "transiva";
    public static final String PREF_FCM_TOKEN = "fcm_token";

    public static final String CHANNEL_ORDER_ID = "transiva_order_channel_v2";
    public static final String CHANNEL_FINANCE_ID = "transiva_finance_channel_v1";
    public static final String CHANNEL_GENERAL_ID = "transiva_general_channel_v1";

    public static final String CHANNEL_ORDER_NAME = "Order Transiva";
    public static final String CHANNEL_FINANCE_NAME = "Saldo & Withdraw";
    public static final String CHANNEL_GENERAL_NAME = "Transiva";

    public static final int NOTIF_ID_ORDER = 1001;
    public static final int NOTIF_ID_FINANCE = 2001;
    public static final int NOTIF_ID_GENERAL = 3001;

    private static final String DEFAULT_ACTION_ENDPOINT =
            "https://transiva.my.id/server/notification_action.php";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);

        try {
            getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(PREF_FCM_TOKEN, token == null ? "" : token)
                    .apply();

            Log.d(TAG, "FCM token disimpan lokal");
        } catch (Exception e) {
            Log.e(TAG, "Gagal simpan token: " + e.getMessage());
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);
        wakeDevice();

        Map<String, String> data = message.getData();

        String title = getValue(data, "title", "Transiva");
        String body = firstNotEmpty(
                getValue(data, "message", ""),
                getValue(data, "body", ""),
                "Pesan baru masuk"
        );

        if (message.getNotification() != null) {
            if (isEmpty(title) || "Transiva".equals(title)) {
                title = firstNotEmpty(message.getNotification().getTitle(), title, "Transiva");
            }

            if (isEmpty(body) || "Pesan baru masuk".equals(body)) {
                body = firstNotEmpty(message.getNotification().getBody(), body, "Pesan baru masuk");
            }
        }

        String event = detectEvent(data, title, body);

        if ("order".equals(event)) {
            showOrderNotification(title, body, data);
        } else if ("finance".equals(event)) {
            showFinanceNotification(title, body, data);
        } else {
            showGeneralNotification(title, body, data);
        }
    }

    private String detectEvent(Map<String, String> data, String title, String body) {
        String type = lower(firstNotEmpty(
                getValue(data, "type", ""),
                getValue(data, "event", ""),
                getValue(data, "screen", ""),
                getValue(data, "open_screen", ""),
                getValue(data, "status", "")
        ));

        String all = lower(type + " " + safe(title) + " " + safe(body));

        if ("1".equals(getValue(data, "has_action", ""))) return "order";
        if (all.contains("driver_order") || all.contains("order masuk") || all.contains("pesanan masuk")) return "order";

        if (all.contains("deposit")
                || all.contains("topup")
                || all.contains("top_up")
                || all.contains("saldo")
                || all.contains("withdraw")
                || all.contains("wd")
                || all.contains("penarikan")) {
            return "finance";
        }

        return "general";
    }

    private void showOrderNotification(String title, String body, Map<String, String> data) {
        createNotificationChannels();

        String screen = firstNotEmpty(getValue(data, "screen", ""), getValue(data, "open_screen", ""), "driver_order");
        String orderDbId = firstNotEmpty(
                getValue(data, "order_db_id", ""),
                getValue(data, "order_id", ""),
                getValue(data, "id", "")
        );

        Intent contentOpenIntent = buildOpenIntent(screen, orderDbId, "", data);
        int contentReq = makeRequestCode(1100, orderDbId, screen);

        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                contentReq,
                contentOpenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ORDER_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(firstNotEmpty(title, "Order Transiva"))
                .setContentText(firstNotEmpty(body, "Order baru masuk"))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(firstNotEmpty(body, "Order baru masuk")))
                .setContentIntent(contentIntent)
                .setDeleteIntent(createDeleteIntent(orderDbId, NOTIF_ID_ORDER))
                .setAutoCancel(true)
                .setOngoing(false)
                .setOnlyAlertOnce(false)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(new long[]{0, 500, 250, 500, 250, 900})
                .setLights(0xffffffff, 1000, 1000)
                .setFullScreenIntent(contentIntent, true);

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
                    createOrderActionIntent(
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
                    createOrderActionIntent(
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
            Log.e(TAG, "Action order tidak dibuat. orderDbId/action_token kosong. orderDbId=" + orderDbId);
        }

        notifyNow(NOTIF_ID_ORDER, builder.build());
    }

    private void showFinanceNotification(String title, String body, Map<String, String> data) {
        createNotificationChannels();

        String screen = normalizeFinanceScreen(data, title, body);
        String rawId = firstNotEmpty(
                getValue(data, "withdraw_id", ""),
                getValue(data, "deposit_id", ""),
                getValue(data, "transaction_id", ""),
                getValue(data, "id", ""),
                String.valueOf(System.currentTimeMillis())
        );

        Intent openIntent = buildOpenIntent(screen, rawId, "finance", data);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                makeRequestCode(4100, rawId, screen),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String finalTitle = firstNotEmpty(title, financeTitle(screen));
        String finalBody = firstNotEmpty(body, financeBody(screen));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_FINANCE_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(finalTitle)
                .setContentText(finalBody)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(finalBody))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setOngoing(false)
                .setOnlyAlertOnce(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(new long[]{0, 350, 150, 500})
                .setLights(0xff0B7CFF, 1000, 1000);

        notifyNow(makeRequestCode(NOTIF_ID_FINANCE, rawId, screen), builder.build());
    }

    private void showGeneralNotification(String title, String body, Map<String, String> data) {
        createNotificationChannels();

        String id = firstNotEmpty(getValue(data, "id", ""), String.valueOf(System.currentTimeMillis()));
        String screen = firstNotEmpty(getValue(data, "screen", ""), getValue(data, "open_screen", ""), "main");

        Intent openIntent = buildOpenIntent(screen, id, "general", data);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                makeRequestCode(5100, id, screen),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_GENERAL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(firstNotEmpty(title, "Transiva"))
                .setContentText(firstNotEmpty(body, "Pesan baru masuk"))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(firstNotEmpty(body, "Pesan baru masuk")))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        notifyNow(makeRequestCode(NOTIF_ID_GENERAL, id, screen), builder.build());
    }

    private PendingIntent createOrderActionIntent(
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

    private PendingIntent createDeleteIntent(String id, int notificationId) {
        Intent intent = new Intent(this, TransivaNotificationActionReceiver.class);
        intent.setAction("com.transiva.app.NOTIFICATION_DISMISSED");
        intent.putExtra("notification_id", notificationId);
        intent.putExtra("order_db_id", safe(id));

        return PendingIntent.getBroadcast(
                this,
                makeRequestCode(3001, id, "delete"),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private Intent buildOpenIntent(String screen, String id, String source, Map<String, String> data) {
        String normalized = lower(firstNotEmpty(screen, ""));

        Intent intent;

        if (normalized.contains("withdraw") || normalized.equals("wd") || normalized.contains("penarikan")) {
            intent = new Intent(this, DriverWithdrawActivity.class);
        } else if (normalized.contains("deposit") || normalized.contains("topup") || normalized.contains("top_up")) {
            intent = new Intent(this, DriverTopUpActivity.class);
        } else if (normalized.contains("receipt") || normalized.contains("nota") || normalized.contains("history")) {
            intent = new Intent(this, DriverReceiptHistoryActivity.class);
        } else if (normalized.contains("driver") || normalized.contains("saldo")) {
            intent = new Intent(this, DriverDashboardActivity.class);
        } else {
            intent = new Intent(this, MainActivity.class);
        }

        intent.setAction("OPEN_TRANSIVA");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        if (data != null) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                if (entry != null && entry.getKey() != null && entry.getValue() != null) {
                    intent.putExtra(entry.getKey(), entry.getValue());
                }
            }
        }

        intent.putExtra("open_screen", safe(screen));
        intent.putExtra("screen", safe(screen));
        intent.putExtra("source", safe(source));
        intent.putExtra("notification_click", "1");
        intent.putExtra("order_db_id", safe(id));
        intent.putExtra("order_id", safe(id));
        intent.putExtra("id", safe(id));

        return intent;
    }

    private String normalizeFinanceScreen(Map<String, String> data, String title, String body) {
        String raw = lower(firstNotEmpty(
                getValue(data, "screen", ""),
                getValue(data, "open_screen", ""),
                getValue(data, "type", ""),
                getValue(data, "event", ""),
                getValue(data, "status", ""),
                title,
                body
        ));

        if (raw.contains("withdraw") || raw.contains("wd") || raw.contains("penarikan")) {
            return "driver_withdraw";
        }

        if (raw.contains("deposit") || raw.contains("topup") || raw.contains("top_up")) {
            return "driver_topup";
        }

        if (raw.contains("receipt") || raw.contains("nota") || raw.contains("history")) {
            return "driver_receipt_history";
        }

        return "driver_dashboard";
    }

    private String financeTitle(String screen) {
        String s = lower(screen);
        if (s.contains("withdraw")) return "Withdraw Driver";
        if (s.contains("topup") || s.contains("deposit")) return "Deposit Driver";
        return "Saldo Driver";
    }

    private String financeBody(String screen) {
        String s = lower(screen);
        if (s.contains("withdraw")) return "Status withdraw driver diperbarui.";
        if (s.contains("topup") || s.contains("deposit")) return "Status deposit driver diperbarui.";
        return "Saldo driver diperbarui.";
    }

    private void notifyNow(int notificationId, Notification notification) {
        if (notification == null) return;

        notification.flags |= Notification.FLAG_SHOW_LIGHTS;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Izin POST_NOTIFICATIONS belum diberikan");
                return;
            }
        }

        try {
            NotificationManagerCompat.from(this).notify(notificationId, notification);
        } catch (Exception e) {
            Log.e(TAG, "Gagal tampilkan notif: " + e.getMessage());
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        createChannel(
                manager,
                CHANNEL_ORDER_ID,
                CHANNEL_ORDER_NAME,
                "Notifikasi order masuk dan tombol Terima/Tolak",
                NotificationManager.IMPORTANCE_HIGH,
                new long[]{0, 500, 250, 500, 250, 900}
        );

        createChannel(
                manager,
                CHANNEL_FINANCE_ID,
                CHANNEL_FINANCE_NAME,
                "Notifikasi saldo masuk, deposit, dan withdraw",
                NotificationManager.IMPORTANCE_HIGH,
                new long[]{0, 350, 150, 500}
        );

        createChannel(
                manager,
                CHANNEL_GENERAL_ID,
                CHANNEL_GENERAL_NAME,
                "Notifikasi umum Transiva",
                NotificationManager.IMPORTANCE_DEFAULT,
                new long[]{0, 250, 150, 250}
        );
    }

    private void createChannel(
            NotificationManager manager,
            String id,
            String name,
            String desc,
            int importance,
            long[] vibration
    ) {
        if (manager.getNotificationChannel(id) != null) return;

        NotificationChannel channel = new NotificationChannel(id, name, importance);
        channel.setDescription(desc);
        channel.enableVibration(true);
        channel.setVibrationPattern(vibration);
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

    private int makeRequestCode(int prefix, String id, String extra) {
        String raw = prefix + "_" + safe(id) + "_" + safe(extra);
        int hash = Math.abs(raw.hashCode());
        return prefix * 100000 + (hash % 99999);
    }

    private String firstNotEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String lower(String value) {
        return safe(value).toLowerCase();
    }
}
