package com.transiva.app;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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
        final PowerManager.WakeLock wakeLock = acquireWakeLock(context);

        new Thread(() -> {
            String orderDbId = "";
            String action = "";
            String responseText = "";
            boolean success = false;

            try{
                orderDbId = getExtra(intent, "order_db_id");
                if(orderDbId.isEmpty()) orderDbId = getExtra(intent, "order_id");
                if(orderDbId.isEmpty()) orderDbId = getExtra(intent, "id");

                action = getExtra(intent, "action");
                String endpoint = getExtra(intent, "action_endpoint");
                String token = getExtra(intent, "action_token");
                String actor = getExtra(intent, "actor");
                if(actor.isEmpty()) actor = getExtra(intent, "username");
                if(actor.isEmpty()) actor = getExtra(intent, "offered_driver");
                String driverType = getExtra(intent, "driver_type");
                if(driverType.isEmpty()) driverType = "bike";

                int notificationId = intent.getIntExtra("notification_id", 1001);

                if(orderDbId.isEmpty() || action.isEmpty() || endpoint.isEmpty() || token.isEmpty()){
                    Log.e(TAG, "Data action tidak lengkap: orderDbId=" + orderDbId + ", action=" + action);
                    openApp(context, orderDbId, action, false, "Data aksi tidak lengkap");
                    return;
                }

                JSONObject json = new JSONObject();
                json.put("order_db_id", orderDbId);
                json.put("order_id", orderDbId);
                json.put("id", orderDbId);
                json.put("action", action);
                json.put("action_token", token);
                json.put("actor", actor);
                json.put("username", actor);
                json.put("offered_driver", actor);
                json.put("driver_type", driverType);

                HttpURLConnection conn = null;

                try{
                    URL url = new URL(endpoint);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setRequestProperty("Accept", "application/json");

                    OutputStream os = conn.getOutputStream();
                    os.write(json.toString().getBytes("UTF-8"));
                    os.flush();
                    os.close();

                    int code = conn.getResponseCode();
                    InputStream inputStream = code >= 200 && code < 300
                            ? conn.getInputStream()
                            : conn.getErrorStream();

                    responseText = readStream(inputStream);
                    Log.d(TAG, "HTTP " + code + " => " + responseText);

                    if(code >= 200 && code < 300){
                        try{
                            JSONObject res = new JSONObject(responseText);
                            success = res.optBoolean("success", false);
                        }catch(Exception ignored){
                            success = true;
                        }
                    }

                }finally{
                    if(conn != null){
                        conn.disconnect();
                    }
                }

                NotificationManager manager =
                        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

                if(manager != null){
                    manager.cancel(notificationId);
                }

                openApp(context, orderDbId, action, success, responseText);

            }catch(Exception e){
                Log.e(TAG, "Action gagal: " + e.getMessage());
                openApp(context, orderDbId, action, false, e.getMessage());
            }finally{
                try{
                    if(wakeLock != null && wakeLock.isHeld()){
                        wakeLock.release();
                    }
                }catch(Exception ignored){}

                pendingResult.finish();
            }
        }).start();
    }

    private PowerManager.WakeLock acquireWakeLock(Context context){
        try{
            PowerManager powerManager =
                    (PowerManager) context.getSystemService(Context.POWER_SERVICE);

            if(powerManager == null) return null;

            PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Transiva:NotificationAction"
            );
            wakeLock.acquire(20000);
            return wakeLock;
        }catch(Exception e){
            Log.e(TAG, "WakeLock gagal: " + e.getMessage());
            return null;
        }
    }

    private String getExtra(Intent intent, String key){
        String value = intent.getStringExtra(key);
        return value == null ? "" : value.trim();
    }

    private String readStream(InputStream inputStream){
        if(inputStream == null) return "";

        try{
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            StringBuilder builder = new StringBuilder();
            String line;

            while((line = reader.readLine()) != null){
                builder.append(line);
            }

            reader.close();
            return builder.toString();
        }catch(Exception e){
            return "";
        }
    }

    private void openApp(Context context, String orderDbId, String action, boolean success, String response){
        try{
            Intent openIntent = new Intent(context, MainActivity.class);
            openIntent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TOP |
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
            );

            openIntent.setAction("OPEN_TRANSIVA");
            openIntent.putExtra("from_notification_action", "1");
            openIntent.putExtra("order_db_id", orderDbId == null ? "" : orderDbId);
            openIntent.putExtra("order_id", orderDbId == null ? "" : orderDbId);
            openIntent.putExtra("id", orderDbId == null ? "" : orderDbId);
            openIntent.putExtra("action", action == null ? "" : action);
            openIntent.putExtra("action_success", success ? "1" : "0");
            openIntent.putExtra("action_response", response == null ? "" : response);

            if("driver_accept".equals(action)){
                openIntent.putExtra("open_screen", success ? "driver_trip" : "driver_order");
            }else{
                openIntent.putExtra("open_screen", "driver_order");
            }

            context.startActivity(openIntent);
        }catch(Exception e){
            Log.e(TAG, "Gagal buka aplikasi: " + e.getMessage());
        }
    }
}
