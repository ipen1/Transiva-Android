package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;

public class AdminCustomerManagementActivity extends Activity {

    private static final String ENDPOINT =
            "https://transiva.my.id/server/admin_customer_management_native.php";

    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 25000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final NumberFormat rupiah = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));

    private SessionManager session;
    private String adminToken = "";
    private LinearLayout customerList;
    private TextView summaryText;
    private EditText searchInput;
    private ProgressBar progress;
    private JSONArray cachedCustomers = new JSONArray();
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#0B7CFF"));
        getWindow().setNavigationBarColor(Color.parseColor("#071426"));

        session = new SessionManager(this);
        adminToken = clean(session.getToken());

        if (!session.isLoggedIn()
                || !"admin".equals(session.normalizeRole(session.getRole()))
                || adminToken.isEmpty()) {
            showFatalSession();
            return;
        }

        setContentView(buildScreen());
        loadCustomers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (customerList != null && !loading) loadCustomers();
    }

    private View buildScreen() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F3F8FF"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(16), dp(14), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        root.addView(buildHeader());
        root.addView(buildBanner(), marginTop(12));
        root.addView(buildSummary(), marginTop(12));
        root.addView(buildSearch(), marginTop(12));

        TextView title = text("Daftar Customer", 18, "#0B3A78", true);
        root.addView(title, marginTop(15));

        customerList = new LinearLayout(this);
        customerList.setOrientation(LinearLayout.VERTICAL);
        root.addView(customerList, marginTop(9));

        Button back = outlineButton("Kembali");
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(-1, dp(48));
        backLp.setMargins(0, dp(14), 0, 0);
        root.addView(back, backLp);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressLp = new FrameLayout.LayoutParams(dp(50), dp(50));
        progressLp.gravity = Gravity.CENTER;
        page.addView(progress, progressLp);

        return page;
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = text("‹", 33, "#0B3A78", true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        row.addView(back, new LinearLayout.LayoutParams(dp(38), dp(44)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text("Manajemen Customer", 24, "#0B3A78", true));
        copy.addView(text("Kelola akun dan perangkat customer", 11, "#64748B", false));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView refresh = text("↻", 26, "#0B7CFF", true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setBackground(roundStroke("#FFFFFF", "#D7E6F8", 16, 1));
        refresh.setOnClickListener(v -> loadCustomers());
        row.addView(refresh, new LinearLayout.LayoutParams(dp(46), dp(46)));
        return row;
    }

    private View buildBanner() {
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(dp(18), dp(16), dp(18), dp(16));
        banner.setBackground(gradient());
        banner.addView(text("👥 Customer Aktif", 19, "#FFFFFF", true));
        banner.addView(text("Lihat status perangkat, reset, blokir, dan edit akun.", 12, "#FFFFFF", false));
        return banner;
    }

    private View buildSummary() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15), dp(13), dp(15), dp(13));
        box.setBackground(roundStroke("#FFFFFF", "#D7E6F8", 18, 1));
        box.addView(text("Ringkasan", 15, "#0B3A78", true));
        summaryText = text("Memuat data...", 12, "#64748B", false);
        box.addView(summaryText, marginTop(5));
        return box;
    }

    private View buildSearch() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        searchInput = new EditText(this);
        searchInput.setHint("Cari username, email, atau telepon");
        searchInput.setTextSize(13);
        searchInput.setSingleLine(true);
        searchInput.setPadding(dp(13), 0, dp(13), 0);
        searchInput.setBackground(roundStroke("#FFFFFF", "#D7E6F8", 16, 1));
        row.addView(searchInput, new LinearLayout.LayoutParams(0, dp(48), 1));

        Button search = primaryButton("Cari");
        search.setOnClickListener(v -> loadCustomers());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(76), dp(48));
        lp.setMargins(dp(8), 0, 0, 0);
        row.addView(search, lp);
        return row;
    }

    private void loadCustomers() {
        if (loading) return;
        setLoading(true);

        String query = searchInput == null ? "" : clean(searchInput.getText().toString());
        new Thread(() -> {
            try {
                String url = ENDPOINT + "?action=list&q=" + URLEncoder.encode(query, "UTF-8");
                JSONObject response = request("GET", url, null);
                if (!response.optBoolean("success")) {
                    throw new Exception(response.optString("message", "Gagal memuat customer."));
                }

                JSONArray customers = response.optJSONArray("customers");
                JSONObject summary = response.optJSONObject("summary");
                cachedCustomers = customers == null ? new JSONArray() : customers;

                mainHandler.post(() -> {
                    renderCustomers(cachedCustomers);
                    if (summary != null) {
                        summaryText.setText(
                                "Total " + summary.optInt("total")
                                        + "  •  Online " + summary.optInt("online")
                                        + "  •  Terhubung " + summary.optInt("bound")
                                        + "  •  Diblokir " + summary.optInt("banned")
                        );
                    }
                    setLoading(false);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setLoading(false);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void renderCustomers(JSONArray customers) {
        customerList.removeAllViews();

        if (customers.length() == 0) {
            TextView empty = text("Belum ada customer yang ditemukan.", 13, "#64748B", false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(24));
            customerList.addView(empty);
            return;
        }

        for (int i = 0; i < customers.length(); i++) {
            JSONObject customer = customers.optJSONObject(i);
            if (customer == null) continue;
            customerList.addView(buildCustomerCard(customer), marginBottom(10));
        }
    }

    private View buildCustomerCard(JSONObject customer) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(roundStroke("#FFFFFF", "#D7E6F8", 18, 1));
        card.setElevation(dp(2));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.addView(text(customer.optString("username", "-"), 17, "#0B3A78", true));
        identity.addView(text("ID #" + customer.optInt("id") + " • " + customer.optString("email", "-"), 11, "#64748B", false));
        titleRow.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));

        boolean online = customer.optInt("is_online") == 1;
        String status = clean(customer.optString("device_status", ""));
        String chipText = "banned".equals(status) ? "DIBLOKIR" : (online ? "ONLINE" : "OFFLINE");
        String chipColor = "banned".equals(status) ? "#DC2626" : (online ? "#16A34A" : "#64748B");
        TextView chip = text(chipText, 9, "#FFFFFF", true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(8), dp(4), dp(8), dp(4));
        chip.setBackground(roundStroke(chipColor, chipColor, 12, 1));
        titleRow.addView(chip);
        card.addView(titleRow);

        String device = customer.optInt("device_id") > 0
                ? clean(customer.optString("manufacturer")) + " " + clean(customer.optString("model"))
                : "Belum ada perangkat terhubung";
        if (device.trim().isEmpty()) device = clean(customer.optString("device_name", "Perangkat Android"));

        String details = "Telepon: " + fallback(customer.optString("phone"))
                + "\nSaldo: " + rupiah.format(customer.optDouble("balance", 0))
                + "\nPerangkat: " + device.trim()
                + "\nStatus device: " + fallback(status)
                + "\nTerakhir aktif: " + fallback(customer.optString("last_seen_at"));

        TextView info = text(details, 12, "#475569", false);
        info.setLineSpacing(0, 1.15f);
        card.addView(info, marginTop(9));

        LinearLayout firstRow = new LinearLayout(this);
        Button edit = smallButton("Edit Akun", "#0B7CFF");
        edit.setOnClickListener(v -> showEditDialog(customer));
        firstRow.addView(edit, weightedButton());

        Button reset = smallButton("Reset Device", "#F59E0B");
        reset.setOnClickListener(v -> confirmAction(
                "Reset perangkat",
                "Customer akan logout dan dapat masuk pada perangkat baru.",
                "reset_device",
                customer,
                "Reset perangkat oleh admin"
        ));
        LinearLayout.LayoutParams resetLp = weightedButton();
        resetLp.setMargins(dp(7), 0, 0, 0);
        firstRow.addView(reset, resetLp);
        card.addView(firstRow, marginTop(11));

        LinearLayout secondRow = new LinearLayout(this);
        if ("banned".equals(status)) {
            Button unban = smallButton("Buka Ban", "#16A34A");
            unban.setOnClickListener(v -> confirmAction(
                    "Buka blokir perangkat",
                    "Perangkat akan dapat digunakan kembali setelah customer login ulang.",
                    "unban_device",
                    customer,
                    "Ban dibuka oleh admin"
            ));
            secondRow.addView(unban, weightedButton());
        } else {
            Button ban = smallButton("Ban Device", "#DC2626");
            ban.setEnabled(customer.optInt("device_id") > 0);
            ban.setAlpha(ban.isEnabled() ? 1f : 0.5f);
            ban.setOnClickListener(v -> showReasonDialog(customer));
            secondRow.addView(ban, weightedButton());
        }
        card.addView(secondRow, marginTop(7));
        return card;
    }

    private void showEditDialog(JSONObject customer) {
        LinearLayout form = dialogForm();
        EditText username = dialogInput("Username", customer.optString("username"), false);
        EditText password = dialogInput("Password baru (kosongkan jika tidak diubah)", "", true);
        form.addView(username);
        form.addView(password, marginTop(8));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit akun customer")
                .setView(form)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", null)
                .create();

        dialog.setOnShowListener(ignore -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newUsername = clean(username.getText().toString());
            String newPassword = password.getText().toString();
            if (newUsername.length() < 3) {
                username.setError("Minimal 3 karakter");
                return;
            }
            dialog.dismiss();
            JSONObject body = new JSONObject();
            try {
                body.put("action", "edit_account");
                body.put("user_id", customer.optInt("id"));
                body.put("username", newUsername);
                body.put("new_password", newPassword);
            } catch (Exception ignored) {}
            performAction(body);
        }));
        dialog.show();
    }

    private void showReasonDialog(JSONObject customer) {
        LinearLayout form = dialogForm();
        EditText reason = dialogInput("Alasan ban perangkat", "", false);
        form.addView(reason);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Ban perangkat")
                .setMessage("Perangkat ini akan langsung logout dan tidak dapat dipakai login.")
                .setView(form)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Ban", null)
                .create();

        dialog.setOnShowListener(ignore -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = clean(reason.getText().toString());
            if (value.isEmpty()) {
                reason.setError("Alasan wajib diisi");
                return;
            }
            dialog.dismiss();
            JSONObject body = baseAction("ban_device", customer, value);
            performAction(body);
        }));
        dialog.show();
    }

    private void confirmAction(String title, String message, String action, JSONObject customer, String reason) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Lanjutkan", (dialog, which) -> performAction(baseAction(action, customer, reason)))
                .show();
    }

    private JSONObject baseAction(String action, JSONObject customer, String reason) {
        JSONObject body = new JSONObject();
        try {
            body.put("action", action);
            body.put("user_id", customer.optInt("id"));
            body.put("device_id", customer.optInt("device_id"));
            body.put("reason", reason);
        } catch (Exception ignored) {}
        return body;
    }

    private void performAction(JSONObject body) {
        if (loading) return;
        setLoading(true);
        new Thread(() -> {
            try {
                JSONObject response = request("POST", ENDPOINT, body);
                if (!response.optBoolean("success")) {
                    throw new Exception(response.optString("message", "Aksi gagal."));
                }
                String message = response.optString("message", "Berhasil.");
                mainHandler.post(() -> {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    setLoading(false);
                    loadCustomers();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setLoading(false);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private JSONObject request(String method, String endpoint, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setUseCaches(false);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Authorization", "Bearer " + adminToken);

        if ("POST".equals(method)) {
            connection.setDoOutput(true);
            byte[] bytes = (body == null ? "{}" : body.toString()).getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 400
                ? connection.getInputStream()
                : connection.getErrorStream();
        String raw = readStream(stream);
        connection.disconnect();

        if (raw.trim().isEmpty()) throw new Exception("Server tidak memberikan respons.");
        JSONObject result = new JSONObject(raw);
        if (status == 401) {
            mainHandler.post(() -> {
                session.forceLogout("admin_session_expired");
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }
        return result;
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
        }
        return out.toString();
    }

    private void setLoading(boolean value) {
        loading = value;
        if (progress != null) progress.setVisibility(value ? View.VISIBLE : View.GONE);
    }

    private void showFatalSession() {
        new AlertDialog.Builder(this)
                .setTitle("Sesi admin tidak valid")
                .setMessage("Silakan login kembali sebagai admin.")
                .setCancelable(false)
                .setPositiveButton("Masuk", (dialog, which) -> {
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .show();
    }

    private LinearLayout dialogForm() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(6), dp(22), 0);
        return form;
    }

    private EditText dialogInput(String hint, String value, boolean password) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setSingleLine(true);
        input.setTextSize(14);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(roundStroke("#FFFFFF", "#CBD5E1", 12, 1));
        input.setInputType(password
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT);
        input.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(50)));
        return input;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(gradient());
        return button;
    }

    private Button outlineButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.parseColor("#0B3A78"));
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(roundStroke("#FFFFFF", "#BFD5EE", 16, 1));
        return button;
    }

    private Button smallButton(String label, String color) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(11);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setBackground(roundStroke(color, color, 12, 1));
        return button;
    }

    private LinearLayout.LayoutParams weightedButton() {
        return new LinearLayout.LayoutParams(0, dp(43), 1);
    }

    private TextView text(String value, int size, String color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.parseColor(color));
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable gradient() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#086BFF"), Color.parseColor("#2DA8FF")}
        );
        drawable.setCornerRadius(dp(20));
        return drawable;
    }

    private GradientDrawable roundStroke(String background, String stroke, int radius, int width) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(background));
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(width), Color.parseColor(stroke));
        return drawable;
    }

    private LinearLayout.LayoutParams marginTop(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(top), 0, 0);
        return lp;
    }

    private LinearLayout.LayoutParams marginBottom(int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(bottom));
        return lp;
    }

    private String fallback(String value) {
        String clean = clean(value);
        return clean.isEmpty() || "null".equalsIgnoreCase(clean) ? "-" : clean;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
