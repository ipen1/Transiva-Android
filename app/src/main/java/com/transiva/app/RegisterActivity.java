package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * RegisterActivity.java - Transiva Native Register
 *
 * Menggantikan RegisterView JS.
 * Tidak memakai WebView.
 */
public class RegisterActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String SEND_OTP_URL = BASE_URL + "server/sendEmailOtp.php";
    private static final String VERIFY_OTP_URL = BASE_URL + "server/verifyEmailOtp.php";
    private static final String REGISTER_URL = BASE_URL + "server/register.php";

    private boolean otpVerified = false;
    private String lastEmail = "";

    private EditText usernameInput;
    private EditText emailInput;
    private EditText otpInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;

    private TextView messageText;

    private Button sendOtpBtn;
    private Button verifyOtpBtn;
    private Button registerBtn;

    private CountDownTimer resendTimer;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_.-]+$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildLayout();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (resendTimer != null) {
            resendTimer.cancel();
            resendTimer = null;
        }
    }

    private void buildLayout() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.parseColor("#020617"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(22), dp(22), dp(22), dp(22));

        scrollView.addView(
                root,
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT
                )
        );

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setBackgroundColor(Color.parseColor("#0F172A"));

        root.addView(
                card,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        TextView title = new TextView(this);
        title.setText("Daftar Transiva");
        title.setTextColor(Color.WHITE);
        title.setTextSize(23);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Daftar dan nikmati semua layanan Transiva");
        subtitle.setTextColor(Color.parseColor("#CBD5E1"));
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(8), 0, dp(16));
        card.addView(subtitle);

        messageText = new TextView(this);
        messageText.setVisibility(View.GONE);
        messageText.setTextSize(14);
        messageText.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.addView(messageText);

        usernameInput = createInput(
                card,
                "Nama Pengguna",
                "Masukan Nama Pengguna",
                InputType.TYPE_CLASS_TEXT,
                30
        );

        emailInput = createInput(
                card,
                "Email",
                "contoh@email.com",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                80
        );

        sendOtpBtn = createSecondaryButton("Kirim Kode OTP");
        card.addView(sendOtpBtn);

        otpInput = createInput(
                card,
                "Kode OTP (Opsional)",
                "Masukan OTP jika sudah masuk",
                InputType.TYPE_CLASS_NUMBER,
                6
        );

        verifyOtpBtn = createSecondaryButton("Verifikasi OTP");
        card.addView(verifyOtpBtn);

        passwordInput = createInput(
                card,
                "Kata Sandi",
                "Masukan Kata Sandi",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                100
        );

        confirmPasswordInput = createInput(
                card,
                "Konfirmasi Kata Sandi",
                "Masukan Kembali Kata Sandi",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                100
        );
        confirmPasswordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);

        registerBtn = createPrimaryButton("Buat Akun");
        card.addView(registerBtn);

        TextView toLogin = new TextView(this);
        toLogin.setText("Sudah Punya Akun? Masuk");
        toLogin.setTextColor(Color.parseColor("#2DD4BF"));
        toLogin.setGravity(Gravity.CENTER);
        toLogin.setTextSize(14);
        toLogin.setPadding(0, dp(16), 0, 0);
        card.addView(toLogin);

        setContentView(scrollView);

        usernameInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(android.text.Editable s) {
                String clean = cleanUsername(s.toString());
                if (!clean.equals(s.toString())) {
                    usernameInput.setText(clean);
                    usernameInput.setSelection(clean.length());
                }
            }
        });

        emailInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(android.text.Editable s) {
                String currentEmail = s.toString().trim().toLowerCase(Locale.US);

                if (!lastEmail.isEmpty() && !currentEmail.equals(lastEmail)) {
                    resetOtpState();
                }
            }
        });

        otpInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(android.text.Editable s) {
                String clean = s.toString().replaceAll("\\D", "");

                if (clean.length() > 6) {
                    clean = clean.substring(0, 6);
                }

                if (!clean.equals(s.toString())) {
                    otpInput.setText(clean);
                    otpInput.setSelection(clean.length());
                }
            }
        });

        sendOtpBtn.setOnClickListener(v -> sendOtp());
        verifyOtpBtn.setOnClickListener(v -> verifyOtp());
        registerBtn.setOnClickListener(v -> registerUser());

        confirmPasswordInput.setOnEditorActionListener((v, actionId, event) -> {
            registerBtn.performClick();
            return true;
        });

        toLogin.setOnClickListener(v -> openLogin());
    }

    private EditText createInput(
            LinearLayout parent,
            String label,
            String hint,
            int inputType,
            int maxLength
    ) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, dp(8));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(Color.parseColor("#E5E7EB"));
        labelView.setTextSize(13);
        labelView.setPadding(0, 0, 0, dp(6));
        box.addView(labelView);

        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(Color.parseColor("#64748B"));
        input.setTextColor(Color.WHITE);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackgroundColor(Color.parseColor("#111827"));

        box.addView(
                input,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                )
        );

        parent.addView(box);
        return input;
    }

    private Button createPrimaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackgroundColor(Color.parseColor("#0F766E"));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        params.setMargins(0, dp(14), 0, 0);
        button.setLayoutParams(params);

        return button;
    }

    private Button createSecondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackgroundColor(Color.parseColor("#334155"));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        params.setMargins(0, dp(6), 0, dp(6));
        button.setLayoutParams(params);

        return button;
    }

    private void sendOtp() {
        clearMessage();

        String email = cleanEmail(emailInput.getText().toString());

        if (email.isEmpty()) {
            showMessage("Email wajib diisi", false);
            return;
        }

        if (!isValidEmail(email)) {
            showMessage("Format email tidak valid", false);
            return;
        }

        otpVerified = false;
        lastEmail = "";

        setButtonLoading(sendOtpBtn, true, "Mengirim OTP...");

        runNetwork(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("email", email);

                JSONObject result = postJson(SEND_OTP_URL, payload);

                mainHandler.post(() -> {
                    setButtonLoading(sendOtpBtn, false, "Kirim Kode OTP");

                    if (!result.optBoolean("success", false)) {
                        showMessage(
                                result.optString("message", "Gagal mengirim OTP"),
                                false
                        );
                        return;
                    }

                    lastEmail = email;

                    showMessage(
                            "Kode OTP berhasil dikirim. Jika OTP tidak masuk, Anda tetap bisa daftar dan menunggu verifikasi admin.",
                            true
                    );

                    startResendCooldown();

                    otpInput.requestFocus();
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    setButtonLoading(sendOtpBtn, false, "Kirim Kode OTP");
                    showMessage(
                            "OTP gagal dikirim. Anda tetap bisa daftar, nanti admin dapat memverifikasi manual.",
                            false
                    );
                });
            }
        });
    }

    private void verifyOtp() {
        clearMessage();

        String email = cleanEmail(emailInput.getText().toString());
        String otp = otpInput.getText().toString().trim();

        if (email.isEmpty() || !isValidEmail(email)) {
            showMessage("Email tidak valid", false);
            return;
        }

        if (!otp.matches("^[0-9]{6}$")) {
            showMessage("OTP harus 6 angka", false);
            return;
        }

        if (lastEmail.isEmpty() || !email.equals(lastEmail)) {
            showMessage("Kirim OTP ke email ini terlebih dahulu", false);
            return;
        }

        setButtonLoading(verifyOtpBtn, true, "Memverifikasi...");

        runNetwork(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("email", email);
                payload.put("otp", otp);

                JSONObject result = postJson(VERIFY_OTP_URL, payload);

                mainHandler.post(() -> {
                    setButtonLoading(verifyOtpBtn, false, "Verifikasi OTP");

                    if (!result.optBoolean("success", false)) {
                        otpVerified = false;
                        showMessage(
                                result.optString("message", "OTP salah"),
                                false
                        );
                        return;
                    }

                    otpVerified = true;
                    lastEmail = email;

                    emailInput.setEnabled(false);
                    otpInput.setEnabled(false);
                    verifyOtpBtn.setEnabled(false);
                    verifyOtpBtn.setText("OTP Terverifikasi");

                    showMessage("Email berhasil diverifikasi", true);

                    passwordInput.requestFocus();
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    setButtonLoading(verifyOtpBtn, false, "Verifikasi OTP");
                    showMessage("Server error saat verifikasi OTP", false);
                });
            }
        });
    }

    private void registerUser() {
        clearMessage();

        String username = cleanUsername(usernameInput.getText().toString());
        String email = cleanEmail(emailInput.getText().toString());
        String password = passwordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();

        if (username.isEmpty()
                || email.isEmpty()
                || password.isEmpty()
                || confirmPassword.isEmpty()) {
            showMessage("Semua field wajib diisi", false);
            return;
        }

        if (username.length() < 3) {
            showMessage("Nama Pengguna minimal 3 karakter", false);
            return;
        }

        if (!USERNAME_PATTERN.matcher(username).matches()) {
            showMessage(
                    "Nama Pengguna hanya boleh huruf, angka, titik, strip, dan underscore",
                    false
            );
            return;
        }

        if (!isValidEmail(email)) {
            showMessage("Format email tidak valid", false);
            return;
        }

        if (password.length() < 5) {
            showMessage("Kata Sandi minimal 5 karakter", false);
            return;
        }

        if (!password.equals(confirmPassword)) {
            showMessage("Konfirmasi Kata Sandi tidak cocok", false);
            return;
        }

        int emailVerified = otpVerified && email.equals(lastEmail) ? 1 : 0;

        setButtonLoading(registerBtn, true, "Membuat Akun...");

        runNetwork(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("username", username);
                payload.put("email", email);
                payload.put("password", password);
                payload.put("role", "customer");
                payload.put("email_verified", emailVerified);

                JSONObject result = postJson(REGISTER_URL, payload);

                mainHandler.post(() -> {
                    setButtonLoading(registerBtn, false, "Buat Akun");

                    if (!result.optBoolean("success", false)) {
                        showMessage(
                                result.optString("message", "Gagal membuat akun"),
                                false
                        );
                        return;
                    }

                    if (emailVerified == 1) {
                        showMessage(
                                "Akun berhasil dibuat dan email sudah terverifikasi",
                                true
                        );
                    } else {
                        showMessage(
                                "Akun berhasil dibuat. Email belum terverifikasi, admin dapat memverifikasi manual.",
                                true
                        );
                    }

                    mainHandler.postDelayed(this::openLogin, 1200);
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    setButtonLoading(registerBtn, false, "Buat Akun");
                    showMessage("Server error", false);
                });
            }
        });
    }

    private JSONObject postJson(String urlText, JSONObject payload) throws Exception {
        HttpURLConnection conn = null;

        try {
            URL url = new URL(urlText);
            conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(os, "UTF-8")
            );

            writer.write(payload.toString());
            writer.flush();
            writer.close();
            os.close();

            int code = conn.getResponseCode();

            InputStream is =
                    code >= 200 && code < 300
                            ? conn.getInputStream()
                            : conn.getErrorStream();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, "UTF-8")
            );

            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            reader.close();

            String body = response.toString().trim();

            if (body.isEmpty()) {
                JSONObject empty = new JSONObject();
                empty.put("success", false);
                empty.put("message", "Server tidak mengirim response");
                return empty;
            }

            return new JSONObject(body);

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void startResendCooldown() {
        if (resendTimer != null) {
            resendTimer.cancel();
        }

        sendOtpBtn.setEnabled(false);

        resendTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long second = millisUntilFinished / 1000;
                sendOtpBtn.setText("Kirim ulang " + second + "d");
            }

            @Override
            public void onFinish() {
                sendOtpBtn.setEnabled(true);
                sendOtpBtn.setText("Kirim Ulang OTP");
            }
        };

        resendTimer.start();
    }

    private void resetOtpState() {
        otpVerified = false;
        lastEmail = "";

        otpInput.setText("");
        otpInput.setEnabled(true);

        verifyOtpBtn.setEnabled(true);
        verifyOtpBtn.setText("Verifikasi OTP");
    }

    private void showMessage(String message, boolean success) {
        messageText.setVisibility(View.VISIBLE);
        messageText.setText(message);
        messageText.setTextColor(
                success
                        ? Color.parseColor("#BBF7D0")
                        : Color.parseColor("#FECACA")
        );
        messageText.setBackgroundColor(
                success
                        ? Color.parseColor("#14532D")
                        : Color.parseColor("#7F1D1D")
        );
    }

    private void clearMessage() {
        messageText.setVisibility(View.GONE);
        messageText.setText("");
    }

    private void setButtonLoading(Button button, boolean loading, String text) {
        button.setEnabled(!loading);
        button.setText(text);
    }

    private boolean isValidEmail(String email) {
        return EMAIL_PATTERN.matcher(email).matches();
    }

    private String cleanUsername(String value) {
        return String.valueOf(value == null ? "" : value)
                .trim()
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.US);
    }

    private String cleanEmail(String value) {
        return String.valueOf(value == null ? "" : value)
                .trim()
                .toLowerCase(Locale.US);
    }

    private void openLogin() {
        Intent intent = new Intent(
                RegisterActivity.this,
                LoginActivity.class
        );
        intent.setFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );
        startActivity(intent);
        finish();
    }

    private void runNetwork(Runnable runnable) {
        new Thread(runnable).start();
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private abstract static class SimpleTextWatcher implements android.text.TextWatcher {
        @Override
        public void beforeTextChanged(
                CharSequence s,
                int start,
                int count,
                int after
        ) {
        }

        @Override
        public void onTextChanged(
                CharSequence s,
                int start,
                int before,
                int count
        ) {
        }
    }
}
