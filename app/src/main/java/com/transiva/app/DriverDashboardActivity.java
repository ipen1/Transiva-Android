package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;

public class DriverDashboardActivity extends Activity {

    private static final String HOME_URL = "https://transiva.my.id/?app=1";
    private static final String BASE = "https://transiva.my.id/server/";
    private static final int TIMEOUT_MS = 15000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SessionManager sessionManager;

    private FrameLayout page;
    private LinearLayout root;
    private LinearLayout activeBox;
    private LinearLayout offerBox;
    private TextView nameText;
    private TextView roleBadge;
    private TextView balanceText;
    private TextView statusText;
    private ProgressBar progressBar;

    private String username = "";
    private String role = "driver";
    private boolean polling = false;
    private boolean loading = false;

    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            if (!polling) return;
            loadDashboard(false);
            mainHandler.postDelayed(this, 5000);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setStatusBarColor(Color.WHITE);
            getWindow().setNavigationBarColor(Color.WHITE);
            if (Build.VERSION.SDK_INT >= 23) {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        } catch (Exception ignored) {}

        sessionManager = new SessionManager(this);
        loadSession();
        buildUi();
        startNativeServices();
        startPolling();
    }

    @Override protected void onResume() {
        super.onResume();
        startNativeServices();
        startPolling();
    }

    @Override protected void onPause() {
        stopPolling();
        super.onPause();
    }

    @Override protected void onDestroy() {
        stopPolling();
        super.onDestroy();
    }

    private void startPolling() {
        polling = true;
        mainHandler.removeCallbacks(pollRunnable);
        loadDashboard(true);
        mainHandler.postDelayed(pollRunnable, 5000);
    }

    private void stopPolling() {
        polling = false;
        mainHandler.removeCallbacks(pollRunnable);
    }

    private void loadSession() {
        try {
            username = firstNonEmpty(sessionManager.getUsername(), "");
            role = firstNonEmpty(sessionManager.getRole(), "driver");
        } catch (Exception ignored) {}
    }

    private void buildUi() {
        page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F3F8FF"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(26), dp(18), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        buildHeader();
        buildWalletCard();
        buildQuickActions();
        buildStatusText();
        buildActiveSection();
        buildOfferSection();

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(52), dp(52));
        plp.gravity = Gravity.CENTER;
        page.addView(progressBar, plp);

        setContentView(page);
    }

    private void buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

        TextView title = text("Transiva Driver", 30, "#0B3A78", true);
        left.addView(title);

        nameText = text("Halo, " + firstNonEmpty(username, "Driver"), 17, "#64748B", false);
        nameText.setPadding(0, dp(6), 0, 0);
        left.addView(nameText);

        roleBadge = text("ONLINE SERVICE", 11, "#0B7CFF", true);
        roleBadge.setGravity(Gravity.CENTER);
        roleBadge.setPadding(dp(10), dp(5), dp(10), dp(5));
        roleBadge.setBackground(roundStroke("#EAF4FF", "#B9DBFF", dp(18), 1));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.setMargins(0, dp(8), 0, 0);
        left.addView(roleBadge, blp);

        TextView avatar = text("D", 24, "#FFFFFF", true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(roundGradient("#086BFF", "#2EA2FF", dp(28)));
        row.addView(avatar, new LinearLayout.LayoutParams(dp(56), dp(56)));
    }

    private void buildWalletCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.setBackground(roundGradient("#086BFF", "#2EA2FF", dp(28)));
        card.setElevation(dp(5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(22), 0, dp(16));
        root.addView(card, lp);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        top.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

        col.addView(text("💳 Saldo Driver", 14, "#EAF4FF", true));
        balanceText = text("Memuat saldo...", 31, "#FFFFFF", true);
        balanceText.setPadding(0, dp(6), 0, 0);
        col.addView(balanceText);
        TextView note = text("Saldo otomatis diperbarui saat transaksi masuk.", 12, "#EAF4FF", false);
        note.setPadding(0, dp(6), 0, 0);
        col.addView(note);

        TextView coin = text("Rp", 20, "#0B7CFF", true);
        coin.setGravity(Gravity.CENTER);
        coin.setBackground(round("#FFFFFF", dp(24)));
        top.addView(coin, new LinearLayout.LayoutParams(dp(50), dp(50)));
    }

    private void buildQuickActions() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(16));
        root.addView(row, lp);

        Button refresh = outlineButton("Refresh");
        refresh.setOnClickListener(v -> loadDashboard(true));
        row.addView(refresh, new LinearLayout.LayoutParams(0, dp(54), 1));

        Button history = outlineButton("Riwayat");
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(0, dp(54), 1);
        hlp.setMargins(dp(10), 0, 0, 0);
        history.setOnClickListener(v -> openWeb(HOME_URL + "#driver_history"));
        row.addView(history, hlp);

        Button profile = primaryButton("Profil");
        LinearLayout.LayoutParams prlp = new LinearLayout.LayoutParams(0, dp(54), 1);
        prlp.setMargins(dp(10), 0, 0, 0);
        profile.setOnClickListener(v -> openDriverProfile());
        row.addView(profile, prlp);
    }

    private void buildStatusText() {
        statusText = text("Dashboard driver siap • auto refresh 5 detik", 14, "#64748B", false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(16));
        root.addView(statusText, lp);
    }

    private void buildActiveSection() {
        root.addView(sectionTitle("Order Aktif"));
        activeBox = new LinearLayout(this);
        activeBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(activeBox, new LinearLayout.LayoutParams(-1, -2));
    }

    private void buildOfferSection() {
        root.addView(sectionTitle("Tawaran Order Terbaru"));
        offerBox = new LinearLayout(this);
        offerBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(offerBox, new LinearLayout.LayoutParams(-1, -2));
    }

    private void loadDashboard(boolean showLoading) {
        if (loading) return;
        if (username.length() == 0) loadSession();
        if (username.length() == 0) {
            statusText.setText("Driver belum login. Silakan login ulang.");
            return;
        }

        loading = true;
        if (showLoading && progressBar != null) progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            String balanceJson = "";
            String activeJson = "";
            String offerJson = "";
            try { balanceJson = get(BASE + "getBalance.php?username=" + enc(username) + "&v=" + System.currentTimeMillis()); } catch (Exception ignored) {}
            try { activeJson = get(BASE + "driver_get_unified_orders.php?driver=" + enc(username) + "&mode=active&v=" + System.currentTimeMillis()); } catch (Exception ignored) {}
            try { offerJson = get(BASE + "driver_get_unified_orders.php?driver=" + enc(username) + "&mode=offers&v=" + System.currentTimeMillis()); } catch (Exception ignored) {}

            final String b = balanceJson;
            final String a = activeJson;
            final String o = offerJson;
            mainHandler.post(() -> {
                loading = false;
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                showBalance(b);
                showActiveOrder(a);
                showOfferOrders(o);
                statusText.setText("Dashboard driver siap • auto refresh 5 detik");
            });
        }).start();
    }

    private void showBalance(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            double balance = obj.optDouble("balance", 0);
            balanceText.setText(rupiah(balance));
        } catch (Exception e) {
            balanceText.setText("Saldo belum terbaca");
        }
    }

    private void showActiveOrder(String json) {
        activeBox.removeAllViews();
        try {
            JSONObject obj = new JSONObject(json);
            JSONObject order = obj.optJSONObject("order");
            if (order == null) {
                JSONArray arr = obj.optJSONArray("orders");
                if (arr != null && arr.length() > 0) order = arr.optJSONObject(0);
            }
            if (order == null) {
                activeBox.addView(infoCard("Belum ada order aktif."));
                return;
            }
            activeBox.addView(orderCard(order, true));
        } catch (Exception e) {
            activeBox.addView(infoCard("Belum ada order aktif."));
        }
    }

    private void showOfferOrders(String json) {
        offerBox.removeAllViews();
        try {
            JSONObject obj = new JSONObject(json);
            JSONArray arr = obj.optJSONArray("orders");
            if (arr == null || arr.length() == 0) {
                offerBox.addView(infoCard("Belum ada tawaran order."));
                return;
            }
            int max = Math.min(arr.length(), 8);
            for (int i = 0; i < max; i++) {
                JSONObject order = arr.optJSONObject(i);
                if (order != null) offerBox.addView(orderCard(order, false));
            }
        } catch (Exception e) {
            offerBox.addView(infoCard("Belum ada tawaran order."));
        }
    }

    private LinearLayout orderCard(JSONObject order, boolean active) {
        LinearLayout card = whiteCard();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        String id = firstNonEmpty(order.optString("id"), order.optString("order_id"), "-");
        String type = firstNonEmpty(order.optString("order_type"), order.optString("type"), order.optString("service_type"), "order");
        String status = firstNonEmpty(order.optString("status"), "-");
        String pickup = firstNonEmpty(order.optString("pickup_address"), order.optString("pickup"), "-");
        String destination = firstNonEmpty(order.optString("delivery_address"), order.optString("destination_address"), order.optString("destination"), "-");
        double price = order.optDouble("price", order.optDouble("fare", order.optDouble("total", 0)));
        String source = firstNonEmpty(order.optString("source"), order.optString("table"), "orders");

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top, new LinearLayout.LayoutParams(-1, -2));

        TextView title = text((active ? "🚦 Order Aktif" : serviceIcon(type, source) + " Tawaran Baru"), 16, "#0B3A78", true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView priceView = text(rupiah(price), 15, "#0B7CFF", true);
        top.addView(priceView);

        TextView detail = text("Order #" + id + "\nLayanan: " + serviceName(type, source) + "\nStatus: " + status + "\n\n📍 Jemput:\n" + pickup + "\n\n🏁 Tujuan:\n" + destination, 14, "#111827", false);
        detail.setPadding(0, dp(10), 0, dp(12));
        card.addView(detail);

        if (active) {
            Button trip = primaryButton("Lanjutkan Trip");
            trip.setOnClickListener(v -> openDriverTrip(order));
            card.addView(trip, new LinearLayout.LayoutParams(-1, dp(50)));
        } else {
            Button take = primaryButton("Ambil Order");
            take.setOnClickListener(v -> takeOrder(order));
            card.addView(take, new LinearLayout.LayoutParams(-1, dp(50)));
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(14));
        card.setLayoutParams(lp);
        return card;
    }

    private String serviceIcon(String type, String source) {
        String t = (type + " " + source).toLowerCase(Locale.US);
        if (t.contains("pickup")) return "📦";
        if (t.contains("food")) return "🍔";
        if (t.contains("car") || t.contains("mobil")) return "🚗";
        return "🛵";
    }

    private String serviceName(String type, String source) {
        String t = (type + " " + source).toLowerCase(Locale.US);
        if (t.contains("pickup")) return "TransPickup";
        if (t.contains("food")) return "TransFood";
        if (t.contains("car") || t.contains("mobil")) return "TransCar";
        return firstNonEmpty(type, "TransRide");
    }

    private void takeOrder(JSONObject order) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("id", firstNonEmpty(order.optString("id"), order.optString("order_id")));
            payload.put("order_id", firstNonEmpty(order.optString("order_id"), order.optString("id")));
            payload.put("driver", username);
            payload.put("source", firstNonEmpty(order.optString("source"), order.optString("table"), "orders"));
            payload.put("type", firstNonEmpty(order.optString("type"), order.optString("order_type"), "orders"));

            setLoading(true);
            new Thread(() -> {
                try {
                    JSONObject res = post(BASE + "driver_take_unified_order.php", payload);
                    boolean ok = res.optBoolean("success", false);
                    String msg = firstNonEmpty(res.optString("message"), ok ? "Order berhasil diambil" : "Gagal mengambil order");
                    JSONObject taken = res.optJSONObject("order");
                    mainHandler.post(() -> {
                        setLoading(false);
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                        if (ok) {
                            if (taken != null) openDriverTrip(taken);
                            else loadDashboard(true);
                        } else loadDashboard(true);
                    });
                } catch (Exception e) {
                    mainHandler.post(() -> { setLoading(false); showInfo("Gagal", "Koneksi gagal mengambil order."); });
                }
            }).start();
        } catch (Exception e) {
            showInfo("Gagal", "Data order tidak valid.");
        }
    }

    private void openDriverTrip(JSONObject order) {
        Intent intent = new Intent(this, DriverTripActivity.class);
        intent.putExtra("order_json", order.toString());
        intent.putExtra("order_id", firstNonEmpty(order.optString("order_id"), order.optString("id")));
        intent.putExtra("source", firstNonEmpty(order.optString("source"), order.optString("table"), "orders"));
        startActivity(intent);
    }

    private void openDriverProfile() {
        try {
            Intent intent = new Intent(this, ProfileActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            showProfileDialog();
        }
    }

    private void showProfileDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Profil Driver")
                .setMessage("Driver: " + firstNonEmpty(username, "-") + "\nRole: " + firstNonEmpty(role, "driver"))
                .setNegativeButton("Tutup", null)
                .setPositiveButton("Logout", (d, w) -> logout())
                .show();
    }

    private void logout() {
        try { if (sessionManager != null) sessionManager.clearSession(); } catch (Exception ignored) {}
        try {
            Intent stop = new Intent(this, LocationService.class);
            stop.setAction(LocationService.ACTION_STOP);
            startService(stop);
        } catch (Exception ignored) {}
        try { BackgroundSyncService.stop(this); } catch (Exception ignored) {}
        Toast.makeText(this, "Logout berhasil", Toast.LENGTH_SHORT).show();
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void startNativeServices() {
        try {
            if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 90);
                return;
            }
            Intent location = new Intent(this, LocationService.class);
            location.setAction(LocationService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(location); else startService(location);
            try { BackgroundSyncService.start(this); } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    private String get(String link) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(link).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
        String body = readStream(is);
        conn.disconnect();
        return body;
    }

    private JSONObject post(String link, JSONObject payload) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(link).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        OutputStream os = conn.getOutputStream();
        os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        os.flush(); os.close();
        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
        String body = readStream(is);
        conn.disconnect();
        return new JSONObject(body);
    }

    private String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private void openWeb(String url) {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.putExtra("url", url);
            startActivity(i);
        } catch (Exception ignored) {}
    }

    private void setLoading(boolean value) {
        loading = value;
        if (progressBar != null) progressBar.setVisibility(value ? View.VISIBLE : View.GONE);
    }

    private void showInfo(String title, String message) {
        try { new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show(); } catch (Exception ignored) {}
    }

    private TextView sectionTitle(String value) {
        TextView t = text(value, 22, "#0B3A78", true);
        t.setPadding(0, dp(12), 0, dp(8));
        return t;
    }

    private LinearLayout whiteCard() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(24), 1));
        v.setElevation(dp(2));
        return v;
    }

    private TextView infoCard(String value) {
        TextView t = text(value, 16, "#111827", false);
        t.setPadding(dp(18), dp(18), dp(18), dp(18));
        t.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(24), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(16));
        t.setLayoutParams(lp);
        return t;
    }

    private Button primaryButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackground(roundGradient("#086BFF", "#2EA2FF", dp(18)));
        return b;
    }

    private Button outlineButton(String value) {
        Button b = primaryButton(value);
        b.setTextColor(Color.parseColor("#0B7CFF"));
        b.setBackground(roundStroke("#FFFFFF", "#9DCAFF", dp(18), 1));
        return b;
    }

    private TextView text(String value, int sp, String color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(Color.parseColor(color));
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(String color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(color));
        g.setCornerRadius(radius);
        return g;
    }

    private GradientDrawable roundStroke(String color, String stroke, int radius, int width) {
        GradientDrawable g = round(color, radius);
        g.setStroke(dp(width), Color.parseColor(stroke));
        return g;
    }

    private GradientDrawable roundGradient(String start, String end, int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor(start), Color.parseColor(end)});
        g.setCornerRadius(radius);
        return g;
    }

    private String enc(String value) {
        try { return URLEncoder.encode(value == null ? "" : value, "UTF-8"); } catch (Exception e) { return ""; }
    }

    private String rupiah(double value) {
        try { return "Rp " + NumberFormat.getNumberInstance(new Locale("id", "ID")).format((long) value); }
        catch (Exception e) { return "Rp " + Math.round(value); }
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) if (v != null && v.trim().length() > 0 && !"null".equalsIgnoreCase(v.trim())) return v.trim();
        return "";
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
