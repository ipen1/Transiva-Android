package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.firebase.messaging.FirebaseMessaging;

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
import java.util.Locale;

public class LoginActivity extends Activity {

    private static final String TAG = "TRANSIVA_LOGIN_FCM";
    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String LOGIN_URL = BASE_URL + "server/login.php";
    private static final String SAVE_FCM_URL = BASE_URL + "server/save_fcm_token.php";
    private static final String PRIVACY_URL = BASE_URL + "privacy.html";
    private static final String TERMS_URL = BASE_URL + "terms.html";
    private static final int TIMEOUT_MS = 25000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private EditText usernameInput;
    private EditText passwordInput;
    private Button loginBtn;
    private Button updateBtn;
    private TextView messageText;
    private TextView versionText;
    private ProgressBar progressBar;
    private ImageButton eyeBtn;

    private boolean loading = false;
    private boolean passwordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            getWindow().setStatusBarColor(Color.parseColor("#0A1A2E"));
            getWindow().setNavigationBarColor(Color.parseColor("#0A1A2E"));
        } catch (Exception ignored) {}

        buildLayout();
    }

    private void buildLayout() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F4F8FF"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(20));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        ImageView logo = new ImageView(this);
        int logoRes = findDrawable("transiva_logo");
        if (logoRes == 0) logoRes = findDrawable("logo_transiva");
        if (logoRes == 0) logoRes = findDrawable("logo");
        if (logoRes == 0) logoRes = getApplicationInfo().icon;
        logo.setImageResource(logoRes);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(175), dp(70));
        logoLp.setMargins(0, dp(4), 0, dp(4));
        root.addView(logo, logoLp);

        TextView title = text("Masuk Transiva", 26, "#123F7A", true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = text("Masuk untuk mulai pesan atau menerima order", 14, "#667085", false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(dp(4), dp(6), dp(4), dp(16));
        root.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(20), dp(18), dp(18));
        card.setBackground(roundStroke("#FFFFFF", "#EEF3FA", dp(24), 1));
        card.setElevation(dp(5));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, 0, 0, dp(18));
        root.addView(card, cardLp);

        messageText = text("", 12, "#D32F2F", true);
        messageText.setVisibility(View.GONE);
        messageText.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(-1, -2);
        msgLp.setMargins(0, 0, 0, dp(12));
        card.addView(messageText, msgLp);

        card.addView(label("Nama Pengguna"));
        usernameInput = input("Masukkan Nama Pengguna", InputType.TYPE_CLASS_TEXT, false);
        card.addView(usernameInput, fieldLp());

        card.addView(label("Kata Sandi"));
        FrameLayout passBox = new FrameLayout(this);
        passwordInput = input(
                "Masukkan Kata Sandi",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                true
        );
        passBox.addView(passwordInput, new FrameLayout.LayoutParams(-1, -1));

        eyeBtn = new ImageButton(this);
        eyeBtn.setBackgroundColor(Color.TRANSPARENT);
        eyeBtn.setImageResource(android.R.drawable.ic_menu_view);
        eyeBtn.setColorFilter(Color.parseColor("#1E88F5"));

        FrameLayout.LayoutParams eyeLp = new FrameLayout.LayoutParams(dp(42), dp(42));
        eyeLp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        eyeLp.rightMargin = dp(4);
        passBox.addView(eyeBtn, eyeLp);
        card.addView(passBox, fieldLp());

        loginBtn = new Button(this);
        loginBtn.setAllCaps(false);
        loginBtn.setText("Masuk →");
        loginBtn.setTextSize(17);
        loginBtn.setTypeface(Typeface.DEFAULT_BOLD);
        loginBtn.setTextColor(Color.WHITE);
        loginBtn.setBackground(roundGradient("#006BEF", "#2E9BFF", dp(16)));

        LinearLayout.LayoutParams loginLp = new LinearLayout.LayoutParams(-1, dp(52));
        loginLp.setMargins(0, dp(8), 0, dp(14));
        card.addView(loginBtn, loginLp);

        TextView register = text("Belum punya akun? Daftar", 14, "#1685F2", true);
        register.setGravity(Gravity.CENTER);
        register.setPadding(0, dp(2), 0, dp(14));
        card.addView(register, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout versionBox = new LinearLayout(this);
        versionBox.setOrientation(LinearLayout.HORIZONTAL);
        versionBox.setGravity(Gravity.CENTER_VERTICAL);
        versionBox.setPadding(dp(12), dp(10), dp(12), dp(10));
        versionBox.setBackground(round("#F1F6FF", dp(16)));

        LinearLayout.LayoutParams versionBoxLp = new LinearLayout.LayoutParams(-1, -2);
        versionBoxLp.setMargins(0, 0, 0, dp(14));
        card.addView(versionBox, versionBoxLp);

        TextView shield = text("✓", 22, "#FFFFFF", true);
        shield.setGravity(Gravity.CENTER);
        shield.setBackground(round("#1685F2", dp(34)));

        LinearLayout.LayoutParams shieldLp = new LinearLayout.LayoutParams(dp(46), dp(46));
        shieldLp.setMargins(0, 0, dp(10), 0);
        versionBox.addView(shield, shieldLp);

        LinearLayout verTexts = new LinearLayout(this);
        verTexts.setOrientation(LinearLayout.VERTICAL);
        versionBox.addView(verTexts, new LinearLayout.LayoutParams(0, -2, 1f));

        versionText = text("Versi Aplikasi : 1.0", 13, "#123F7A", true);
        verTexts.addView(versionText, new LinearLayout.LayoutParams(-1, -2));

        TextView verSub = text("Aplikasi selalu diperbarui", 11, "#667085", false);
        verTexts.addView(verSub, new LinearLayout.LayoutParams(-1, -2));

        updateBtn = new Button(this);
        updateBtn.setAllCaps(false);
        updateBtn.setText("Cek");
        updateBtn.setTextSize(12);
        updateBtn.setTypeface(Typeface.DEFAULT_BOLD);
        updateBtn.setTextColor(Color.parseColor("#1685F2"));
        updateBtn.setBackground(roundStroke("#FFFFFF", "#9BCBFF", dp(16), 1));
        versionBox.addView(updateBtn, new LinearLayout.LayoutParams(dp(74), dp(40)));

        LinearLayout legal = new LinearLayout(this);
        legal.setGravity(Gravity.CENTER);
        legal.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(legal, new LinearLayout.LayoutParams(-1, -2));

        TextView privacy = text("Kebijakan Privasi", 12, "#1685F2", true);
        TextView sep = text(" | ", 12, "#CBD5E1", false);
        TextView terms = text("Syarat & Ketentuan", 12, "#1685F2", true);
        legal.addView(privacy);
        legal.addView(sep);
        legal.addView(terms);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressLp = new FrameLayout.LayoutParams(dp(52), dp(52));
        progressLp.gravity = Gravity.CENTER;
        page.addView(progressBar, progressLp);

        setContentView(page);
        setVersionText();

        loginBtn.setOnClickListener(v -> attemptLogin());
        eyeBtn.setOnClickListener(v -> togglePassword());
        register.setOnClickListener(v -> openRegister());
        privacy.setOnClickListener(v -> openBrowser(PRIVACY_URL));
        terms.setOnClickListener(v -> openBrowser(TERMS_URL));
        updateBtn.setOnClickListener(v -> showInfo(
                "Pembaruan Aplikasi",
                "Cek pembaharuan native siap.\nHubungkan endpoint versi jika ingin otomatis."
        ));

        passwordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                attemptLogin();
                return true;
            }
            return false;
        });
    }

    private void attemptLogin() {
        if (loading) return;

        clearMessage();

        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showMessage("Lengkapi Nama Pengguna dan Kata Sandi", false);
            return;
        }

        setLoading(true);

        new Thread(() -> {
            LoginResult result = doLogin(username, password);

            mainHandler.post(() -> {
                setLoading(false);

                if (!result.success) {
                    showMessage(result.message, false);
                    return;
                }

                if (result.user != null) {
                    try {
                        new SessionManager(LoginActivity.this).saveUser(result.user);
                    } catch (Exception ignored) {}

                    try {
                        TransivaSession.saveUser(LoginActivity.this, result.user);
                    } catch (Exception ignored) {}

                    saveFcmTokenAfterLogin(result.user);
                }

                String cleanRole = normalizeRole(result.role);
                showMessage("Login berhasil", true);
                mainHandler.postDelayed(() -> openRolePage(cleanRole), 600);
            });
        }).start();
    }

    private LoginResult doLogin(String username, String password) {
        HttpURLConnection conn = null;

        try {
            URL url = new URL(LOGIN_URL);
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
            payload.put("username", username);
            payload.put("password", password);

            String cachedFcmToken = getCachedFcmToken();
            if (!cachedFcmToken.isEmpty()) {
                payload.put("fcm_token", cachedFcmToken);
                payload.put("token", cachedFcmToken);
                payload.put("platform", "android_native");
            }

            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
            writer.write(payload.toString());
            writer.flush();
            writer.close();
            os.close();

            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
            String body = readStream(is).trim();

            if (body.isEmpty()) return LoginResult.fail("Server tidak mengirim response");

            JSONObject json = new JSONObject(body);
            boolean success = json.optBoolean("success", false);
            String message = json.optString("message", success ? "Login berhasil" : "Login gagal");

            if (!success) return LoginResult.fail(message);

            JSONObject user = json.optJSONObject("user");
            String role = user != null ? user.optString("role", "customer") : "customer";
            role = normalizeRole(role);

            if (user != null) {
                user.put("role", role);
            }

            return LoginResult.ok(message, role, user);

        } catch (Exception e) {
            return LoginResult.fail("Server error / koneksi gagal");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void saveFcmTokenAfterLogin(JSONObject user) {
        try {
            final int userId = firstPositiveInt(
                    user.optInt("id", 0),
                    user.optInt("user_id", 0),
                    user.optInt("uid", 0)
            );
            final String username = firstNotEmpty(
                    user.optString("username", ""),
                    user.optString("user_name", ""),
                    user.optString("name", "")
            );
            final String role = normalizeRole(user.optString("role", "customer"));

            String cachedToken = getCachedFcmToken();
            if (!cachedToken.isEmpty()) {
                saveTokenLocal(cachedToken, userId, username, role);
                uploadFcmToken(userId, username, role, cachedToken);
            }

            FirebaseMessaging.getInstance().getToken()
                    .addOnSuccessListener(token -> {
                        String cleanToken = token == null ? "" : token.trim();
                        if (cleanToken.isEmpty()) {
                            Log.e(TAG, "Firebase token kosong");
                            return;
                        }

                        saveTokenLocal(cleanToken, userId, username, role);
                        uploadFcmToken(userId, username, role, cleanToken);
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Gagal mengambil FCM token", e));

        } catch (Exception e) {
            Log.e(TAG, "saveFcmTokenAfterLogin error", e);
        }
    }

    private void uploadFcmToken(int userId, String username, String role, String token) {
        final String cleanToken = token == null ? "" : token.trim();
        final String cleanUsername = username == null ? "" : username.trim();

        if (cleanToken.isEmpty()) {
            Log.e(TAG, "Upload FCM dibatalkan: token kosong");
            return;
        }

        if (userId <= 0 && cleanUsername.isEmpty()) {
            Log.e(TAG, "Upload FCM dibatalkan: user_id dan username kosong");
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;

            try {
                URL url = new URL(SAVE_FCM_URL);
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
                payload.put("token", cleanToken);
                payload.put("fcm_token", cleanToken);
                payload.put("user_id", userId);
                payload.put("id", userId);
                payload.put("username", cleanUsername);
                payload.put("role", role == null ? "" : role);
                payload.put("platform", "android_native");

                OutputStream os = conn.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
                writer.write(payload.toString());
                writer.flush();
                writer.close();
                os.close();

                int code = conn.getResponseCode();
                InputStream is = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
                String body = readStream(is).trim();

                Log.d(TAG, "save_fcm_token code=" + code + " body=" + body);

                try {
                    JSONObject json = new JSONObject(body);
                    if (!json.optBoolean("success", false)) {
                        Log.e(TAG, "Server menolak FCM token: " + body);
                    }
                } catch (Exception ignored) {}

            } catch (Exception e) {
                Log.e(TAG, "Upload FCM token gagal", e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private String getCachedFcmToken() {
        try {
            String token = getSharedPreferences("transiva_fcm", MODE_PRIVATE)
                    .getString("fcm_token", "");
            if (token != null && !token.trim().isEmpty()) return token.trim();
        } catch (Exception ignored) {}

        try {
            String token = getSharedPreferences("transiva", MODE_PRIVATE)
                    .getString("fcm_token", "");
            if (token != null && !token.trim().isEmpty()) return token.trim();
        } catch (Exception ignored) {}

        try {
            String token = new SessionManager(this).getFcmToken();
            if (token != null && !token.trim().isEmpty()) return token.trim();
        } catch (Exception ignored) {}

        return "";
    }

    private void saveTokenLocal(String token, int userId, String username, String role) {
        try {
            getSharedPreferences("transiva_fcm", MODE_PRIVATE)
                    .edit()
                    .putString("fcm_token", token == null ? "" : token.trim())
                    .putInt("user_id", userId)
                    .putString("username", username == null ? "" : username.trim())
                    .putString("role", role == null ? "" : role)
                    .putLong("fcm_token_saved_at", System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {}

        try {
            getSharedPreferences("transiva", MODE_PRIVATE)
                    .edit()
                    .putString("fcm_token", token == null ? "" : token.trim())
                    .apply();
        } catch (Exception ignored) {}

        try {
            new SessionManager(this).saveFcmToken(token == null ? "" : token.trim());
        } catch (Exception ignored) {}
    }

    private int firstPositiveInt(int... values) {
        if (values == null) return 0;
        for (int value : values) {
            if (value > 0) return value;
        }
        return 0;
    }

    private String firstNotEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";

        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }

        reader.close();
        return sb.toString();
    }

    private void setVersionText() {
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            versionText.setText("Versi Aplikasi : " + version);
        } catch (Exception ignored) {
            versionText.setText("Versi Aplikasi : 1.0");
        }
    }

    private TextView label(String value) {
        TextView tv = text(value, 14, "#123F7A", true);
        tv.setPadding(0, dp(5), 0, dp(6));
        return tv;
    }

    private EditText input(String hint, int type, boolean hasEye) {
        EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setTextSize(14);
        et.setTextColor(Color.parseColor("#1F2937"));
        et.setHintTextColor(Color.parseColor("#98A2B3"));
        et.setHint(hint);
        et.setInputType(type);
        et.setPadding(dp(18), 0, hasEye ? dp(46) : dp(18), 0);
        et.setBackground(roundStroke("#FFFFFF", "#D8E1ED", dp(16), 1));
        et.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        et.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return et;
    }

    private LinearLayout.LayoutParams fieldLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.setMargins(0, 0, 0, dp(12));
        return lp;
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

    private void togglePassword() {
        int pos = passwordInput.getSelectionStart();
        passwordVisible = !passwordVisible;

        if (passwordVisible) {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        } else {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }

        passwordInput.setSelection(Math.max(0, pos));
    }

    private void setLoading(boolean value) {
        loading = value;
        progressBar.setVisibility(value ? View.VISIBLE : View.GONE);
        loginBtn.setEnabled(!value);
        usernameInput.setEnabled(!value);
        passwordInput.setEnabled(!value);
        loginBtn.setText(value ? "Memuat..." : "Masuk →");
        loginBtn.setAlpha(value ? 0.75f : 1f);
    }

    private void showMessage(String message, boolean success) {
        messageText.setVisibility(View.VISIBLE);
        messageText.setText(message);
        messageText.setTextColor(Color.parseColor(success ? "#166534" : "#B91C1C"));
        messageText.setBackground(round(success ? "#DCFCE7" : "#FEE2E2", dp(12)));
    }

    private void clearMessage() {
        messageText.setText("");
        messageText.setVisibility(View.GONE);
    }

    private void openRolePage(String role) {
        String cleanRole = normalizeRole(role);
        String className;

        switch (cleanRole) {
            case "customer":
                className = "com.transiva.app.CustomerDashboardActivity";
                break;
            case "driver":
                className = "com.transiva.app.DriverDashboardActivity";
                break;
            case "merchant":
                className = "com.transiva.app.MerchantDashboardActivity";
                break;
            case "admin":
                className = "com.transiva.app.AdminDashboardActivity";
                break;
            case "wisata":
                className = "com.transiva.app.NativeHomeActivity";
                break;
            default:
                className = "com.transiva.app.CustomerDashboardActivity";
                break;
        }

        try {
            Class target = Class.forName(className);
            Intent intent = new Intent(this, target);
            intent.putExtra("native_role", cleanRole);
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                            Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            );
            startActivity(intent);
            finish();
        } catch (Exception e) {
            showMessage("Dashboard tidak ditemukan: " + className, false);
        }
    }

    private String normalizeRole(String role) {
        if (role == null) return "customer";

        String clean = role.trim().toLowerCase(Locale.US);

        if (clean.equals("customer") || clean.equals("costumer") || clean.equals("user") || clean.equals("pelanggan")) {
            return "customer";
        }

        if (clean.equals("driver") || clean.equals("kurir") || clean.equals("ojek") || clean.equals("rider")) {
            return "driver";
        }

        if (clean.equals("merchant") || clean.equals("merchen") || clean.equals("resto") || clean.equals("restaurant") || clean.equals("penjual")) {
            return "merchant";
        }

        if (clean.equals("admin") || clean.equals("administrator") || clean.equals("owner") || clean.equals("superadmin")) {
            return "admin";
        }

        if (clean.equals("wisata") || clean.equals("wisataowner") || clean.equals("wisata_owner") || clean.equals("owner_wisata")) {
            return "wisata";
        }

        return "customer";
    }

    private void openRegister() {
        try {
            Intent intent = new Intent(this, RegisterActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            openBrowser(BASE_URL + "?route=register");
        }
    }

    private void openBrowser(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            showInfo("Transiva", "Tidak bisa membuka halaman.");
        }
    }

    private void showInfo(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
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

    private int findDrawable(String name) {
        try {
            return getResources().getIdentifier(name, "drawable", getPackageName());
        } catch (Exception e) {
            return 0;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class LoginResult {
        final boolean success;
        final String message;
        final String role;
        final JSONObject user;

        LoginResult(boolean success, String message, String role, JSONObject user) {
            this.success = success;
            this.message = message;
            this.role = role;
            this.user = user;
        }

        static LoginResult ok(String message, String role, JSONObject user) {
            return new LoginResult(true, message, role, user);
        }

        static LoginResult fail(String message) {
            return new LoginResult(false, message, "customer", null);
        }
    }
}
