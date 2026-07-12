package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class AdminVerifyUserActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/server/";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 20000;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private LinearLayout userList;
    private TextView messageView;
    private ProgressBar progressBar;
    private Button refreshButton;
    private Button backButton;

    private boolean loading = false;
    private boolean processing = false;

    private SessionManager sessionManager;
    private String adminUsername = "";
    private String adminToken = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow()
                    .getDecorView()
                    .setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    );
        }

        sessionManager = new SessionManager(this);
        adminUsername = safe(sessionManager.getUsername());
        adminToken = firstNonEmpty(
                sessionManager.getToken(),
                sessionManager.getFcmToken(),
                getSharedPreferences("transiva_fcm", MODE_PRIVATE)
                        .getString("fcm_token", ""),
                getSharedPreferences("transiva", MODE_PRIVATE)
                        .getString("fcm_token", "")
        );

        buildUi();

        if (adminUsername.isEmpty() || adminToken.isEmpty()) {
            showMessage(
                    "Sesi admin native tidak lengkap. Silakan logout lalu login kembali.",
                    false
            );
        } else {
            loadUsers(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (userList != null && userList.getChildCount() > 0) {
            loadUsers(false);
        }
    }

    private void buildUi() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F3F8FF"));

        ScrollView scrollView = new ScrollView(this);
        page.addView(
                scrollView,
                new FrameLayout.LayoutParams(-1, -1)
        );

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(18), dp(14), dp(24));

        scrollView.addView(
                root,
                new ScrollView.LayoutParams(-1, -2)
        );

        LinearLayout header = card();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(header);

        TextView icon = text(
                "✅",
                24,
                "#FFFFFF",
                true
        );
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(
                roundGradient("#086BFF", "#2EA2FF", dp(20))
        );

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(dp(46), dp(46));
        iconParams.setMargins(0, 0, dp(12), 0);
        header.addView(icon, iconParams);

        LinearLayout headerText = new LinearLayout(this);
        headerText.setOrientation(LinearLayout.VERTICAL);
        header.addView(
                headerText,
                new LinearLayout.LayoutParams(0, -2, 1f)
        );

        headerText.addView(text(
                "Verifikasi User",
                18,
                "#0B3A78",
                true
        ));

        add(
                headerText,
                text(
                        "Verifikasi manual email user",
                        12,
                        "#64748B",
                        false
                ),
                0,
                dp(2),
                0,
                0
        );

        TextView badge = text(
                "Admin",
                12,
                "#0B7CFF",
                true
        );
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(
                dp(11),
                dp(6),
                dp(11),
                dp(6)
        );
        badge.setBackground(
                roundStroke(
                        "#EAF4FF",
                        "#B9DBFF",
                        dp(18),
                        1
                )
        );
        header.addView(badge);

        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(dp(16), dp(16), dp(16), dp(16));
        banner.setBackground(
                roundGradient("#086BFF", "#2EA2FF", dp(22))
        );

        add(root, banner, 0, dp(12), 0, dp(12));

        banner.addView(text(
                "Verifikasi Email Manual",
                18,
                "#FFFFFF",
                true
        ));

        add(
                banner,
                text(
                        "Aktifkan akun user yang belum menyelesaikan verifikasi email.",
                        12,
                        "#EAF4FF",
                        false
                ),
                0,
                dp(5),
                0,
                0
        );

        messageView = text(
                "",
                13,
                "#B91C1C",
                true
        );
        messageView.setVisibility(View.GONE);
        messageView.setPadding(
                dp(13),
                dp(11),
                dp(13),
                dp(11)
        );
        add(root, messageView, 0, 0, 0, dp(10));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView sectionTitle = text(
                "User Menunggu Verifikasi",
                17,
                "#0B3A78",
                true
        );

        actionRow.addView(
                sectionTitle,
                new LinearLayout.LayoutParams(0, -2, 1f)
        );

        refreshButton = outlineButton("Refresh");
        refreshButton.setOnClickListener(v -> loadUsers(true));
        actionRow.addView(
                refreshButton,
                new LinearLayout.LayoutParams(dp(105), dp(44))
        );

        root.addView(actionRow);

        userList = new LinearLayout(this);
        userList.setOrientation(LinearLayout.VERTICAL);
        add(root, userList, 0, dp(10), 0, 0);

        backButton = outlineButton("Kembali");
        backButton.setOnClickListener(v -> finish());
        add(root, backButton, 0, dp(10), 0, 0);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);

        FrameLayout.LayoutParams progressParams =
                new FrameLayout.LayoutParams(dp(52), dp(52));
        progressParams.gravity = Gravity.CENTER;
        page.addView(progressBar, progressParams);

        setContentView(page);
    }

    private void loadUsers(boolean showProgress) {
        if (loading || processing) {
            return;
        }

        setLoading(true, showProgress);
        hideMessage();

        userList.removeAllViews();
        userList.addView(empty("Memuat data user..."));

        new Thread(() -> {
            try {
                JSONObject response = getJson(
                        BASE_URL
                                + "adminGetUnverifiedUsers.php?v="
                                + System.currentTimeMillis()
                                + "&admin_username="
                                + encode(adminUsername)
                                + "&admin_token="
                                + encode(adminToken)
                );

                if (!response.optBoolean("success", false)) {
                    throw new IOException(
                            response.optString(
                                    "message",
                                    "Gagal memuat user"
                            )
                    );
                }

                JSONArray users = response.optJSONArray("users");

                if (users == null) {
                    users = new JSONArray();
                }

                final JSONArray finalUsers = users;

                handler.post(() -> {
                    if (!activityUsable()) {
                        return;
                    }

                    setLoading(false, false);
                    renderUsers(finalUsers);
                });

            } catch (Exception exception) {
                final String message = readableError(exception);

                handler.post(() -> {
                    if (!activityUsable()) {
                        return;
                    }

                    setLoading(false, false);
                    userList.removeAllViews();
                    userList.addView(empty(
                            "Server error saat memuat user"
                    ));
                    showMessage(
                            "Gagal memuat user: " + message,
                            false
                    );
                });
            }
        }).start();
    }

    private void renderUsers(JSONArray users) {
        userList.removeAllViews();

        if (users.length() == 0) {
            userList.addView(empty(
                    "Tidak ada user yang menunggu verifikasi"
            ));
            return;
        }

        for (int i = 0; i < users.length(); i++) {
            JSONObject user = users.optJSONObject(i);

            if (user != null) {
                userList.addView(userCard(user));
            }
        }
    }

    private View userCard(JSONObject user) {
        final int userId = user.optInt("id", 0);
        final String username = firstNonEmpty(
                user.optString("username"),
                "-"
        );

        LinearLayout card = card();
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top);

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        top.addView(
                identity,
                new LinearLayout.LayoutParams(0, -2, 1f)
        );

        identity.addView(text(
                username,
                16,
                "#0B3A78",
                true
        ));

        TextView pendingBadge = text(
                "Belum Verifikasi",
                11,
                "#B45309",
                true
        );
        pendingBadge.setPadding(
                dp(9),
                dp(5),
                dp(9),
                dp(5)
        );
        pendingBadge.setBackground(
                roundStroke(
                        "#FFFBEB",
                        "#FDE68A",
                        dp(16),
                        1
                )
        );
        top.addView(pendingBadge);

        add(
                card,
                text(
                        "Email: " + firstNonEmpty(
                                user.optString("email"),
                                "-"
                        ),
                        13,
                        "#64748B",
                        false
                ),
                0,
                dp(9),
                0,
                0
        );

        add(
                card,
                text(
                        "Role: " + firstNonEmpty(
                                user.optString("role"),
                                "-"
                        ),
                        13,
                        "#64748B",
                        false
                ),
                0,
                dp(4),
                0,
                0
        );

        add(
                card,
                text(
                        "Status: ❌ Belum Verifikasi",
                        13,
                        "#64748B",
                        false
                ),
                0,
                dp(4),
                0,
                0
        );

        Button verifyButton = primaryButton(
                "Verifikasi Manual"
        );

        verifyButton.setOnClickListener(v -> {
            if (userId <= 0) {
                showMessage(
                        "ID user tidak valid.",
                        false
                );
                return;
            }

            confirmVerification(
                    userId,
                    username,
                    verifyButton
            );
        });

        add(
                card,
                verifyButton,
                0,
                dp(12),
                0,
                0
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);

        return card;
    }

    private void confirmVerification(
            int userId,
            String username,
            Button verifyButton
    ) {
        new AlertDialog.Builder(this)
                .setTitle("Verifikasi Manual")
                .setMessage(
                        "Verifikasi user "
                                + username
                                + " secara manual?"
                )
                .setPositiveButton(
                        "Verifikasi",
                        (dialog, which) ->
                                verifyUser(userId, verifyButton)
                )
                .setNegativeButton("Batal", null)
                .show();
    }

    private void verifyUser(
            int userId,
            Button verifyButton
    ) {
        if (processing) {
            return;
        }

        processing = true;
        hideMessage();

        verifyButton.setEnabled(false);
        verifyButton.setAlpha(0.6f);
        verifyButton.setText("Memverifikasi...");

        refreshButton.setEnabled(false);
        backButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("id", userId);
                payload.put("admin_username", adminUsername);
                payload.put("admin_token", adminToken);

                JSONObject response = postJson(
                        BASE_URL + "adminVerifyUserEmail.php",
                        payload
                );

                final boolean success =
                        response.optBoolean("success", false);

                final String responseMessage =
                        response.optString(
                                "message",
                                success
                                        ? "User berhasil diverifikasi"
                                        : "Gagal verifikasi user"
                        );

                handler.post(() -> {
                    if (!activityUsable()) {
                        return;
                    }

                    processing = false;
                    progressBar.setVisibility(View.GONE);
                    refreshButton.setEnabled(true);
                    backButton.setEnabled(true);

                    if (success) {
                        showMessage(
                                responseMessage,
                                true
                        );

                        Toast.makeText(
                                this,
                                responseMessage,
                                Toast.LENGTH_LONG
                        ).show();

                        loadUsers(false);
                    } else {
                        verifyButton.setEnabled(true);
                        verifyButton.setAlpha(1f);
                        verifyButton.setText(
                                "Verifikasi Manual"
                        );

                        showMessage(
                                responseMessage,
                                false
                        );
                    }
                });

            } catch (Exception exception) {
                final String message = readableError(exception);

                handler.post(() -> {
                    if (!activityUsable()) {
                        return;
                    }

                    processing = false;
                    progressBar.setVisibility(View.GONE);
                    refreshButton.setEnabled(true);
                    backButton.setEnabled(true);

                    verifyButton.setEnabled(true);
                    verifyButton.setAlpha(1f);
                    verifyButton.setText(
                            "Verifikasi Manual"
                    );

                    showMessage(
                            "Server error: " + message,
                            false
                    );
                });
            }
        }).start();
    }

    private JSONObject getJson(String url)
            throws Exception {
        return requestJson("GET", url, null);
    }

    private JSONObject postJson(
            String url,
            JSONObject payload
    ) throws Exception {
        return requestJson("POST", url, payload);
    }

    private JSONObject requestJson(
            String method,
            String url,
            JSONObject payload
    ) throws Exception {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection)
                    new URL(url).openConnection();

            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );

            if (!adminUsername.isEmpty()) {
                connection.setRequestProperty(
                        "X-Admin-Username",
                        adminUsername
                );
            }

            if (!adminToken.isEmpty()) {
                connection.setRequestProperty(
                        "X-Admin-Token",
                        adminToken
                );
            }

            if (payload != null) {
                byte[] body = payload
                        .toString()
                        .getBytes(StandardCharsets.UTF_8);

                connection.setDoOutput(true);
                connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );
                connection.setFixedLengthStreamingMode(body.length);

                try (OutputStream outputStream =
                             connection.getOutputStream()) {
                    outputStream.write(body);
                    outputStream.flush();
                }
            }

            int responseCode = connection.getResponseCode();

            InputStream inputStream =
                    responseCode >= 200
                            && responseCode < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream();

            String responseBody = readStream(inputStream);

            if (responseBody.trim().isEmpty()) {
                throw new IOException(
                        "Respons server kosong. HTTP "
                                + responseCode
                );
            }

            JSONObject response;

            try {
                response = new JSONObject(responseBody);
            } catch (JSONException exception) {
                throw new IOException(
                        "Respons server bukan JSON: "
                                + limitText(responseBody)
                );
            }

            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException(
                        response.optString(
                                "message",
                                "HTTP error " + responseCode
                        )
                );
            }

            return response;

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readStream(InputStream inputStream)
            throws IOException {
        if (inputStream == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        try (BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader(
                                     inputStream,
                                     StandardCharsets.UTF_8
                             )
                     )) {
            String line;

            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }

        return result.toString();
    }

    private void setLoading(
            boolean value,
            boolean showProgress
    ) {
        loading = value;

        progressBar.setVisibility(
                value && showProgress
                        ? View.VISIBLE
                        : View.GONE
        );

        refreshButton.setEnabled(!value && !processing);
        backButton.setEnabled(!value && !processing);
    }

    private void showMessage(
            String message,
            boolean success
    ) {
        messageView.setText(message);
        messageView.setTextColor(
                Color.parseColor(
                        success ? "#047857" : "#B91C1C"
                )
        );

        messageView.setBackground(
                roundStroke(
                        success ? "#ECFDF5" : "#FEF2F2",
                        success ? "#A7F3D0" : "#FECACA",
                        dp(15),
                        1
                )
        );

        messageView.setVisibility(View.VISIBLE);
    }

    private void hideMessage() {
        messageView.setVisibility(View.GONE);
        messageView.setText("");
    }

    private boolean activityUsable() {
        if (isFinishing()) {
            return false;
        }

        return Build.VERSION.SDK_INT
                < Build.VERSION_CODES.JELLY_BEAN_MR1
                || !isDestroyed();
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(
                dp(14),
                dp(14),
                dp(14),
                dp(14)
        );
        layout.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#D7E6F8",
                        dp(22),
                        1
                )
        );
        return layout;
    }

    private TextView empty(String message) {
        TextView view = text(
                message,
                14,
                "#64748B",
                false
        );
        view.setGravity(Gravity.CENTER);
        view.setPadding(
                dp(14),
                dp(22),
                dp(14),
                dp(22)
        );
        view.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#D7E6F8",
                        dp(22),
                        1
                )
        );
        return view;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(Color.WHITE);
        button.setBackground(
                roundGradient(
                        "#086BFF",
                        "#2EA2FF",
                        dp(16)
                )
        );
        return button;
    }

    private Button outlineButton(String value) {
        Button button = primaryButton(value);
        button.setTextColor(Color.parseColor("#0B7CFF"));
        button.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#9DCAFF",
                        dp(16),
                        1
                )
        );
        return button;
    }

    private TextView text(
            String value,
            int size,
            String color,
            boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.parseColor(color));

        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }

        return view;
    }

    private void add(
            LinearLayout parent,
            View child,
            int left,
            int top,
            int right,
            int bottom
    ) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);

        params.setMargins(
                left,
                top,
                right,
                bottom
        );

        parent.addView(child, params);
    }

    private GradientDrawable round(
            String color,
            int radius
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(color));
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable roundStroke(
            String background,
            String stroke,
            int radius,
            int width
    ) {
        GradientDrawable drawable =
                round(background, radius);

        drawable.setStroke(
                dp(width),
                Color.parseColor(stroke)
        );

        return drawable;
    }

    private GradientDrawable roundGradient(
            String start,
            String end,
            int radius
    ) {
        GradientDrawable drawable =
                new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{
                                Color.parseColor(start),
                                Color.parseColor(end)
                        }
                );

        drawable.setCornerRadius(radius);
        return drawable;
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null
                    && !value.trim().isEmpty()
                    && !"null".equalsIgnoreCase(
                    value.trim()
            )) {
                return value.trim();
            }
        }

        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(
                    safe(value),
                    "UTF-8"
            );
        } catch (Exception ignored) {
            return "";
        }
    }

    private String readableError(Exception exception) {
        String message = exception.getMessage();

        if (message == null || message.trim().isEmpty()) {
            return exception.getClass().getSimpleName();
        }

        return message.trim();
    }

    private String limitText(String value) {
        if (value == null) {
            return "";
        }

        String cleaned = value.trim();

        if (cleaned.length() <= 220) {
            return cleaned;
        }

        return cleaned.substring(0, 220) + "...";
    }

    private int dp(int value) {
        return (int) (
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
                        + 0.5f
        );
    }
}
