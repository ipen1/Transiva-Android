package com.transiva.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class TransivaDriverForegroundService extends Service {

    private static final String TAG = "TRANSIVA_DRIVER_SERVICE";

    public static final String ACTION_START = "TRANSIVA_DRIVER_SERVICE_START";
    public static final String ACTION_STOP = "TRANSIVA_DRIVER_SERVICE_STOP";

    private static final String CHANNEL_ID = "transiva_channel";
    private static final int NOTIFICATION_ID = 2001;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Log.d(TAG, "Service dibuat");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
            Log.d(TAG, "Service dihentikan");
            return START_NOT_STICKY;
        }

        Notification notification = buildNotification();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        Log.d(TAG, "Service aktif foreground");

        return START_STICKY;
    }

    private Notification buildNotification() {

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        android.app.PendingIntent pendingIntent =
                android.app.PendingIntent.getActivity(
                        this,
                        2001,
                        openIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT |
                                android.app.PendingIntent.FLAG_IMMUTABLE
                );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Transiva Driver Aktif")
                .setContentText("Aplikasi menjaga koneksi driver tetap stabil.")
                .setStyle(
                        new NotificationCompat.BigTextStyle()
                                .bigText("Transiva berjalan di latar belakang untuk menjaga order dan lokasi driver tetap stabil.")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT < 26) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Transiva",
                NotificationManager.IMPORTANCE_HIGH
        );

        channel.setDescription("Notifikasi dan layanan latar belakang Transiva");
        channel.enableVibration(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "Service dihancurkan");

        /*
         * Android modern tetap bisa menghentikan service jika sistem butuh memori.
         * START_STICKY membantu Android mencoba menjalankan ulang service.
         */
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
