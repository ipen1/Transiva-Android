package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
    private final int NAVY = Color.parseColor("#071426");
    private final int BG = Color.parseColor("#F3F8FF");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        buildLayout();
    }

    private void buildLayout() {
        ScrollView scroll = new ScrollView(this);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(28));
        root.setBackgroundColor(BG);

        scroll.addView(root);
        setContentView(scroll);

        addHeader();
        gap(14);
        addBanner();
        gap(14);
        addMenu();
        gap(14);
        addPromoSummary();
        gap(14);
        addStatus();
        gap(14);

        Button logout = button("Keluar");
        logout.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        root.addView(logout, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void addHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(txt("Selamat Datang 👋", 13, "#64748B", false));
        left.addView(txt("Admin Transiva", 23, "#0B3A78", true));

        TextView badge = txt("Verified Admin", 11, "#0B7CFF", true);
        badge.setPadding(dp(10), dp(4), dp(10), dp(4));
        badge.setBackground(card("#EAF4FF", "#B9DBFF", 18));
        left.addView(badge);

        header.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(txt("🛠️", 30, "#0B3A78", true));
        root.addView(header);
    }

    private void addBanner() {
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(dp(18), dp(17), dp(18), dp(17));
        banner.setBackground(gradient());

        banner.addView(txt("🛡 Admin Control Center", 18, "#FFFFFF", true));
        banner.addView(txt(
                "Kelola pengguna, keuangan, promo, dan sistem Transiva",
                13,
                "#FFFFFF",
                false
        ));

        root.addView(banner);
    }

    private void addMenu() {
        root.addView(txt("Menu Admin", 18, "#0B3A78", true));

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
                {"✅", "Verifikasi"},
                {"🎁", "Promo"},
                {"⭐", "Rekomendasi"}
        };

        for (String[] itemData : menu) {
            final String menuName = itemData[1];

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(6), dp(10), dp(6), dp(9));
            item.setBackground(card(
                    "Promo".equals(menuName) ? "#EAF4FF" : "#FFFFFF",
                    "Promo".equals(menuName) ? "#8EC5FF" : "#D7E6F8",
                    20
            ));
            item.setElevation(dp("Promo".equals(menuName) ? 4 : 2));
            item.setClickable(true);
            item.setFocusable(true);

            item.addView(txt(itemData[0], 28, "#086BFF", true));

            TextView title = txt(menuName, 11, "#0B3A78", true);
            title.setGravity(Gravity.CENTER);
            item.addView(title);

            if ("Promo".equals(menuName)) {
                TextView chip = txt("BARU", 8, "#FFFFFF", true);
                chip.setGravity(Gravity.CENTER);
                chip.setPadding(dp(7), dp(2), dp(7), dp(2));
                chip.setBackground(card("#0B7CFF", "#0B7CFF", 10));
                item.addView(chip);
            }

            item.setOnClickListener(v -> openMenu(menuName));

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width =
                    (getResources().getDisplayMetrics().widthPixels - dp(50)) / 3;
            params.height = dp(102);
            params.setMargins(dp(4), dp(4), dp(4), dp(8));

            grid.addView(item, params);
        }

        root.addView(grid);
    }

    private void openMenu(String menuName) {
        Intent intent = null;

        switch (menuName) {
            case "Money":
                intent = new Intent(this, AdminMoneyManagementActivity.class);
                break;
            case "WD Driver":
                intent = new Intent(this, AdminDriverWithdrawManagementActivity.class);
                break;
            case "Verifikasi":
                intent = new Intent(this, AdminVerifyUserActivity.class);
                break;
            case "Promo":
                intent = new Intent(this, AdminPromoManagementActivity.class);
                break;
            case "Rekomendasi":
                intent = new Intent(this, AdminRecommendationManagementActivity.class);
                break;
        }

        if (intent != null) startActivity(intent);
    }

    private void addPromoSummary() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(15), dp(13), dp(15), dp(13));
        box.setBackground(card("#FFFFFF", "#D7E6F8", 20));

        TextView icon = txt("🎁", 28, "#086BFF", true);
        box.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(9), 0, 0, 0);
        copy.addView(txt("Promo & Broadcast", 15, "#0B3A78", true));
        copy.addView(txt(
                "Atur banner customer dan kirim notifikasi ke pengguna.",
                11,
                "#64748B",
                false
        ));
        box.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView open = txt("Buka ›", 12, "#0B7CFF", true);
        box.addView(open);
        box.setOnClickListener(v ->
                startActivity(new Intent(this, AdminPromoManagementActivity.class))
        );

        root.addView(box);
    }

    private void addStatus() {
        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setPadding(dp(16), dp(15), dp(16), dp(15));
        status.setBackground(card("#FFFFFF", "#D7E6F8", 20));

        status.addView(txt("Status Sistem", 16, "#0B3A78", true));
        status.addView(txt(
                "🟢 Admin panel aktif\n🟢 Sistem promo siap digunakan",
                12,
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

    private TextView txt(String text, int size, String color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.parseColor(color));
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable card(
            String background,
            String stroke,
            int radiusDp
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(background));
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), Color.parseColor(stroke));
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
        drawable.setCornerRadius(dp(22));
        return drawable;
    }

    private void gap(int height) {
        Space space = new Space(this);
        root.addView(space, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }
}
