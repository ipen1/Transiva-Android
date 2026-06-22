package com.transiva.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/*
 * NativeSplashView.java
 *
 * Splash + loading native untuk Transiva WebView Hybrid.
 * Tujuan:
 * - Tidak ada layar putih saat WebView sedang load.
 * - User melihat loading yang terasa seperti aplikasi native.
 * - Bisa menampilkan mode offline/error tanpa crash.
 *
 * Cara pakai:
 * - Simpan di app/src/main/java/com/transiva/app/NativeSplashView.java
 * - MainActivity.java versi upgrade sudah otomatis memakai view ini.
 */
public class NativeSplashView extends FrameLayout {

    private TextView titleView;
    private TextView subtitleView;
    private TextView percentView;
    private ProgressBar progressBar;

    public NativeSplashView(Context context) {
        super(context);
        build(context);
    }

    private void build(Context context) {
        setClickable(true);
        setFocusable(true);
        setAlpha(1f);
        setVisibility(View.VISIBLE);

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.parseColor("#06142E"),
                        Color.parseColor("#081B3D"),
                        Color.parseColor("#030712")
                }
        );
        setBackground(bg);

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(28), dp(28), dp(28), dp(28));

        FrameLayout logo = new FrameLayout(context);
        GradientDrawable logoBg = new GradientDrawable();
        logoBg.setShape(GradientDrawable.OVAL);
        logoBg.setColor(Color.parseColor("#FF8A00"));
        logoBg.setStroke(dp(3), Color.parseColor("#FFFFFF"));
        logo.setBackground(logoBg);

        if (Build.VERSION.SDK_INT >= 21) {
            logo.setElevation(dp(10));
        }

        TextView logoText = new TextView(context);
        logoText.setText("T");
        logoText.setTextColor(Color.WHITE);
        logoText.setTextSize(34);
        logoText.setTypeface(Typeface.DEFAULT_BOLD);
        logoText.setGravity(Gravity.CENTER);
        logo.addView(logoText, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(86), dp(86));
        logoLp.bottomMargin = dp(18);
        box.addView(logo, logoLp);

        titleView = new TextView(context);
        titleView.setText("Transiva");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(25);
        titleView.setGravity(Gravity.CENTER);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        subtitleView = new TextView(context);
        subtitleView.setText("Membuka layanan...");
        subtitleView.setTextColor(Color.parseColor("#D7E3FF"));
        subtitleView.setTextSize(14);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setPadding(0, dp(7), 0, 0);
        box.addView(subtitleView, new LinearLayout.LayoutParams(-1, -2));

        progressBar = new ProgressBar(
                context,
                null,
                android.R.attr.progressBarStyleHorizontal
        );
        progressBar.setMax(100);
        progressBar.setProgress(5);

        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(dp(220), dp(8));
        progressLp.topMargin = dp(26);
        box.addView(progressBar, progressLp);

        percentView = new TextView(context);
        percentView.setText("5%");
        percentView.setTextColor(Color.parseColor("#AFC6F7"));
        percentView.setTextSize(12);
        percentView.setGravity(Gravity.CENTER);
        percentView.setPadding(0, dp(8), 0, 0);
        box.addView(percentView, new LinearLayout.LayoutParams(-1, -2));

        addView(box, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
    }

    public void showLoading(String message, int progress) {
        try {
            setAlpha(1f);
            setVisibility(View.VISIBLE);
            titleView.setText("Transiva");
            subtitleView.setText(message == null || message.trim().isEmpty()
                    ? "Membuka layanan..."
                    : message);
            progressBar.setVisibility(View.VISIBLE);
            percentView.setVisibility(View.VISIBLE);
            updateProgress(progress);
        } catch (Exception ignored) {}
    }

    public void updateProgress(int progress) {
        try {
            int p = progress;
            if (p < 5) p = 5;
            if (p > 100) p = 100;
            progressBar.setProgress(p);
            percentView.setText(p + "%");

            if (p >= 85) {
                subtitleView.setText("Menyiapkan aplikasi...");
            } else if (p >= 45) {
                subtitleView.setText("Memuat data Transiva...");
            }
        } catch (Exception ignored) {}
    }

    public void showError(String title, String subtitle) {
        try {
            setAlpha(1f);
            setVisibility(View.VISIBLE);
            titleView.setText(title == null || title.trim().isEmpty()
                    ? "Koneksi terputus"
                    : title);
            subtitleView.setText(subtitle == null || subtitle.trim().isEmpty()
                    ? "Periksa koneksi internet Anda"
                    : subtitle);
            progressBar.setVisibility(View.GONE);
            percentView.setVisibility(View.GONE);
        } catch (Exception ignored) {}
    }

    public void hideSmooth() {
        try {
            animate()
                    .alpha(0f)
                    .setDuration(220)
                    .withEndAction(() -> {
                        try {
                            setVisibility(View.GONE);
                            setAlpha(1f);
                        } catch (Exception ignored) {}
                    })
                    .start();
        } catch (Exception e) {
            setVisibility(View.GONE);
            setAlpha(1f);
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
