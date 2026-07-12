package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
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
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Locale;

public class AdminPromoManagementActivity extends Activity {

    private static final String API =
            "https://transiva.my.id/server/admin_manage_promos.php";

    private LinearLayout listBox;
    private ProgressBar loading;

    private EditText titleInput;
    private EditText descriptionInput;
    private EditText codeInput;
    private PromoImagePickerHelper promoImagePicker;
    private EditText startInput;
    private EditText endInput;
    private EditText sortInput;
    private Spinner targetSpinner;
    private CheckBox activeCheck;
    private CheckBox notifyCheck;

    private int editingId = 0;
    private boolean requestRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#071426"));
        getWindow().setNavigationBarColor(Color.parseColor("#071426"));
        setContentView(buildScreen());
        loadPromos();
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(15), dp(16), dp(15), dp(28));
        root.setBackgroundColor(Color.parseColor("#F4F8FF"));
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = text("‹", 34, "#0B3A78", true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text("Pengaturan Promo", 22, "#0B3A78", true));
        copy.addView(text(
                "Banner customer dan notifikasi broadcast",
                11,
                "#64748B",
                false
        ));
        header.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView refresh = text("⟳", 26, "#0B7CFF", true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setOnClickListener(v -> loadPromos());
        header.addView(refresh, new LinearLayout.LayoutParams(dp(44), dp(44)));

        root.addView(header);
        addGap(root, 14);

        loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);
        LinearLayout.LayoutParams loadingLp =
                new LinearLayout.LayoutParams(dp(38), dp(38));
        loadingLp.gravity = Gravity.CENTER;
        root.addView(loading, loadingLp);

        root.addView(buildForm());
        addGap(root, 16);

        root.addView(text("Daftar Promo", 18, "#0B3A78", true));
        addGap(root, 8);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(listBox);

        return scroll;
    }

    private View buildForm() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(15), dp(15), dp(15));
        card.setBackground(roundStroke("#FFFFFF", "#D7E6F8", 20));
        card.setElevation(dp(2));

        card.addView(text("Buat / Edit Promo", 17, "#0B3A78", true));
        addGap(card, 10);

        titleInput = input("Judul promo *", false);
        descriptionInput = input("Deskripsi promo *", true);
        codeInput = input("Kode promo, contoh JALAN20", false);
        promoImagePicker =
                new PromoImagePickerHelper(this);
        startInput = input("Mulai: YYYY-MM-DD HH:mm:ss", false);
        endInput = input("Berakhir: YYYY-MM-DD HH:mm:ss", false);
        sortInput = input("Urutan banner, contoh 1", false);
        sortInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        card.addView(titleInput);
        addGap(card, 7);
        card.addView(descriptionInput);
        addGap(card, 7);
        card.addView(codeInput);
        addGap(card, 7);
        card.addView(
                text(
                        "Foto Banner",
                        12,
                        "#52647A",
                        true
                )
        );
        addGap(card, 6);
        card.addView(promoImagePicker.buildView());
        addGap(card, 9);

        LinearLayout dates = new LinearLayout(this);
        dates.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams half =
                new LinearLayout.LayoutParams(0, dp(48), 1);

        dates.addView(startInput, half);

        LinearLayout.LayoutParams endLp =
                new LinearLayout.LayoutParams(0, dp(48), 1);
        endLp.setMargins(dp(7), 0, 0, 0);
        dates.addView(endInput, endLp);

        startInput.setFocusable(false);
        endInput.setFocusable(false);
        startInput.setOnClickListener(v -> pickDateTime(startInput));
        endInput.setOnClickListener(v -> pickDateTime(endInput));

        card.addView(dates);
        addGap(card, 7);
        card.addView(sortInput);

        addGap(card, 9);
        card.addView(text("Target Notifikasi", 12, "#52647A", true));

        targetSpinner = new Spinner(this);
        String[] targets = {
                "all",
                "customer",
                "driver",
                "merchant",
                "admin",
                "wisata"
        };
        targetSpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                targets
        ));
        targetSpinner.setBackground(roundStroke("#F9FBFF", "#D7E6F8", 13));
        card.addView(targetSpinner, new LinearLayout.LayoutParams(-1, dp(48)));

        activeCheck = new CheckBox(this);
        activeCheck.setText("Promo aktif");
        activeCheck.setChecked(true);
        card.addView(activeCheck);

        notifyCheck = new CheckBox(this);
        notifyCheck.setText("Kirim notifikasi setelah disimpan");
        notifyCheck.setChecked(true);
        card.addView(notifyCheck);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button save = primaryButton("Simpan Promo");
        save.setOnClickListener(v -> savePromo());
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(48), 1));

        Button reset = outlineButton("Reset");
        reset.setOnClickListener(v -> resetForm());

        LinearLayout.LayoutParams resetLp =
                new LinearLayout.LayoutParams(0, dp(48), 1);
        resetLp.setMargins(dp(8), 0, 0, 0);
        actions.addView(reset, resetLp);

        card.addView(actions);
        return card;
    }

    private EditText input(String hint, boolean multiline) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setTextSize(13);
        edit.setTextColor(Color.parseColor("#0F172A"));
        edit.setHintTextColor(Color.parseColor("#94A3B8"));
        edit.setPadding(dp(12), dp(4), dp(12), dp(4));
        edit.setBackground(roundStroke("#F9FBFF", "#D7E6F8", 13));

        if (multiline) {
            edit.setMinLines(3);
            edit.setGravity(Gravity.TOP);
            edit.setInputType(
                    InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            );
        } else {
            edit.setSingleLine(true);
        }

        edit.setLayoutParams(
                new LinearLayout.LayoutParams(
                        -1,
                        multiline ? dp(84) : dp(48)
                )
        );
        return edit;
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

        if (promoImagePicker != null) {
            promoImagePicker.handleActivityResult(
                    requestCode,
                    resultCode,
                    data
            );
        }
    }

    @Override
    protected void onDestroy() {
        if (promoImagePicker != null) {
            promoImagePicker.destroy();
        }

        super.onDestroy();
    }

    private void loadPromos() {
        if (requestRunning) return;
        requestRunning = true;
        showLoading(true);

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("action", "list");

                JSONObject response = post(payload);
                JSONArray items = response.optJSONArray("promos");

                runOnUiThread(() -> {
                    requestRunning = false;
                    showLoading(false);
                    renderPromos(items);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    requestRunning = false;
                    showLoading(false);
                    toast("Gagal mengambil promo: " + error.getMessage());
                });
            }
        }).start();
    }

    private void renderPromos(JSONArray items) {
        listBox.removeAllViews();

        if (items == null || items.length() == 0) {
            TextView empty = text(
                    "Belum ada promo. Buat promo pertama dari formulir di atas.",
                    12,
                    "#64748B",
                    false
            );
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(14), dp(20), dp(14), dp(20));
            empty.setBackground(roundStroke("#FFFFFF", "#D7E6F8", 17));
            listBox.addView(empty);
            return;
        }

        for (int i = 0; i < items.length(); i++) {
            JSONObject promo = items.optJSONObject(i);
            if (promo != null) listBox.addView(promoCard(promo));
        }
    }

    private View promoCard(JSONObject promo) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(roundStroke("#FFFFFF", "#D7E6F8", 18));
        card.setElevation(dp(1));

        boolean active = promo.optInt("is_active", 0) == 1;

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text(
                promo.optString("title", "Promo"),
                16,
                "#0B3A78",
                true
        );
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView status = text(
                active ? "AKTIF" : "NONAKTIF",
                9,
                active ? "#047857" : "#B91C1C",
                true
        );
        status.setPadding(dp(8), dp(4), dp(8), dp(4));
        status.setBackground(roundStroke(
                active ? "#ECFDF5" : "#FEF2F2",
                active ? "#A7F3D0" : "#FECACA",
                12
        ));
        titleRow.addView(status);
        card.addView(titleRow);

        TextView desc = text(
                promo.optString("description", ""),
                12,
                "#64748B",
                false
        );
        LinearLayout.LayoutParams descLp =
                new LinearLayout.LayoutParams(-1, -2);
        descLp.setMargins(0, dp(5), 0, 0);
        card.addView(desc, descLp);

        String meta =
                "Kode: " + emptyDash(promo.optString("promo_code", ""))
                        + "\nTarget: " + promo.optString("target_role", "all")
                        + " • Urutan: " + promo.optInt("sort_order", 0)
                        + "\nMulai: " + emptyDash(promo.optString("starts_at", ""))
                        + "\nBerakhir: " + emptyDash(promo.optString("ends_at", ""));

        TextView metaView = text(meta, 10, "#8495A8", false);
        LinearLayout.LayoutParams metaLp =
                new LinearLayout.LayoutParams(-1, -2);
        metaLp.setMargins(0, dp(6), 0, dp(10));
        card.addView(metaView, metaLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button edit = smallButton("Edit", "#EAF4FF", "#0B7CFF");
        edit.setOnClickListener(v -> fillForm(promo));
        actions.addView(edit, actionLp());

        Button toggle = smallButton(
                active ? "Nonaktifkan" : "Aktifkan",
                active ? "#FFF7ED" : "#ECFDF5",
                active ? "#C2410C" : "#047857"
        );
        toggle.setOnClickListener(v ->
                confirmToggle(promo.optInt("id", 0), !active)
        );
        actions.addView(toggle, actionLpMargin());

        Button notify = smallButton("Kirim Notif", "#F5F3FF", "#6D28D9");
        notify.setOnClickListener(v ->
                sendNotification(promo.optInt("id", 0))
        );
        actions.addView(notify, actionLpMargin());

        Button delete = smallButton("Hapus", "#FEF2F2", "#B91C1C");
        delete.setOnClickListener(v ->
                confirmDelete(promo.optInt("id", 0))
        );
        actions.addView(delete, actionLpMargin());

        card.addView(actions);

        LinearLayout.LayoutParams cardLp =
                new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, 0, 0, dp(9));
        card.setLayoutParams(cardLp);

        return card;
    }

    private LinearLayout.LayoutParams actionLp() {
        return new LinearLayout.LayoutParams(0, dp(38), 1);
    }

    private LinearLayout.LayoutParams actionLpMargin() {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(0, dp(38), 1);
        lp.setMargins(dp(5), 0, 0, 0);
        return lp;
    }

    private void fillForm(JSONObject promo) {
        editingId = promo.optInt("id", 0);
        titleInput.setText(promo.optString("title", ""));
        descriptionInput.setText(promo.optString("description", ""));
        codeInput.setText(promo.optString("promo_code", ""));
        promoImagePicker.setExistingUrl(
                promo.optString("image_url", "")
        );
        startInput.setText(promo.optString("starts_at", ""));
        endInput.setText(promo.optString("ends_at", ""));
        sortInput.setText(String.valueOf(promo.optInt("sort_order", 0)));
        activeCheck.setChecked(promo.optInt("is_active", 0) == 1);

        String target = promo.optString("target_role", "all");
        for (int i = 0; i < targetSpinner.getCount(); i++) {
            if (target.equals(String.valueOf(targetSpinner.getItemAtPosition(i)))) {
                targetSpinner.setSelection(i);
                break;
            }
        }

        notifyCheck.setChecked(false);
        titleInput.requestFocus();
        toast("Mode edit promo aktif");
    }

    private void resetForm() {
        editingId = 0;
        titleInput.setText("");
        descriptionInput.setText("");
        codeInput.setText("");
        promoImagePicker.clear(false);
        startInput.setText("");
        endInput.setText("");
        sortInput.setText("0");
        targetSpinner.setSelection(0);
        activeCheck.setChecked(true);
        notifyCheck.setChecked(true);
    }

    private void savePromo() {
        String title = titleInput.getText().toString().trim();
        String description = descriptionInput.getText().toString().trim();

        if (title.isEmpty() || description.isEmpty()) {
            toast("Judul dan deskripsi wajib diisi");
            return;
        }

        if (requestRunning) return;
        requestRunning = true;
        showLoading(true);

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put(
                        "action",
                        editingId > 0 ? "update" : "create"
                );
                payload.put("id", editingId);
                payload.put("title", title);
                payload.put("description", description);
                payload.put(
                        "promo_code",
                        codeInput.getText().toString().trim()
                );
                payload.put(
                        "image_url",
                        promoImagePicker.getImageUrl()
                );
                payload.put(
                        "starts_at",
                        startInput.getText().toString().trim()
                );
                payload.put(
                        "ends_at",
                        endInput.getText().toString().trim()
                );
                payload.put(
                        "sort_order",
                        parseInt(sortInput.getText().toString(), 0)
                );
                payload.put(
                        "target_role",
                        String.valueOf(targetSpinner.getSelectedItem())
                );
                payload.put("is_active", activeCheck.isChecked() ? 1 : 0);
                payload.put(
                        "send_notification",
                        notifyCheck.isChecked() ? 1 : 0
                );

                JSONObject response = post(payload);

                if (!response.optBoolean("success", false)) {
                    throw new IllegalStateException(
                            response.optString("message", "Gagal menyimpan")
                    );
                }

                runOnUiThread(() -> {
                    requestRunning = false;
                    showLoading(false);
                    toast(response.optString(
                            "message",
                            "Promo berhasil disimpan"
                    ));
                    resetForm();
                    loadPromos();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    requestRunning = false;
                    showLoading(false);
                    toast("Gagal menyimpan: " + error.getMessage());
                });
            }
        }).start();
    }

    private void confirmToggle(int id, boolean active) {
        new AlertDialog.Builder(this)
                .setTitle(active ? "Aktifkan Promo" : "Nonaktifkan Promo")
                .setMessage(
                        active
                                ? "Promo akan tampil pada dashboard customer."
                                : "Promo tidak lagi tampil pada dashboard customer."
                )
                .setNegativeButton("Batal", null)
                .setPositiveButton("Lanjut", (dialog, which) ->
                        simpleAction(
                                "toggle",
                                id,
                                active ? 1 : 0,
                                "Status promo diperbarui"
                        )
                )
                .show();
    }

    private void confirmDelete(int id) {
        new AlertDialog.Builder(this)
                .setTitle("Hapus Promo")
                .setMessage("Promo akan dihapus permanen dari database.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus", (dialog, which) ->
                        simpleAction(
                                "delete",
                                id,
                                0,
                                "Promo berhasil dihapus"
                        )
                )
                .show();
    }

    private void sendNotification(int id) {
        new AlertDialog.Builder(this)
                .setTitle("Kirim Notifikasi Promo")
                .setMessage(
                        "Notifikasi promo akan dikirim ke target pengguna yang dipilih."
                )
                .setNegativeButton("Batal", null)
                .setPositiveButton("Kirim", (dialog, which) ->
                        simpleAction(
                                "send_notification",
                                id,
                                0,
                                "Notifikasi selesai diproses"
                        )
                )
                .show();
    }

    private void simpleAction(
            String action,
            int id,
            int active,
            String fallback
    ) {
        if (requestRunning) return;
        requestRunning = true;
        showLoading(true);

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("action", action);
                payload.put("id", id);
                payload.put("is_active", active);

                JSONObject response = post(payload);

                if (!response.optBoolean("success", false)) {
                    throw new IllegalStateException(
                            response.optString("message", "Operasi gagal")
                    );
                }

                runOnUiThread(() -> {
                    requestRunning = false;
                    showLoading(false);
                    toast(response.optString("message", fallback));
                    loadPromos();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    requestRunning = false;
                    showLoading(false);
                    toast("Operasi gagal: " + error.getMessage());
                });
            }
        }).start();
    }

    private JSONObject post(JSONObject body) throws Exception {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(API).openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
            );
            connection.setRequestProperty("Accept", "application/json");

            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(
                            connection.getOutputStream(),
                            "UTF-8"
                    )
            );
            writer.write(body.toString());
            writer.flush();
            writer.close();

            int status = connection.getResponseCode();
            InputStream stream =
                    status >= 200 && status < 400
                            ? connection.getInputStream()
                            : connection.getErrorStream();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, "UTF-8")
            );

            StringBuilder raw = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                raw.append(line);
            }

            reader.close();

            JSONObject response = new JSONObject(
                    raw.length() == 0 ? "{}" : raw.toString()
            );

            if (status < 200 || status >= 400) {
                throw new IllegalStateException(
                        response.optString(
                                "message",
                                "HTTP " + status
                        )
                );
            }

            return response;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void pickDateTime(EditText target) {
        Calendar calendar = Calendar.getInstance();

        DatePickerDialog dateDialog = new DatePickerDialog(
                this,
                (view, year, month, day) -> {
                    TimePickerDialog timeDialog = new TimePickerDialog(
                            this,
                            (timeView, hour, minute) -> target.setText(
                                    String.format(
                                            Locale.US,
                                            "%04d-%02d-%02d %02d:%02d:00",
                                            year,
                                            month + 1,
                                            day,
                                            hour,
                                            minute
                                    )
                            ),
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            true
                    );
                    timeDialog.show();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        dateDialog.show();
    }

    private void showLoading(boolean show) {
        loading.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(gradient());
        return button;
    }

    private Button outlineButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.parseColor("#0B7CFF"));
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(roundStroke("#FFFFFF", "#B9DBFF", 13));
        return button;
    }

    private Button smallButton(
            String value,
            String background,
            String foreground
    ) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.parseColor(foreground));
        button.setTextSize(8);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setBackground(roundStroke(background, background, 10));
        return button;
    }

    private TextView text(
            String value,
            int sp,
            String color,
            boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.parseColor(color));
        view.setIncludeFontPadding(false);

        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable roundStroke(
            String fill,
            String stroke,
            int radius
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(fill));
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), Color.parseColor(stroke));
        return drawable;
    }

    private GradientDrawable gradient() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.parseColor("#086BFF"),
                        Color.parseColor("#2EA2FF")
                }
        );
        drawable.setCornerRadius(dp(13));
        return drawable;
    }

    private void addGap(LinearLayout parent, int height) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String emptyDash(String value) {
        return value == null || value.trim().isEmpty()
                ? "-"
                : value.trim();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }
}
