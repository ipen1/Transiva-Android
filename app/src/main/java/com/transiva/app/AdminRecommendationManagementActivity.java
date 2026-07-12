package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
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
import java.util.ArrayList;
import java.util.List;

public class AdminRecommendationManagementActivity extends Activity {

    private static final String API =
            "https://transiva.my.id/server/admin_manage_recommendations.php";
    private static final String CATALOG_API =
            "https://transiva.my.id/server/admin_recommendation_catalog.php";

    private Spinner typeSpinner;
    private Spinner itemSpinner;
    private EditText titleOverride;
    private EditText subtitleOverride;
    private EditText sortOrder;
    private CheckBox featuredCheck;
    private CheckBox activeCheck;
    private LinearLayout listBox;
    private ProgressBar loading;

    private final List<JSONObject> catalog = new ArrayList<>();
    private int editingId;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#071426"));
        getWindow().setNavigationBarColor(Color.parseColor("#071426"));
        setContentView(buildScreen());
        loadCatalog();
        loadRecommendations();
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(15), dp(16), dp(15), dp(28));
        root.setBackgroundColor(Color.parseColor("#F4F8FF"));
        scroll.addView(root);

        TextView back = text("‹  Pengaturan Rekomendasi", 22, "#0B3A78", true);
        back.setOnClickListener(v -> finish());
        root.addView(back);

        TextView sub = text(
                "Pilih makanan, restoran, atau wisata untuk ditampilkan di dashboard customer.",
                11,
                "#64748B",
                false
        );
        sub.setPadding(0, dp(4), 0, dp(12));
        root.addView(sub);

        loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);
        LinearLayout.LayoutParams loadLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        loadLp.gravity = Gravity.CENTER;
        root.addView(loading, loadLp);

        LinearLayout form = card();
        form.addView(text("Tambah / Edit Rekomendasi", 17, "#0B3A78", true));

        typeSpinner = new Spinner(this);
        typeSpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"food", "restaurant", "tour"}
        ));
        typeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { rebuildItemSpinner(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        addField(form, text("Jenis data", 11, "#52647A", true));
        addField(form, typeSpinner);

        itemSpinner = new Spinner(this);
        addField(form, text("Pilih data real", 11, "#52647A", true));
        addField(form, itemSpinner);

        titleOverride = input("Judul khusus (opsional)");
        subtitleOverride = input("Subjudul khusus (opsional)");
        sortOrder = input("Urutan, contoh 1");
        sortOrder.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        addField(form, titleOverride);
        addField(form, subtitleOverride);
        addField(form, sortOrder);

        featuredCheck = new CheckBox(this);
        featuredCheck.setText("Jadikan pilihan admin / featured");
        featuredCheck.setChecked(true);
        form.addView(featuredCheck);

        activeCheck = new CheckBox(this);
        activeCheck.setText("Aktif");
        activeCheck.setChecked(true);
        form.addView(activeCheck);

        LinearLayout actions = new LinearLayout(this);
        Button save = primaryButton("Simpan");
        save.setOnClickListener(v -> save());
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button reset = outlineButton("Reset");
        reset.setOnClickListener(v -> reset());
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        resetLp.setMargins(dp(8), 0, 0, 0);
        actions.addView(reset, resetLp);
        form.addView(actions);

        root.addView(form);

        TextView listTitle = text("Rekomendasi Aktif", 18, "#0B3A78", true);
        listTitle.setPadding(0, dp(16), 0, dp(8));
        root.addView(listTitle);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(listBox);
        return scroll;
    }

    private void loadCatalog() {
        networkGet(CATALOG_API, response -> {
            catalog.clear();
            JSONArray items = response.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item != null) catalog.add(item);
                }
            }
            rebuildItemSpinner();
        });
    }

    private void rebuildItemSpinner() {
        if (itemSpinner == null || typeSpinner == null) return;
        String type = String.valueOf(typeSpinner.getSelectedItem());
        List<String> labels = new ArrayList<>();
        for (JSONObject item : catalog) {
            if (type.equals(item.optString("item_type"))) {
                labels.add(item.optString("title", "Item")
                        + (item.optDouble("rating", 0) > 0
                        ? " • ⭐ " + item.optString("rating") : ""));
            }
        }
        if (labels.isEmpty()) labels.add("Belum ada data " + type);
        itemSpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                labels
        ));
    }

    private JSONObject selectedCatalogItem() {
        String type = String.valueOf(typeSpinner.getSelectedItem());
        int selected = itemSpinner.getSelectedItemPosition();
        int index = 0;
        for (JSONObject item : catalog) {
            if (type.equals(item.optString("item_type"))) {
                if (index == selected) return item;
                index++;
            }
        }
        return null;
    }

    private void save() {
        JSONObject selected = selectedCatalogItem();
        if (selected == null) {
            toast("Data real belum tersedia");
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("action", editingId > 0 ? "update" : "create");
            payload.put("id", editingId);
            payload.put("item_type", selected.optString("item_type"));
            payload.put("target_id", selected.optInt("target_id"));
            payload.put("parent_id", selected.optInt("parent_id"));
            payload.put("title", selected.optString("title"));
            payload.put("subtitle", selected.optString("subtitle"));
            payload.put("owner_name", selected.optString("owner_name"));
            payload.put("image_url", selected.optString("image_url"));
            payload.put("rating", selected.optDouble("rating"));
            payload.put("review_count", selected.optInt("review_count"));
            payload.put("price", selected.optDouble("price"));
            payload.put("title_override", titleOverride.getText().toString().trim());
            payload.put("subtitle_override", subtitleOverride.getText().toString().trim());
            payload.put("sort_order", parseInt(sortOrder.getText().toString(), 0));
            payload.put("is_featured", featuredCheck.isChecked() ? 1 : 0);
            payload.put("is_active", activeCheck.isChecked() ? 1 : 0);

            networkPost(payload, response -> {
                toast(response.optString("message", "Tersimpan"));
                reset();
                loadRecommendations();
            });
        } catch (Exception e) {
            toast(e.getMessage());
        }
    }

    private void loadRecommendations() {
        JSONObject payload = new JSONObject();
        try { payload.put("action", "list"); } catch (Exception ignored) {}
        networkPost(payload, response -> renderList(response.optJSONArray("recommendations")));
    }

    private void renderList(JSONArray items) {
        listBox.removeAllViews();
        if (items == null || items.length() == 0) {
            TextView empty = text("Belum ada rekomendasi pilihan admin.", 12, "#64748B", false);
            empty.setPadding(dp(14), dp(18), dp(14), dp(18));
            empty.setBackground(roundStroke("#FFFFFF", "#D7E6F8", 17));
            listBox.addView(empty);
            return;
        }

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            LinearLayout card = card();
            card.addView(text(item.optString("title"), 15, "#0B3A78", true));
            card.addView(text(
                    item.optString("item_type") + " • ⭐ " + item.optString("rating", "0")
                            + " • urutan " + item.optInt("sort_order"),
                    11,
                    "#64748B",
                    false
            ));
            LinearLayout actions = new LinearLayout(this);
            Button toggle = outlineButton(item.optInt("is_active", 0) == 1 ? "Nonaktifkan" : "Aktifkan");
            toggle.setOnClickListener(v -> simpleAction("toggle", item.optInt("id"), item.optInt("is_active", 0) == 1 ? 0 : 1));
            actions.addView(toggle, new LinearLayout.LayoutParams(0, dp(42), 1));
            Button delete = outlineButton("Hapus");
            delete.setTextColor(Color.parseColor("#B91C1C"));
            delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Hapus rekomendasi")
                    .setMessage("Data sumber tidak ikut dihapus.")
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Hapus", (d, w) -> simpleAction("delete", item.optInt("id"), 0))
                    .show());
            LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(0, dp(42), 1);
            deleteLp.setMargins(dp(7), 0, 0, 0);
            actions.addView(delete, deleteLp);
            card.addView(actions);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, dp(8));
            listBox.addView(card, lp);
        }
    }

    private void simpleAction(String action, int id, int active) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("action", action);
            payload.put("id", id);
            payload.put("is_active", active);
            networkPost(payload, response -> {
                toast(response.optString("message", "Berhasil"));
                loadRecommendations();
            });
        } catch (Exception e) { toast(e.getMessage()); }
    }

    private interface JsonCallback { void call(JSONObject response); }

    private void networkGet(String url, JsonCallback callback) {
        if (busy) return;
        busy = true; showLoading(true);
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url + "?_=" + System.currentTimeMillis()).openConnection();
                c.setConnectTimeout(15000); c.setReadTimeout(25000); c.setUseCaches(false);
                JSONObject response = read(c);
                runOnUiThread(() -> { busy = false; showLoading(false); callback.call(response); });
            } catch (Exception e) {
                runOnUiThread(() -> { busy = false; showLoading(false); toast("Gagal: " + e.getMessage()); });
            }
        }).start();
    }

    private void networkPost(JSONObject payload, JsonCallback callback) {
        if (busy) return;
        busy = true; showLoading(true);
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
                c.setConnectTimeout(15000); c.setReadTimeout(25000); c.setRequestMethod("POST");
                c.setDoOutput(true); c.setUseCaches(false);
                c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(c.getOutputStream(), "UTF-8"));
                writer.write(payload.toString()); writer.flush(); writer.close();
                JSONObject response = read(c);
                if (!response.optBoolean("success", false)) throw new IllegalStateException(response.optString("message", "Operasi gagal"));
                runOnUiThread(() -> { busy = false; showLoading(false); callback.call(response); });
            } catch (Exception e) {
                runOnUiThread(() -> { busy = false; showLoading(false); toast("Gagal: " + e.getMessage()); });
            }
        }).start();
    }

    private JSONObject read(HttpURLConnection c) throws Exception {
        InputStream stream = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder raw = new StringBuilder(); String line;
        while ((line = reader.readLine()) != null) raw.append(line);
        reader.close(); c.disconnect();
        return new JSONObject(raw.toString());
    }

    private void reset() {
        editingId = 0;
        titleOverride.setText(""); subtitleOverride.setText(""); sortOrder.setText("0");
        featuredCheck.setChecked(true); activeCheck.setChecked(true);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(roundStroke("#FFFFFF", "#D7E6F8", 18));
        card.setElevation(dp(1));
        return card;
    }

    private void addField(LinearLayout parent, View view) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, view instanceof TextView ? -2 : dp(48));
        lp.setMargins(0, dp(8), 0, 0);
        parent.addView(view, lp);
    }

    private EditText input(String hint) {
        EditText edit = new EditText(this);
        edit.setHint(hint); edit.setSingleLine(true); edit.setTextSize(13);
        edit.setPadding(dp(12), 0, dp(12), 0);
        edit.setBackground(roundStroke("#F9FBFF", "#D7E6F8", 13));
        return edit;
    }

    private Button primaryButton(String value) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD); b.setBackground(round("#0B7CFF", 13)); return b;
    }

    private Button outlineButton(String value) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextColor(Color.parseColor("#0B7CFF"));
        b.setTypeface(Typeface.DEFAULT_BOLD); b.setBackground(roundStroke("#FFFFFF", "#B9DBFF", 13)); return b;
    }

    private TextView text(String value, int size, String color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(Color.parseColor(color));
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD); return t;
    }

    private GradientDrawable round(String color, int radius) {
        GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(color)); g.setCornerRadius(dp(radius)); return g;
    }

    private GradientDrawable roundStroke(String fill, String stroke, int radius) {
        GradientDrawable g = round(fill, radius); g.setStroke(dp(1), Color.parseColor(stroke)); return g;
    }

    private void showLoading(boolean value) { loading.setVisibility(value ? View.VISIBLE : View.GONE); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }
    private int parseInt(String value, int fallback) { try { return Integer.parseInt(value.trim()); } catch (Exception e) { return fallback; } }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
