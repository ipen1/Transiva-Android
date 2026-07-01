package com.transiva.app;

import android.os.Bundle;
import android.net.Uri;
import android.content.Intent;
import android.text.InputType;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.*;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class MerchantRestaurantProfileActivity extends MerchantBaseActivity {
    private static final int PICK_BANNER = 802;
    private LinearLayout root;
    private EditText nameInput;
    private TextView statusText, bannerIcon, bannerTitle, bannerSub;
    private ImageView bannerImage;
    private Uri bannerUri = null;
    private String restaurantId = "";

    @Override protected void onCreate(Bundle b){ super.onCreate(b); build(); load(); }

    private void build(){
        root = new LinearLayout(this); setContentView(page(root));
        root.addView(title("Profil Merchant"));
        root.addView(sub("Kelola nama merchant dan banner yang tampil di aplikasi"));

        root.addView(activeBannerCard());

        statusText = card("Memuat profil merchant...");
        root.addView(statusText);

        root.addView(label("Nama Merchant / Restoran"));
        nameInput = input("Masukkan nama merchant", InputType.TYPE_CLASS_TEXT);
        root.addView(nameInput);
        nameInput.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){
                String name = nameInput.getText().toString().trim();
                bannerTitle.setText(name.isEmpty() ? "Merchant Transiva" : name);
            }
            public void afterTextChanged(android.text.Editable e){}
        });

        Button pick = outlineBtn("🖼️ Pilih Banner Merchant");
        pick.setOnClickListener(v -> choose());
        root.addView(pick);
        Button save = btn("💾 Simpan Profil Merchant");
        save.setOnClickListener(v -> save(save));
        root.addView(save);
        Button back = outlineBtn("← Kembali"); back.setOnClickListener(v -> finish()); root.addView(back);
    }

    private FrameLayout activeBannerCard(){
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(round(Color.parseColor("#0A1A2E"), dp(24)));
        frame.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(180));
        lp.setMargins(0, dp(4), 0, dp(14));
        frame.setLayoutParams(lp);

        bannerImage = new ImageView(this);
        bannerImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(bannerImage, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        overlay.setPadding(dp(18), dp(16), dp(18), dp(16));
        overlay.setBackgroundColor(Color.parseColor("#66000000"));

        bannerIcon = tv("🏪", 34, Color.WHITE, true);
        bannerTitle = tv("Merchant Transiva", 24, Color.WHITE, true);
        bannerSub = tv("Banner aktif sedang dimuat...", 13, Color.WHITE, false);
        overlay.addView(bannerIcon);
        overlay.addView(bannerTitle);
        overlay.addView(bannerSub);
        frame.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        return frame;
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
            }catch(Exception e){ runOnUiThread(() -> statusText.setText("Gagal memuat profil merchant."));}
        }).start();
    }

    private void show(JSONObject res){
        if(!res.optBoolean("success", true)){ statusText.setText(res.optString("message","Profil belum tersedia")); return; }
        JSONObject r = res.optJSONObject("restaurant"); if(r == null) r = res;
        String name = s(r,"name","restaurant_name","merchant_name","store_name");
        String banner = s(r,"banner","banner_url","image","photo","foto","cover","cover_image","restaurant_banner");
        nameInput.setText(name);
        bannerTitle.setText(name.isEmpty() ? "Merchant Transiva" : name);
        if(banner.isEmpty()){
            bannerSub.setText("Belum ada banner aktif. Pilih banner lalu simpan.");
            statusText.setText("🏪 " + (name.isEmpty() ? "Merchant" : name) + "\nBanner aktif belum tersedia.");
        }else{
            bannerSub.setText("Banner aktif saat ini");
            statusText.setText("🏪 " + (name.isEmpty() ? "Merchant" : name) + "\nBanner aktif sudah tampil di atas.");
            loadBannerImage(banner);
        }
    }

    private void choose(){
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(Intent.createChooser(i, "Pilih banner merchant"), PICK_BANNER);
    }

    @Override protected void onActivityResult(int r, int c, Intent data){
        super.onActivityResult(r,c,data);
        if(r == PICK_BANNER && c == RESULT_OK && data != null){
            bannerUri = data.getData();
            showPickedBanner(bannerUri);
            bannerSub.setText("Preview banner baru. Tekan Simpan Profil Merchant.");
            statusText.setText("Banner baru dipilih. Tekan Simpan Profil Merchant.");
        }
    }

    private void showPickedBanner(Uri uri){
        if(uri == null) return;
        try{
            Bitmap bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            if(bmp != null) bannerImage.setImageBitmap(bmp);
        }catch(Exception e){ toast("Preview banner gagal, tapi file masih bisa dicoba upload."); }
    }

    private void loadBannerImage(String rawUrl){
        final String url = normalizeImageUrl(rawUrl);
        if(url.isEmpty()) return;
        new Thread(() -> {
            try{
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setUseCaches(true);
                conn.setRequestProperty("User-Agent", "Transiva-Android");
                InputStream is = conn.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                is.close();
                conn.disconnect();
                if(bitmap != null) runOnUiThread(() -> bannerImage.setImageBitmap(bitmap));
            }catch(Exception ignored){}
        }).start();
    }

    private String normalizeImageUrl(String raw){
        if(raw == null) return "";
        String url = raw.trim();
        if(url.isEmpty() || "null".equalsIgnoreCase(url)) return "";
        if(url.startsWith("http://") || url.startsWith("https://")) return url;
        if(url.startsWith("/")) return "https://transiva.my.id" + url;
        return "https://transiva.my.id/" + url;
    }

    private void save(Button save){
        String name = nameInput.getText().toString().trim();
        if(name.isEmpty()){ alert("Nama Kosong", "Nama merchant tidak boleh kosong."); return; }
        if(restaurantId.isEmpty()){ alert("Merchant Tidak Ditemukan", "Silakan login ulang atau cek getMerchantDashboard.php."); return; }
        save.setEnabled(false); save.setText("Menyimpan...");
        new Thread(() -> {
            try{
                JSONObject f = new JSONObject(); f.put("id", restaurantId); f.put("name", name);
                JSONObject res = new JSONObject(postForm(BASE + "update_restaurant_profile.php", f, bannerUri, "banner", "merchant_banner.jpg"));
                runOnUiThread(() -> {
                    save.setEnabled(true); save.setText("💾 Simpan Profil Merchant");
                    toast(res.optString("message", res.optBoolean("success") ? "Profil berhasil disimpan" : "Gagal"));
                    if(res.optBoolean("success", false)) load();
                });
            }catch(Exception e){ runOnUiThread(() -> { save.setEnabled(true); save.setText("💾 Simpan Profil Merchant"); alert("Error","Gagal menyimpan profil."); });}
        }).start();
    }
}
