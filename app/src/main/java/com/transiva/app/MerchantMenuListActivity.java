package com.transiva.app;

import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.Gravity;
import android.widget.*;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

public class MerchantMenuListActivity extends MerchantBaseActivity {
    private LinearLayout root, list;

    @Override protected void onCreate(Bundle b){ super.onCreate(b); build(); load(); }

    private void build(){
        root = new LinearLayout(this); setContentView(page(root));
        root.addView(title("Daftar Menu"));
        root.addView(sub("Aktifkan, nonaktifkan, atau hapus menu restoran"));
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); root.addView(list);
        Button add = btn("➕ Tambah Menu Baru"); add.setOnClickListener(v -> open(MerchantAddMenuActivity.class)); root.addView(add);
        Button back = outlineBtn("← Kembali"); back.setOnClickListener(v -> finish()); root.addView(back);
    }

    @Override protected void onResume(){ super.onResume(); load(); }

    private void load(){
        final int uid = userId();
        list.removeAllViews(); list.addView(card("Memuat menu..."));
        new Thread(() -> {
            try{
                String link = BASE + "merchant_get_menus.php?user_id=" + uid + "&v=" + System.currentTimeMillis();
                JSONObject res = new JSONObject(get(link));
                runOnUiThread(() -> show(res));
            }catch(Exception e){ runOnUiThread(() -> { list.removeAllViews(); list.addView(card("Gagal memuat menu. Pastikan user_id tersimpan setelah login.")); });}
        }).start();
    }

    private void show(JSONObject data){
        list.removeAllViews();
        if(!data.optBoolean("success", false)){ list.addView(card(data.optString("message","Gagal mengambil menu"))); return; }
        JSONArray arr = data.optJSONArray("menus"); if(arr == null) arr = data.optJSONArray("data");
        if(arr == null || arr.length()==0){ list.addView(card("Belum ada menu.")); return; }
        for(int i=0;i<arr.length();i++){ JSONObject m = arr.optJSONObject(i); if(m != null) list.addView(menuCard(m)); }
    }

    private LinearLayout menuCard(JSONObject m){
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12),dp(12),dp(12),dp(12));
        box.setBackground(round(Color.WHITE, dp(20)));
        box.setElevation(dp(2));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,0,0,dp(12));
        box.setLayoutParams(lp);

        String id = s(m,"id","menu_id");
        int active = m.optInt("is_active", m.optInt("active", 1));
        String name = s(m,"name","menu_name","food_name");
        String category = s(m,"category","type");
        String image = s(m,"image","photo","foto","image_url","menu_image","food_image");

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0,0,0,dp(10));

        FrameLayout imageWrap = new FrameLayout(this);
        imageWrap.setBackground(round(Color.parseColor("#EEF6FF"), dp(18)));

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageWrap.addView(img, new FrameLayout.LayoutParams(-1, -1));

        TextView fallback = tv("🍽️", 30, BLUE, true);
        fallback.setGravity(Gravity.CENTER);
        imageWrap.addView(fallback, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(dp(92), dp(92));
        imgLp.setMargins(0,0,dp(12),0);
        top.addView(imageWrap, imgLp);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);

        info.addView(tv(name.isEmpty() ? "Tanpa nama" : name, 17, NAVY, true));
        info.addView(tv(category.isEmpty() ? "Menu" : category, 13, MUTED, false));
        info.addView(tv(rupiah(m.optLong("price",0)), 15, BLUE, true));
        info.addView(tv(active == 1 ? "🟢 Aktif" : "🔴 Tidak tersedia", 13, active == 1 ? Color.parseColor("#16803A") : Color.parseColor("#B42318"), true));

        top.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(top);

        loadMenuImage(img, fallback, image);

        LinearLayout r = row();
        Button status = active == 1 ? outlineBtn("Nonaktifkan") : btn("Aktifkan");
        Button del = outlineBtn("Hapus");
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, dp(48), 1f);
        a.setMargins(0,0,dp(6),0);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        dlp.setMargins(dp(6),0,0,0);
        r.addView(status, a);
        r.addView(del, dlp);
        box.addView(r);

        status.setOnClickListener(v -> updateStatus(id, active == 1 ? 0 : 1));
        del.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("Hapus Menu")
                .setMessage("Yakin ingin menghapus menu ini?")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus", (d,w) -> deleteMenu(id)).show());
        return box;
    }

    private void loadMenuImage(ImageView imageView, TextView fallback, String rawUrl){
        String url = normalizeImageUrl(rawUrl);
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

                if(bitmap != null){
                    runOnUiThread(() -> {
                        fallback.setVisibility(android.view.View.GONE);
                        imageView.setImageBitmap(bitmap);
                    });
                }
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

    private void updateStatus(String menuId, int active){
        new Thread(() -> {
            try{
                JSONObject f = new JSONObject(); f.put("user_id", userId()); f.put("menu_id", menuId); f.put("is_active", active);
                JSONObject r = new JSONObject(postForm(BASE + "merchant_update_menu_status.php", f, null, "", ""));
                runOnUiThread(() -> { toast(r.optString("message", r.optBoolean("success")?"Berhasil":"Gagal")); load(); });
            }catch(Exception e){ runOnUiThread(() -> alert("Error","Gagal update status menu."));}
        }).start();
    }

    private void deleteMenu(String menuId){
        new Thread(() -> {
            try{
                JSONObject f = new JSONObject(); f.put("user_id", userId()); f.put("menu_id", menuId);
                JSONObject r = new JSONObject(postForm(BASE + "merchant_delete_menu.php", f, null, "", ""));
                runOnUiThread(() -> { toast(r.optString("message", r.optBoolean("success")?"Berhasil":"Gagal")); load(); });
            }catch(Exception e){ runOnUiThread(() -> alert("Error","Gagal hapus menu."));}
        }).start();
    }
}
