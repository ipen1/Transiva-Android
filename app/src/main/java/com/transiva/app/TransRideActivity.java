package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransRideActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String CREATE_ORDER_URL = BASE_URL + "server/createOrder.php";
    private static final String RESOLVE_MAP_URL = BASE_URL + "server/resolve_google_maps.php";
    private static final int TIMEOUT_MS = 25000;
    private static final int REQ_LOCATION = 2201;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView pickupText;
    private TextView deliveryText;
    private TextView estimateText;
    private EditText googleLinkInput;
    private EditText noteInput;
    private Button pickupBtn;
    private Button deliveryBtn;
    private Button linkBtn;
    private Button orderBtn;
    private ProgressBar progressBar;

    private LocationManager locationManager;
    private boolean loading = false;
    private String pickingMode = "pickup";

    private double userLat = 0;
    private double userLng = 0;

    private double pickupLat = 0;
    private double pickupLng = 0;
    private String pickupAddress = "";

    private double deliveryLat = 0;
    private double deliveryLng = 0;
    private String deliveryAddress = "";

    private int userId = 0;
    private String username = "User";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            getWindow().setStatusBarColor(Color.parseColor("#071426"));
            getWindow().setNavigationBarColor(Color.parseColor("#071426"));
        } catch (Exception ignored) {}

        loadSession();
        buildLayout();
        getCurrentLocation("pickup");
    }

    private void loadSession() {
        try {
            SessionManager session = new SessionManager(this);
            username = firstNonEmpty(session.getUsername(), session.getName(), "User");
            userId = Integer.parseInt(firstNonEmpty(session.getId(), session.getUserId(), "0"));
        } catch (Exception ignored) {
            userId = 0;
        }
    }

    private void buildLayout() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F3F8FF"));

        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = card("#FFFFFF", "#D7E6F8", 22);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, lp(-1, -2, 0, 0, 0, 14));

        TextView icon = text("🏍️", 34, "#0B7CFF", true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round("#EAF4FF", dp(18)));
        header.addView(icon, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1);
        titleLp.setMargins(dp(12), 0, 0, 0);
        header.addView(titleBox, titleLp);

        titleBox.addView(text("TransRide / Transbike", 20, "#0B3A78", true));
        titleBox.addView(text("Pilih jemput, tujuan, lalu buat order", 12, "#64748B", false));

        Button close = smallButton("Tutup", "#FFFFFF", "#0B7CFF", "#9DCAFF");
        header.addView(close, new LinearLayout.LayoutParams(dp(82), dp(42)));
        close.setOnClickListener(v -> finish());

        LinearLayout locCard = card("#FFFFFF", "#D7E6F8", 24);
        root.addView(locCard, lp(-1, -2, 0, 0, 0, 14));

        pickupText = locationRow(locCard, "👤", "Lokasi Jemput", "Mengambil lokasi kamu...");
        addDivider(locCard);
        deliveryText = locationRow(locCard, "📍", "Lokasi Tujuan", "Belum dipilih");

        LinearLayout mapBox = card("#FFFFFF", "#D7E6F8", 22);
        mapBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(mapBox, lp(-1, -2, 0, 0, 0, 14));

        mapBox.addView(text("Link Google Maps Tujuan", 14, "#0B3A78", true));

        LinearLayout linkRow = new LinearLayout(this);
        linkRow.setOrientation(LinearLayout.HORIZONTAL);
        linkRow.setGravity(Gravity.CENTER_VERTICAL);
        mapBox.addView(linkRow, lp(-1, dp(48), 0, 10, 0, 0));

        googleLinkInput = new EditText(this);
        googleLinkInput.setSingleLine(true);
        googleLinkInput.setTextSize(13);
        googleLinkInput.setHint("Paste link Google Maps tujuan...");
        googleLinkInput.setHintTextColor(Color.parseColor("#94A3B8"));
        googleLinkInput.setTextColor(Color.parseColor("#0F172A"));
        googleLinkInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        googleLinkInput.setPadding(dp(14), 0, dp(14), 0);
        googleLinkInput.setBackground(roundStroke("#F8FBFF", "#D8E4F2", dp(16), 1));
        linkRow.addView(googleLinkInput, new LinearLayout.LayoutParams(0, -1, 1));

        linkBtn = smallButton("Pakai", "#0B7CFF", "#FFFFFF", "#0B7CFF");
        LinearLayout.LayoutParams linkBtnLp = new LinearLayout.LayoutParams(dp(82), -1);
        linkBtnLp.setMargins(dp(8), 0, 0, 0);
        linkRow.addView(linkBtn, linkBtnLp);
        linkBtn.setOnClickListener(v -> useGoogleMapsLink());

        LinearLayout actionGrid = new LinearLayout(this);
        actionGrid.setOrientation(LinearLayout.VERTICAL);
        root.addView(actionGrid, lp(-1, -2, 0, 0, 0, 14));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        actionGrid.addView(row1, new LinearLayout.LayoutParams(-1, dp(50)));

        pickupBtn = primaryButton("Gunakan GPS Jemput");
        deliveryBtn = outlineButton("GPS Jadi Tujuan");
        row1.addView(pickupBtn, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(0, -1, 1);
        dLp.setMargins(dp(10), 0, 0, 0);
        row1.addView(deliveryBtn, dLp);

        pickupBtn.setOnClickListener(v -> getCurrentLocation("pickup"));
        deliveryBtn.setOnClickListener(v -> getCurrentLocation("delivery"));

        LinearLayout noteCard = card("#FFFFFF", "#D7E6F8", 22);
        noteCard.setOrientation(LinearLayout.VERTICAL);
        root.addView(noteCard, lp(-1, -2, 0, 0, 0, 14));

        noteCard.addView(text("Pesan untuk kurir", 14, "#0B3A78", true));
        noteInput = new EditText(this);
        noteInput.setMinLines(3);
        noteInput.setGravity(Gravity.TOP);
        noteInput.setTextSize(14);
        noteInput.setTextColor(Color.parseColor("#0F172A"));
        noteInput.setHintTextColor(Color.parseColor("#94A3B8"));
        noteInput.setHint("Contoh: jemput di depan rumah, pagar biru...");
        noteInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        noteInput.setBackground(roundStroke("#F8FBFF", "#D8E4F2", dp(16), 1));
        noteCard.addView(noteInput, lp(-1, dp(94), 0, 10, 0, 0));

        estimateText = text("Estimasi muncul setelah lokasi lengkap", 13, "#64748B", false);
        estimateText.setPadding(dp(14), dp(12), dp(14), dp(12));
        estimateText.setBackground(round("#EAF4FF", dp(16)));
        root.addView(estimateText, lp(-1, -2, 0, 0, 0, 14));

        orderBtn = primaryButton("Order TransRide");
        root.addView(orderBtn, new LinearLayout.LayoutParams(-1, dp(54)));
        orderBtn.setOnClickListener(v -> confirmAndCreateOrder());

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams pLp = new FrameLayout.LayoutParams(dp(54), dp(54));
        pLp.gravity = Gravity.CENTER;
        page.addView(progressBar, pLp);

        setContentView(page);
    }

    private TextView locationRow(LinearLayout parent, String emoji, String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));

        TextView dot = text(emoji, 24, "#0B7CFF", true);
        dot.setGravity(Gravity.CENTER);
        dot.setBackground(round("#EAF4FF", dp(16)));
        row.addView(dot, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, -2, 1);
        cLp.setMargins(dp(12), 0, 0, 0);
        row.addView(col, cLp);

        col.addView(text(title, 12, "#64748B", true));
        TextView value = text(subtitle, 14, "#0B3A78", true);
        value.setMaxLines(3);
        col.addView(value);
        return value;
    }

    private void getCurrentLocation(String mode) {
        pickingMode = "delivery".equals(mode) ? "delivery" : "pickup";

        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }

        setLoading(true, "Mengambil lokasi...");

        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) {
                setLoading(false, null);
                showInfo("GPS", "Lokasi tidak tersedia di perangkat ini.");
                return;
            }

            boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (!gps && !network) {
                setLoading(false, null);
                showGpsDialog();
                return;
            }

            Location last = null;
            try { last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
            if (last == null) {
                try { last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); } catch (Exception ignored) {}
            }
            if (last != null) applyLocation(pickingMode, last);

            String provider = gps ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            locationManager.requestSingleUpdate(provider, new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    applyLocation(pickingMode, location);
                    setLoading(false, null);
                }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            }, Looper.getMainLooper());

            mainHandler.postDelayed(() -> setLoading(false, null), 6500);

        } catch (Exception e) {
            setLoading(false, null);
            showInfo("GPS", "Gagal mengambil lokasi. Pastikan izin lokasi aktif.");
        }
    }

    private void applyLocation(String mode, Location location) {
        if (location == null) return;

        double lat = location.getLatitude();
        double lng = location.getLongitude();
        userLat = lat;
        userLng = lng;

        new Thread(() -> {
            String addr = getAddress(lat, lng);
            mainHandler.post(() -> {
                if ("delivery".equals(mode)) {
                    deliveryLat = lat;
                    deliveryLng = lng;
                    deliveryAddress = addr;
                } else {
                    pickupLat = lat;
                    pickupLng = lng;
                    pickupAddress = addr;
                }
                updateLocationTexts();
            });
        }).start();
    }

    private void useGoogleMapsLink() {
        String link = googleLinkInput.getText().toString().trim();
        if (link.isEmpty()) {
            showInfo("Link Kosong", "Paste link Google Maps tujuan terlebih dahulu.");
            return;
        }

        setLoading(true, "Membaca link...");

        new Thread(() -> {
            try {
                String finalLink = link;
                if (link.contains("maps.app.goo.gl") || link.contains("goo.gl/maps")) {
                    JSONObject res = postJson(RESOLVE_MAP_URL, new JSONObject().put("url", link));
                    if (res.optBoolean("success", false)) {
                        finalLink = res.optString("url", link);
                    }
                }

                double[] coords = extractLatLng(finalLink);
                if (coords == null) {
                    mainHandler.post(() -> {
                        setLoading(false, null);
                        showInfo("Lokasi Tidak Valid", "Link Google Maps tidak bisa dibaca. Pastikan link berasal dari titik lokasi.");
                    });
                    return;
                }

                String addr = getAddress(coords[0], coords[1]);
                mainHandler.post(() -> {
                    deliveryLat = coords[0];
                    deliveryLng = coords[1];
                    deliveryAddress = addr;
                    updateLocationTexts();
                    setLoading(false, null);
                    showInfo("Tujuan Dipilih", "Lokasi tujuan dari Google Maps berhasil digunakan.");
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setLoading(false, null);
                    showInfo("Gagal", "Gagal membaca link Google Maps.");
                });
            }
        }).start();
    }

    private double[] extractLatLng(String link) {
        try {
            String decoded = Uri.decode(link);
            String[] patterns = new String[]{
                    "[?&]q=(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)",
                    "@(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)",
                    "!3d(-?\\d+(?:\\.\\d+)?)!4d(-?\\d+(?:\\.\\d+)?)"
            };
            for (String p : patterns) {
                Matcher m = Pattern.compile(p).matcher(decoded);
                if (m.find()) return new double[]{Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2))};
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void updateLocationTexts() {
        if (pickupLat != 0 && pickupLng != 0) {
            pickupText.setText(shortAddr(pickupAddress) + "\n" + fmt(pickupLat) + ", " + fmt(pickupLng));
        } else {
            pickupText.setText("Belum dipilih");
        }

        if (deliveryLat != 0 && deliveryLng != 0) {
            deliveryText.setText(shortAddr(deliveryAddress) + "\n" + fmt(deliveryLat) + ", " + fmt(deliveryLng));
        } else {
            deliveryText.setText("Belum dipilih");
        }

        if (pickupLat != 0 && pickupLng != 0 && deliveryLat != 0 && deliveryLng != 0) {
            double km = haversineKm(pickupLat, pickupLng, deliveryLat, deliveryLng) * 1.25;
            int minutes = Math.max(1, (int) Math.ceil(km * 4));
            estimateText.setText("Estimasi jarak: " + String.format(Locale.US, "%.1f", km) + " KM • Estimasi waktu: " + minutes + " menit\nOngkir final dihitung server saat order dibuat.");
        } else {
            estimateText.setText("Estimasi muncul setelah lokasi jemput dan tujuan lengkap");
        }
    }

    private void confirmAndCreateOrder() {
        if (loading) return;

        if (userId <= 0) {
            showInfo("Sesi Berakhir", "Sesi login tidak valid. Silakan login ulang.");
            return;
        }

        if (pickupLat == 0 || pickupLng == 0) {
            showInfo("Pickup Belum Ada", "Pilih lokasi jemput terlebih dahulu.");
            return;
        }

        if (deliveryLat == 0 || deliveryLng == 0) {
            showInfo("Tujuan Belum Ada", "Pilih lokasi tujuan terlebih dahulu.");
            return;
        }

        double km = haversineKm(pickupLat, pickupLng, deliveryLat, deliveryLng) * 1.25;
        int minutes = Math.max(1, (int) Math.ceil(km * 4));

        new AlertDialog.Builder(this)
                .setTitle("Konfirmasi Order")
                .setMessage("Layanan: Transbike\nJarak estimasi: " + String.format(Locale.US, "%.1f", km) + " KM\nEstimasi waktu: " + minutes + " menit\n\nLanjut order sekarang?")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Order", (d, w) -> createOrder())
                .show();
    }

    private void createOrder() {
        setLoading(true, "Menyimpan order...");

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("user_id", userId);
                payload.put("id", "ORD-" + System.currentTimeMillis());
                payload.put("order_type", "Transbike");
                payload.put("driver_type", "bike");
                payload.put("service_name", "Transbike");
                payload.put("note", noteInput.getText().toString().trim());
                payload.put("status", "pending");

                JSONObject pickup = new JSONObject();
                pickup.put("latitude", pickupLat);
                pickup.put("longitude", pickupLng);
                pickup.put("address", pickupAddress);
                payload.put("pickup", pickup);

                JSONObject delivery = new JSONObject();
                delivery.put("latitude", deliveryLat);
                delivery.put("longitude", deliveryLng);
                delivery.put("address", deliveryAddress);
                payload.put("delivery", delivery);

                double km = haversineKm(pickupLat, pickupLng, deliveryLat, deliveryLng) * 1.25;
                JSONObject route = new JSONObject();
                route.put("km", km);
                route.put("minutes", Math.max(1, (int) Math.ceil(km * 4)));
                route.put("price", 0);
                payload.put("route", route);

                JSONObject userLocation = new JSONObject();
                userLocation.put("latitude", userLat == 0 ? pickupLat : userLat);
                userLocation.put("longitude", userLng == 0 ? pickupLng : userLng);
                payload.put("userLocation", userLocation);

                JSONObject save = postJson(CREATE_ORDER_URL, payload);

                mainHandler.post(() -> {
                    setLoading(false, null);
                    if (save.optBoolean("success", false)) {
                        String orderId = firstNonEmpty(save.optString("order_id", ""), save.optString("id", ""));
                        String price = rupiah(save.optDouble("price", 0));
                        showSuccessOrder(orderId, price);
                    } else {
                        showInfo("Order Gagal", firstNonEmpty(save.optString("message", ""), "Gagal membuat order."));
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setLoading(false, null);
                    showInfo("Order Gagal", "Koneksi server bermasalah. Silakan coba lagi.");
                });
            }
        }).start();
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
            writer.write(payload.toString());
            writer.flush();
            writer.close();
            os.close();

            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
            String body = readStream(is).trim();
            if (body.isEmpty()) return new JSONObject();
            return new JSONObject(body);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String getAddress(double lat, double lng) {
        try {
            Geocoder geocoder = new Geocoder(this, new Locale("id", "ID"));
            List<Address> list = geocoder.getFromLocation(lat, lng, 1);
            if (list != null && list.size() > 0) {
                Address a = list.get(0);
                return firstNonEmpty(a.getAddressLine(0), a.getSubAdminArea(), a.getLocality(), a.getAdminArea(), "Lokasi dipilih");
            }
        } catch (Exception ignored) {}
        return "Lokasi dipilih";
    }

    private void showSuccessOrder(String orderId, String price) {
        new AlertDialog.Builder(this)
                .setTitle("Order Berhasil")
                .setMessage("Order berhasil dibuat.\nOrder ID: " + orderId + "\nOngkir: " + price + "\n\nTransiva akan mencarikan driver terdekat.")
                .setCancelable(false)
                .setPositiveButton("Cari Driver", (d, w) -> openSearchDriver(orderId))
                .show();
    }

    private void openSearchDriver(String orderId) {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("native_route", "?route=searchDriver");
            intent.putExtra("url", BASE_URL + "?route=searchDriver");
            intent.putExtra("active_order_id", orderId);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            finish();
        }
    }

    private void showGpsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("GPS Belum Aktif")
                .setMessage("Aktifkan GPS untuk memakai TransRide.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Aktifkan", (d, w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                .show();
    }

    private int checkPermission(String permission) {
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
            if (ok) getCurrentLocation(pickingMode);
            else showInfo("Izin Lokasi Ditolak", "TransRide membutuhkan izin lokasi agar titik jemput akurat.");
        }
    }

    private void setLoading(boolean value, String text) {
        loading = value;
        if (progressBar != null) progressBar.setVisibility(value ? View.VISIBLE : View.GONE);
        if (orderBtn != null) orderBtn.setEnabled(!value);
        if (pickupBtn != null) pickupBtn.setEnabled(!value);
        if (deliveryBtn != null) deliveryBtn.setEnabled(!value);
        if (linkBtn != null) linkBtn.setEnabled(!value);
        if (orderBtn != null) orderBtn.setText(value ? firstNonEmpty(text, "Memuat...") : "Order TransRide");
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

    private String shortAddr(String value) {
        String clean = firstNonEmpty(value, "Lokasi dipilih");
        if (clean.length() > 70) clean = clean.substring(0, 70).trim() + "...";
        return clean;
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
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

    private String fmt(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && v.trim().length() > 0 && !v.trim().equalsIgnoreCase("null")) return v.trim();
        }
        return "";
    }

    private LinearLayout card(String bg, String stroke, int radius) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        l.setBackground(roundStroke(bg, stroke, dp(radius), 1));
        return l;
    }

    private void addDivider(LinearLayout parent) {
        View v = new View(this);
        v.setBackgroundColor(Color.parseColor("#E2E8F0"));
        parent.addView(v, lp(-1, 1, dp(60), dp(10), 0, dp(10)));
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

    private Button smallButton(String value, String bg, String fg, String stroke) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.parseColor(fg));
        b.setBackground(roundStroke(bg, stroke, dp(16), 1));
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

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    private void showInfo(String title, String message) {
        try {
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Exception e) {
            android.widget.Toast.makeText(
                    this,
                    message,
                    android.widget.Toast.LENGTH_LONG
            ).show();
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
