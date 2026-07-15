package com.transiva.app.driver.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.transiva.app.DriverChatActivity;
import com.transiva.app.DriverReceiptHistoryActivity;
import com.transiva.app.DriverWithdrawActivity;
import com.transiva.app.ProfileActivity;

public final class DriverBottomNavigation {

    public interface HomeAction {
        void showHome();
        void showOrders();
    }

    private DriverBottomNavigation() {}

    public static View build(Activity activity, HomeAction homeAction) {
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(activity, 6), dp(activity, 5),
                dp(activity, 6), dp(activity, 5));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setStroke(dp(activity, 1), Color.parseColor("#DDE8F7"));
        bar.setBackground(background);
        bar.setElevation(dp(activity, 10));

        add(bar, item(activity, "⌂", "Beranda",
                view -> homeAction.showHome()), 1);

        add(bar, item(activity, "✉", "Pesan",
                view -> activity.startActivity(
                        new Intent(activity, DriverChatActivity.class))), 1);

        add(bar, item(activity, "Rp", "Pendapatan",
                view -> activity.startActivity(
                        new Intent(activity, DriverWithdrawActivity.class))), 1);

        add(bar, item(activity, "◷", "Riwayat",
                view -> activity.startActivity(
                        new Intent(activity, DriverReceiptHistoryActivity.class))), 1);

        add(bar, item(activity, "●", "Profil",
                view -> activity.startActivity(
                        new Intent(activity, ProfileActivity.class))), 1);

        return bar;
    }

    private static View item(
            Activity activity,
            String icon,
            String label,
            View.OnClickListener listener
    ) {
        LinearLayout item = new LinearLayout(activity);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(activity, 2), dp(activity, 3),
                dp(activity, 2), dp(activity, 2));

        TextView iconView = new TextView(activity);
        iconView.setText(icon);
        iconView.setTextSize(17);
        iconView.setGravity(Gravity.CENTER);
        iconView.setTextColor(Color.parseColor("#0B7CFF"));
        iconView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        item.addView(iconView);

        TextView labelView = new TextView(activity);
        labelView.setText(label);
        labelView.setTextSize(9);
        labelView.setGravity(Gravity.CENTER);
        labelView.setTextColor(Color.parseColor("#475569"));
        item.addView(labelView);

        item.setOnClickListener(listener);
        return item;
    }

    private static void add(LinearLayout parent, View view, int weight) {
        parent.addView(view, new LinearLayout.LayoutParams(0, -1, weight));
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value
                * activity.getResources().getDisplayMetrics().density);
    }
}
