package com.transiva.app;

import android.os.Bundle;
import android.graphics.Color;
import android.widget.*;
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
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14),dp(14),dp(14),dp(14)); box.setBackground(round(Color.WHITE, dp(18))); box.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,dp(12)); box.setLayoutParams(lp);
        String id = s(m,"id","menu_id");
        int active = m.optInt("is_active", m.optInt("active", 1));
        box.addView(tv(s(m,"name","menu_name","food_name").isEmpty() ? "Tanpa nama" : s(m,"name","menu_name","food_name"), 17, NAVY, true));
        box.addView(tv(s(m,"category","type").isEmpty() ? "Menu" : s(m,"category","type"), 13, MUTED, false));
        box.addView(tv(rupiah(m.optLong("price",0)), 15, BLUE, true));
        box.addView(tv(active == 1 ? "🟢 Aktif" : "🔴 Tidak tersedia", 13, active == 1 ? Color.parseColor("#16803A") : Color.parseColor("#B42318"), true));
        LinearLayout r = row();
        Button status = active == 1 ? outlineBtn("Nonaktifkan") : btn("Aktifkan");
        Button del = outlineBtn("Hapus");
        r.addView(status, new LinearLayout.LayoutParams(0, dp(48), 1f));
        r.addView(del, new LinearLayout.LayoutParams(0, dp(48), 1f));
        box.addView(r);
        status.setOnClickListener(v -> updateStatus(id, active == 1 ? 0 : 1));
        del.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("Hapus Menu")
                .setMessage("Yakin ingin menghapus menu ini?")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus", (d,w) -> deleteMenu(id)).show());
        return box;
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
