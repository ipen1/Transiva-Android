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
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
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
import java.util.Locale;

public class CustomerDashboardActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String PREF_NAME = "transiva";
    private static final int TIMEOUT_MS = 20000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private LinearLayout root;
    private TextView usernameText;
    private TextView verifiedText;
    private TextView balanceText;
    private TextView statusText;
    private TextView locationText;
    private EditText searchInput;
    private ProgressBar progressBar;

    private String username = "User";
    private int userId = 0;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setStatusBarColor(Color.parseColor("#071426"));
            getWindow().setNavigationBarColor(Color.parseColor("#071426"));
        } catch (Exception ignored) {}
        loadSession();
        buildLayout();
        loadBalance();
        loadOrderStatus();
    }

    private void loadSession() {
        try {
            SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
            username = firstNonEmpty(
                    sp.getString("username", ""),
                    sp.getString("player_username", ""),
                    sp.getString("user_username", ""),
                    "User"
            );
            userId = sp.getInt("id", sp.getInt("user_id", 0));
        } catch (Exception ignored) {}
    }

    private void buildLayout() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F3F8FF"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(22), dp(16), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        buildHeader();
        buildSearch();
        buildPayCard();
        buildWeatherCard();
        buildMenuGrid();
        buildStatusCard();
        buildBottomActions();

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams pLp = new FrameLayout.LayoutParams(dp(50), dp(50));
        pLp.gravity = Gravity.CENTER;
        page.addView(progressBar, pLp);

        setContentView(page);
    }

    private void buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, -2, 1);
        header.addView(left, leftLp);

        TextView welcome = text("Selamat Datang 👋", 13, "#64748B", false);
        left.addView(welcome);

        usernameText = text(username, 23, "#0B3A78", true);
        usernameText.setSingleLine(true);
        left.addView(usernameText);

        verifiedText = text("Verified", 11, "#0B7CFF", true);
        verifiedText.setPadding(dp(10), dp(4), dp(10), dp(4));
        verifiedText.setBackground(roundStroke("#EAF4FF", "#B9DBFF", dp(20), 1));
        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(-2, -2);
        vLp.setMargins(0, dp(4), 0, 0);
        left.addView(verifiedText, vLp);

        locationText = text("📍 Lokasi Kamu", 12, "#0B3A78", true);
        locationText.setGravity(Gravity.CENTER);
        locationText.setPadding(dp(10), dp(8), dp(10), dp(8));
        locationText.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(20), 1));
        header.addView(locationText, new LinearLayout.LayoutParams(-2, -2));
    }

    private void buildSearch() {
        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(14);
        searchInput.setTextColor(Color.parseColor("#0F172A"));
        searchInput.setHintTextColor(Color.parseColor("#94A3B8"));
        searchInput.setHint("Cari makanan, toko, kurir...");
        searchInput.setPadding(dp(16), 0, dp(16), 0);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setBackground(roundStroke("#FFFFFF", "#D8E4F2", dp(22), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.setMargins(0, dp(16), 0, dp(14));
        root.addView(searchInput, lp);
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            String q = searchInput.getText().toString().trim();
            if (q.length() == 0) {
                showInfo("Pencarian", "Masukkan kata kunci terlebih dahulu.");
            } else {
                openWeb("?route=searchFood&keyword=" + Uri.encode(q));
            }
            return true;
        });
    }

    private void buildPayCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(roundGradient("#086BFF", "#2EA2FF", dp(24)));
        card.setElevation(dp(4));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, 0, 0, dp(14));
        root.addView(card, cardLp);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

        TextView label = text("💳 Transiva Pay", 13, "#EAF4FF", true);
        col.addView(label);

        balanceText = text("Memuat saldo...", 24, "#FFFFFF", true);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(-1, -2);
        bLp.setMargins(0, dp(4), 0, 0);
        col.addView(balanceText, bLp);

        TextView logo = text("T", 22, "#0B7CFF", true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(round("#FFFFFF", dp(22)));
        row.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView sub = text("Bayar layanan Transiva lebih praktis dengan saldo aplikasi.", 12, "#EAF4FF", false);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-1, -2);
        sLp.setMargins(0, dp(8), 0, dp(12));
        card.addView(sub, sLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        Button topup = smallButton("+ Isi Saldo", "#FFFFFF", "#0B7CFF");
        Button refresh = smallButton("Refresh", "#EAF4FF", "#FFFFFF");
        actions.addView(topup, new LinearLayout.LayoutParams(0, dp(42), 1));
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(0, dp(42), 1);
        rLp.setMargins(dp(10), 0, 0, 0);
        actions.addView(refresh, rLp);
        topup.setOnClickListener(v -> openWeb("?route=deposit"));
        refresh.setOnClickListener(v -> loadBalance());
    }

    private void buildWeatherCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(22), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(14));
        root.addView(card, lp);

        TextView title = text("🛒 Transiva Lokal", 16, "#0B3A78", true);
        TextView sub = text("Kurir • Makanan • Titip Belanja siap antar kebutuhanmu", 12, "#64748B", false);
        sub.setPadding(0, dp(4), 0, 0);
        card.addView(title);
        card.addView(sub);
    }

    private void buildMenuGrid() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        LinearLayout.LayoutParams gLp = new LinearLayout.LayoutParams(-1, -2);
        gLp.setMargins(0, 0, 0, dp(14));
        root.addView(grid, gLp);

        addMenu(grid, "TransRide", "ic_transride", "?route=kurir");
        addMenu(grid, "TransCar", "ic_transcar", "?route=mobil");
        addMenu(grid, "TransFood", "ic_transfood", "?route=food");
        addMenu(grid, "TransTour", "ic_transtour", "?route=wisata");
        addMenu(grid, "Laundry", "ic_translaundry", "?route=laundry");
        addMenu(grid, "Pickup", "ic_transpickup", "soon");
    }

    private void addMenu(GridLayout grid, String title, String iconName, String route) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(8), dp(12), dp(8), dp(10));
        item.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(22), 1));
        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(v -> {
            if ("soon".equals(route)) showInfo("Segera Hadir", "TransPickup sedang dikembangkan.");
            else openWeb(route);
        });

        int iconRes = getDrawableId(iconName);
        if (iconRes != 0) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(iconRes);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            item.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        } else {
            TextView fallback = text("●", 28, "#0B7CFF", true);
            fallback.setGravity(Gravity.CENTER);
            item.addView(fallback, new LinearLayout.LayoutParams(dp(42), dp(42)));
        }

        TextView label = text(title, 12, "#0B3A78", true);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(8), 0, 0);
        item.addView(label, new LinearLayout.LayoutParams(-1, -2));

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = (getResources().getDisplayMetrics().widthPixels - dp(50)) / 3;
        lp.height = dp(104);
        lp.setMargins(dp(4), dp(4), dp(4), dp(8));
        grid.addView(item, lp);
    }

    private void buildStatusCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(22), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(14));
        root.addView(card, lp);

        TextView title = text("Status Pesanan", 17, "#0B3A78", true);
        card.addView(title);
        statusText = text("Memuat status pesanan...", 13, "#64748B", false);
        statusText.setPadding(0, dp(8), 0, 0);
        card.addView(statusText);
    }

    private void buildBottomActions() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
        Button history = primaryButton("Riwayat");
        Button profile = outlineButton("Profil");
        row.addView(history, new LinearLayout.LayoutParams(0, dp(50), 1));
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(0, dp(50), 1);
        pLp.setMargins(dp(10), 0, 0, 0);
        row.addView(profile, pLp);
        history.setOnClickListener(v -> openWeb("?route=history"));
        profile.setOnClickListener(v -> openWeb("?route=profile"));
    }

    private void loadBalance() {
        if (loading) return;
        loading = true;
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            String result = rupiah(0);
            try {
                JSONObject json = getJson(BASE_URL + "server/getBalance.php?username=" + Uri.encode(username));
                if (json.optBoolean("success", false)) result = rupiah(json.optDouble("balance", 0));
            } catch (Exception ignored) {}
            String finalResult = result;
            mainHandler.post(() -> {
                loading = false;
                progressBar.setVisibility(View.GONE);
                balanceText.setText(finalResult);
            });
        }).start();
    }

    private void loadOrderStatus() {
        new Thread(() -> {
            String text = "Belum ada pesanan aktif";
            try {
                if (userId > 0) {
                    JSONObject json = getJson(BASE_URL + "server/get_user_orders.php?user_id=" + userId);
                    JSONArray orders = json.optJSONArray("orders");
                    if (json.optBoolean("success", false) && orders != null && orders.length() > 0) {
                        JSONObject o = orders.optJSONObject(0);
                        if (o != null) {
                            text = "Order #" + firstNonEmpty(o.optString("order_id"), o.optString("id"), "-") + "\nStatus: " + o.optString("status", "-");
                        }
                    }
                }
            } catch (Exception ignored) {}
            String finalText = text;
            mainHandler.post(() -> statusText.setText(finalText));
        }).start();
    }

    private JSONObject getJson(String urlText) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlText);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
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

    private void openWeb(String route) {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("native_route", route);
            intent.putExtra("url", BASE_URL + route);
            startActivity(intent);
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BASE_URL + route)));
        }
    }

    private void showInfo(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
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

    private Button smallButton(String value, String bg, String fg) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.parseColor(fg));
        b.setBackground(roundStroke(bg, "#FFFFFF", dp(16), 1));
        return b;
    }

    private TextView text(String value, int sp, String color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(Color.parseColor(color));
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
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
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor(start), Color.parseColor(end)});
        gd.setCornerRadius(radius);
        return gd;
    }

    private String rupiah(double value) {
        try {
            NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));
            nf.setMaximumFractionDigits(0);
            return nf.format(value).replace("Rp", "Rp ");
        } catch (Exception e) {
            return "Rp " + Math.round(value);
        }
    }

    private String firstNonEmpty(String... values) {
        for (String v : values) if (v != null && v.trim().length() > 0) return v.trim();
        return "";
    }

    private int getDrawableId(String name) {
        try { return getResources().getIdentifier(name, "drawable", getPackageName()); } catch (Exception e) { return 0; }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
