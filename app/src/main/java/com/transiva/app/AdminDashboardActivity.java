package com.transiva.app;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

public class AdminDashboardActivity extends Activity {

    private LinearLayout root;

    private static final String CHANNEL_ID = "transiva_admin_updates";

    private final int NAVY = Color.parseColor("#071426");
    private final int BG = Color.parseColor("#F3F8FF");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);

        createNotificationChannel();
        buildLayout();

        sendAdminNotification(
                "Admin Dashboard Aktif",
                "Panel administrator Transiva berhasil dibuka."
        );
    }

    private void buildLayout() {
        ScrollView scroll = new ScrollView(this);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(22), dp(16), dp(28));
        root.setBackgroundColor(BG);

        scroll.addView(root);
        setContentView(scroll);

        addHeader();
        gap(14);
        addBanner();
        gap(14);
        addMenu();
        gap(14);
        addStatus();
        gap(14);

        Button logout = button("Keluar");
        logout.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK
            );
            startActivity(intent);
            finish();
        });

        root.addView(
                logout,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(50)
                )
        );
    }

    private void addHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        left.addView(txt(
                "Selamat Datang 👋",
                13,
                "#64748B",
                false
        ));

        left.addView(txt(
                "Admin Transiva",
                23,
                "#0B3A78",
                true
        ));

        TextView badge = txt(
                "Verified Admin",
                11,
                "#0B7CFF",
                true
        );

        badge.setPadding(dp(10), dp(4), dp(10), dp(4));
        badge.setBackground(card("#EAF4FF", "#B9DBFF"));
        left.addView(badge);

        header.addView(
                left,
                new LinearLayout.LayoutParams(0, -2, 1f)
        );

        header.addView(txt(
                "🛠️",
                32,
                "#0B3A78",
                true
        ));

        root.addView(header);
    }

    private void addBanner() {
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(dp(18), dp(18), dp(18), dp(18));
        banner.setBackground(gradient());

        banner.addView(txt(
                "🛡 Admin Control Center",
                18,
                "#FFFFFF",
                true
        ));

        banner.addView(txt(
                "Monitoring Driver • Customer • Merchant • Sistem",
                13,
                "#FFFFFF",
                false
        ));

        root.addView(banner);
    }

    private void addMenu() {
        root.addView(txt(
                "Menu Admin",
                18,
                "#0B3A78",
                true
        ));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);

        String[][] menu = {
                {"🛵", "Driver"},
                {"👥", "Customer"},
                {"🏪", "Merchant"},
                {"📍", "Business"},
                {"🧺", "Laundry"},
                {"🏝️", "Wisata"},
                {"🏦", "Money"},
                {"💸", "WD Driver"},
                {"✅", "Verifikasi"}
        };

        for (String[] itemData : menu) {
            final String menuName = itemData[1];

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(8), dp(12), dp(8), dp(10));
            item.setBackground(card("#FFFFFF", "#D7E6F8"));
            item.setClickable(true);
            item.setFocusable(true);

            item.addView(txt(
                    itemData[0],
                    30,
                    "#086BFF",
                    true
            ));

            item.addView(txt(
                    menuName,
                    12,
                    "#0B3A78",
                    true
            ));

            item.setOnClickListener(v -> openMenu(menuName));

            GridLayout.LayoutParams params =
                    new GridLayout.LayoutParams();

            params.width =
                    (getResources()
                            .getDisplayMetrics()
                            .widthPixels - dp(50)) / 3;

            params.height = dp(105);
            params.setMargins(
                    dp(4),
                    dp(4),
                    dp(4),
                    dp(8)
            );

            grid.addView(item, params);
        }

        root.addView(grid);
    }

    private void openMenu(String menuName) {
        Intent intent = null;

        switch (menuName) {
            case "Money":
                intent = new Intent(
                        this,
                        AdminMoneyManagementActivity.class
                );
                break;

            case "WD Driver":
                intent = new Intent(
                        this,
                        AdminDriverWithdrawManagementActivity.class
                );
                break;

            case "Verifikasi":
                intent = new Intent(
                        this,
                        AdminVerifyUserActivity.class
                );
                break;
        }

        if (intent != null) {
            startActivity(intent);
        }
    }

    private void addStatus() {
        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setPadding(dp(16), dp(16), dp(16), dp(16));
        status.setBackground(card("#FFFFFF", "#D7E6F8"));

        status.addView(txt(
                "Status Sistem",
                17,
                "#0B3A78",
                true
        ));

        status.addView(txt(
                "🟢 Admin panel aktif\nNotifikasi sistem aktif",
                13,
                "#64748B",
                false
        ));

        root.addView(status);
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(gradient());
        return button;
    }

    private TextView txt(
            String text,
            int size,
            String color,
            boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.parseColor(color));

        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }

        return view;
    }

    private GradientDrawable card(
            String background,
            String stroke
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(background));
        drawable.setCornerRadius(dp(22));
        drawable.setStroke(
                dp(1),
                Color.parseColor(stroke)
        );
        return drawable;
    }

    private GradientDrawable gradient() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.parseColor("#086BFF"),
                        Color.parseColor("#2EA2FF")
                }
        );

        drawable.setCornerRadius(dp(24));
        return drawable;
    }

    private void gap(int height) {
        Space space = new Space(this);
        root.addView(
                space,
                new LinearLayout.LayoutParams(1, dp(height))
        );
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Transiva Admin Updates",
                            NotificationManager.IMPORTANCE_DEFAULT
                    );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void sendAdminNotification(
            String title,
            String message
    ) {
        try {
            NotificationManager manager =
                    (NotificationManager)
                            getSystemService(NOTIFICATION_SERVICE);

            Intent intent =
                    new Intent(this, AdminDashboardActivity.class);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pendingIntent =
                    PendingIntent.getActivity(
                            this,
                            1,
                            intent,
                            flags
                    );

            android.app.Notification.Builder builder =
                    Build.VERSION.SDK_INT >= 26
                            ? new android.app.Notification.Builder(
                                    this,
                                    CHANNEL_ID
                            )
                            : new android.app.Notification.Builder(this);

            builder.setSmallIcon(
                            android.R.drawable.ic_dialog_info
                    )
                    .setContentTitle(title)
                    .setContentText(message)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true);

            if (manager != null) {
                manager.notify(1001, builder.build());
            }
        } catch (Exception ignored) {
        }
    }

    private int dp(int value) {
        return (int) (
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
                        + 0.5f
        );
    }
}
