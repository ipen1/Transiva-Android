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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

public class LoginActivity extends Activity {

    private static final String LOGIN_URL = "https://transiva.my.id/server/login.php";
    private static final String WEB_LOGIN_URL = "https://transiva.my.id/?app=1";
    private static final String PREF_NAME = "transiva_login";
    private static final String PREF_LAST_USERNAME = "last_username";

    private EditText usernameInput;
    private EditText passwordInput;
    private CheckBox showPasswordBox;
    private Button loginBtn;
    private Button webLoginBtn;
    private ProgressBar progressBar;
    private TextView messageText;

    private SessionManager sessionManager;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        try {
            getWindow().setStatusBarColor(Color.parseColor("#06142E"));
            getWindow().setNavigationBarColor(Color.parseColor("#06142E"));
        } catch (Exception ignored) {}

        sessionManager = new SessionManager(this);
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        if (sessionManager.isLoggedIn()) {
            openNextPage();
            return;
        }

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

        TextView sub = new TextView(this);
        sub.setText("Masuk ke akun kamu");
        sub.setTextSize(16);
        sub.setTextColor(Color.parseColor("#D1D5DB"));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(26));
        root.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(20), dp(22), dp(20), dp(18));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, 0, 0, dp(18));
        root.addView(card, cardLp);

        usernameInput = input("Username / Nomor HP");
        usernameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        usernameInput.setText(prefs.getString(PREF_LAST_USERNAME, ""));
        card.addView(usernameInput);

        passwordInput = input("Password");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                doLogin();
                return true;
            }
            return false;
        });
        card.addView(passwordInput);

        showPasswordBox = new CheckBox(this);
        showPasswordBox.setText("Tampilkan password");
        showPasswordBox.setTextColor(Color.parseColor("#374151"));
        showPasswordBox.setTextSize(14);
        showPasswordBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int pos = passwordInput.getSelectionStart();
            if (isChecked) {
                passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            } else {
                passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            passwordInput.setSelection(Math.max(0, passwordInput.getText().length() < pos ? passwordInput.getText().length() : pos));
        });
        card.addView(showPasswordBox);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        card.addView(progressBar);

        messageText = new TextView(this);
        messageText.setTextColor(Color.parseColor("#DC2626"));
        messageText.setTextSize(14);
        messageText.setGravity(Gravity.CENTER);
        messageText.setPadding(0, dp(12), 0, dp(8));
        card.addView(messageText, new LinearLayout.LayoutParams(-1, -2));

        loginBtn = button("Masuk Native");
        loginBtn.setOnClickListener(v -> doLogin());
        card.addView(loginBtn);

        webLoginBtn = button("Masuk lewat WebView");
        webLoginBtn.setOnClickListener(v -> openWebLogin());
        card.addView(webLoginBtn);

        TextView note = new TextView(this);
        note.setText("Login native aktif. Setelah berhasil, aplikasi otomatis membuka dashboard sesuai role akun.");
        note.setTextSize(12);
        note.setTextColor(Color.parseColor("#9CA3AF"));
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(10), 0, dp(10), 0);
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

    private void doLogin() {
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
                String response = postLogin(username, password);
                JSONObject json = new JSONObject(response);

                boolean success = json.optBoolean("success", false);

                if (!success) {
                    String msg = json.optString("message", "Login gagal. Periksa username dan password.");
                    runOnUiThread(() -> setLoading(false, msg));
                    return;
                }

                JSONObject player = extractUserObject(json, username);

                try {
                    if (sessionManager == null) {
                        sessionManager = new SessionManager(this);
                    }
                    sessionManager.clearSession();
                } catch (Exception ignored) {}

                sessionManager.saveSession(player.toString());

                prefs.edit()
                        .putString(PREF_LAST_USERNAME, username)
                        .apply();

                runOnUiThread(() -> {
                    Toast.makeText(this, "Login berhasil", Toast.LENGTH_SHORT).show();
                    openNextPage();
                });

            } catch (Exception e) {
                runOnUiThread(() -> setLoading(false, "Gagal login: " + cleanError(e.getMessage())));
            }
        }).start();
    }

    private JSONObject extractUserObject(JSONObject json, String fallbackUsername) throws Exception {
        JSONObject player = json.optJSONObject("player");

        if (player == null) {
            player = json.optJSONObject("user");
        }

        if (player == null) {
            player = json.optJSONObject("data");
        }

        if (player == null) {
            player = new JSONObject();
        }

        String username = firstNonEmpty(
                player.optString("username", ""),
                player.optString("name", ""),
                json.optString("username", ""),
                json.optString("name", ""),
                fallbackUsername
        );

        String role = firstNonEmpty(
                player.optString("role", ""),
                player.optString("user_role", ""),
                json.optString("role", ""),
                json.optString("user_role", ""),
                "customer"
        );

        String id = firstNonEmpty(
                player.optString("id", ""),
                player.optString("user_id", ""),
                json.optString("id", ""),
                json.optString("user_id", "")
        );

        player.put("username", username);
        player.put("role", normalizeRole(role));

        if (!id.isEmpty()) {
            player.put("id", id);
            player.put("user_id", id);
        }

        return player;
    }

    private String postLogin(String username, String password) throws Exception {
        String body =
                "username=" + enc(username) +
                "&password=" + enc(password) +
                "&app=1" +
                "&native=1" +
                "&device=android";

        HttpURLConnection c = (HttpURLConnection) new URL(LOGIN_URL).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        c.setRequestProperty("Accept", "application/json");

        OutputStream os = c.getOutputStream();
        os.write(body.getBytes("UTF-8"));
        os.flush();
        os.close();

        int code = c.getResponseCode();
        BufferedReader br;

        if (code >= 200 && code < 300) {
            br = new BufferedReader(new InputStreamReader(c.getInputStream()));
        } else {
            br = new BufferedReader(new InputStreamReader(c.getErrorStream()));
        }

        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null) {
            sb.append(line);
        }

        br.close();
        c.disconnect();

        String result = sb.toString().trim();

        if (result.isEmpty()) {
            throw new Exception("Server kosong");
        }

        return result;
    }

    private void openNextPage() {
        String role = "";

        try {
            if (sessionManager == null) {
                sessionManager = new SessionManager(this);
            }
            role = sessionManager.getRole();
        } catch (Exception ignored) {}

        role = normalizeRole(role);

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
            intent.putExtra("url", WEB_LOGIN_URL);
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void openWebLogin() {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("url", WEB_LOGIN_URL);
        startActivity(i);
    }

    private void setLoading(boolean loading, String msg) {
        try {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            loginBtn.setEnabled(!loading);
            webLoginBtn.setEnabled(!loading);
            usernameInput.setEnabled(!loading);
            passwordInput.setEnabled(!loading);
            showPasswordBox.setEnabled(!loading);
            messageText.setText(msg == null ? "" : msg);
        } catch (Exception ignored) {}
    }

    private String normalizeRole(String role) {
        if (role == null) return "";

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

        return r;
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";

        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }

        return "";
    }

    private String cleanError(String msg) {
        if (msg == null || msg.trim().isEmpty()) {
            return "Koneksi/server bermasalah";
        }
        return msg.replace("java.lang.", "");
    }

    private String enc(String s) throws Exception {
        return URLEncoder.encode(s == null ? "" : s, "UTF-8");
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
