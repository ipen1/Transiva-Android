package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
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
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class ProfileActivity extends Activity {

    private static final String BASE_URL =
            "https://transiva.my.id/server/";

    private static final String PROFILE_URL =
            BASE_URL + "get_customer_profile.php";

    private static final String UPDATE_URL =
            BASE_URL + "update_customer_profile.php";

    private static final int REQUEST_GALLERY = 5101;
    private static final int TIMEOUT_MS = 30000;

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private SessionManager session;

    private ImageView avatarView;
    private TextView nameView;
    private TextView usernameView;
    private TextView emailView;
    private TextView emailBadge;
    private TextView phoneView;
    private TextView roleView;

    private EditText usernameInput;
    private EditText phoneInput;
    private EditText addressInput;
    private EditText passwordInput;

    private Button photoButton;
    private Button saveButton;
    private Button logoutButton;
    private ProgressBar progress;

    private String userId = "";
    private String username = "";
    private String email = "";
    private String phone = "";
    private String address = "";
    private String photoUrl = "";
    private boolean emailVerified;
    private boolean loading;

    private byte[] pendingPhotoWebp;

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

        readSession();
        setContentView(buildScreen());
        loadProfile();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (
                avatarView != null
                        && !loading
        ) {
            loadProfile();
        }
    }

    private void readSession() {
        try {
            userId = first(
                    session.getId(),
                    session.getUserId()
            );

            username = first(
                    session.getUsername(),
                    session.getName(),
                    "Customer"
            );

            JSONObject data =
                    session.getSessionJson();

            email = first(
                    data.optString("email"),
                    session.get("email")
            );

            phone = first(
                    data.optString("phone"),
                    data.optString("phone_number"),
                    data.optString("no_hp"),
                    session.get("phone"),
                    session.get("phone_number")
            );

            address = first(
                    data.optString(
                            "delivery_address"
                    ),
                    session.get(
                            "delivery_address"
                    )
            );

            photoUrl = first(
                    data.optString(
                            "profile_photo"
                    ),
                    data.optString("photo"),
                    session.get("profile_photo")
            );

            emailVerified =
                    data.optInt(
                            "email_verified",
                            0
                    ) == 1;

        } catch (Exception ignored) {
        }
    }

    private View buildScreen() {
        FrameLayout page =
                new FrameLayout(this);

        page.setBackgroundColor(
                Color.parseColor("#F5F8FD")
        );

        LinearLayout shell =
                new LinearLayout(this);

        shell.setOrientation(
                LinearLayout.VERTICAL
        );

        page.addView(
                shell,
                new FrameLayout.LayoutParams(
                        -1,
                        -1
                )
        );

        ScrollView scroll =
                new ScrollView(this);

        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        shell.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(
                dp(14),
                dp(14),
                dp(14),
                dp(24)
        );

        scroll.addView(
                root,
                new ScrollView.LayoutParams(
                        -1,
                        -2
                )
        );

        buildHeader(root);
        buildIdentityCard(root);
        buildFormCard(root);
        buildSecurityCard(root);

        shell.addView(
                buildBottomNavigation(),
                new LinearLayout.LayoutParams(
                        -1,
                        dp(66)
                )
        );

        progress =
                new ProgressBar(this);

        progress.setVisibility(
                View.GONE
        );

        FrameLayout.LayoutParams progressLp =
                new FrameLayout.LayoutParams(
                        dp(48),
                        dp(48)
                );

        progressLp.gravity =
                Gravity.CENTER;

        page.addView(
                progress,
                progressLp
        );

        return page;
    }

    private void buildHeader(
            LinearLayout root
    ) {
        LinearLayout row =
                new LinearLayout(this);

        row.setGravity(
                Gravity.CENTER_VERTICAL
        );

        LinearLayout title =
                new LinearLayout(this);

        title.setOrientation(
                LinearLayout.VERTICAL
        );

        title.addView(
                text(
                        "Akun",
                        24,
                        "#0B3A78",
                        true
                )
        );

        title.addView(
                text(
                        "Kelola identitas dan keamanan akun",
                        11,
                        "#718096",
                        false
                )
        );

        row.addView(
                title,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        TextView refresh =
                text(
                        "↻",
                        25,
                        "#0B7CFF",
                        true
                );

        refresh.setGravity(
                Gravity.CENTER
        );

        refresh.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#DCE8F6",
                        16,
                        1
                )
        );

        refresh.setOnClickListener(
                view -> loadProfile()
        );

        row.addView(
                refresh,
                new LinearLayout.LayoutParams(
                        dp(44),
                        dp(44)
                )
        );

        root.addView(row);
    }

    private void buildIdentityCard(
            LinearLayout root
    ) {
        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setGravity(
                Gravity.CENTER_HORIZONTAL
        );

        card.setPadding(
                dp(18),
                dp(22),
                dp(18),
                dp(18)
        );

        card.setBackground(
                gradient(
                        "#075EF4",
                        "#25A7FF",
                        22
                )
        );

        card.setElevation(dp(3));

        LinearLayout.LayoutParams cardLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        cardLp.setMargins(
                0,
                dp(14),
                0,
                dp(14)
        );

        root.addView(card, cardLp);

        FrameLayout avatarFrame =
                new FrameLayout(this);

        avatarFrame.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#FFFFFF",
                        50,
                        3
                )
        );

        avatarView =
                new ImageView(this);

        avatarView.setScaleType(
                ImageView.ScaleType.CENTER_CROP
        );

        avatarView.setImageResource(
                android.R.drawable
                        .sym_def_app_icon
        );

        FrameLayout.LayoutParams avatarLp =
                new FrameLayout.LayoutParams(
                        dp(92),
                        dp(92)
                );

        avatarLp.gravity =
                Gravity.CENTER;

        avatarFrame.addView(
                avatarView,
                avatarLp
        );

        card.addView(
                avatarFrame,
                new LinearLayout.LayoutParams(
                        dp(98),
                        dp(98)
                )
        );

        photoButton =
                outlineLightButton(
                        "Ubah Foto Profil"
                );

        LinearLayout.LayoutParams photoLp =
                new LinearLayout.LayoutParams(
                        -2,
                        dp(40)
                );

        photoLp.setMargins(
                0,
                dp(10),
                0,
                dp(12)
        );

        card.addView(
                photoButton,
                photoLp
        );

        photoButton.setOnClickListener(
                view -> openGallery()
        );

        nameView =
                text(
                        username,
                        20,
                        "#FFFFFF",
                        true
                );

        nameView.setGravity(
                Gravity.CENTER
        );

        card.addView(nameView);

        usernameView =
                text(
                        "@" + username,
                        11,
                        "#EAF5FF",
                        false
                );

        usernameView.setGravity(
                Gravity.CENTER
        );

        card.addView(usernameView);

        LinearLayout badges =
                new LinearLayout(this);

        badges.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams badgesLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        badgesLp.setMargins(
                0,
                dp(12),
                0,
                0
        );

        card.addView(
                badges,
                badgesLp
        );

        emailBadge =
                badge(
                        emailVerified
                                ? "✓ Email Terverifikasi"
                                : "Email Belum Terverifikasi",
                        emailVerified
                                ? "#E7FFF2"
                                : "#FFF4E5",
                        emailVerified
                                ? "#0A8F4C"
                                : "#C96A05"
                );

        badges.addView(emailBadge);

        roleView =
                badge(
                        "Customer",
                        "#FFFFFF22",
                        "#FFFFFF"
                );

        LinearLayout.LayoutParams roleLp =
                new LinearLayout.LayoutParams(
                        -2,
                        -2
                );

        roleLp.setMargins(
                dp(7),
                0,
                0,
                0
        );

        badges.addView(
                roleView,
                roleLp
        );
    }

    private void buildFormCard(
            LinearLayout root
    ) {
        LinearLayout card =
                whiteCard();

        card.setPadding(
                dp(16),
                dp(16),
                dp(16),
                dp(16)
        );

        card.addView(
                sectionTitle(
                        "Informasi Akun",
                        "Data utama akun Transiva"
                )
        );

        card.addView(
                label("Username")
        );

        usernameInput =
                input(
                        "Username",
                        InputType.TYPE_CLASS_TEXT
                );

        usernameInput.setText(username);

        card.addView(
                usernameInput,
                fieldLp()
        );

        card.addView(
                label("Email")
        );

        emailView =
                readonlyField(
                        first(
                                email,
                                "Email belum tersedia"
                        )
                );

        card.addView(
                emailView,
                fieldLp()
        );

        card.addView(
                label("Nomor HP")
        );

        phoneInput =
                input(
                        "Contoh: 081234567890",
                        InputType.TYPE_CLASS_PHONE
                );

        phoneInput.setText(phone);

        card.addView(
                phoneInput,
                fieldLp()
        );

        phoneView = phoneInput;

        card.addView(
                label("Alamat Delivery")
        );

        addressInput =
                input(
                        "Alamat lengkap untuk layanan Transiva",
                        InputType.TYPE_CLASS_TEXT
                                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                );

        addressInput.setSingleLine(false);
        addressInput.setMinLines(3);
        addressInput.setGravity(
                Gravity.TOP | Gravity.START
        );

        addressInput.setText(address);

        LinearLayout.LayoutParams addressLp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(94)
                );

        addressLp.setMargins(
                0,
                0,
                0,
                dp(12)
        );

        card.addView(
                addressInput,
                addressLp
        );

        saveButton =
                primaryButton(
                        "Simpan Perubahan"
                );

        saveButton.setOnClickListener(
                view -> saveProfile()
        );

        card.addView(
                saveButton,
                buttonLp()
        );

        root.addView(
                card,
                sectionLp()
        );
    }

    private void buildSecurityCard(
            LinearLayout root
    ) {
        LinearLayout card =
                whiteCard();

        card.setPadding(
                dp(16),
                dp(16),
                dp(16),
                dp(16)
        );

        card.addView(
                sectionTitle(
                        "Keamanan",
                        "Gunakan password yang kuat dan unik"
                )
        );

        card.addView(
                label("Password Baru")
        );

        passwordInput =
                input(
                        "Kosongkan jika tidak diganti",
                        InputType.TYPE_CLASS_TEXT
                                | InputType.TYPE_TEXT_VARIATION_PASSWORD
                );

        card.addView(
                passwordInput,
                fieldLp()
        );

        Button appSettings =
                outlineButton(
                        "Buka Pengaturan Aplikasi"
                );

        appSettings.setOnClickListener(
                view -> {
                    Intent intent =
                            new Intent(
                                    Settings
                                            .ACTION_APPLICATION_DETAILS_SETTINGS
                            );

                    intent.setData(
                            Uri.parse(
                                    "package:"
                                            + getPackageName()
                            )
                    );

                    startActivity(intent);
                }
        );

        card.addView(
                appSettings,
                buttonLp()
        );

        logoutButton =
                dangerButton(
                        "Keluar dari Akun"
                );

        logoutButton.setOnClickListener(
                view -> confirmLogout()
        );

        card.addView(
                logoutButton,
                buttonLp()
        );

        root.addView(
                card,
                sectionLp()
        );
    }

    private void openGallery() {
        Intent intent =
                new Intent(
                        Intent.ACTION_OPEN_DOCUMENT
                );

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        intent.setType(
                "image/*"
        );

        startActivityForResult(
                intent,
                REQUEST_GALLERY
        );
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (
                requestCode != REQUEST_GALLERY
                        || resultCode != RESULT_OK
                        || data == null
                        || data.getData() == null
        ) {
            return;
        }

        Uri uri = data.getData();

        setLoading(true);

        new Thread(() -> {
            try {
                byte[] image =
                        createProfileWebp(uri);

                Bitmap preview =
                        BitmapFactory
                                .decodeByteArray(
                                        image,
                                        0,
                                        image.length
                                );

                mainHandler.post(() -> {
                    pendingPhotoWebp = image;
                    avatarView.setImageBitmap(
                            preview
                    );

                    setLoading(false);
                    toast(
                            "Foto siap disimpan"
                    );
                });

            } catch (Exception error) {
                mainHandler.post(() -> {
                    setLoading(false);

                    showInfo(
                            "Foto Gagal",
                            first(
                                    error.getMessage(),
                                    "Foto tidak dapat diproses."
                            )
                    );
                });
            }
        }).start();
    }

    private byte[] createProfileWebp(
            Uri uri
    ) throws Exception {
        BitmapFactory.Options bounds =
                new BitmapFactory.Options();

        bounds.inJustDecodeBounds = true;

        try (
                InputStream stream =
                        getContentResolver()
                                .openInputStream(uri)
        ) {
            BitmapFactory.decodeStream(
                    stream,
                    null,
                    bounds
            );
        }

        int sample = 1;

        while (
                bounds.outWidth / sample > 1400
                        || bounds.outHeight / sample > 1400
        ) {
            sample *= 2;
        }

        BitmapFactory.Options options =
                new BitmapFactory.Options();

        options.inSampleSize =
                Math.max(1, sample);

        options.inPreferredConfig =
                Bitmap.Config.ARGB_8888;

        Bitmap bitmap;

        try (
                InputStream stream =
                        getContentResolver()
                                .openInputStream(uri)
        ) {
            bitmap =
                    BitmapFactory.decodeStream(
                            stream,
                            null,
                            options
                    );
        }

        if (bitmap == null) {
            throw new IllegalStateException(
                    "Foto tidak dapat dibaca"
            );
        }

        int side =
                Math.min(
                        bitmap.getWidth(),
                        bitmap.getHeight()
                );

        int left =
                (bitmap.getWidth() - side) / 2;

        int top =
                (bitmap.getHeight() - side) / 2;

        Bitmap square =
                Bitmap.createBitmap(
                        bitmap,
                        left,
                        top,
                        side,
                        side
                );

        Bitmap resized =
                Bitmap.createScaledBitmap(
                        square,
                        720,
                        720,
                        true
                );

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        resized.compress(
                Bitmap.CompressFormat.WEBP,
                86,
                output
        );

        if (square != bitmap) {
            square.recycle();
        }

        if (resized != square) {
            resized.recycle();
        }

        if (!bitmap.isRecycled()) {
            bitmap.recycle();
        }

        return output.toByteArray();
    }

    private void loadProfile() {
        if (
                loading
                        || userId.isEmpty()
        ) {
            return;
        }

        setLoading(true);

        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                URL url =
                        new URL(
                                PROFILE_URL
                                        + "?id="
                                        + Uri.encode(
                                        userId
                                )
                                        + "&_="
                                        + System
                                        .currentTimeMillis()
                        );

                connection =
                        (HttpURLConnection)
                                url.openConnection();

                connection.setConnectTimeout(
                        TIMEOUT_MS
                );

                connection.setReadTimeout(
                        TIMEOUT_MS
                );

                connection.setRequestProperty(
                        "Accept",
                        "application/json"
                );

                String body =
                        readStream(
                                connection
                                        .getInputStream()
                        );

                JSONObject response =
                        new JSONObject(body);

                if (
                        !response.optBoolean(
                                "success",
                                false
                        )
                ) {
                    throw new IllegalStateException(
                            response.optString(
                                    "message",
                                    "Profil tidak dapat dimuat."
                            )
                    );
                }

                JSONObject user =
                        response.optJSONObject(
                                "user"
                        );

                if (user == null) {
                    throw new IllegalStateException(
                            "Data profil kosong"
                    );
                }

                mainHandler.post(() -> {
                    applyUser(user);
                    setLoading(false);
                });

            } catch (Exception error) {
                mainHandler.post(() -> {
                    setLoading(false);

                    toast(
                            first(
                                    error.getMessage(),
                                    "Gagal memuat profil"
                            )
                    );
                });

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private void applyUser(
            JSONObject user
    ) {
        username = first(
                user.optString("username"),
                username
        );

        email = first(
                user.optString("email"),
                email
        );

        phone = first(
                user.optString("phone"),
                user.optString(
                        "phone_number"
                ),
                user.optString("no_hp"),
                phone
        );

        address = first(
                user.optString(
                        "delivery_address"
                ),
                address
        );

        photoUrl = first(
                user.optString(
                        "profile_photo"
                ),
                user.optString("photo"),
                photoUrl
        );

        emailVerified =
                user.optInt(
                        "email_verified",
                        0
                ) == 1;

        nameView.setText(username);
        usernameView.setText(
                "@" + username
        );

        usernameInput.setText(username);
        emailView.setText(
                first(
                        email,
                        "Email belum tersedia"
                )
        );

        phoneInput.setText(phone);
        addressInput.setText(address);

        emailBadge.setText(
                emailVerified
                        ? "✓ Email Terverifikasi"
                        : "Email Belum Terverifikasi"
        );

        emailBadge.setTextColor(
                Color.parseColor(
                        emailVerified
                                ? "#0A8F4C"
                                : "#C96A05"
                )
        );

        emailBadge.setBackground(
                round(
                        emailVerified
                                ? "#E7FFF2"
                                : "#FFF4E5",
                        14
                )
        );

        if (!photoUrl.isEmpty()) {
            loadRemoteImage(photoUrl);
        }

        try {
            session.saveUser(user);
        } catch (Exception ignored) {
        }
    }

    private void loadRemoteImage(
            String rawUrl
    ) {
        String fixed =
                absoluteUrl(rawUrl);

        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                connection =
                        (HttpURLConnection)
                                new URL(fixed)
                                        .openConnection();

                connection.setConnectTimeout(
                        20000
                );

                connection.setReadTimeout(
                        25000
                );

                Bitmap bitmap =
                        BitmapFactory.decodeStream(
                                connection
                                        .getInputStream()
                        );

                if (bitmap != null) {
                    mainHandler.post(
                            () -> avatarView
                                    .setImageBitmap(
                                            bitmap
                                    )
                    );
                }

            } catch (Exception ignored) {
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private void saveProfile() {
        if (loading) {
            return;
        }

        String newUsername =
                usernameInput.getText()
                        .toString()
                        .trim();

        String newPhone =
                phoneInput.getText()
                        .toString()
                        .replaceAll(
                                "[^0-9+]",
                                ""
                        )
                        .trim();

        String newAddress =
                addressInput.getText()
                        .toString()
                        .trim();

        String newPassword =
                passwordInput.getText()
                        .toString()
                        .trim();

        if (newUsername.length() < 3) {
            usernameInput.setError(
                    "Minimal 3 karakter"
            );
            return;
        }

        if (
                !newPhone.isEmpty()
                        && newPhone.length() < 9
        ) {
            phoneInput.setError(
                    "Nomor HP tidak valid"
            );
            return;
        }

        if (newAddress.isEmpty()) {
            addressInput.setError(
                    "Alamat wajib diisi"
            );
            return;
        }

        if (
                !newPassword.isEmpty()
                        && newPassword.length() < 8
        ) {
            passwordInput.setError(
                    "Password minimal 8 karakter"
            );
            return;
        }

        setLoading(true);

        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                String boundary =
                        "----TransivaProfile"
                                + System
                                .currentTimeMillis();

                connection =
                        (HttpURLConnection)
                                new URL(UPDATE_URL)
                                        .openConnection();

                connection.setRequestMethod(
                        "POST"
                );

                connection.setConnectTimeout(
                        TIMEOUT_MS
                );

                connection.setReadTimeout(
                        TIMEOUT_MS
                );

                connection.setDoOutput(true);

                connection.setRequestProperty(
                        "Content-Type",
                        "multipart/form-data; boundary="
                                + boundary
                );

                connection.setRequestProperty(
                        "Accept",
                        "application/json"
                );

                try (
                        OutputStream output =
                                connection
                                        .getOutputStream()
                ) {
                    writeField(
                            output,
                            boundary,
                            "id",
                            userId
                    );

                    writeField(
                            output,
                            boundary,
                            "username",
                            newUsername
                    );

                    writeField(
                            output,
                            boundary,
                            "phone",
                            newPhone
                    );

                    writeField(
                            output,
                            boundary,
                            "delivery_address",
                            newAddress
                    );

                    writeField(
                            output,
                            boundary,
                            "password",
                            newPassword
                    );

                    if (
                            pendingPhotoWebp != null
                                    && pendingPhotoWebp.length > 0
                    ) {
                        writeFile(
                                output,
                                boundary,
                                "profile_photo",
                                "profile.webp",
                                "image/webp",
                                pendingPhotoWebp
                        );
                    }

                    output.write(
                            (
                                    "--"
                                            + boundary
                                            + "--\r\n"
                            ).getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );
                }

                int code =
                        connection
                                .getResponseCode();

                InputStream stream =
                        code >= 200
                                && code < 400
                                ? connection
                                .getInputStream()
                                : connection
                                .getErrorStream();

                String body =
                        readStream(stream);

                JSONObject response =
                        new JSONObject(body);

                if (
                        !response.optBoolean(
                                "success",
                                false
                        )
                ) {
                    throw new IllegalStateException(
                            response.optString(
                                    "message",
                                    "Profil gagal disimpan."
                            )
                    );
                }

                JSONObject user =
                        response.optJSONObject(
                                "user"
                        );

                mainHandler.post(() -> {
                    pendingPhotoWebp = null;

                    if (user != null) {
                        applyUser(user);
                    }

                    passwordInput.setText("");
                    setLoading(false);

                    showInfo(
                            "Profil Disimpan",
                            "Informasi akun berhasil diperbarui."
                    );
                });

            } catch (Exception error) {
                mainHandler.post(() -> {
                    setLoading(false);

                    showInfo(
                            "Gagal Menyimpan",
                            first(
                                    error.getMessage(),
                                    "Periksa koneksi internet."
                            )
                    );
                });

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private void writeField(
            OutputStream output,
            String boundary,
            String name,
            String value
    ) throws Exception {
        String block =
                "--"
                        + boundary
                        + "\r\n"
                        + "Content-Disposition: form-data; name=\""
                        + name
                        + "\"\r\n\r\n"
                        + (
                        value == null
                                ? ""
                                : value
                )
                        + "\r\n";

        output.write(
                block.getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }

    private void writeFile(
            OutputStream output,
            String boundary,
            String name,
            String filename,
            String mime,
            byte[] data
    ) throws Exception {
        String header =
                "--"
                        + boundary
                        + "\r\n"
                        + "Content-Disposition: form-data; name=\""
                        + name
                        + "\"; filename=\""
                        + filename
                        + "\"\r\n"
                        + "Content-Type: "
                        + mime
                        + "\r\n\r\n";

        output.write(
                header.getBytes(
                        StandardCharsets.UTF_8
                )
        );

        output.write(data);
        output.write(
                "\r\n".getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }

    private View buildBottomNavigation() {
        LinearLayout nav =
                new LinearLayout(this);

        nav.setOrientation(
                LinearLayout.HORIZONTAL
        );

        nav.setGravity(Gravity.CENTER);

        nav.setPadding(
                dp(5),
                dp(4),
                dp(5),
                dp(4)
        );

        nav.setBackgroundColor(
                Color.WHITE
        );

        nav.setElevation(dp(8));

        nav.addView(
                navItem(
                        "Beranda",
                        "ic_nav_home",
                        CustomerDashboardActivity.class,
                        false
                ),
                navLp()
        );

        nav.addView(
                navItem(
                        "Aktivitas",
                        "ic_nav_activity",
                        CustomerHistoryActivity.class,
                        false
                ),
                navLp()
        );

        nav.addView(
                navItem(
                        "Pesan",
                        "ic_nav_chat",
                        CustomerChatActivity.class,
                        false
                ),
                navLp()
        );

        nav.addView(
                navItem(
                        "Transaksi",
                        "ic_nav_wallet",
                        CustomerBalanceHistoryActivity.class,
                        false
                ),
                navLp()
        );

        nav.addView(
                navItem(
                        "Akun",
                        "ic_nav_profile",
                        null,
                        true
                ),
                navLp()
        );

        return nav;
    }

    private LinearLayout.LayoutParams navLp() {
        return new LinearLayout.LayoutParams(
                0,
                -1,
                1
        );
    }

    private View navItem(
            String label,
            String iconName,
            Class<?> target,
            boolean active
    ) {
        LinearLayout item =
                new LinearLayout(this);

        item.setOrientation(
                LinearLayout.VERTICAL
        );

        item.setGravity(
                Gravity.CENTER
        );

        ImageView icon =
                new ImageView(this);

        int resource =
                drawable(iconName);

        if (resource != 0) {
            icon.setImageResource(resource);
        }

        icon.setAlpha(
                active
                        ? 1f
                        : 0.62f
        );

        item.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(23),
                        dp(23)
                )
        );

        TextView title =
                text(
                        label,
                        9,
                        active
                                ? "#0B7CFF"
                                : "#64748B",
                        active
                );

        title.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams titleLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        titleLp.setMargins(
                0,
                dp(2),
                0,
                0
        );

        item.addView(
                title,
                titleLp
        );

        if (target != null) {
            item.setOnClickListener(
                    view -> {
                        Intent intent =
                                new Intent(
                                        this,
                                        target
                                );

                        intent.addFlags(
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        );

                        startActivity(intent);
                    }
            );
        }

        return item;
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Keluar Akun")
                .setMessage(
                        "Yakin ingin keluar dari akun Transiva?"
                )
                .setNegativeButton(
                        "Batal",
                        null
                )
                .setPositiveButton(
                        "Keluar",
                        (dialog, which) ->
                                logout()
                )
                .show();
    }

    private void logout() {
        if (loading) {
            return;
        }

        setLoading(true);

        NativeLogoutClient
                .logoutAndDeleteToken(
                        this,
                        (success, response) -> {
                            try {
                                session.logout();
                            } catch (Exception ignored) {
                            }

                            Intent intent =
                                    new Intent(
                                            this,
                                            LoginActivity.class
                                    );

                            intent.addFlags(
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                                            | Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TASK
                            );

                            startActivity(intent);
                            finish();
                        }
                );
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

        if (usernameInput != null) {
            usernameInput.setEnabled(!value);
        }

        if (phoneInput != null) {
            phoneInput.setEnabled(!value);
        }

        if (addressInput != null) {
            addressInput.setEnabled(!value);
        }

        if (passwordInput != null) {
            passwordInput.setEnabled(!value);
        }

        if (photoButton != null) {
            photoButton.setEnabled(!value);
        }

        if (saveButton != null) {
            saveButton.setEnabled(!value);
        }

        if (logoutButton != null) {
            logoutButton.setEnabled(!value);
        }
    }

    private LinearLayout whiteCard() {
        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#E0EAF5",
                        20,
                        1
                )
        );

        card.setElevation(dp(1));

        return card;
    }

    private View sectionTitle(
            String title,
            String subtitle
    ) {
        LinearLayout box =
                new LinearLayout(this);

        box.setOrientation(
                LinearLayout.VERTICAL
        );

        box.addView(
                text(
                        title,
                        16,
                        "#0B3A78",
                        true
                )
        );

        box.addView(
                text(
                        subtitle,
                        10,
                        "#718096",
                        false
                )
        );

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        lp.setMargins(
                0,
                0,
                0,
                dp(12)
        );

        box.setLayoutParams(lp);

        return box;
    }

    private TextView label(
            String value
    ) {
        TextView label =
                text(
                        value,
                        11,
                        "#334E6F",
                        true
                );

        label.setPadding(
                dp(2),
                0,
                0,
                dp(6)
        );

        return label;
    }

    private EditText input(
            String hint,
            int type
    ) {
        EditText field =
                new EditText(this);

        field.setHint(hint);
        field.setTextSize(14);

        field.setTextColor(
                Color.parseColor("#0F172A")
        );

        field.setHintTextColor(
                Color.parseColor("#94A3B8")
        );

        field.setInputType(type);

        field.setPadding(
                dp(14),
                0,
                dp(14),
                0
        );

        field.setBackground(
                roundStroke(
                        "#F9FBFE",
                        "#D7E4F2",
                        14,
                        1
                )
        );

        field.setImeOptions(
                EditorInfo.IME_ACTION_NEXT
        );

        return field;
    }

    private TextView readonlyField(
            String value
    ) {
        TextView field =
                text(
                        value,
                        14,
                        "#52667F",
                        false
                );

        field.setGravity(
                Gravity.CENTER_VERTICAL
        );

        field.setPadding(
                dp(14),
                0,
                dp(14),
                0
        );

        field.setSingleLine(true);

        field.setEllipsize(
                TextUtils.TruncateAt.END
        );

        field.setBackground(
                roundStroke(
                        "#F1F5F9",
                        "#D9E3EE",
                        14,
                        1
                )
        );

        return field;
    }

    private TextView badge(
            String value,
            String background,
            String color
    ) {
        TextView badge =
                text(
                        value,
                        9,
                        color,
                        true
                );

        badge.setGravity(
                Gravity.CENTER
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
                        14
                )
        );

        return badge;
    }

    private Button primaryButton(
            String value
    ) {
        Button button =
                new Button(this);

        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(13);

        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        button.setTextColor(
                Color.WHITE
        );

        button.setBackground(
                gradient(
                        "#086BFF",
                        "#2EA2FF",
                        15
                )
        );

        return button;
    }

    private Button outlineButton(
            String value
    ) {
        Button button =
                primaryButton(value);

        button.setTextColor(
                Color.parseColor("#0B7CFF")
        );

        button.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#A8D1FF",
                        15,
                        1
                )
        );

        return button;
    }

    private Button outlineLightButton(
            String value
    ) {
        Button button =
                primaryButton(value);

        button.setTextSize(11);

        button.setBackground(
                roundStroke(
                        "#FFFFFF22",
                        "#FFFFFF99",
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
                primaryButton(value);

        button.setBackground(
                gradient(
                        "#EF4444",
                        "#DC2626",
                        15
                )
        );

        return button;
    }

    private LinearLayout.LayoutParams sectionLp() {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        lp.setMargins(
                0,
                0,
                0,
                dp(14)
        );

        return lp;
    }

    private LinearLayout.LayoutParams fieldLp() {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(50)
                );

        lp.setMargins(
                0,
                0,
                0,
                dp(12)
        );

        return lp;
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(50)
                );

        lp.setMargins(
                0,
                0,
                0,
                dp(10)
        );

        return lp;
    }

    private GradientDrawable round(
            String color,
            int radius
    ) {
        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(
                Color.parseColor(color)
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
                round(fill, radius);

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

    private TextView text(
            String value,
            int size,
            String color,
            boolean bold
    ) {
        TextView view =
                new TextView(this);

        view.setText(
                value == null
                        ? ""
                        : value
        );

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

    private int drawable(
            String name
    ) {
        return getResources()
                .getIdentifier(
                        name,
                        "drawable",
                        getPackageName()
                );
    }

    private String absoluteUrl(
            String value
    ) {
        String clean =
                first(value);

        if (
                clean.startsWith("http://")
                        || clean.startsWith(
                        "https://"
                )
        ) {
            return clean;
        }

        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }

        return "https://transiva.my.id/"
                + clean;
    }

    private String readStream(
            InputStream stream
    ) throws Exception {
        if (stream == null) {
            return "";
        }

        StringBuilder builder =
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
                builder.append(line);
            }
        }

        return builder.toString();
    }

    private String first(
            String... values
    ) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (
                    value != null
                            && !value.trim()
                            .isEmpty()
                            && !"null"
                            .equalsIgnoreCase(
                                    value.trim()
                            )
                            && !"undefined"
                            .equalsIgnoreCase(
                                    value.trim()
                            )
            ) {
                return value.trim();
            }
        }

        return "";
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

    private void showInfo(
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
}
