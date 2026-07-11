package com.transiva.app;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;

import android.graphics.drawable.GradientDrawable;

import android.view.Space;
import android.view.View;
import android.view.ViewOutlineProvider;

import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
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

import java.util.Locale;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminMoneyManagementActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";

    private LinearLayout list;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    /*
     * Digunakan untuk request API dan download gambar.
     * Semua proses jaringan dijalankan di background thread.
     */
    private final ExecutorService executor =
            Executors.newFixedThreadPool(4);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(
                Color.parseColor("#071426")
        );

        getWindow().setNavigationBarColor(
                Color.parseColor("#071426")
        );

        build();
        load();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        /*
         * Menghentikan pekerjaan jaringan ketika Activity ditutup.
         */
        executor.shutdownNow();
    }


    private void build() {

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(
                dp(16),
                dp(20),
                dp(16),
                dp(24)
        );

        root.setBackgroundColor(
                Color.parseColor("#F3F8FF")
        );

        scrollView.addView(
                root,
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT
                )
        );

        setContentView(scrollView);


        root.addView(
                txt(
                        "🏦 Money Management",
                        22,
                        "#0B3A78",
                        true
                )
        );

        root.addView(
                txt(
                        "Kelola deposit customer Transiva",
                        13,
                        "#64748B",
                        false
                )
        );


        Space space = new Space(this);

        root.addView(
                space,
                new LinearLayout.LayoutParams(
                        1,
                        dp(14)
                )
        );


        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(
                dp(16),
                dp(16),
                dp(16),
                dp(16)
        );

        banner.setBackground(gradient());

        banner.addView(
                txt(
                        "Transiva Pay Control",
                        17,
                        "#FFFFFF",
                        true
                )
        );

        banner.addView(
                txt(
                        "Approve dan monitor transaksi deposit",
                        12,
                        "#EAF4FF",
                        false
                )
        );

        root.addView(
                banner,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );


        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams listParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        listParams.setMargins(
                0,
                dp(14),
                0,
                dp(10)
        );

        root.addView(list, listParams);


        Button back = new Button(this);
        back.setText("Kembali");
        back.setAllCaps(false);
        back.setTextColor(
                Color.parseColor("#0B3A78")
        );

        /*
         * Mencegah warna bawaan tema menimpa drawable.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            back.setBackgroundTintList(null);
        }

        back.setBackground(outline());

        back.setOnClickListener(view -> finish());

        root.addView(
                back,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(48)
                )
        );
    }


    /**
     * Mengambil daftar deposit dari server.
     */
    private void load() {

        if (list == null) {
            return;
        }

        list.removeAllViews();

        list.addView(
                txt(
                        "Memuat deposit...",
                        14,
                        "#64748B",
                        false
                )
        );


        executor.execute(() -> {

            try {

                JSONObject response = getJson(
                        BASE_URL + "server/getDeposits.php"
                );

                boolean success =
                        response.optBoolean("success", false);

                JSONArray deposits;

                if (success) {

                    deposits =
                            response.optJSONArray("deposits");

                    if (deposits == null) {
                        deposits = new JSONArray();
                    }

                } else {

                    deposits = new JSONArray();
                }


                final JSONArray finalDeposits = deposits;

                final String errorMessage =
                        success
                                ? null
                                : response.optString(
                                        "message",
                                        "Gagal mengambil daftar deposit"
                                );


                handler.post(() -> {

                    if (!activityIsActive()) {
                        return;
                    }

                    render(finalDeposits);

                    if (errorMessage != null) {
                        Toast.makeText(
                                this,
                                errorMessage,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });

            } catch (Exception exception) {

                final String message =
                        readableError(exception);

                handler.post(() -> {

                    if (!activityIsActive()) {
                        return;
                    }

                    render(new JSONArray());

                    Toast.makeText(
                            this,
                            "Gagal memuat deposit: " + message,
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }


    private void render(JSONArray deposits) {

        list.removeAllViews();

        if (deposits == null || deposits.length() == 0) {

            list.addView(
                    txt(
                            "Belum ada deposit",
                            14,
                            "#64748B",
                            false
                    )
            );

            return;
        }


        for (int i = 0; i < deposits.length(); i++) {

            JSONObject deposit =
                    deposits.optJSONObject(i);

            if (deposit != null) {
                addCard(deposit);
            }
        }
    }


    private void addCard(JSONObject deposit) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                dp(14),
                dp(14),
                dp(14),
                dp(14)
        );

        card.setBackground(cardBg());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(dp(2));
        }


        String username =
                deposit.optString("username", "-");

        double amount =
                deposit.optDouble("amount", 0);

        String status =
                deposit.optString("status", "-");

        String createdAt =
                deposit.optString("created_at", "-");

        String proofImage =
                deposit.optString("proof_image", "");

        int depositId =
                deposit.optInt("id", 0);


        card.addView(
                txt(
                        username,
                        16,
                        "#0B3A78",
                        true
                )
        );

        card.addView(
                txt(
                        rupiah(amount),
                        19,
                        "#086BFF",
                        true
                )
        );

        card.addView(
                txt(
                        "Status : " + status,
                        12,
                        "#64748B",
                        false
                )
        );

        card.addView(
                txt(
                        "Waktu : " + createdAt,
                        12,
                        "#64748B",
                        false
                )
        );


        /*
         * Menampilkan gambar bukti transfer.
         */
        addProofImage(card, proofImage);


        if ("PENDING".equalsIgnoreCase(status)) {

            Button approveButton = createActionButton(
                    "✓ Approve Deposit",
                    green()
            );

            Button rejectButton = createActionButton(
                    "✕ Reject",
                    red()
            );


            approveButton.setOnClickListener(view -> {

                if (depositId <= 0) {

                    Toast.makeText(
                            this,
                            "ID deposit tidak valid",
                            Toast.LENGTH_SHORT
                    ).show();

                    return;
                }

                action(
                        "approveDeposit.php",
                        depositId,
                        approveButton,
                        rejectButton,
                        "Deposit berhasil disetujui"
                );
            });


            rejectButton.setOnClickListener(view -> {

                if (depositId <= 0) {

                    Toast.makeText(
                            this,
                            "ID deposit tidak valid",
                            Toast.LENGTH_SHORT
                    ).show();

                    return;
                }

                action(
                        "rejectDeposit.php",
                        depositId,
                        approveButton,
                        rejectButton,
                        "Deposit berhasil ditolak"
                );
            });


            LinearLayout.LayoutParams approveParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(48)
                    );

            approveParams.setMargins(
                    0,
                    dp(12),
                    0,
                    dp(8)
            );

            card.addView(
                    approveButton,
                    approveParams
            );


            LinearLayout.LayoutParams rejectParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(48)
                    );

            rejectParams.setMargins(
                    0,
                    0,
                    0,
                    0
            );

            card.addView(
                    rejectButton,
                    rejectParams
            );
        }


        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                0,
                0,
                dp(10)
        );

        list.addView(card, cardParams);
    }


    /**
     * Membuat ImageView dan mengunduh gambar bukti di background.
     */
    private void addProofImage(
            LinearLayout card,
            String proofImage
    ) {

        String imageUrl =
                buildProofImageUrl(proofImage);

        if (imageUrl == null) {

            TextView unavailable = txt(
                    "Bukti transfer tidak tersedia",
                    12,
                    "#94A3B8",
                    false
            );

            LinearLayout.LayoutParams unavailableParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            unavailableParams.setMargins(
                    0,
                    dp(8),
                    0,
                    0
            );

            card.addView(
                    unavailable,
                    unavailableParams
            );

            return;
        }


        ImageView proofView = new ImageView(this);

        proofView.setContentDescription(
                "Bukti transfer deposit"
        );

        /*
         * Icon sementara selama gambar dimuat.
         */
        proofView.setImageResource(
                android.R.drawable.ic_menu_gallery
        );

        proofView.setScaleType(
                ImageView.ScaleType.CENTER
        );

        proofView.setBackground(
                roundedColor("#E8EEF7", 12)
        );


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            proofView.setOutlineProvider(
                    ViewOutlineProvider.BACKGROUND
            );

            proofView.setClipToOutline(true);
        }


        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(210)
                );

        imageParams.setMargins(
                0,
                dp(10),
                0,
                0
        );

        card.addView(
                proofView,
                imageParams
        );


        executor.execute(() -> {

            try {

                Bitmap bitmap =
                        downloadBitmap(imageUrl);

                if (bitmap == null) {
                    throw new IOException(
                            "File gambar tidak dapat dibaca"
                    );
                }


                handler.post(() -> {

                    if (!activityIsActive()) {
                        return;
                    }

                    proofView.setScaleType(
                            ImageView.ScaleType.CENTER_CROP
                    );

                    proofView.setImageBitmap(bitmap);
                });

            } catch (Exception exception) {

                handler.post(() -> {

                    if (!activityIsActive()) {
                        return;
                    }

                    proofView.setScaleType(
                            ImageView.ScaleType.CENTER
                    );

                    proofView.setImageResource(
                            android.R.drawable.ic_dialog_alert
                    );

                    proofView.setContentDescription(
                            "Gagal memuat bukti transfer"
                    );
                });
            }
        });
    }


    /**
     * Mendukung nilai proof_image berikut:
     *
     * uploads/bukti.jpg
     * server/uploads/bukti.jpg
     * /server/uploads/bukti.jpg
     * https://domain.com/uploads/bukti.jpg
     */
    private String buildProofImageUrl(
            String proofImage
    ) {

        if (proofImage == null) {
            return null;
        }

        String path = proofImage.trim();

        if (path.isEmpty()
                || "null".equalsIgnoreCase(path)) {

            return null;
        }


        /*
         * Server terkadang mengembalikan backslash.
         */
        path = path.replace("\\", "/");


        if (path.startsWith("http://")
                || path.startsWith("https://")) {

            return path.replace(" ", "%20");
        }


        while (path.startsWith("/")) {
            path = path.substring(1);
        }


        String result;

        if (path.startsWith("server/")) {

            result = BASE_URL + path;

        } else {

            /*
             * Sama dengan JavaScript:
             * src="server/${deposit.proof_image}"
             */
            result = BASE_URL + "server/" + path;
        }


        return result.replace(" ", "%20");
    }


    /**
     * Menjalankan approve atau reject.
     */
    private void action(
            String api,
            int id,
            Button approveButton,
            Button rejectButton,
            String defaultSuccessMessage
    ) {

        approveButton.setEnabled(false);
        rejectButton.setEnabled(false);

        approveButton.setAlpha(0.6f);
        rejectButton.setAlpha(0.6f);


        executor.execute(() -> {

            try {

                JSONObject requestBody =
                        new JSONObject();

                requestBody.put("id", id);


                JSONObject response =
                        postJson(
                                BASE_URL + "server/" + api,
                                requestBody
                        );


                boolean success =
                        response.optBoolean(
                                "success",
                                false
                        );

                String message =
                        response.optString(
                                "message",
                                success
                                        ? defaultSuccessMessage
                                        : "Proses gagal"
                        );


                handler.post(() -> {

                    if (!activityIsActive()) {
                        return;
                    }

                    Toast.makeText(
                            this,
                            message,
                            success
                                    ? Toast.LENGTH_SHORT
                                    : Toast.LENGTH_LONG
                    ).show();


                    if (success) {

                        /*
                         * Muat ulang agar status berubah dari PENDING.
                         */
                        load();

                    } else {

                        enableButtons(
                                approveButton,
                                rejectButton
                        );
                    }
                });

            } catch (Exception exception) {

                final String message =
                        readableError(exception);

                handler.post(() -> {

                    if (!activityIsActive()) {
                        return;
                    }

                    enableButtons(
                            approveButton,
                            rejectButton
                    );

                    Toast.makeText(
                            this,
                            "Terjadi kesalahan: " + message,
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }


    private void enableButtons(
            Button approveButton,
            Button rejectButton
    ) {

        approveButton.setEnabled(true);
        rejectButton.setEnabled(true);

        approveButton.setAlpha(1f);
        rejectButton.setAlpha(1f);
    }


    /**
     * GET JSON.
     */
    private JSONObject getJson(
            String url
    ) throws Exception {

        return requestJson(
                "GET",
                url,
                null
        );
    }


    /**
     * POST dengan format application/json.
     */
    private JSONObject postJson(
            String url,
            JSONObject data
    ) throws Exception {

        return requestJson(
                "POST",
                url,
                data
        );
    }


    /**
     * Method jaringan utama.
     *
     * Berbeda dari kode lama, method ini:
     * - menutup OutputStream;
     * - memanggil getResponseCode();
     * - membaca respons JSON;
     * - membaca error response;
     * - memutus koneksi.
     */
    private JSONObject requestJson(
            String method,
            String url,
            JSONObject data
    ) throws Exception {

        HttpURLConnection connection = null;

        try {

            connection =
                    (HttpURLConnection)
                            new URL(url).openConnection();

            connection.setRequestMethod(method);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);

            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );


            if (data != null) {

                byte[] body =
                        data.toString().getBytes(
                                StandardCharsets.UTF_8
                        );

                connection.setDoOutput(true);

                connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );

                connection.setFixedLengthStreamingMode(
                        body.length
                );


                try (OutputStream outputStream =
                             connection.getOutputStream()) {

                    outputStream.write(body);
                    outputStream.flush();
                }
            }


            /*
             * Memaksa request benar-benar dikirim.
             */
            int responseCode =
                    connection.getResponseCode();


            InputStream inputStream;

            if (responseCode >= 200
                    && responseCode < 300) {

                inputStream =
                        connection.getInputStream();

            } else {

                inputStream =
                        connection.getErrorStream();
            }


            String responseBody =
                    readStream(inputStream);


            if (responseBody.trim().isEmpty()) {

                throw new IOException(
                        "Respons server kosong. HTTP "
                                + responseCode
                );
            }


            JSONObject response;

            try {

                response =
                        new JSONObject(responseBody);

            } catch (Exception exception) {

                throw new IOException(
                        "Respons server bukan JSON: "
                                + limitText(responseBody)
                );
            }


            if (responseCode < 200
                    || responseCode >= 300) {

                throw new IOException(
                        response.optString(
                                "message",
                                "Server mengembalikan HTTP "
                                        + responseCode
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


    private Bitmap downloadBitmap(
            String imageUrl
    ) throws Exception {

        HttpURLConnection connection = null;

        try {

            connection =
                    (HttpURLConnection)
                            new URL(imageUrl).openConnection();

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setUseCaches(true);
            connection.setInstanceFollowRedirects(true);

            connection.setRequestProperty(
                    "Accept",
                    "image/*"
            );


            int responseCode =
                    connection.getResponseCode();

            if (responseCode < 200
                    || responseCode >= 300) {

                throw new IOException(
                        "Gambar mengembalikan HTTP "
                                + responseCode
                );
            }


            try (InputStream inputStream =
                         connection.getInputStream()) {

                return BitmapFactory.decodeStream(
                        inputStream
                );
            }

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }


    private String readStream(
            InputStream inputStream
    ) throws IOException {

        if (inputStream == null) {
            return "";
        }


        StringBuilder result =
                new StringBuilder();


        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        inputStream,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            String line;

            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }


        return result.toString();
    }


    private Button createActionButton(
            String text,
            GradientDrawable background
    ) {

        Button button = new Button(this);

        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setTypeface(null, Typeface.BOLD);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setBackgroundTintList(null);
        }

        button.setBackground(background);

        return button;
    }


    private TextView txt(
            String text,
            int size,
            String color,
            boolean bold
    ) {

        TextView textView = new TextView(this);

        textView.setText(text);
        textView.setTextSize(size);
        textView.setTextColor(
                Color.parseColor(color)
        );

        if (bold) {
            textView.setTypeface(
                    null,
                    Typeface.BOLD
            );
        }

        textView.setPadding(
                0,
                dp(3),
                0,
                dp(3)
        );

        return textView;
    }


    private GradientDrawable cardBg() {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dp(20));

        drawable.setStroke(
                dp(1),
                Color.parseColor("#D7E6F8")
        );

        return drawable;
    }


    private GradientDrawable gradient() {

        GradientDrawable drawable =
                new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{
                                Color.parseColor("#086BFF"),
                                Color.parseColor("#2EA2FF")
                        }
                );

        drawable.setCornerRadius(dp(22));

        return drawable;
    }


    private GradientDrawable outline() {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dp(18));

        drawable.setStroke(
                dp(1),
                Color.parseColor("#9DCAFF")
        );

        return drawable;
    }


    private GradientDrawable green() {
        return roundedColor(
                "#16A34A",
                18
        );
    }


    private GradientDrawable red() {
        return roundedColor(
                "#EF4444",
                18
        );
    }


    private GradientDrawable roundedColor(
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


    private String rupiah(double number) {

        return "Rp " +
                NumberFormat.getInstance(
                        new Locale("id", "ID")
                ).format(number);
    }


    private String readableError(
            Exception exception
    ) {

        String message =
                exception.getMessage();

        if (message == null
                || message.trim().isEmpty()) {

            return exception
                    .getClass()
                    .getSimpleName();
        }

        return message;
    }


    private String limitText(String text) {

        if (text == null) {
            return "";
        }

        if (text.length() <= 150) {
            return text;
        }

        return text.substring(0, 150) + "...";
    }


    private boolean activityIsActive() {

        if (isFinishing()) {
            return false;
        }

        return Build.VERSION.SDK_INT
                < Build.VERSION_CODES.JELLY_BEAN_MR1
                || !isDestroyed();
    }


    private int dp(int value) {

        return (int) (
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}