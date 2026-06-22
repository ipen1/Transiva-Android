package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
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

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public class RegisterActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String SEND_OTP_URL = BASE_URL + "server/sendEmailOtp.php";
    private static final String VERIFY_OTP_URL = BASE_URL + "server/verifyEmailOtp.php";
    private static final String REGISTER_URL = BASE_URL + "server/register.php";
    private static final int TIMEOUT_MS = 25000;

    private boolean otpVerified = false;
    private boolean loading = false;
    private String lastEmail = "";

    private EditText usernameInput;
    private EditText emailInput;
    private EditText otpInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;

    private TextView messageText;
    private TextView otpStatusText;
    private Button sendOtpBtn;
    private Button verifyOtpBtn;
    private Button registerBtn;
    private ProgressBar progressBar;

    private CountDownTimer resendTimer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            getWindow().setStatusBarColor(Color.parseColor("#07142C"));
            getWindow().setNavigationBarColor(Color.parseColor("#06142E"));
        } catch (Exception ignored) {}

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
        FrameLayout page = new FrameLayout(this);
        page.setBackground(roundGradientVertical("#07142C", "#101827", dp(0)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(14), dp(38), dp(14), dp(34));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        ImageView logo = new ImageView(this);
        int logoRes = getDrawableId("transiva_logo");
        if (logoRes == 0) logoRes = getDrawableId("logo_transiva");
        if (logoRes == 0) logoRes = getDrawableId("logo");
        if (logoRes == 0) logoRes = getApplicationInfo().icon;
        logo.setImageResource(logoRes);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(116), dp(64));
        logoLp.setMargins(0, 0, 0, dp(20));
        root.addView(logo, logoLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(24), dp(20), dp(20));
        card.setBackground(roundStroke("#111A2B", "#26324A", dp(28), 1));
        card.setElevation(dp(6));
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));

        TextView title = text("Daftar Transiva", 26, "#F8FAFC", true);
        title.setGravity(Gravity.CENTER);
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = text("Buat akun untuk mulai pesan atau menerima layanan", 15, "#9AA7BB", false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(8), 0, dp(18));
        card.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        messageText = text("", 13, "#FECACA", true);
        messageText.setVisibility(View.GONE);
        messageText.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(-1, -2);
        msgLp.setMargins(0, 0, 0, dp(14));
        card.addView(messageText, msgLp);

        card.addView(label("Nama Pengguna"));
        usernameInput = input("Masukkan Nama Pengguna", InputType.TYPE_CLASS_TEXT, 30);
        usernameInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        card.addView(usernameInput, fieldLp());

        card.addView(label("Email"));
        emailInput = input("contoh@email.com", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, 80);
        emailInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        card.addView(emailInput, fieldLp());

        sendOtpBtn = secondaryButton("Kirim Kode OTP");
        LinearLayout.LayoutParams otpBtnLp = new LinearLayout.LayoutParams(-1, dp(48));
        otpBtnLp.setMargins(0, 0, 0, dp(12));
        card.addView(sendOtpBtn, otpBtnLp);

        card.addView(label("Kode OTP"));
        otpInput = input("Masukkan 6 digit OTP", InputType.TYPE_CLASS_NUMBER, 6);
        otpInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        card.addView(otpInput, fieldLp());

        verifyOtpBtn = secondaryButton("Verifikasi OTP");
        LinearLayout.LayoutParams verifyLp = new LinearLayout.LayoutParams(-1, dp(48));
        verifyLp.setMargins(0, 0, 0, dp(8));
        card.addView(verifyOtpBtn, verifyLp);

        otpStatusText = text("OTP boleh dikosongkan, admin dapat verifikasi manual.", 12, "#7FA6D9", false);
        otpStatusText.setGravity(Gravity.CENTER);
        otpStatusText.setPadding(0, 0, 0, dp(12));
        card.addView(otpStatusText, new LinearLayout.LayoutParams(-1, -2));

        card.addView(label("Kata Sandi"));
        passwordInput = input("Masukkan Kata Sandi", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, 100);
        passwordInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        card.addView(passwordInput, fieldLp());

        card.addView(label("Konfirmasi Kata Sandi"));
        confirmPasswordInput = input("Masukkan Kembali Kata Sandi", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, 100);
        confirmPasswordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        card.addView(confirmPasswordInput, fieldLp());

        registerBtn = primaryButton("Buat Akun");
        LinearLayout.LayoutParams registerLp = new LinearLayout.LayoutParams(-1, dp(56));
        registerLp.setMargins(0, dp(6), 0, dp(16));
        card.addView(registerBtn, registerLp);

        TextView toLogin = text("Sudah punya akun? Masuk", 15, "#58A6FF", true);
        toLogin.setGravity(Gravity.CENTER);
        toLogin.setPadding(0, dp(4), 0, dp(8));
        card.addView(toLogin, new LinearLayout.LayoutParams(-1, -2));

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#26324A"));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, dp(1));
        divLp.setMargins(0, dp(10), 0, dp(14));
        card.addView(divider, divLp);

        LinearLayout legal = new LinearLayout(this);
        legal.setGravity(Gravity.CENTER);
        legal.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(legal, new LinearLayout.LayoutParams(-1, -2));

        TextView privacy = pillText("Kebijakan Privasi");
        legal.addView(privacy);
        TextView dot = text("  •  ", 13, "#9AA7BB", false);
        legal.addView(dot);
        TextView terms = pillText("Syarat & Ketentuan");
        legal.addView(terms);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressLp = new FrameLayout.LayoutParams(dp(54), dp(54));
        progressLp.gravity = Gravity.CENTER;
        page.addView(progressBar, progressLp);

        setContentView(page);

        usernameInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(android.text.Editable s) {
                String clean = cleanUsername(s.toString());
                if (!clean.equals(s.toString())) {
                    usernameInput.setText(clean);
                    usernameInput.setSelection(clean.length());
                }
            }
        });

        emailInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(android.text.Editable s) {
                String currentEmail = cleanEmail(s.toString());
                if (!lastEmail.isEmpty() && !currentEmail.equals(lastEmail)) resetOtpState();
            }
        });

        otpInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(android.text.Editable s) {
                String clean = s.toString().replaceAll("\\D", "");
                if (clean.length() > 6) clean = clean.substring(0, 6);
                if (!clean.equals(s.toString())) {
                    otpInput.setText(clean);
                    otpInput.setSelection(clean.length());
                }
            }
        });

        sendOtpBtn.setOnClickListener(v -> sendOtp());
        verifyOtpBtn.setOnClickListener(v -> verifyOtp());
        registerBtn.setOnClickListener(v -> registerUser());
        toLogin.setOnClickListener(v -> openLogin());

        confirmPasswordInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                registerUser();
                return true;
            }
            return false;
        });
    }

    private void sendOtp() {
        if (loading) return;
        clearMessage();
        String email = cleanEmail(emailInput.getText().toString());
        if (email.isEmpty()) { showMessage("Email wajib diisi", false); return; }
        if (!isValidEmail(email)) { showMessage("Format email tidak valid", false); return; }

        otpVerified = false;
        lastEmail = "";
        setGlobalLoading(true, sendOtpBtn, "Mengirim OTP...");

        runNetwork(() -> {
            JSONObject result;
            try {
                JSONObject payload = new JSONObject();
                payload.put("email", email);
                result = postJson(SEND_OTP_URL, payload);
            } catch (Exception e) {
                result = error("OTP gagal dikirim. Anda tetap bisa daftar, nanti admin dapat memverifikasi manual.");
            }
            JSONObject finalResult = result;
            mainHandler.post(() -> {
                setGlobalLoading(false, sendOtpBtn, "Kirim Kode OTP");
                if (!finalResult.optBoolean("success", false)) {
                    showMessage(finalResult.optString("message", "Gagal mengirim OTP"), false);
                    return;
                }
                lastEmail = email;
                otpStatusText.setText("Kode OTP sudah dikirim ke email.");
                otpStatusText.setTextColor(Color.parseColor("#86EFAC"));
                showMessage("Kode OTP berhasil dikirim", true);
                startResendCooldown();
                otpInput.requestFocus();
            });
        });
    }

    private void verifyOtp() {
        if (loading) return;
        clearMessage();
        String email = cleanEmail(emailInput.getText().toString());
        String otp = otpInput.getText().toString().trim();
        if (email.isEmpty() || !isValidEmail(email)) { showMessage("Email tidak valid", false); return; }
        if (!otp.matches("^[0-9]{6}$")) { showMessage("OTP harus 6 angka", false); return; }
        if (lastEmail.isEmpty() || !email.equals(lastEmail)) { showMessage("Kirim OTP ke email ini terlebih dahulu", false); return; }

        setGlobalLoading(true, verifyOtpBtn, "Memverifikasi...");
        runNetwork(() -> {
            JSONObject result;
            try {
                JSONObject payload = new JSONObject();
                payload.put("email", email);
                payload.put("otp", otp);
                result = postJson(VERIFY_OTP_URL, payload);
            } catch (Exception e) {
                result = error("Server error saat verifikasi OTP");
            }
            JSONObject finalResult = result;
            mainHandler.post(() -> {
                setGlobalLoading(false, verifyOtpBtn, "Verifikasi OTP");
                if (!finalResult.optBoolean("success", false)) {
                    otpVerified = false;
                    showMessage(finalResult.optString("message", "OTP salah"), false);
                    return;
                }
                otpVerified = true;
                lastEmail = email;
                emailInput.setEnabled(false);
                otpInput.setEnabled(false);
                verifyOtpBtn.setEnabled(false);
                verifyOtpBtn.setText("OTP Terverifikasi");
                verifyOtpBtn.setBackground(roundGradient("#22C55E", "#16A34A", dp(18)));
                otpStatusText.setText("Email berhasil diverifikasi.");
                otpStatusText.setTextColor(Color.parseColor("#86EFAC"));
                showMessage("Email berhasil diverifikasi", true);
                passwordInput.requestFocus();
            });
        });
    }

    private void registerUser() {
        if (loading) return;
        clearMessage();
        String username = cleanUsername(usernameInput.getText().toString());
        String email = cleanEmail(emailInput.getText().toString());
        String password = passwordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();

        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) { showMessage("Semua field wajib diisi", false); return; }
        if (username.length() < 3) { showMessage("Nama Pengguna minimal 3 karakter", false); return; }
        if (!USERNAME_PATTERN.matcher(username).matches()) { showMessage("Nama Pengguna hanya boleh huruf, angka, titik, strip, dan underscore", false); return; }
        if (!isValidEmail(email)) { showMessage("Format email tidak valid", false); return; }
        if (password.length() < 5) { showMessage("Kata Sandi minimal 5 karakter", false); return; }
        if (!password.equals(confirmPassword)) { showMessage("Konfirmasi Kata Sandi tidak cocok", false); return; }

        int emailVerified = otpVerified && email.equals(lastEmail) ? 1 : 0;
        setGlobalLoading(true, registerBtn, "Membuat Akun...");

        runNetwork(() -> {
            JSONObject result;
            try {
                JSONObject payload = new JSONObject();
                payload.put("username", username);
                payload.put("email", email);
                payload.put("password", password);
                payload.put("role", "customer");
                payload.put("email_verified", emailVerified);
                result = postJson(REGISTER_URL, payload);
            } catch (Exception e) {
                result = error("Server error");
            }
            JSONObject finalResult = result;
            mainHandler.post(() -> {
                setGlobalLoading(false, registerBtn, "Buat Akun");
                if (!finalResult.optBoolean("success", false)) {
                    showMessage(finalResult.optString("message", "Gagal membuat akun"), false);
                    return;
                }
                showMessage(emailVerified == 1 ? "Akun berhasil dibuat dan email sudah terverifikasi" : "Akun berhasil dibuat. Email belum terverifikasi, admin dapat memverifikasi manual.", true);
                mainHandler.postDelayed(this::openLogin, 1200);
            });
        });
    }

    private JSONObject postJson(String urlText, JSONObject payload) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlText);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
            writer.write(payload.toString());
            writer.flush();
            writer.close();
            os.close();

            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String body = readStream(is).trim();
            if (body.isEmpty()) return error("Server tidak mengirim response");
            JSONObject json = new JSONObject(body);
            if (!json.has("success")) json.put("success", code >= 200 && code < 300);
            return json;
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

    private void startResendCooldown() {
        if (resendTimer != null) resendTimer.cancel();
        sendOtpBtn.setEnabled(false);
        resendTimer = new CountDownTimer(60000, 1000) {
            @Override public void onTick(long millisUntilFinished) { sendOtpBtn.setText("Kirim ulang " + (millisUntilFinished / 1000) + "d"); }
            @Override public void onFinish() { sendOtpBtn.setEnabled(true); sendOtpBtn.setText("Kirim Ulang OTP"); }
        };
        resendTimer.start();
    }

    private void resetOtpState() {
        otpVerified = false;
        lastEmail = "";
        otpInput.setText("");
        otpInput.setEnabled(true);
        emailInput.setEnabled(true);
        verifyOtpBtn.setEnabled(true);
        verifyOtpBtn.setText("Verifikasi OTP");
        verifyOtpBtn.setBackground(roundGradient("#0F6ABF", "#123F6D", dp(18)));
        otpStatusText.setText("OTP boleh dikosongkan, admin dapat verifikasi manual.");
        otpStatusText.setTextColor(Color.parseColor("#7FA6D9"));
    }

    private void setGlobalLoading(boolean value, Button activeButton, String text) {
        loading = value;
        progressBar.setVisibility(value ? View.VISIBLE : View.GONE);
        usernameInput.setEnabled(!value);
        emailInput.setEnabled(!value || otpVerified);
        otpInput.setEnabled(!value && !otpVerified);
        passwordInput.setEnabled(!value);
        confirmPasswordInput.setEnabled(!value);
        sendOtpBtn.setEnabled(!value);
        verifyOtpBtn.setEnabled(!value && !otpVerified);
        registerBtn.setEnabled(!value);
        activeButton.setText(text);
        activeButton.setAlpha(value ? 0.75f : 1f);
        if (!value && activeButton != verifyOtpBtn) activeButton.setAlpha(1f);
    }

    private JSONObject error(String message) {
        JSONObject obj = new JSONObject();
        try { obj.put("success", false); obj.put("message", message == null || message.length() == 0 ? "Server error" : message); } catch (Exception ignored) {}
        return obj;
    }

    private void showMessage(String message, boolean success) {
        messageText.setVisibility(View.VISIBLE);
        messageText.setText(message);
        messageText.setTextColor(Color.parseColor(success ? "#BBF7D0" : "#FECACA"));
        messageText.setBackground(round(success ? "#123B2A" : "#4A1E2A", dp(14)));
    }

    private void clearMessage() {
        messageText.setVisibility(View.GONE);
        messageText.setText("");
    }

    private TextView label(String value) {
        TextView tv = text(value, 14, "#F1F5F9", true);
        tv.setPadding(0, dp(8), 0, dp(7));
        return tv;
    }

    private EditText input(String hint, int inputType, int maxLength) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextSize(16);
        et.setTextColor(Color.parseColor("#F8FAFC"));
        et.setHintTextColor(Color.parseColor("#6D7890"));
        et.setSingleLine(true);
        et.setInputType(inputType);
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        et.setPadding(dp(16), 0, dp(16), 0);
        et.setBackground(roundStroke("#111827", "#26324A", dp(18), 1));
        return et;
    }

    private LinearLayout.LayoutParams fieldLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56));
        lp.setMargins(0, 0, 0, dp(12));
        return lp;
    }

    private Button primaryButton(String value) {
        Button btn = new Button(this);
        btn.setText(value);
        btn.setAllCaps(false);
        btn.setTextSize(16);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(Color.WHITE);
        btn.setBackground(roundGradient("#2F78FF", "#F47B22", dp(18)));
        return btn;
    }

    private Button secondaryButton(String value) {
        Button btn = new Button(this);
        btn.setText(value);
        btn.setAllCaps(false);
        btn.setTextSize(14);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(Color.WHITE);
        btn.setBackground(roundGradient("#0F6ABF", "#123F6D", dp(18)));
        return btn;
    }

    private TextView pillText(String value) {
        TextView tv = text(value, 13, "#168BD2", true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        tv.setBackground(round("#112F4A", dp(22)));
        return tv;
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

    private boolean isValidEmail(String email) { return EMAIL_PATTERN.matcher(email).matches(); }

    private String cleanUsername(String value) {
        return String.valueOf(value == null ? "" : value).trim().replaceAll("\\s+", "").toLowerCase(Locale.US);
    }

    private String cleanEmail(String value) {
        return String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.US);
    }

    private void openLogin() {
        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void runNetwork(Runnable runnable) { new Thread(runnable).start(); }

    private int getDrawableId(String name) {
        try { return getResources().getIdentifier(name, "drawable", getPackageName()); } catch (Exception e) { return 0; }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private abstract static class SimpleTextWatcher implements android.text.TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
