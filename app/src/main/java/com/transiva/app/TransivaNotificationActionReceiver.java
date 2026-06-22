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
    if (context == null || intent == null) {
      return;
    }

    try {
      int notificationId = intent.getIntExtra("notification_id", 1001);

      NotificationManager manager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

      if (manager != null) {
        manager.cancel(notificationId);
      }

      String orderDbId = firstNotEmpty(
        getExtra(intent, "order_db_id"),
        getExtra(intent, "order_id"),
        getExtra(intent, "id")
      );

      String action = firstNotEmpty(
        getExtra(intent, "action"),
        "driver_accept"
      );

      String endpoint = getExtra(intent, "action_endpoint");
      String token = getExtra(intent, "action_token");

      String actor = firstNotEmpty(
        getExtra(intent, "actor"),
        getExtra(intent, "username"),
        getExtra(intent, "offered_driver"),
        getExtra(intent, "driver")
      );

      String driverType = firstNotEmpty(
        getExtra(intent, "driver_type"),
        "bike"
      );

      String openScreen = "driver_accept".equals(action)
        ? "driver_trip"
        : "driver_order";

      Intent openIntent = new Intent(context, MainActivity.class);
      openIntent.setAction("OPEN_TRANSIVA");

      openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      openIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      openIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

      openIntent.putExtra("from_notification_action", "1");
      openIntent.putExtra("open_screen", openScreen);
      openIntent.putExtra("screen", openScreen);

      openIntent.putExtra("order_db_id", orderDbId);
      openIntent.putExtra("order_id", orderDbId);
      openIntent.putExtra("id", orderDbId);

      openIntent.putExtra("action", action);
      openIntent.putExtra("action_endpoint", endpoint);
      openIntent.putExtra("action_token", token);

      openIntent.putExtra("actor", actor);
      openIntent.putExtra("username", actor);
      openIntent.putExtra("offered_driver", actor);
      openIntent.putExtra("driver", actor);

      openIntent.putExtra("driver_type", driverType);
      openIntent.putExtra("notification_id", notificationId);
      openIntent.putExtra("source", "notification_action_receiver");
      openIntent.putExtra("time", String.valueOf(System.currentTimeMillis()));

      context.startActivity(openIntent);

    } catch (Exception e) {
      Log.e(TAG, "Gagal buka aplikasi dari tombol notifikasi", e);
    }
  }

  private String getExtra(Intent intent, String key) {
    try {
      String value = intent.getStringExtra(key);
      return value == null ? "" : value.trim();
    } catch (Exception e) {
      return "";
    }
  }

  private String firstNotEmpty(String... values) {
    if (values == null) return "";

    for (String value : values) {
      if (value != null && !value.trim().isEmpty()) {
        return value.trim();
      }
    }

    return "";
  }
}
