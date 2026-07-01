package com.transiva.app;

import android.os.Bundle;
import android.net.Uri;
import android.content.Intent;
import android.text.InputType;
import android.widget.*;
import org.json.JSONObject;

public class MerchantRestaurantProfileActivity extends MerchantBaseActivity {
    private static final int PICK_BANNER = 802;
    private LinearLayout root;
    private EditText nameInput;
    private TextView statusText;
    private Uri bannerUri = null;
    private String restaurantId = "";

    @Override protected void onCreate(Bundle b){ super.onCreate(b); build(); load(); }

    private void build(){
        root = new LinearLayout(this); setContentView(page(root));
        root.addView(title("Profil Restoran"));
        root.addView(sub("Kelola nama dan banner restoran"));
        statusText = card("Memuat profil restoran...");
        root.addView(statusText);
        root.addView(label("Nama Restoran"));
        nameInput = input("Masukkan nama restoran", InputType.TYPE_CLASS_TEXT);
        root.addView(nameInput);
        Button pick = outlineBtn("🖼️ Pilih Banner Restoran");
        pick.setOnClickListener(v -> choose());
        root.addView(pick);
        Button save = btn("💾 Simpan Profil Restoran");
        save.setOnClickListener(v -> save(save));
        root.addView(save);
        Button back = outlineBtn("← Kembali"); back.setOnClickListener(v -> finish()); root.addView(back);
    }

    private void load(){
        final String u = username();
        new Thread(() -> {
            try{
                JSONObject dash = new JSONObject(get(BASE + "getMerchantDashboard.php?username=" + enc(u) + "&v=" + System.currentTimeMillis()));
                restaurantId = dash.optString("restaurant_id", "");
                if(restaurantId.isEmpty()) restaurantId = dash.optString("id", "");
                JSONObject prof = new JSONObject(get(BASE + "get_restaurant_profile.php?id=" + enc(restaurantId) + "&v=" + System.currentTimeMillis()));
                runOnUiThread(() -> show(prof));
            }catch(Exception e){ runOnUiThread(() -> statusText.setText("Gagal memuat profil restoran."));}
        }).start();
    }

    private void show(JSONObject res){
        if(!res.optBoolean("success", true)){ statusText.setText(res.optString("message","Profil belum tersedia")); return; }
        JSONObject r = res.optJSONObject("restaurant"); if(r == null) r = res;
        String name = s(r,"name","restaurant_name");
        nameInput.setText(name);
        statusText.setText("🏪 " + (name.isEmpty() ? "Restoran" : name) + "\nBanner dapat diganti dari tombol pilih banner.");
    }

    private void choose(){
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(Intent.createChooser(i, "Pilih banner restoran"), PICK_BANNER);
    }

    @Override protected void onActivityResult(int r, int c, Intent data){
        super.onActivityResult(r,c,data);
        if(r == PICK_BANNER && c == RESULT_OK && data != null){
            bannerUri = data.getData();
            statusText.setText("Banner baru dipilih. Tekan Simpan Profil Restoran.");
        }
    }

    private void save(Button save){
        String name = nameInput.getText().toString().trim();
        if(name.isEmpty()){ alert("Nama Kosong", "Nama restoran tidak boleh kosong."); return; }
        if(restaurantId.isEmpty()){ alert("Restoran Tidak Ditemukan", "Silakan login ulang atau cek getMerchantDashboard.php."); return; }
        save.setEnabled(false); save.setText("Menyimpan...");
        new Thread(() -> {
            try{
                JSONObject f = new JSONObject(); f.put("id", restaurantId); f.put("name", name);
                JSONObject res = new JSONObject(postForm(BASE + "update_restaurant_profile.php", f, bannerUri, "banner", "restaurant_banner.jpg"));
                runOnUiThread(() -> {
                    save.setEnabled(true); save.setText("💾 Simpan Profil Restoran");
                    toast(res.optString("message", res.optBoolean("success") ? "Profil berhasil disimpan" : "Gagal"));
                    if(res.optBoolean("success", false)) load();
                });
            }catch(Exception e){ runOnUiThread(() -> { save.setEnabled(true); save.setText("💾 Simpan Profil Restoran"); alert("Error","Gagal menyimpan profil."); });}
        }).start();
    }
}
