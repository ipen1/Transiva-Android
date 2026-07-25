package com.transiva.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;

public class MerchantDashboardActivity extends MerchantBaseActivity {
    private LinearLayout root, grid, ordersTile;
    private TextView nameText, statusText, descText, todayText, ratingText, reviewText, orderBadgeText, orderTileIcon, orderTileTitle, orderTileSub;
    private Button storeStatusBtn;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final HashSet<String> notifiedOrders = new HashSet<>();
    private boolean firstLoad = true;
    private Runnable autoTask;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        build();
    }

    @Override protected void onResume(){
        super.onResume();
        startAuto();
    }

    @Override protected void onPause(){
        super.onPause();
        stopAuto();
    }

    private void build(){
        root = new LinearLayout(this);
        setContentView(page(root));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable heroBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#0B7CFF"), Color.parseColor("#0756C9")});
        heroBg.setCornerRadius(dp(24));
        hero.setBackground(heroBg);
        hero.setElevation(dp(5));
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, -2);
        heroLp.setMargins(0, 0, 0, dp(14));
        root.addView(hero, heroLp);

        TextView badge = tv("TRANSIVA  •  MERCHANT", 11, Color.parseColor("#DCEEFF"), true);
        hero.addView(badge);
        TextView heroTitle = tv("Kelola bisnis lebih mudah", 24, Color.WHITE, true);
        heroTitle.setPadding(0, dp(7), 0, dp(3));
        hero.addView(heroTitle);
        nameText = tv("Halo, " + (username().isEmpty() ? "Merchant" : username()), 13, Color.WHITE, false);
        hero.addView(nameText);
        TextView heroSub = tv("Pesanan, menu, ulasan, dan status toko dalam satu tempat.", 12, Color.parseColor("#DCEEFF"), false);
        heroSub.setPadding(0, dp(4), 0, 0);
        hero.addView(heroSub);

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        statusCard.setBackground(round(Color.WHITE, dp(22)));
        statusCard.setElevation(dp(3));
        root.addView(statusCard, new LinearLayout.LayoutParams(-1, -2));

        statusText = tv("Memuat...", 24, NAVY, true);
        descText = tv("Memuat status restoran...", 13, MUTED, false);
        statusCard.addView(tv("Status Restoran", 13, MUTED, false));
        statusCard.addView(statusText);
        statusCard.addView(descText);

        LinearLayout stats = row();
        statusCard.addView(stats);
        todayText = stat(stats, "Hari Ini", "0");
        ratingText = stat(stats, "Rating", "0.0 ⭐");
        reviewText = stat(stats, "Ulasan", "0");

        storeStatusBtn = btn("Memuat...");
        storeStatusBtn.setOnClickListener(v -> toggleStore());
        root.addView(storeStatusBtn);

        TextView section = tv("Menu Merchant", 17, NAVY, true);
        section.setPadding(dp(4), dp(14), dp(4), dp(8));
        root.addView(section);

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        root.addView(grid);

        LinearLayout r1 = row(); grid.addView(r1);
        tile(r1, "🍟", "Tambah Menu", () -> open(MerchantAddMenuActivity.class));
        tile(r1, "📋", "Daftar Menu", () -> open(MerchantMenuListActivity.class));

        LinearLayout r2 = row(); grid.addView(r2);
        ordersTile = orderTile(r2);
        tile(r2, "⭐", "Rating & Ulasan", () -> open(MerchantReviewsActivity.class));

        LinearLayout r3 = row(); grid.addView(r3);
        tile(r3, "🏪", "Profil Merchant", () -> open(MerchantRestaurantProfileActivity.class));
        tile(r3, "⚙️", "Pengaturan", () -> open(MerchantSettingsActivity.class));

        Button refresh = outlineBtn("↻  Refresh Dashboard");
        refresh.setOnClickListener(v -> loadAll());
        root.addView(refresh);

        Button logout = outlineBtn("Keluar");
        logout.setOnClickListener(v -> logout());
        root.addView(logout);
    }

    private TextView stat(LinearLayout parent, String label, String value){
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(6), dp(12), dp(6), dp(8));
        box.setBackground(round(Color.parseColor("#F1F6FF"), dp(16)));
        TextView l = tv(label, 11, MUTED, false);
        TextView v = tv(value, 16, NAVY, true);
        l.setGravity(Gravity.CENTER); v.setGravity(Gravity.CENTER);
        box.addView(l); box.addView(v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(dp(4), dp(12), dp(4), 0);
        parent.addView(box, lp);
        return v;
    }

    private void tile(LinearLayout parent, String icon, String text, final Runnable action){
        LinearLayout t = baseTile();
        TextView ic = tv(icon, 28, NAVY, true);
        TextView tx = tv(text, 13, TEXT, true);
        ic.setGravity(Gravity.CENTER); tx.setGravity(Gravity.CENTER);
        t.addView(ic); t.addView(tx);
        t.setOnClickListener(v -> action.run());
        parent.addView(t, tileLp());
    }

    private LinearLayout orderTile(LinearLayout parent){
        LinearLayout t = baseTile();

        orderBadgeText = tv("", 11, Color.WHITE, true);
        orderBadgeText.setGravity(Gravity.CENTER);
        orderBadgeText.setBackground(round(Color.parseColor("#EF4444"), dp(18)));
        orderBadgeText.setVisibility(TextView.GONE);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(34), dp(24));
        badgeLp.gravity = Gravity.RIGHT;
        t.addView(orderBadgeText, badgeLp);

        orderTileIcon = tv("🛵", 28, NAVY, true);
        orderTileTitle = tv("Pesanan", 13, TEXT, true);
        orderTileSub = tv("Tidak ada order", 11, MUTED, false);
        orderTileIcon.setGravity(Gravity.CENTER);
        orderTileTitle.setGravity(Gravity.CENTER);
        orderTileSub.setGravity(Gravity.CENTER);
        t.addView(orderTileIcon);
        t.addView(orderTileTitle);
        t.addView(orderTileSub);
        t.setOnClickListener(v -> open(MerchantOrdersActivity.class));
        parent.addView(t, tileLp());
        return t;
    }

    private LinearLayout baseTile(){
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(10), dp(8), dp(12));
        t.setBackground(round(Color.WHITE, dp(20)));
        t.setElevation(dp(2));
        return t;
    }

    private LinearLayout.LayoutParams tileLp(){
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(122), 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(8));
        return lp;
    }

    private void setOrderTileActive(int count){
        if(ordersTile == null) return;
        if(count > 0){
            ordersTile.setBackground(stroke(Color.parseColor("#EAF3FF"), BLUE, dp(20)));
            ordersTile.setElevation(dp(8));
            if(orderBadgeText != null){
                orderBadgeText.setText(count > 99 ? "99+" : String.valueOf(count));
                orderBadgeText.setVisibility(TextView.VISIBLE);
            }
            if(orderTileIcon != null) orderTileIcon.setText("🚨");
            if(orderTileTitle != null) orderTileTitle.setTextColor(BLUE);
            if(orderTileSub != null) {
                orderTileSub.setText(count + " order baru");
                orderTileSub.setTextColor(Color.parseColor("#EF4444"));
            }
        }else{
            ordersTile.setBackground(round(Color.WHITE, dp(20)));
            ordersTile.setElevation(dp(2));
            if(orderBadgeText != null) orderBadgeText.setVisibility(TextView.GONE);
            if(orderTileIcon != null) orderTileIcon.setText("🛵");
            if(orderTileTitle != null) orderTileTitle.setTextColor(TEXT);
            if(orderTileSub != null) {
                orderTileSub.setText("Tidak ada order");
                orderTileSub.setTextColor(MUTED);
            }
        }
    }

    private void startAuto(){
        stopAuto();
        firstLoad = true;
        loadAll();
        autoTask = () -> { loadAll(); handler.postDelayed(autoTask, 5000); };
        handler.postDelayed(autoTask, 5000);
    }

    private void stopAuto(){
        if(autoTask != null) handler.removeCallbacks(autoTask);
    }

    private void loadAll(){
        final String u = username();
        if(u.isEmpty()){ alert("Sesi", "Silakan login ulang."); return; }
        new Thread(() -> {
            try {
                String dash = get(BASE + "getMerchantDashboard.php?username=" + enc(u) + "&v=" + System.currentTimeMillis());
                String orders = get(BASE + "getMerchantOrders.php?username=" + enc(u) + "&v=" + System.currentTimeMillis());
                runOnUiThread(() -> { showDash(dash); checkOrders(orders); });
            } catch(Exception e){ runOnUiThread(() -> descText.setText("Koneksi gagal")); }
        }).start();
    }

    private void showDash(String json){
        try {
            JSONObject d = new JSONObject(json);
            boolean open = d.optInt("is_open", 0) == 1;
            statusText.setText(open ? "🟢 Buka" : "🔴 Tutup");
            descText.setText(open ? "Restoran sedang menerima pesanan" : "Restoran sedang tidak menerima pesanan");
            todayText.setText(String.valueOf(d.optInt("today_orders", 0)));
            ratingText.setText(String.format(java.util.Locale.US, "%.1f ⭐", d.optDouble("rating", 0)));
            reviewText.setText(d.optInt("review_count", 0) + " ulasan");
            storeStatusBtn.setText(open ? "🔴 Tutup Restoran" : "🟢 Buka Restoran");
            storeStatusBtn.setTag(open ? "1" : "0");
        } catch(Exception e){ descText.setText("Dashboard belum terbaca"); }
    }

    private void checkOrders(String json){
        try {
            JSONArray arr = new JSONObject(json).optJSONArray("orders");
            int pending = 0;
            HashSet<String> current = new HashSet<>();
            if(arr != null){
                for(int i=0;i<arr.length();i++){
                    JSONObject o = arr.optJSONObject(i);
                    if(o == null) continue;
                    String st = o.optString("status","").trim().toLowerCase();
                    if(!"pending".equals(st)) continue;
                    pending++;
                    String id = s(o,"order_id","id");
                    if(id.isEmpty()) continue;
                    current.add(id);
                    if(!notifiedOrders.contains(id)){
                        if(!firstLoad) toast("Pesanan merchant baru #" + id);
                        notifiedOrders.add(id);
                    }
                }
            }
            notifiedOrders.retainAll(current);
            firstLoad = false;
            setOrderTileActive(pending);
        } catch(Exception ignored){ setOrderTileActive(0); }
    }

    private void toggleStore(){
        final String u = username();
        final int current = "1".equals(String.valueOf(storeStatusBtn.getTag())) ? 1 : 0;
        final int next = current == 1 ? 0 : 1;
        storeStatusBtn.setEnabled(false);
        storeStatusBtn.setText("Memproses...");
        new Thread(() -> {
            try {
                JSONObject p = new JSONObject();
                p.put("username", u);
                p.put("is_open", next);
                JSONObject r = new JSONObject(postJson(BASE + "updateRestaurantStatus.php", p));
                runOnUiThread(() -> {
                    toast(r.optString("message", r.optBoolean("success") ? "Berhasil" : "Gagal"));
                    storeStatusBtn.setEnabled(true);
                    loadAll();
                });
            } catch(Exception e){ runOnUiThread(() -> { storeStatusBtn.setEnabled(true); alert("Error","Koneksi gagal"); loadAll(); }); }
        }).start();
    }
}
