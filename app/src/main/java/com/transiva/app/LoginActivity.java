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
import android.text.InputType;
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
    private static final String PRIVACY_URL = BASE_URL + "privacy.html";
    private static final String TERMS_URL = BASE_URL + "terms.html";
    private static final int TIMEOUT_MS = 25000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FrameLayout page;
    private ScrollView scroll;
    private LinearLayout root;
    private LinearLayout loginCard;
    private LinearLayout toggleWrap;
    private TextView normalBtn;
    private TextView darkBtn;
    private TextView messageText;
    private EditText usernameInput;
    private EditText passwordInput;
    private ImageButton eyeBtn;
    private Button loginBtn;
    private Button updateBtn;
    private ProgressBar progressBar;

    private boolean darkMode = false;
    private boolean passwordVisible = false;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setStatusBarColor(Color.parseColor("#0B1B30"));
            getWindow().setNavigationBarColor(Color.parseColor("#0B1B30"));
        } catch (Exception ignored) {}
        buildLayout();
        applyMode(false);
    }

    private void buildLayout() {
        page = new FrameLayout(this);
        page.setBackgroundColor(Color.WHITE);

        addBackgroundShapes(page);

        scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(48), dp(24), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        buildModeToggle(root);
        buildHeader(root);
        buildLoginCard(root);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(54), dp(54));
        plp.gravity = Gravity.CENTER;
        page.addView(progressBar, plp);

        setContentView(page);
    }

    private void addBackgroundShapes(FrameLayout parent) {
        TextView blob1 = new TextView(this);
        blob1.setBackground(oval("#EAF4FF"));
        FrameLayout.LayoutParams b1 = new FrameLayout.LayoutParams(dp(360), dp(520));
        b1.gravity = Gravity.TOP | Gravity.LEFT;
        b1.leftMargin = dp(-120);
        b1.topMargin = dp(-40);
        parent.addView(blob1, b1);

        TextView blob2 = new TextView(this);
        blob2.setBackground(oval("#F1F7FF"));
        FrameLayout.LayoutParams b2 = new FrameLayout.LayoutParams(dp(340), dp(420));
        b2.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        b2.rightMargin = dp(-150);
        b2.bottomMargin = dp(-80);
        parent.addView(blob2, b2);
    }

    private void buildModeToggle(LinearLayout parent) {
        toggleWrap = new LinearLayout(this);
        toggleWrap.setOrientation(LinearLayout.HORIZONTAL);
        toggleWrap.setGravity(Gravity.CENTER);
        toggleWrap.setPadding(dp(5), dp(5), dp(5), dp(5));
        toggleWrap.setBackground(roundStroke("#FFFFFF", "#D7E3F4", dp(36), 1));

        normalBtn = toggleText("☀ Normal");
        darkBtn = toggleText("☾ Dark");
        toggleWrap.addView(normalBtn, new LinearLayout.LayoutParams(dp(112), dp(48)));
        toggleWrap.addView(darkBtn, new LinearLayout.LayoutParams(dp(100), dp(48)));

        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.gravity = Gravity.RIGHT;
        tlp.setMargins(0, 0, 0, dp(52));
        parent.addView(toggleWrap, tlp);

        normalBtn.setOnClickListener(v -> applyMode(false));
        darkBtn.setOnClickListener(v -> applyMode(true));
    }

    private TextView toggleText(String value) {
        TextView tv = text(value, 16, "#64748B", true);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    private void buildHeader(LinearLayout parent) {
        ImageView logo = new ImageView(this);
        int logoRes = getDrawableId("transiva_logo");
        if (logoRes == 0) logoRes = getDrawableId("logo_transiva");
        if (logoRes == 0) logoRes = getDrawableId("logo");
        if (logoRes == 0) logoRes = getApplicationInfo().icon;
        logo.setImageResource(logoRes);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(230), dp(92));
        logoLp.setMargins(0, 0, 0, dp(14));
        parent.addView(logo, logoLp);

        TextView title = text("Masuk Transiva", 34, "#113D7C", true);
        title.setGravity(Gravity.CENTER);
        parent.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = text("Masuk untuk mulai pesan atau menerima order", 17, "#667085", false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(dp(6), dp(8), dp(6), dp(30));
        parent.addView(sub, new LinearLayout.LayoutParams(-1, -2));
    }

    private void buildLoginCard(LinearLayout parent) {
        loginCard = new LinearLayout(this);
        loginCard.setOrientation(LinearLayout.VERTICAL);
        loginCard.setPadding(dp(26), dp(30), dp(26), dp(24));
        loginCard.setBackground(round("#FFFFFF", dp(28)));
        loginCard.setElevation(dp(6));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        parent.addView(loginCard, clp);

        messageText = text("", 13, "#D32F2F", true);
        messageText.setVisibility(View.GONE);
        messageText.setPadding(dp(12), dp(10), dp(12), dp(10));
        loginCard.addView(messageText, new LinearLayout.LayoutParams(-1, -2));

        loginCard.addView(label("Nama Pengguna"));
        usernameInput = plainInput("Masukkan Nama Pengguna", InputType.TYPE_CLASS_TEXT);
        loginCard.addView(inputRow("👤", usernameInput, null), fieldLp());

        loginCard.addView(label("Kata Sandi"));
        passwordInput = plainInput("Masukkan Kata Sandi", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        eyeBtn = new ImageButton(this);
        eyeBtn.setBackgroundColor(Color.TRANSPARENT);
        eyeBtn.setImageResource(android.R.drawable.ic_menu_view);
        eyeBtn.setColorFilter(Color.parseColor("#1687F7"));
        loginCard.addView(inputRow("🔒", passwordInput, eyeBtn), fieldLp());

        loginBtn = new Button(this);
        loginBtn.setText("Masuk    →");
        loginBtn.setAllCaps(false);
        loginBtn.setTextSize(20);
        loginBtn.setTypeface(Typeface.DEFAULT_BOLD);
        loginBtn.setTextColor(Color.WHITE);
        loginBtn.setBackground(roundGradient("#006EEB", "#36A1FF", dp(18)));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(64));
        blp.setMargins(0, dp(12), 0, dp(22));
        loginCard.addView(loginBtn, blp);

        TextView toRegister = text("Belum punya akun? Daftar", 16, "#1687F7", true);
        toRegister.setGravity(Gravity.CENTER);
        toRegister.setPadding(0, 0, 0, dp(18));
        loginCard.addView(toRegister, new LinearLayout.LayoutParams(-1, -2));

        buildVersionBox(loginCard);
        buildLegal(loginCard);

        loginBtn.setOnClickListener(v -> attemptLogin());
        eyeBtn.setOnClickListener(v -> togglePassword());
        toRegister.setOnClickListener(v -> openRegister());
        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                attemptLogin();
                return true;
            }
            return false;
        });
    }

    private void buildVersionBox(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        box.setBackground(round("#F1F6FF", dp(18)));

        TextView icon = text("✓", 26, "#FFFFFF", true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(oval("#147CFF"));
        box.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(14), 0, dp(10), 0);
        TextView version = text("Versi Aplikasi : " + getAppVersion(), 15, "#113D7C", true);
        TextView desc = text("Aplikasi selalu diperbarui untuk pengalaman terbaik Anda", 12, "#667085", false);
        texts.addView(version);
        texts.addView(desc);
        box.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));

        updateBtn = new Button(this);
        updateBtn.setText("Cek Pembaharuan");
        updateBtn.setAllCaps(false);
        updateBtn.setTextSize(13);
        updateBtn.setTypeface(Typeface.DEFAULT_BOLD);
        updateBtn.setTextColor(Color.parseColor("#147CFF"));
        updateBtn.setBackground(roundStroke("#FFFFFF", "#8ABBFF", dp(16), 1));
        box.addView(updateBtn, new LinearLayout.LayoutParams(dp(142), dp(48)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(18));
        parent.addView(box, lp);

        updateBtn.setOnClickListener(v -> showInfo("Pembaruan Aplikasi", "Anda menggunakan versi aplikasi " + getAppVersion() + "."));
    }

    private void buildLegal(LinearLayout parent) {
        LinearLayout legal = new LinearLayout(this);
        legal.setGravity(Gravity.CENTER);
        legal.setOrientation(LinearLayout.HORIZONTAL);

        TextView privacy = text("Kebijakan Privasi", 14, "#1687F7", true);
        TextView sep = text("   |   ", 14, "#CBD5E1", false);
        TextView terms = text("Syarat & Ketentuan", 14, "#1687F7", true);
        legal.addView(privacy);
        legal.addView(sep);
        legal.addView(terms);
        parent.addView(legal, new LinearLayout.LayoutParams(-1, -2));

        privacy.setOnClickListener(v -> openUrl(PRIVACY_URL));
        terms.setOnClickListener(v -> openUrl(TERMS_URL));
    }

    private TextView label(String value) {
        TextView tv = text(value, 16, "#113D7C", true);
        tv.setPadding(0, dp(10), 0, dp(8));
        return tv;
    }

    private EditText plainInput(String hint, int inputType) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextSize(16);
        et.setTextColor(Color.parseColor("#102A43"));
        et.setHintTextColor(Color.parseColor("#8A94A6"));
        et.setSingleLine(true);
        et.setInputType(inputType);
        et.setPadding(dp(10), 0, dp(8), 0);
        et.setBackgroundColor(Color.TRANSPARENT);
        return et;
    }

    private LinearLayout inputRow(String leftIcon, EditText input, View rightView) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(12), 0);
        row.setBackground(roundStroke("#FFFFFF", "#D7E3F4", dp(18), 1));

        TextView icon = text(leftIcon, 22, "#1687F7", false);
        icon.setGravity(Gravity.CENTER);
        row.addView(icon, new LinearLayout.LayoutParams(dp(40), -1));
        row.addView(input, new LinearLayout.LayoutParams(0, -1, 1));
        if (rightView != null) row.addView(rightView, new LinearLayout.LayoutParams(dp(46), dp(46)));
        return row;
    }

    private LinearLayout.LayoutParams fieldLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(62));
        lp.setMargins(0, 0, 0, dp(18));
        return lp;
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
                if (result.user != null) saveUser(result.user);
                showMessage("Login berhasil", true);
                mainHandler.postDelayed(() -> openRolePage(result.role), 650);
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
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            JSONObject body = new JSONObject();
            body.put("username", username);
            body.put("password", password);

            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
            writer.write(body.toString());
            writer.flush();
            writer.close();
            os.close();

            int code = conn.getResponseCode();
            InputStream stream = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
            String response = readStream(stream).trim();
            if (response.isEmpty()) return LoginResult.fail("Server tidak mengirim response");

            JSONObject json = new JSONObject(response);
            boolean success = json.optBoolean("success", false);
            String message = json.optString("message", success ? "Login berhasil" : "Login gagal");
            if (!success) return LoginResult.fail(message);

            JSONObject user = json.optJSONObject("user");
            String role = user != null ? user.optString("role", "customer") : "customer";
            return LoginResult.ok(message, role, user);
        } catch (Exception e) {
            return LoginResult.fail("Server error / koneksi gagal");
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

    private void saveUser(JSONObject user) {
        SharedPreferences sp = getSharedPreferences("transiva", MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putBoolean("logged_in", true);
        ed.putString("user_json", user.toString());
        ed.putString("id", user.optString("id", ""));
        ed.putString("username", user.optString("username", ""));
        ed.putString("role", user.optString("role", "customer"));
        ed.putString("email", user.optString("email", ""));
        ed.putString("balance", user.optString("balance", "0"));
        ed.putString("restaurant_id", user.optString("restaurant_id", ""));
        ed.apply();
    }

    private void openRolePage(String role) {
        if (role == null || role.trim().isEmpty()) role = "customer";
        String target;
        switch (role) {
            case "driver": target = "com.transiva.app.DriverDashboardActivity"; break;
            case "merchant": target = "com.transiva.app.MerchantDashboardActivity"; break;
            case "admin": target = "com.transiva.app.AdminDashboardActivity"; break;
            case "wisataowner":
            case "wisata_owner": target = "com.transiva.app.MainActivity"; break;
            default: target = "com.transiva.app.CustomerDashboardActivity"; break;
        }
        try {
            Class<?> cls = Class.forName(target);
            Intent intent = new Intent(this, cls);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("native_role", role);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    private void togglePassword() {
        int pos = passwordInput.getSelectionStart();
        passwordVisible = !passwordVisible;
        passwordInput.setInputType(passwordVisible ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setSelection(Math.max(0, pos));
    }

    private void setLoading(boolean value) {
        loading = value;
        progressBar.setVisibility(value ? View.VISIBLE : View.GONE);
        loginBtn.setEnabled(!value);
        usernameInput.setEnabled(!value);
        passwordInput.setEnabled(!value);
        loginBtn.setText(value ? "Memuat..." : "Masuk    →");
        loginBtn.setAlpha(value ? 0.75f : 1f);
    }

    private void showMessage(String message, boolean success) {
        messageText.setVisibility(View.VISIBLE);
        messageText.setText(message);
        messageText.setTextColor(Color.parseColor(success ? "#138A36" : "#D32F2F"));
        messageText.setBackground(round(success ? "#EAFBF0" : "#FDECEC", dp(12)));
    }

    private void clearMessage() {
        messageText.setText("");
        messageText.setVisibility(View.GONE);
    }

    private void applyMode(boolean dark) {
        darkMode = dark;
        if (normalBtn == null || darkBtn == null) return;
        normalBtn.setTextColor(Color.parseColor(dark ? "#94A3B8" : "#147CFF"));
        darkBtn.setTextColor(Color.parseColor(dark ? "#147CFF" : "#64748B"));
        normalBtn.setBackground(dark ? null : roundStroke("#FFFFFF", "#8ABBFF", dp(28), 1));
        darkBtn.setBackground(dark ? roundStroke("#FFFFFF", "#8ABBFF", dp(28), 1) : null);
        if (page != null) page.setBackgroundColor(Color.parseColor(dark ? "#081423" : "#FFFFFF"));
    }

    private void openRegister() {
        try {
            Intent intent = new Intent(this, RegisterActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            openUrl(BASE_URL + "?route=register");
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            showInfo("Transiva", "Tidak bisa membuka halaman.");
        }
    }

    private void showInfo(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
    }

    private String getAppVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0";
        }
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

    private GradientDrawable oval(String color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(Color.parseColor(color));
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

    private int getDrawableId(String name) {
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
