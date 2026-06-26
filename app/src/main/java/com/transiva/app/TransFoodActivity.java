package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TransFoodActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final int TIMEOUT_MS = 20000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FrameLayout page;
    private LinearLayout root;
    private ProgressBar progressBar;

    private final List<JSONObject> restaurants = new ArrayList<>();
    private final List<JSONObject> menus = new ArrayList<>();
    private final List<CartItem> cart = new ArrayList<>();
    private JSONObject activeRestaurant;

    private int userId = 0;
    private String username = "User";
    private String deliveryMode = "standard";
    private String paymentMethod = "cash";
    private double deliveryFee = 0;
    private double standardFee = 0;
    private double hematFee = 0;
    private double distanceKm = 0;

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
        showRestaurantList();
        loadRestaurants();
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
            android.content.SharedPreferences sp = getSharedPreferences("transiva", MODE_PRIVATE);
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

    private void showRestaurantList() {
        root.removeAllViews();
        buildTopBar("Trans Food", "Pesan makanan favorit di sekitar kamu", true);
        LinearLayout hero = card();
        hero.setPadding(dp(18), dp(16), dp(18), dp(16));
        hero.setBackground(roundGradient("#FFFFFF", "#EEF7FF", dp(24)));
        hero.addView(text("🍔 Trans Food", 22, "#0B3A78", true));
        TextView sub = text("Pilih restoran, tambah menu, hitung ongkir, lalu checkout seperti versi web.", 13, "#64748B", false);
        sub.setPadding(0, dp(6), 0, 0);
        hero.addView(sub);
        addWithMargin(hero, 0, 0, 0, dp(14));

        if (restaurants.isEmpty()) {
            addStatus("Memuat restoran...");
        } else {
            for (JSONObject r : restaurants) addRestaurantCard(r);
        }
    }

    private void addRestaurantCard(JSONObject r) {
        boolean open = r.optInt("is_open", 1) == 1;
        LinearLayout card = card();
        card.setPadding(0, 0, 0, dp(14));
        card.setClickable(true);
        card.setAlpha(open ? 1f : 0.62f);
        card.setOnClickListener(v -> {
            if (!open) {
                showInfo("Restoran Tutup", "Restoran sedang tidak menerima orderan.");
                return;
            }
            activeRestaurant = r;
            cart.clear();
            showMenuPage();
            loadMenus(r.optInt("id", 0));
        });

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackgroundColor(Color.parseColor("#EAF4FF"));
        card.addView(img, new LinearLayout.LayoutParams(-1, dp(132)));
        loadImage(img, absoluteUrl(firstNonEmpty(r.optString("banner"), "assets/default-food.png")));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(12), dp(14), 0);
        card.addView(body);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(row);
        TextView name = text(firstNonEmpty(r.optString("name"), "Restoran"), 18, "#0F172A", true);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = text(open ? "Buka" : "Tutup", 11, open ? "#0B7CFF" : "#EF4444", true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        badge.setBackground(roundStroke(open ? "#EAF4FF" : "#FFF1F2", open ? "#B9DBFF" : "#FECACA", dp(20), 1));
        row.addView(badge);
        TextView info = text(open ? ("⭐ " + r.optString("rating", "0.0") + " • " + firstNonEmpty(r.optString("duration"), "15 menit")) : "🔴 Tidak menerima orderan", 13, "#64748B", false);
        info.setPadding(0, dp(6), 0, 0);
        body.addView(info);
        addWithMargin(card, 0, 0, 0, dp(14));
    }

    private void showMenuPage() {
        root.removeAllViews();
        buildTopBar("Detail Restoran", firstNonEmpty(activeRestaurant != null ? activeRestaurant.optString("name") : "", "Menu makanan"), true);
        if (menus.isEmpty()) addStatus("Memuat menu...");
        else renderMenus();
    }

    private void renderMenus() {
        root.removeViews(1, Math.max(0, root.getChildCount() - 1));
        LinearLayout resto = card();
        resto.setPadding(dp(16), dp(14), dp(16), dp(14));
        resto.addView(text(firstNonEmpty(activeRestaurant.optString("name"), "Restoran"), 19, "#0B3A78", true));
        resto.addView(text((hasRestoLocation() ? "📍 Lokasi resto tersedia" : "⚠️ Lokasi resto belum tersedia"), 13, "#64748B", false));
        addWithMargin(resto, 0, 0, 0, dp(14));
        for (JSONObject m : menus) addMenuCard(m);
        buildCartBar();
    }

    private void addMenuCard(JSONObject m) {
        boolean active = m.optInt("is_active", 1) == 1;
        LinearLayout card = card();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setAlpha(active ? 1f : 0.55f);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackgroundColor(Color.parseColor("#EAF4FF"));
        card.addView(img, new LinearLayout.LayoutParams(dp(92), dp(92)));
        loadImage(img, absoluteUrl(firstNonEmpty(m.optString("image"), "assets/no-image.png")));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), 0, 0, 0);
        card.addView(body, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(text(firstNonEmpty(m.optString("name"), "Menu"), 16, "#0F172A", true));
        body.addView(text(firstNonEmpty(m.optString("category"), "Menu"), 12, "#94A3B8", false));
        TextView price = text(rupiah(m.optDouble("price", 0)), 15, "#0B7CFF", true);
        price.setPadding(0, dp(6), 0, dp(6));
        body.addView(price);
        if (!active) {
            body.addView(text("Tidak tersedia", 12, "#EF4444", true));
        } else {
            LinearLayout qty = new LinearLayout(this);
            qty.setGravity(Gravity.CENTER_VERTICAL);
            Button minus = tinyButton("−");
            Button plus = tinyButton("+");
            TextView value = text(String.valueOf(getQty(m.optInt("id", 0))), 15, "#0F172A", true);
            value.setGravity(Gravity.CENTER);
            qty.addView(minus, new LinearLayout.LayoutParams(dp(36), dp(34)));
            qty.addView(value, new LinearLayout.LayoutParams(dp(42), dp(34)));
            qty.addView(plus, new LinearLayout.LayoutParams(dp(36), dp(34)));
            body.addView(qty);
            minus.setOnClickListener(v -> { changeQty(m, -1); renderMenus(); });
            plus.setOnClickListener(v -> { changeQty(m, 1); renderMenus(); });
        }
        addWithMargin(card, 0, 0, 0, dp(12));
    }

    private void buildCartBar() {
        if (cart.isEmpty()) return;
        LinearLayout bar = card();
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text("Total Belanja", 12, "#64748B", false));
        left.addView(text(rupiah(foodTotal()), 20, "#0B3A78", true));
        bar.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        Button checkout = primaryButton("Checkout");
        checkout.setOnClickListener(v -> showCheckout());
        bar.addView(checkout, new LinearLayout.LayoutParams(dp(130), dp(48)));
        addWithMargin(bar, 0, dp(8), 0, 0);
    }

    private void showCheckout() {
        root.removeAllViews();
        buildTopBar("Checkout", "Cek pesanan dan pilih pengantaran", true);
        addStatus("Menghitung ongkir...");
        calculateOngkirThenRender();
    }

    private void renderCheckout() {
        root.removeViews(1, Math.max(0, root.getChildCount() - 1));
        LinearLayout info = card();
        info.setPadding(dp(16), dp(14), dp(16), dp(14));
        info.addView(text("Resto", 12, "#64748B", true));
        info.addView(text(firstNonEmpty(activeRestaurant.optString("name"), "Restoran"), 16, "#0F172A", true));
        TextView jarak = text(distanceKm > 0 ? ("Jarak pengantaran: " + String.format(Locale.US, "%.2f", distanceKm) + " km") : "Lokasi belum lengkap, ongkir belum bisa dihitung", 13, distanceKm > 0 ? "#64748B" : "#EF4444", false);
        jarak.setPadding(0, dp(8), 0, 0);
        info.addView(jarak);
        addWithMargin(info, 0, 0, 0, dp(14));

        for (CartItem item : cart) {
            LinearLayout row = card();
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(text(item.name, 15, "#0F172A", true));
            col.addView(text(item.qty + " x " + rupiah(item.price), 12, "#64748B", false));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(text(rupiah(item.price * item.qty), 14, "#0B7CFF", true));
            addWithMargin(row, 0, 0, 0, dp(10));
        }

        LinearLayout delivery = card();
        delivery.setPadding(dp(14), dp(14), dp(14), dp(14));
        delivery.addView(text("Pilih Pengantaran", 16, "#0B3A78", true));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, 0);
        Button standard = choiceButton("Standar\n" + rupiah(standardFee), "standard".equals(deliveryMode));
        Button hemat = choiceButton("Hemat\n" + rupiah(hematFee), "hemat".equals(deliveryMode));
        standard.setOnClickListener(v -> { deliveryMode = "standard"; deliveryFee = standardFee; renderCheckout(); });
        hemat.setOnClickListener(v -> { deliveryMode = "hemat"; deliveryFee = hematFee; renderCheckout(); });
        row.addView(standard, new LinearLayout.LayoutParams(0, dp(60), 1));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(0, dp(60), 1); hlp.setMargins(dp(10),0,0,0);
        row.addView(hemat, hlp);
        delivery.addView(row);
        addWithMargin(delivery, 0, 0, 0, dp(14));

        LinearLayout pay = card();
        pay.setPadding(dp(14), dp(14), dp(14), dp(14));
        pay.addView(text("Pilih Pembayaran", 16, "#0B3A78", true));
        LinearLayout prow = new LinearLayout(this);
        prow.setOrientation(LinearLayout.HORIZONTAL);
        prow.setPadding(0, dp(10), 0, 0);
        Button cash = choiceButton("Tunai", "cash".equals(paymentMethod));
        Button balance = choiceButton("Saldo", "balance".equals(paymentMethod));
        cash.setOnClickListener(v -> { paymentMethod = "cash"; renderCheckout(); });
        balance.setOnClickListener(v -> { paymentMethod = "balance"; renderCheckout(); });
        prow.addView(cash, new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, dp(52), 1); blp.setMargins(dp(10),0,0,0);
        prow.addView(balance, blp);
        pay.addView(prow);
        addWithMargin(pay, 0, 0, 0, dp(14));

        LinearLayout total = card();
        total.setPadding(dp(16), dp(14), dp(16), dp(14));
        total.addView(summaryLine("Total makanan", foodTotal()));
        total.addView(summaryLine("Ongkir", deliveryFee));
        total.addView(summaryLine("Total bayar", foodTotal() + deliveryFee));
        Button order = primaryButton("Buat Pesanan");
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-1, dp(52)); olp.setMargins(0, dp(12), 0, 0);
        total.addView(order, olp);
        order.setOnClickListener(v -> createFoodOrder());
        addWithMargin(total, 0, 0, 0, dp(20));
    }

    private LinearLayout summaryLine(String label, double value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));
        row.addView(text(label, 14, "#64748B", false), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text(rupiah(value), 15, "#0F172A", true));
        return row;
    }

    private void loadRestaurants() {
        setLoading(true);
        new Thread(() -> {
            try {
                JSONObject res = getJson(BASE_URL + "server/get_food_restaurants.php?v=" + System.currentTimeMillis());
                restaurants.clear();
                JSONArray arr = res.optJSONArray("restaurants");
                if (res.optBoolean("success", false) && arr != null) {
                    for (int i = 0; i < arr.length(); i++) restaurants.add(arr.getJSONObject(i));
                    mainHandler.post(() -> { setLoading(false); showRestaurantList(); });
                } else throw new Exception(firstNonEmpty(res.optString("message"), "Gagal memuat restoran"));
            } catch (Exception e) {
                mainHandler.post(() -> { setLoading(false); root.removeAllViews(); buildTopBar("Trans Food", "", true); addStatus("Koneksi gagal memuat restoran"); showInfo("Gagal", e.getMessage()); });
            }
        }).start();
    }

    private void loadMenus(int restaurantId) {
        setLoading(true);
        new Thread(() -> {
            try {
                JSONObject res = getJson(BASE_URL + "server/get_food_menus.php?restaurant_id=" + restaurantId + "&v=" + System.currentTimeMillis());
                menus.clear();
                if (res.optJSONObject("restaurant") != null) activeRestaurant = res.optJSONObject("restaurant");
                JSONArray arr = res.optJSONArray("menus");
                if (res.optBoolean("success", false) && arr != null) {
                    for (int i = 0; i < arr.length(); i++) menus.add(arr.getJSONObject(i));
                    mainHandler.post(() -> { setLoading(false); showMenuPage(); });
                } else throw new Exception(firstNonEmpty(res.optString("message"), "Gagal memuat menu"));
            } catch (Exception e) {
                mainHandler.post(() -> { setLoading(false); showMenuPage(); addStatus("Gagal memuat menu"); showInfo("Gagal", e.getMessage()); });
            }
        }).start();
    }

    private void calculateOngkirThenRender() {
        setLoading(true);
        new Thread(() -> {
            try {
                String url = BASE_URL + "server/calculateOngkir.php?service_type=Transbike" +
                        "&restaurant_id=" + Uri.encode(String.valueOf(activeRestaurant.optInt("id", 0))) +
                        "&user_id=" + Uri.encode(String.valueOf(userId)) +
                        "&delivery_mode=" + Uri.encode(deliveryMode) +
                        "&v=" + System.currentTimeMillis();
                JSONObject res = getJson(url);
                if (!res.optBoolean("success", false)) throw new Exception(firstNonEmpty(res.optString("message"), "Gagal menghitung ongkir"));
                deliveryFee = res.optDouble("price", 0);
                standardFee = res.optDouble("standard_price", deliveryFee);
                hematFee = res.optDouble("hemat_price", deliveryFee);
                distanceKm = res.optDouble("distance_km", 0);
                mainHandler.post(() -> { setLoading(false); renderCheckout(); });
            } catch (Exception e) {
                mainHandler.post(() -> { setLoading(false); deliveryFee = standardFee = hematFee = 0; distanceKm = 0; renderCheckout(); showInfo("Ongkir", e.getMessage()); });
            }
        }).start();
    }

    private void createFoodOrder() {
        if (userId <= 0) { showInfo("Login", "User ID tidak ditemukan. Silakan login ulang."); return; }
        if (activeRestaurant == null || activeRestaurant.optInt("id", 0) <= 0) { showInfo("Gagal", "Restoran tidak valid."); return; }
        if (cart.isEmpty()) { showInfo("Keranjang kosong", "Tambahkan menu terlebih dahulu."); return; }
        setLoading(true);
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("user_id", userId);
                payload.put("restaurant_id", activeRestaurant.optInt("id", 0));
                payload.put("delivery_mode", deliveryMode);
                payload.put("payment_method", paymentMethod);
                JSONArray items = new JSONArray();
                for (CartItem c : cart) {
                    JSONObject o = new JSONObject();
                    o.put("id", c.id);
                    o.put("qty", c.qty);
                    items.put(o);
                }
                payload.put("items", items);
                JSONObject res = postJson(BASE_URL + "server/create_food_order.php", payload);
                boolean ok = res.optBoolean("success", false);
                String msg = firstNonEmpty(res.optString("message"), ok ? "Pesanan berhasil dibuat" : "Gagal membuat pesanan");
                mainHandler.post(() -> {
                    setLoading(false);
                    if (ok) {
                        new AlertDialog.Builder(this)
                                .setTitle("Berhasil")
                                .setMessage(msg + "\n\nOrder ID: " + res.optString("order_id", "-"))
                                .setPositiveButton("OK", (d, w) -> finish())
                                .show();
                    } else showInfo("Gagal", msg);
                });
            } catch (Exception e) {
                mainHandler.post(() -> { setLoading(false); showInfo("Error", "Koneksi gagal membuat pesanan makanan."); });
            }
        }).start();
    }

    private JSONObject getJson(String urlText) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
        c.setConnectTimeout(TIMEOUT_MS); c.setReadTimeout(TIMEOUT_MS); c.setRequestMethod("GET");
        return new JSONObject(readStream(c));
    }

    private JSONObject postJson(String urlText, JSONObject payload) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
        c.setConnectTimeout(TIMEOUT_MS); c.setReadTimeout(TIMEOUT_MS); c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setDoOutput(true);
        OutputStream os = c.getOutputStream();
        os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        os.flush(); os.close();
        return new JSONObject(readStream(c));
    }

    private String readStream(HttpURLConnection c) throws Exception {
        InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close(); c.disconnect();
        return sb.toString();
    }

    private void loadImage(ImageView view, String urlText) {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
                c.setConnectTimeout(10000); c.setReadTimeout(10000);
                Bitmap bm = BitmapFactory.decodeStream(c.getInputStream());
                mainHandler.post(() -> { if (bm != null) view.setImageBitmap(bm); });
            } catch (Exception ignored) {}
        }).start();
    }

    private String absoluteUrl(String value) {
        value = firstNonEmpty(value, "assets/no-image.png").trim();
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        if (value.startsWith("/")) return BASE_URL.substring(0, BASE_URL.length() - 1) + value;
        return BASE_URL + value;
    }

    private void changeQty(JSONObject menu, int delta) {
        int id = menu.optInt("id", 0);
        CartItem found = null;
        for (CartItem c : cart) if (c.id == id) found = c;
        if (found == null && delta > 0) {
            found = new CartItem(); found.id = id; found.restaurantId = menu.optInt("restaurant_id", activeRestaurant.optInt("id", 0));
            found.name = firstNonEmpty(menu.optString("name"), "Menu"); found.price = menu.optDouble("price", 0); found.qty = 0; cart.add(found);
        }
        if (found != null) {
            found.qty += delta;
            if (found.qty <= 0) cart.remove(found);
        }
    }

    private int getQty(int id) { for (CartItem c : cart) if (c.id == id) return c.qty; return 0; }
    private double foodTotal() { double t = 0; for (CartItem c : cart) t += c.price * c.qty; return t; }
    private boolean hasRestoLocation() { return activeRestaurant != null && activeRestaurant.optString("latitude").length() > 0 && activeRestaurant.optString("longitude").length() > 0; }

    private void buildTopBar(String title, String sub, boolean back) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(16));
        if (back) {
            TextView b = text("‹", 34, "#0B3A78", true);
            b.setGravity(Gravity.CENTER);
            b.setBackground(round("#FFFFFF", dp(18)));
            b.setOnClickListener(v -> handleBack());
            row.addView(b, new LinearLayout.LayoutParams(dp(44), dp(44)));
        }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, 0, 0);
        col.addView(text(title, 23, "#0B3A78", true));
        if (sub != null && sub.length() > 0) col.addView(text(sub, 12, "#64748B", false));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void handleBack() {
        if (activeRestaurant == null) { finish(); return; }
        if (!cart.isEmpty() && root.getChildCount() > 0) { showMenuPage(); return; }
        activeRestaurant = null; menus.clear(); cart.clear(); showRestaurantList();
    }

    @Override public void onBackPressed() { handleBack(); }

    private void addStatus(String message) {
        TextView t = text(message, 14, "#64748B", false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(24), dp(16), dp(24));
        t.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(20), 1));
        addWithMargin(t, 0, 0, 0, dp(12));
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackground(roundStroke("#FFFFFF", "#E2ECF8", dp(22), 1));
        v.setElevation(dp(2));
        return v;
    }

    private TextView text(String s, int sp, String color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(Color.parseColor(color));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }

    private Button primaryButton(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(round("#0B7CFF", dp(18))); return b; }
    private Button tinyButton(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(18); b.setTextColor(Color.parseColor("#0B7CFF")); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(roundStroke("#EAF4FF", "#B9DBFF", dp(14), 1)); return b; }
    private Button choiceButton(String s, boolean active) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(Color.parseColor(active ? "#FFFFFF" : "#0B3A78")); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(roundStroke(active ? "#0B7CFF" : "#FFFFFF", active ? "#0B7CFF" : "#D7E6F8", dp(18), 1)); return b; }

    private GradientDrawable round(String color, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(color)); g.setCornerRadius(radius); return g; }
    private GradientDrawable roundStroke(String color, String stroke, int radius, int sw) { GradientDrawable g = round(color, radius); g.setStroke(dp(sw), Color.parseColor(stroke)); return g; }
    private GradientDrawable roundGradient(String c1, String c2, int radius) { GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.parseColor(c1), Color.parseColor(c2)}); g.setCornerRadius(radius); return g; }

    private void addWithMargin(View v, int l, int t, int r, int b) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(l,t,r,b); root.addView(v, lp); }
    private void setLoading(boolean b) { if (progressBar != null) progressBar.setVisibility(b ? View.VISIBLE : View.GONE); }
    private void showInfo(String title, String msg) { try { new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show(); } catch (Exception ignored) {} }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private String rupiah(double v) { return "Rp " + NumberFormat.getNumberInstance(new Locale("id", "ID")).format((long) v); }
    private String firstNonEmpty(String... values) { if (values == null) return ""; for (String s : values) if (s != null && s.trim().length() > 0 && !"null".equalsIgnoreCase(s.trim())) return s.trim(); return ""; }

    private static class CartItem { int id; int restaurantId; String name; double price; int qty; }
}
