package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.util.Locale;

public class DriverDashboardActivity extends Activity {

    private static final String HOME_URL = "https://transiva.my.id/?app=1";
    private static final String BASE = "https://transiva.my.id/server/";

    private SessionManager sessionManager;

    private LinearLayout root;
    private LinearLayout activeBox;
    private LinearLayout offerBox;

    private TextView nameText;
    private TextView balanceText;
    private TextView statusText;
    private TextView gpsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            getWindow().setStatusBarColor(Color.parseColor("#06142E"));
            getWindow().setNavigationBarColor(Color.parseColor("#06142E"));
        } catch (Exception ignored) {}

        sessionManager = new SessionManager(this);

        buildUi();
        startNativeServices();
        loadDashboard();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startNativeServices();
        loadDashboard();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 34, 28, 34);
        root.setBackgroundColor(Color.parseColor("#F4F7FB"));

        scroll.addView(root);
        setContentView(scroll);

        TextView title = title("Transiva Driver");
        root.addView(title);

        nameText = smallText("Memuat akun driver...");
        root.addView(nameText);

        balanceText = cardText("💳 Saldo Driver\nMemuat saldo...");
        root.addView(balanceText);

        gpsText = cardText("📍 GPS Driver\nMenyiapkan lokasi...");
        root.addView(gpsText);

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        root.addView(menu);

        addButton(menu, "🛵 Cari Orderan", HOME_URL + "#driver");
        addButton(menu, "📦 Order Aktif", HOME_URL + "#driver_trip");
        addButton(menu, "🗺️ Buka Map Web", HOME_URL + "#map");
        addButton(menu, "💰 Riwayat Penghasilan", HOME_URL + "#driver_history");
        addButton(menu, "👤 Profil Driver", HOME_URL + "#profile");

        statusText = smallText("");
        root.addView(statusText);

        TextView activeTitle = sectionTitle("Order Aktif");
        root.addView(activeTitle);

        activeBox = new LinearLayout(this);
        activeBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(activeBox);

        TextView offerTitle = sectionTitle("Tawaran Order Terbaru");
        root.addView(offerTitle);

        offerBox = new LinearLayout(this);
        offerBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(offerBox);

        Button refresh = button("Refresh Dashboard");
        refresh.setOnClickListener(v -> loadDashboard());
        root.addView(refresh);

        Button web = button("Buka Dashboard WebView");
        web.setOnClickListener(v -> openWeb(HOME_URL));
        root.addView(web);

        Button logout = button("Logout");
        logout.setOnClickListener(v -> logout());
        root.addView(logout);
    }

    private void loadDashboard() {
        if (sessionManager == null) sessionManager = new SessionManager(this);

        String username = safe(sessionManager.getUsername());
        String role = safe(sessionManager.getRole());

        if (username.isEmpty()) {
            nameText.setText("Driver belum login");
            statusText.setText("Silakan login ulang.");
            return;
        }

        nameText.setText("Halo, " + username + " • " + (role.isEmpty() ? "driver" : role));
        statusText.setText("Memuat data dashboard...");
        gpsText.setText("📍 GPS Driver\nService lokasi aktif di background");
        activeBox.removeAllViews();
        offerBox.removeAllViews();

        new Thread(() -> {
            String balanceJson = "";
            String activeJson = "";
            String offerJson = "";

            try {
                balanceJson = get(BASE + "getBalance.php?username=" + enc(username));
            } catch (Exception ignored) {}

            try {
                activeJson = get(BASE + "get_driver_active_order.php?driver=" + enc(username));
            } catch (Exception ignored) {}

            try {
                offerJson = get(BASE + "get_driver_orders.php?driver=" + enc(username));
            } catch (Exception ignored) {}

            final String b = balanceJson;
            final String a = activeJson;
            final String o = offerJson;

            runOnUiThread(() -> {
                showBalance(b);
                showActiveOrder(a);
                showOfferOrders(o);
                statusText.setText("Dashboard driver siap");
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

    private void showActiveOrder(String json) {
        try {
            JSONObject obj = new JSONObject(json);

            JSONObject order = obj.optJSONObject("order");
            if (order == null) {
                JSONArray arr = obj.optJSONArray("orders");
                if (arr != null && arr.length() > 0) order = arr.optJSONObject(0);
            }

            if (order == null) {
                activeBox.addView(cardText("Belum ada order aktif."));
                return;
            }

            activeBox.addView(orderCard(order, true));
        } catch (Exception e) {
            activeBox.addView(cardText("Belum ada order aktif / endpoint belum tersedia."));
        }
    }

    private void showOfferOrders(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            JSONArray arr = obj.optJSONArray("orders");

            if (arr == null || arr.length() == 0) {
                offerBox.addView(cardText("Belum ada tawaran order."));
                return;
            }

            int max = Math.min(arr.length(), 5);
            for (int i = 0; i < max; i++) {
                JSONObject order = arr.optJSONObject(i);
                if (order != null) offerBox.addView(orderCard(order, false));
            }
        } catch (Exception e) {
            offerBox.addView(cardText("Tawaran order belum terbaca / endpoint belum tersedia."));
        }
    }

    private TextView orderCard(JSONObject order, boolean active) {
        String id = order.optString("id", order.optString("order_id", "-"));
        String type = order.optString("order_type", order.optString("type", "order"));
        String status = order.optString("status", "-");
        String pickup = order.optString("pickup_address", order.optString("pickup", "-"));
        String destination = order.optString("destination_address", order.optString("destination", "-"));
        int price = order.optInt("price", order.optInt("fare", order.optInt("total", 0)));

        String text =
                (active ? "🚦 Order Aktif" : "🔔 Tawaran Order") + "\n" +
                "Order #" + id + "\n" +
                "Layanan: " + type + "\n" +
                "Status: " + status + "\n" +
                "Jemput: " + pickup + "\n" +
                "Tujuan: " + destination + "\n" +
                "Harga: " + rupiah(price);

        TextView card = cardText(text);
        card.setOnClickListener(v -> openWeb(active ? HOME_URL + "#driver_trip" : HOME_URL + "#driver"));
        return card;
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(location);
            } else {
                startService(location);
            }

            try {
                BackgroundSyncService.start(this);
            } catch (Exception ignored) {}

        } catch (Exception ignored) {}
    }

    private void openWeb(String url) {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("url", url);
        startActivity(i);
    }

    private void logout() {
        try {
            if (sessionManager != null) sessionManager.clearSession();
        } catch (Exception ignored) {}

        try {
            Intent stop = new Intent(this, LocationService.class);
            stop.setAction(LocationService.ACTION_STOP);
            startService(stop);
        } catch (Exception ignored) {}

        try {
            BackgroundSyncService.stop(this);
        } catch (Exception ignored) {}

        Toast.makeText(this, "Logout berhasil", Toast.LENGTH_SHORT).show();

        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private String get(String link) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(link).openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(12000);
        conn.setRequestMethod("GET");

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null) sb.append(line);

        br.close();
        conn.disconnect();

        return sb.toString();
    }

    private String enc(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String rupiah(int value) {
        return "Rp " + NumberFormat.getNumberInstance(new Locale("id", "ID")).format(value);
    }

    private void addButton(LinearLayout parent, String label, String url) {
        Button b = button(label);
        b.setOnClickListener(v -> openWeb(url));
        parent.addView(b);
    }

    private TextView title(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(27);
        t.setTypeface(null, 1);
        t.setTextColor(Color.parseColor("#06142E"));
        t.setPadding(0, 0, 0, 18);
        return t;
    }

    private TextView sectionTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(20);
        t.setTypeface(null, 1);
        t.setTextColor(Color.parseColor("#06142E"));
        t.setPadding(0, 18, 0, 8);
        return t;
    }

    private TextView smallText(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTextColor(Color.parseColor("#1F2937"));
        t.setPadding(0, 6, 0, 12);
        return t;
    }

    private TextView cardText(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(16);
        t.setTextColor(Color.parseColor("#111827"));
        t.setBackgroundColor(Color.WHITE);
        t.setPadding(26, 22, 26, 22);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 10, 0, 14);
        t.setLayoutParams(lp);

        return t;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setGravity(Gravity.CENTER);
        return b;
    }
}
