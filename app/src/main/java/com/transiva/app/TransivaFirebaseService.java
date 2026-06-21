package com.transiva.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

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
                .putString(PREF_FCM_TOKEN, token)
                .apply();
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);

        wakeDevice();

        Map<String, String> data = message.getData();

        String title = "Transiva";
        String body = "Pesan baru masuk";

        if(data != null){

            if(data.containsKey("title") && data.get("title") != null){
                title = data.get("title");
            }

            if(data.containsKey("body") && data.get("body") != null){
                body = data.get("body");
            }

            if(data.containsKey("message") && data.get("message") != null){
                body = data.get("message");
            }
        }

        if(message.getNotification() != null){

            if(message.getNotification().getTitle() != null && title.equals("Transiva")){
                title = message.getNotification().getTitle();
            }

            if(message.getNotification().getBody() != null && body.equals("Pesan baru masuk")){
                body = message.getNotification().getBody();
            }
        }

        showHighPriorityNotification(title, body, data);
    }

    private void showHighPriorityNotification(
            String title,
            String body,
            Map<String, String> data
    ){

        createNotificationChannel();

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        intent.setAction("OPEN_TRANSIVA");

        if(data != null){
            for(Map.Entry<String, String> entry : data.entrySet()){
                intent.putExtra(entry.getKey(), entry.getValue());
            }
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setOngoing(false)
                        .setOnlyAlertOnce(false)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setVibrate(new long[]{0, 500, 250, 500, 250, 900})
                        .setLights(0xffffffff, 1000, 1000)
                        .setFullScreenIntent(pendingIntent, true);

        if(data != null && "1".equals(data.get("has_action"))){

            String orderDbId = data.get("order_db_id");
            String endpoint = data.get("action_endpoint");
            String token = data.get("action_token");
            String acceptAction = data.get("action_accept");
            String rejectAction = data.get("action_reject");

            if(orderDbId != null && endpoint != null && token != null){

                builder.addAction(
                        R.mipmap.ic_launcher,
                        "Terima",
                        createActionIntent(
                                2001,
                                orderDbId,
                                acceptAction,
                                endpoint,
                                token
                        )
                );

                builder.addAction(
                        R.mipmap.ic_launcher,
                        "Tolak",
                        createActionIntent(
                                2002,
                                orderDbId,
                                rejectAction,
                                endpoint,
                                token
                        )
                );
            }
        }

        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_SHOW_LIGHTS;

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if(manager != null){
            manager.notify(NOTIF_ID_ORDER, notification);
        }
    }

    private PendingIntent createActionIntent(
            int requestCode,
            String orderDbId,
            String action,
            String endpoint,
            String token
    ){

        Intent intent = new Intent(this, TransivaNotificationActionReceiver.class);
        intent.setAction("TRANSIVA_NOTIFICATION_ACTION");
        intent.putExtra("order_id", orderDbId);
        intent.putExtra("action", action);
        intent.putExtra("action_endpoint", endpoint);
        intent.putExtra("action_token", token);
        intent.putExtra("notification_id", NOTIF_ID_ORDER);

        return PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void createNotificationChannel(){

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O){
            return;
        }

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if(manager == null){
            return;
        }

        NotificationChannel oldChannel = manager.getNotificationChannel(CHANNEL_ID);

        if(oldChannel != null){
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

    private void wakeDevice(){

        try{

            PowerManager powerManager =
                    (PowerManager) getSystemService(Context.POWER_SERVICE);

            if(powerManager == null){
                return;
            }

            PowerManager.WakeLock wakeLock =
                    powerManager.newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK,
                            "Transiva:FCMWakeLock"
                    );

            wakeLock.acquire(15000);

        }catch(Exception e){
            Log.e(TAG, "WakeLock gagal: " + e.getMessage());
        }
    }
}
