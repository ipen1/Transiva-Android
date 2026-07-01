package com.transiva.app;

import android.Manifest;
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
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class TransivaFirebaseService extends FirebaseMessagingService {

    public static final String BASE_URL = "https://transiva.my.id/server/";

    private static final String CH_ORDER = "transiva_order_channel";
    private static final String CH_WALLET = "transiva_wallet_channel";
    private static final String CH_CHAT = "transiva_chat_channel";
    private static final String CH_PROMO = "transiva_promo_channel";
    private static final String CH_BROADCAST = "transiva_broadcast_channel";
    private static final String CH_GENERAL = "transiva_general_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        saveTokenLocal(token);
        sendTokenToServer(token);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        createChannels();

        Map<String, String> data = remoteMessage.getData();
        if (data == null || data.isEmpty()) {
            String title = remoteMessage.getNotification() != null ? remoteMessage.getNotification().getTitle() : "Transiva";
            String body = remoteMessage.getNotification() != null ? remoteMessage.getNotification().getBody() : "Notifikasi baru";
            showNotification("general", title, body, null, null, null, data);
            return;
        }

        String type = first(data.get("type"), data.get("notif_type"), data.get("category"), "general").toLowerCase();
        String title = first(data.get("title"), "Transiva");
        String body = first(data.get("body"), data.get("message"), "Notifikasi baru");
        String orderId = first(data.get("order_id"), data.get("id_order"), data.get("orderId"), "");
        String roomId = first(data.get("room_id"), data.get("chat_room"), "");
        String url = first(data.get("url"), data.get("link"), "");

        showNotification(type, title, body, orderId, roomId, url, data);
    }

    private void showNotification(String type, String title, String body, String orderId, String roomId, String url, Map<String, String> data) {
        String channelId = channelForType(type);
        Intent intent = buildOpenIntent(type, orderId, roomId, url, data);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int requestCode = Math.abs((type + "|" + first(orderId, "") + "|" + first(roomId, "") + "|" + System.currentTimeMillis()).hashCode());
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(getSmallIcon())
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(priorityForType(type))
                .setCategory(categoryForType(type))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_VIBRATE | NotificationCompat.DEFAULT_LIGHTS);

        if (isOrder(type)) {
            builder.setOngoing(false);
        }

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManagerCompat.from(this).notify(requestCode, builder.build());
    }

    private Intent buildOpenIntent(String type, String orderId, String roomId, String url, Map<String, String> data) {
        Intent intent;
        String screen = data != null ? first(data.get("screen"), "") : "";
        String targetRole = data != null ? first(data.get("target_role"), data.get("role"), "") : "";
        String senderType = data != null ? first(data.get("sender_type"), "") : "";

        if (isChat(type)) {
            boolean openDriverChat = "driver".equalsIgnoreCase(targetRole) || "customer".equalsIgnoreCase(senderType);
            intent = new Intent(this, openDriverChat ? DriverChatActivity.class : CustomerChatActivity.class);
            intent.putExtra("room_id", first(roomId, orderId));
            intent.putExtra("order_id", orderId);
            intent.putExtra("from_fcm", true);
            return intent;
        }

        if (isOrder(type) || "driver_order".equalsIgnoreCase(screen)) {
            boolean driverScreen = "driver".equalsIgnoreCase(targetRole) || "driver_order".equalsIgnoreCase(screen) || "driver_accept".equalsIgnoreCase(first(data != null ? data.get("action_accept") : "", ""));
            intent = new Intent(this, driverScreen ? DriverDashboardActivity.class : CustomerTripActivity.class);
            intent.putExtra("order_id", orderId);
            intent.putExtra("from_fcm", true);
            return intent;
        }

        if (isWallet(type)) {
            boolean driverWallet = "driver".equalsIgnoreCase(targetRole) || type.contains("driver") || type.contains("withdraw");
            intent = new Intent(this, driverWallet ? DriverTopUpActivity.class : CustomerTopUpActivity.class);
            intent.putExtra("from_fcm", true);
            return intent;
        }

        if (!TextUtils.isEmpty(url) && (url.startsWith("http://") || url.startsWith("https://"))) {
            intent = new Intent(this, MainActivity.class);
            intent.putExtra("url", url);
            intent.putExtra("from_fcm", true);
            return intent;
        }

        intent = new Intent(this, NativeHomeActivity.class);
        intent.putExtra("from_fcm", true);
        intent.putExtra("notif_type", type);
        return intent;
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        createChannel(CH_ORDER, "Order Transiva", "Notifikasi order baru dan status order", NotificationManager.IMPORTANCE_HIGH);
        createChannel(CH_WALLET, "Financial Transiva", "Deposit, saldo, dan penarikan", NotificationManager.IMPORTANCE_HIGH);
        createChannel(CH_CHAT, "Chat Transiva", "Pesan customer dan driver", NotificationManager.IMPORTANCE_HIGH);
        createChannel(CH_PROMO, "Promo Transiva", "Promo dan penawaran", NotificationManager.IMPORTANCE_DEFAULT);
        createChannel(CH_BROADCAST, "Broadcast Admin", "Pengumuman admin Transiva", NotificationManager.IMPORTANCE_HIGH);
        createChannel(CH_GENERAL, "Transiva", "Notifikasi umum", NotificationManager.IMPORTANCE_DEFAULT);
    }

    private void createChannel(String id, String name, String desc, int importance) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(id) != null) return;

        NotificationChannel channel = new NotificationChannel(id, name, importance);
        channel.setDescription(desc);
        channel.enableVibration(true);
        channel.enableLights(true);
        channel.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
        nm.createNotificationChannel(channel);
    }

    private String channelForType(String type) {
        type = first(type, "general").toLowerCase();
        if (isChat(type)) return CH_CHAT;
        if (isWallet(type)) return CH_WALLET;
        if (isOrder(type)) return CH_ORDER;
        if (type.contains("promo")) return CH_PROMO;
        if (type.contains("broadcast") || type.contains("admin")) return CH_BROADCAST;
        return CH_GENERAL;
    }

    private int priorityForType(String type) {
        type = first(type, "").toLowerCase();
        if (isChat(type) || isOrder(type) || isWallet(type) || type.contains("broadcast")) {
            return NotificationCompat.PRIORITY_HIGH;
        }
        return NotificationCompat.PRIORITY_DEFAULT;
    }

    private String categoryForType(String type) {
        type = first(type, "").toLowerCase();
        if (isChat(type)) return NotificationCompat.CATEGORY_MESSAGE;
        if (isOrder(type)) return NotificationCompat.CATEGORY_STATUS;
        if (isWallet(type)) return NotificationCompat.CATEGORY_STATUS;
        if (type.contains("promo")) return NotificationCompat.CATEGORY_PROMO;
        return NotificationCompat.CATEGORY_MESSAGE;
    }

    private boolean isChat(String type) {
        type = first(type, "").toLowerCase();
        return type.contains("chat") || type.contains("message");
    }

    private boolean isOrder(String type) {
        type = first(type, "").toLowerCase();
        return type.contains("order") || type.contains("ride") || type.contains("food") || type.contains("pickup") || type.contains("wisata") || type.contains("merchant");
    }

    private boolean isWallet(String type) {
        type = first(type, "").toLowerCase();
        return type.contains("wallet") || type.contains("financial") || type.contains("deposit") || type.contains("withdraw") || type.contains("saldo") || type.contains("balance");
    }

    private int getSmallIcon() {
        try {
            return getApplicationInfo().icon;
        } catch (Exception e) {
            return android.R.drawable.ic_dialog_info;
        }
    }

    private void saveTokenLocal(String token) {
        getSharedPreferences("transiva_fcm", MODE_PRIVATE).edit().putString("fcm_token", token).apply();
    }

    private void sendTokenToServer(String token) {
        new Thread(() -> {
            try {
                SharedPreferences sp1 = getSharedPreferences("transiva_session", MODE_PRIVATE);
                SharedPreferences sp2 = getSharedPreferences("TransivaSession", MODE_PRIVATE);
                SharedPreferences sp3 = getSharedPreferences("user_session", MODE_PRIVATE);

                String userId = first(sp1.getString("user_id", ""), sp2.getString("user_id", ""), sp3.getString("user_id", ""), sp1.getString("id", ""), sp2.getString("id", ""), sp3.getString("id", ""));
                String username = first(sp1.getString("username", ""), sp2.getString("username", ""), sp3.getString("username", ""));

                String json = "{"
                        + "\"token\":" + quote(token) + ","
                        + "\"fcm_token\":" + quote(token) + ","
                        + "\"user_id\":" + quote(userId) + ","
                        + "\"username\":" + quote(username)
                        + "}";

                URL url = new URL(BASE_URL + "save_fcm_token.php");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {
            }
        }).start();
    }

    private String quote(String s) {
        if (s == null) s = "";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private String first(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null) {
                v = v.trim();
                if (!v.isEmpty() && !"null".equalsIgnoreCase(v) && !"undefined".equalsIgnoreCase(v)) return v;
            }
        }
        return "";
    }
}
