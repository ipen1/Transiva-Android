package com.transiva.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MerchantOrdersActivity extends MerchantBaseActivity {
    private LinearLayout root, list;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable task;
    private boolean firstLoad = true;
    private boolean updating = false;

    @Override protected void onCreate(Bundle b){ super.onCreate(b); build(); }
    @Override protected void onResume(){ super.onResume(); start(); }
    @Override protected void onPause(){ super.onPause(); stop(); }

    private void build(){
        root = new LinearLayout(this); setContentView(page(root));
        root.addView(title("Pesanan Masuk"));
        root.addView(sub("Kelola order merchant aktif"));
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); root.addView(list);
        Button back = outlineBtn("← Kembali"); back.setOnClickListener(v -> finish()); root.addView(back);
    }

    private void start(){ stop(); firstLoad = true; load(); task = () -> { if(!updating) load(); handler.postDelayed(task, 5000); }; handler.postDelayed(task, 5000); }
    private void stop(){ if(task != null) handler.removeCallbacks(task); }

    private void load(){
        final String u = username();
        if(u.isEmpty()){
            list.removeAllViews();
            list.addView(card("Sesi merchant tidak ditemukan. Silakan login ulang."));
            return;
        }
        if(firstLoad){ list.removeAllViews(); list.addView(card("Memuat pesanan...")); }
        new Thread(() -> {
            try {
                String res = get(BASE + "getMerchantOrders.php?username=" + enc(u) + "&v=" + System.currentTimeMillis());
                runOnUiThread(() -> { firstLoad = false; show(res); });
            } catch(Exception e){ runOnUiThread(() -> { firstLoad = false; list.removeAllViews(); list.addView(card("Koneksi gagal.")); });}
        }).start();
    }

    private void show(String json){
        list.removeAllViews();
        try {
            JSONObject obj = new JSONObject(json);
            if(!obj.optBoolean("success", true)){ list.addView(card(obj.optString("message","Gagal memuat pesanan"))); return; }
            JSONArray arr = obj.optJSONArray("orders"); if(arr == null) arr = obj.optJSONArray("data");
            if(arr == null || arr.length() == 0){ list.addView(card("Belum ada pesanan aktif.")); return; }
            int shown = 0;
            for(int i=0;i<arr.length();i++){
                JSONObject o = arr.optJSONObject(i); if(o == null) continue;
                String st = o.optString("status","pending").trim().toLowerCase();
                if(st.equals("finished")||st.equals("completed")||st.equals("cancelled")||st.equals("canceled")||st.equals("merchant_rejected")) continue;
                list.addView(orderView(o)); shown++;
            }
            if(shown == 0) list.addView(card("Belum ada pesanan aktif."));
        } catch(Exception e){ list.addView(card("Response pesanan tidak valid."));}
    }

    private LinearLayout orderView(JSONObject o){
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(round(Color.WHITE, dp(18))); box.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,dp(12)); box.setLayoutParams(lp);

        String displayId = s(o,"order_id","id");
        String actionId = s(o,"id");
        if(actionId.isEmpty()) actionId = s(o,"order_numeric_id","order_db_id","order_id");

        String status = o.optString("status","pending");
        long price = o.optLong("price", o.optLong("total", 0));
        String address = s(o,"delivery_address","destination_address","address");
        String driver = s(o,"driver","driver_name");
        box.addView(tv("Order #" + displayId, 17, NAVY, true));
        box.addView(tv(statusLabel(status), 13, BLUE, true));
        box.addView(tv("Total: " + rupiah(price), 14, TEXT, true));
        box.addView(tv("Tujuan: " + (address.isEmpty() ? "-" : address), 13, MUTED, false));
        box.addView(tv("Driver: " + (driver.isEmpty() ? "Belum ada driver" : driver), 13, MUTED, false));

        if("pending".equalsIgnoreCase(status)){
            LinearLayout actions = row();
            Button accept = btn("Terima");
            Button reject = outlineBtn("Tolak");
            LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
            aLp.setMargins(0, dp(6), dp(4), 0);
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
            rLp.setMargins(dp(4), dp(6), 0, 0);
            actions.addView(accept, aLp);
            actions.addView(reject, rLp);
            box.addView(actions);

            final String finalActionId = actionId;
            final String finalDisplayId = displayId;
            accept.setOnClickListener(v -> update(finalActionId, finalDisplayId, "merchant_accepted"));
            reject.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                    .setTitle("Tolak Pesanan")
                    .setMessage("Yakin ingin menolak pesanan #" + finalDisplayId + "?")
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Tolak", (d,w) -> update(finalActionId, finalDisplayId, "merchant_rejected")).show());
        }
        return box;
    }

    private String statusLabel(String s){
        if(s == null) return "-";
        switch(s.trim().toLowerCase()){
            case "pending": return "Menunggu Merchant";
            case "merchant_accepted": return "Diterima Merchant";
            case "merchant_rejected": return "Ditolak Merchant";
            case "taken": return "Driver Menuju Pickup";
            case "arrived_pickup": return "Driver Tiba di Resto";
            case "on_delivery": return "Menuju Customer";
            case "arrived_delivery": return "Driver Tiba di Customer";
            case "finished":
            case "completed": return "Selesai";
            case "canceled":
            case "cancelled": return "Dibatalkan";
        }
        return s;
    }

    private void update(String id, String displayId, String status){
        if(id == null || id.trim().isEmpty()){
            alert("Data tidak lengkap", "ID pesanan tidak ditemukan. Muat ulang halaman pesanan lalu coba lagi.");
            return;
        }
        updating = true;
        toast("Memproses order #" + displayId + "...");
        new Thread(() -> {
            try {
                JSONObject p = new JSONObject();
                p.put("id", id);
                p.put("order_id", displayId);
                p.put("status", status);
                p.put("username", username());
                p.put("user_id", userId());
                JSONObject r = new JSONObject(postJson(BASE + "updateMerchantOrder.php", p));
                runOnUiThread(() -> {
                    updating = false;
                    boolean success = r.optBoolean("success", false);
                    if(success){
                        toast(r.optString("message", status.equals("merchant_accepted") ? "Pesanan diterima" : "Pesanan ditolak"));
                        load();
                    }else{
                        alert("Gagal", r.optString("message", "Gagal mengubah status pesanan."));
                        load();
                    }
                });
            } catch(Exception e){ runOnUiThread(() -> { updating = false; alert("Error","Gagal mengubah status pesanan."); load(); }); }
        }).start();
    }
}
