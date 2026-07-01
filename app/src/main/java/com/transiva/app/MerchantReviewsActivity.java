package com.transiva.app;

import android.os.Bundle;
import android.graphics.Color;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MerchantReviewsActivity extends MerchantBaseActivity {
    private LinearLayout root, list;

    @Override protected void onCreate(Bundle b){ super.onCreate(b); build(); load(); }

    private void build(){
        root = new LinearLayout(this); setContentView(page(root));
        root.addView(title("Rating & Ulasan"));
        root.addView(sub("Ulasan customer untuk restoran kamu"));
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); root.addView(list);
        Button back = outlineBtn("← Kembali"); back.setOnClickListener(v -> finish()); root.addView(back);
    }

    private void load(){
        list.removeAllViews(); list.addView(card("Memuat ulasan..."));
        new Thread(() -> {
            try{
                JSONObject res = new JSONObject(get(BASE + "getMerchantReviews.php?username=" + enc(username()) + "&v=" + System.currentTimeMillis()));
                runOnUiThread(() -> show(res));
            }catch(Exception e){ runOnUiThread(() -> { list.removeAllViews(); list.addView(card("Koneksi gagal.")); });}
        }).start();
    }

    private void show(JSONObject res){
        list.removeAllViews();
        if(!res.optBoolean("success", false)){ list.addView(card(res.optString("message","Gagal memuat ulasan"))); return; }
        JSONObject sum = res.optJSONObject("restaurant");
        if(sum != null) list.addView(card("⭐ Rating Restoran\n" + String.format(java.util.Locale.US, "%.1f", sum.optDouble("rating",0)) + " dari " + sum.optInt("review_count",0) + " ulasan"));
        JSONArray arr = res.optJSONArray("reviews");
        if(arr == null || arr.length()==0){ list.addView(card("Belum ada ulasan.\nUlasan customer akan tampil di sini.")); return; }
        for(int i=0;i<arr.length();i++){
            JSONObject r = arr.optJSONObject(i); if(r == null) continue;
            int rating = Math.max(0, Math.min(5, r.optInt("rating",0)));
            String stars = new String(new char[rating]).replace("\0","★") + new String(new char[5-rating]).replace("\0","☆");
            list.addView(card("Order #" + s(r,"order_id","id") + "\n" + stars + "\n" + s(r,"review","comment") + "\nDriver: " + (s(r,"driver","driver_name").isEmpty() ? "-" : s(r,"driver","driver_name"))));
        }
    }
}
