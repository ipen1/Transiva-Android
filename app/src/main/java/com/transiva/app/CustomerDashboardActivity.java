package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;

public class CustomerDashboardActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String PREF_NAME = "transiva";
    private static final int TIMEOUT_MS = 20000;
    private static final int REQ_LOCATION = 1201;
    private static final int REQ_NOTIFICATION = 1202;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private LinearLayout root;
    private TextView usernameText;
    private TextView verifiedText;
    private TextView balanceText;
    private TextView statusText;
    private LinearLayout statusActionsBox;
    private TextView locationText;
    private EditText searchInput;
    private LinearLayout searchResultsBox;
    private ProgressBar progressBar;

    private final List<SearchItem> globalSearchItems = new ArrayList<>();
    private Runnable globalSearchRunnable;
    private boolean searchIndexLoading = false;

    private double lastBalanceValue = -1;
    private boolean firstBalanceLoaded = false;
    private boolean balancePollingActive = false;
    private boolean balanceLoading = false;
    private String lastOrderStatusKey = "";

    private String username = "User";
    private int userId = 0;
    private boolean loading = false;
    private boolean orderStatusLoading = false;
    private boolean statusPollingActive = false;
    private LocationManager locationManager;

    private final Runnable orderStatusRunnable = new Runnable() {
        @Override public void run() {
            if (!statusPollingActive) return;
            loadOrderStatus();
            mainHandler.postDelayed(this, 5000);
        }
    };

    private final Runnable balanceRunnable = new Runnable() {
        @Override public void run() {
            if (!balancePollingActive) return;
            loadBalanceRealtime(true);
            mainHandler.postDelayed(this, 5000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            getWindow().setStatusBarColor(Color.parseColor("#071426"));
            getWindow().setNavigationBarColor(Color.parseColor("#071426"));
        } catch (Exception ignored) {}

        loadSession();
        buildLayout();
        createNotificationChannel();
        requestNotificationPermissionIfNeeded();
        loadBalanceRealtime(false);
        startBalancePolling();
        startOrderStatusPolling();
        loadActualLocation();
        loadGlobalSearchIndex();
    }


    @Override
    protected void onResume() {
        super.onResume();
        startOrderStatusPolling();
        startBalancePolling();
        loadGlobalSearchIndex();
    }

    @Override
    protected void onPause() {
        stopOrderStatusPolling();
        stopBalancePolling();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopOrderStatusPolling();
        stopBalancePolling();
        super.onDestroy();
    }

    private void startOrderStatusPolling() {
        statusPollingActive = true;
        mainHandler.removeCallbacks(orderStatusRunnable);
        loadOrderStatus();
        mainHandler.postDelayed(orderStatusRunnable, 5000);
    }

    private void stopOrderStatusPolling() {
        statusPollingActive = false;
        mainHandler.removeCallbacks(orderStatusRunnable);
    }

    private void loadSession() {
        try {
            SessionManager session = new SessionManager(this);
            if (session.isLoggedIn()) {
                username = firstNonEmpty(
                        session.getUsername(),
                        session.getName(),
                        "User"
                );
                try {
                    userId = Integer.parseInt(firstNonEmpty(session.getId(), session.getUserId(), "0"));
                } catch (Exception ignored) {
                    userId = 0;
                }
                return;
            }
        } catch (Exception ignored) {}

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

        locationText = text("📍 Mengambil lokasi...", 12, "#0B3A78", true);
        locationText.setGravity(Gravity.CENTER);
        locationText.setSingleLine(true);
        locationText.setPadding(dp(10), dp(8), dp(10), dp(8));
        locationText.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(20), 1));
        locationText.setOnClickListener(v -> loadActualLocation());
        header.addView(locationText, new LinearLayout.LayoutParams(-2, -2));
    }

    private void buildSearch() {
        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(15);
        searchInput.setTextColor(Color.parseColor("#0F172A"));
        searchInput.setHintTextColor(Color.parseColor("#94A3B8"));
        searchInput.setHint("Cari makanan, restoran, wisata, laundry...");
        searchInput.setPadding(dp(18), 0, dp(18), 0);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setBackground(roundStroke("#FFFFFF", "#D8E4F2", dp(24), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.setMargins(0, dp(16), 0, dp(10));
        root.addView(searchInput, lp);

        searchResultsBox = new LinearLayout(this);
        searchResultsBox.setOrientation(LinearLayout.VERTICAL);
        searchResultsBox.setVisibility(View.GONE);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(-1, -2);
        boxLp.setMargins(0, 0, 0, dp(12));
        root.addView(searchResultsBox, boxLp);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (globalSearchRunnable != null) mainHandler.removeCallbacks(globalSearchRunnable);
                globalSearchRunnable = () -> renderGlobalSearchResults(s == null ? "" : s.toString());
                mainHandler.postDelayed(globalSearchRunnable, 120);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            renderGlobalSearchResults(searchInput.getText().toString());
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

        col.addView(text("💳 Transiva Pay", 13, "#EAF4FF", true));

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
        topup.setOnClickListener(v -> startActivity(new Intent(CustomerDashboardActivity.this, CustomerTopUpActivity.class)));
        refresh.setOnClickListener(v -> loadBalanceRealtime(false));
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
        addMenu(grid, "TransLaundry", "ic_translaundry", "?route=laundry");
        addMenu(grid, "TransPickup", "ic_transpickup", "pickup_native");
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

            if ("TransRide".equalsIgnoreCase(title)
                    || "TransBike".equalsIgnoreCase(title)
                    || "Transbike".equalsIgnoreCase(title)) {

                startActivity(
                        new Intent(
                                CustomerDashboardActivity.this,
                                TransRideActivity.class
                        )
                );

                return;
            }

            if ("TransCar".equalsIgnoreCase(title)
                    || "Transcar".equalsIgnoreCase(title)
                    || "Mobil".equalsIgnoreCase(title)) {

                startActivity(
                        new Intent(
                                CustomerDashboardActivity.this,
                                PassengerCarActivity.class
                        )
                );

                return;
            }

            if ("TransFood".equalsIgnoreCase(title) || "Food".equalsIgnoreCase(title)) {
                startActivity(new Intent(CustomerDashboardActivity.this, TransFoodActivity.class));
                return;
            }

            if ("TransTour".equalsIgnoreCase(title) || "Transtour".equalsIgnoreCase(title) || "Wisata".equalsIgnoreCase(title)) {
                startActivity(new Intent(CustomerDashboardActivity.this, TranstourActivity.class));
                return;
            }

            if ("TransLaundry".equalsIgnoreCase(title) || "Laundry".equalsIgnoreCase(title)) {
                startActivity(new Intent(CustomerDashboardActivity.this, TransLaundryActivity.class));
                return;
            }

            if ("TransPickup".equalsIgnoreCase(title) || "Pickup".equalsIgnoreCase(title)) {
                startActivity(new Intent(CustomerDashboardActivity.this, TransPickupActivity.class));
                return;
            }

            if ("soon".equals(route)) {
                showInfo(
                        "Segera Hadir",
                        "TransPickup sedang dikembangkan."
                );
                return;
            }

            openWeb(route);

        });

        TextView icon = text(menuEmoji(title), 30, "#0B7CFF", true);
        icon.setGravity(Gravity.CENTER);
        item.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));

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

    private String menuEmoji(String title) {
        if ("TransRide".equalsIgnoreCase(title)) return "🏍️";
        if ("TransCar".equalsIgnoreCase(title)) return "🚗";
        if ("TransFood".equalsIgnoreCase(title)) return "🍔";
        if ("TransTour".equalsIgnoreCase(title)) return "🏝️";
        if ("TransLaundry".equalsIgnoreCase(title) || "Laundry".equalsIgnoreCase(title)) return "🧺";
        if ("Pickup".equalsIgnoreCase(title) || "TransPickup".equalsIgnoreCase(title)) return "📦";
        return "●";
    }

    private void buildStatusCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(22), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(14));
        root.addView(card, lp);

        card.addView(text("Status Pesanan", 17, "#0B3A78", true));

        statusText = text("Memuat status pesanan...", 13, "#64748B", false);
        statusText.setPadding(0, dp(8), 0, 0);
        card.addView(statusText);

        statusActionsBox = new LinearLayout(this);
        statusActionsBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(-1, -2);
        actionLp.setMargins(0, dp(10), 0, 0);
        card.addView(statusActionsBox, actionLp);
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
        history.setOnClickListener(v -> startActivity(new Intent(CustomerDashboardActivity.this, CustomerHistoryActivity.class)));
        profile.setOnClickListener(v -> {Intent intent = new Intent(this, ProfileActivity.class);startActivity(intent);});
    }

    private void loadActualLocation() {
        if (locationText != null) locationText.setText("📍 Mengambil lokasi...");

        if (checkSelfPermissionSafe(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermissionSafe(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }

        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) {
                locationText.setText("📍 Lokasi tidak tersedia");
                return;
            }

            boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            if (!gps && !network) {
                locationText.setText("📍 Aktifkan GPS");
                locationText.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
                return;
            }

            Location best = null;
            try { best = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
            if (best == null) {
                try { best = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); } catch (Exception ignored) {}
            }

            if (best != null) {
                updateLocationName(best);
            }

            String provider = gps ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            locationManager.requestSingleUpdate(provider, new LocationListener() {
                @Override public void onLocationChanged(Location location) { updateLocationName(location); }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            }, Looper.getMainLooper());

        } catch (Exception e) {
            locationText.setText("📍 Lokasi gagal");
        }
    }

    private int checkSelfPermissionSafe(String permission) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 23) return checkSelfPermission(permission);
            return PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return PackageManager.PERMISSION_DENIED;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            boolean ok = false;
            if (grantResults != null) {
                for (int g : grantResults) if (g == PackageManager.PERMISSION_GRANTED) ok = true;
            }
            if (ok) loadActualLocation();
            else locationText.setText("📍 Izin lokasi ditolak");
        }
    }

    private void updateLocationName(Location location) {
        if (location == null) return;

        try {
            new SessionManager(this).saveLastLocation(
                    String.valueOf(location.getLatitude()),
                    String.valueOf(location.getLongitude())
            );
        } catch (Exception ignored) {}

        new Thread(() -> {
            String result = "📍 Lokasi Kamu";

            try {
                Geocoder geocoder = new Geocoder(CustomerDashboardActivity.this, new Locale("id", "ID"));
                List<Address> list = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);

                if (list != null && list.size() > 0) {
                    Address a = list.get(0);

                    String clean = firstNonEmpty(
                            a.getSubAdminArea(),
                            a.getLocality(),
                            a.getSubLocality(),
                            a.getFeatureName(),
                            a.getAdminArea()
                    );

                    clean = shortLocationName(clean);

                    if (clean.length() > 0) {
                        result = "📍 " + clean;
                    }
                }
            } catch (Exception ignored) {}

            String finalResult = result;
            mainHandler.post(() -> locationText.setText(finalResult));
        }).start();
    }

    private String shortLocationName(String value) {
        String clean = firstNonEmpty(value, "");

        if (clean.length() == 0) return "";

        clean = clean.replace("Kabupaten ", "");
        clean = clean.replace("Kota ", "");
        clean = clean.replace("Provinsi ", "");

        int comma = clean.indexOf(",");
        if (comma > 0) {
            clean = clean.substring(0, comma);
        }

        clean = clean.trim();

        if (clean.equalsIgnoreCase("Sulawesi Tengah")) {
            return "Parigi Moutong";
        }

        return clean;
    }

    private void startBalancePolling() {
        balancePollingActive = true;
        mainHandler.removeCallbacks(balanceRunnable);
        loadBalanceRealtime(true);
        mainHandler.postDelayed(balanceRunnable, 5000);
    }

    private void stopBalancePolling() {
        balancePollingActive = false;
        mainHandler.removeCallbacks(balanceRunnable);
    }

    private void loadBalanceRealtime(boolean silent) {
        if (balanceLoading) return;
        balanceLoading = true;
        if (!silent && progressBar != null) progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            double value = 0;
            boolean ok = false;
            try {
                JSONObject json = getJson(BASE_URL + "server/getBalance.php?username=" + Uri.encode(username) + "&_=" + System.currentTimeMillis());
                if (json.optBoolean("success", false)) {
                    value = json.optDouble("balance", 0);
                    ok = true;
                }
            } catch (Exception ignored) {}

            double finalValue = value;
            boolean finalOk = ok;
            mainHandler.post(() -> {
                balanceLoading = false;
                if (!silent && progressBar != null) progressBar.setVisibility(View.GONE);
                if (!finalOk) return;

                if (balanceText != null) balanceText.setText(rupiah(finalValue));

                if (firstBalanceLoaded && lastBalanceValue >= 0 && finalValue > lastBalanceValue) {
                    double masuk = finalValue - lastBalanceValue;
                    showLocalNotification("Saldo Masuk", "Saldo bertambah " + rupiah(masuk) + ". Saldo sekarang " + rupiah(finalValue));
                }

                if (firstBalanceLoaded && lastBalanceValue >= 0 && finalValue < lastBalanceValue) {
                    showLocalNotification("Saldo Berubah", "Saldo sekarang " + rupiah(finalValue));
                }

                lastBalanceValue = finalValue;
                firstBalanceLoaded = true;
            });
        }).start();
    }

    private void loadGlobalSearchIndex() {
        if (searchIndexLoading) return;
        searchIndexLoading = true;
        new Thread(() -> {
            ArrayList<SearchItem> fresh = new ArrayList<>();

            try {
                JSONObject food = getJson(BASE_URL + "server/get_food_restaurants.php?v=" + System.currentTimeMillis());
                JSONArray restaurants = food.optJSONArray("restaurants");
                if (restaurants != null) {
                    for (int i = 0; i < restaurants.length(); i++) {
                        JSONObject r = restaurants.optJSONObject(i);
                        if (r == null) continue;
                        int rid = r.optInt("id", 0);
                        String restoName = firstNonEmpty(r.optString("name"), "Restoran");
                        fresh.add(new SearchItem("restaurant", restoName, firstNonEmpty(r.optString("address"), "Restoran makanan"), "🍔", rid, 0, restoName));
                        try {
                            JSONObject menusJson = getJson(BASE_URL + "server/get_food_menus.php?restaurant_id=" + rid + "&v=" + System.currentTimeMillis());
                            JSONArray menus = menusJson.optJSONArray("menus");
                            if (menus != null) {
                                for (int m = 0; m < menus.length(); m++) {
                                    JSONObject menu = menus.optJSONObject(m);
                                    if (menu == null || menu.optInt("is_active", 1) != 1) continue;
                                    String menuName = firstNonEmpty(menu.optString("name"), "Menu makanan");
                                    String sub = "Milik restoran: " + restoName + " • " + rupiah(menu.optDouble("price", 0));
                                    fresh.add(new SearchItem("food", menuName, sub, "🍽️", rid, menu.optInt("id", 0), restoName));
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}

            try {
                JSONObject wisata = getJson(BASE_URL + "server/getWisataPlaces.php?v=" + System.currentTimeMillis());
                JSONArray places = firstArray(wisata, "places", "data", "wisata", "items");
                if (places != null) {
                    for (int i = 0; i < places.length(); i++) {
                        JSONObject p = places.optJSONObject(i);
                        if (p == null) continue;
                        String name = firstNonEmpty(p.optString("name"), p.optString("title"), "Tempat wisata");
                        String sub = firstNonEmpty(p.optString("address"), p.optString("location"), "TransTour");
                        fresh.add(new SearchItem("tour", name, sub, "🏝️", p.optInt("id", 0), 0, name));
                    }
                }
            } catch (Exception ignored) {}

            try {
                JSONObject laundry = getJson(BASE_URL + "server/get_laundries.php?v=" + System.currentTimeMillis());
                JSONArray arr = firstArray(laundry, "laundries", "data", "items");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject l = arr.optJSONObject(i);
                        if (l == null) continue;
                        String name = firstNonEmpty(l.optString("name"), "Laundry");
                        String sub = firstNonEmpty(l.optString("address"), "TransLaundry") + " • Mulai " + rupiah(l.optDouble("price", 0));
                        fresh.add(new SearchItem("laundry", name, sub, "🧺", l.optInt("id", 0), 0, name));
                    }
                }
            } catch (Exception ignored) {}

            mainHandler.post(() -> {
                searchIndexLoading = false;
                globalSearchItems.clear();
                globalSearchItems.addAll(fresh);
                if (searchInput != null && searchInput.getText().toString().trim().length() > 0) {
                    renderGlobalSearchResults(searchInput.getText().toString());
                }
            });
        }).start();
    }

    private JSONArray firstArray(JSONObject obj, String... keys) {
        if (obj == null) return null;
        for (String key : keys) {
            JSONArray arr = obj.optJSONArray(key);
            if (arr != null) return arr;
        }
        return null;
    }

    private void renderGlobalSearchResults(String raw) {
        if (searchResultsBox == null) return;
        searchResultsBox.removeAllViews();
        String q = firstNonEmpty(raw, "").toLowerCase(Locale.US).trim();

        if (q.length() == 0) {
            searchResultsBox.setVisibility(View.GONE);
            return;
        }

        searchResultsBox.setVisibility(View.VISIBLE);

        if (searchIndexLoading && globalSearchItems.isEmpty()) {
            addSearchStatus("Mengambil data makanan, restoran, wisata, dan laundry...");
            return;
        }

        int count = 0;
        for (SearchItem item : globalSearchItems) {
            if (item.matches(q)) {
                addSearchCard(item);
                count++;
                if (count >= 30) break;
            }
        }

        if (count == 0) {
            addSearchStatus("Tidak ada hasil untuk: " + raw + (searchIndexLoading ? "\nData masih dimuat, lanjut ketik atau coba sebentar lagi." : ""));
        }
    }

    private void addSearchStatus(String message) {
        TextView t = text(message, 13, "#64748B", false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(14), dp(14), dp(14));
        t.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(18), 1));
        searchResultsBox.addView(t, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addSearchCard(SearchItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(18), 1));
        card.setClickable(true);
        card.setOnClickListener(v -> openSearchItem(item));

        TextView icon = text(item.icon, 24, "#0B7CFF", true);
        icon.setGravity(Gravity.CENTER);
        card.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), 0, 0, 0);
        card.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

        col.addView(text(item.title, 15, "#0F172A", true));
        TextView sub = text(item.label() + " • " + item.sub, 12, "#64748B", false);
        sub.setPadding(0, dp(3), 0, 0);
        col.addView(sub);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        searchResultsBox.addView(card, lp);
    }

    private void openSearchItem(SearchItem item) {
        Intent intent;
        if ("food".equals(item.type) || "restaurant".equals(item.type)) {
            intent = new Intent(this, TransFoodActivity.class);
            intent.putExtra("search_query", item.title);
            intent.putExtra("restaurant_id", item.parentId);
            startActivity(intent);
            return;
        }
        if ("tour".equals(item.type)) {
            intent = new Intent(this, TranstourActivity.class);
            intent.putExtra("search_query", item.title);
            intent.putExtra("place_id", item.parentId);
            startActivity(intent);
            return;
        }
        if ("laundry".equals(item.type)) {
            intent = new Intent(this, TransLaundryActivity.class);
            intent.putExtra("search_query", item.title);
            intent.putExtra("laundry_id", item.parentId);
            startActivity(intent);
        }
    }

    private void loadOrderStatus() {
        if (orderStatusLoading) return;

        orderStatusLoading = true;

        new Thread(() -> {
            JSONObject activeOrder = null;
            String text = "Belum ada pesanan aktif";

            try {
                if (userId <= 0) {
                    loadSession();
                }

                if (userId > 0) {
                    String url = BASE_URL
                            + "server/get_user_orders.php?user_id="
                            + userId
                            + "&_="
                            + System.currentTimeMillis();

                    JSONObject json = getJson(url);
                    JSONArray orders = json.optJSONArray("orders");

                    if (json.optBoolean("success", false) && orders != null && orders.length() > 0) {
                        for (int i = 0; i < orders.length(); i++) {
                            JSONObject o = orders.optJSONObject(i);
                            if (o == null) continue;

                            String status = firstNonEmpty(o.optString("status"), "").toLowerCase(Locale.US).trim();
                            if (!isFinishedOrCanceled(status)) {
                                activeOrder = o;
                                break;
                            }
                        }
                    }
                }

                String activeOrderId = firstNonEmpty(
                        getStringPref("active_order_id"),
                        activeOrder != null ? firstNonEmpty(activeOrder.optString("order_id"), activeOrder.optString("id")) : ""
                );

                if (activeOrderId.length() > 0) {
                    JSONObject fresh = fetchFreshOrderStatus(activeOrderId);
                    if (fresh != null) {
                        activeOrder = mergeOrder(activeOrder, fresh);
                    }
                }

                if (activeOrder != null) {
                    String status = firstNonEmpty(activeOrder.optString("status"), "").toLowerCase(Locale.US).trim();
                    if (isFinishedOrCanceled(status)) {
                        clearActiveOrderPrefs();
                        activeOrder = null;
                    } else {
                        saveActiveOrderToPrefs(activeOrder);
                        text = buildNativeStatusText(activeOrder);
                    }
                }

            } catch (Exception ignored) {}

            JSONObject finalOrder = activeOrder;
            String finalText = text;

            mainHandler.post(() -> {
                orderStatusLoading = false;
                if (statusText != null) statusText.setText(finalText);
                String newKey = finalOrder == null ? "" : firstNonEmpty(finalOrder.optString("order_id"), finalOrder.optString("id")) + ":" + firstNonEmpty(finalOrder.optString("status"));
                if (newKey.length() > 0 && lastOrderStatusKey.length() > 0 && !newKey.equals(lastOrderStatusKey)) {
                    showLocalNotification("Update Pesanan", finalText);
                }
                if (newKey.length() > 0) lastOrderStatusKey = newKey;
                buildOrderActionButtons(finalOrder);
            });
        }).start();
    }

    private JSONObject fetchFreshOrderStatus(String orderId) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("order_id", orderId);

            JSONObject res = postJson(BASE_URL + "server/check_order_status.php", payload);
            if (!res.optBoolean("success", false)) return null;

            JSONObject order = res.optJSONObject("order");
            if (order == null) order = new JSONObject();

            order.put("order_id", firstNonEmpty(
                    order.optString("order_id"),
                    order.optString("id"),
                    res.optString("order_id"),
                    res.optString("id"),
                    orderId
            ));

            order.put("status", firstNonEmpty(
                    res.optString("status"),
                    order.optString("status"),
                    "pending"
            ));

            JSONObject driver = res.optJSONObject("driver");
            if (driver != null) {
                order.put("driver", firstNonEmpty(
                        order.optString("driver"),
                        order.optString("driver_username"),
                        driver.optString("name"),
                        driver.optString("username"),
                        res.optString("driver_username")
                ));

                order.put("driver_username", firstNonEmpty(
                        order.optString("driver_username"),
                        driver.optString("username"),
                        driver.optString("name"),
                        res.optString("driver_username")
                ));
            } else {
                order.put("driver", firstNonEmpty(
                        order.optString("driver"),
                        order.optString("driver_username"),
                        res.optString("driver_username")
                ));
            }

            copyIfExists(res, order, "pickup_lat");
            copyIfExists(res, order, "pickup_lng");
            copyIfExists(res, order, "delivery_lat");
            copyIfExists(res, order, "delivery_lng");
            copyIfExists(res, order, "driver_type");
            copyIfExists(res, order, "order_type");
            copyIfExists(res, order, "service_type");
            copyIfExists(res, order, "service_name");
            copyIfExists(res, order, "price_mode");

            return order;
        } catch (Exception e) {
            return null;
        }
    }

    private JSONObject mergeOrder(JSONObject oldOrder, JSONObject freshOrder) {
        if (oldOrder == null) return freshOrder;
        if (freshOrder == null) return oldOrder;

        try {
            java.util.Iterator<String> keys = freshOrder.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = freshOrder.opt(key);
                if (value == null || String.valueOf(value).trim().length() == 0 || "null".equalsIgnoreCase(String.valueOf(value))) {
                    continue;
                }
                oldOrder.put(key, value);
            }
        } catch (Exception ignored) {}

        return oldOrder;
    }

    private void copyIfExists(JSONObject from, JSONObject to, String key) {
        try {
            String value = from.optString(key, "");
            if (value != null && value.trim().length() > 0 && !"null".equalsIgnoreCase(value.trim())) {
                to.put(key, value);
            }
        } catch (Exception ignored) {}
    }

    private void saveActiveOrderToPrefs(JSONObject order) {
        if (order == null) return;

        String orderId = firstNonEmpty(order.optString("order_id"), order.optString("id"));
        if (orderId.length() == 0) return;

        SharedPreferences.Editor e = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
        e.putString("active_order_id", orderId);
        e.putString("order_status", firstNonEmpty(order.optString("status"), ""));
        e.putString("pickup_lat", firstNonEmpty(order.optString("pickup_lat"), order.optString("user_lat"), getStringPref("pickup_lat")));
        e.putString("pickup_lng", firstNonEmpty(order.optString("pickup_lng"), order.optString("user_lng"), getStringPref("pickup_lng")));
        e.putString("delivery_lat", firstNonEmpty(order.optString("delivery_lat"), getStringPref("delivery_lat")));
        e.putString("delivery_lng", firstNonEmpty(order.optString("delivery_lng"), getStringPref("delivery_lng")));
        String driverType = detectDriverType(order);
        e.putString("active_driver_type", driverType);
        e.putString("active_order_type", driverType.equals("car") ? "Transcar" : firstNonEmpty(order.optString("order_type"), "TransRide"));
        e.putString("active_service_name", driverType.equals("car") ? "Transcar" : firstNonEmpty(order.optString("service_name"), ""));
        e.apply();
    }

    private boolean isFinishedOrCanceled(String status) {
        status = firstNonEmpty(status, "").toLowerCase(Locale.US).trim();
        return status.equals("finished")
                || status.equals("completed")
                || status.equals("finish")
                || status.equals("canceled")
                || status.equals("cancelled")
                || status.equals("merchant_rejected");
    }

    private String buildNativeStatusText(JSONObject order) {
        String orderId = firstNonEmpty(order.optString("order_id"), order.optString("id"), "-");
        String status = order.optString("status", "").toLowerCase(Locale.US).trim();
        String driver = firstNonEmpty(order.optString("driver"), order.optString("driver_username"), "Driver");
        String orderType = order.optString("order_type", "").toLowerCase(Locale.US).trim();

        boolean isCar = orderType.equals("passenger_car")
                || orderType.equals("transcar")
                || orderType.equals("car")
                || orderType.equals("mobil")
                || order.optString("service_type", "").equalsIgnoreCase("Transcar")
                || order.optString("service_name", "").equalsIgnoreCase("Transcar")
                || order.optString("driver_type", "").equalsIgnoreCase("car");

        String vehicle = isCar ? "🚗" : "🛵";

        if (status.equals("pending")) {
            return "Order #" + orderId + "\n⏳ Menunggu " + (isCar ? "driver mobil" : "kurir") + " menerima orderan.";
        }

        if (status.equals("merchant_accepted")) {
            return "Order #" + orderId + "\n✅ Pesanan diterima merchant. Menunggu driver mengambil pesanan.";
        }

        if (status.equals("taken")) {
            return "Order #" + orderId + "\n" + vehicle + " " + driver + " sedang menuju lokasi pickup.";
        }

        if (status.equals("arrived_pickup")) {
            return "Order #" + orderId + "\n✅ " + driver + " sudah tiba di lokasi pickup.";
        }

        if (status.equals("on_delivery")) {
            return "Order #" + orderId + "\n" + vehicle + " " + driver + " sedang menuju lokasi tujuan.";
        }

        if (status.equals("arrived_delivery")) {
            String payInfo = buildPaymentInfo(order);
            return "Order #" + orderId + "\n🏁 Driver sudah tiba di lokasi tujuan." + payInfo + "\nSilakan tekan Terima Pesanan jika pesanan sudah diterima.";
        }

        return "Order #" + orderId + "\nStatus: " + firstNonEmpty(status, "-");
    }

    private void buildOrderActionButtons(JSONObject order) {
        if (statusActionsBox == null) return;

        statusActionsBox.removeAllViews();

        if (order == null) return;

        String status = order.optString("status", "").toLowerCase(Locale.US).trim();
        String orderId = firstNonEmpty(order.optString("order_id"), order.optString("id"), "");

        if (orderId.length() == 0 || isFinishedOrCanceled(status)) return;

        if (status.equals("pending") || status.equals("merchant_accepted")) {
            Button cancel = dangerButton("Batalkan Order");
            cancel.setOnClickListener(v -> confirmCancelOrder(orderId));
            statusActionsBox.addView(cancel, new LinearLayout.LayoutParams(-1, dp(48)));
            return;
        }

        if (status.equals("taken")
                || status.equals("arrived_pickup")
                || status.equals("on_delivery")
                || status.equals("arrived_delivery")) {

            Button trip = primaryButton("🗺️ Lihat Status Perjalanan Driver");
            trip.setOnClickListener(v -> openNativeTrip(order));
            statusActionsBox.addView(trip, new LinearLayout.LayoutParams(-1, dp(48)));

            Button chat = outlineButton("💬 Chat Driver");
            LinearLayout.LayoutParams chatLp = new LinearLayout.LayoutParams(-1, dp(48));
            chatLp.setMargins(0, dp(8), 0, 0);
            chat.setOnClickListener(v -> openNativeChat(order));
            statusActionsBox.addView(chat, chatLp);

            if (status.equals("arrived_delivery")) {
                Button received = successButton("✅ Terima Pesanan");
                LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1, dp(50));
                rLp.setMargins(0, dp(8), 0, 0);
                received.setOnClickListener(v -> confirmOrderReceived(order));
                statusActionsBox.addView(received, rLp);
            }
        }
    }

    private String buildPaymentInfo(JSONObject order) {
        try {
            JSONObject note = parseNoteJson(order.optString("note", ""));
            String method = firstNonEmpty(
                    order.optString("payment_method"),
                    order.optString("payment_type"),
                    note.optString("payment_method"),
                    note.optString("payment_label"),
                    "cash"
            ).toLowerCase(Locale.US).trim();

            String status = firstNonEmpty(
                    order.optString("payment_status"),
                    note.optString("payment_status"),
                    ""
            ).toLowerCase(Locale.US).trim();

            boolean nonCash = method.equals("saldo")
                    || method.equals("wallet")
                    || method.equals("transiva_pay")
                    || method.equals("transivapay")
                    || method.equals("qris")
                    || method.equals("non_tunai")
                    || method.equals("non-tunai")
                    || method.equals("transfer");

            if (nonCash) {
                String label = method.equals("qris") ? "QRIS" : "Transiva Pay / Non Tunai";
                String payStatus = status.length() > 0 ? status : "paid/escrow";
                return "\n💳 Pembayaran: " + label + " (" + payStatus + ")";
            }

            if (method.equals("cash") || method.equals("tunai")) {
                return "\n💵 Pembayaran: Tunai";
            }
        } catch (Exception ignored) {}
        return "";
    }

    private JSONObject parseNoteJson(String note) {
        try {
            if (note == null || note.trim().length() == 0) return new JSONObject();
            return new JSONObject(note);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private void confirmOrderReceived(JSONObject order) {
        if (order == null) {
            showInfo("Gagal", "Data order tidak ditemukan.");
            return;
        }

        String orderId = firstNonEmpty(order.optString("order_id"), order.optString("id"), getStringPref("active_order_id"));
        int nativeId = order.optInt("id", 0);

        new AlertDialog.Builder(this)
                .setTitle("Terima Pesanan")
                .setMessage("Pastikan pesanan sudah benar-benar diterima. Untuk pembayaran non tunai, saldo tertahan akan dicairkan ke driver setelah dikonfirmasi.")
                .setNegativeButton("Belum", null)
                .setPositiveButton("Ya, Terima", (d, w) -> submitOrderReceived(nativeId, orderId))
                .show();
    }

    private void submitOrderReceived(int id, String orderId) {
        if (id <= 0 && firstNonEmpty(orderId, "").length() == 0) {
            showInfo("Gagal", "Order ID tidak ditemukan.");
            return;
        }

        setLoading(true);

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("id", id);
                payload.put("order_id", firstNonEmpty(orderId, ""));
                payload.put("user_id", userId);

                JSONObject res = postJson(BASE_URL + "server/customerConfirmFoodReceived.php", payload);
                boolean ok = res.optBoolean("success", false);
                String msg = firstNonEmpty(res.optString("message"), ok ? "Pesanan selesai" : "Gagal menyelesaikan pesanan");

                mainHandler.post(() -> {
                    setLoading(false);
                    showInfo(ok ? "Berhasil" : "Gagal", msg);
                    if (ok) {
                        clearActiveOrderPrefs();
                        loadOrderStatus();
                        loadBalanceRealtime(true);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setLoading(false);
                    showInfo("Gagal", "Terjadi kesalahan koneksi saat menyelesaikan pesanan.");
                });
            }
        }).start();
    }

    private void confirmCancelOrder(String orderId) {
        new AlertDialog.Builder(this)
                .setTitle("Batalkan Order")
                .setMessage("Yakin ingin membatalkan order ini?\n\nOrder yang dibatalkan tidak bisa dilanjutkan kembali.")
                .setNegativeButton("Tidak", null)
                .setPositiveButton("Ya", (d, w) -> cancelOrder(orderId))
                .show();
    }

    private void cancelOrder(String orderId) {
        if (orderId == null || orderId.trim().length() == 0) {
            showInfo("Gagal", "Order ID tidak ditemukan.");
            return;
        }

        setLoading(true);

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("order_id", orderId);

                JSONObject res = postJson(BASE_URL + "server/cancel_order.php", payload);
                boolean ok = res.optBoolean("success", false);
                String msg = firstNonEmpty(
                        res.optString("message"),
                        ok ? "Order berhasil dibatalkan" : "Gagal membatalkan order"
                );

                mainHandler.post(() -> {
                    setLoading(false);
                    showInfo(ok ? "Berhasil" : "Gagal", msg);

                    if (ok) {
                        clearActiveOrderPrefs();
                        loadOrderStatus();
                    }
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    setLoading(false);
                    showInfo("Error", "Terjadi kesalahan koneksi saat membatalkan order.");
                });
            }
        }).start();
    }

    private void openNativeTrip(JSONObject order) {
        if (order == null) {
            showInfo("Gagal", "Data order tidak ditemukan.");
            return;
        }

        String orderId = firstNonEmpty(order.optString("order_id"), order.optString("id"), "");
        String pickupLat = firstNonEmpty(order.optString("pickup_lat"), order.optString("user_lat"));
        String pickupLng = firstNonEmpty(order.optString("pickup_lng"), order.optString("user_lng"));
        String deliveryLat = firstNonEmpty(order.optString("delivery_lat"), "");
        String deliveryLng = firstNonEmpty(order.optString("delivery_lng"), "");

        if (!isValidCoordText(pickupLat, pickupLng)) {
            showInfo("Lokasi Tidak Valid", "Lokasi pickup tidak ditemukan. Silakan cek ulang pesanan.");
            return;
        }

        SharedPreferences.Editor e = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
        e.putString("active_order_id", orderId);
        e.putString("pickup_lat", pickupLat);
        e.putString("pickup_lng", pickupLng);
        e.putString("delivery_lat", deliveryLat);
        e.putString("delivery_lng", deliveryLng);
        String driverType = detectDriverType(order);
        e.putString("active_driver_type", driverType);
        e.putString("active_order_type", driverType.equals("car") ? "Transcar" : firstNonEmpty(order.optString("order_type"), "TransRide"));
        e.putString("active_service_name", driverType.equals("car") ? "Transcar" : firstNonEmpty(order.optString("service_name"), ""));
        e.apply();

        Intent intent = new Intent(this, CustomerTripActivity.class);
        intent.putExtra("order_id", orderId);
        intent.putExtra("active_order_id", orderId);
        intent.putExtra("pickup_lat", pickupLat);
        intent.putExtra("pickup_lng", pickupLng);
        intent.putExtra("delivery_lat", deliveryLat);
        intent.putExtra("delivery_lng", deliveryLng);
        intent.putExtra("driver_type", driverType);
        intent.putExtra("active_driver_type", driverType);
        intent.putExtra("active_order_type", driverType.equals("car") ? "Transcar" : "TransRide");
        startActivity(intent);
    }

    private void openNativeChat(JSONObject order) {
        if (order == null) {
            showInfo("Gagal Membuka Chat", "Data order tidak ditemukan.");
            return;
        }

        String orderId = firstNonEmpty(
                order.optString("order_id"),
                order.optString("id"),
                getStringPref("active_order_id")
        );

        if (orderId.trim().length() == 0) {
            showInfo("Gagal Membuka Chat", "Order ID chat tidak ditemukan.");
            return;
        }

        String roomId = firstNonEmpty(
                order.optString("room_id"),
                getStringPref("active_chat_room_id"),
                "ROOM-" + orderId
        );

        roomId = normalizeRoomId(roomId);

        String driverName = firstNonEmpty(
                order.optString("driver_name"),
                order.optString("driver"),
                order.optString("driver_username"),
                "Driver"
        );

        String status = firstNonEmpty(
                order.optString("status"),
                getStringPref("order_status")
        );

        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .edit()
                .putString("active_order_id", orderId)
                .putString("active_chat_order_id", orderId)
                .putString("active_chat_room_id", roomId)
                .putString("active_chat_driver_name", driverName)
                .putString("active_chat_order_status", status)
                .apply();

        Intent intent = new Intent(this, CustomerChatActivity.class);
        intent.putExtra("order_id", orderId);
        intent.putExtra("room_id", roomId);
        intent.putExtra("driver_name", driverName);
        intent.putExtra("order_status", status);
        startActivity(intent);
    }

    private String normalizeRoomId(String value) {
        String clean = firstNonEmpty(value, "").trim().replace("_", "-").toUpperCase(Locale.US);
        clean = clean.replaceAll("[^A-Z0-9\\-]", "");
        if (clean.length() > 0 && !clean.startsWith("ROOM-")) {
            clean = "ROOM-" + clean;
        }
        return clean;
    }

    private String detectDriverType(JSONObject order) {
        String type = firstNonEmpty(
                order.optString("driver_type"),
                order.optString("service_type"),
                order.optString("service_name"),
                order.optString("order_type"),
                order.optString("price_mode"),
                "bike"
        ).toLowerCase(Locale.US).trim();

        if (type.equals("car")
                || type.equals("mobil")
                || type.equals("passenger_car")
                || type.equals("transcar")
                || type.contains("transcar")) {
            return "car";
        }

        return "bike";
    }

    private boolean isValidCoordText(String lat, String lng) {
        try {
            double a = Double.parseDouble(firstNonEmpty(lat, "0"));
            double b = Double.parseDouble(firstNonEmpty(lng, "0"));
            return a != 0 && b != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void clearActiveOrderPrefs() {
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .edit()
                .remove("active_order_id")
                .remove("active_order")
                .remove("order_status")
                .remove("pickup_lat")
                .remove("pickup_lng")
                .remove("delivery_lat")
                .remove("delivery_lng")
                .remove("active_chat_order_id")
                .remove("active_chat_room_id")
                .apply();
    }

    private String getStringPref(String key) {
        try {
            return getSharedPreferences(PREF_NAME, MODE_PRIVATE).getString(key, "");
        } catch (Exception e) {
            return "";
        }
    }

    private void setLoading(boolean value) {
        loading = value;
        if (progressBar != null) progressBar.setVisibility(value ? View.VISIBLE : View.GONE);
    }

    private JSONObject postJson(String urlText, JSONObject payload) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlText);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
            writer.write(payload == null ? "{}" : payload.toString());
            writer.flush();
            writer.close();
            os.close();

            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
            String body = readStream(is).trim();

            if (body.length() == 0) return new JSONObject();

            return new JSONObject(body);
        } finally {
            if (conn != null) conn.disconnect();
        }
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

    private void requestNotificationPermissionIfNeeded() {
        try {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermissionSafe(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
            }
        } catch (Exception ignored) {}
    }

    private void createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(
                        "transiva_updates",
                        "Transiva Updates",
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                channel.setDescription("Notifikasi saldo dan status pesanan Transiva");
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.createNotificationChannel(channel);
            }
        } catch (Exception ignored) {}
    }

    private void showLocalNotification(String title, String message) {
        try {
            Toast.makeText(this, title + ": " + message, Toast.LENGTH_LONG).show();

            Intent intent = new Intent(this, CustomerDashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int flags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent pi = PendingIntent.getActivity(this, 2001, intent, flags);

            android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new android.app.Notification.Builder(this, "transiva_updates")
                    : new android.app.Notification.Builder(this);

            b.setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new android.app.Notification.BigTextStyle().bigText(message))
                    .setContentIntent(pi)
                    .setAutoCancel(true);

            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify((int) (System.currentTimeMillis() % 100000), b.build());
        } catch (Exception ignored) {}
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

    private Button dangerButton(String value) {
        Button b = primaryButton(value);
        b.setTextColor(Color.WHITE);
        b.setBackground(roundGradient("#EF4444", "#DC2626", dp(18)));
        return b;
    }

    private Button successButton(String value) {
        Button b = primaryButton(value);
        b.setTextColor(Color.WHITE);
        b.setBackground(roundGradient("#16A34A", "#22C55E", dp(18)));
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
        if (values == null) return "";
        for (String v : values) if (v != null && v.trim().length() > 0 && !v.trim().equalsIgnoreCase("null")) return v.trim();
        return "";
    }

    private int getDrawableId(String name) {
        try { return getResources().getIdentifier(name, "drawable", getPackageName()); } catch (Exception e) { return 0; }
    }

    private static class SearchItem {
        String type;
        String title;
        String sub;
        String icon;
        int parentId;
        int itemId;
        String owner;

        SearchItem(String type, String title, String sub, String icon, int parentId, int itemId, String owner) {
            this.type = type;
            this.title = title == null ? "" : title;
            this.sub = sub == null ? "" : sub;
            this.icon = icon == null ? "🔎" : icon;
            this.parentId = parentId;
            this.itemId = itemId;
            this.owner = owner == null ? "" : owner;
        }

        boolean matches(String q) {
            String all = (title + " " + sub + " " + owner + " " + label()).toLowerCase(Locale.US);
            return all.contains(q);
        }

        String label() {
            if ("food".equals(type)) return "Makanan";
            if ("restaurant".equals(type)) return "Restoran";
            if ("tour".equals(type)) return "Wisata";
            if ("laundry".equals(type)) return "Laundry";
            return "Hasil";
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}