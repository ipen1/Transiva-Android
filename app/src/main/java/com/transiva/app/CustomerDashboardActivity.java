package com.transiva.app;

import android.app.Activity;
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
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.util.Locale;

public class CustomerDashboardActivity extends Activity {

    private static final String TAG = "TRANSIVA_NATIVE_HOME";
    private static final String BASE = "https://transiva.my.id/server/";
    private static final String WEB_HOME = "https://transiva.my.id/?app=1";

    private SessionManager session;
    private LinearLayout root;
    private TextView nameText;
    private TextView locationText;
    private TextView balanceText;
    private TextView weatherText;
    private TextView statusText;
    private LinearLayout orderBox;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            getWindow().setStatusBarColor(Color.parseColor("#06142E"));
            getWindow().setNavigationBarColor(Color.parseColor("#06142E"));
        } catch (Exception ignored) {}

        session = new SessionManager(this);

        if (!safeLoggedIn()) {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        buildNativeHome();
        loadDashboard();
    }

    private boolean safeLoggedIn() {
        try {
            return session != null && session.isLoggedIn();
        } catch (Exception e) {
            return false;
        }
    }

    private void buildNativeHome() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor("#F4F7FB"));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        setContentView(scroll);

        root.addView(headerCard());
        root.addView(searchBox());
        root.addView(payCard());
        root.addView(weatherCard());
        root.addView(serviceGrid());
        root.addView(orderStatusCard());
        root.addView(bottomActions());
    }

    private View headerCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(roundGradient("#06142E", "#0F766E", dp(22)));
        card.setLayoutParams(margin(-1, -2, 0, 0, 0, dp(14)));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, -2, 1);
        card.addView(left, leftLp);

        TextView welcome = text("Selamat Datang 👋", 14, "#D1FAE5", false);
        left.addView(welcome);

        nameText = text("Memuat akun...", 24, "#FFFFFF", true);
        nameText.setPadding(0, dp(4), 0, dp(6));
        left.addView(nameText);

        locationText = pill("📍 Parigi / lokasi aktif");
        left.addView(locationText);

        ImageView logo = new ImageView(this);
        logo.setImageResource(getDrawableId("transiva_logo"));
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        card.addView(logo, new LinearLayout.LayoutParams(dp(78), dp(78)));

        return card;
    }

    private View searchBox() {
        TextView box = text("🔎 Cari makanan, toko, kurir, wisata...", 15, "#6B7280", false);
        box.setPadding(dp(18), dp(15), dp(18), dp(15));
        box.setBackground(round("#FFFFFF", dp(18)));
        box.setLayoutParams(margin(-1, -2, 0, 0, 0, dp(14)));
        box.setOnClickListener(v -> openWeb(WEB_HOME + "#search"));
        return box;
    }

    private View payCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(16));
        card.setBackground(roundGradient("#111827", "#2563EB", dp(22)));
        card.setLayoutParams(margin(-1, -2, 0, 0, 0, dp(14)));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(top);

        LinearLayout textWrap = new LinearLayout(this);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        top.addView(textWrap, new LinearLayout.LayoutParams(0, -2, 1));

        textWrap.addView(text("💳 Transiva Pay", 14, "#DBEAFE", true));
        balanceText = text("Memuat saldo...", 25, "#FFFFFF", true);
        balanceText.setPadding(0, dp(5), 0, 0);
        textWrap.addView(balanceText);

        TextView badge = text("T", 26, "#FFFFFF", true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundStroke("#2563EB", "#93C5FD", dp(18), 2));
        top.addView(badge, new LinearLayout.LayoutParams(dp(58), dp(58)));

        TextView sub = text("Bayar layanan Transiva lebih praktis dengan saldo aplikasi.", 13, "#E0F2FE", false);
        sub.setPadding(0, dp(12), 0, dp(10));
        card.addView(sub);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(actions);

        Button topup = smallButton("+ Isi Saldo");
        topup.setOnClickListener(v -> openWeb(WEB_HOME + "#deposit"));
        actions.addView(topup, new LinearLayout.LayoutParams(0, -2, 1));

        Button refresh = smallButton("Refresh");
        refresh.setOnClickListener(v -> loadDashboard());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -2, 1);
        rlp.setMargins(dp(10), 0, 0, 0);
        actions.addView(refresh, rlp);

        return card;
    }

    private View weatherCard() {
        weatherText = text("🌤️ Rekomendasi cuaca: minuman segar & layanan cepat di dekatmu", 15, "#92400E", true);
        weatherText.setPadding(dp(18), dp(16), dp(18), dp(16));
        weatherText.setBackground(round("#FEF3C7", dp(18)));
        weatherText.setLayoutParams(margin(-1, -2, 0, 0, 0, dp(14)));
        return weatherText;
    }

    private View serviceGrid() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutParams(margin(-1, -2, 0, 0, 0, dp(14)));

        LinearLayout row1 = row();
        row1.addView(serviceCard("TransRide", "Motor", "ic_transride", WEB_HOME + "#kurir"), cellLp(0, 0, dp(8), dp(8)));
        row1.addView(serviceCard("TransCar", "Mobil", "ic_transcar", WEB_HOME + "#mobil"), cellLp(dp(8), 0, 0, dp(8)));
        box.addView(row1);

        LinearLayout row2 = row();
        row2.addView(serviceCard("TransFood", "Makanan", "ic_transfood", WEB_HOME + "#food"), cellLp(0, dp(8), dp(8), dp(8)));
        row2.addView(serviceCard("TransTour", "Wisata", "ic_transtour", WEB_HOME + "#wisata"), cellLp(dp(8), dp(8), 0, dp(8)));
        box.addView(row2);

        LinearLayout row3 = row();
        row3.addView(serviceCard("TransLaundry", "Cuci pakaian", "ic_translaundry", WEB_HOME + "#laundry"), cellLp(0, dp(8), dp(8), 0));
        row3.addView(serviceCard("TransPickup", "Kurir barang", "ic_transpickup", WEB_HOME + "#pickup"), cellLp(dp(8), dp(8), 0, 0));
        box.addView(row3);

        return box;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private LinearLayout.LayoutParams cellLp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(118), 1);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    private View serviceCard(String title, String sub, String iconName, String url) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(round("#FFFFFF", dp(20)));
        card.setOnClickListener(v -> openWeb(url));

        ImageView icon = new ImageView(this);
        icon.setImageResource(getDrawableId(iconName));
        icon.setColorFilter(Color.parseColor("#0F766E"));
        card.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));

        TextView titleText = text(title, 16, "#111827", true);
        titleText.setGravity(Gravity.CENTER);
        titleText.setPadding(0, dp(8), 0, dp(2));
        card.addView(titleText);

        TextView subText = text(sub, 12, "#6B7280", false);
        subText.setGravity(Gravity.CENTER);
        card.addView(subText);

        return card;
    }

    private View orderStatusCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(round("#FFFFFF", dp(20)));
        card.setLayoutParams(margin(-1, -2, 0, 0, 0, dp(14)));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(top);

        TextView title = text("Status Pesanan", 19, "#111827", true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        top.addView(progressBar, new LinearLayout.LayoutParams(dp(32), dp(32)));

        statusText = text("Memuat status...", 14, "#6B7280", false);
        statusText.setPadding(0, dp(8), 0, dp(8));
        card.addView(statusText);

        orderBox = new LinearLayout(this);
        orderBox.setOrientation(LinearLayout.VERTICAL);
        card.addView(orderBox);

        return card;
    }

    private View bottomActions() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);

        Button history = button("Riwayat");
        history.setOnClickListener(v -> openWeb(WEB_HOME + "#history"));
        box.addView(history, new LinearLayout.LayoutParams(0, -2, 1));

        Button profile = button("Profil");
        profile.setOnClickListener(v -> openWeb(WEB_HOME + "#profile"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(10), 0, 0, 0);
        box.addView(profile, lp);

        return box;
    }

    private void loadDashboard() {
        if (!safeLoggedIn()) return;

        String username = session.getUsername();
        String userId = session.getUserId();

        if (username == null || username.trim().isEmpty()) username = "User";

        nameText.setText(username);
        statusText.setText("Memuat pesanan aktif...");
        progressBar.setVisibility(View.VISIBLE);
        orderBox.removeAllViews();

        final String u = username;
        final String uid = userId;

        new Thread(() -> {
            String balanceJson = "";
            String ordersJson = "";
            Exception error = null;

            try {
                balanceJson = httpGet(BASE + "getBalance.php?username=" + enc(u));
                ordersJson = httpGet(BASE + "get_user_orders.php?user_id=" + enc(uid));
            } catch (Exception e) {
                error = e;
            }

            final String b = balanceJson;
            final String o = ordersJson;
            final Exception err = error;

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (err != null) {
                    statusText.setText("Dashboard belum sinkron: " + clean(err.getMessage()));
                    balanceText.setText("Saldo tidak terbaca");
                    orderBox.addView(orderMiniCard("Koneksi server bermasalah", "Coba refresh atau buka ulang aplikasi."));
                    return;
                }
                showBalance(b);
                showOrders(o);
            });
        }).start();
    }

    private void showBalance(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            int balance = obj.optInt("balance", 0);
            balanceText.setText(rupiah(balance));
        } catch (Exception e) {
            balanceText.setText("Saldo tidak terbaca");
        }
    }

    private void showOrders(String json) {
        orderBox.removeAllViews();
        try {
            JSONObject obj = new JSONObject(json);
            JSONArray arr = obj.optJSONArray("orders");
            if (arr == null || arr.length() == 0) {
                statusText.setText("Belum ada pesanan aktif.");
                orderBox.addView(orderMiniCard("Aman", "Pesanan aktif akan tampil di sini."));
                return;
            }

            statusText.setText("Ada " + arr.length() + " pesanan/riwayat terbaru.");
            int max = Math.min(arr.length(), 4);
            for (int i = 0; i < max; i++) {
                JSONObject item = arr.getJSONObject(i);
                String title = "Order #" + item.optString("id", "-") + " • " + item.optString("status", "-");
                String sub = "Layanan: " + item.optString("order_type", "kurir")
                        + "\nDriver: " + item.optString("driver", "-")
                        + "\nHarga: " + rupiah(item.optInt("price", 0));
                View card = orderMiniCard(title, sub);
                card.setOnClickListener(v -> openWeb(WEB_HOME + "#history"));
                orderBox.addView(card);
            }
        } catch (Exception e) {
            statusText.setText("Status pesanan tidak terbaca.");
            orderBox.addView(orderMiniCard("Data belum siap", "Format response server perlu disamakan JSON orders."));
        }
    }

    private View orderMiniCard(String title, String sub) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(round("#F9FAFB", dp(16)));
        card.setLayoutParams(margin(-1, -2, 0, dp(8), 0, 0));

        card.addView(text(title, 15, "#111827", true));
        TextView s = text(sub, 13, "#4B5563", false);
        s.setPadding(0, dp(4), 0, 0);
        card.addView(s);
        return card;
    }

    private void openWeb(String url) {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("url", url == null ? WEB_HOME : url);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Fitur belum tersedia", Toast.LENGTH_SHORT).show();
        }
    }

    private String httpGet(String link) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(link).openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "TransivaAndroidNativeHome/1.0");

            int code = conn.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String result = read(stream).trim();
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code + " " + result);
            }
            return result;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private String rupiah(int value) {
        return "Rp " + NumberFormat.getNumberInstance(new Locale("id", "ID")).format(value);
    }

    private String clean(String message) {
        if (message == null || message.trim().isEmpty()) return "koneksi bermasalah";
        message = message.trim();
        if (message.length() > 90) return message.substring(0, 90) + "...";
        return message;
    }

    private TextView text(String value, int sp, String color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(Color.parseColor(color));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private TextView pill(String value) {
        TextView t = text(value, 12, "#FFFFFF", true);
        t.setPadding(dp(10), dp(6), dp(10), dp(6));
        t.setBackground(roundStroke("#1E40AF", "#60A5FA", dp(99), 1));
        return t;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(Color.parseColor("#FFFFFF"));
        b.setBackground(round("#0F766E", dp(16)));
        b.setPadding(dp(10), dp(12), dp(10), dp(12));
        return b;
    }

    private Button smallButton(String value) {
        Button b = button(value);
        b.setTextSize(14);
        b.setBackground(roundStroke("#1D4ED8", "#93C5FD", dp(14), 1));
        return b;
    }

    private GradientDrawable round(String color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(color));
        g.setCornerRadius(radius);
        return g;
    }

    private GradientDrawable roundStroke(String color, String stroke, int radius, int sw) {
        GradientDrawable g = round(color, radius);
        g.setStroke(dp(sw), Color.parseColor(stroke));
        return g;
    }

    private GradientDrawable roundGradient(String start, String end, int radius) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor(start), Color.parseColor(end)}
        );
        g.setCornerRadius(radius);
        return g;
    }

    private LinearLayout.LayoutParams margin(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    private int getDrawableId(String name) {
        int id = getResources().getIdentifier(name, "drawable", getPackageName());
        return id == 0 ? android.R.drawable.sym_def_app_icon : id;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
