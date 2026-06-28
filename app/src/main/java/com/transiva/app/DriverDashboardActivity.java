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
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class DriverDashboardActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String SERVER = BASE_URL + "server/";
    private static final int TIMEOUT_MS = 15000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SessionManager sessionManager;

    private LinearLayout root, orderBox, activeBox;
    private TextView nameText, levelText, balanceText, statusText, onlineText;
    private Switch onlineSwitch;
    private ProgressBar progressBar;

    private String username = "";
    private String driverType = "motor";
    private boolean driverOnline = false;
    private boolean loading = false;
    private boolean firstLoadDone = false;
    private final Set<String> notifiedIds = new HashSet<>();

    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            refreshDriverData();
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
        refreshDriverData();
    }

    @Override protected void onResume() {
        super.onResume();
        mainHandler.removeCallbacks(refreshRunnable);
        mainHandler.postDelayed(refreshRunnable, 5000);
        startNativeServices();
        refreshDriverData();
    }

    @Override protected void onPause() {
        mainHandler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    @Override protected void onDestroy() {
        mainHandler.removeCallbacks(refreshRunnable);
        super.onDestroy();
    }

    private void loadSession() {
        try {
            username = safe(sessionManager.getUsername());
            String type = safe(sessionManager.getDriverType());
            if (type.length() == 0) type = safe(sessionManager.getRole());
            driverType = normalizeDriverType(type);
        } catch (Exception ignored) {}

        if (username.length() == 0) username = "Driver";
    }

    private String normalizeDriverType(String value) {
        value = safe(value).toLowerCase(Locale.US);
        if (value.equals("car") || value.equals("mobil") || value.equals("driver_car")) return "car";
        return "motor";
    }

    private void buildUi() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F3F8FF"));

        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(26), dp(18), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        buildHeader();
        buildWalletCard();
        buildActions();
        statusText = text("Dashboard driver siap • auto refresh 5 detik", 14, "#64748B", false);
        add(root, statusText, 0, dp(10), 0, dp(18));

        root.addView(section("Order Aktif"));
        activeBox = new LinearLayout(this);
        activeBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(activeBox);

        root.addView(section("Tawaran Order Terbaru"));
        orderBox = new LinearLayout(this);
        orderBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(orderBox);

        Button profile = outlineButton("👤 Profil Driver");
        profile.setOnClickListener(v -> openProfile());
        add(root, profile, 0, dp(18), 0, 0);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(52), dp(52));
        plp.gravity = Gravity.CENTER;
        page.addView(progressBar, plp);

        setContentView(page);
    }

    private void buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        header.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

        left.addView(text("Transiva Driver", 30, "#0B3A78", true));
        nameText = text("Halo, " + username + " • " + driverLabel(), 17, "#64748B", false);
        add(left, nameText, 0, dp(4), 0, 0);

        levelText = text("Siap menerima order", 12, "#0B7CFF", true);
        levelText.setGravity(Gravity.CENTER);
        levelText.setPadding(dp(12), dp(5), dp(12), dp(5));
        levelText.setBackground(roundStroke("#EAF4FF", "#B9DBFF", dp(20), 1));
        LinearLayout.LayoutParams levelLp = new LinearLayout.LayoutParams(-2, -2);
        levelLp.setMargins(0, dp(8), 0, 0);
        left.addView(levelText, levelLp);

        root.addView(header, new LinearLayout.LayoutParams(-1, -2));
    }

    private void buildWalletCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(roundGradient("#086BFF", "#2EA2FF", dp(28)));
        card.setElevation(dp(3));

        TextView label = text("💳 Saldo Driver", 14, "#EAF4FF", true);
        card.addView(label);

        balanceText = text("Rp 0", 30, "#FFFFFF", true);
        add(card, balanceText, 0, dp(5), 0, 0);

        TextView note = text("Saldo otomatis diperbarui saat transaksi masuk.", 12, "#EAF4FF", false);
        add(card, note, 0, dp(6), 0, 0);

        LinearLayout onlineRow = new LinearLayout(this);
        onlineRow.setGravity(Gravity.CENTER_VERTICAL);
        onlineRow.setPadding(0, dp(14), 0, 0);

        onlineText = text("Status: OFFLINE", 15, "#FFFFFF", true);
        onlineRow.addView(onlineText, new LinearLayout.LayoutParams(0, -2, 1));

        onlineSwitch = new Switch(this);
        onlineSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (loading) return;
            setDriverOnline(isChecked);
        });
        onlineRow.addView(onlineSwitch);

        card.addView(onlineRow);

        add(root, card, 0, dp(22), 0, dp(16));
    }

    private void buildActions() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button refresh = outlineButton("Refresh");
        refresh.setOnClickListener(v -> refreshDriverData());
        row.addView(refresh, new LinearLayout.LayoutParams(0, dp(52), 1));

        Button history = outlineButton("Riwayat");
        history.setOnClickListener(v -> openWeb(BASE_URL + "?app=1#driver_history"));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(0, dp(52), 1);
        hlp.setMargins(dp(10), 0, 0, 0);
        row.addView(history, hlp);

        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void refreshDriverData() {
        if (loading) return;
        loading = true;
        // Auto refresh dibuat silent agar dashboard tidak selalu menampilkan loading.
        if (progressBar != null) progressBar.setVisibility(View.GONE);

        new Thread(() -> {
            String balanceJson = "";
            String statusJson = "";
            String activeJson = "";
            String ordersJson = "";

            try { statusJson = get(SERVER + "getDriverStatus.php?username=" + enc(username) + "&v=" + System.currentTimeMillis()); } catch (Exception ignored) {}
            try { balanceJson = get(SERVER + "getBalance.php?username=" + enc(username) + "&v=" + System.currentTimeMillis()); } catch (Exception ignored) {}

            try {
                // Sama seperti JS web: driver wajib online, lalu assignNextDriver, baru getOrders.
                if (parseOnline(statusJson)) {
                    get(SERVER + "assignNextDriver.php?driver_type=" + enc(driverType) + "&v=" + System.currentTimeMillis());
                }
            } catch (Exception ignored) {}

            try { activeJson = get(SERVER + "getActiveDriverOrder.php?driver=" + enc(username) + "&v=" + System.currentTimeMillis()); } catch (Exception ignored) {}
            try { ordersJson = get(SERVER + "getOrders.php?driver=" + enc(username) + "&driver_type=" + enc(driverType) + "&v=" + System.currentTimeMillis()); } catch (Exception ignored) {}

            // Tambahan pickup_orders dari endpoint baru. Jika belum ada, tidak membuat dashboard gagal.
            String pickupJson = "";
            try { pickupJson = get(SERVER + "driver_get_pickup_orders.php?driver=" + enc(username) + "&driver_type=" + enc(driverType) + "&v=" + System.currentTimeMillis()); } catch (Exception ignored) {}

            final String fBalance = balanceJson;
            final String fStatus = statusJson;
            final String fActive = activeJson;
            final String fOrders = ordersJson;
            final String fPickup = pickupJson;

            mainHandler.post(() -> {
                loading = false;
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                showStatus(fStatus);
                showBalance(fBalance);
                showActive(fActive);
                showOffers(fOrders, fPickup);
                statusText.setText("Dashboard driver siap • auto refresh 5 detik");
            });
        }).start();
    }

    private boolean parseOnline(String json) {
        try {
            JSONObject o = new JSONObject(json);
            return o.optBoolean("success", false) && o.optInt("is_online", 0) == 1;
        } catch (Exception e) {
            return driverOnline;
        }
    }

    private void showStatus(String json) {
        driverOnline = parseOnline(json);
        if (onlineSwitch != null && onlineSwitch.isChecked() != driverOnline) {
            onlineSwitch.setOnCheckedChangeListener(null);
            onlineSwitch.setChecked(driverOnline);
            onlineSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (loading) return;
                setDriverOnline(isChecked);
            });
        }
        if (onlineText != null) {
            onlineText.setText(driverOnline ? "Status: ONLINE" : "Status: OFFLINE");
        }
        if (levelText != null) {
            levelText.setText(driverOnline ? "ONLINE menerima order" : "OFFLINE");
            levelText.setTextColor(Color.parseColor(driverOnline ? "#0B7CFF" : "#EF4444"));
        }
    }

    private void showBalance(String json) {
        try {
            JSONObject o = new JSONObject(json);
            double b = o.optDouble("balance", 0);
            balanceText.setText(rupiah(b));
        } catch (Exception e) {
            balanceText.setText("Saldo belum terbaca");
        }
    }

    private void showActive(String json) {
        activeBox.removeAllViews();
        try {
            JSONObject o = new JSONObject(json);
            JSONObject order = o.optJSONObject("order");
            if (o.optBoolean("success", false) && order != null) {
                activeBox.addView(orderCard(order, true, "orders"));
                return;
            }
        } catch (Exception ignored) {}
        activeBox.addView(emptyCard("Belum ada order aktif."));
    }

    private void showOffers(String ordersJson, String pickupJson) {
        orderBox.removeAllViews();

        if (!driverOnline) {
            orderBox.addView(emptyCard("Driver masih OFFLINE. Aktifkan ONLINE untuk menerima order."));
            return;
        }

        int count = 0;

        try {
            JSONObject o = new JSONObject(ordersJson);
            JSONArray arr = o.optJSONArray("orders");
            if (o.optBoolean("success", false) && arr != null) {
                checkNotify(arr);
                for (int i = 0; i < arr.length() && count < 8; i++) {
                    JSONObject order = arr.optJSONObject(i);
                    if (order != null) {
                        orderBox.addView(orderCard(order, false, "orders"));
                        count++;
                    }
                }
            }
        } catch (Exception ignored) {}

        try {
            JSONObject p = new JSONObject(pickupJson);
            JSONArray arr = p.optJSONArray("orders");
            if (arr == null) arr = p.optJSONArray("pickup_orders");
            if (p.optBoolean("success", false) && arr != null) {
                checkNotify(arr);
                for (int i = 0; i < arr.length() && count < 8; i++) {
                    JSONObject order = arr.optJSONObject(i);
                    if (order != null) {
                        orderBox.addView(orderCard(order, false, "pickup_orders"));
                        count++;
                    }
                }
            }
        } catch (Exception ignored) {}

        if (count == 0) {
            orderBox.addView(emptyCard("Belum ada tawaran order."));
        }

        firstLoadDone = true;
    }

    private void checkNotify(JSONArray arr) {
        try {
            Set<String> current = new HashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String id = safe(o.optString("order_id", o.optString("id", "")));
                if (id.length() == 0) continue;
                current.add(id);
                if (!notifiedIds.contains(id)) {
                    if (firstLoadDone) Toast.makeText(this, "Order baru masuk #" + id, Toast.LENGTH_SHORT).show();
                    notifiedIds.add(id);
                }
            }
        } catch (Exception ignored) {}
    }

    private View orderCard(JSONObject order, boolean active, String table) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(24), 1));
        card.setElevation(dp(1));

        String id = safe(order.optString("id", order.optString("order_id", "")));
        String service = detectService(order, table);
        String pickup = firstNonEmpty(order.optString("pickup_address"), order.optString("pickup"), "-");
        String delivery = firstNonEmpty(order.optString("delivery_address"), order.optString("destination_address"), order.optString("delivery"), order.optString("destination"), "-");
        double price = order.optDouble("price", order.optDouble("fare", order.optDouble("total", order.optDouble("total_price", 0))));
        String distance = firstNonEmpty(order.optString("distance_km"), "0");

        card.addView(text((active ? "🚦 Order Aktif" : "🔔 Tawaran Order") + " #" + id, 17, "#0B3A78", true));
        add(card, text(service, 14, "#0B7CFF", true), 0, dp(6), 0, 0);
        add(card, text("📍 Penjemputan:\n" + pickup, 14, "#334155", false), 0, dp(8), 0, 0);
        add(card, text("🏁 Tujuan:\n" + delivery, 14, "#334155", false), 0, dp(6), 0, 0);
        add(card, text("💰 " + rupiah(price) + " • " + distance + " KM", 15, "#0F172A", true), 0, dp(8), 0, 0);

        if (active) {
            Button trip = primaryButton("Lanjutkan Trip");
            trip.setOnClickListener(v -> openNativeTrip(order, table));
            add(card, trip, 0, dp(12), 0, 0);
        } else {
            Button take = primaryButton("Ambil Order");
            take.setOnClickListener(v -> takeOrder(id, table));
            add(card, take, 0, dp(12), 0, 0);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(12));
        card.setLayoutParams(lp);
        return card;
    }

    private String detectService(JSONObject o, String table) {
        if ("pickup_orders".equals(table)) return "📦 TransPickup";
        String t = firstNonEmpty(o.optString("order_type"), o.optString("type"), o.optString("driver_type")).toLowerCase(Locale.US);
        String note = o.optString("note", "").toLowerCase(Locale.US);
        if (t.contains("car") || t.contains("mobil")) return "🚗 TransCar";
        if (t.contains("food") || note.contains("\"type\":\"food\"")) return "🍔 TransFood";
        return driverType.equals("car") ? "🚗 TransCar" : "🛵 TransRide";
    }

    private void takeOrder(String id, String table) {
        if (id.length() == 0) {
            showInfo("Gagal", "Order ID tidak ditemukan.");
            return;
        }

        setBusy(true);
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("id", id);
                payload.put("driver", username);
                payload.put("driver_type", driverType);
                payload.put("table", table);

                String endpoint = "pickup_orders".equals(table) ? "driver_take_pickup_order.php" : "takeOrder.php";
                JSONObject res = post(SERVER + endpoint, payload);
                boolean ok = res.optBoolean("success", false);
                String msg = firstNonEmpty(res.optString("message"), ok ? "Order berhasil diambil" : "Gagal mengambil order");
                JSONObject order = res.optJSONObject("order");

                mainHandler.post(() -> {
                    setBusy(false);
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    if (ok) {
                        if (order != null) openNativeTrip(order, table);
                        else refreshDriverData();
                    } else {
                        refreshDriverData();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setBusy(false);
                    showInfo("Gagal", "Koneksi gagal mengambil order.");
                    refreshDriverData();
                });
            }
        }).start();
    }

    private void setDriverOnline(boolean online) {
        setBusy(true);
        new Thread(() -> {
            try {
                JSONObject p = new JSONObject();
                p.put("username", username);
                p.put("is_online", online ? 1 : 0);
                p.put("driver_type", driverType);
                JSONObject res = post(SERVER + "updateDriverStatus.php", p);
                boolean ok = res.optBoolean("success", false);

                mainHandler.post(() -> {
                    setBusy(false);
                    if (!ok) {
                        showInfo("Gagal", firstNonEmpty(res.optString("message"), "Gagal mengubah status driver."));
                    }
                    refreshDriverData();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setBusy(false);
                    showInfo("Gagal", "Koneksi gagal mengubah status driver.");
                    refreshDriverData();
                });
            }
        }).start();
    }

    private void openNativeTrip(JSONObject order, String table) {
        try {
            if (order == null) {
                showInfo("Order", "Data order tidak ditemukan.");
                return;
            }

            String id = firstNonEmpty(order.optString("id"), order.optString("order_id"), "");
            String pickupLat = firstNonEmpty(order.optString("pickup_lat"), order.optString("user_lat"), order.optString("latitude"));
            String pickupLng = firstNonEmpty(order.optString("pickup_lng"), order.optString("user_lng"), order.optString("longitude"));
            String deliveryLat = firstNonEmpty(order.optString("delivery_lat"), order.optString("destination_lat"), order.optString("to_lat"));
            String deliveryLng = firstNonEmpty(order.optString("delivery_lng"), order.optString("destination_lng"), order.optString("to_lng"));

            Intent i = new Intent(this, DriverTripActivity.class);
            i.putExtra("order_json", order.toString());
            i.putExtra("order_table", table);
            i.putExtra("source", table);
            i.putExtra("table", table);
            i.putExtra("order_id", id);
            i.putExtra("id", id);
            i.putExtra("pickup_lat", pickupLat);
            i.putExtra("pickup_lng", pickupLng);
            i.putExtra("delivery_lat", deliveryLat);
            i.putExtra("delivery_lng", deliveryLng);
            i.putExtra("driver", username);
            i.putExtra("driver_type", driverType);
            startActivity(i);
        } catch (Exception e) {
            showInfo("Trip", "Gagal membuka Driver Trip native: " + e.getMessage());
        }
    }

    private void openProfile() {
        try {
            Intent i = new Intent(this, ProfileActivity.class);
            startActivity(i);
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("Profil Driver")
                    .setMessage("Driver: " + username + "\nTipe: " + driverLabel())
                    .setPositiveButton("Logout", (d, w) -> logout())
                    .setNegativeButton("Tutup", null)
                    .show();
        }
    }

    private void logout() {
        try { sessionManager.clearSession(); } catch (Exception ignored) {}
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(location);
            else startService(location);
            try { BackgroundSyncService.start(this); } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    private void openWeb(String url) {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("url", url);
        startActivity(i);
    }

    private TextView emptyCard(String msg) {
        TextView t = text(msg, 16, "#0F172A", false);
        t.setPadding(dp(16), dp(18), dp(16), dp(18));
        t.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(24), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(12));
        t.setLayoutParams(lp);
        return t;
    }

    private TextView section(String value) {
        TextView t = text(value, 21, "#0B3A78", true);
        t.setPadding(0, dp(14), 0, dp(6));
        return t;
    }

    private void setBusy(boolean b) {
        loading = b;
        if (progressBar != null) progressBar.setVisibility(b ? View.VISIBLE : View.GONE);
    }

    private String get(String link) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(link).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestMethod("GET");
        c.setRequestProperty("Accept", "application/json");
        InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = read(is);
        c.disconnect();
        return body;
    }

    private JSONObject post(String link, JSONObject payload) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(link).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        c.setRequestProperty("Accept", "application/json");
        OutputStream os = c.getOutputStream();
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        bw.write(payload == null ? "{}" : payload.toString());
        bw.flush();
        bw.close();
        os.close();
        InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = read(is);
        c.disconnect();
        if (body == null || body.trim().length() == 0) return new JSONObject();
        return new JSONObject(body);
    }

    private String read(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private Button primaryButton(String s) {
        Button b = new Button(this);
        b.setText(s); b.setAllCaps(false); b.setTextSize(15); b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE); b.setBackground(roundGradient("#086BFF", "#2EA2FF", dp(18)));
        return b;
    }

    private Button outlineButton(String s) {
        Button b = primaryButton(s);
        b.setTextColor(Color.parseColor("#0B7CFF"));
        b.setBackground(roundStroke("#FFFFFF", "#9DCAFF", dp(18), 1));
        return b;
    }

    private TextView text(String s, int sp, String color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(Color.parseColor(color));
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void add(LinearLayout parent, View v, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(l, t, r, b);
        parent.addView(v, lp);
    }

    private GradientDrawable round(String c, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(c));
        g.setCornerRadius(r);
        return g;
    }

    private GradientDrawable roundStroke(String c, String s, int r, int w) {
        GradientDrawable g = round(c, r);
        g.setStroke(dp(w), Color.parseColor(s));
        return g;
    }

    private GradientDrawable roundGradient(String a, String b, int r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor(a), Color.parseColor(b)});
        g.setCornerRadius(r);
        return g;
    }

    private String rupiah(double v) {
        return "Rp " + NumberFormat.getNumberInstance(new Locale("id", "ID")).format((long) v);
    }

    private String enc(String v) {
        try { return URLEncoder.encode(v == null ? "" : v, "UTF-8"); } catch (Exception e) { return ""; }
    }

    private String safe(String v) { return v == null ? "" : v.trim(); }

    private String firstNonEmpty(String... vals) {
        if (vals == null) return "";
        for (String v : vals) if (v != null && v.trim().length() > 0 && !"null".equalsIgnoreCase(v.trim())) return v.trim();
        return "";
    }

    private String driverLabel() { return "car".equals(driverType) ? "Driver Mobil" : "Driver Motor"; }

    private void showInfo(String title, String msg) {
        try { new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show(); } catch (Exception ignored) {}
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}