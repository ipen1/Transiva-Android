package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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
import android.widget.EditText;
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
import java.util.Locale;

public class DriverTripActivity extends Activity {

    private static final String BASE = "https://transiva.my.id/server/";
    private static final int TIMEOUT_MS = 20000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private LinearLayout root;
    private ProgressBar progressBar;
    private TextView statusText;
    private LinearLayout actionBox;

    private SessionManager sessionManager;
    private String driver = "";
    private String source = "orders";
    private String orderId = "";
    private String pickupLat = "";
    private String pickupLng = "";
    private String deliveryLat = "";
    private String deliveryLng = "";
    private JSONObject currentOrder;
    private boolean running = false;
    private boolean loading = false;

    private final Runnable polling = new Runnable() {
        @Override public void run() {
            if (!running) return;
            loadTrip(false);
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
        driver = firstNonEmpty(sessionManager.getUsername(), sessionManager.getName(), "");
        source = firstNonEmpty(getIntent().getStringExtra("source"), getIntent().getStringExtra("table"), "orders");
        orderId = firstNonEmpty(getIntent().getStringExtra("order_id"), getIntent().getStringExtra("id"), "");
        pickupLat = firstNonEmpty(getIntent().getStringExtra("pickup_lat"), "");
        pickupLng = firstNonEmpty(getIntent().getStringExtra("pickup_lng"), "");
        deliveryLat = firstNonEmpty(getIntent().getStringExtra("delivery_lat"), "");
        deliveryLng = firstNonEmpty(getIntent().getStringExtra("delivery_lng"), "");

        buildUi();
        loadTrip(true);
    }

    @Override protected void onResume() {
        super.onResume();
        running = true;
        mainHandler.removeCallbacks(polling);
        mainHandler.postDelayed(polling, 5000);
    }

    @Override protected void onPause() {
        running = false;
        mainHandler.removeCallbacks(polling);
        super.onPause();
    }

    private void buildUi() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F7FAFF"));
        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        TextView back = text("‹", 34, "#0B3A78", true);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round("#FFFFFF", dp(18)));
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(12), 0, 0, 0);
        top.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        titleBox.addView(text("Driver Trip", 23, "#0B3A78", true));
        titleBox.addView(text("Status perjalanan order native", 12, "#64748B", false));

        statusText = text("Memuat trip...", 14, "#64748B", false);
        LinearLayout statusCard = card();
        statusCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        statusCard.addView(statusText);
        addWithMargin(statusCard, 0, dp(16), 0, dp(12));

        actionBox = new LinearLayout(this);
        actionBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(actionBox, new LinearLayout.LayoutParams(-1, -2));

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(52), dp(52));
        lp.gravity = Gravity.CENTER;
        page.addView(progressBar, lp);
        setContentView(page);
    }

    private void loadTrip(boolean showLoading) {
        if (loading) return;
        loading = true;
        if (showLoading) setLoading(true);
        new Thread(() -> {
            JSONObject found = null;
            String error = "";
            try {
                String url = BASE + "driver_get_unified_orders.php?driver=" + enc(driver) + "&_=" + System.currentTimeMillis();
                JSONObject res = getJson(url);
                JSONArray active = res.optJSONArray("active_orders");
                JSONArray offers = res.optJSONArray("orders");
                found = findOrder(active, orderId, source);
                if (found == null) found = findOrder(offers, orderId, source);
                if (found == null && orderId.length() > 0) {
                    found = new JSONObject();
                    found.put("id", orderId);
                    found.put("source", source);
                    found.put("status", "taken");
                    found.put("pickup_lat", pickupLat);
                    found.put("pickup_lng", pickupLng);
                    found.put("delivery_lat", deliveryLat);
                    found.put("delivery_lng", deliveryLng);
                }
            } catch (Exception e) {
                error = "Koneksi gagal memuat trip.";
            }
            JSONObject finalFound = found;
            String finalError = error;
            mainHandler.post(() -> {
                loading = false;
                setLoading(false);
                if (finalFound != null) {
                    currentOrder = finalFound;
                    bindOrder(finalFound);
                } else {
                    statusText.setText(finalError.length() > 0 ? finalError : "Order tidak ditemukan.");
                    actionBox.removeAllViews();
                }
            });
        }).start();
    }

    private JSONObject findOrder(JSONArray arr, String id, String src) {
        if (arr == null) return null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String oid = firstNonEmpty(o.optString("id"), o.optString("order_id"), "");
            String osrc = firstNonEmpty(o.optString("source"), "orders");
            if (id.length() == 0 || (id.equals(oid) && src.equalsIgnoreCase(osrc))) return o;
        }
        return null;
    }

    private void bindOrder(JSONObject o) {
        source = firstNonEmpty(o.optString("source"), source, "orders");
        orderId = firstNonEmpty(o.optString("id"), o.optString("order_id"), orderId);
        pickupLat = firstNonEmpty(o.optString("pickup_lat"), o.optString("user_lat"), pickupLat);
        pickupLng = firstNonEmpty(o.optString("pickup_lng"), o.optString("user_lng"), pickupLng);
        deliveryLat = firstNonEmpty(o.optString("delivery_lat"), o.optString("destination_lat"), deliveryLat);
        deliveryLng = firstNonEmpty(o.optString("delivery_lng"), o.optString("destination_lng"), deliveryLng);

        String type = firstNonEmpty(o.optString("order_type"), o.optString("type"), source.equals("pickup_orders") ? "TransPickup" : "Order");
        String status = firstNonEmpty(o.optString("status"), "-");
        String pickup = firstNonEmpty(o.optString("pickup_address"), o.optString("pickup"), "-");
        String destination = firstNonEmpty(o.optString("destination_address"), o.optString("destination"), "-");
        int price = o.optInt("price", o.optInt("fare", o.optInt("total", 0)));

        statusText.setText(
                "Order #" + orderId + "\n" +
                "Layanan: " + type + "\n" +
                "Status: " + status + "\n" +
                "Jemput: " + pickup + "\n" +
                "Tujuan: " + destination + "\n" +
                "Harga: " + rupiah(price)
        );

        buildActions(status.toLowerCase(Locale.US));
    }

    private void buildActions(String status) {
        actionBox.removeAllViews();

        LinearLayout mapCard = card();
        mapCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        mapCard.addView(text("Navigasi", 17, "#0B3A78", true));
        Button pickup = outlineButton("📍 Buka Maps ke Pickup");
        pickup.setOnClickListener(v -> openMaps(pickupLat, pickupLng));
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(-1, dp(50));
        pLp.setMargins(0, dp(10), 0, 0);
        mapCard.addView(pickup, pLp);
        Button dest = primaryButton("🏁 Buka Maps ke Tujuan");
        dest.setOnClickListener(v -> openMaps(deliveryLat, deliveryLng));
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(-1, dp(50));
        dLp.setMargins(0, dp(8), 0, 0);
        mapCard.addView(dest, dLp);
        addWithMargin(mapCard, 0, 0, 0, dp(12));

        LinearLayout progress = card();
        progress.setPadding(dp(14), dp(14), dp(14), dp(14));
        progress.addView(text("Update Status", 17, "#0B3A78", true));

        if (status.equals("taken") || status.equals("accepted") || status.equals("driver_accepted")) {
            addStatusButton(progress, "✅ Tiba di Lokasi Pickup", "arrived_pickup");
        } else if (status.equals("arrived_pickup")) {
            addStatusButton(progress, "📦 Mulai Antar / Ambil Paket", "on_delivery");
        } else if (status.equals("on_delivery")) {
            addStatusButton(progress, "🏁 Tiba di Tujuan", "arrived_delivery");
        } else if (status.equals("arrived_delivery")) {
            if (source.equals("pickup_orders")) {
                Button otp = primaryButton("🔐 Selesaikan dengan OTP Penerima");
                otp.setOnClickListener(v -> askOtpAndFinish());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
                lp.setMargins(0, dp(10), 0, 0);
                progress.addView(otp, lp);
            } else {
                addStatusButton(progress, "✅ Selesaikan Order", "finished");
            }
        } else if (status.equals("finished") || status.equals("completed")) {
            TextView done = text("✅ Order sudah selesai.", 14, "#16A34A", true);
            done.setPadding(0, dp(10), 0, 0);
            progress.addView(done);
        } else {
            addStatusButton(progress, "✅ Tiba di Lokasi Pickup", "arrived_pickup");
        }

        addWithMargin(progress, 0, 0, 0, dp(12));

        Button refresh = outlineButton("Refresh Status");
        refresh.setOnClickListener(v -> loadTrip(true));
        root.addView(refresh, new LinearLayout.LayoutParams(-1, dp(50)));
    }

    private void addStatusButton(LinearLayout parent, String label, String status) {
        Button b = primaryButton(label);
        b.setOnClickListener(v -> updateStatus(status, ""));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.setMargins(0, dp(10), 0, 0);
        parent.addView(b, lp);
    }

    private void askOtpAndFinish() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(18);
        input.setGravity(Gravity.CENTER);
        input.setHint("OTP penerima");
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setBackground(roundStroke("#FFFFFF", "#B9DBFF", dp(16), 1));
        new AlertDialog.Builder(this)
                .setTitle("OTP Penerima")
                .setMessage("Masukkan OTP dari penerima untuk menyelesaikan TransPickup.")
                .setView(input)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Selesai", (d, w) -> updateStatus("finished", input.getText().toString().trim()))
                .show();
    }

    private void updateStatus(String status, String otp) {
        if (orderId.length() == 0) {
            showInfo("Gagal", "Order ID tidak ditemukan.");
            return;
        }
        setLoading(true);
        new Thread(() -> {
            boolean ok = false;
            String msg = "";
            try {
                JSONObject payload = new JSONObject();
                payload.put("source", source);
                payload.put("order_id", orderId);
                payload.put("driver", driver);
                payload.put("status", status);
                if (otp != null && otp.length() > 0) payload.put("receiver_otp", otp);
                JSONObject res = postJson(BASE + "driver_update_unified_status.php", payload);
                ok = res.optBoolean("success", false);
                msg = firstNonEmpty(res.optString("message"), ok ? "Status diperbarui" : "Gagal update status");
            } catch (Exception e) {
                msg = "Koneksi gagal update status.";
            }
            boolean finalOk = ok;
            String finalMsg = msg;
            mainHandler.post(() -> {
                setLoading(false);
                Toast.makeText(this, finalMsg, Toast.LENGTH_SHORT).show();
                if (!finalOk) showInfo("Info", finalMsg);
                loadTrip(false);
            });
        }).start();
    }

    private void openMaps(String lat, String lng) {
        if (!validCoord(lat, lng)) {
            showInfo("Lokasi", "Koordinat belum tersedia.");
            return;
        }
        try {
            Uri uri = Uri.parse("google.navigation:q=" + lat + "," + lng);
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.setPackage("com.google.android.apps.maps");
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps?q=" + lat + "," + lng)));
        }
    }

    private boolean validCoord(String lat, String lng) {
        try {
            double a = Double.parseDouble(firstNonEmpty(lat, "0"));
            double b = Double.parseDouble(firstNonEmpty(lng, "0"));
            return a != 0 && b != 0;
        } catch (Exception e) { return false; }
    }

    private JSONObject getJson(String urlText) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(urlText).openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestMethod("GET");
            c.setRequestProperty("Accept", "application/json");
            InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
            return new JSONObject(readStream(is));
        } finally { if (c != null) c.disconnect(); }
    }

    private JSONObject postJson(String urlText, JSONObject payload) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(urlText).openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestMethod("POST");
            c.setDoInput(true);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            c.setRequestProperty("Accept", "application/json");
            OutputStream os = c.getOutputStream();
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
            w.write(payload == null ? "{}" : payload.toString());
            w.flush(); w.close(); os.close();
            InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
            return new JSONObject(readStream(is));
        } finally { if (c != null) c.disconnect(); }
    }

    private String readStream(InputStream is) throws Exception {
        if (is == null) return "{}";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        String body = sb.toString().trim();
        return body.length() == 0 ? "{}" : body;
    }

    private String enc(String value) {
        try { return URLEncoder.encode(value == null ? "" : value, "UTF-8"); }
        catch (Exception e) { return ""; }
    }

    private void setLoading(boolean b) { if (progressBar != null) progressBar.setVisibility(b ? View.VISIBLE : View.GONE); }
    private void showInfo(String title, String msg) { new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show(); }
    private LinearLayout card() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(22), 1)); v.setElevation(dp(2)); return v; }
    private void addWithMargin(View v, int l, int t, int r, int b) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(l,t,r,b); root.addView(v, lp); }
    private TextView text(String s, int sp, String color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(Color.parseColor(color)); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private Button primaryButton(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(roundGradient("#086BFF", "#2EA2FF", dp(18))); return b; }
    private Button outlineButton(String s) { Button b = primaryButton(s); b.setTextColor(Color.parseColor("#0B7CFF")); b.setBackground(roundStroke("#FFFFFF", "#9DCAFF", dp(18), 1)); return b; }
    private GradientDrawable round(String color, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(color)); g.setCornerRadius(radius); return g; }
    private GradientDrawable roundStroke(String color, String stroke, int radius, int width) { GradientDrawable g = round(color, radius); g.setStroke(dp(width), Color.parseColor(stroke)); return g; }
    private GradientDrawable roundGradient(String start, String end, int radius) { GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor(start), Color.parseColor(end)}); g.setCornerRadius(radius); return g; }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private String rupiah(int value) { return "Rp " + NumberFormat.getNumberInstance(new Locale("id", "ID")).format(value); }
    private String firstNonEmpty(String... values) { if (values == null) return ""; for (String v : values) if (v != null && v.trim().length() > 0 && !"null".equalsIgnoreCase(v.trim())) return v.trim(); return ""; }
}
