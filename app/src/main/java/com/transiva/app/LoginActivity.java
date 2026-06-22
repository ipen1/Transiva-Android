package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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

public class LoginActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String LOGIN_URL = BASE_URL + "server/login.php";
    private static final int TIMEOUT_MS = 25000;

    /* Warna tema WebView Transiva */
    private static final String BG_TOP = "#050B1E";
    private static final String BG_BOTTOM = "#08172F";
    private static final String CARD_BG = "#101827";
    private static final String CARD_STROKE = "#263247";
    private static final String FIELD_BG = "#0D1626";
    private static final String FIELD_STROKE = "#27324A";
    private static final String TEXT_MAIN = "#F8FAFC";
    private static final String TEXT_SOFT = "#94A3B8";
    private static final String BLUE = "#2F7BFF";
    private static final String ORANGE = "#FF7A21";
    private static final String GREEN = "#29B765";
    private static final String LINK_BLUE = "#2E8CFF";

    private SessionManager session;
    private EditText usernameInput;
    private EditText passwordInput;
    private TextView messageText;
    private Button loginButton;
    private ProgressBar progressBar;
    private boolean passwordVisible = false;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);

        if (session.isLoggedIn()) {
            openDashboard(session.getRole());
            return;
        }

        try {
            getWindow().setStatusBarColor(Color.parseColor("#07162F"));
            getWindow().setNavigationBarColor(Color.parseColor("#020817"));
        } catch (Exception ignored) {}

        buildLoginView();
    }

    private void buildLoginView() {
        FrameLayout page = new FrameLayout(this);
        page.setBackground(roundGradientVertical(BG_TOP, BG_BOTTOM, 0));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(14), dp(34), dp(14), dp(34));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        ImageView logo = new ImageView(this);
        int logoRes = getDrawableId("transiva_logo");
        if (logoRes == 0) logoRes = getDrawableId("logo_transiva");
        if (logoRes == 0) logoRes = getDrawableId("logo");
        if (logoRes == 0) logoRes = getApplicationInfo().icon;
        logo.setImageResource(logoRes);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(132), dp(70));
        logoLp.setMargins(0, dp(8), 0, dp(12));
        root.addView(logo, logoLp);

        View glowLine = new View(this);
        glowLine.setBackground(roundGradient(BLUE, "#0B1730", dp(1)));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(230), dp(1));
        glp.setMargins(0, 0, 0, dp(26));
        root.addView(glowLine, glp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(24), dp(20), dp(18));
        card.setBackground(roundStroke(CARD_BG, CARD_STROKE, dp(28), 1));
        card.setElevation(dp(6));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, 0, 0, dp(20));
        root.addView(card, cardLp);

        TextView subtitle = text("Masuk untuk mulai pesan atau menerima\norder", 19, TEXT_SOFT, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(dp(4), 1.0f);
        subtitle.setPadding(0, 0, 0, dp(22));
        card.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        messageText = text("", 14, "#FFFFFF", true);
        messageText.setVisibility(View.GONE);
        messageText.setPadding(dp(14), dp(12), dp(14), dp(12));
        messageText.setBackground(roundStroke("#3A1620", "#7F1D1D", dp(14), 1));
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(-1, -2);
        msgLp.setMargins(0, 0, 0, dp(14));
        card.addView(messageText, msgLp);

        card.addView(label("Nama Pengguna"));
        usernameInput = input("Masukkan Nama Pengguna", false);
        usernameInput.setSingleLine(true);
        usernameInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        card.addView(usernameInput, fieldLp());

        card.addView(label("Kata Sandi"));

        LinearLayout passwordBox = new LinearLayout(this);
        passwordBox.setOrientation(LinearLayout.HORIZONTAL);
        passwordBox.setGravity(Gravity.CENTER_VERTICAL);
        passwordBox.setPadding(dp(16), 0, dp(8), 0);
        passwordBox.setBackground(roundStroke(FIELD_BG, FIELD_STROKE, dp(19), 1));

        passwordInput = new EditText(this);
        passwordInput.setHint("Masukkan Kata Sandi");
        passwordInput.setTextColor(Color.parseColor(TEXT_MAIN));
        passwordInput.setHintTextColor(Color.parseColor("#64748B"));
        passwordInput.setTextSize(16);
        passwordInput.setSingleLine(true);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        passwordInput.setBackgroundColor(Color.TRANSPARENT);
        passwordBox.addView(passwordInput, new LinearLayout.LayoutParams(0, dp(58), 1));

        TextView toggle = text("👁", 22, "#D1D5DB", false);
        toggle.setGravity(Gravity.CENTER);
        toggle.setOnClickListener(v -> togglePassword(toggle));
        passwordBox.addView(toggle, new LinearLayout.LayoutParams(dp(46), dp(58)));
        card.addView(passwordBox, fieldLp());

        loginButton = new Button(this);
        loginButton.setText("Masuk");
        loginButton.setTextSize(16);
        loginButton.setTypeface(Typeface.DEFAULT_BOLD);
        loginButton.setTextColor(Color.WHITE);
        loginButton.setAllCaps(false);
        loginButton.setBackground(roundGradient(BLUE, ORANGE, dp(20)));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, dp(58));
        btnLp.setMargins(0, dp(6), 0, dp(18));
        card.addView(loginButton, btnLp);

        TextView register = text("Belum punya akun? Daftar", 16, LINK_BLUE, true);
        register.setGravity(Gravity.CENTER);
        register.setPadding(0, dp(8), 0, dp(18));
        register.setOnClickListener(v -> openRegister());
        card.addView(register, new LinearLayout.LayoutParams(-1, -2));

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#263247"));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, dp(1));
        divLp.setMargins(0, 0, 0, dp(16));
        card.addView(divider, divLp);

        LinearLayout versionRow = new LinearLayout(this);
        versionRow.setOrientation(LinearLayout.HORIZONTAL);
        versionRow.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(versionRow, new LinearLayout.LayoutParams(-1, -2));

        TextView version = text("Versi Aplikasi : " + getAppVersion(), 14, "#D1D5DB", false);
        versionRow.addView(version, new LinearLayout.LayoutParams(0, dp(50), 1));

        Button checkUpdate = new Button(this);
        checkUpdate.setText("Cek Pembaharuan");
        checkUpdate.setAllCaps(false);
        checkUpdate.setTextSize(13);
        checkUpdate.setTypeface(Typeface.DEFAULT_BOLD);
        checkUpdate.setTextColor(Color.WHITE);
        checkUpdate.setBackground(round(GREEN, dp(14)));
        checkUpdate.setOnClickListener(v -> showInfo("Pembaruan Aplikasi", "Untuk versi native, pembaruan dilakukan melalui file APK terbaru Transiva."));
        versionRow.addView(checkUpdate, new LinearLayout.LayoutParams(dp(146), dp(44)));

        LinearLayout legal = new LinearLayout(this);
        legal.setGravity(Gravity.CENTER);
        legal.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams legalLp = new LinearLayout.LayoutParams(-1, -2);
        legalLp.setMargins(0, dp(14), 0, 0);
        card.addView(legal, legalLp);

        TextView privacy = legalButton("Kebijakan Privasi");
        privacy.setOnClickListener(v -> openExternal(BASE_URL + "privacy.html"));
        legal.addView(privacy);

        TextView dot = text("  •  ", 15, TEXT_SOFT, false);
        legal.addView(dot);

        TextView terms = legalButton("Syarat & Ketentuan");
        terms.setOnClickListener(v -> openExternal(BASE_URL + "terms.html"));
        legal.addView(terms);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressLp = new FrameLayout.LayoutParams(dp(54), dp(54));
        progressLp.gravity = Gravity.CENTER;
        page.addView(progressBar, progressLp);

        setContentView(page);

        loginButton.setOnClickListener(v -> submitLogin());
        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                submitLogin();
                return true;
            }
            return false;
        });
    }

    private void submitLogin() {
        if (loading) return;

        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        hideMessage();

        if (username.length() == 0 || password.length() == 0) {
            showMessage("Lengkapi Nama Pengguna dan Kata Sandi", false);
            return;
        }

        setLoading(true);

        new Thread(() -> {
            JSONObject result = postLogin(username, password);
            runOnUiThread(() -> handleLoginResult(result));
        }).start();
    }

    private JSONObject postLogin(String username, String password) {
        HttpURLConnection conn = null;

        try {
            JSONObject body = new JSONObject();
            body.put("username", username);
            body.put("password", password);

            URL url = new URL(LOGIN_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
            writer.write(body.toString());
            writer.flush();
            writer.close();
            os.close();

            int code = conn.getResponseCode();
            InputStream stream = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
            String raw = readStream(stream);

            if (raw == null || raw.trim().length() == 0) {
                return error("Response server kosong");
            }

            try {
                JSONObject json = new JSONObject(raw.trim());
                if (!json.has("success")) json.put("success", code >= 200 && code < 300);
                return json;
            } catch (Exception e) {
                return error("Server mengirim response bukan JSON");
            }

        } catch (Exception e) {
            String msg = e.getMessage() == null ? "Gagal terhubung ke server" : e.getMessage();
            return error(msg);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void handleLoginResult(JSONObject result) {
        setLoading(false);

        if (result == null) {
            showMessage("Server error", false);
            return;
        }

        boolean success = result.optBoolean("success", false);
        String message = result.optString("message", success ? "Login berhasil" : "Login gagal");

        if (!success) {
            showMessage(message, false);
            return;
        }

        JSONObject user = result.optJSONObject("user");
        if (user == null) {
            showMessage("Data user tidak ditemukan dari server", false);
            return;
        }

        session.saveUser(user);
        showMessage("Login berhasil", true);

        usernameInput.postDelayed(() -> openDashboard(user.optString("role", "customer")), 500);
    }

    private void openDashboard(String role) {
        Intent intent;

        if ("driver".equals(role)) {
            intent = new Intent(this, DriverDashboardActivity.class);
        } else if ("merchant".equals(role)) {
            intent = new Intent(this, MerchantDashboardActivity.class);
        } else if ("admin".equals(role)) {
            intent = new Intent(this, AdminDashboardActivity.class);
        } else {
            intent = new Intent(this, CustomerDashboardActivity.class);
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void openRegister() {
        try {
            Class<?> registerClass = Class.forName(getPackageName() + ".RegisterActivity");
            startActivity(new Intent(this, registerClass));
        } catch (Exception e) {
            Toast.makeText(this, "Register native belum dibuat. Lanjutkan upgrade RegisterActivity.", Toast.LENGTH_LONG).show();
        }
    }

    private void togglePassword(TextView toggle) {
        passwordVisible = !passwordVisible;

        int pos = passwordInput.getSelectionStart();
        if (passwordVisible) {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            toggle.setText("🙈");
        } else {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            toggle.setText("👁");
        }
        passwordInput.setSelection(Math.max(0, pos));
    }

    private void setLoading(boolean value) {
        loading = value;
        progressBar.setVisibility(value ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!value);
        usernameInput.setEnabled(!value);
        passwordInput.setEnabled(!value);
        loginButton.setText(value ? "Memuat..." : "Masuk");
        loginButton.setAlpha(value ? 0.70f : 1f);
    }

    private JSONObject error(String message) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("success", false);
            obj.put("message", message == null || message.length() == 0 ? "Server error" : message);
        } catch (Exception ignored) {}
        return obj;
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

    private void showMessage(String msg, boolean success) {
        messageText.setText(msg);
        messageText.setTextColor(Color.parseColor(success ? "#D1FAE5" : "#FECACA"));
        messageText.setBackground(roundStroke(success ? "#123826" : "#3A1620", success ? "#22C55E" : "#7F1D1D", dp(14), 1));
        messageText.setVisibility(View.VISIBLE);
    }

    private void hideMessage() {
        messageText.setVisibility(View.GONE);
    }

    private void showInfo(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Tidak bisa membuka halaman", Toast.LENGTH_SHORT).show();
        }
    }

    private TextView label(String value) {
        TextView tv = text(value, 16, TEXT_MAIN, true);
        tv.setPadding(0, dp(8), 0, dp(8));
        return tv;
    }

    private EditText input(String hint, boolean password) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextSize(16);
        et.setTextColor(Color.parseColor(TEXT_MAIN));
        et.setHintTextColor(Color.parseColor("#64748B"));
        et.setSingleLine(true);
        et.setPadding(dp(16), 0, dp(16), 0);
        et.setBackground(roundStroke(FIELD_BG, FIELD_STROKE, dp(19), 1));
        et.setInputType(password ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT);
        return et;
    }

    private LinearLayout.LayoutParams fieldLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58));
        lp.setMargins(0, 0, 0, dp(14));
        return lp;
    }

    private TextView text(String value, int sp, String color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(Color.parseColor(color));
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private TextView legalButton(String value) {
        TextView tv = text(value, 13, LINK_BLUE, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(14), dp(9), dp(14), dp(9));
        tv.setBackground(round("#0B2B48", dp(18)));
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

    private GradientDrawable roundGradientVertical(String start, String end, int radius) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor(start), Color.parseColor(end)}
        );
        gd.setCornerRadius(radius);
        return gd;
    }

    private int getDrawableId(String name) {
        try {
            return getResources().getIdentifier(name, "drawable", getPackageName());
        } catch (Exception e) {
            return 0;
        }
    }

    private String getAppVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0";
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
