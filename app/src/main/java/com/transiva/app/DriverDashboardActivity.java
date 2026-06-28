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
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
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

    private SessionManager sessionManager;

    private LinearLayout root;
    private LinearLayout activeBox;
    private LinearLayout offerBox;

    private TextView nameText;
    private TextView balanceText;
    private TextView statusText;
    private TextView gpsText;

    private String username = "";
    private boolean loading = false;
    private boolean polling = false;

    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            if (!polling) return;
            loadDashboard(false);
            mainHandler.postDelayed(this, 5000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            getWindow().setStatusBarColor(Color.WHITE);
            getWindow().setNavigationBarColor(Color.WHITE);
            if (Build.VERSION.SDK_INT >= 23) {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        } catch (Exception ignored) {}

        sessionManager = new SessionManager(this);
        username = safe(sessionManager.getUsername());

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

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(22), dp(16), dp(28));
        root.setBackgroundColor(Color.parseColor("#F3F8FF"));

        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        TextView title = text("Transiva Driver", 26, "#0B3A78", true);
        root.addView(title);

        nameText = text("Memuat akun driver...", 14, "#64748B", false);
        addWithMargin(nameText, 0, dp(4), 0, dp(12));

        balanceText = cardText("💳 Saldo Driver\nMemuat saldo...");
        root.addView(balanceText);

        gpsText = cardText("📍 GPS Driver\nMenyiapkan lokasi...");
        root.addView(gpsText);

        LinearLayout shortcut = new LinearLayout(this);
        shortcut.setOrientation(LinearLayout.HORIZONTAL);
        addWithMargin(shortcut, 0, 0, 0, dp(12));

        Button refresh = smallButton("Refresh");
        Button history = smallButton("Riwayat");
        shortcut.addView(refresh, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        hLp.setMargins(dp(10), 0, 0, 0);
        shortcut.addView(history, hLp);

        refresh.setOnClickListener(v -> loadDashboard(true));
        history.setOnClickListener(v -> openWeb(HOME_URL + "#driver_history"));

        statusText = text("", 13, "#64748B", false);
        addWithMargin(statusText, 0, 0, 0, dp(6));

        root.addView(sectionTitle("Order Aktif"));
        activeBox = new LinearLayout(this);
        activeBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(activeBox);

        root.addView(sectionTitle("Tawaran Order Terbaru"));
        offerBox = new LinearLayout(this);
        offerBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(offerBox);

        Button web = outlineButton("Buka Dashboard WebView");
        web.setOnClickListener(v -> openWeb(HOME_URL));
        addWithMargin(web, 0, dp(8), 0, dp(10));

        Button logout = dangerButton("Logout");
        logout.setOnClickListener(v -> logout());
        addWithMargin(logout, 0, 0, 0, 0);
    }

    private void loadDashboard(boolean showText) {
        if (loading) return;

        if (sessionManager == null) sessionManager = new SessionManager(this);
        username = safe(sessionManager.getUsername());
        String role = safe(sessionManager.getRole());

        if (username.length() == 0) {
            nameText.setText("Driver belum login");
            statusText.setText("Silakan login ulang.");
            return;
        }

        nameText.setText("Halo, " + username + " • " + (role.length() == 0 ? "driver" : role));
        gpsText.setText("📍 GPS Driver\nService lokasi aktif di background");

        if (showText) statusText.setText("Memuat order dari orders dan pickup_orders...");
        loading = true;

        new Thread(() -> {
            String balanceJson = "{}";
            String unifiedJson = "{}";

            try {
                balanceJson = get(BASE + "getBalance.php?username=" + enc(username));
            } catch (Exception ignored) {}

            try {
                unifiedJson = get(BASE + "driver_get_unified_orders.php?driver=" + enc(username) + "&_=" + System.currentTimeMillis());
            } catch (Exception ignored) {}

            final String b = balanceJson;
            final String u = unifiedJson;

            runOnUiThread(() -> {
                loading = false;
                showBalance(b);
                renderUnifiedOrders(u);
                statusText.setText("Dashboard driver siap • auto refresh 5 detik");
            });
        }).start();
    }

    private void showBalance(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            int balance = obj.optInt("balance", 0);
            balanceText.setText("💳 Saldo Driver\n" + rupiah(balance));
        } catch (Exception e) {
            balanceText.setText("💳 Saldo Driver\nSaldo belum terbaca");
        }
    }

    private void renderUnifiedOrders(String json) {
        activeBox.removeAllViews();
        offerBox.removeAllViews();

        try {
            JSONObject obj = new JSONObject(json);

            JSONArray active = obj.optJSONArray("active_orders");
            JSONArray offers = obj.optJSONArray("offer_orders");

            if (active == null || active.length() == 0) {
                activeBox.addView(cardText("Belum ada order aktif."));
            } else {
                for (int i = 0; i < active.length(); i++) {
                    JSONObject order = active.optJSONObject(i);
                    if (order != null) activeBox.addView(orderCard(order, true));
                }
            }

            if (offers == null || offers.length() == 0) {
                offerBox.addView(cardText("Belum ada tawaran order."));
            } else {
                int max = Math.min(offers.length(), 10);
                for (int i = 0; i < max; i++) {
                    JSONObject order = offers.optJSONObject(i);
                    if (order != null) offerBox.addView(orderCard(order, false));
                }
            }
        } catch (Exception e) {
            activeBox.addView(cardText("Gagal membaca order aktif."));
            offerBox.addView(cardText("Gagal membaca tawaran order. Pastikan PHP unified sudah diupload."));
        }
    }

    private View orderCard(JSONObject order, boolean active) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(22), 1));
        card.setElevation(dp(2));

        String source = firstNonEmpty(order.optString("source"), "orders");
        String sourceLabel = source.equals("pickup_orders") ? "📦 TransPickup" : "🛵 Order Reguler";
        String id = firstNonEmpty(order.optString("id"), order.optString("order_id"), "-");
        String status = firstNonEmpty(order.optString("status"), "-");
        String service = firstNonEmpty(order.optString("service_name"), order.optString("order_type"), order.optString("type"), sourceLabel);
        String pickup = firstNonEmpty(order.optString("pickup_address"), order.optString("pickup"), "-");
        String destination = firstNonEmpty(order.optString("destination_address"), order.optString("destination"), "-");
        String customer = firstNonEmpty(order.optString("customer_name"), order.optString("username"), order.optString("user_name"), "Customer");
        int price = order.optInt("driver_price", order.optInt("price", order.optInt("fare", order.optInt("total", 0))));

        TextView title = text((active ? "🚦 Order Aktif" : "🔔 Tawaran Baru") + " • " + sourceLabel, 15, "#0B3A78", true);
        card.addView(title);

        TextView body = text(
                "Order #" + id +
                        "\nCustomer: " + customer +
                        "\nLayanan: " + service +
                        "\nStatus: " + status +
                        "\nJemput: " + pickup +
                        "\nTujuan: " + destination +
                        "\nPendapatan: " + rupiah(price),
                13, "#334155", false
        );
        body.setPadding(0, dp(8), 0, dp(10));
        card.addView(body);

        if (!active) {
            Button take = primaryButton("Terima Order");
            take.setOnClickListener(v -> confirmTake(order));
            card.addView(take, new LinearLayout.LayoutParams(-1, dp(48)));
        } else {
            addStatusButtons(card, order, source, status);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(12));
        card.setLayoutParams(lp);

        return card;
    }

    private void addStatusButtons(LinearLayout card, JSONObject order, String source, String status) {
        String s = safe(status).toLowerCase(Locale.US);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));

        Button map = outlineButton("🗺️ Buka Map / Perjalanan");
        map.setOnClickListener(v -> openTrip(order));
        row.addView(map, new LinearLayout.LayoutParams(-1, dp(48)));

        String nextStatus = "";
        String label = "";

        if (s.equals("taken") || s.equals("driver_accepted")) {
            nextStatus = "arrived_pickup";
            label = "Saya Sudah Tiba di Pickup";
        } else if (s.equals("arrived_pickup")) {
            nextStatus = source.equals("pickup_orders") ? "picked_up" : "on_delivery";
            label = source.equals("pickup_orders") ? "Paket Sudah Diambil" : "Mulai Antar";
        } else if (s.equals("picked_up")) {
            nextStatus = "on_delivery";
            label = "Antar ke Tujuan";
        } else if (s.equals("on_delivery")) {
            nextStatus = "arrived_delivery";
            label = "Sudah Sampai Tujuan";
        } else if (s.equals("arrived_delivery")) {
            if (source.equals("pickup_orders")) {
                nextStatus = "completed";
                label = "Selesaikan dengan OTP";
            } else {
                nextStatus = "finished";
                label = "Selesaikan Order";
            }
        }

        if (label.length() > 0) {
            Button next = primaryButton(label);
            String finalNextStatus = nextStatus;
            next.setOnClickListener(v -> {
                if (source.equals("pickup_orders") && finalNextStatus.equals("completed")) {
                    askOtpAndUpdate(order, finalNextStatus);
                } else {
                    updateStatus(order, finalNextStatus, "");
                }
            });
            LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(-1, dp(48));
            nLp.setMargins(0, dp(8), 0, 0);
            row.addView(next, nLp);
        }
    }

    private void confirmTake(JSONObject order) {
        String source = firstNonEmpty(order.optString("source"), "orders");
        String id = firstNonEmpty(order.optString("id"), order.optString("order_id"), "");
        if (id.length() == 0) {
            showInfo("Gagal", "Order ID tidak ditemukan.");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Terima Order")
                .setMessage("Ambil order ini sekarang?")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Terima", (d, w) -> takeOrder(source, id))
                .show();
    }

    private void takeOrder(String source, String id) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("driver", username);
            payload.put("source", source);
            payload.put("id", id);

            postAction("driver_take_unified_order.php", payload);
        } catch (Exception e) {
            showInfo("Gagal", "Data order tidak lengkap.");
        }
    }

    private void askOtpAndUpdate(JSONObject order, String nextStatus) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Masukkan OTP penerima");
        input.setSingleLine(true);
        input.setTextColor(Color.parseColor("#0F172A"));
        input.setHintTextColor(Color.parseColor("#94A3B8"));
        input.setPadding(dp(14), dp(8), dp(14), dp(8));

        new AlertDialog.Builder(this)
                .setTitle("OTP Penerima")
                .setMessage("Masukkan OTP dari penerima untuk menyelesaikan pickup.")
                .setView(input)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Selesai", (d, w) -> updateStatus(order, nextStatus, input.getText().toString().trim()))
                .show();
    }

    private void updateStatus(JSONObject order, String nextStatus, String otp) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("driver", username);
            payload.put("source", firstNonEmpty(order.optString("source"), "orders"));
            payload.put("id", firstNonEmpty(order.optString("id"), order.optString("order_id"), ""));
            payload.put("status", nextStatus);
            payload.put("otp", firstNonEmpty(otp, ""));

            postAction("driver_update_unified_status.php", payload);
        } catch (Exception e) {
            showInfo("Gagal", "Status tidak valid.");
        }
    }

    private void postAction(String endpoint, JSONObject payload) {
        statusText.setText("Memproses...");
        new Thread(() -> {
            boolean ok = false;
            String msg = "Gagal memproses order.";
            try {
                JSONObject res = post(BASE + endpoint, payload);
                ok = res.optBoolean("success", false);
                msg = firstNonEmpty(res.optString("message"), ok ? "Berhasil" : "Gagal");
            } catch (Exception e) {
                msg = "Koneksi gagal ke server.";
            }

            boolean finalOk = ok;
            String finalMsg = msg;
            runOnUiThread(() -> {
                Toast.makeText(this, finalMsg, Toast.LENGTH_SHORT).show();
                if (!finalOk) showInfo("Info", finalMsg);
                loadDashboard(true);
            });
        }).start();
    }

    private void openTrip(JSONObject order) {
        try {
            String source = firstNonEmpty(order.optString("source"), "orders");
            String id = firstNonEmpty(order.optString("id"), order.optString("order_id"), "");

            String pickupLat = firstNonEmpty(order.optString("pickup_lat"), order.optString("user_lat"), "");
            String pickupLng = firstNonEmpty(order.optString("pickup_lng"), order.optString("user_lng"), "");
            String deliveryLat = firstNonEmpty(order.optString("delivery_lat"), order.optString("destination_lat"), "");
            String deliveryLng = firstNonEmpty(order.optString("delivery_lng"), order.optString("destination_lng"), "");

            try {
                Class<?> tripClass = Class.forName("com.transiva.app.DriverTripActivity");
                Intent intent = new Intent(this, tripClass);
                intent.putExtra("source", source);
                intent.putExtra("order_id", id);
                intent.putExtra("pickup_lat", pickupLat);
                intent.putExtra("pickup_lng", pickupLng);
                intent.putExtra("delivery_lat", deliveryLat);
                intent.putExtra("delivery_lng", deliveryLng);
                startActivity(intent);
            } catch (Exception missingNativeTrip) {
                openWeb(HOME_URL + "#driver_trip");
            }
        } catch (Exception e) {
            openWeb(HOME_URL + "#driver_trip");
        }
    }

    private void startNativeServices() {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }, 90);
                    return;
                }
            }

            Intent location = new Intent(this, LocationService.class);
            location.setAction(LocationService.ACTION_START);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(location);
            else startService(location);

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
        os.write((payload == null ? "{}" : payload.toString()).getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
        String body = readStream(is);
        conn.disconnect();

        if (body.length() == 0) return new JSONObject();
        return new JSONObject(body);
    }

    private String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString().trim();
    }

    private void openWeb(String url) {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("url", url);
        startActivity(i);
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

    private TextView sectionTitle(String value) {
        TextView t = text(value, 18, "#0B3A78", true);
        t.setPadding(0, dp(16), 0, dp(6));
        return t;
    }

    private TextView cardText(String value) {
        TextView t = text(value, 14, "#111827", false);
        t.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(22), 1));
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(12));
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

    private Button dangerButton(String value) {
        Button b = primaryButton(value);
        b.setBackground(roundGradient("#EF4444", "#DC2626", dp(18)));
        return b;
    }

    private Button smallButton(String value) {
        Button b = outlineButton(value);
        b.setTextSize(13);
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
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(color));
        gd.setCornerRadius(radius);
        return gd;
    }

    private GradientDrawable roundStroke(String color, String stroke, int radius, int width) {
        GradientDrawable gd = round(color, radius);
        gd.setStroke(dp(width), Color.parseColor(stroke));
        return gd;
    }

    private GradientDrawable roundGradient(String start, String end, int radius) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor(start), Color.parseColor(end)}
        );
        gd.setCornerRadius(radius);
        return gd;
    }

    private void addWithMargin(View view, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(l, t, r, b);
        root.addView(view, lp);
    }

    private String enc(String value) {
        try { return URLEncoder.encode(value == null ? "" : value, "UTF-8"); }
        catch (Exception e) { return ""; }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && v.trim().length() > 0 && !"null".equalsIgnoreCase(v.trim())) return v.trim();
        }
        return "";
    }

    private String rupiah(int value) {
        return "Rp " + NumberFormat.getNumberInstance(new Locale("id", "ID")).format(value);
    }

    private void showInfo(String title, String message) {
        try {
            new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
        } catch (Exception ignored) {}
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
