package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import android.widget.EditText;
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
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class AdminDriverWithdrawManagementActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String SERVER_URL = BASE_URL + "server/";
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 20000;
    private static final long AUTO_REFRESH_MS = 20000L;

    private static final String CHANNEL_ID = "admin_withdraw_requests";
    private static final String CHANNEL_NAME = "Request Withdraw Driver";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 3107;

    private static final String PREF_NAME = "admin_withdraw_notification";
    private static final String PREF_SEEN_IDS = "seen_pending_withdraw_ids";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout list;
    private ProgressBar progressBar;
    private TextView statusText;
    private Button refreshButton;

    private boolean loading = false;
    private boolean processing = false;
    private boolean activityActive = false;

    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!activityActive || isFinishing()) {
                return;
            }

            load(false);
            handler.postDelayed(this, AUTO_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.parseColor("#071426"));
        getWindow().setNavigationBarColor(Color.parseColor("#071426"));

        createNotificationChannel();
        requestNotificationPermissionIfNeeded();
        build();
        load(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityActive = true;

        handler.removeCallbacks(autoRefreshRunnable);
        handler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_MS);
    }

    @Override
    protected void onPause() {
        activityActive = false;
        handler.removeCallbacks(autoRefreshRunnable);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        activityActive = false;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void build() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor("#F3F8FF"));

        ScrollView scrollView = new ScrollView(this);
        page.addView(
                scrollView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                )
        );

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(20));
        scrollView.addView(
                root,
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT
                )
        );

        root.addView(txt("💸 WD Driver", 22, true));
        root.addView(txt(
                "Approve / Reject penarikan saldo driver",
                13,
                false
        ));

        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(dp(16), dp(16), dp(16), dp(16));
        banner.setBackground(grad());

        TextView title = new TextView(this);
        title.setText("Withdraw Driver");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        banner.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(
                "Approve = saldo tetap keluar. Reject = saldo kembali ke driver."
        );
        subtitle.setTextColor(Color.parseColor("#EAF4FF"));
        subtitle.setTextSize(12);
        subtitle.setPadding(0, dp(4), 0, 0);
        banner.addView(subtitle);

        LinearLayout.LayoutParams bannerParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        bannerParams.setMargins(0, dp(14), 0, dp(12));
        root.addView(banner, bannerParams);

        LinearLayout actionBar = new LinearLayout(this);
        actionBar.setGravity(Gravity.CENTER_VERTICAL);

        statusText = txt("Memuat data...", 12, false);
        actionBar.addView(
                statusText,
                new LinearLayout.LayoutParams(0, -2, 1f)
        );

        refreshButton = new Button(this);
        refreshButton.setText("Refresh");
        refreshButton.setAllCaps(false);
        refreshButton.setOnClickListener(v -> load(true));
        actionBar.addView(
                refreshButton,
                new LinearLayout.LayoutParams(dp(110), dp(46))
        );

        root.addView(actionBar);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams =
                new LinearLayout.LayoutParams(dp(42), dp(42));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.setMargins(0, dp(8), 0, dp(8));
        root.addView(progressBar, progressParams);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        Button back = new Button(this);
        back.setText("Kembali");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());

        LinearLayout.LayoutParams backParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(48)
                );
        backParams.setMargins(0, dp(10), 0, 0);
        root.addView(back, backParams);

        setContentView(page);
    }

    private void load(boolean showProgress) {
        if (loading || processing) {
            return;
        }

        setLoading(true, showProgress);

        new Thread(() -> {
            try {
                JSONObject response = getJson(
                        SERVER_URL
                                + "getDriverWithdrawals.php?v="
                                + System.currentTimeMillis()
                );

                boolean success = response.optBoolean("success", false);
                JSONArray withdrawals = response.optJSONArray("withdrawals");

                if (!success) {
                    throw new IOException(
                            response.optString(
                                    "message",
                                    "Server gagal mengambil data withdraw"
                            )
                    );
                }

                if (withdrawals == null) {
                    withdrawals = new JSONArray();
                }

                final JSONArray finalWithdrawals = withdrawals;

                handler.post(() -> {
                    if (!isActivityUsable()) {
                        return;
                    }

                    setLoading(false, false);
                    render(finalWithdrawals);
                    notifyNewPendingWithdrawals(finalWithdrawals);
                    updateStatus(finalWithdrawals);
                });

            } catch (Exception exception) {
                final String message = readableError(exception);

                handler.post(() -> {
                    if (!isActivityUsable()) {
                        return;
                    }

                    setLoading(false, false);
                    statusText.setText("Gagal memuat data");
                    Toast.makeText(
                            this,
                            "Gagal memuat withdraw: " + message,
                            Toast.LENGTH_LONG
                    ).show();

                    if (list.getChildCount() == 0) {
                        list.addView(txt(
                                "Data belum dapat dimuat. Tekan Refresh.",
                                14,
                                false
                        ));
                    }
                });
            }
        }).start();
    }

    private void render(JSONArray withdrawals) {
        list.removeAllViews();

        if (withdrawals == null || withdrawals.length() == 0) {
            TextView empty = txt("Belum ada pengajuan WD.", 14, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(24), dp(12), dp(24));
            list.addView(empty);
            return;
        }

        for (int i = 0; i < withdrawals.length(); i++) {
            JSONObject item = withdrawals.optJSONObject(i);

            if (item != null) {
                addCard(item);
            }
        }
    }

    private void addCard(JSONObject item) {
        final int withdrawId = item.optInt("id", 0);
        final String status = clean(item.optString("status", "pending"))
                .toLowerCase(Locale.US);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1), Color.parseColor("#D7E6F8"));
        card.setBackground(background);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        top.addView(
                identity,
                new LinearLayout.LayoutParams(0, -2, 1f)
        );

        identity.addView(txt(
                firstNonEmpty(item.optString("username"), "-"),
                16,
                true
        ));

        identity.addView(txt(
                rupiah(item.optDouble("amount", 0)),
                20,
                true
        ));

        TextView statusBadge = txt(statusLabel(status), 12, true);
        statusBadge.setTextColor(Color.parseColor(statusColor(status)));
        statusBadge.setGravity(Gravity.CENTER);
        statusBadge.setPadding(dp(10), dp(6), dp(10), dp(6));
        statusBadge.setBackground(
                roundedStroke(
                        statusBackground(status),
                        statusStroke(status),
                        16
                )
        );
        top.addView(statusBadge);

        card.addView(top);

        addDetail(card, "🏦 " + firstNonEmpty(
                item.optString("bank_name"),
                "-"
        ));

        addDetail(card, "🔢 " + firstNonEmpty(
                item.optString("account_number"),
                "-"
        ));

        addDetail(card, "👤 " + firstNonEmpty(
                item.optString("account_name"),
                "-"
        ));

        addDetail(card, "🕒 " + firstNonEmpty(
                item.optString("requested_at"),
                item.optString("created_at"),
                "-"
        ));

        String note = clean(item.optString("note"));
        if (!note.isEmpty()) {
            addDetail(card, "Catatan driver: " + note);
        }

        String adminNote = clean(item.optString("admin_note"));
        if (!adminNote.isEmpty()) {
            addDetail(card, "Catatan admin: " + adminNote);
        }

        if ("pending".equals(status)) {
            Button approveButton = actionButton("✓ Approve", "#059669");
            Button rejectButton = actionButton("✕ Reject", "#DC2626");

            approveButton.setOnClickListener(v -> {
                if (withdrawId <= 0) {
                    Toast.makeText(
                            this,
                            "ID withdraw tidak valid",
                            Toast.LENGTH_LONG
                    ).show();
                    return;
                }

                process(
                        withdrawId,
                        "approve",
                        approveButton,
                        rejectButton
                );
            });

            rejectButton.setOnClickListener(v -> {
                if (withdrawId <= 0) {
                    Toast.makeText(
                            this,
                            "ID withdraw tidak valid",
                            Toast.LENGTH_LONG
                    ).show();
                    return;
                }

                process(
                        withdrawId,
                        "reject",
                        approveButton,
                        rejectButton
                );
            });

            LinearLayout.LayoutParams buttonParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(48)
                    );
            buttonParams.setMargins(0, dp(12), 0, dp(6));
            card.addView(approveButton, buttonParams);

            LinearLayout.LayoutParams rejectParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(48)
                    );
            card.addView(rejectButton, rejectParams);
        }

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        cardParams.setMargins(0, 0, 0, dp(10));
        list.addView(card, cardParams);
    }

    private void process(
            int id,
            String action,
            Button approveButton,
            Button rejectButton
    ) {
        if (processing) {
            return;
        }

        final boolean approve = "approve".equalsIgnoreCase(action);
        final EditText noteInput = new EditText(this);
        noteInput.setHint("Catatan admin (opsional)");
        noteInput.setSingleLine(false);
        noteInput.setMinLines(2);
        noteInput.setPadding(dp(12), dp(8), dp(12), dp(8));

        String message = approve
                ? "Setujui WD ini?\n\nPastikan dana sudah atau akan ditransfer ke driver."
                : "Tolak WD ini?\n\nSaldo akan dikembalikan ke driver.";

        new AlertDialog.Builder(this)
                .setTitle(approve ? "Approve Withdraw" : "Reject Withdraw")
                .setMessage(message)
                .setView(noteInput)
                .setPositiveButton("Proses", (dialog, which) -> {
                    String adminNote = noteInput.getText() == null
                            ? ""
                            : noteInput.getText().toString().trim();

                    setActionButtonsEnabled(
                            approveButton,
                            rejectButton,
                            false
                    );
                    processing = true;
                    progressBar.setVisibility(View.VISIBLE);
                    refreshButton.setEnabled(false);

                    new Thread(() -> {
                        try {
                            String adminUsername = getAdminUsername();

                            JSONObject payload = new JSONObject();
                            payload.put("id", id);
                            payload.put("action", action);
                            payload.put("admin", adminUsername);
                            payload.put("admin_note", adminNote);

                            JSONObject response = postJson(
                                    SERVER_URL + "processDriverWithdraw.php",
                                    payload
                            );

                            final boolean success =
                                    response.optBoolean("success", false);

                            final String responseMessage =
                                    response.optString(
                                            "message",
                                            success
                                                    ? "Withdraw berhasil diproses"
                                                    : "Withdraw gagal diproses"
                                    );

                            handler.post(() -> {
                                if (!isActivityUsable()) {
                                    return;
                                }

                                processing = false;
                                progressBar.setVisibility(View.GONE);
                                refreshButton.setEnabled(true);

                                Toast.makeText(
                                        this,
                                        responseMessage,
                                        Toast.LENGTH_LONG
                                ).show();

                                if (success) {
                                    removeSeenWithdrawId(id);
                                    load(true);
                                } else {
                                    setActionButtonsEnabled(
                                            approveButton,
                                            rejectButton,
                                            true
                                    );
                                }
                            });

                        } catch (Exception exception) {
                            final String error = readableError(exception);

                            handler.post(() -> {
                                if (!isActivityUsable()) {
                                    return;
                                }

                                processing = false;
                                progressBar.setVisibility(View.GONE);
                                refreshButton.setEnabled(true);
                                setActionButtonsEnabled(
                                        approveButton,
                                        rejectButton,
                                        true
                                );

                                Toast.makeText(
                                        this,
                                        "Gagal memproses withdraw: " + error,
                                        Toast.LENGTH_LONG
                                ).show();
                            });
                        }
                    }).start();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private JSONObject getJson(String url) throws Exception {
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
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );

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
                    responseCode >= 200 && responseCode < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream();

            String responseBody = readStream(inputStream);

            if (responseBody.trim().isEmpty()) {
                throw new IOException(
                        "Respons server kosong. HTTP " + responseCode
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

    private void notifyNewPendingWithdrawals(JSONArray withdrawals) {
        Set<String> seenIds = getSeenWithdrawIds();
        Set<String> currentPendingIds = new HashSet<>();

        int newCount = 0;
        String latestUsername = "";
        double latestAmount = 0;

        for (int i = 0; i < withdrawals.length(); i++) {
            JSONObject item = withdrawals.optJSONObject(i);

            if (item == null) {
                continue;
            }

            String status = clean(item.optString("status"))
                    .toLowerCase(Locale.US);

            int id = item.optInt("id", 0);

            if (!"pending".equals(status) || id <= 0) {
                continue;
            }

            String idText = String.valueOf(id);
            currentPendingIds.add(idText);

            if (!seenIds.contains(idText)) {
                newCount++;
                latestUsername = firstNonEmpty(
                        item.optString("username"),
                        "Driver"
                );
                latestAmount = item.optDouble("amount", 0);
            }
        }

        saveSeenWithdrawIds(currentPendingIds);

        if (newCount <= 0) {
            return;
        }

        String title = newCount == 1
                ? "Request withdraw baru"
                : newCount + " request withdraw baru";

        String text = newCount == 1
                ? latestUsername + " mengajukan " + rupiah(latestAmount)
                : "Buka menu WD Driver untuk memeriksa pengajuan baru.";

        showWithdrawNotification(title, text, newCount);
    }

    private void showWithdrawNotification(
            String title,
            String message,
            int count
    ) {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = new Intent(
                this,
                AdminDriverWithdrawManagementActivity.class
        );
        intent.setFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                8101,
                intent,
                pendingIntentFlags
        );

        android.app.Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new android.app.Notification.Builder(
                    this,
                    CHANNEL_ID
            );
        } else {
            builder = new android.app.Notification.Builder(this);
        }

        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setNumber(count)
                .setDefaults(
                        android.app.Notification.DEFAULT_SOUND
                                | android.app.Notification.DEFAULT_VIBRATE
                );

        NotificationManager notificationManager =
                (NotificationManager)
                        getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify(8102, builder.build());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager =
                (NotificationManager)
                        getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager == null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );

        channel.setDescription(
                "Notifikasi saat driver mengajukan withdraw baru"
        );
        channel.enableVibration(true);

        manager.createNotificationChannel(channel);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.POST_NOTIFICATIONS
                    },
                    NOTIFICATION_PERMISSION_REQUEST
            );
        }
    }

    private Set<String> getSeenWithdrawIds() {
        SharedPreferences preferences =
                getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        Set<String> stored =
                preferences.getStringSet(
                        PREF_SEEN_IDS,
                        new HashSet<>()
                );

        return stored == null
                ? new HashSet<>()
                : new HashSet<>(stored);
    }

    private void saveSeenWithdrawIds(Set<String> ids) {
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .edit()
                .putStringSet(
                        PREF_SEEN_IDS,
                        new HashSet<>(ids)
                )
                .apply();
    }

    private void removeSeenWithdrawId(int id) {
        Set<String> ids = getSeenWithdrawIds();
        ids.remove(String.valueOf(id));
        saveSeenWithdrawIds(ids);
    }

    private String getAdminUsername() {
        try {
            SessionManager sessionManager = new SessionManager(this);
            String username = sessionManager.getUsername();

            if (username != null && !username.trim().isEmpty()) {
                return username.trim();
            }
        } catch (Exception ignored) {
        }

        return "admin";
    }

    private void updateStatus(JSONArray withdrawals) {
        int pendingCount = 0;

        for (int i = 0; i < withdrawals.length(); i++) {
            JSONObject item = withdrawals.optJSONObject(i);

            if (item != null
                    && "pending".equalsIgnoreCase(
                    item.optString("status")
            )) {
                pendingCount++;
            }
        }

        statusText.setText(
                pendingCount > 0
                        ? pendingCount + " withdraw menunggu diproses"
                        : "Tidak ada withdraw pending"
        );
    }

    private void setLoading(boolean value, boolean showProgress) {
        loading = value;
        progressBar.setVisibility(
                value && showProgress
                        ? View.VISIBLE
                        : View.GONE
        );
        refreshButton.setEnabled(!value && !processing);
    }

    private void setActionButtonsEnabled(
            Button approveButton,
            Button rejectButton,
            boolean enabled
    ) {
        approveButton.setEnabled(enabled);
        rejectButton.setEnabled(enabled);
        approveButton.setAlpha(enabled ? 1f : 0.55f);
        rejectButton.setAlpha(enabled ? 1f : 0.55f);
    }

    private boolean isActivityUsable() {
        if (isFinishing()) {
            return false;
        }

        return Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1
                || !isDestroyed();
    }

    private void addDetail(LinearLayout parent, String value) {
        TextView detail = txt(value, 13, false);
        detail.setPadding(0, dp(5), 0, 0);
        parent.addView(detail);
    }

    private Button actionButton(String text, String color) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor(color));
        background.setCornerRadius(dp(14));
        button.setBackground(background);

        return button;
    }

    private TextView txt(String text, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.parseColor("#0B3A78"));

        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }

        return view;
    }

    private GradientDrawable grad() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.parseColor("#086BFF"),
                        Color.parseColor("#2EA2FF")
                }
        );

        drawable.setCornerRadius(dp(20));
        return drawable;
    }

    private GradientDrawable roundedStroke(
            String backgroundColor,
            String strokeColor,
            int radiusDp
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(backgroundColor));
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(
                dp(1),
                Color.parseColor(strokeColor)
        );
        return drawable;
    }

    private String rupiah(double value) {
        return "Rp " + NumberFormat
                .getNumberInstance(new Locale("id", "ID"))
                .format(value);
    }

    private String statusLabel(String status) {
        if ("approved".equals(status)) {
            return "Disetujui";
        }

        if ("rejected".equals(status)) {
            return "Ditolak";
        }

        return "Menunggu";
    }

    private String statusColor(String status) {
        if ("approved".equals(status)) {
            return "#047857";
        }

        if ("rejected".equals(status)) {
            return "#B91C1C";
        }

        return "#B45309";
    }

    private String statusBackground(String status) {
        if ("approved".equals(status)) {
            return "#ECFDF5";
        }

        if ("rejected".equals(status)) {
            return "#FEF2F2";
        }

        return "#FFFBEB";
    }

    private String statusStroke(String status) {
        if ("approved".equals(status)) {
            return "#A7F3D0";
        }

        if ("rejected".equals(status)) {
            return "#FECACA";
        }

        return "#FDE68A";
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            String cleaned = clean(value);

            if (!cleaned.isEmpty()
                    && !"null".equalsIgnoreCase(cleaned)) {
                return cleaned;
            }
        }

        return "";
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
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
