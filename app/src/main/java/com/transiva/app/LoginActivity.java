package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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
    private static final String LOGIN_ENDPOINT = "server/login.php";
    private static final String PRIVACY_URL = BASE_URL + "privacy.html";
    private static final String TERMS_URL = BASE_URL + "terms.html";

    private EditText usernameInput;
    private EditText passwordInput;
    private Button loginBtn;
    private Button checkUpdateBtn;
    private ImageButton togglePasswordBtn;
    private TextView loginMessage;
    private TextView currentVersion;
    private TextView toRegister;
    private TextView privacyBtn;
    private TextView termsBtn;
    private TextView normalModeBtn;
    private TextView darkModeBtn;
    private View root;

    private boolean passwordVisible = false;
    private boolean darkMode = false;
    private String originalLoginText = "Masuk";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        bindViews();
        setupView();
        setupClicks();
    }

    private void bindViews() {
        root = findViewById(R.id.loginRoot);
        usernameInput = findViewById(R.id.loginUsername);
        passwordInput = findViewById(R.id.loginPassword);
        loginBtn = findViewById(R.id.loginBtn);
        checkUpdateBtn = findViewById(R.id.checkUpdateBtn);
        togglePasswordBtn = findViewById(R.id.togglePasswordBtn);
        loginMessage = findViewById(R.id.loginMessage);
        currentVersion = findViewById(R.id.currentVersion);
        toRegister = findViewById(R.id.toRegister);
        privacyBtn = findViewById(R.id.privacyBtn);
        termsBtn = findViewById(R.id.termsBtn);
        normalModeBtn = findViewById(R.id.normalModeBtn);
        darkModeBtn = findViewById(R.id.darkModeBtn);
    }

    private void setupView() {
        if (root != null) {
            root.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in));
        }

        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            currentVersion.setText("Versi Aplikasi : " + version);
        } catch (Exception e) {
            currentVersion.setText("Versi Aplikasi : 1.0");
        }

        passwordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });
    }

    private void setupClicks() {
        loginBtn.setOnClickListener(v -> attemptLogin());

        togglePasswordBtn.setOnClickListener(v -> togglePassword());

        checkUpdateBtn.setOnClickListener(v -> showInfo(
                "Pembaruan Aplikasi",
                "Fitur cek pembaharuan native sudah siap. Hubungkan endpoint versi aplikasi jika ingin cek otomatis dari server."
        ));

        toRegister.setOnClickListener(v -> openWeb(BASE_URL + "?route=register"));
        privacyBtn.setOnClickListener(v -> openWeb(PRIVACY_URL));
        termsBtn.setOnClickListener(v -> openWeb(TERMS_URL));

        normalModeBtn.setOnClickListener(v -> applyMode(false));
        darkModeBtn.setOnClickListener(v -> applyMode(true));
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

    private void applyMode(boolean dark) {
        darkMode = dark;

        if (dark) {
            getWindow().getDecorView().setBackgroundColor(Color.parseColor("#071326"));
            normalModeBtn.setTextColor(Color.parseColor("#8FA0BA"));
            darkModeBtn.setTextColor(Color.parseColor("#0B6DFF"));
            darkModeBtn.setBackgroundResource(R.drawable.bg_toggle_active);
            normalModeBtn.setBackgroundColor(Color.TRANSPARENT);
            showInfo("Dark Mode", "Mode gelap aktif untuk halaman login.");
        } else {
            getWindow().getDecorView().setBackgroundColor(Color.WHITE);
            normalModeBtn.setTextColor(Color.parseColor("#0B6DFF"));
            darkModeBtn.setTextColor(Color.parseColor("#6E7D96"));
            normalModeBtn.setBackgroundResource(R.drawable.bg_toggle_active);
            darkModeBtn.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private void attemptLogin() {
        hideMessage();

        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showMessage("Lengkapi Nama Pengguna dan Kata Sandi", false);
            return;
        }

        setLoading(true);

        new Thread(() -> {
            LoginResult result = doLogin(username, password);

            runOnUiThread(() -> {
                setLoading(false);

                if (!result.success) {
                    showMessage(result.message, false);
                    return;
                }

                if (result.user != null) {
                    TransivaSession.saveUser(LoginActivity.this, result.user);
                }

                showMessage("Login berhasil", true);

                root.postDelayed(() -> openRolePage(result.role), 650);
            });
        }).start();
    }

    private LoginResult doLogin(String username, String password) {
        HttpURLConnection conn = null;

        try {
            URL url = new URL(BASE_URL + LOGIN_ENDPOINT);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(25000);
            conn.setReadTimeout(25000);
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
            String response = readStream(stream);

            JSONObject json = new JSONObject(response);
            boolean success = json.optBoolean("success", false);
            String message = json.optString("message", success ? "Berhasil Masuk" : "Login gagal");

            if (!success) {
                return LoginResult.fail(message);
            }

            JSONObject user = json.optJSONObject("user");
            String role = "customer";
            if (user != null) {
                role = user.optString("role", "customer");
            }

            return LoginResult.ok(message, role, user);

        } catch (Exception e) {
            return LoginResult.fail("Server error / koneksi gagal: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
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
        return sb.toString().trim();
    }

    private void openRolePage(String role) {
        if (role == null) role = "customer";

        String className;

        switch (role) {
            case "driver":
                className = "com.transiva.app.DriverActivity";
                break;
            case "merchant":
                className = "com.transiva.app.MerchantActivity";
                break;
            case "wisataowner":
            case "wisata_owner":
                className = "com.transiva.app.WisataOwnerActivity";
                break;
            case "admin":
                className = "com.transiva.app.AdminActivity";
                break;
            default:
                className = "com.transiva.app.MainActivity";
                break;
        }

        try {
            Class<?> target = Class.forName(className);
            Intent intent = new Intent(this, target);
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

    private void openWeb(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            showInfo("Transiva", "Tidak bisa membuka halaman.");
        }
    }

    private void showMessage(String message, boolean success) {
        loginMessage.setVisibility(View.VISIBLE);
        loginMessage.setText(message);
        loginMessage.setTextColor(success ? Color.parseColor("#138A36") : Color.parseColor("#D32F2F"));
    }

    private void hideMessage() {
        loginMessage.setText("");
        loginMessage.setVisibility(View.GONE);
    }

    private void setLoading(boolean loading) {
        loginBtn.setEnabled(!loading);
        usernameInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
        loginBtn.setText(loading ? "Memuat..." : originalLoginText);
    }

    private void showInfo(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
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
