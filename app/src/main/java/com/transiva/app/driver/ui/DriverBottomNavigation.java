package com.transiva.app.driver.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.transiva.app.DriverActivityHistoryActivity;
import com.transiva.app.DriverChatActivity;
import com.transiva.app.DriverDashboardActivity;
import com.transiva.app.DriverEarningsActivity;
import com.transiva.app.DriverProfileActivity;

public final class DriverBottomNavigation {

    public enum ActiveItem {
        HOME,
        ACTIVITY,
        CHAT,
        EARNINGS,
        PROFILE
    }

    private DriverBottomNavigation() {
    }

    public static View build(
            Activity activity,
            ActiveItem activeItem
    ) {
        LinearLayout navigation =
                new LinearLayout(activity);

        navigation.setOrientation(
                LinearLayout.HORIZONTAL
        );

        navigation.setGravity(Gravity.CENTER);

        navigation.setPadding(
                dp(activity, 5),
                dp(activity, 4),
                dp(activity, 5),
                dp(activity, 4)
        );

        navigation.setBackgroundColor(
                Color.WHITE
        );

        navigation.setElevation(
                dp(activity, 8)
        );

        navigation.addView(
                navItem(
                        activity,
                        "Beranda",
                        "ic_nav_home",
                        ActiveItem.HOME,
                        activeItem,
                        DriverDashboardActivity.class
                ),
                itemLayoutParams()
        );

        navigation.addView(
                navItem(
                        activity,
                        "Aktivitas",
                        "ic_nav_activity",
                        ActiveItem.ACTIVITY,
                        activeItem,
                        DriverActivityHistoryActivity.class
                ),
                itemLayoutParams()
        );

        navigation.addView(
                navItem(
                        activity,
                        "Pesan",
                        "ic_nav_chat",
                        ActiveItem.CHAT,
                        activeItem,
                        DriverChatActivity.class
                ),
                itemLayoutParams()
        );

        navigation.addView(
                navItem(
                        activity,
                        "Pendapatan",
                        "ic_nav_wallet",
                        ActiveItem.EARNINGS,
                        activeItem,
                        DriverEarningsActivity.class
                ),
                itemLayoutParams()
        );

        navigation.addView(
                navItem(
                        activity,
                        "Akun",
                        "ic_nav_profile",
                        ActiveItem.PROFILE,
                        activeItem,
                        DriverProfileActivity.class
                ),
                itemLayoutParams()
        );

        return navigation;
    }

    private static View navItem(
            Activity activity,
            String label,
            String iconName,
            ActiveItem item,
            ActiveItem activeItem,
            Class<?> target
    ) {
        boolean active = item == activeItem;

        LinearLayout root =
                new LinearLayout(activity);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setGravity(Gravity.CENTER);

        root.setClickable(!active);
        root.setFocusable(!active);

        ImageView icon =
                new ImageView(activity);

        int drawableId =
                activity.getResources()
                        .getIdentifier(
                                iconName,
                                "drawable",
                                activity.getPackageName()
                        );

        if (drawableId != 0) {
            icon.setImageResource(drawableId);
        } else {
            icon.setImageResource(
                    android.R.drawable.ic_menu_help
            );
        }

        icon.setScaleType(
                ImageView.ScaleType.CENTER_INSIDE
        );

        icon.setAlpha(
                active
                        ? 1f
                        : 0.58f
        );

        root.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(activity, 22),
                        dp(activity, 22)
                )
        );

        TextView title =
                new TextView(activity);

        title.setText(label);
        title.setTextSize(9);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(false);

        title.setTextColor(
                Color.parseColor(
                        active
                                ? "#0B7CFF"
                                : "#64748B"
                )
        );

        title.setTypeface(
                Typeface.DEFAULT,
                active
                        ? Typeface.BOLD
                        : Typeface.NORMAL
        );

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        titleParams.setMargins(
                0,
                dp(activity, 2),
                0,
                0
        );

        root.addView(
                title,
                titleParams
        );

        View indicator =
                new View(activity);

        indicator.setBackgroundColor(
                active
                        ? Color.parseColor("#0B7CFF")
                        : Color.TRANSPARENT
        );

        LinearLayout.LayoutParams indicatorParams =
                new LinearLayout.LayoutParams(
                        dp(activity, 22),
                        dp(activity, 2)
                );

        indicatorParams.setMargins(
                0,
                dp(activity, 2),
                0,
                0
        );

        root.addView(
                indicator,
                indicatorParams
        );

        if (!active) {
            root.setOnClickListener(
                    view ->
                            DriverPageTransition.open(
                                    activity,
                                    target,
                                    pageIndex(activeItem),
                                    pageIndex(item)
                            )
            );
        }

        return root;
    }

    private static int pageIndex(
            ActiveItem item
    ) {
        if (item == ActiveItem.ACTIVITY) {
            return DriverPageTransition.ACTIVITY;
        }

        if (item == ActiveItem.CHAT) {
            return DriverPageTransition.CHAT;
        }

        if (item == ActiveItem.EARNINGS) {
            return DriverPageTransition.EARNINGS;
        }

        if (item == ActiveItem.PROFILE) {
            return DriverPageTransition.PROFILE;
        }

        return DriverPageTransition.HOME;
    }

    private static LinearLayout.LayoutParams itemLayoutParams() {
        return new LinearLayout.LayoutParams(
                0,
                -1,
                1
        );
    }

    private static int dp(
            Activity activity,
            int value
    ) {
        return Math.round(
                value
                        * activity
                        .getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
