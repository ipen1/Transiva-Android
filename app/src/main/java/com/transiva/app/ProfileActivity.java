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
import android.view.inputmethod.EditorInfo;
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
import java.util.List;
import java.util.Locale;

public class ProfileActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String UPDATE_URL = BASE_URL + "server/update_profile.php";
    private static final int TIMEOUT_MS = 25000;
    private static final int REQ_LOCATION = 2101;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private EditText usernameInput;
    private EditText passwordInput;
    private EditText addressInput;
    private TextView locationInfo;
    private TextView titleName;
    private Button locationBtn;
    private Button saveBtn;
    private Button backBtn;
    private Button logoutBtn;
    private ProgressBar progressBar;

    private SessionManager session;
    private LocationManager locationManager;

    private String userId = "";
    private String username = "";
    private String deliveryAddress = "";
    private String deliveryLat = "";
    private String deliveryLng = "";
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            getWindow().setStatusBarColor(Color.parseColor("#071426"));
            getWindow().setNavigationBarColor(Color.parseColor("#071426"));
        } catch (Exception ignored) {}

        session = new SessionManager(this);
        loadSession();
        buildLayout();
    }

    private void loadSession() {
        try {
            userId = firstNonEmpty(session.getId(), session.getUserId());
            username = firstNonEmpty(session.getUsername(), session.getName(), "User");

            JSONObject obj = session.getSessionJson();
            deliveryAddress = firstNonEmpty(
                    obj.optString("delivery_address", ""),
                    session.get("delivery_address")
            );
            deliveryLat = firstNonEmpty(
                    obj.optString("delivery_lat", ""),
                    session.get("delivery_lat"),
                    session.get("last_latitude")
            );
            deliveryLng = firstNonEmpty(
                    obj.optString("delivery_lng", ""),
                    session.get("delivery_lng"),
                    session.get("last_longitude")
            );
        } catch (Exception ignored) {}
    }

    private void buildLayout() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F3F8FF"));

        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(22), dp(16), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(14));
        header.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(24), 1));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView avatar = text("👤", 26, "#FFFFFF", true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(roundGradient("#086BFF", "#2EA2FF", dp(22)));
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(dp(50), dp(50));
        aLp.setMargins(0, 0, dp(12), 0);
        header.addView(avatar, aLp);

        LinearLayout hText = new LinearLayout(this);
        hText.setOrientation(LinearLayout.VERTICAL);
        header.addView(hText, new LinearLayout.LayoutParams(0, -2, 1));

        titleName = text("Profil Saya", 19, "#0B3A78", true);
        hText.addView(titleName);

        TextView sub = text("Kelola akun dan alamat delivery", 12, "#64748B", false);
        sub.setPadding(0, dp(3), 0, 0);
        hText.addView(sub);

        TextView badge = text("Verified", 11, "#0B7CFF", true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        badge.setBackground(roundStroke("#EAF4FF", "#B9DBFF", dp(18), 1));
        header.addView(badge, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(24), 1));
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(-1, -2);
        cLp.setMargins(0, dp(14), 0, dp(14));
        root.addView(card, cLp);

        card.addView(label("Username"));
        usernameInput = input("Masukkan username", InputType.TYPE_CLASS_TEXT);
        usernameInput.setText(username);
        card.addView(usernameInput, fieldLp());

        card.addView(label("Password Baru"));
        passwordInput = input("Kosongkan jika tidak diganti", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(passwordInput, fieldLp());

        card.addView(label("Alamat Delivery"));
        addressInput = input("Contoh: Jl. Trans Sulawesi, dekat Indomaret...", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        addressInput.setSingleLine(false);
        addressInput.setMinLines(3);
        addressInput.setGravity(Gravity.TOP | Gravity.START);
        addressInput.setText(deliveryAddress);
        LinearLayout.LayoutParams addrLp = new LinearLayout.LayoutParams(-1, dp(96));
        addrLp.setMargins(0, 0, 0, dp(12));
        card.addView(addressInput, addrLp);

        locationInfo = text(getLocationText(), 12, "#0B3A78", true);
        locationInfo.setPadding(dp(12), dp(10), dp(12), dp(10));
        locationInfo.setBackground(roundStroke("#F1F8FF", "#B9DBFF", dp(16), 1));
        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(-1, -2);
        lLp.setMargins(0, 0, 0, dp(12));
        card.addView(locationInfo, lLp);

        locationBtn = outlineButton("📍 Gunakan Lokasi Saat Ini");
        card.addView(locationBtn, buttonLp());

        saveBtn = primaryButton("Simpan Profil");
        card.addView(saveBtn, buttonLp());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));

        backBtn = outlineButton("Kembali");
        logoutBtn = dangerButton("Keluar");

        row.addView(backBtn, new LinearLayout.LayoutParams(0, dp(50), 1));
        LinearLayout.LayoutParams outLp = new LinearLayout.LayoutParams(0, dp(50), 1);
        outLp.setMargins(dp(10), 0, 0, 0);
        row.addView(logoutBtn, outLp);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams pLp = new FrameLayout.LayoutParams(dp(52), dp(52));
        pLp.gravity = Gravity.CENTER;
        page.addView(progressBar, pLp);

        setContentView(page);

        locationBtn.setOnClickListener(v -> loadCurrentLocation());
        saveBtn.setOnClickListener(v -> saveProfile());
        backBtn.setOnClickListener(v -> finish());
        logoutBtn.setOnClickListener(v -> confirmLogout());

        addressInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
    }

    private String getLocationText() {
        if (!deliveryLat.isEmpty() && !deliveryLng.isEmpty()) {
            return "✅ Lokasi GPS sudah tersimpan";
        }
        return "📍 Lokasi GPS belum dipilih";
    }

    private void loadCurrentLocation() {
        if (loading) return;

        if (checkSelfPermissionSafe(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermissionSafe(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }

        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) {
                showInfo("GPS Tidak Tersedia", "GPS tidak tersedia di perangkat ini.");
                return;
            }

            boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (!gps && !network) {
                new AlertDialog.Builder(this)
                        .setTitle("GPS Belum Aktif")
                        .setMessage("Aktifkan lokasi/GPS untuk mengambil alamat delivery.")
                        .setPositiveButton("Buka Pengaturan", (d, w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                        .setNegativeButton("Batal", null)
                        .show();
                return;
            }

            setLoading(true, "Mengambil lokasi...");

            Location best = null;
            try { best = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
            if (best == null) {
                try { best = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); } catch (Exception ignored) {}
            }

            if (best != null) {
                applyLocation(best);
            }

            String provider = gps ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            locationManager.requestSingleUpdate(provider, new LocationListener() {
                @Override public void onLocationChanged(Location location) { applyLocation(location); }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            }, Looper.getMainLooper());

            mainHandler.postDelayed(() -> {
                if (loading) {
                    setLoading(false, "");
                    locationInfo.setText(getLocationText());
                }
            }, 15000);

        } catch (Exception e) {
            setLoading(false, "");
            showInfo("GPS Gagal", "Gagal mengambil lokasi. Pastikan izin lokasi aktif.");
        }
    }

    private void applyLocation(Location location) {
        if (location == null) return;

        deliveryLat = String.valueOf(location.getLatitude());
        deliveryLng = String.valueOf(location.getLongitude());

        try {
            session.saveLastLocation(deliveryLat, deliveryLng);
        } catch (Exception ignored) {}

        new Thread(() -> {
            String shortName = "";
            String fullAddress = "";

            try {
                Geocoder geocoder = new Geocoder(ProfileActivity.this, new Locale("id", "ID"));
                List<Address> list = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                if (list != null && list.size() > 0) {
                    Address a = list.get(0);
                    shortName = cleanLocationName(firstNonEmpty(a.getSubAdminArea(), a.getLocality(), a.getAdminArea()));
                    fullAddress = firstNonEmpty(
                            a.getAddressLine(0),
                            a.getThoroughfare(),
                            a.getFeatureName()
                    );
                }
            } catch (Exception ignored) {}

            if (fullAddress.isEmpty()) {
                fullAddress = "Lat: " + round6(location.getLatitude()) + ", Lng: " + round6(location.getLongitude());
            }

            String finalShortName = shortName;
            String finalFullAddress = fullAddress;

            mainHandler.post(() -> {
                setLoading(false, "");
                locationInfo.setText(
                        finalShortName.isEmpty()
                                ? "✅ Lokasi GPS berhasil dipilih"
                                : "✅ " + finalShortName
                );

                if (addressInput.getText().toString().trim().isEmpty()) {
                    addressInput.setText(finalFullAddress);
                }

                showInfo("Lokasi Berhasil", "Lokasi GPS berhasil dipilih.\n\nJangan lupa tekan Simpan Profil.");
            });
        }).start();
    }

    private void saveProfile() {
        if (loading) return;

        String newUsername = usernameInput.getText().toString().trim();
        String newPassword = passwordInput.getText().toString().trim();
        String newAddress = addressInput.getText().toString().trim();

        if (userId.isEmpty()) {
            showInfo("Sesi Berakhir", "Sesi login tidak ditemukan. Silakan login ulang.");
            openLoginClear();
            return;
        }

        if (newUsername.isEmpty()) {
            showInfo("Data Belum Lengkap", "Username wajib diisi.");
            return;
        }

        if (newUsername.length() < 3) {
            showInfo("Username Terlalu Pendek", "Username minimal 3 karakter.");
            return;
        }

        if (!newPassword.isEmpty() && newPassword.length() < 6) {
            showInfo("Password Terlalu Pendek", "Password minimal 6 karakter.");
            return;
        }

        if (newAddress.isEmpty()) {
            showInfo("Alamat Belum Lengkap", "Alamat delivery wajib diisi.");
            return;
        }

        setLoading(true, "Menyimpan...");

        new Thread(() -> {
            SaveResult result = doSave(newUsername, newPassword, newAddress);
            mainHandler.post(() -> {
                setLoading(false, "");
                if (result.success) {
                    try {
                        if (result.user != null) {
                            session.saveUser(result.user);
                        }
                        session.put("delivery_address", newAddress);
                        session.put("delivery_lat", deliveryLat);
                        session.put("delivery_lng", deliveryLng);
                    } catch (Exception ignored) {}

                    showInfo("Berhasil", "Profil berhasil disimpan.");
                    mainHandler.postDelayed(() -> {
                        Intent i = new Intent(ProfileActivity.this, CustomerDashboardActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(i);
                        finish();
                    }, 500);
                } else {
                    showInfo("Gagal", result.message);
                }
            });
        }).start();
    }

    private SaveResult doSave(String newUsername, String newPassword, String newAddress) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(UPDATE_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            JSONObject payload = new JSONObject();
            payload.put("id", userId);
            payload.put("username", newUsername);
            payload.put("password", newPassword);
            payload.put("delivery_address", newAddress);
            payload.put("delivery_lat", deliveryLat);
            payload.put("delivery_lng", deliveryLng);

            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
            writer.write(payload.toString());
            writer.flush();
            writer.close();
            os.close();

            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
            String body = readStream(is).trim();

            if (body.isEmpty()) {
                return SaveResult.fail("Server tidak mengirim response.");
            }

            JSONObject json = new JSONObject(body);
            boolean success = json.optBoolean("success", false);
            String message = json.optString("message", success ? "Profil berhasil disimpan." : "Gagal menyimpan profil.");

            if (!success) {
                return SaveResult.fail(message);
            }

            JSONObject user = json.optJSONObject("user");
            if (user == null) {
                user = new JSONObject();
                user.put("id", userId);
                user.put("username", newUsername);
                user.put("role", "customer");
                user.put("delivery_address", newAddress);
                user.put("delivery_lat", deliveryLat);
                user.put("delivery_lng", deliveryLng);
            }

            return SaveResult.ok(message, user);

        } catch (Exception e) {
            return SaveResult.fail("Terjadi kesalahan koneksi server.");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Keluar Akun")
                .setMessage("Yakin ingin keluar dari akun Transiva?")
                .setPositiveButton("Keluar", (d, w) -> {
                    try {
                        session.logout();
                    } catch (Exception ignored) {}
                    openLoginClear();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void openLoginClear() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
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
                for (int g : grantResults) {
                    if (g == PackageManager.PERMISSION_GRANTED) ok = true;
                }
            }

            if (ok) {
                loadCurrentLocation();
            } else {
                showInfo("Izin Lokasi Ditolak", "Aktifkan izin lokasi agar alamat delivery bisa memakai GPS.");
            }
        }
    }

    private void setLoading(boolean value, String buttonText) {
        loading = value;
        progressBar.setVisibility(value ? View.VISIBLE : View.GONE);

        usernameInput.setEnabled(!value);
        passwordInput.setEnabled(!value);
        addressInput.setEnabled(!value);
        locationBtn.setEnabled(!value);
        saveBtn.setEnabled(!value);
        backBtn.setEnabled(!value);
        logoutBtn.setEnabled(!value);

        if (value && !buttonText.isEmpty()) {
            if (buttonText.toLowerCase(Locale.US).contains("lokasi")) {
                locationBtn.setText(buttonText);
            } else {
                saveBtn.setText(buttonText);
            }
        } else {
            locationBtn.setText("📍 Gunakan Lokasi Saat Ini");
            saveBtn.setText("Simpan Profil");
        }
    }

    private TextView label(String value) {
        TextView tv = text(value, 13, "#0B3A78", true);
        tv.setPadding(0, dp(6), 0, dp(6));
        return tv;
    }

    private EditText input(String hint, int type) {
        EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setTextSize(14);
        et.setTextColor(Color.parseColor("#0F172A"));
        et.setHintTextColor(Color.parseColor("#94A3B8"));
        et.setHint(hint);
        et.setInputType(type);
        et.setPadding(dp(14), 0, dp(14), 0);
        et.setBackground(roundStroke("#FFFFFF", "#D8E4F2", dp(16), 1));
        return et;
    }

    private LinearLayout.LayoutParams fieldLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.setMargins(0, 0, 0, dp(12));
        return lp;
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.setMargins(0, 0, 0, dp(10));
        return lp;
    }

    private Button primaryButton(String value) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(value);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackground(roundGradient("#086BFF", "#2EA2FF", dp(17)));
        return b;
    }

    private Button outlineButton(String value) {
        Button b = primaryButton(value);
        b.setTextColor(Color.parseColor("#0B7CFF"));
        b.setBackground(roundStroke("#FFFFFF", "#9DCAFF", dp(17), 1));
        return b;
    }

    private Button dangerButton(String value) {
        Button b = primaryButton(value);
        b.setTextColor(Color.WHITE);
        b.setBackground(roundGradient("#EF4444", "#DC2626", dp(17)));
        return b;
    }

    private TextView text(String value, int sp, String color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(Color.parseColor(color));
        tv.setIncludeFontPadding(true);
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
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor(start), Color.parseColor(end)}
        );
        gd.setCornerRadius(radius);
        return gd;
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

    private void showInfo(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private String cleanLocationName(String value) {
        String v = firstNonEmpty(value);
        v = v.replace("Kabupaten ", "");
        v = v.replace("Kota ", "");
        v = v.replace("Regency", "");
        v = v.replace("City", "");
        v = v.trim();

        if (v.length() == 0) return "";
        return v;
    }

    private String round6(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null) {
                String clean = v.trim();
                if (clean.length() > 0 && !clean.equalsIgnoreCase("null") && !clean.equalsIgnoreCase("undefined")) {
                    return clean;
                }
            }
        }
        return "";
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class SaveResult {
        final boolean success;
        final String message;
        final JSONObject user;

        SaveResult(boolean success, String message, JSONObject user) {
            this.success = success;
            this.message = message;
            this.user = user;
        }

        static SaveResult ok(String message, JSONObject user) {
            return new SaveResult(true, message, user);
        }

        static SaveResult fail(String message) {
            return new SaveResult(false, message, null);
        }
    }
}
