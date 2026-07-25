package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Bottom navigation tunggal untuk seluruh sisi Merchant. */
public final class MerchantBottomNavigation {
    private static final String ACTIVE_BG = "#EAF4FF";
    private static final String ACTIVE_COLOR = "#0B7CFF";
    private static final String INACTIVE_COLOR = "#64748B";

    public enum ActiveItem { HOME, ORDERS, MENU, REVIEWS, SETTINGS }

    private MerchantBottomNavigation() {}

    public static View build(Activity activity, ActiveItem activeItem) {
        LinearLayout nav = new LinearLayout(activity);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(activity, 5), dp(activity, 4), dp(activity, 5), dp(activity, 4));
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(dp(activity, 10));

        add(nav, item(activity, "Beranda", "ic_nav_home", ActiveItem.HOME, activeItem, MerchantDashboardActivity.class));
        add(nav, item(activity, "Pesanan", "ic_nav_activity", ActiveItem.ORDERS, activeItem, MerchantOrdersActivity.class));
        add(nav, item(activity, "Menu", "ic_service_food", ActiveItem.MENU, activeItem, MerchantMenuListActivity.class));
        add(nav, item(activity, "Ulasan", "ic_nav_chat", ActiveItem.REVIEWS, activeItem, MerchantReviewsActivity.class));
        add(nav, item(activity, "Pengaturan", "ic_nav_profile", ActiveItem.SETTINGS, activeItem, MerchantSettingsActivity.class));

        nav.setAlpha(0f);
        nav.setTranslationY(dp(activity, 10));
        nav.animate().alpha(1f).translationY(0f).setDuration(220L)
                .setInterpolator(new DecelerateInterpolator(1.8f)).start();
        return nav;
    }

    public static ActiveItem resolve(Activity activity) {
        if (activity instanceof MerchantOrdersActivity) return ActiveItem.ORDERS;
        if (activity instanceof MerchantMenuListActivity || activity instanceof MerchantAddMenuActivity) return ActiveItem.MENU;
        if (activity instanceof MerchantReviewsActivity) return ActiveItem.REVIEWS;
        if (activity instanceof MerchantSettingsActivity || activity instanceof MerchantRestaurantProfileActivity) return ActiveItem.SETTINGS;
        return ActiveItem.HOME;
    }

    private static void add(LinearLayout nav, View item) {
        nav.addView(item, new LinearLayout.LayoutParams(0, -1, 1f));
    }

    private static View item(Activity activity, String label, String iconName,
                             ActiveItem item, ActiveItem activeItem, Class<?> target) {
        boolean active = item == activeItem;
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
        root.setClickable(!active);
        root.setFocusable(!active);

        if (active) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor(ACTIVE_BG));
            bg.setCornerRadius(dp(activity, 18));
            root.setBackground(bg);
            root.setScaleX(1.02f);
            root.setScaleY(1.02f);
        }

        ImageView icon = new ImageView(activity);
        int drawableId = activity.getResources().getIdentifier(iconName, "drawable", activity.getPackageName());
        if (drawableId != 0) icon.setImageResource(drawableId);
        else icon.setImageResource(android.R.drawable.ic_menu_manage);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setAlpha(active ? 1f : .62f);
        root.addView(icon, new LinearLayout.LayoutParams(dp(activity, 22), dp(activity, 22)));

        TextView title = new TextView(activity);
        title.setText(label);
        title.setTextSize(9f);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(false);
        title.setTextColor(Color.parseColor(active ? ACTIVE_COLOR : INACTIVE_COLOR));
        title.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(activity, 2), 0, 0);
        root.addView(title, titleLp);

        if (!active) {
            root.setOnClickListener(v -> v.animate().scaleX(.90f).scaleY(.90f).setDuration(70L)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(110L)
                            .setInterpolator(new DecelerateInterpolator(1.7f))
                            .withEndAction(() -> open(activity, target)).start()).start());
        }
        return root;
    }

    private static void open(Activity activity, Class<?> target) {
        Intent i = new Intent(activity, target);
        i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        activity.startActivity(i);
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
