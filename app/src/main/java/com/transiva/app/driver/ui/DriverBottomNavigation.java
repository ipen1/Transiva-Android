package com.transiva.app.driver.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.transiva.app.DriverChatActivity;
import com.transiva.app.DriverDashboardActivity;
import com.transiva.app.DriverReceiptHistoryActivity;
import com.transiva.app.DriverWithdrawActivity;
import com.transiva.app.ProfileActivity;

public final class DriverBottomNavigation {

    public enum ActiveItem {
        HOME,
        ACTIVITY,
        CHAT,
        WALLET,
        PROFILE
    }

    public interface HomeAction {
        void showHome();
        void showOrders();
    }

    private DriverBottomNavigation() {}

    public static View build(
            Activity activity,
            ActiveItem activeItem
    ) {
        LinearLayout navigation = createContainer(activity);

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
                        DriverReceiptHistoryActivity.class
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
                        ActiveItem.WALLET,
                        activeItem,
                        DriverWithdrawActivity.class
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
                        ProfileActivity.class
                ),
                itemLayoutParams()
        );

        return navigation;
    }

    public static View build(
            Activity activity,
            HomeAction homeAction
    ) {
        LinearLayout navigation = createContainer(activity);

        navigation.addView(
                navItemWithAction(
                        activity,
                        "Beranda",
                        "ic_nav_home",
                        true,
                        view -> {
                            if (homeAction != null) {
                                homeAction.showHome();
                            }
                        }
                ),
                itemLayoutParams()
        );

        navigation.addView(
                navItem(
                        activity,
                        "Aktivitas",
                        "ic_nav_activity",
                        ActiveItem.ACTIVITY,
                        ActiveItem.HOME,
                        DriverReceiptHistoryActivity.class
                ),
                itemLayoutParams()
        );

        navigation.addView(
                navItem(
                        activity,
                        "Pesan",
                        "ic_nav_chat",
                        ActiveItem.CHAT,
                        ActiveItem.HOME,
                        DriverChatActivity.class
                ),
                itemLayoutParams()
        );

        navigation.addView(
                navItem(
                        activity,
                        "Pendapatan",
                        "ic_nav_wallet",
                        ActiveItem.WALLET,
                        ActiveItem.HOME,
                        DriverWithdrawActivity.class
                ),
                itemLayoutParams()
        );

        navigation.addView(
                navItem(
                        activity,
                        "Akun",
                        "ic_nav_profile",
                        ActiveItem.PROFILE,
                        ActiveItem.HOME,
                        ProfileActivity.class
                ),
                itemLayoutParams()
        );

        return navigation;
    }

    private static LinearLayout createContainer(
            Activity activity
    ) {
        LinearLayout navigation = new LinearLayout(activity);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(
                dp(activity, 5),
                dp(activity, 4),
                dp(activity, 5),
                dp(activity, 4)
        );
        navigation.setBackgroundColor(Color.WHITE);
        navigation.setElevation(dp(activity, 8));
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

        return navItemWithAction(
                activity,
                label,
                iconName,
                active,
                active
                        ? null
                        : view -> {
                            Intent intent = new Intent(activity, target);
                            intent.addFlags(
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
                            );
                            activity.startActivity(intent);
                        }
        );
    }

    private static View navItemWithAction(
            Activity activity,
            String label,
            String iconName,
            boolean active,
            View.OnClickListener listener
    ) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setClickable(listener != null);
        root.setFocusable(listener != null);

        ImageView icon = new ImageView(activity);

        int drawableId = activity
                .getResources()
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

        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setAlpha(active ? 1f : 0.55f);

        root.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(activity, 22),
                        dp(activity, 22)
                )
        );

        TextView title = new TextView(activity);
        title.setText(label);
        title.setTextSize(9);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(
                Color.parseColor(
                        active ? "#0B7CFF" : "#64748B"
                )
        );
        title.setTypeface(
                Typeface.DEFAULT,
                active
                        ? Typeface.BOLD
                        : Typeface.NORMAL
        );

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(-1, -2);
        titleParams.setMargins(
                0,
                dp(activity, 2),
                0,
                0
        );
        root.addView(title, titleParams);

        View indicator = new View(activity);
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
        root.addView(indicator, indicatorParams);

        if (listener != null) {
            root.setOnClickListener(listener);
        }

        return root;
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
