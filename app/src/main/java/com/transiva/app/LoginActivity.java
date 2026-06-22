package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
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

public class LoginActivity extends Activity {

    private static final String LOGIN_URL = "https://transiva.my.id/server/login.php";

    private EditText usernameInput;
    private EditText passwordInput;
    private Button loginBtn;
    private Button webLoginBtn;
    private ProgressBar progressBar;
    private TextView messageText;

    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        try {
            getWindow().setStatusBarColor(Color.parseColor("#06142E"));
            getWindow().setNavigationBarColor(Color.parseColor("#06142E"));
        } catch (Exception ignored) {}

        sessionManager = new SessionManager(this);

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
        root.setPadding(34, 40, 34, 40);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -1));

        TextView logo = new TextView(this);
        logo.setText("TRANSIVA");
        logo.setTextSize(34);
        logo.setTypeface(null, 1);
        logo.setTextColor(Color.WHITE);
        logo.setGravity(Gravity.CENTER);
        logo.setPadding(0, 0, 0, 8);
        root.addView(logo, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("Masuk ke akun kamu");
        sub.setTextSize(16);
        sub.setTextColor(Color.parseColor("#D1D5DB"));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, 30);
        root.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        usernameInput = input("Username / Nomor HP");
        passwordInput = input("Password");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        root.addView(usernameInput);
        root.addView(passwordInput);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar);

        messageText = new TextView(this);
        messageText.setTextColor(Color.parseColor("#FCA5A5"));
        messageText.setTextSize(14);
        messageText.setGravity(Gravity.CENTER);
        messageText.setPadding(0, 14, 0, 14);
        root.addView(messageText, new LinearLayout.LayoutParams(-1, -2));

        loginBtn = button("Masuk");
        loginBtn.setOnClickListener(v -> doLogin());
        root.addView(loginBtn);

        webLoginBtn = button("Masuk lewat WebView");
        webLoginBtn.setOnClickListener(v -> openWebLogin());
        root.addView(webLoginBtn);

        TextView note = new TextView(this);
        note.setText("Native login aktif. Jika endpoint login berbeda, cukup ubah LOGIN_URL di file ini.");
        note.setTextSize(12);
        note.setTextColor(Color.parseColor("#9CA3AF"));
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, 24, 0, 0);
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
        e.setBackgroundColor(Color.WHITE);
        e.setPadding(26, 18, 26, 18);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, 16);
        e.setLayoutParams(lp);

        return e;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(16);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 8, 0, 8);
        b.setLayoutParams(lp);

        return b;
    }

    private void doLogin() {
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (username.isEmpty()) {
            usernameInput.setError("Username wajib diisi");
            return;
        }

        if (password.isEmpty()) {
            passwordInput.setError("Password wajib diisi");
            return;
        }

        setLoading(true, "Memeriksa akun...");

        new Thread(() -> {
            try {
                String response = postLogin(username, password);
                JSONObject json = new JSONObject(response);

                boolean success = json.optBoolean("success", false);

                if (!success) {
                    String msg = json.optString("message", "Login gagal");
                    runOnUiThread(() -> setLoading(false, msg));
                    return;
                }

                JSONObject player = json.optJSONObject("player");

                if (player == null) {
                    player = json.optJSONObject("user");
                }

                if (player == null) {
                    player = new JSONObject();
                    player.put("username", username);
                    player.put("role", json.optString("role", "customer"));
                    player.put("id", json.optString("id", json.optString("user_id", "")));
                }

                if (!player.has("username")) {
                    player.put("username", username);
                }

                sessionManager.saveSession(player.toString());

                runOnUiThread(() -> {
                    Toast.makeText(this, "Login berhasil", Toast.LENGTH_SHORT).show();
                    openNextPage();
                });

            } catch (Exception e) {
                runOnUiThread(() -> setLoading(false, "Gagal login: " + e.getMessage()));
            }
        }).start();
    }

    private String postLogin(String username, String password) throws Exception {
        String body =
                "username=" + enc(username) +
                "&password=" + enc(password) +
                "&app=1" +
                "&native=1";

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

        BufferedReader br;
        int code = c.getResponseCode();

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

        return sb.toString();
    }

    private void setLoading(boolean loading, String msg) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        loginBtn.setEnabled(!loading);
        webLoginBtn.setEnabled(!loading);
        messageText.setText(msg == null ? "" : msg);
    }

    private void openNextPage() {
        String role = "";

        try {
            role = sessionManager.getRole();
        } catch (Exception ignored) {}

        Intent intent;

        if (role.equalsIgnoreCase("customer") || role.equalsIgnoreCase("user") || role.equalsIgnoreCase("pelanggan")) {
            intent = new Intent(this, CustomerDashboardActivity.class);
        } else {
            intent = new Intent(this, MainActivity.class);
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void openWebLogin() {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("url", "https://transiva.my.id/?app=1");
        startActivity(i);
    }

    private String enc(String s) throws Exception {
        return URLEncoder.encode(s == null ? "" : s, "UTF-8");
    }
}
