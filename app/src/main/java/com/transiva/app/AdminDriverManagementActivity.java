package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.ImageView;
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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

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

    private static final int REQ_PICK_DRIVER_MEDIA = 7301;
    private DriverMediaSelection activeMediaSelection;
    private String pendingMediaField = "";

    private static final class MediaUpload {
        final byte[] bytes;
        final String mimeType;
        final String fileName;

        MediaUpload(byte[] bytes, String mimeType, String fileName) {
            this.bytes = bytes;
            this.mimeType = mimeType;
            this.fileName = fileName;
        }
    }

    private static final class DriverMediaSelection {
        final Map<String, MediaUpload> uploads = new LinkedHashMap<>();
        final Map<String, ImageView> previews = new LinkedHashMap<>();
    }

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
                                    + " " + driver.optString("bpjs_number")
                                    + " " + driver.optString("bpjs_name")
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

        boolean bpjsActive = readFlag(driver, "bpjs_active", "bpjs_is_active");
        TextView bpjsLine = text(
                "BPJS Ketenagakerjaan: " + (bpjsActive ? "Aktif" : "Tidak Aktif")
                        + (clean(driver.optString("bpjs_number")).isEmpty() ? "" : " • " + driver.optString("bpjs_number")),
                11,
                bpjsActive ? "#0A8F4C" : "#C62828",
                true
        );
        card.addView(bpjsLine, marginTop(7));

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

        Button bpjs = outlineButton("Kelola BPJS Ketenagakerjaan");
        bpjs.setOnClickListener(view -> showBpjsForm(driver));
        LinearLayout.LayoutParams bpjsLp = new LinearLayout.LayoutParams(-1, dp(46));
        bpjsLp.setMargins(0, dp(8), 0, 0);
        card.addView(bpjs, bpjsLp);

        return card;
    }

    private void showBpjsForm(JSONObject driver) {
        if (driver == null) return;

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(4), dp(4), dp(2));

        TextView info = text(
                "Driver: " + first(driver.optString("name"), driver.optString("username"), "-")
                        + "\nAktifkan kepesertaan lalu lengkapi data sesuai BPJS Ketenagakerjaan.",
                11, "#64748B", false
        );
        form.addView(info);

        Spinner active = new Spinner(this);
        String[] activeOptions = new String[]{"Tidak Aktif", "Aktif"};
        ArrayAdapter<String> activeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, activeOptions);
        active.setAdapter(activeAdapter);
        active.setSelection(readFlag(driver, "bpjs_active", "bpjs_is_active") ? 1 : 0);
        form.addView(active, inputLp());

        EditText number = input("Nomor BPJS Ketenagakerjaan");
        number.setText(first(driver.optString("bpjs_number"), driver.optString("bpjs_no")));
        form.addView(number, inputLp());

        EditText name = input("Nama peserta sesuai BPJS");
        name.setText(first(driver.optString("bpjs_name"), driver.optString("name")));
        form.addView(name, inputLp());

        EditText birthPlace = input("Tempat lahir");
        birthPlace.setText(driver.optString("bpjs_birth_place"));
        form.addView(birthPlace, inputLp());

        EditText birthDate = input("Tanggal lahir (YYYY-MM-DD)");
        birthDate.setText(driver.optString("bpjs_birth_date"));
        form.addView(birthDate, inputLp());

        EditText registeredSince = input("Terdaftar sejak (YYYY-MM-DD)");
        registeredSince.setText(driver.optString("bpjs_registered_since"));
        form.addView(registeredSince, inputLp());

        EditText note = input("Catatan / kelengkapan dokumen");
        note.setSingleLine(false);
        note.setMinLines(2);
        note.setText(driver.optString("bpjs_note"));
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, dp(76));
        noteLp.setMargins(0, dp(9), 0, 0);
        form.addView(note, noteLp);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("BPJS Ketenagakerjaan")
                .setView(scroll)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            boolean enabled = active.getSelectedItemPosition() == 1;
            String bpjsNumber = clean(number.getText().toString());
            String bpjsName = clean(name.getText().toString());

            if (enabled && (bpjsNumber.isEmpty() || bpjsName.isEmpty())) {
                toast("Nomor BPJS dan nama peserta wajib diisi saat status Aktif");
                return;
            }

            JSONObject data = new JSONObject();
            try {
                data.put("action", "update_bpjs");
                data.put("user_id", driver.optInt("user_id", 0));
                data.put("bpjs_active", enabled ? 1 : 0);
                data.put("bpjs_number", bpjsNumber);
                data.put("bpjs_name", bpjsName);
                data.put("bpjs_birth_place", clean(birthPlace.getText().toString()));
                data.put("bpjs_birth_date", clean(birthDate.getText().toString()));
                data.put("bpjs_registered_since", clean(registeredSince.getText().toString()));
                data.put("bpjs_note", clean(note.getText().toString()));
            } catch (Exception e) {
                toast("Data BPJS tidak valid");
                return;
            }

            dialog.dismiss();
            executeAction(data);
        }));

        dialog.show();
    }

    private void showDriverForm(JSONObject existing) {
        boolean editing = existing != null;
        DriverMediaSelection media = new DriverMediaSelection();
        activeMediaSelection = media;

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(6), dp(4), dp(6), 0);

        EditText username = input("Username");
        username.setText(editing ? existing.optString("username") : "");
        username.setEnabled(!editing);
        form.addView(username, inputLp());

        EditText name = input("Nama lengkap");
        name.setText(editing ? existing.optString("name") : "");
        form.addView(name, inputLp());

        EditText email = input("Email");
        email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        email.setText(editing ? existing.optString("email") : "");
        form.addView(email, inputLp());

        EditText phone = input("Nomor HP");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        phone.setText(editing ? existing.optString("phone") : "");
        form.addView(phone, inputLp());

        EditText plate = input("Nomor polisi");
        plate.setText(editing ? existing.optString("plate") : "");
        form.addView(plate, inputLp());

        Spinner driverType = new Spinner(this);
        String[] types = {"bike", "car"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, types);
        driverType.setAdapter(adapter);
        if (editing && "car".equalsIgnoreCase(existing.optString("driver_type"))) {
            driverType.setSelection(1);
        }
        form.addView(driverType, inputLp());

        EditText password = input(editing ? "Password baru (opsional)" : "Password");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(password, inputLp());

        form.addView(text(editing
                        ? "Foto aktif dapat diganti, tetapi tidak dapat dihapus."
                        : "Tambahkan ketiga foto untuk melengkapi data driver.",
                11, "#64748B", false), marginTop(14));

        form.addView(buildMediaPicker(media, "driver_photo", "Foto Driver",
                editing ? existing.optString("driver_photo") : "", editing), marginTop(9));
        form.addView(buildMediaPicker(media, "vehicle_photo", "Foto Kendaraan",
                editing ? existing.optString("vehicle_photo") : "", editing), marginTop(9));
        form.addView(buildMediaPicker(media, "ktp_photo", "Foto KTP",
                editing ? existing.optString("ktp_photo") : "", editing), marginTop(9));

        ScrollView container = new ScrollView(this);
        container.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing ? "Edit Driver" : "Tambah Driver")
                .setView(container)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", null)
                .create();

        dialog.setOnDismissListener(ignored -> {
            if (activeMediaSelection == media) activeMediaSelection = null;
        });

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String user = clean(username.getText().toString());
                    String pass = clean(password.getText().toString());
                    if (!editing && user.length() < 3) {
                        toast("Username minimal 3 karakter");
                        return;
                    }
                    if (!editing && pass.length() < 6) {
                        toast("Password minimal 6 karakter");
                        return;
                    }
                    if (!editing && (!media.uploads.containsKey("driver_photo")
                            || !media.uploads.containsKey("vehicle_photo")
                            || !media.uploads.containsKey("ktp_photo"))) {
                        toast("Foto driver, kendaraan, dan KTP wajib dipilih");
                        return;
                    }

                    JSONObject data = new JSONObject();
                    try {
                        data.put("action", editing ? "update" : "create");
                        if (editing) data.put("user_id", existing.optInt("user_id"));
                        data.put("username", user);
                        data.put("name", clean(name.getText().toString()));
                        data.put("email", clean(email.getText().toString()));
                        data.put("phone", clean(phone.getText().toString()));
                        data.put("plate", clean(plate.getText().toString()));
                        data.put("driver_type", String.valueOf(driverType.getSelectedItem()));
                        data.put("password", pass);
                    } catch (Exception error) {
                        toast("Form tidak valid");
                        return;
                    }

                    dialog.dismiss();
                    executeAction(data, media.uploads);
                }));
        dialog.show();
    }

    private View buildMediaPicker(DriverMediaSelection media, String field,
                                  String label, String currentUrl, boolean editing) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(10), dp(10), dp(10), dp(10));
        box.setBackground(roundStroke("#F8FBFF", "#D8E5F3", 14, 1));

        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackground(round("#E8F1FC", 12));
        preview.setClipToOutline(true);
        media.previews.put(field, preview);
        box.addView(preview, new LinearLayout.LayoutParams(dp(74), dp(74)));

        String url = absoluteMediaUrl(currentUrl);
        if (!url.isEmpty()) {
            RemoteImageLoader.loadCenterCrop(preview, url, android.R.drawable.ic_menu_gallery);
        } else {
            preview.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(11), 0, 0, 0);
        copy.addView(text(label, 13, "#0B3A78", true));
        copy.addView(text(editing && !url.isEmpty() ? "Foto aktif • pilih untuk mengganti" : "Pilih gambar dari perangkat",
                10, "#64748B", false), marginTop(4));
        Button choose = outlineButton(editing && !url.isEmpty() ? "Ubah Foto" : "Pilih Foto");
        choose.setOnClickListener(v -> openMediaPicker(media, field));
        LinearLayout.LayoutParams chooseLp = new LinearLayout.LayoutParams(-1, dp(42));
        chooseLp.setMargins(0, dp(7), 0, 0);
        copy.addView(choose, chooseLp);
        box.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        return box;
    }

    private void openMediaPicker(DriverMediaSelection media, String field) {
        activeMediaSelection = media;
        pendingMediaField = field;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Pilih foto"), REQ_PICK_DRIVER_MEDIA);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_DRIVER_MEDIA || resultCode != RESULT_OK
                || data == null || data.getData() == null || activeMediaSelection == null) return;
        Uri uri = data.getData();
        String field = pendingMediaField;
        new Thread(() -> {
            try {
                MediaUpload upload = prepareImage(uri, field);
                mainHandler.post(() -> {
                    if (activeMediaSelection == null) return;
                    activeMediaSelection.uploads.put(field, upload);
                    ImageView preview = activeMediaSelection.previews.get(field);
                    if (preview != null) {
                        Bitmap bitmap = BitmapFactory.decodeByteArray(upload.bytes, 0, upload.bytes.length);
                        if (bitmap != null) preview.setImageBitmap(bitmap);
                    }
                    toast("Foto dipilih dan dioptimalkan");
                });
            } catch (Exception error) {
                mainHandler.post(() -> showError("Foto tidak dapat dipakai", readable(error)));
            }
        }).start();
    }

    private MediaUpload prepareImage(Uri uri, String field) throws Exception {
        long originalSize = -1L;
        try (android.database.Cursor cursor = getContentResolver().query(uri,
                new String[]{android.provider.OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) originalSize = cursor.getLong(index);
            }
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IllegalArgumentException("File bukan gambar yang valid");
        }

        boolean alreadySmall = originalSize > 0 && originalSize <= 350L * 1024L
                && bounds.outWidth <= 1600 && bounds.outHeight <= 1600;
        if (alreadySmall) {
            byte[] raw = readImageBytes(uri, 2L * 1024L * 1024L);
            String mime = first(getContentResolver().getType(uri), "image/jpeg");
            return new MediaUpload(raw, mime, field + imageExtension(mime));
        }

        int sample = 1;
        while (bounds.outWidth / sample > 2200 || bounds.outHeight / sample > 2200) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(input, null, options);
        }
        if (bitmap == null) throw new IllegalArgumentException("Gambar gagal dibaca");

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scale = Math.min(1f, 1600f / Math.max(width, height));
        Bitmap resized = scale < 1f
                ? Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)), true)
                : bitmap;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int quality = 84;
        resized.compress(Bitmap.CompressFormat.JPEG, quality, out);
        while (out.size() > 500 * 1024 && quality > 58) {
            quality -= 7;
            out.reset();
            resized.compress(Bitmap.CompressFormat.JPEG, quality, out);
        }
        if (resized != bitmap) resized.recycle();
        bitmap.recycle();
        return new MediaUpload(out.toByteArray(), "image/jpeg", field + ".jpg");
    }

    private byte[] readImageBytes(Uri uri, long maxBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalArgumentException("File tidak bisa dibaca");
            byte[] buffer = new byte[8192];
            int length;
            long total = 0;
            while ((length = input.read(buffer)) != -1) {
                total += length;
                if (total > maxBytes) throw new IllegalArgumentException("Ukuran foto terlalu besar");
                out.write(buffer, 0, length);
            }
        }
        return out.toByteArray();
    }

    private String imageExtension(String mime) {
        String value = clean(mime).toLowerCase(Locale.ROOT);
        if (value.contains("png")) return ".png";
        if (value.contains("webp")) return ".webp";
        return ".jpg";
    }

    private String absoluteMediaUrl(String value) {
        String clean = clean(value);
        if (clean.isEmpty()) return "";
        if (clean.startsWith("http://") || clean.startsWith("https://")) return clean;
        while (clean.startsWith("/")) clean = clean.substring(1);
        return "https://transiva.my.id/" + clean;
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

    private void executeAction(JSONObject data) {
        executeAction(data, null);
    }

    private void executeAction(JSONObject data, Map<String, MediaUpload> uploads) {
        if (loading) {
            return;
        }

        setLoading(true);

        new Thread(() -> {
            try {
                JSONObject response = uploads != null && !uploads.isEmpty()
                        ? requestMultipart(data, uploads)
                        : request("POST", data);

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

    private JSONObject requestMultipart(JSONObject body, Map<String, MediaUpload> uploads) throws Exception {
        String boundary = "----TransivaDriver" + System.currentTimeMillis();
        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT + 20000);
        connection.setRequestMethod("POST");
        connection.setUseCaches(false);
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + adminToken);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream output = connection.getOutputStream()) {
            java.util.Iterator<String> keys = body.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                writeMultipartText(output, boundary, key, String.valueOf(body.opt(key)));
            }
            for (Map.Entry<String, MediaUpload> entry : uploads.entrySet()) {
                MediaUpload upload = entry.getValue();
                String header = "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"" + entry.getKey()
                        + "\"; filename=\"" + upload.fileName + "\"\r\n"
                        + "Content-Type: " + upload.mimeType + "\r\n\r\n";
                output.write(header.getBytes(StandardCharsets.UTF_8));
                output.write(upload.bytes);
                output.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 400
                ? connection.getInputStream() : connection.getErrorStream();
        String response = readStream(stream);
        connection.disconnect();
        if (response.trim().isEmpty()) throw new IllegalStateException("Respons server kosong");
        JSONObject json = new JSONObject(response);
        if (status == 401) throw new SecurityException(json.optString("message", "Sesi admin berakhir"));
        return json;
    }

    private void writeMultipartText(OutputStream output, String boundary,
                                    String name, String value) throws Exception {
        String part = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n";
        output.write(part.getBytes(StandardCharsets.UTF_8));
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

    private boolean readFlag(JSONObject object, String... keys) {
        if (object == null || keys == null) return false;
        for (String key : keys) {
            if (!object.has(key) || object.isNull(key)) continue;
            Object value = object.opt(key);
            if (value instanceof Boolean) return (Boolean) value;
            if (value instanceof Number) return ((Number) value).intValue() == 1;
            String text = clean(String.valueOf(value)).toLowerCase(Locale.ROOT);
            if ("1".equals(text) || "true".equals(text) || "active".equals(text) || "aktif".equals(text) || "yes".equals(text)) return true;
        }
        return false;
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
