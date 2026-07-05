package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.RingtoneManager;
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DriverDashboardActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String SERVER = BASE_URL + "server/";
    private static final int TIMEOUT_MS = 15000;
    private static final int REQ_NOTIFICATION = 8701;
    private static final int REQ_LOCATION = 8702;
    private static final String CHANNEL_ORDER = "transiva_driver_orders";
    private static final String CHANNEL_BALANCE = "transiva_driver_balance";
    /*
     * Real-time dashboard fix:
     * Jika customer cancel, endpoint server tidak lagi mengembalikan order itu.
     * Versi lama menunggu 12x refresh @5 detik, sehingga hilang 25-60 detik.
     * Versi ini refresh 2 detik dan cache hanya tahan 2 miss, jadi order cancel
     * hilang sekitar 2-4 detik tanpa membuat tawaran kedip terlalu agresif.
     */
    private static final int OFFER_MISSING_LIMIT = 2;
    private static final int OFFER_MAX_CACHE = 20;
    private static final long ASSIGN_MIN_INTERVAL_MS = 10000L;
    private static final long REFRESH_INTERVAL_MS = 2000L;
    private static final long LOCATION_INTERVAL_MS = 5000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private LocationManager locationManager;
    private SessionManager sessionManager;

    private LinearLayout root, orderBox, activeBox;
    private TextView nameText, levelText, balanceText, onlineText, pendingDepositText, pendingWithdrawText;
    private Switch onlineSwitch;
    private ProgressBar progressBar;

    private String username = "";
    private String driverType = "motor";
    private boolean driverOnline = false;
    private boolean loading = false;
    private boolean firstLoadDone = false;
    private long lastBalance = -1;
    private long lastAssignAt = 0L;

    private final Set<String> notifiedIds = new HashSet<>();
    private final Map<String, JSONObject> stableOfferMap = new LinkedHashMap<>();
    private final Map<String, Integer> stableMissingMap = new LinkedHashMap<>();
    private final Map<String, String> stableTableMap = new LinkedHashMap<>();


    private final LocationListener locationListener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            sendDriverLocation(location);
        }

        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {}
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    };

    private final Runnable locationRunnable = new Runnable() {
        @Override public void run() {
            updateDriverLocation();
            mainHandler.postDelayed(this, LOCATION_INTERVAL_MS);
        }
    };

    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            refreshDriverData(false);
            mainHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setStatusBarColor(Color.WHITE);
            getWindow().setNavigationBarColor(Color.WHITE);
            if (Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        } catch (Exception ignored) {}

        sessionManager = new SessionManager(this);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        loadSession();
        createChannels();
        askNotificationPermissionIfNeeded();
        askLocationPermissionIfNeeded();
        buildUi();
        refreshDriverData(true);
    }

    @Override protected void onResume() {
        super.onResume();
        mainHandler.removeCallbacks(refreshRunnable);
        mainHandler.removeCallbacks(locationRunnable);
        refreshDriverData(false);
        startLocationUpdate();
        mainHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    @Override protected void onPause() {
        mainHandler.removeCallbacks(refreshRunnable);
        stopLocationUpdate();
        super.onPause();
    }

    @Override protected void onDestroy() {
        mainHandler.removeCallbacks(refreshRunnable);
        stopLocationUpdate();
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
        root.setPadding(dp(14), dp(18), dp(14), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        buildHeader();
        buildWalletCard();
        buildActions();

        root.addView(section("Order Aktif"));
        activeBox = new LinearLayout(this);
        activeBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(activeBox);

        root.addView(section("Tawaran Order Terbaru"));
        orderBox = new LinearLayout(this);
        orderBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(orderBox);

        Button profile = outlineButton("Profil Driver");
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

        left.addView(text("Transiva Driver", 24, "#0B3A78", true));
        nameText = text("Halo, " + username + " • " + driverLabel(), 14, "#64748B", false);
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
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundGradient("#086BFF", "#2EA2FF", dp(22)));
        card.setElevation(dp(3));

        TextView label = text("Saldo Driver", 14, "#EAF4FF", true);
        card.addView(label);

        balanceText = text("Rp 0", 26, "#FFFFFF", true);
        add(card, balanceText, 0, dp(5), 0, 0);

        pendingDepositText = text("Deposit pending: Rp 0", 12, "#EAF4FF", true);
        pendingDepositText.setPadding(0, dp(4), 0, 0);
        card.addView(pendingDepositText);

        pendingWithdrawText = text("Withdraw pending: Rp 0", 12, "#EAF4FF", true);
        pendingWithdrawText.setPadding(0, dp(2), 0, dp(10));
        card.addView(pendingWithdrawText);

        LinearLayout moneyRow = new LinearLayout(this);
        moneyRow.setOrientation(LinearLayout.HORIZONTAL);
        Button depositBtn = whiteMiniButton("Deposit");
        depositBtn.setOnClickListener(v -> startActivity(new Intent(this, DriverTopUpActivity.class)));
        moneyRow.addView(depositBtn, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button withdrawBtn = whiteMiniButton("Withdraw");
        withdrawBtn.setOnClickListener(v -> startActivity(new Intent(this, DriverWithdrawActivity.class)));
        LinearLayout.LayoutParams wdlp = new LinearLayout.LayoutParams(0, dp(44), 1);
        wdlp.setMargins(dp(8), 0, 0, 0);
        moneyRow.addView(withdrawBtn, wdlp);
        card.addView(moneyRow);

        LinearLayout onlineRow = new LinearLayout(this);
        onlineRow.setGravity(Gravity.CENTER_VERTICAL);
        onlineRow.setPadding(0, dp(14), 0, 0);

        onlineText = text("OFFLINE", 15, "#FFFFFF", true);
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
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);

        Button refresh = outlineButton("Refresh");
        refresh.setOnClickListener(v -> refreshDriverData(true));
        row1.addView(refresh, new LinearLayout.LayoutParams(0, dp(48), 1));

        Button history = outlineButton("Riwayat");
        history.setOnClickListener(v -> startActivity(new Intent(this, DriverReceiptHistoryActivity.class)));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(0, dp(48), 1);
        hlp.setMargins(dp(8), 0, 0, 0);
        row1.addView(history, hlp);

        root.addView(row1, new LinearLayout.LayoutParams(-1, -2));
    }

    private void refreshDriverData(boolean showLoading) {
        if (loading) return;
        loading = true;
        if (showLoading && progressBar != null) progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            String balanceJson = "", statusJson = "", activeJson = "", ordersJson = "", pickupJson = "", dashJson = "";
            try { statusJson = get(SERVER + "getDriverStatus.php?username=" + enc(username) + "&v=" + System.currentTimeMillis()); } catch (Exception ignored) {}
            try { balanceJson = get(SERVER + "getBalance.php?username=" + enc(username) + "&v=" + System.currentTimeMillis()); } catch (Exception ignored) {}
            try { dashJson = get(SERVER + "driver_get_dashboard.php?username=" + enc(username) + "&v=" + System.currentTimeMillis()); } catch (Exception ignored) {}
            try {
                if (parseOnline(statusJson)) {
                    long now = System.currentTimeMillis();
                    if (now - lastAssignAt >= ASSIGN_MIN_INTERVAL_MS) {
                        lastAssignAt = now;
                        get(SERVER + "assignNextDriver.php?driver_type=" + enc(driverType) + "&driver=" + enc(username) + "&username=" + enc(username) + "&v=" + now);
                    }
                }
            } catch (Exception ignored) {}
            try { activeJson = get(SERVER + "getActiveDriverOrder.php?driver=" + enc(username) + "&v=" + System.currentTimeMillis()); } catch (Exception ignored) {}

            /*
             * FIX UTAMA:
             * Food setelah merchant accept status-nya biasanya merchant_accepted
             * dan driver ada di kolom offered_driver.
             * Endpoint lama getOrders.php sering hanya membaca order ride biasa,
             * sehingga food/pickup tidak muncul di dashboard native.
             */
            try { ordersJson = get(SERVER + "driver_get_unified_orders.php?driver=" + enc(username) + "&username=" + enc(username) + "&driver_type=" + enc(driverType) + "&v=" + System.currentTimeMillis()); } catch (Exception ignored) {}

            /*
             * Backup khusus pickup. Jika unified endpoint belum ter-upload,
             * pickup tetap bisa dibaca dari endpoint ini.
             */
            try { pickupJson = get(SERVER + "driver_get_pickup_orders.php?driver=" + enc(username) + "&username=" + enc(username) + "&driver_type=" + enc(driverType) + "&v=" + System.currentTimeMillis()); } catch (Exception ignored) {}

            final String fBalance = balanceJson, fStatus = statusJson, fActive = activeJson, fOrders = ordersJson, fPickup = pickupJson, fDash = dashJson;
            mainHandler.post(() -> {
                loading = false;
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                showStatus(fStatus);
                showBalance(fBalance, fDash);
                showActive(fActive, fOrders);
                showOffers(fOrders, fPickup);
            });
        }).start();
    }

    private boolean parseOnline(String json) {
        try { JSONObject o = new JSONObject(json); return o.optBoolean("success", false) && o.optInt("is_online", 0) == 1; }
        catch (Exception e) { return driverOnline; }
    }

    private void showStatus(String json) {
        driverOnline = parseOnline(json);
        if (onlineSwitch != null && onlineSwitch.isChecked() != driverOnline) {
            onlineSwitch.setOnCheckedChangeListener(null);
            onlineSwitch.setChecked(driverOnline);
            onlineSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> { if (!loading) setDriverOnline(isChecked); });
        }
        if (onlineText != null) onlineText.setText(driverOnline ? "ONLINE" : "OFFLINE");
        if (levelText != null) {
            levelText.setText(driverOnline ? "ONLINE" : "OFFLINE");
            levelText.setTextColor(Color.parseColor(driverOnline ? "#0B7CFF" : "#EF4444"));
            levelText.setBackground(roundStroke(driverOnline ? "#EAF4FF" : "#FFF1F2", driverOnline ? "#B9DBFF" : "#FECACA", dp(20), 1));
        }
    }

    private void showBalance(String balanceJson, String dashJson) {
        long b = -1;
        int pendingCount = 0;
        long pendingAmount = 0;
        int pendingWithdrawCount = 0;
        long pendingWithdrawAmount = 0;
        try {
            JSONObject d = new JSONObject(dashJson);
            if (d.optBoolean("success", false)) {
                b = d.optLong("balance", -1);
                pendingCount = d.optInt("pending_deposit_count", 0);
                pendingAmount = d.optLong("pending_deposit_amount", 0);
                pendingWithdrawCount = d.optInt("pending_withdraw_count", 0);
                pendingWithdrawAmount = d.optLong("pending_withdraw_amount", 0);
            }
        } catch (Exception ignored) {}
        if (b < 0) {
            try { JSONObject o = new JSONObject(balanceJson); b = o.optLong("balance", 0); } catch (Exception ignored) {}
        }
        if (b < 0) {
            balanceText.setText("Saldo belum terbaca");
            return;
        }

        balanceText.setText(rupiah(b));
        if (pendingDepositText != null) {
            pendingDepositText.setText("Deposit pending: " + rupiah(pendingAmount) + (pendingCount > 0 ? " • " + pendingCount + "x" : ""));
        }
        if (pendingWithdrawText != null) {
            pendingWithdrawText.setText("Withdraw pending: " + rupiah(pendingWithdrawAmount) + (pendingWithdrawCount > 0 ? " • " + pendingWithdrawCount + "x" : ""));
        }

        if (lastBalance >= 0 && b > lastBalance) {
            long inc = b - lastBalance;
            showBalanceIncreaseNotification(inc, b);
        }
        lastBalance = b;
    }

    private void showBalanceIncreaseNotification(long inc, long balance) {
        try {
            Toast.makeText(this, "Saldo bertambah " + rupiah(inc), Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, DriverDashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, 7719, intent,
                    Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_BALANCE) : new Notification.Builder(this);
            b.setSmallIcon(getAppIcon())
                    .setContentTitle("Saldo driver bertambah")
                    .setContentText("+ " + rupiah(inc) + " • Saldo sekarang " + rupiah(balance))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                    .setVibrate(new long[]{0, 220, 120, 300})
                    .setPriority(Notification.PRIORITY_HIGH);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(7719, b.build());
        } catch (Exception ignored) {}
    }

    private void showActive(String activeJson, String unifiedJson) {
        activeBox.removeAllViews();

        /*
         * Coba baca active order dari endpoint unified dulu,
         * karena pickup_orders dan food merchant_accepted/taken tidak selalu
         * terbaca oleh getActiveDriverOrder.php lama.
         */
        try {
            JSONObject u = new JSONObject(unifiedJson);
            JSONArray activeArr = u.optJSONArray("active_orders");
            if (u.optBoolean("success", false) && activeArr != null && activeArr.length() > 0) {
                JSONObject order = activeArr.optJSONObject(0);
                if (order != null) {
                    String table = normalizeTable(firstNonEmpty(order.optString("source"), order.optString("_transiva_table"), "orders"));
                    activeBox.addView(orderCard(order, true, table));
                    return;
                }
            }
        } catch (Exception ignored) {}

        try {
            JSONObject o = new JSONObject(activeJson);
            JSONObject order = o.optJSONObject("order");
            if (o.optBoolean("success", false) && order != null) {
                String table = normalizeTable(firstNonEmpty(order.optString("source"), order.optString("_transiva_table"), "orders"));
                activeBox.addView(orderCard(order, true, table));
                return;
            }
        } catch (Exception ignored) {}

        activeBox.addView(emptyCard("Belum ada order aktif."));
    }

    private void showOffers(String ordersJson, String pickupJson) {
        orderBox.removeAllViews();

        if (!driverOnline) {
            stableOfferMap.clear();
            stableMissingMap.clear();
            stableTableMap.clear();
            orderBox.addView(emptyCard("Driver OFFLINE.\nAktifkan ONLINE untuk menerima order."));
            firstLoadDone = true;
            return;
        }

        Set<String> freshKeys = new HashSet<>();
        readOffersIntoStableCache(ordersJson, "auto", freshKeys);
        readOffersIntoStableCache(pickupJson, "pickup_orders", freshKeys);
        ageMissingOffers(freshKeys);

        int count = 0;
        for (String key : stableOfferMap.keySet()) {
            if (count >= 8) break;
            JSONObject order = stableOfferMap.get(key);
            String table = stableTableMap.containsKey(key) ? stableTableMap.get(key) : "orders";
            if (order != null) {
                orderBox.addView(orderCard(order, false, table));
                count++;
            }
        }

        if (count == 0) {
            orderBox.addView(emptyCard("Belum ada tawaran order."));
        }

        firstLoadDone = true;
    }

    private void readOffersIntoStableCache(String json, String table, Set<String> freshKeys) {
        try {
            JSONObject obj = new JSONObject(json);
            JSONArray arr = obj.optJSONArray("offer_orders");
            if (arr == null) arr = obj.optJSONArray("orders");
            if (arr == null) arr = obj.optJSONArray("pickup_orders");

            if (!obj.optBoolean("success", false) || arr == null) return;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject order = arr.optJSONObject(i);
                if (order == null) continue;

                String id = offerId(order);
                if (id.length() == 0) continue;

                String realTable = "auto".equals(table)
                        ? normalizeTable(firstNonEmpty(order.optString("source"), order.optString("_transiva_table"), "orders"))
                        : normalizeTable(table);

                String status = safe(order.optString("status", "")).toLowerCase(Locale.US);
                if (status.equals("taken") ||
                        status.equals("driver_accepted") ||
                        status.equals("arrived_pickup") ||
                        status.equals("picked_up") ||
                        status.equals("on_delivery") ||
                        status.equals("arrived_delivery") ||
                        status.equals("finished") ||
                        status.equals("completed") ||
                        status.equals("cancelled") ||
                        status.equals("canceled") ||
                        status.equals("merchant_rejected")) {
                    continue;
                }

                String key = realTable + "-" + id;
                freshKeys.add(key);

                try {
                    order.put("_transiva_table", realTable);
                } catch (Exception ignored) {}

                stableOfferMap.put(key, order);
                stableTableMap.put(key, realTable);
                stableMissingMap.put(key, 0);

                if (!notifiedIds.contains(key)) {
                    if (firstLoadDone) showOrderNotification(order, realTable);
                    notifiedIds.add(key);
                }
            }

            trimStableOffersIfNeeded();

        } catch (Exception ignored) {}
    }

    private void ageMissingOffers(Set<String> freshKeys) {
        Set<String> keys = new HashSet<>(stableOfferMap.keySet());

        for (String key : keys) {
            if (freshKeys.contains(key)) {
                stableMissingMap.put(key, 0);
                continue;
            }

            int miss = stableMissingMap.containsKey(key) ? stableMissingMap.get(key) + 1 : 1;
            stableMissingMap.put(key, miss);

            JSONObject cached = stableOfferMap.get(key);
            String status = cached == null ? "" : safe(cached.optString("status", "")).toLowerCase(Locale.US);

            boolean finishedOrTakenByOther =
                    status.equals("finished") ||
                    status.equals("completed") ||
                    status.equals("cancelled") ||
                    status.equals("canceled") ||
                    status.equals("taken") ||
                    status.equals("merchant_rejected") ||
                    status.equals("expired") ||
                    status.equals("rejected");

            if (finishedOrTakenByOther || miss >= OFFER_MISSING_LIMIT) {
                stableOfferMap.remove(key);
                stableMissingMap.remove(key);
                stableTableMap.remove(key);
                notifiedIds.remove(key);
            }
        }
    }

    private void trimStableOffersIfNeeded() {
        while (stableOfferMap.size() > OFFER_MAX_CACHE) {
            String firstKey = stableOfferMap.keySet().iterator().next();
            stableOfferMap.remove(firstKey);
            stableMissingMap.remove(firstKey);
            stableTableMap.remove(firstKey);
        }
    }

    private String offerId(JSONObject order) {
        if (order == null) return "";
        return safe(order.optString("id", order.optString("order_id", "")));
    }

    private void notifyNewOrders(JSONArray arr, String table) {
        try {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String id = safe(o.optString("order_id", o.optString("id", "")));
                if (id.length() == 0) continue;
                String key = table + "-" + id;
                if (!notifiedIds.contains(key)) {
                    if (firstLoadDone) showOrderNotification(o, table);
                    notifiedIds.add(key);
                }
            }
        } catch (Exception ignored) {}
    }

    private void showOrderNotification(JSONObject order, String table) {
        try {
            String id = safe(order.optString("order_id", order.optString("id", "")));
            String service = detectService(order, table);
            String pickup = firstNonEmpty(order.optString("pickup_address"), order.optString("pickup"), "Lokasi pickup");
            Intent intent = new Intent(this, DriverDashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, id.hashCode(), intent,
                    Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ORDER) : new Notification.Builder(this);
            b.setSmallIcon(getAppIcon())
                    .setContentTitle("Order baru masuk")
                    .setContentText(service.trim() + " #" + id + " • " + pickup)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                    .setVibrate(new long[]{0, 250, 120, 250})
                    .setPriority(Notification.PRIORITY_HIGH);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(Math.abs((table + id).hashCode()), b.build());
            Toast.makeText(this, "Order baru masuk #" + id, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {}
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel order = new NotificationChannel(CHANNEL_ORDER, "Order Driver", NotificationManager.IMPORTANCE_HIGH);
            order.setDescription("Notifikasi order baru untuk driver Transiva");
            order.enableVibration(true);
            order.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null);
            nm.createNotificationChannel(order);

            NotificationChannel balance = new NotificationChannel(CHANNEL_BALANCE, "Saldo Driver", NotificationManager.IMPORTANCE_HIGH);
            balance.setDescription("Notifikasi ketika saldo driver bertambah");
            balance.enableVibration(true);
            balance.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null);
            nm.createNotificationChannel(balance);
        }
    }

    private void askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            try { if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION); }
            catch (Exception ignored) {}
        }
    }

    private void askLocationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
                }
            } catch (Exception ignored) {}
        }
    }

    private boolean hasLocationPermission() {
        if (Build.VERSION.SDK_INT < 23) return true;
        try {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    private void startLocationUpdate() {
        stopLocationUpdate();
        updateDriverLocation();
        mainHandler.postDelayed(locationRunnable, LOCATION_INTERVAL_MS);

        if (!hasLocationPermission() || locationManager == null) return;

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_INTERVAL_MS, 0, locationListener);
            }
        } catch (Exception ignored) {}

        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_INTERVAL_MS, 0, locationListener);
            }
        } catch (Exception ignored) {}
    }

    private void stopLocationUpdate() {
        mainHandler.removeCallbacks(locationRunnable);
        try {
            if (locationManager != null) locationManager.removeUpdates(locationListener);
        } catch (Exception ignored) {}
    }

    private void updateDriverLocation() {
        if (username.length() == 0 || "Driver".equalsIgnoreCase(username)) return;
        if (!hasLocationPermission() || locationManager == null) return;

        Location best = null;

        try {
            Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (gps != null) best = gps;
        } catch (Exception ignored) {}

        try {
            Location net = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (net != null && (best == null || net.getTime() > best.getTime())) best = net;
        } catch (Exception ignored) {}

        if (best != null) sendDriverLocation(best);
    }

    private void sendDriverLocation(Location location) {
        if (location == null) return;
        if (username.length() == 0 || "Driver".equalsIgnoreCase(username)) return;

        final double lat = location.getLatitude();
        final double lng = location.getLongitude();

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("username", username);
                payload.put("latitude", lat);
                payload.put("longitude", lng);
                payload.put("driver_type", driverType);

                // Disamakan dengan JS DriverView.updateDriverLocation():
                // POST server/updateDriverLocation.php { username, latitude, longitude, driver_type }
                post(SERVER + "updateDriverLocation.php", payload);
            } catch (Exception ignored) {}
        }).start();
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

        card.addView(text((active ? "Order Aktif" : "Tawaran Order") + " #" + id, 17, "#0B3A78", true));
        add(card, text(service, 14, "#0B7CFF", true), 0, dp(6), 0, 0);
        add(card, text("Penjemputan:\n" + pickup, 14, "#334155", false), 0, dp(8), 0, 0);
        add(card, text("Tujuan:\n" + delivery, 14, "#334155", false), 0, dp(6), 0, 0);
        add(card, text(rupiah(price) + " • " + distance + " KM", 15, "#0F172A", true), 0, dp(8), 0, 0);

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
        if ("pickup_orders".equals(table)) return "TransPickup";
        String t = firstNonEmpty(o.optString("order_type"), o.optString("type"), o.optString("driver_type")).toLowerCase(Locale.US);
        String note = o.optString("note", "").toLowerCase(Locale.US);
        if (t.contains("car") || t.contains("mobil")) return "TransCar";
        if (t.contains("food") || note.contains("food")) return "TransFood";
        return driverType.equals("car") ? "TransCar" : "TransRide";
    }

    private void takeOrder(String id, String table) {
        if (id.length() == 0) { showInfo("Gagal", "Order ID tidak ditemukan."); return; }
        setBusy(true, true);
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
                    setBusy(false, false);
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    if (ok) {
                        removeOfferFromStableCache(table + "-" + id);
                        if (order != null) openNativeTrip(order, table); else refreshDriverData(false);
                    } else refreshDriverData(false);
                });
            } catch (Exception e) {
                mainHandler.post(() -> { setBusy(false, false); showInfo("Gagal", "Koneksi gagal mengambil order."); refreshDriverData(false); });
            }
        }).start();
    }

    private void removeOfferFromStableCache(String key) {
        try {
            stableOfferMap.remove(key);
            stableMissingMap.remove(key);
            stableTableMap.remove(key);
            notifiedIds.remove(key);
        } catch (Exception ignored) {}
    }

    private void setDriverOnline(boolean online) {
        setBusy(true, true);
        new Thread(() -> {
            try {
                JSONObject p = new JSONObject();
                p.put("username", username);
                p.put("is_online", online ? 1 : 0);
                p.put("driver_type", driverType);
                JSONObject res = post(SERVER + "updateDriverStatus.php", p);
                boolean ok = res.optBoolean("success", false);
                mainHandler.post(() -> { setBusy(false, false); if (!ok) { showInfo("Gagal", firstNonEmpty(res.optString("message"), "Gagal mengubah status driver.")); } else { if (online) startLocationUpdate(); else stopLocationUpdate(); } refreshDriverData(false); });
            } catch (Exception e) {
                mainHandler.post(() -> { setBusy(false, false); showInfo("Gagal", "Koneksi gagal mengubah status driver."); refreshDriverData(false); });
            }
        }).start();
    }

    private void openNativeTrip(JSONObject order, String table) {
        try {
            getSharedPreferences("transiva", MODE_PRIVATE).edit()
                    .putString("driver_active_order_json", order.toString())
                    .putString("driver_active_order_kind", table.contains("pickup") ? "pickup" : "order")
                    .apply();
            Intent i = new Intent(this, DriverTripActivity.class);
            i.putExtra("order_json", order.toString());
            i.putExtra("order_table", table);
            i.putExtra("driver", username);
            i.putExtra("driver_type", driverType);
            startActivity(i);
        } catch (Exception e) { openWeb(BASE_URL + "?app=1#driver_trip"); }
    }

    private void openProfile() {
        try { startActivity(new Intent(this, ProfileActivity.class)); }
        catch (Exception e) { showInfo("Profil Driver", "Driver: " + username + "\nTipe: " + driverLabel()); }
    }

    private void openWeb(String url) { Intent i = new Intent(this, MainActivity.class); i.putExtra("url", url); startActivity(i); }
    private TextView emptyCard(String msg) { TextView t = text(msg, 16, "#0F172A", false); t.setPadding(dp(16), dp(18), dp(16), dp(18)); t.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(24), 1)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(8), 0, dp(12)); t.setLayoutParams(lp); return t; }
    private TextView section(String value) { TextView t = text(value, 21, "#0B3A78", true); t.setPadding(0, dp(14), 0, dp(6)); return t; }
    private void setBusy(boolean b, boolean show) { loading = b; if (progressBar != null) progressBar.setVisibility(b && show ? View.VISIBLE : View.GONE); }

    private String get(String link) throws Exception { HttpURLConnection c = (HttpURLConnection) new URL(link).openConnection(); c.setConnectTimeout(TIMEOUT_MS); c.setReadTimeout(TIMEOUT_MS); c.setRequestMethod("GET"); c.setRequestProperty("Accept", "application/json"); InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream(); String body = read(is); c.disconnect(); return body; }
    private JSONObject post(String link, JSONObject payload) throws Exception { HttpURLConnection c = (HttpURLConnection) new URL(link).openConnection(); c.setConnectTimeout(TIMEOUT_MS); c.setReadTimeout(TIMEOUT_MS); c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json; charset=UTF-8"); c.setRequestProperty("Accept", "application/json"); OutputStream os = c.getOutputStream(); BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8)); bw.write(payload == null ? "{}" : payload.toString()); bw.flush(); bw.close(); os.close(); InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream(); String body = read(is); c.disconnect(); if (body == null || body.trim().length() == 0) return new JSONObject(); return new JSONObject(body); }
    private String read(InputStream is) throws Exception { if (is == null) return ""; BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)); StringBuilder sb = new StringBuilder(); String line; while ((line = br.readLine()) != null) sb.append(line); br.close(); return sb.toString(); }

    private int getAppIcon() { try { int id = getResources().getIdentifier("transiva_icon_192", "drawable", getPackageName()); if (id != 0) return id; } catch (Exception ignored) {} return android.R.drawable.ic_dialog_info; }
    private Button primaryButton(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(15); b.setTypeface(Typeface.DEFAULT_BOLD); b.setTextColor(Color.WHITE); b.setBackground(roundGradient("#086BFF", "#2EA2FF", dp(18))); return b; }
    private Button whiteMiniButton(String s) { Button b = primaryButton(s); b.setTextSize(13); b.setTextColor(Color.parseColor("#086BFF")); b.setBackground(roundStroke("#FFFFFF", "#D9ECFF", dp(16), 1)); return b; }
    private Button outlineButton(String s) { Button b = primaryButton(s); b.setTextColor(Color.parseColor("#0B7CFF")); b.setBackground(roundStroke("#FFFFFF", "#9DCAFF", dp(18), 1)); return b; }
    private TextView text(String s, int sp, String color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(Color.parseColor(color)); if (bold) t.setTypeface(Typeface.DEFAULT_BOLD); return t; }
    private void add(LinearLayout parent, View v, int l, int t, int r, int b) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(l, t, r, b); parent.addView(v, lp); }
    private GradientDrawable round(String c, int r) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(c)); g.setCornerRadius(r); return g; }
    private GradientDrawable roundStroke(String c, String s, int r, int w) { GradientDrawable g = round(c, r); g.setStroke(dp(w), Color.parseColor(s)); return g; }
    private GradientDrawable roundGradient(String a, String b, int r) { GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor(a), Color.parseColor(b)}); g.setCornerRadius(r); return g; }
    private String rupiah(double v) { return "Rp " + NumberFormat.getNumberInstance(new Locale("id", "ID")).format((long) v); }
    private String enc(String v) { try { return URLEncoder.encode(v == null ? "" : v, "UTF-8"); } catch (Exception e) { return ""; } }
    private String safe(String v) { return v == null ? "" : v.trim(); }
    private String firstNonEmpty(String... vals) { if (vals == null) return ""; for (String v : vals) if (v != null && v.trim().length() > 0 && !"null".equalsIgnoreCase(v.trim())) return v.trim(); return ""; }
    private String normalizeTable(String table) {
        table = safe(table).toLowerCase(Locale.US);
        if (table.contains("pickup")) return "pickup_orders";
        return "orders";
    }

    private String driverLabel() { return "car".equals(driverType) ? "Driver Mobil" : "Driver Motor"; }
    private void showInfo(String title, String msg) { try { new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show(); } catch (Exception ignored) {} }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}