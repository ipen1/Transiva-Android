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

    private boolean darkMode = false;
    private boolean passwordVisible = false;
    private boolean loading = false;

    private SessionManager session;
    private FrameLayout page;
    private LinearLayout root;
    private LinearLayout card;
    private EditText usernameInput;
    private EditText passwordInput;
    private TextView messageText;
    private TextView normalTab;
    private TextView darkTab;
    private Button loginButton;
    private ProgressBar progressBar;

    private final String BLUE = "#1478FF";
    private final String BLUE_DARK = "#053B91";
    private final String ORANGE = "#FF7A00";
    private final String TEXT_BLUE = "#063B86";
    private final String TEXT_SOFT = "#66758E";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);

        if (session.isLoggedIn()) {
            openDashboard(session.getRole());
            return;
        }

        buildLoginView();
    }

    private void buildLoginView() {
        setBars(false);

        page = new FrameLayout(this);
        page.setBackground(pageBg(false));

        addDecoration(page, false);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(26), dp(28), dp(26), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        buildThemeSwitch(root);
        buildLogo(root);
        buildTitle(root);
        buildCard(root);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(54), dp(54));
        plp.gravity = Gravity.CENTER;
        page.addView(progressBar, plp);

        setContentView(page);
    }

    private void buildThemeSwitch(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(4), dp(4), dp(4), dp(4));
        box.setBackground(roundStroke("#F8FBFF", "#CBD8EA", dp(28), 1));
        box.setElevation(dp(4));

        normalTab = switchTab("☀ Normal", true);
        darkTab = switchTab("☾ Dark", false);
        box.addView(normalTab, new LinearLayout.LayoutParams(dp(116), dp(44)));
        box.addView(darkTab, new LinearLayout.LayoutParams(dp(92), dp(44)));

        normalTab.setOnClickListener(v -> applyTheme(false));
        darkTab.setOnClickListener(v -> applyTheme(true));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.gravity = Gravity.RIGHT;
        lp.setMargins(0, 0, 0, dp(54));
        parent.addView(box, lp);
    }

    private TextView switchTab(String text, boolean active) {
        TextView tv = text(text, 15, active ? BLUE : "#728095", true);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(active ? roundStroke("#FFFFFF", "#9DC7FF", dp(23), 1) : round("#00000000", dp(23)));
        return tv;
    }

    private void buildLogo(LinearLayout parent) {
        ImageView logo = new ImageView(this);
        int logoRes = getDrawableId("transiva_logo");
        if (logoRes == 0) logoRes = getDrawableId("logo_transiva");
        if (logoRes == 0) logoRes = getDrawableId("logo");
        if (logoRes == 0) logoRes = getApplicationInfo().icon;
        logo.setImageResource(logoRes);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(250), dp(95));
        lp.setMargins(0, 0, 0, dp(26));
        parent.addView(logo, lp);
    }

    private void buildTitle(LinearLayout parent) {
        TextView title = text("Masuk Transiva", 31, TEXT_BLUE, true);
        title.setGravity(Gravity.CENTER);
        parent.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = text("Masuk untuk mulai pesan atau menerima order", 17, TEXT_SOFT, false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(34));
        parent.addView(sub, lp);
    }

    private void buildCard(LinearLayout parent) {
        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24), dp(28), dp(24), dp(22));
        card.setBackground(round("#FFFFFF", dp(24)));
        card.setElevation(dp(10));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        parent.addView(card, cardLp);

        messageText = text("", 14, "#B91C1C", true);
        messageText.setVisibility(View.GONE);
        messageText.setPadding(dp(14), dp(12), dp(14), dp(12));
        messageText.setBackground(roundStroke("#FFF1F2", "#FCA5A5", dp(14), 1));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.setMargins(0, 0, 0, dp(14));
        card.addView(messageText, mlp);

        card.addView(label("Nama Pengguna"));
        usernameInput = input("Masukkan Nama Pengguna", "👤", false);
        usernameInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        card.addView(usernameInput, fieldLp());

        card.addView(label("Kata Sandi"));
        LinearLayout passwordBox = passwordInputBox();
        card.addView(passwordBox, fieldLp());

        loginButton = new Button(this);
        loginButton.setText("Masuk        →");
        loginButton.setTextSize(18);
        loginButton.setTypeface(Typeface.DEFAULT_BOLD);
        loginButton.setTextColor(Color.WHITE);
        loginButton.setAllCaps(false);
        loginButton.setBackground(roundGradient(BLUE_DARK, "#3296FF", dp(18)));
        loginButton.setElevation(dp(7));
        loginButton.setOnClickListener(v -> submitLogin());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(62));
        blp.setMargins(0, dp(8), 0, dp(22));
        card.addView(loginButton, blp);

        buildRegisterLine(card);
        buildVersionBox(card);
        buildLegal(card);

        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                submitLogin();
                return true;
            }
            return false;
        });
    }

    private TextView label(String value) {
        TextView tv = text(value, 16, TEXT_BLUE, true);
        tv.setPadding(0, dp(6), 0, dp(10));
        return tv;
    }

    private EditText input(String hint, String icon, boolean password) {
        EditText et = new EditText(this);
        et.setHint(icon + "     " + hint);
        et.setTextSize(16);
        et.setTextColor(Color.parseColor("#0F2A4F"));
        et.setHintTextColor(Color.parseColor("#7B879A"));
        et.setSingleLine(true);
        et.setPadding(dp(18), 0, dp(18), 0);
        et.setBackground(roundStroke("#FFFFFF", "#D9DFEA", dp(17), 1));
        et.setInputType(password ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT);
        return et;
    }

    private LinearLayout passwordInputBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(18), 0, dp(8), 0);
        box.setBackground(roundStroke("#FFFFFF", "#D9DFEA", dp(17), 1));

        TextView lock = text("🔒", 22, BLUE, false);
        lock.setGravity(Gravity.CENTER);
        box.addView(lock, new LinearLayout.LayoutParams(dp(42), dp(58)));

        passwordInput = new EditText(this);
        passwordInput.setHint("Masukkan Kata Sandi");
        passwordInput.setTextColor(Color.parseColor("#0F2A4F"));
        passwordInput.setHintTextColor(Color.parseColor("#7B879A"));
        passwordInput.setTextSize(16);
        passwordInput.setSingleLine(true);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        passwordInput.setBackgroundColor(Color.TRANSPARENT);
        box.addView(passwordInput, new LinearLayout.LayoutParams(0, dp(58), 1));

        TextView eye = text("●", 24, BLUE, true);
        eye.setGravity(Gravity.CENTER);
        eye.setOnClickListener(v -> togglePassword(eye));
        box.addView(eye, new LinearLayout.LayoutParams(dp(48), dp(58)));
        return box;
    }

    private LinearLayout.LayoutParams fieldLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58));
        lp.setMargins(0, 0, 0, dp(18));
        return lp;
    }

    private void buildRegisterLine(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(line(), new LinearLayout.LayoutParams(0, dp(1), 1));
        TextView reg = text("  Belum punya akun? ", 15, TEXT_SOFT, false);
        row.addView(reg);
        TextView daftar = text("Daftar  ", 15, BLUE, true);
        daftar.setOnClickListener(v -> openRegister());
        row.addView(daftar);
        row.addView(line(), new LinearLayout.LayoutParams(0, dp(1), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(20));
        parent.addView(row, lp);
    }

    private View line() {
        View v = new View(this);
        v.setBackgroundColor(Color.parseColor("#DDE5F0"));
        return v;
    }

    private void buildVersionBox(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(16), dp(14), dp(14), dp(14));
        box.setBackground(round("#F1F7FF", dp(14)));

        TextView shield = text("✓", 26, "#FFFFFF", true);
        shield.setGravity(Gravity.CENTER);
        shield.setBackground(round(BLUE, dp(28)));
        box.addView(shield, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(14), 0, dp(8), 0);
        texts.addView(text("Versi Aplikasi : " + getAppVersion(), 14, BLUE_DARK, true));
        TextView desc = text("Aplikasi selalu diperbarui untuk\npengalaman terbaik Anda", 13, TEXT_SOFT, false);
        desc.setLineSpacing(dp(3), 1f);
        texts.addView(desc);
        box.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));

        TextView update = text("Cek Pembaharuan", 13, BLUE, true);
        update.setGravity(Gravity.CENTER);
        update.setBackground(roundStroke("#FFFFFF", "#7DB5FF", dp(14), 1));
        update.setOnClickListener(v -> showInfo("Pembaruan Aplikasi", "Untuk versi native, gunakan APK Transiva terbaru."));
        box.addView(update, new LinearLayout.LayoutParams(dp(128), dp(48)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(20));
        parent.addView(box, lp);
    }

    private void buildLegal(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView privacy = text("▱  Kebijakan Privasi", 14, BLUE, true);
        privacy.setOnClickListener(v -> openExternal(BASE_URL + "privacy.html"));
        TextView mid = text("     |     ", 14, "#CBD5E1", false);
        TextView terms = text("▤  Syarat & Ketentuan", 14, BLUE, true);
        terms.setOnClickListener(v -> openExternal(BASE_URL + "terms.html"));
        row.addView(privacy);
        row.addView(mid);
        row.addView(terms);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addDecoration(FrameLayout parent, boolean dark) {
        View top = new View(this);
        top.setBackground(roundGradient("#E9F4FF", "#FFFFFF00", dp(220)));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(dp(300), dp(230));
        tlp.gravity = Gravity.LEFT | Gravity.TOP;
        tlp.setMargins(dp(-80), dp(42), 0, 0);
        parent.addView(top, tlp);

        View bottom = new View(this);
        bottom.setBackground(roundGradient("#D8EAFF", "#FFFFFF00", dp(230)));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(320), dp(210));
        blp.gravity = Gravity.LEFT | Gravity.BOTTOM;
        blp.setMargins(dp(-110), 0, 0, dp(-60));
        parent.addView(bottom, blp);
    }

    private void applyTheme(boolean dark) {
        darkMode = dark;
        setBars(dark);
        page.setBackground(pageBg(dark));
        normalTab.setTextColor(Color.parseColor(dark ? "#93A4BA" : BLUE));
        darkTab.setTextColor(Color.parseColor(dark ? "#FFFFFF" : "#728095"));
        normalTab.setBackground(dark ? round("#00000000", dp(23)) : roundStroke("#FFFFFF", "#9DC7FF", dp(23), 1));
        darkTab.setBackground(dark ? roundStroke("#0F2B55", "#3B82F6", dp(23), 1) : round("#00000000", dp(23)));
    }

    private void setBars(boolean dark) {
        try {
            getWindow().setStatusBarColor(Color.parseColor(dark ? "#06142A" : "#0A7BFF"));
            getWindow().setNavigationBarColor(Color.parseColor(dark ? "#06142A" : "#FFFFFF"));
        } catch (Exception ignored) {}
    }

    private GradientDrawable pageBg(boolean dark) {
        return roundGradientVertical(dark ? "#06142A" : "#F8FCFF", dark ? "#0B1F3D" : "#EAF4FF", 0);
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
            if (raw == null || raw.trim().length() == 0) return error("Response server kosong");

            JSONObject json = new JSONObject(raw.trim());
            if (!json.has("success")) json.put("success", code >= 200 && code < 300);
            return json;
        } catch (Exception e) {
            return error(e.getMessage() == null ? "Gagal terhubung ke server" : e.getMessage());
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
        if ("driver".equals(role)) intent = new Intent(this, DriverDashboardActivity.class);
        else if ("merchant".equals(role)) intent = new Intent(this, MerchantDashboardActivity.class);
        else if ("admin".equals(role)) intent = new Intent(this, AdminDashboardActivity.class);
        else intent = new Intent(this, CustomerDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void openRegister() {
        try {
            Class<?> registerClass = Class.forName(getPackageName() + ".RegisterActivity");
            startActivity(new Intent(this, registerClass));
        } catch (Exception e) {
            Toast.makeText(this, "Register native belum dibuat", Toast.LENGTH_LONG).show();
        }
    }

    private void togglePassword(TextView toggle) {
        passwordVisible = !passwordVisible;
        int pos = passwordInput.getSelectionStart();
        if (passwordVisible) {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            toggle.setText("○");
        } else {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            toggle.setText("●");
        }
        passwordInput.setSelection(Math.max(0, pos));
    }

    private void setLoading(boolean value) {
        loading = value;
        progressBar.setVisibility(value ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!value);
        usernameInput.setEnabled(!value);
        passwordInput.setEnabled(!value);
        loginButton.setText(value ? "Memuat..." : "Masuk        →");
        loginButton.setAlpha(value ? 0.72f : 1f);
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
        messageText.setTextColor(Color.parseColor(success ? "#047857" : "#B91C1C"));
        messageText.setBackground(roundStroke(success ? "#ECFDF5" : "#FFF1F2", success ? "#86EFAC" : "#FCA5A5", dp(14), 1));
        messageText.setVisibility(View.VISIBLE);
    }

    private void hideMessage() {
        messageText.setVisibility(View.GONE);
    }

    private void showInfo(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Tidak bisa membuka halaman", Toast.LENGTH_SHORT).show();
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

    private GradientDrawable roundGradientVertical(String start, String end, int radius) {
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.parseColor(start), Color.parseColor(end)});
        gd.setCornerRadius(radius);
        return gd;
    }

    private int getDrawableId(String name) {
        try { return getResources().getIdentifier(name, "drawable", getPackageName()); }
        catch (Exception e) { return 0; }
    }

    private String getAppVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "1.0"; }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
