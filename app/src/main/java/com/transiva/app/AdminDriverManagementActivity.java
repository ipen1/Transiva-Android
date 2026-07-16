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
import android.widget.Spinner;
import android.widget.ArrayAdapter;
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
import java.util.Locale;

public class AdminDriverManagementActivity extends Activity {

    private static final String ENDPOINT =
            "https://transiva.my.id/server/admin_driver_management_native.php";

    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 25000;

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private SessionManager session;
    private String adminToken = "";

    private LinearLayout driverList;
    private ProgressBar progress;
    private TextView summaryText;
    private EditText searchInput;

    private boolean loading;
    private JSONArray cachedDrivers =
            new JSONArray();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(
                Color.parseColor("#0B7CFF")
        );

        getWindow().setNavigationBarColor(
                Color.parseColor("#071426")
        );

        session = new SessionManager(this);
        adminToken = clean(session.getToken());

        if (
                !session.isLoggedIn()
                        || !"admin".equals(
                        session.normalizeRole(
                                session.getRole()
                        )
                )
                        || adminToken.isEmpty()
        ) {
            showFatalSession();
            return;
        }

        setContentView(buildScreen());
        loadDrivers();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (
                driverList != null
                        && !loading
        ) {
            loadDrivers();
        }
    }

    private View buildScreen() {
        FrameLayout page =
                new FrameLayout(this);

        page.setBackgroundColor(
                Color.parseColor("#F3F8FF")
        );

        ScrollView scroll =
                new ScrollView(this);

        scroll.setFillViewport(true);

        page.addView(
                scroll,
                new FrameLayout.LayoutParams(
                        -1,
                        -1
                )
        );

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(
                dp(14),
                dp(16),
                dp(14),
                dp(28)
        );

        scroll.addView(
                root,
                new ScrollView.LayoutParams(
                        -1,
                        -2
                )
        );

        root.addView(buildHeader());
        root.addView(buildBanner(), marginTop(12));
        root.addView(buildSummaryCard(), marginTop(12));
        root.addView(buildSearchAndActions(), marginTop(12));

        TextView listTitle =
                text(
                        "Daftar Driver",
                        18,
                        "#0B3A78",
                        true
                );

        root.addView(
                listTitle,
                marginTop(15)
        );

        driverList =
                new LinearLayout(this);

        driverList.setOrientation(
                LinearLayout.VERTICAL
        );

        root.addView(
                driverList,
                marginTop(9)
        );

        Button back =
                outlineButton("Kembali");

        back.setOnClickListener(
                view -> finish()
        );

        LinearLayout.LayoutParams backLp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(48)
                );

        backLp.setMargins(
                0,
                dp(14),
                0,
                0
        );

        root.addView(back, backLp);

        progress =
                new ProgressBar(this);

        progress.setVisibility(View.GONE);

        FrameLayout.LayoutParams progressLp =
                new FrameLayout.LayoutParams(
                        dp(50),
                        dp(50)
                );

        progressLp.gravity = Gravity.CENTER;

        page.addView(
                progress,
                progressLp
        );

        return page;
    }

    private View buildHeader() {
        LinearLayout row =
                new LinearLayout(this);

        row.setGravity(
                Gravity.CENTER_VERTICAL
        );

        TextView back =
                text(
                        "‹",
                        33,
                        "#0B3A78",
                        true
                );

        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(
                view -> finish()
        );

        row.addView(
                back,
                new LinearLayout.LayoutParams(
                        dp(38),
                        dp(44)
                )
        );

        LinearLayout copy =
                new LinearLayout(this);

        copy.setOrientation(
                LinearLayout.VERTICAL
        );

        copy.addView(
                text(
                        "Manajemen Driver",
                        24,
                        "#0B3A78",
                        true
                )
        );

        copy.addView(
                text(
                        "Buat, verifikasi, edit, suspend, dan hapus driver",
                        11,
                        "#64748B",
                        false
                )
        );

        row.addView(
                copy,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        TextView refresh =
                text(
                        "↻",
                        26,
                        "#0B7CFF",
                        true
                );

        refresh.setGravity(Gravity.CENTER);

        refresh.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#D7E6F8",
                        16,
                        1
                )
        );

        refresh.setOnClickListener(
                view -> loadDrivers()
        );

        row.addView(
                refresh,
                new LinearLayout.LayoutParams(
                        dp(46),
                        dp(46)
                )
        );

        return row;
    }

    private View buildBanner() {
        LinearLayout banner =
                new LinearLayout(this);

        banner.setOrientation(
                LinearLayout.VERTICAL
        );

        banner.setPadding(
                dp(17),
                dp(16),
                dp(17),
                dp(16)
        );

        banner.setBackground(
                gradient(
                        "#086BFF",
                        "#2EA2FF",
                        22
                )
        );

        banner.setElevation(dp(3));

        banner.addView(
                text(
                        "🛵 Driver Control Center",
                        18,
                        "#FFFFFF",
                        true
                )
        );

        banner.addView(
                text(
                        "Kelola akun driver bike dan car dengan status verifikasi terpusat.",
                        11,
                        "#EAF4FF",
                        false
                )
        );

        return banner;
    }

    private View buildSummaryCard() {
        LinearLayout card = whiteCard();

        summaryText =
                text(
                        "Memuat ringkasan driver...",
                        12,
                        "#64748B",
                        false
                );

        card.addView(
                text(
                        "Ringkasan",
                        16,
                        "#0B3A78",
                        true
                )
        );

        card.addView(
                summaryText,
                marginTop(7)
        );

        return card;
    }

    private View buildSearchAndActions() {
        LinearLayout card = whiteCard();

        card.addView(
                text(
                        "Cari dan Tambah Driver",
                        16,
                        "#0B3A78",
                        true
                )
        );

        searchInput =
                input(
                        "Cari username, nama, email, atau plat"
                );

        card.addView(
                searchInput,
                inputLp()
        );

        LinearLayout actions =
                new LinearLayout(this);

        Button search =
                outlineButton("Cari");

        search.setOnClickListener(
                view -> renderFiltered()
        );

        actions.addView(
                search,
                new LinearLayout.LayoutParams(
                        0,
                        dp(48),
                        1
                )
        );

        Button create =
                primaryButton("Tambah Driver");

        create.setOnClickListener(
                view -> showDriverForm(null)
        );

        LinearLayout.LayoutParams createLp =
                new LinearLayout.LayoutParams(
                        0,
                        dp(48),
                        1
                );

        createLp.setMargins(
                dp(8),
                0,
                0,
                0
        );

        actions.addView(
                create,
                createLp
        );

        card.addView(
                actions,
                marginTop(10)
        );

        return card;
    }

    private void loadDrivers() {
        if (loading) {
            return;
        }

        setLoading(true);

        new Thread(() -> {
            try {
                JSONObject response =
                        request(
                                "GET",
                                null
                        );

                if (
                        !response.optBoolean(
                                "success",
                                false
                        )
                ) {
                    throw new IllegalStateException(
                            response.optString(
                                    "message",
                                    "Gagal memuat driver"
                            )
                    );
                }

                JSONArray drivers =
                        response.optJSONArray(
                                "drivers"
                        );

                JSONObject summary =
                        response.optJSONObject(
                                "summary"
                        );

                final JSONArray finalDrivers =
                        drivers == null
                                ? new JSONArray()
                                : drivers;

                mainHandler.post(() -> {
                    cachedDrivers =
                            finalDrivers;

                    updateSummary(summary);
                    renderFiltered();
                    setLoading(false);
                });

            } catch (Exception error) {
                mainHandler.post(() -> {
                    setLoading(false);
                    showError(
                            "Gagal memuat driver",
                            readable(error)
                    );
                });
            }
        }).start();
    }

    private void updateSummary(
            JSONObject summary
    ) {
        if (summary == null) {
            summaryText.setText(
                    "Data ringkasan tidak tersedia."
            );
            return;
        }

        summaryText.setText(
                "Total "
                        + summary.optInt("total", 0)
                        + "  •  Bike "
                        + summary.optInt("bike", 0)
                        + "  •  Car "
                        + summary.optInt("car", 0)
                        + "\nVerified "
                        + summary.optInt("verified", 0)
                        + "  •  Pending "
                        + summary.optInt("pending", 0)
                        + "  •  Suspend "
                        + summary.optInt("suspended", 0)
        );
    }

    private void renderFiltered() {
        if (driverList == null) {
            return;
        }

        driverList.removeAllViews();

        String query =
                clean(
                        searchInput == null
                                ? ""
                                : searchInput
                                .getText()
                                .toString()
                ).toLowerCase(
                        Locale.ROOT
                );

        int visible = 0;

        for (
                int i = 0;
                i < cachedDrivers.length();
                i++
        ) {
            JSONObject driver =
                    cachedDrivers.optJSONObject(i);

            if (driver == null) {
                continue;
            }

            String searchable =
                    (
                            driver.optString("username")
                                    + " "
                                    + driver.optString("name")
                                    + " "
                                    + driver.optString("email")
                                    + " "
                                    + driver.optString("phone")
                                    + " "
                                    + driver.optString("plate")
                    ).toLowerCase(Locale.ROOT);

            if (
                    !query.isEmpty()
                            && !searchable.contains(
                            query
                    )
            ) {
                continue;
            }

            driverList.addView(
                    driverCard(driver),
                    cardListLp()
            );

            visible++;
        }

        if (visible == 0) {
            TextView empty =
                    text(
                            query.isEmpty()
                                    ? "Belum ada akun driver."
                                    : "Driver tidak ditemukan.",
                            12,
                            "#718096",
                            false
                    );

            empty.setGravity(Gravity.CENTER);
            empty.setPadding(
                    dp(12),
                    dp(28),
                    dp(12),
                    dp(28)
            );

            empty.setBackground(
                    roundStroke(
                            "#FFFFFF",
                            "#DCE8F6",
                            18,
                            1
                    )
            );

            driverList.addView(empty);
        }
    }

    private View driverCard(
            JSONObject driver
    ) {
        int userId =
                driver.optInt(
                        "user_id",
                        0
                );

        String username =
                first(
                        driver.optString("username"),
                        "-"
                );

        String name =
                first(
                        driver.optString("name"),
                        username
                );

        String type =
                normalizeType(
                        driver.optString(
                                "driver_type"
                        )
                );

        String status =
                normalizeStatus(
                        driver.optString(
                                "verification_status"
                        )
                );

        LinearLayout card = whiteCard();

        LinearLayout top =
                new LinearLayout(this);

        top.setGravity(
                Gravity.CENTER_VERTICAL
        );

        LinearLayout identity =
                new LinearLayout(this);

        identity.setOrientation(
                LinearLayout.VERTICAL
        );

        identity.addView(
                text(
                        name,
                        17,
                        "#0B3A78",
                        true
                )
        );

        identity.addView(
                text(
                        "@"
                                + username
                                + " • Driver "
                                + type,
                        10,
                        "#64748B",
                        false
                )
        );

        top.addView(
                identity,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        top.addView(
                statusBadge(status)
        );

        card.addView(top);

        card.addView(
                text(
                        "Email: "
                                + first(
                                driver.optString(
                                        "email"
                                ),
                                "-"
                        ),
                        11,
                        "#64748B",
                        false
                ),
                marginTop(10)
        );

        card.addView(
                text(
                        "HP: "
                                + first(
                                driver.optString(
                                        "phone"
                                ),
                                "-"
                        ),
                        11,
                        "#64748B",
                        false
                ),
                marginTop(3)
        );

        card.addView(
                text(
                        "Plat: "
                                + first(
                                driver.optString(
                                        "plate"
                                ),
                                "-"
                        )
                                + "  •  "
                                + (
                                driver.optInt(
                                        "is_online",
                                        0
                                ) == 1
                                        ? "Online"
                                        : "Offline"
                        ),
                        11,
                        "#64748B",
                        false
                ),
                marginTop(3)
        );

        LinearLayout firstActions =
                new LinearLayout(this);

        Button edit =
                outlineButton("Edit");

        edit.setOnClickListener(
                view -> showDriverForm(driver)
        );

        firstActions.addView(
                edit,
                actionLp(false)
        );

        Button verify =
                primaryButton(
                        "verified".equals(status)
                                ? "Set Pending"
                                : "Verifikasi"
                );

        verify.setOnClickListener(
                view -> confirmStatus(
                        userId,
                        username,
                        "verified".equals(status)
                                ? "pending"
                                : "verified"
                )
        );

        firstActions.addView(
                verify,
                actionLp(true)
        );

        card.addView(
                firstActions,
                marginTop(12)
        );

        LinearLayout secondActions =
                new LinearLayout(this);

        Button suspend =
                warningButton(
                        "suspended".equals(status)
                                ? "Aktifkan"
                                : "Suspend"
                );

        suspend.setOnClickListener(
                view -> confirmStatus(
                        userId,
                        username,
                        "suspended".equals(status)
                                ? "verified"
                                : "suspended"
                )
        );

        secondActions.addView(
                suspend,
                actionLp(false)
        );

        Button delete =
                dangerButton("Hapus");

        delete.setOnClickListener(
                view -> confirmDelete(
                        userId,
                        username
                )
        );

        secondActions.addView(
                delete,
                actionLp(true)
        );

        card.addView(
                secondActions,
                marginTop(8)
        );

        return card;
    }

    private void showDriverForm(
            JSONObject existing
    ) {
        boolean editing =
                existing != null;

        LinearLayout form =
                new LinearLayout(this);

        form.setOrientation(
                LinearLayout.VERTICAL
        );

        form.setPadding(
                dp(6),
                dp(4),
                dp(6),
                0
        );

        EditText username =
                input("Username");

        username.setText(
                editing
                        ? existing.optString(
                        "username"
                )
                        : ""
        );

        username.setEnabled(!editing);
        form.addView(username, inputLp());

        EditText name =
                input("Nama lengkap");

        name.setText(
                editing
                        ? existing.optString("name")
                        : ""
        );

        form.addView(name, inputLp());

        EditText email =
                input("Email");

        email.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType
                        .TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        );

        email.setText(
                editing
                        ? existing.optString("email")
                        : ""
        );

        form.addView(email, inputLp());

        EditText phone =
                input("Nomor HP");

        phone.setInputType(
                InputType.TYPE_CLASS_PHONE
        );

        phone.setText(
                editing
                        ? existing.optString("phone")
                        : ""
        );

        form.addView(phone, inputLp());

        EditText plate =
                input("Nomor polisi");

        plate.setText(
                editing
                        ? existing.optString("plate")
                        : ""
        );

        form.addView(plate, inputLp());

        Spinner driverType =
                new Spinner(this);

        String[] types = {
                "bike",
                "car"
        };

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout
                                .simple_spinner_dropdown_item,
                        types
                );

        driverType.setAdapter(adapter);

        if (
                editing
                        && "car".equalsIgnoreCase(
                        existing.optString(
                                "driver_type"
                        )
                )
        ) {
            driverType.setSelection(1);
        }

        form.addView(
                driverType,
                inputLp()
        );

        EditText password =
                input(
                        editing
                                ? "Password baru (opsional)"
                                : "Password"
                );

        password.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType
                        .TYPE_TEXT_VARIATION_PASSWORD
        );

        form.addView(password, inputLp());

        ScrollView container =
                new ScrollView(this);

        container.addView(form);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                editing
                                        ? "Edit Driver"
                                        : "Tambah Driver"
                        )
                        .setView(container)
                        .setNegativeButton(
                                "Batal",
                                null
                        )
                        .setPositiveButton(
                                "Simpan",
                                null
                        )
                        .create();

        dialog.setOnShowListener(
                ignored ->
                        dialog.getButton(
                                AlertDialog
                                        .BUTTON_POSITIVE
                        ).setOnClickListener(
                                view -> {
                                    String user =
                                            clean(
                                                    username
                                                            .getText()
                                                            .toString()
                                            );

                                    String pass =
                                            clean(
                                                    password
                                                            .getText()
                                                            .toString()
                                            );

                                    if (
                                            !editing
                                                    && user.length()
                                                    < 3
                                    ) {
                                        toast(
                                                "Username minimal 3 karakter"
                                        );
                                        return;
                                    }

                                    if (
                                            !editing
                                                    && pass.length()
                                                    < 6
                                    ) {
                                        toast(
                                                "Password minimal 6 karakter"
                                        );
                                        return;
                                    }

                                    JSONObject data =
                                            new JSONObject();

                                    try {
                                        data.put(
                                                "action",
                                                editing
                                                        ? "update"
                                                        : "create"
                                        );

                                        if (editing) {
                                            data.put(
                                                    "user_id",
                                                    existing.optInt(
                                                            "user_id"
                                                    )
                                            );
                                        }

                                        data.put(
                                                "username",
                                                user
                                        );

                                        data.put(
                                                "name",
                                                clean(
                                                        name
                                                                .getText()
                                                                .toString()
                                                )
                                        );

                                        data.put(
                                                "email",
                                                clean(
                                                        email
                                                                .getText()
                                                                .toString()
                                                )
                                        );

                                        data.put(
                                                "phone",
                                                clean(
                                                        phone
                                                                .getText()
                                                                .toString()
                                                )
                                        );

                                        data.put(
                                                "plate",
                                                clean(
                                                        plate
                                                                .getText()
                                                                .toString()
                                                )
                                        );

                                        data.put(
                                                "driver_type",
                                                String.valueOf(
                                                        driverType
                                                                .getSelectedItem()
                                                )
                                        );

                                        data.put(
                                                "password",
                                                pass
                                        );

                                    } catch (
                                            Exception error
                                    ) {
                                        toast(
                                                "Form tidak valid"
                                        );
                                        return;
                                    }

                                    dialog.dismiss();
                                    executeAction(data);
                                }
                        )
        );

        dialog.show();
    }

    private void confirmStatus(
            int userId,
            String username,
            String status
    ) {
        String title;

        if ("verified".equals(status)) {
            title = "Verifikasi Driver";
        } else if ("suspended".equals(status)) {
            title = "Suspend Driver";
        } else {
            title = "Ubah ke Pending";
        }

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(
                        "Terapkan status "
                                + status
                                + " untuk @"
                                + username
                                + "?"
                )
                .setNegativeButton(
                        "Batal",
                        null
                )
                .setPositiveButton(
                        "Lanjutkan",
                        (
                                dialog,
                                which
                        ) -> {
                            JSONObject data =
                                    new JSONObject();

                            try {
                                data.put(
                                        "action",
                                        "status"
                                );

                                data.put(
                                        "user_id",
                                        userId
                                );

                                data.put(
                                        "verification_status",
                                        status
                                );
                            } catch (
                                    Exception ignored
                            ) {
                            }

                            executeAction(data);
                        }
                )
                .show();
    }

    private void confirmDelete(
            int userId,
            String username
    ) {
        new AlertDialog.Builder(this)
                .setTitle("Hapus Driver")
                .setMessage(
                        "Akun @"
                                + username
                                + " akan dihapus permanen. Tindakan ini hanya berhasil jika driver tidak mempunyai order aktif."
                )
                .setNegativeButton(
                        "Batal",
                        null
                )
                .setPositiveButton(
                        "Hapus Permanen",
                        (
                                dialog,
                                which
                        ) -> {
                            JSONObject data =
                                    new JSONObject();

                            try {
                                data.put(
                                        "action",
                                        "delete"
                                );

                                data.put(
                                        "user_id",
                                        userId
                                );
                            } catch (
                                    Exception ignored
                            ) {
                            }

                            executeAction(data);
                        }
                )
                .show();
    }

    private void executeAction(
            JSONObject data
    ) {
        if (loading) {
            return;
        }

        setLoading(true);

        new Thread(() -> {
            try {
                JSONObject response =
                        request(
                                "POST",
                                data
                        );

                if (
                        !response.optBoolean(
                                "success",
                                false
                        )
                ) {
                    throw new IllegalStateException(
                            response.optString(
                                    "message",
                                    "Aksi gagal"
                            )
                    );
                }

                String message =
                        response.optString(
                                "message",
                                "Perubahan berhasil"
                        );

                mainHandler.post(() -> {
                    toast(message);
                    setLoading(false);
                    loadDrivers();
                });

            } catch (Exception error) {
                mainHandler.post(() -> {
                    setLoading(false);
                    showError(
                            "Proses gagal",
                            readable(error)
                    );
                });
            }
        }).start();
    }

    private JSONObject request(
            String method,
            JSONObject body
    ) throws Exception {
        URL url =
                new URL(
                        "GET".equals(method)
                                ? ENDPOINT
                                + "?v="
                                + System.currentTimeMillis()
                                : ENDPOINT
                );

        HttpURLConnection connection =
                (HttpURLConnection)
                        url.openConnection();

        connection.setConnectTimeout(
                CONNECT_TIMEOUT
        );

        connection.setReadTimeout(
                READ_TIMEOUT
        );

        connection.setRequestMethod(method);
        connection.setUseCaches(false);

        connection.setRequestProperty(
                "Accept",
                "application/json"
        );

        connection.setRequestProperty(
                "Authorization",
                "Bearer " + adminToken
        );

        if ("POST".equals(method)) {
            connection.setDoOutput(true);

            connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
            );

            byte[] bytes =
                    body.toString()
                            .getBytes(
                                    StandardCharsets.UTF_8
                            );

            connection.setFixedLengthStreamingMode(
                    bytes.length
            );

            try (
                    OutputStream output =
                            connection
                                    .getOutputStream()
            ) {
                output.write(bytes);
            }
        }

        int status =
                connection.getResponseCode();

        InputStream stream =
                status >= 200
                        && status < 400
                        ? connection.getInputStream()
                        : connection.getErrorStream();

        String response =
                readStream(stream);

        connection.disconnect();

        if (response.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Respons server kosong"
            );
        }

        JSONObject json =
                new JSONObject(response);

        if (status == 401) {
            throw new SecurityException(
                    json.optString(
                            "message",
                            "Sesi admin berakhir"
                    )
            );
        }

        return json;
    }

    private String readStream(
            InputStream stream
    ) throws Exception {
        if (stream == null) {
            return "";
        }

        StringBuilder result =
                new StringBuilder();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        stream,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {
            String line;

            while (
                    (line = reader.readLine())
                            != null
            ) {
                result.append(line);
            }
        }

        return result.toString();
    }

    private void showFatalSession() {
        new AlertDialog.Builder(this)
                .setTitle("Sesi Admin Tidak Valid")
                .setMessage(
                        "Silakan login kembali menggunakan akun admin."
                )
                .setCancelable(false)
                .setPositiveButton(
                        "Login",
                        (
                                dialog,
                                which
                        ) -> {
                            Intent intent =
                                    new Intent(
                                            this,
                                            LoginActivity.class
                                    );

                            intent.addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TASK
                            );

                            startActivity(intent);
                            finish();
                        }
                )
                .show();
    }

    private void setLoading(
            boolean value
    ) {
        loading = value;

        if (progress != null) {
            progress.setVisibility(
                    value
                            ? View.VISIBLE
                            : View.GONE
            );
        }
    }

    private LinearLayout whiteCard() {
        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(14),
                dp(14),
                dp(14),
                dp(14)
        );

        card.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#D7E6F8",
                        18,
                        1
                )
        );

        card.setElevation(dp(2));

        return card;
    }

    private TextView statusBadge(
            String status
    ) {
        String label;
        String background;
        String color;

        if ("verified".equals(status)) {
            label = "Verified";
            background = "#E7FFF2";
            color = "#0A8F4C";
        } else if ("suspended".equals(status)) {
            label = "Suspended";
            background = "#FFF0E5";
            color = "#B45309";
        } else if ("rejected".equals(status)) {
            label = "Rejected";
            background = "#FFECEC";
            color = "#C62828";
        } else {
            label = "Pending";
            background = "#FFF7E6";
            color = "#C96A05";
        }

        TextView badge =
                text(
                        label,
                        9,
                        color,
                        true
                );

        badge.setPadding(
                dp(9),
                dp(5),
                dp(9),
                dp(5)
        );

        badge.setBackground(
                round(
                        background,
                        13
                )
        );

        return badge;
    }

    private EditText input(
            String hint
    ) {
        EditText input =
                new EditText(this);

        input.setHint(hint);
        input.setTextSize(12);

        input.setSingleLine(true);

        input.setPadding(
                dp(13),
                0,
                dp(13),
                0
        );

        input.setBackground(
                roundStroke(
                        "#F8FBFF",
                        "#D8E5F3",
                        14,
                        1
                )
        );

        return input;
    }

    private LinearLayout.LayoutParams inputLp() {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(50)
                );

        lp.setMargins(
                0,
                dp(9),
                0,
                0
        );

        return lp;
    }

    private Button primaryButton(
            String value
    ) {
        Button button =
                baseButton(value);

        button.setTextColor(Color.WHITE);

        button.setBackground(
                gradient(
                        "#086BFF",
                        "#2EA2FF",
                        14
                )
        );

        return button;
    }

    private Button outlineButton(
            String value
    ) {
        Button button =
                baseButton(value);

        button.setTextColor(
                Color.parseColor("#0B7CFF")
        );

        button.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#A9D1FF",
                        14,
                        1
                )
        );

        return button;
    }

    private Button warningButton(
            String value
    ) {
        Button button =
                baseButton(value);

        button.setTextColor(
                Color.parseColor("#8A4A00")
        );

        button.setBackground(
                roundStroke(
                        "#FFF7E6",
                        "#F4C77C",
                        14,
                        1
                )
        );

        return button;
    }

    private Button dangerButton(
            String value
    ) {
        Button button =
                baseButton(value);

        button.setTextColor(Color.WHITE);

        button.setBackground(
                gradient(
                        "#EF4444",
                        "#DC2626",
                        14
                )
        );

        return button;
    }

    private Button baseButton(
            String value
    ) {
        Button button =
                new Button(this);

        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(11);

        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        return button;
    }

    private TextView text(
            String value,
            int size,
            String color,
            boolean bold
    ) {
        TextView view =
                new TextView(this);

        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(
                Color.parseColor(color)
        );

        view.setIncludeFontPadding(false);

        if (bold) {
            view.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return view;
    }

    private GradientDrawable round(
            String fill,
            int radius
    ) {
        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(
                Color.parseColor(fill)
        );

        drawable.setCornerRadius(
                dp(radius)
        );

        return drawable;
    }

    private GradientDrawable roundStroke(
            String fill,
            String stroke,
            int radius,
            int width
    ) {
        GradientDrawable drawable =
                round(
                        fill,
                        radius
                );

        drawable.setStroke(
                dp(width),
                Color.parseColor(stroke)
        );

        return drawable;
    }

    private GradientDrawable gradient(
            String start,
            String end,
            int radius
    ) {
        GradientDrawable drawable =
                new GradientDrawable(
                        GradientDrawable
                                .Orientation
                                .LEFT_RIGHT,
                        new int[]{
                                Color.parseColor(start),
                                Color.parseColor(end)
                        }
                );

        drawable.setCornerRadius(
                dp(radius)
        );

        return drawable;
    }

    private LinearLayout.LayoutParams marginTop(
            int top
    ) {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        lp.setMargins(
                0,
                dp(top),
                0,
                0
        );

        return lp;
    }

    private LinearLayout.LayoutParams cardListLp() {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        lp.setMargins(
                0,
                0,
                0,
                dp(10)
        );

        return lp;
    }

    private LinearLayout.LayoutParams actionLp(
            boolean margin
    ) {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        0,
                        dp(44),
                        1
                );

        if (margin) {
            lp.setMargins(
                    dp(8),
                    0,
                    0,
                    0
            );
        }

        return lp;
    }

    private String normalizeType(
            String value
    ) {
        return "car".equalsIgnoreCase(
                clean(value)
        )
                ? "Car"
                : "Bike";
    }

    private String normalizeStatus(
            String value
    ) {
        String status =
                clean(value)
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (
                "verified".equals(status)
                        || "suspended".equals(status)
                        || "rejected".equals(status)
        ) {
            return status;
        }

        return "pending";
    }

    private String first(
            String... values
    ) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            String clean = clean(value);

            if (!clean.isEmpty()) {
                return clean;
            }
        }

        return "";
    }

    private String clean(
            String value
    ) {
        if (value == null) {
            return "";
        }

        value = value.trim();

        if (
                value.isEmpty()
                        || "null".equalsIgnoreCase(
                        value
                )
                        || "undefined".equalsIgnoreCase(
                        value
                )
        ) {
            return "";
        }

        return value;
    }

    private String readable(
            Exception error
    ) {
        return first(
                error == null
                        ? ""
                        : error.getMessage(),
                "Terjadi kesalahan"
        );
    }

    private void showError(
            String title,
            String message
    ) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(
                        "OK",
                        null
                )
                .show();
    }

    private void toast(
            String message
    ) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }

    private int dp(
            int value
    ) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
