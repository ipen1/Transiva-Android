package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CustomerHistoryActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String PREF_NAME = "transiva";
    private static final int TIMEOUT_MS = 20000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FrameLayout page;
    private LinearLayout root;
    private LinearLayout listBox;
    private ProgressBar progressBar;

    private int userId = 0;
    private String username = "User";
    private String filter = "all";
    private final List<JSONObject> orders = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setStatusBarColor(Color.WHITE);
            getWindow().setNavigationBarColor(Color.WHITE);
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        } catch (Exception ignored) {}

        loadSession();
        buildBase();
        renderPage();
        loadHistory();
    }

    private void loadSession() {
        try {
            SessionManager session = new SessionManager(this);
            if (session.isLoggedIn()) {
                username = firstNonEmpty(session.getUsername(), session.getName(), "User");
                try { userId = Integer.parseInt(firstNonEmpty(session.getId(), session.getUserId(), "0")); } catch (Exception ignored) {}
                return;
            }
        } catch (Exception ignored) {}

        try {
            SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
            username = firstNonEmpty(sp.getString("username", ""), sp.getString("player_username", ""), "User");
            userId = sp.getInt("id", sp.getInt("user_id", 0));
        } catch (Exception ignored) {}
    }

    private void buildBase() {
        page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F7FAFF"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(52), dp(52));
        lp.gravity = Gravity.CENTER;
        page.addView(progressBar, lp);

        setContentView(page);
    }

    private void renderPage() {
        root.removeAllViews();
        buildTopBar();
        buildSummaryCard();
        buildFilterTabs();

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(listBox, new LinearLayout.LayoutParams(-1, -2));
        renderList();
    }

    private void buildTopBar() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(16));

        TextView back = text("‹", 34, "#0B3A78", true);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round("#FFFFFF", dp(18)));
        back.setOnClickListener(v -> finish());
        row.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, 0, 0);
        col.addView(text("Riwayat Customer", 23, "#0B3A78", true));
        col.addView(text("Semua pesanan dan transaksi layanan Transiva", 12, "#64748B", false));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

        Button refresh = smallButton("↻", "#FFFFFF", "#0B7CFF");
        refresh.setOnClickListener(v -> loadHistory());
        row.addView(refresh, new LinearLayout.LayoutParams(dp(44), dp(44)));
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void buildSummaryCard() {
        LinearLayout card = card();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundGradient("#FFFFFF", "#EEF7FF", dp(22)));
        card.addView(text("Halo, " + username, 17, "#0B3A78", true));
        TextView sub = text("Pantau riwayat order selesai, dibatalkan, atau masih aktif dari satu halaman native.", 12, "#64748B", false);
        sub.setPadding(0, dp(6), 0, 0);
        card.addView(sub);
        addWithMargin(card, 0, 0, 0, dp(14));
    }

    private void buildFilterTabs() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, dp(14));

        Button all = filterButton("Semua", "all".equals(filter));
        Button active = filterButton("Aktif", "active".equals(filter));
        Button done = filterButton("Selesai", "done".equals(filter));
        Button cancel = filterButton("Batal", "cancel".equals(filter));

        all.setOnClickListener(v -> { filter = "all"; renderPage(); });
        active.setOnClickListener(v -> { filter = "active"; renderPage(); });
        done.setOnClickListener(v -> { filter = "done"; renderPage(); });
        cancel.setOnClickListener(v -> { filter = "cancel"; renderPage(); });

        row.addView(all, new LinearLayout.LayoutParams(0, dp(44), 1));
        addGap(row);
        row.addView(active, new LinearLayout.LayoutParams(0, dp(44), 1));
        addGap(row);
        row.addView(done, new LinearLayout.LayoutParams(0, dp(44), 1));
        addGap(row);
        row.addView(cancel, new LinearLayout.LayoutParams(0, dp(44), 1));
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void renderList() {
        if (listBox == null) return;
        listBox.removeAllViews();

        if (orders.isEmpty()) {
            addStatusTo(listBox, "Riwayat masih kosong atau sedang dimuat...");
            return;
        }

        int shown = 0;
        for (JSONObject order : orders) {
            if (!passesFilter(order)) continue;
            addOrderCard(order);
            shown++;
        }

        if (shown == 0) addStatusTo(listBox, "Tidak ada riwayat untuk filter ini.");
    }

    private boolean passesFilter(JSONObject order) {
        String status = norm(order.optString("status"));
        if ("all".equals(filter)) return true;
        if ("active".equals(filter)) return !isFinished(status) && !isCanceled(status);
        if ("done".equals(filter)) return isFinished(status);
        if ("cancel".equals(filter)) return isCanceled(status);
        return true;
    }

    private void addOrderCard(JSONObject order) {
        LinearLayout card = card();
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        String id = firstNonEmpty(order.optString("order_id"), order.optString("id"), "-");
        titleCol.addView(text(serviceIcon(order) + " " + serviceName(order), 16, "#0F172A", true));
        titleCol.addView(text("Order #" + id, 12, "#64748B", false));
        top.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));

        TextView badge = text(statusLabel(order.optString("status")), 11, statusColor(order.optString("status")), true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        badge.setBackground(roundStroke(statusBg(order.optString("status")), statusStroke(order.optString("status")), dp(18), 1));
        top.addView(badge, new LinearLayout.LayoutParams(-2, -2));

        addInfo(card, "Pickup", firstNonEmpty(order.optString("pickup_address"), order.optString("from_address"), order.optString("restaurant_name"), order.optString("wisata_name"), "-"));
        addInfo(card, "Tujuan", firstNonEmpty(order.optString("delivery_address"), order.optString("to_address"), order.optString("destination"), order.optString("address"), "-"));
        addInfo(card, "Driver", firstNonEmpty(order.optString("driver"), order.optString("driver_username"), "Belum ada"));

        double price = orderPrice(order);
        if (price > 0) {
            TextView total = text(rupiah(price), 19, "#0B7CFF", true);
            total.setPadding(0, dp(8), 0, 0);
            card.addView(total);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(12), 0, 0);
        Button detail = outlineButton("Detail");
        Button repeat = primaryButton("Pesan Lagi");
        detail.setOnClickListener(v -> showOrderDetail(order));
        repeat.setOnClickListener(v -> openRepeat(order));
        actions.addView(detail, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, dp(46), 1);
        rlp.setMargins(dp(10), 0, 0, 0);
        actions.addView(repeat, rlp);
        card.addView(actions);

        addWithMarginTo(listBox, card, 0, 0, 0, dp(12));
    }

    private void addInfo(LinearLayout parent, String label, String value) {
        if (value == null || value.trim().length() == 0 || "-".equals(value.trim())) return;
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(7), 0, 0);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(label, 12, "#64748B", false), new LinearLayout.LayoutParams(dp(72), -2));
        TextView v = text(value, 13, "#0F172A", true);
        v.setMaxLines(2);
        row.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
        parent.addView(row);
    }

    private void showOrderDetail(JSONObject o) {
        String msg = "Order ID: " + firstNonEmpty(o.optString("order_id"), o.optString("id"), "-")
                + "\nLayanan: " + serviceName(o)
                + "\nStatus: " + statusLabel(o.optString("status"))
                + "\nPickup: " + firstNonEmpty(o.optString("pickup_address"), o.optString("restaurant_name"), "-")
                + "\nTujuan: " + firstNonEmpty(o.optString("delivery_address"), o.optString("destination"), "-")
                + "\nDriver: " + firstNonEmpty(o.optString("driver"), o.optString("driver_username"), "-")
                + "\nTotal: " + rupiah(orderPrice(o));
        showInfo("Detail Riwayat", msg);
    }

    private void openRepeat(JSONObject o) {
        String type = norm(firstNonEmpty(o.optString("order_type"), o.optString("service_type"), o.optString("service")));
        try {
            if (type.contains("food")) { startActivity(new Intent(this, TransFoodActivity.class)); return; }
            if (type.contains("wisata") || type.contains("tour")) { startActivity(new Intent(this, TranstourActivity.class)); return; }
            if (type.contains("laundry")) { startActivity(new Intent(this, TransLaundryActivity.class)); return; }
            if (type.contains("car") || type.contains("mobil") || type.contains("transcar")) { startActivity(new Intent(this, PassengerCarActivity.class)); return; }
            startActivity(new Intent(this, TransRideActivity.class));
        } catch (Exception e) {
            showInfo("Pesan Lagi", "Halaman layanan belum tersedia di native app ini.");
        }
    }

    private void loadHistory() {
        if (userId <= 0) {
            addStatusTo(listBox, "User ID tidak ditemukan. Silakan login ulang.");
            return;
        }
        setLoading(true);
        new Thread(() -> {
            try {
                JSONObject res = getJson(BASE_URL + "server/get_user_orders.php?user_id=" + Uri.encode(String.valueOf(userId)) + "&_=" + System.currentTimeMillis());
                JSONArray arr = res.optJSONArray("orders");
                orders.clear();
                if (res.optBoolean("success", false) && arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o != null) orders.add(o);
                    }
                }
                mainHandler.post(() -> { setLoading(false); renderPage(); });
            } catch (Exception e) {
                mainHandler.post(() -> { setLoading(false); if (listBox != null) { listBox.removeAllViews(); addStatusTo(listBox, "Gagal memuat riwayat customer."); } showInfo("Gagal", "Koneksi gagal memuat riwayat."); });
            }
        }).start();
    }

    private String serviceName(JSONObject o) {
        String type = norm(firstNonEmpty(o.optString("order_type"), o.optString("service_type"), o.optString("service"), o.optString("service_name")));
        if (type.contains("food")) return "TransFood";
        if (type.contains("wisata") || type.contains("tour")) return "TransTour";
        if (type.contains("laundry")) return "TransLaundry";
        if (type.contains("car") || type.contains("mobil") || type.contains("transcar")) return "TransCar";
        if (type.contains("ride") || type.contains("bike") || type.contains("kurir") || type.contains("transbike")) return "TransRide";
        return firstNonEmpty(o.optString("service_name"), o.optString("order_type"), "Transiva");
    }

    private String serviceIcon(JSONObject o) {
        String s = serviceName(o).toLowerCase(Locale.US);
        if (s.contains("food")) return "🍔";
        if (s.contains("tour")) return "🏝️";
        if (s.contains("laundry")) return "🧺";
        if (s.contains("car")) return "🚗";
        if (s.contains("ride")) return "🏍️";
        return "📦";
    }

    private double orderPrice(JSONObject o) {
        return o.optDouble("total_price", o.optDouble("total", o.optDouble("food_total", o.optDouble("price", o.optDouble("amount", 0)))));
    }

    private boolean isFinished(String status) {
        status = norm(status);
        return status.equals("finished") || status.equals("finish") || status.equals("completed") || status.equals("complete") || status.equals("done") || status.equals("selesai") || status.equals("claimed");
    }

    private boolean isCanceled(String status) {
        status = norm(status);
        return status.equals("canceled") || status.equals("cancelled") || status.equals("batal") || status.equals("merchant_rejected") || status.equals("rejected");
    }

    private String statusLabel(String status) {
        String s = norm(status);
        if (s.equals("pending")) return "Menunggu";
        if (s.equals("merchant_accepted")) return "Diterima";
        if (s.equals("taken")) return "Diambil";
        if (s.equals("arrived_pickup")) return "Tiba Pickup";
        if (s.equals("on_delivery")) return "Diantar";
        if (s.equals("arrived_delivery")) return "Tiba Tujuan";
        if (isFinished(s)) return "Selesai";
        if (isCanceled(s)) return "Batal";
        return firstNonEmpty(status, "Status");
    }

    private String statusColor(String status) {
        String s = norm(status);
        if (isFinished(s)) return "#16A34A";
        if (isCanceled(s)) return "#EF4444";
        if (s.equals("pending")) return "#F59E0B";
        return "#0B7CFF";
    }

    private String statusBg(String status) {
        String s = norm(status);
        if (isFinished(s)) return "#ECFDF5";
        if (isCanceled(s)) return "#FFF1F2";
        if (s.equals("pending")) return "#FFFBEB";
        return "#EAF4FF";
    }

    private String statusStroke(String status) {
        String s = norm(status);
        if (isFinished(s)) return "#BBF7D0";
        if (isCanceled(s)) return "#FECACA";
        if (s.equals("pending")) return "#FDE68A";
        return "#B9DBFF";
    }

    private JSONObject getJson(String urlText) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlText).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
            String body = readStream(is).trim();
            if (body.length() == 0) return new JSONObject();
            return new JSONObject(body);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }

    private void buildTopSpacer() {}

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackground(roundStroke("#FFFFFF", "#E2ECF8", dp(22), 1));
        v.setElevation(dp(2));
        return v;
    }

    private TextView text(String s, int sp, String color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.parseColor(color));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button primaryButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(roundGradient("#086BFF", "#2EA2FF", dp(18)));
        return b;
    }

    private Button outlineButton(String s) {
        Button b = primaryButton(s);
        b.setTextColor(Color.parseColor("#0B7CFF"));
        b.setBackground(roundStroke("#FFFFFF", "#B9DBFF", dp(18), 1));
        return b;
    }

    private Button smallButton(String s, String bg, String fg) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(18);
        b.setTextColor(Color.parseColor(fg));
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(roundStroke(bg, "#D7E6F8", dp(18), 1));
        return b;
    }

    private Button filterButton(String s, boolean active) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.parseColor(active ? "#FFFFFF" : "#0B3A78"));
        b.setBackground(roundStroke(active ? "#0B7CFF" : "#FFFFFF", active ? "#0B7CFF" : "#D7E6F8", dp(16), 1));
        return b;
    }

    private GradientDrawable round(String color, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(color)); g.setCornerRadius(radius); return g; }
    private GradientDrawable roundStroke(String color, String stroke, int radius, int sw) { GradientDrawable g = round(color, radius); g.setStroke(dp(sw), Color.parseColor(stroke)); return g; }
    private GradientDrawable roundGradient(String c1, String c2, int radius) { GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor(c1), Color.parseColor(c2)}); g.setCornerRadius(radius); return g; }

    private void addWithMargin(View v, int l, int t, int r, int b) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(l,t,r,b); root.addView(v, lp); }
    private void addWithMarginTo(LinearLayout parent, View v, int l, int t, int r, int b) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(l,t,r,b); parent.addView(v, lp); }
    private void addStatusTo(LinearLayout parent, String msg) { if (parent == null) return; TextView t = text(msg, 14, "#64748B", false); t.setGravity(Gravity.CENTER); t.setPadding(dp(16), dp(22), dp(16), dp(22)); t.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(20), 1)); addWithMarginTo(parent, t, 0, 0, 0, dp(12)); }
    private void addGap(LinearLayout row) { View gap = new View(this); row.addView(gap, new LinearLayout.LayoutParams(dp(7), 1)); }
    private void setLoading(boolean b) { if (progressBar != null) progressBar.setVisibility(b ? View.VISIBLE : View.GONE); }
    private void showInfo(String title, String msg) { try { new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show(); } catch (Exception ignored) {} }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private String rupiah(double v) { return "Rp " + NumberFormat.getNumberInstance(new Locale("id", "ID")).format((long) v); }
    private String norm(String s) { return firstNonEmpty(s, "").toLowerCase(Locale.US).trim(); }
    private String firstNonEmpty(String... values) { if (values == null) return ""; for (String s : values) if (s != null && s.trim().length() > 0 && !"null".equalsIgnoreCase(s.trim())) return s.trim(); return ""; }
}
