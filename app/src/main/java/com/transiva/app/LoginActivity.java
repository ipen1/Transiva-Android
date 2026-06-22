package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class LoginActivity extends Activity {

    private static final String LOGIN_URL = "https://transiva.my.id/server/login.php";
    private static final String WEB_HOME_URL = "https://transiva.my.id/?app=1";

    private static final String PREF_NAME = "transiva_login";
    private static final String PREF_LAST_USERNAME = "last_username";

    private EditText usernameInput;
    private EditText passwordInput;
    private CheckBox showPasswordBox;
    private Button loginBtn;
    private Button webBtn;
    private ProgressBar progressBar;
    private TextView messageText;

    private SessionManager sessionManager;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            getWindow().setStatusBarColor(Color.parseColor("#06142E"));
            getWindow().setNavigationBarColor(Color.parseColor("#06142E"));
        } catch (Exception ignored) {}

        sessionManager = new SessionManager(this);
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        try {
            if (sessionManager.isLoggedIn()) {
                openDashboardByRole();
                return;
            }
        } catch (Exception ignored) {}

        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor("#06142E"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(26), dp(36), dp(26), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -1));

        TextView logo = new TextView(this);
        logo.setText("TRANSIVA");
        logo.setTextSize(34);
        logo.setTypeface(null, 1);
        logo.setTextColor(Color.WHITE);
        logo.setGravity(Gravity.CENTER);
        root.addView(logo, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("Login Native langsung ke API");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.parseColor("#D1D5DB"));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(6), 0, dp(24));
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(20), dp(22), dp(20), dp(18));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, 0, 0, dp(18));
        root.addView(card, cardLp);

        usernameInput = input("Username");
        usernameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        usernameInput.setText(prefs.getString(PREF_LAST_USERNAME, ""));
        card.addView(usernameInput);

        passwordInput = input("Password");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                doNativeLogin();
                return true;
            }
            return false;
        });
        card.addView(passwordInput);

        showPasswordBox = new CheckBox(this);
        showPasswordBox.setText("Tampilkan password");
        showPasswordBox.setTextSize(14);
        showPasswordBox.setTextColor(Color.parseColor("#374151"));
        showPasswordBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int pos = passwordInput.getSelectionStart();
            if (isChecked) {
                passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            } else {
                passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            passwordInput.setSelection(Math.min(Math.max(pos, 0), passwordInput.getText().length()));
        });
        card.addView(showPasswordBox);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        card.addView(progressBar);

        messageText = new TextView(this);
        messageText.setText("");
        messageText.setTextSize(14);
        messageText.setTextColor(Color.parseColor("#DC2626"));
        messageText.setGravity(Gravity.CENTER);
        messageText.setPadding(0, dp(10), 0, dp(8));
        card.addView(messageText, new LinearLayout.LayoutParams(-1, -2));

        loginBtn = button("Masuk");
        loginBtn.setOnClickListener(v -> doNativeLogin());
        card.addView(loginBtn);

        webBtn = button("Buka WebView");
        webBtn.setOnClickListener(v -> openWebFallback());
        card.addView(webBtn);

        TextView note = new TextView(this);
        note.setText("Login ini mengirim JSON langsung ke server/login.php. WebView tidak dipakai untuk login.");
        note.setTextSize(12);
        note.setTextColor(Color.parseColor("#9CA3AF"));
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(8), 0, dp(8), 0);
        root.addView(note, new LinearLayout.LayoutParams(-1, -2));

        setContentView(scroll);
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(16);
        e.setTextColor(Color.parseColor("#111827"));
        e.setHintTextColor(Color.parseColor("#6B7280"));
        e.setBackgroundColor(Color.parseColor("#F3F4F6"));
        e.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(14));
        e.setLayoutParams(lp);

        return e;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(7), 0, dp(7));
        b.setLayoutParams(lp);

        return b;
    }

    private void doNativeLogin() {
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (username.isEmpty()) {
            usernameInput.setError("Username wajib diisi");
            usernameInput.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            passwordInput.setError("Password wajib diisi");
            passwordInput.requestFocus();
            return;
        }

        setLoading(true, "Memeriksa akun...");

        new Thread(() -> {
            try {
                String response = postJsonLogin(username, password);
                JSONObject root = new JSONObject(response);

                boolean success = root.optBoolean("success", false);
                String message = root.optString("message", success ? "Berhasil Masuk" : "Login gagal");

                if (!success) {
                    runOnUiThread(() -> setLoading(false, message));
                    return;
                }

                JSONObject user = root.optJSONObject("user");
                if (user == null) {
                    runOnUiThread(() -> setLoading(false, "Response login tidak lengkap"));
                    return;
                }

                JSONObject sessionUser = normalizeUser(user, username);

                try {
                    if (sessionManager == null) {
                        sessionManager = new SessionManager(this);
                    }
                    sessionManager.clearSession();
                    sessionManager.saveSession(sessionUser.toString());
                } catch (Exception e) {
                    runOnUiThread(() -> setLoading(false, "Gagal menyimpan session"));
                    return;
                }

                prefs.edit()
                        .putString(PREF_LAST_USERNAME, username)
                        .apply();

                runOnUiThread(() -> {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    openDashboardByRole();
                });

            } catch (Exception e) {
                runOnUiThread(() -> setLoading(false, "Gagal login: " + cleanError(e.getMessage())));
            }
        }).start();
    }

    private String postJsonLogin(String username, String password) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("username", username);
        payload.put("password", password);

        byte[] body = payload.toString().getBytes("UTF-8");

        HttpURLConnection conn = (HttpURLConnection) new URL(LOGIN_URL).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod("POST");
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        conn.setRequestProperty("User-Agent", "TransivaAndroidNative/1.0");
        conn.setRequestProperty("Content-Length", String.valueOf(body.length));

        OutputStream os = conn.getOutputStream();
        os.write(body);
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300
                ? conn.getInputStream()
                : conn.getErrorStream();

        String result = readStream(stream).trim();
        conn.disconnect();

        if (result.isEmpty()) {
            throw new Exception("Server tidak mengirim response");
        }

        return result;
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";

        BufferedReader br = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null) {
            sb.append(line);
        }

        br.close();
        return sb.toString();
    }

    private JSONObject normalizeUser(JSONObject user, String fallbackUsername) throws Exception {
        JSONObject out = new JSONObject(user.toString());

        String id = firstNonEmpty(
                out.optString("id", ""),
                out.optString("user_id", "")
        );

        String username = firstNonEmpty(
                out.optString("username", ""),
                out.optString("name", ""),
                fallbackUsername
        );

        String role = normalizeRole(firstNonEmpty(
                out.optString("role", ""),
                "customer"
        ));

        String driverType = firstNonEmpty(
                out.optString("driver_type", ""),
                "bike"
        ).toLowerCase(Locale.US);

        if (!driverType.equals("car")) {
            driverType = "bike";
        }

        out.put("id", id);
        out.put("user_id", id);
        out.put("username", username);
        out.put("role", role);
        out.put("driver_type", driverType);

        if (!out.has("balance")) out.put("balance", 0);
        if (!out.has("driver_level")) out.put("driver_level", "bronze");
        if (!out.has("photo")) out.put("photo", out.optString("driver_photo", "assets/default-driver.png"));
        if (!out.has("driver_photo")) out.put("driver_photo", out.optString("photo", "assets/default-driver.png"));

        return out;
    }

    private void openDashboardByRole() {
        String role = "customer";

        try {
            if (sessionManager == null) {
                sessionManager = new SessionManager(this);
            }
            role = normalizeRole(sessionManager.getRole());
        } catch (Exception ignored) {}

        Intent intent;

        if (role.equals("customer")) {
            intent = new Intent(this, CustomerDashboardActivity.class);
        } else if (role.equals("driver")) {
            intent = new Intent(this, DriverDashboardActivity.class);
        } else if (role.equals("merchant")) {
            intent = new Intent(this, MerchantDashboardActivity.class);
        } else if (role.equals("admin")) {
            intent = new Intent(this, AdminDashboardActivity.class);
        } else {
            intent = new Intent(this, MainActivity.class);
            intent.putExtra("url", WEB_HOME_URL);
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void openWebFallback() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("url", WEB_HOME_URL);
        startActivity(intent);
    }

    private String normalizeRole(String role) {
        if (role == null) return "customer";

        String r = role.trim().toLowerCase(Locale.US);

        if (r.equals("user") || r.equals("pelanggan") || r.equals("costumer") || r.equals("customer")) {
            return "customer";
        }

        if (r.equals("driver") || r.equals("kurir") || r.equals("ojek") || r.equals("rider")) {
            return "driver";
        }

        if (r.equals("merchant") || r.equals("merchen") || r.equals("resto") || r.equals("restaurant") || r.equals("penjual")) {
            return "merchant";
        }

        if (r.equals("admin") || r.equals("administrator") || r.equals("owner") || r.equals("superadmin")) {
            return "admin";
        }

        if (r.equals("wisata") || r.equals("wisataowner") || r.equals("wisata_owner")) {
            return "wisata";
        }

        return r.isEmpty() ? "customer" : r;
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";

        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !value.equals("null")) {
                return value.trim();
            }
        }

        return "";
    }

    private void setLoading(boolean loading, String message) {
        try {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            loginBtn.setEnabled(!loading);
            webBtn.setEnabled(!loading);
            usernameInput.setEnabled(!loading);
            passwordInput.setEnabled(!loading);
            showPasswordBox.setEnabled(!loading);
            messageText.setText(message == null ? "" : message);
        } catch (Exception ignored) {}
    }

    private String cleanError(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Koneksi/server bermasalah";
        }

        return message
                .replace("java.lang.", "")
                .replace("org.json.", "")
                .replace("Value ", "Data ");
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
