package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.transiva.app.driver.ui.DriverBottomNavigation;

public class DriverProfileActivity extends Activity {

    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(
                Color.parseColor("#0B7CFF")
        );

        getWindow().setNavigationBarColor(
                Color.parseColor("#071426")
        );

        session = new SessionManager(this);

        if (!validDriverSession()) {
            redirectLogin();
            return;
        }

        setContentView(buildScreen());
    }

    private boolean validDriverSession() {
        return session != null
                && session.isLoggedIn()
                && "driver".equals(
                session.normalizeRole(
                        session.getRole()
                )
        )
                && !clean(
                session.getToken()
        ).isEmpty();
    }

    private void redirectLogin() {
        Intent intent =
                new Intent(
                        this,
                        LoginActivity.class
                );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        startActivity(intent);
        finish();
    }

    private View buildScreen() {
        FrameLayout page =
                new FrameLayout(this);

        page.setBackgroundColor(
                Color.parseColor("#F6F9FE")
        );

        LinearLayout shell =
                new LinearLayout(this);

        shell.setOrientation(
                LinearLayout.VERTICAL
        );

        page.addView(
                shell,
                new FrameLayout.LayoutParams(
                        -1,
                        -1
                )
        );

        ScrollView scroll =
                new ScrollView(this);

        scroll.setFillViewport(true);

        shell.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        content.setPadding(
                dp(14),
                dp(14),
                dp(14),
                dp(24)
        );

        scroll.addView(
                content,
                new ScrollView.LayoutParams(
                        -1,
                        -2
                )
        );

        content.addView(
                header(
                        "Akun Driver",
                        "Profil, kendaraan, dan keamanan akun"
                )
        );

        LinearLayout identity =
                new LinearLayout(this);

        identity.setOrientation(
                LinearLayout.VERTICAL
        );

        identity.setGravity(
                Gravity.CENTER_HORIZONTAL
        );

        identity.setPadding(
                dp(18),
                dp(22),
                dp(18),
                dp(20)
        );

        identity.setBackground(
                gradient(
                        "#075EF4",
                        "#22A4FF",
                        22
                )
        );

        identity.setElevation(dp(3));

        ImageView avatar =
                new ImageView(this);

        int profileIcon =
                drawable("ic_nav_profile");

        if (profileIcon != 0) {
            avatar.setImageResource(
                    profileIcon
            );
        } else {
            avatar.setImageResource(
                    android.R.drawable.sym_def_app_icon
            );
        }

        avatar.setPadding(
                dp(18),
                dp(18),
                dp(18),
                dp(18)
        );

        avatar.setBackground(
                round(
                        "#FFFFFF",
                        50
                )
        );

        identity.addView(
                avatar,
                new LinearLayout.LayoutParams(
                        dp(92),
                        dp(92)
                )
        );

        TextView name =
                text(
                        first(
                                session.getName(),
                                session.getUsername(),
                                "Driver"
                        ),
                        20,
                        "#FFFFFF",
                        true
                );

        name.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams nameLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        nameLp.setMargins(
                0,
                dp(12),
                0,
                0
        );

        identity.addView(
                name,
                nameLp
        );

        TextView username =
                text(
                        "@"
                                + first(
                                session.getUsername(),
                                "driver"
                        ),
                        11,
                        "#EAF5FF",
                        false
                );

        username.setGravity(Gravity.CENTER);
        identity.addView(username);

        TextView verified =
                text(
                        "✓ Driver Transiva",
                        10,
                        "#0B7CFF",
                        true
                );

        verified.setPadding(
                dp(10),
                dp(5),
                dp(10),
                dp(5)
        );

        verified.setBackground(
                round(
                        "#FFFFFF",
                        14
                )
        );

        LinearLayout.LayoutParams verifiedLp =
                new LinearLayout.LayoutParams(
                        -2,
                        -2
                );

        verifiedLp.setMargins(
                0,
                dp(11),
                0,
                0
        );

        identity.addView(
                verified,
                verifiedLp
        );

        LinearLayout.LayoutParams identityLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        identityLp.setMargins(
                0,
                dp(14),
                0,
                dp(14)
        );

        content.addView(
                identity,
                identityLp
        );

        LinearLayout information =
                card();

        information.addView(
                text(
                        "Informasi Driver",
                        16,
                        "#0B3A78",
                        true
                )
        );

        information.addView(
                infoRow(
                        "Username",
                        first(
                                session.getUsername(),
                                "-"
                        )
                )
        );

        information.addView(
                infoRow(
                        "Nomor HP",
                        first(
                                session.getPhone(),
                                "-"
                        )
                )
        );

        information.addView(
                infoRow(
                        "Tipe Driver",
                        first(
                                session.getDriverType(),
                                "-"
                        )
                )
        );

        information.addView(
                infoRow(
                        "Status Akun",
                        "Terverifikasi"
                )
        );

        content.addView(
                information,
                sectionLp()
        );

        LinearLayout vehicle =
                card();

        vehicle.addView(
                text(
                        "Kendaraan & Dokumen",
                        16,
                        "#0B3A78",
                        true
                )
        );

        vehicle.addView(
                text(
                        "Data kendaraan, nomor polisi, foto kendaraan, SIM, dan dokumen driver akan dilengkapi pada tahap berikutnya.",
                        11,
                        "#718096",
                        false
                )
        );

        content.addView(
                vehicle,
                sectionLp()
        );

        LinearLayout security =
                card();

        security.addView(
                text(
                        "Keamanan",
                        16,
                        "#0B3A78",
                        true
                )
        );

        Button logout =
                dangerButton(
                        "Keluar dari Akun"
                );

        logout.setOnClickListener(
                view -> confirmLogout()
        );

        LinearLayout.LayoutParams logoutLp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(48)
                );

        logoutLp.setMargins(
                0,
                dp(13),
                0,
                0
        );

        security.addView(
                logout,
                logoutLp
        );

        content.addView(security);

        shell.addView(
                DriverBottomNavigation.build(
                        this,
                        DriverBottomNavigation.ActiveItem.PROFILE
                ),
                new LinearLayout.LayoutParams(
                        -1,
                        dp(66)
                )
        );

        return page;
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Keluar Akun")
                .setMessage(
                        "Yakin ingin keluar dari akun driver Transiva?"
                )
                .setNegativeButton(
                        "Batal",
                        null
                )
                .setPositiveButton(
                        "Keluar",
                        (dialog, which) -> {
                            DriverServiceController.stop(this);

                            try {
                                session.logout();
                            } catch (Exception ignored) {
                            }

                            redirectLogin();
                        }
                )
                .show();
    }

    private View header(
            String title,
            String subtitle
    ) {
        LinearLayout box =
                new LinearLayout(this);

        box.setOrientation(
                LinearLayout.VERTICAL
        );

        box.addView(
                text(
                        title,
                        24,
                        "#0B3A78",
                        true
                )
        );

        box.addView(
                text(
                        subtitle,
                        11,
                        "#718096",
                        false
                )
        );

        return box;
    }

    private View infoRow(
            String label,
            String value
    ) {
        LinearLayout row =
                new LinearLayout(this);

        row.setGravity(
                Gravity.CENTER_VERTICAL
        );

        row.setPadding(
                0,
                dp(12),
                0,
                dp(10)
        );

        TextView left =
                text(
                        label,
                        11,
                        "#64748B",
                        false
                );

        row.addView(
                left,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        TextView right =
                text(
                        value,
                        12,
                        "#0B3A78",
                        true
                );

        right.setGravity(Gravity.END);
        row.addView(right);

        return row;
    }

    private LinearLayout card() {
        LinearLayout box =
                new LinearLayout(this);

        box.setOrientation(
                LinearLayout.VERTICAL
        );

        box.setPadding(
                dp(15),
                dp(15),
                dp(15),
                dp(15)
        );

        box.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#E1EAF5",
                        18,
                        1
                )
        );

        box.setElevation(dp(1));
        return box;
    }

    private LinearLayout.LayoutParams sectionLp() {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        lp.setMargins(
                0,
                0,
                0,
                dp(14)
        );

        return lp;
    }

    private Button dangerButton(
            String value
    ) {
        Button button =
                new Button(this);

        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(13);

        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        button.setTextColor(Color.WHITE);

        button.setBackground(
                gradient(
                        "#EF4444",
                        "#DC2626",
                        14
                )
        );

        return button;
    }

    private TextView text(
            String value,
            int size,
            String color,
            boolean bold
    ) {
        TextView view =
                new TextView(this);

        view.setText(value);
        view.setTextSize(size);

        view.setTextColor(
                Color.parseColor(color)
        );

        view.setIncludeFontPadding(false);

        if (bold) {
            view.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return view;
    }

    private GradientDrawable round(
            String fill,
            int radius
    ) {
        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(
                Color.parseColor(fill)
        );

        drawable.setCornerRadius(
                dp(radius)
        );

        return drawable;
    }

    private GradientDrawable roundStroke(
            String fill,
            String stroke,
            int radius,
            int width
    ) {
        GradientDrawable drawable =
                round(
                        fill,
                        radius
                );

        drawable.setStroke(
                dp(width),
                Color.parseColor(stroke)
        );

        return drawable;
    }

    private GradientDrawable gradient(
            String start,
            String end,
            int radius
    ) {
        GradientDrawable drawable =
                new GradientDrawable(
                        GradientDrawable
                                .Orientation
                                .LEFT_RIGHT,
                        new int[]{
                                Color.parseColor(start),
                                Color.parseColor(end)
                        }
                );

        drawable.setCornerRadius(
                dp(radius)
        );

        return drawable;
    }

    private int drawable(
            String name
    ) {
        return getResources()
                .getIdentifier(
                        name,
                        "drawable",
                        getPackageName()
                );
    }

    private String first(
            String... values
    ) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            value = clean(value);

            if (!value.isEmpty()) {
                return value;
            }
        }

        return "";
    }

    private String clean(
            String value
    ) {
        if (value == null) {
            return "";
        }

        value = value.trim();

        return "null".equalsIgnoreCase(value)
                ? ""
                : value;
    }

    private int dp(
            int value
    ) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
