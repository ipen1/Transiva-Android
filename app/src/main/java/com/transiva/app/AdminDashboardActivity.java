package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
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

public class AdminDashboardActivity extends Activity {

    private static final String HOME_URL = "https://transiva.my.id/?app=1";
    private static final String BASE = "https://transiva.my.id/server/";

    private SessionManager sessionManager;
    private LinearLayout root;
    private LinearLayout summaryBox;
    private LinearLayout orderBox;
    private TextView nameText;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            getWindow().setStatusBarColor(Color.parseColor("#06142E"));
            getWindow().setNavigationBarColor(Color.parseColor("#06142E"));
        } catch (Exception ignored) {}

        sessionManager = new SessionManager(this);
        buildUi();
        loadDashboard();
    }

    @Override
    protected void onResume() {
        super.onResume();
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

        root.addView(title("Transiva Admin"));

        nameText = smallText("Memuat akun admin...");
        root.addView(nameText);

        statusText = smallText("");
        root.addView(statusText);

        summaryBox = new LinearLayout(this);
        summaryBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(summaryBox);

        root.addView(sectionTitle("Menu Admin"));

        addButton(root, "📊 Dashboard Web Admin", HOME_URL + "#admin");
        addButton(root, "🧾 Semua Order", HOME_URL + "#admin_orders");
        addButton(root, "👤 Kelola User", HOME_URL + "#admin_users");
        addButton(root, "🛵 Kelola Driver", HOME_URL + "#admin_drivers");
        addButton(root, "🍔 Kelola Merchant", HOME_URL + "#admin_merchants");
        addButton(root, "💰 Saldo & Transaksi", HOME_URL + "#admin_balance");
        addButton(root, "⚙️ Pengaturan Aplikasi", HOME_URL + "#admin_settings");

        root.addView(sectionTitle("Order Terbaru"));

        orderBox = new LinearLayout(this);
        orderBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(orderBox);

        Button refresh = button("Refresh Dashboard");
        refresh.setOnClickListener(v -> loadDashboard());
        root.addView(refresh);

        Button web = button("Buka WebView Lengkap");
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

        nameText.setText("Halo, " + (username.isEmpty() ? "Admin" : username) + " • " + (role.isEmpty() ? "admin" : role));
        statusText.setText("Memuat data admin...");
        summaryBox.removeAllViews();
        orderBox.removeAllViews();

        new Thread(() -> {
            String summaryJson = "";
            String ordersJson = "";

            try {
                summaryJson = get(BASE + "admin_dashboard.php?username=" + enc(username));
            } catch (Exception ignored) {}

            try {
                ordersJson = get(BASE + "get_orders.php?admin=1&limit=5");
            } catch (Exception ignored) {}

            final String s = summaryJson;
            final String o = ordersJson;

            runOnUiThread(() -> {
                showSummary(s);
                showOrders(o);
                statusText.setText("Dashboard admin siap");
            });
        }).start();
    }

    private void showSummary(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            JSONObject data = obj.optJSONObject("data");
            if (data == null) data = obj;

            int users = data.optInt("users", data.optInt("total_users", 0));
            int drivers = data.optInt("drivers", data.optInt("total_drivers", 0));
            int merchants = data.optInt("merchants", data.optInt("total_merchants", 0));
            int orders = data.optInt("orders", data.optInt("total_orders", 0));
            int today = data.optInt("today_orders", data.optInt("orders_today", 0));
            int income = data.optInt("income", data.optInt("revenue", data.optInt("profit", 0)));

            summaryBox.addView(cardText("📊 Ringkasan Platform\n" +
                    "User: " + users + "\n" +
                    "Driver: " + drivers + "\n" +
                    "Merchant: " + merchants + "\n" +
                    "Total order: " + orders + "\n" +
                    "Order hari ini: " + today + "\n" +
                    "Pendapatan: " + rupiah(income)));

        } catch (Exception e) {
            summaryBox.addView(cardText("📊 Ringkasan Platform\nEndpoint admin_dashboard.php belum tersedia / data belum terbaca. Tombol menu admin tetap bisa membuka WebView."));
        }
    }

    private void showOrders(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            JSONArray arr = obj.optJSONArray("orders");

            if (arr == null) arr = obj.optJSONArray("data");

            if (arr == null || arr.length() == 0) {
                orderBox.addView(cardText("Belum ada order terbaru / endpoint belum tersedia."));
                return;
            }

            int max = Math.min(arr.length(), 5);
            for (int i = 0; i < max; i++) {
                JSONObject order = arr.optJSONObject(i);
                if (order != null) orderBox.addView(orderCard(order));
            }
        } catch (Exception e) {
            orderBox.addView(cardText("Order terbaru belum terbaca."));
        }
    }

    private TextView orderCard(JSONObject order) {
        String id = order.optString("id", order.optString("order_id", "-"));
        String type = order.optString("order_type", order.optString("type", "order"));
        String status = order.optString("status", "-");
        String customer = order.optString("username", order.optString("customer", order.optString("user", "-")));
        String driver = order.optString("driver", "-");
        int price = order.optInt("price", order.optInt("total", order.optInt("fare", 0)));

        TextView card = cardText("🧾 Order #" + id + "\n" +
                "Layanan: " + type + "\n" +
                "Customer: " + customer + "\n" +
                "Driver: " + driver + "\n" +
                "Status: " + status + "\n" +
                "Nominal: " + rupiah(price));

        card.setOnClickListener(v -> openWeb(HOME_URL + "#admin_orders"));
        return card;
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
        try { return URLEncoder.encode(value == null ? "" : value, "UTF-8"); }
        catch (Exception e) { return ""; }
    }

    private String safe(String value) { return value == null ? "" : value.trim(); }

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
