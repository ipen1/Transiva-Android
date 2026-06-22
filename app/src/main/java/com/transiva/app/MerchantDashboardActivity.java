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

public class MerchantDashboardActivity extends Activity {

    private static final String HOME_URL = "https://transiva.my.id/?app=1";
    private static final String BASE = "https://transiva.my.id/server/";

    private SessionManager sessionManager;
    private LinearLayout root;
    private LinearLayout orderBox;
    private LinearLayout menuBox;
    private TextView nameText;
    private TextView balanceText;
    private TextView storeText;
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

        root.addView(title("Transiva Merchant"));

        nameText = smallText("Memuat akun merchant...");
        root.addView(nameText);

        balanceText = cardText("💳 Saldo Merchant\nMemuat saldo...");
        root.addView(balanceText);

        storeText = cardText("🍔 Toko / Restoran\nMemuat data toko...");
        root.addView(storeText);

        root.addView(sectionTitle("Menu Merchant"));

        addButton(root, "📥 Order Masuk", HOME_URL + "#merchant_orders");
        addButton(root, "🍟 Daftar Menu", HOME_URL + "#merchant_menu");
        addButton(root, "➕ Tambah Menu", HOME_URL + "#merchant_add_menu");
        addButton(root, "🏪 Profil Toko", HOME_URL + "#merchant_profile");
        addButton(root, "💰 Saldo & Pencairan", HOME_URL + "#merchant_balance");

        statusText = smallText("");
        root.addView(statusText);

        root.addView(sectionTitle("Order Masuk / Aktif"));

        orderBox = new LinearLayout(this);
        orderBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(orderBox);

        root.addView(sectionTitle("Menu Terbaru"));

        menuBox = new LinearLayout(this);
        menuBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(menuBox);

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
            nameText.setText("Merchant belum login");
            statusText.setText("Silakan login ulang.");
            return;
        }

        nameText.setText("Halo, " + username + " • " + (role.isEmpty() ? "merchant" : role));
        statusText.setText("Memuat data merchant...");
        orderBox.removeAllViews();
        menuBox.removeAllViews();

        new Thread(() -> {
            String balanceJson = "";
            String storeJson = "";
            String ordersJson = "";
            String menuJson = "";

            try {
                balanceJson = get(BASE + "getBalance.php?username=" + enc(username));
            } catch (Exception ignored) {}

            try {
                storeJson = get(BASE + "get_merchant_store.php?username=" + enc(username));
            } catch (Exception ignored) {}

            try {
                ordersJson = get(BASE + "get_merchant_orders.php?username=" + enc(username));
            } catch (Exception ignored) {}

            try {
                menuJson = get(BASE + "get_merchant_menu.php?username=" + enc(username));
            } catch (Exception ignored) {}

            final String b = balanceJson;
            final String s = storeJson;
            final String o = ordersJson;
            final String m = menuJson;

            runOnUiThread(() -> {
                showBalance(b);
                showStore(s);
                showOrders(o);
                showMenus(m);
                statusText.setText("Dashboard merchant siap");
            });
        }).start();
    }

    private void showBalance(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            int balance = obj.optInt("balance", 0);
            balanceText.setText("💳 Saldo Merchant\n" + rupiah(balance));
        } catch (Exception e) {
            balanceText.setText("💳 Saldo Merchant\nSaldo belum terbaca");
        }
    }

    private void showStore(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            JSONObject store = obj.optJSONObject("store");
            if (store == null) store = obj.optJSONObject("restaurant");
            if (store == null) store = obj;

            String name = store.optString("name", store.optString("restaurant_name", "Toko Merchant"));
            String address = store.optString("address", store.optString("location", "-"));
            String open = store.optString("status", store.optString("open_status", "aktif"));

            storeText.setText("🍔 Toko / Restoran\n" + name + "\nAlamat: " + address + "\nStatus: " + open);
        } catch (Exception e) {
            storeText.setText("🍔 Toko / Restoran\nData toko belum terbaca / endpoint belum tersedia");
        }
    }

    private void showOrders(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            JSONArray arr = obj.optJSONArray("orders");
            if (arr == null) arr = obj.optJSONArray("data");

            if (arr == null || arr.length() == 0) {
                orderBox.addView(cardText("Belum ada order masuk."));
                return;
            }

            int max = Math.min(arr.length(), 5);
            for (int i = 0; i < max; i++) {
                JSONObject order = arr.optJSONObject(i);
                if (order != null) orderBox.addView(orderCard(order));
            }
        } catch (Exception e) {
            orderBox.addView(cardText("Order merchant belum terbaca / endpoint belum tersedia."));
        }
    }

    private void showMenus(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            JSONArray arr = obj.optJSONArray("menus");
            if (arr == null) arr = obj.optJSONArray("data");

            if (arr == null || arr.length() == 0) {
                menuBox.addView(cardText("Belum ada menu / endpoint belum tersedia."));
                return;
            }

            int max = Math.min(arr.length(), 5);
            for (int i = 0; i < max; i++) {
                JSONObject menu = arr.optJSONObject(i);
                if (menu != null) menuBox.addView(menuCard(menu));
            }
        } catch (Exception e) {
            menuBox.addView(cardText("Daftar menu belum terbaca."));
        }
    }

    private TextView orderCard(JSONObject order) {
        String id = order.optString("id", order.optString("order_id", "-"));
        String status = order.optString("status", "-");
        String customer = order.optString("username", order.optString("customer", "-"));
        String address = order.optString("destination_address", order.optString("address", "-"));
        int total = order.optInt("total", order.optInt("price", 0));

        TextView card = cardText("📥 Order #" + id + "\n" +
                "Customer: " + customer + "\n" +
                "Status: " + status + "\n" +
                "Alamat: " + address + "\n" +
                "Total: " + rupiah(total));

        card.setOnClickListener(v -> openWeb(HOME_URL + "#merchant_orders"));
        return card;
    }

    private TextView menuCard(JSONObject menu) {
        String name = menu.optString("name", menu.optString("menu_name", "Menu"));
        String category = menu.optString("category", menu.optString("type", "-"));
        int price = menu.optInt("price", menu.optInt("harga", 0));
        String status = menu.optString("status", menu.optString("available", "aktif"));

        TextView card = cardText("🍟 " + name + "\n" +
                "Kategori: " + category + "\n" +
                "Harga: " + rupiah(price) + "\n" +
                "Status: " + status);

        card.setOnClickListener(v -> openWeb(HOME_URL + "#merchant_menu"));
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
