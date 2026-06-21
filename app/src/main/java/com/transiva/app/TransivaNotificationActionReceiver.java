package com.transiva.app;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class TransivaNotificationActionReceiver extends BroadcastReceiver {

    private static final String TAG = "TRANSIVA_ACTION";

    @Override
    public void onReceive(Context context, Intent intent) {

        if(intent == null){
            return;
        }

        try{

            int notificationId =
                    intent.getIntExtra("notification_id", 1001);

            NotificationManager manager =
                    (NotificationManager) context.getSystemService(
                            Context.NOTIFICATION_SERVICE
                    );

            if(manager != null){
                manager.cancel(notificationId);
            }

            String orderDbId = getExtra(intent, "order_db_id");

            if(orderDbId.isEmpty()){
                orderDbId = getExtra(intent, "order_id");
            }

            if(orderDbId.isEmpty()){
                orderDbId = getExtra(intent, "id");
            }

            String action = getExtra(intent, "action");

            if(action.isEmpty()){
                action = "driver_accept";
            }

            String actor = getExtra(intent, "actor");

            if(actor.isEmpty()){
                actor = getExtra(intent, "username");
            }

            if(actor.isEmpty()){
                actor = getExtra(intent, "offered_driver");
            }

            String driverType = getExtra(intent, "driver_type");

            if(driverType.isEmpty()){
                driverType = "bike";
            }

            String actionToken =
                    getExtra(intent, "action_token");

            String endpoint =
                    getExtra(intent, "action_endpoint");

            Intent openIntent =
                    new Intent(context, MainActivity.class);

            openIntent.setAction("OPEN_TRANSIVA");

            openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            openIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            openIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

            openIntent.putExtra(
                    "from_notification_action",
                    "1"
            );

            openIntent.putExtra(
                    "open_screen",
                    "driver_trip"
            );

            openIntent.putExtra(
                    "order_db_id",
                    orderDbId
            );

            openIntent.putExtra(
                    "order_id",
                    orderDbId
            );

            openIntent.putExtra(
                    "id",
                    orderDbId
            );

            openIntent.putExtra(
                    "action",
                    action
            );

            openIntent.putExtra(
                    "actor",
                    actor
            );

            openIntent.putExtra(
                    "username",
                    actor
            );

            openIntent.putExtra(
                    "offered_driver",
                    actor
            );

            openIntent.putExtra(
                    "driver_type",
                    driverType
            );

            openIntent.putExtra(
                    "action_token",
                    actionToken
            );

            openIntent.putExtra(
                    "action_endpoint",
                    endpoint
            );

            context.startActivity(openIntent);

        }catch(Exception e){
            Log.e(
                    TAG,
                    "Gagal buka aplikasi: " + e.getMessage()
            );
        }
    }

    private String getExtra(Intent intent, String key){
        String value = intent.getStringExtra(key);
        return value == null ? "" : value.trim();
    }
}
