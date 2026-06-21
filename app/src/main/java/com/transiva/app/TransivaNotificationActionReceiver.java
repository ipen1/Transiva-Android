package com.transiva.app;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TransivaNotificationActionReceiver extends BroadcastReceiver {

    private static final String TAG = "TRANSIVA_ACTION";

    @Override
    public void onReceive(Context context, Intent intent) {

        if(intent == null){
            return;
        }

        final PendingResult pendingResult = goAsync();

        PowerManager.WakeLock wakeLock = null;

        try{

            PowerManager powerManager =
                    (PowerManager) context.getSystemService(Context.POWER_SERVICE);

            if(powerManager != null){
                wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "Transiva:NotificationAction"
                );
                wakeLock.acquire(15000);
            }

        }catch(Exception ignored){}

        final PowerManager.WakeLock finalWakeLock = wakeLock;

        new Thread(() -> {

            try{

                String orderId = intent.getStringExtra("order_id");
                String action = intent.getStringExtra("action");
                String endpoint = intent.getStringExtra("action_endpoint");
                String token = intent.getStringExtra("action_token");
                int notificationId = intent.getIntExtra("notification_id", 1001);

                if(orderId == null || action == null || endpoint == null || token == null){
                    return;
                }

                JSONObject json = new JSONObject();
                json.put("order_id", orderId);
                json.put("action", action);
                json.put("action_token", token);

                URL url = new URL(endpoint);

                HttpURLConnection conn =
                        (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Accept", "application/json");

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

                int code = conn.getResponseCode();

                Log.d(TAG, "Action sent. HTTP: " + code);

                conn.disconnect();

                NotificationManager manager =
                        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

                if(manager != null){
                    manager.cancel(notificationId);
                }

            }catch(Exception e){
                Log.e(TAG, "Action gagal: " + e.getMessage());
            }finally{

                try{
                    if(finalWakeLock != null && finalWakeLock.isHeld()){
                        finalWakeLock.release();
                    }
                }catch(Exception ignored){}

                pendingResult.finish();
            }

        }).start();
    }
}
