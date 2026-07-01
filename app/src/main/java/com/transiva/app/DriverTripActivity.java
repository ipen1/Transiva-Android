package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;

public class DriverTripActivity extends Activity {
    private static final String BASE_URL = "https://transiva.my.id/server/";
    private static final String PREF_NAME = "transiva";
    private static final int TIMEOUT_MS = 20000;
    private static final float ARRIVE_RADIUS_METER = 100f;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private LinearLayout root;
    private ProgressBar progressBar;
    private TextView statusBadge, distanceInfo, distanceHint;
    private WebView mapView;
    private Button arrivedPickupBtn, startDeliveryBtn, arrivedDeliveryBtn, finishBtn;
    private JSONObject order;
    private String driverUsername = "";
    private String orderKind = "order";
    private LocationManager locationManager;
    private LocationListener locationListener;
    private double lastDriverLat = 0, lastDriverLng = 0;
    private boolean updatingStatus = false;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        try{
            getWindow().setStatusBarColor(Color.WHITE);
            getWindow().setNavigationBarColor(Color.WHITE);
            if(Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }catch(Exception ignored){}
        loadSession();
        loadOrder();
        buildBase();
        if(order == null){ renderEmpty(); return; }
        renderOrder();
        refreshButtons();
        startLocationWatch();
    }
    @Override protected void onResume(){ super.onResume(); if(order != null) startLocationWatch(); }
    @Override protected void onPause(){ stopLocationWatch(); super.onPause(); }
    @Override protected void onDestroy(){ stopLocationWatch(); try{ if(mapView != null) mapView.destroy(); }catch(Exception ignored){} super.onDestroy(); }

    private void loadSession(){
        try{ SessionManager s = new SessionManager(this); driverUsername = first(s.getUsername(), s.getName(), ""); }catch(Exception ignored){}
        if(driverUsername.isEmpty()) driverUsername = getSharedPreferences(PREF_NAME, MODE_PRIVATE).getString("username", "");
    }
    private void loadOrder(){
        try{
            String raw = first(getIntent().getStringExtra("order_json"), getIntent().getStringExtra("active_order_json"), pref("driver_active_order_json"), pref("active_order_json"), pref("activeOrder"));
            if(raw.trim().startsWith("{")) order = new JSONObject(raw);
        }catch(Exception ignored){}
        if(order == null){
            String id = first(getIntent().getStringExtra("order_id"), pref("driver_active_order_id"));
            if(!id.isEmpty()){
                order = new JSONObject();
                try{
                    order.put("id", id); order.put("order_id", id); order.put("status", first(pref("driver_active_order_status"), "taken"));
                    order.put("pickup_address", pref("driver_active_pickup_address")); order.put("delivery_address", pref("driver_active_delivery_address"));
                    order.put("pickup_lat", pref("driver_active_pickup_lat")); order.put("pickup_lng", pref("driver_active_pickup_lng"));
                    order.put("delivery_lat", pref("driver_active_delivery_lat")); order.put("delivery_lng", pref("driver_active_delivery_lng"));
                    order.put("price", pref("driver_active_price"));
                }catch(Exception ignored){}
            }
        }
        if(order != null){
            orderKind = first(getIntent().getStringExtra("order_kind"), order.optString("order_kind"), order.optString("source_table"), order.optString("type"), pref("driver_active_order_kind"), "order").toLowerCase(Locale.US);
            orderKind = orderKind.contains("pickup") ? "pickup" : "order";
            saveActiveOrder();
        }
    }
    private void buildBase(){
        FrameLayout page = new FrameLayout(this); page.setBackgroundColor(Color.parseColor("#F3F8FF"));
        ScrollView scroll = new ScrollView(this); page.addView(scroll, new FrameLayout.LayoutParams(-1,-1));
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18), dp(22), dp(18), dp(26));
        scroll.addView(root, new ScrollView.LayoutParams(-1,-2));
        progressBar = new ProgressBar(this); progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(48), dp(48)); pp.gravity = Gravity.CENTER; page.addView(progressBar, pp);
        setContentView(page);
    }
    private void renderEmpty(){
        root.removeAllViews(); top("Driver Trip", "Status perjalanan order native");
        LinearLayout c = card(); c.setPadding(dp(18), dp(16), dp(18), dp(16));
        c.addView(text("Order tidak ditemukan.", 16, "#64748B", false));
        Button back = outline("Kembali ke Dashboard"); back.setOnClickListener(v -> finish()); c.addView(back, btnLp(14)); add(c,0,dp(8),0,0);
    }
    private void renderOrder(){
        root.removeAllViews(); top("Driver Trip", "Status perjalanan order native");
        addHeaderCard(); addLocationCard("📍 Lokasi Pickup", pickupAddress(), true); addLocationCard("🏁 Lokasi Delivery", deliveryAddress(), false);
        addMapCard(); addFoodOrNoteCard(); addActions();
    }
    private void top(String title, String sub){
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0,0,0,dp(14));
        TextView back = text("‹", 38, "#0B3A78", true); back.setGravity(Gravity.CENTER); back.setBackground(round("#FFFFFF", dp(22))); back.setOnClickListener(v -> finish());
        row.addView(back, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL); col.setPadding(dp(14),0,0,0);
        col.addView(text(title, 27, "#0B3A78", true)); col.addView(text(sub, 14, "#64748B", false)); row.addView(col, new LinearLayout.LayoutParams(0,-2,1));
        TextView online = text("• Online", 13, "#059669", true); online.setGravity(Gravity.CENTER); online.setPadding(dp(12), dp(8), dp(12), dp(8)); online.setBackground(round("#DCFCE7", dp(22))); row.addView(online);
        root.addView(row);
    }
    private void addHeaderCard(){
        LinearLayout h = card(); h.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); h.addView(top);
        LinearLayout left = new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL); top.addView(left, new LinearLayout.LayoutParams(0,-2,1));
        left.addView(text(cleanServiceLabel(), 14, "#64748B", true)); TextView id = text("#" + orderId(), 24, "#0B3A78", true); id.setMaxLines(2); left.addView(id);
        statusBadge = text(statusLabel(status()), 12, "#FFFFFF", true); statusBadge.setGravity(Gravity.CENTER); statusBadge.setPadding(dp(12), dp(7), dp(12), dp(7)); statusBadge.setBackground(gradient("#086BFF", "#2EA2FF", dp(18))); top.addView(statusBadge);
        LinearLayout stats = new LinearLayout(this); stats.setOrientation(LinearLayout.HORIZONTAL); stats.setGravity(Gravity.CENTER); LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2); sp.setMargins(0, dp(14),0,0); h.addView(stats, sp);
        mini(stats, "💰", "Total Bayar", rupiah(optDouble("price", "fare", "total"))); mini(stats, "🛵", "Jarak", one(optDouble("distance_km")) + " KM"); mini(stats, "⏱️", "Estimasi", zero(optDouble("duration_minutes")) + " menit");
        distanceInfo = text("📡 Mengukur jarak driver...", 13, "#64748B", false); distanceInfo.setPadding(0, dp(10),0,0); h.addView(distanceInfo);
        distanceHint = text("", 13, "#059669", true); distanceHint.setPadding(dp(12), dp(9), dp(12), dp(9)); distanceHint.setBackground(stroke("#ECFDF5", "#86EFAC", dp(14), 1)); LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1,-2); hp.setMargins(0, dp(8),0,0); h.addView(distanceHint, hp);
        add(h,0,dp(8),0,dp(12));
    }
    private void mini(LinearLayout parent, String icon, String label, String value){
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER); box.setPadding(dp(3),0,dp(3),0);
        TextView i = text(icon, 20, "#0B3A78", false); i.setGravity(Gravity.CENTER); box.addView(i);
        TextView l = text(label, 11, "#64748B", false); l.setGravity(Gravity.CENTER); box.addView(l);
        TextView v = text(value, 13, "#111827", true); v.setGravity(Gravity.CENTER); box.addView(v);
        parent.addView(box, new LinearLayout.LayoutParams(0,-2,1));
    }
    private void addLocationCard(String title, String body, boolean pickup){
        LinearLayout c = card(); c.setPadding(dp(16), dp(13), dp(16), dp(13));
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); c.addView(row);
        LinearLayout txt = new LinearLayout(this); txt.setOrientation(LinearLayout.VERTICAL); row.addView(txt, new LinearLayout.LayoutParams(0,-2,1));
        txt.addView(text(title, 16, "#0B3A78", true)); TextView b = text(first(body, "-"), 15, "#111827", false); b.setPadding(0,dp(6),0,0); txt.addView(b);
        Button nav = outline("➤ Navigasi"); nav.setOnClickListener(v -> openExternalMap(pickup)); row.addView(nav, new LinearLayout.LayoutParams(dp(132), dp(48)));
        add(c,0,0,0,dp(12));
    }
    private void addMapCard(){
        LinearLayout c = card(); c.setPadding(dp(12), dp(12), dp(12), dp(12));
        c.addView(text("🗺️ Peta Perjalanan", 16, "#0B3A78", true)); c.addView(text("Marker pickup, delivery, dan driver tampil realtime sederhana.", 12, "#64748B", false));
        mapView = new WebView(this); try{ WebSettings st = mapView.getSettings(); st.setJavaScriptEnabled(true); st.setDomStorageEnabled(true); }catch(Exception ignored){}
        mapView.loadDataWithBaseURL("https://transiva.my.id/", mapHtml(), "text/html", "UTF-8", null); LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, dp(230)); mp.setMargins(0,dp(8),0,0); c.addView(mapView, mp); add(c,0,0,0,dp(12));
    }
    private String mapHtml(){
        double pLat=coord("pickup_lat","user_lat"), pLng=coord("pickup_lng","user_lng"), dLat=coord("delivery_lat","destination_lat"), dLng=coord("delivery_lng","destination_lng");
        double cLat = valid(pLat,pLng) ? pLat : (valid(dLat,dLng) ? dLat : -0.9), cLng = valid(pLat,pLng) ? pLng : (valid(dLat,dLng) ? dLng : 119.87);
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'><script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script><style>html,body,#map{height:100%;margin:0} .pin{font-size:28px}</style></head><body><div id='map'></div><script>var m=L.map('map',{zoomControl:true}).setView(["+cLat+","+cLng+"],16);L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19}).addTo(m);function icon(t){return L.divIcon({html:'<div class=pin>'+t+'</div>',className:'',iconSize:[34,34]});}var p=["+pLat+","+pLng+"],d=["+dLat+","+dLng+"];if(p[0]&&p[1])L.marker(p,{icon:icon('📍')}).addTo(m);if(d[0]&&d[1])L.marker(d,{icon:icon('🏁')}).addTo(m);var drv=null;window.updateDrv=function(a,b){if(!a||!b)return;if(!drv)drv=L.marker([a,b],{icon:icon('🛵')}).addTo(m);else drv.setLatLng([a,b]);};</script></body></html>";
    }
    private void addFoodOrNoteCard(){
        JSONObject food = parseFoodNote();
        if(food == null){ addPlainNoteCard(); return; }
        LinearLayout c = card(); c.setPadding(dp(16), dp(13), dp(16), dp(13));
        c.addView(text("🍔 Detail Order Makanan", 18, "#0B3A78", true));
        rowText(c, "🏪 Resto", first(food.optString("restaurant_name"), pickupAddress(), "Resto"));
        rowText(c, "🧾 Total Makanan", rupiah(food.optDouble("food_total", 0)));
        rowText(c, "🛵 Ongkir", rupiah(food.optDouble("delivery_fee", optDouble("price"))));
        rowText(c, "💳 Pembayaran", first(food.optString("payment_label"), food.optString("payment_method"), "-"));
        rowText(c, "💰 Total Bayar", rupiah(food.optDouble("total", optDouble("price", "total"))));
        TextView menuTitle = text("📦 Menu Pesanan", 16, "#0B3A78", true); menuTitle.setPadding(0, dp(12),0,dp(6)); c.addView(menuTitle);
        JSONArray items = food.optJSONArray("items");
        if(items == null || items.length() == 0){ c.addView(text("-", 14, "#64748B", false)); }
        else{
            for(int i=0;i<items.length();i++){
                JSONObject it = items.optJSONObject(i); if(it == null) continue;
                String name = first(it.optString("name"), it.optString("food_name"), it.optString("menu_name"), "Menu");
                int qty = it.optInt("qty", it.optInt("quantity", 1));
                double subtotal = it.optDouble("subtotal", it.optDouble("total", it.optDouble("price",0) * qty));
                LinearLayout r = new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(0, dp(7),0,dp(7));
                LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.addView(text(name, 15, "#111827", true)); l.addView(text(qty + "x pesanan", 12, "#64748B", false)); r.addView(l, new LinearLayout.LayoutParams(0,-2,1));
                r.addView(text(rupiah(subtotal), 14, "#111827", true)); c.addView(r);
            }
        }
        add(c,0,0,0,dp(12));
    }
    private JSONObject parseFoodNote(){
        try{ JSONObject d = new JSONObject(first(order.optString("note"), "{}")); return "food".equalsIgnoreCase(d.optString("type")) ? d : null; }catch(Exception e){ return null; }
    }
    private void addPlainNoteCard(){
        String note = first(order.optString("note"), order.optString("item_note"), order.optString("description"), "-");
        LinearLayout c = card(); c.setPadding(dp(16), dp(13), dp(16), dp(13)); c.addView(text(orderKind.equals("pickup") ? "📦 Detail Paket" : "📝 Catatan Customer", 16, "#0B3A78", true));
        TextView n = text(note, 14, "#111827", false); n.setPadding(0, dp(6),0,0); c.addView(n); add(c,0,0,0,dp(12));
    }
    private void rowText(LinearLayout p, String l, String v){
        LinearLayout r = new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(0, dp(7),0,dp(7));
        r.addView(text(l, 14, "#64748B", false), new LinearLayout.LayoutParams(0,-2,1)); r.addView(text(v, 14, "#111827", true)); p.addView(r);
    }
    private void addActions(){
        LinearLayout c = card(); c.setPadding(dp(16), dp(16), dp(16), dp(16)); c.addView(text("⚡ Aksi Perjalanan", 18, "#0B3A78", true));
        Button chat = primary("💬 Chat Customer"); chat.setOnClickListener(v -> openChat()); c.addView(chat, btnLp(12));
        arrivedPickupBtn = green("📍 Tiba di Lokasi Pickup"); arrivedPickupBtn.setOnClickListener(v -> confirm("Tiba di lokasi pickup?", "arrived_pickup")); c.addView(arrivedPickupBtn, btnLp(10));
        startDeliveryBtn = green(orderKind.equals("pickup") ? "📦 Paket Sudah Diambil" : "🛵 Lanjutkan Perjalanan"); startDeliveryBtn.setOnClickListener(v -> confirm("Mulai perjalanan ke lokasi delivery?", "on_delivery")); c.addView(startDeliveryBtn, btnLp(10));
        arrivedDeliveryBtn = green("🏁 Tiba di Lokasi Delivery"); arrivedDeliveryBtn.setOnClickListener(v -> confirm("Tiba di lokasi delivery?", "arrived_delivery")); c.addView(arrivedDeliveryBtn, btnLp(10));
        finishBtn = green("✅ Selesaikan Order"); finishBtn.setOnClickListener(v -> confirm("Selesaikan order ini sekarang?", "finished")); c.addView(finishBtn, btnLp(10));
        Button back = outline("← Kembali ke Dashboard"); back.setOnClickListener(v -> finish()); c.addView(back, btnLp(10));
        add(c,0,0,0,dp(18));
    }
    private void startLocationWatch(){
        if(order == null) return;
        if(Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){ requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 701); return; }
        try{
            locationManager = (LocationManager)getSystemService(LOCATION_SERVICE); if(locationManager == null) return; stopLocationWatch();
            locationListener = new LocationListener(){ @Override public void onLocationChanged(Location l){ if(l==null)return; lastDriverLat=l.getLatitude(); lastDriverLng=l.getLongitude(); updateDriverLocation(l); updateMap(); refreshButtons(); } @Override public void onStatusChanged(String p,int s,Bundle e){} @Override public void onProviderEnabled(String p){} @Override public void onProviderDisabled(String p){} };
            try{ locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 2, locationListener, Looper.getMainLooper()); }catch(Exception ignored){}
            try{ locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000, 2, locationListener, Looper.getMainLooper()); }catch(Exception ignored){}
        }catch(Exception ignored){}
    }
    private void stopLocationWatch(){ try{ if(locationManager != null && locationListener != null) locationManager.removeUpdates(locationListener); }catch(Exception ignored){} locationListener = null; }
    private void updateMap(){ try{ if(mapView != null && Build.VERSION.SDK_INT >= 19) mapView.evaluateJavascript("if(window.updateDrv)updateDrv("+lastDriverLat+","+lastDriverLng+");", null); }catch(Exception ignored){} }
    private void refreshButtons(){
        if(arrivedPickupBtn==null) return; String st = status(); arrivedPickupBtn.setVisibility(View.GONE); startDeliveryBtn.setVisibility(View.GONE); arrivedDeliveryBtn.setVisibility(View.GONE); finishBtn.setVisibility(View.GONE); if(statusBadge != null) statusBadge.setText(statusLabel(st));
        if(st.equals("taken")){ float pd = distanceTo(coord("pickup_lat","user_lat"), coord("pickup_lng","user_lng")); if(pd >= 0){ distanceInfo.setText("📍 Jarak ke pickup: " + meter(pd)); distanceHint.setText(pd <= ARRIVE_RADIUS_METER ? "✓ Kamu sudah dekat pickup. Tombol tiba pickup aktif." : "Tombol tiba pickup aktif saat jarak ≤ " + (int)ARRIVE_RADIUS_METER + " meter."); if(pd <= ARRIVE_RADIUS_METER) arrivedPickupBtn.setVisibility(View.VISIBLE); } else { distanceInfo.setText("📡 Menunggu GPS untuk mengukur jarak pickup..."); distanceHint.setText("Pastikan GPS aktif dan izin lokasi diberikan."); arrivedPickupBtn.setVisibility(View.VISIBLE); } return; }
        if(st.equals("arrived_pickup")){ distanceInfo.setText("✅ Driver sudah tiba di pickup."); distanceHint.setText("Lanjutkan perjalanan setelah pesanan siap."); startDeliveryBtn.setVisibility(View.VISIBLE); return; }
        if(st.equals("on_delivery")){ float dd = distanceTo(coord("delivery_lat","destination_lat"), coord("delivery_lng","destination_lng")); if(dd >= 0){ distanceInfo.setText("🏁 Jarak ke delivery: " + meter(dd)); distanceHint.setText(dd <= ARRIVE_RADIUS_METER ? "✓ Kamu sudah dekat delivery. Tombol tiba delivery aktif." : "Tombol tiba delivery aktif saat jarak ≤ " + (int)ARRIVE_RADIUS_METER + " meter."); if(dd <= ARRIVE_RADIUS_METER) arrivedDeliveryBtn.setVisibility(View.VISIBLE); } else { distanceInfo.setText("📡 Menunggu GPS untuk mengukur jarak delivery..."); distanceHint.setText("Pastikan GPS aktif."); arrivedDeliveryBtn.setVisibility(View.VISIBLE); } return; }
        if(st.equals("arrived_delivery")){ distanceInfo.setText("🏁 Driver sudah tiba di delivery."); distanceHint.setText("Selesaikan order setelah pesanan diterima customer."); finishBtn.setVisibility(View.VISIBLE); return; }
        if(st.equals("finished")||st.equals("completed")){ distanceInfo.setText("✅ Order selesai."); distanceHint.setText("Terima kasih."); }
    }
    private void confirm(String msg, String next){ if(updatingStatus)return; new AlertDialog.Builder(this).setTitle("Konfirmasi").setMessage(msg).setNegativeButton("Batal",null).setPositiveButton("Ya",(d,w)->updateStatus(next)).show(); }
    private void updateStatus(String next){
        updatingStatus = true; setLoading(true);
        new Thread(() -> { try{
            JSONObject p = new JSONObject(); p.put("id", internalId()); p.put("order_id", orderId()); p.put("driver", driverUsername); p.put("order_kind", orderKind); p.put("status", next);
            String endpoint = endpoint(next); JSONObject r = postJson(BASE_URL + endpoint, p); boolean ok = r.optBoolean("success", false); String m = first(r.optString("message"), ok ? "Status berhasil diperbarui." : "Gagal update status.");
            mainHandler.post(() -> { updatingStatus=false; setLoading(false); if(ok){ try{ order.put("status", next); }catch(Exception ignored){} saveActiveOrder(); refreshButtons(); info("Berhasil", m); if(next.equals("finished") || next.equals("completed")){ clearActiveOrder(); finish(); } } else info("Gagal", m); });
        }catch(Exception e){ mainHandler.post(() -> { updatingStatus=false; setLoading(false); info("Koneksi gagal", "Tidak bisa update status ke server."); }); }}).start();
    }
    private String endpoint(String n){ if(n.equals("arrived_pickup"))return "driverArrivedPickup.php"; if(n.equals("on_delivery"))return "driverStartDelivery.php"; if(n.equals("arrived_delivery"))return "driverArrivedDelivery.php"; if(n.equals("finished")||n.equals("completed"))return "finishOrder.php"; return "driver_update_unified_status.php"; }
    private void updateDriverLocation(Location loc){ new Thread(() -> { try{ JSONObject p = new JSONObject(); p.put("username", driverUsername); p.put("driver", driverUsername); p.put("order_id", orderId()); p.put("latitude", loc.getLatitude()); p.put("longitude", loc.getLongitude()); postJson(BASE_URL + "updateDriverLocation.php", p); }catch(Exception ignored){} }).start(); }
    private void openChat(){ try{ String roomId = first(order.optString("room_id"), pref("active_chat_room_id"), "ROOM-" + orderId()).trim().replace("_", "-").toUpperCase(Locale.US).replaceAll("[^A-Z0-9\\-]", ""); if(!roomId.startsWith("ROOM-")) roomId = "ROOM-" + roomId; String customerName = first(order.optString("customer_name"), order.optString("customer"), order.optString("username"), order.optString("user_id"), "Customer"); getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().putString("active_order_id", orderId()).putString("active_chat_order_id", orderId()).putString("active_chat_room_id", roomId).putString("active_chat_driver_name", driverUsername).putString("active_chat_customer_name", customerName).putString("active_chat_order_status", status()).apply(); Intent i = new Intent(this, DriverChatActivity.class); i.putExtra("order_id", orderId()); i.putExtra("room_id", roomId); i.putExtra("driver_name", driverUsername); i.putExtra("customer_name", customerName); i.putExtra("order_status", status()); startActivity(i); }catch(Exception e){ info("Chat", "Gagal membuka chat."); } }
    private void openExternalMap(boolean pickup){ double lat = pickup ? coord("pickup_lat","user_lat") : coord("delivery_lat","destination_lat"); double lng = pickup ? coord("pickup_lng","user_lng") : coord("delivery_lng","destination_lng"); if(!valid(lat,lng)){ info("Lokasi", "Koordinat belum tersedia."); return; } try{ startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + lat + "," + lng))); }catch(Exception e){ startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=" + lat + "," + lng))); } }
    private JSONObject postJson(String urlText, JSONObject payload)throws Exception{ HttpURLConnection c=(HttpURLConnection)new URL(urlText).openConnection(); c.setConnectTimeout(TIMEOUT_MS); c.setReadTimeout(TIMEOUT_MS); c.setRequestMethod("POST"); c.setRequestProperty("Content-Type","application/json; charset=utf-8"); c.setRequestProperty("Accept","application/json"); c.setDoOutput(true); OutputStream os=c.getOutputStream(); os.write(payload.toString().getBytes(StandardCharsets.UTF_8)); os.flush(); os.close(); InputStream is=c.getResponseCode()>=400?c.getErrorStream():c.getInputStream(); BufferedReader br=new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)); StringBuilder sb=new StringBuilder(); String line; while((line=br.readLine())!=null) sb.append(line); br.close(); c.disconnect(); String body=sb.toString().trim(); return body.isEmpty()?new JSONObject():new JSONObject(body); }
    private void saveActiveOrder(){ if(order==null)return; getSharedPreferences(PREF_NAME,MODE_PRIVATE).edit().putString("driver_active_order_json", order.toString()).putString("driver_active_order_id", orderId()).putString("driver_active_order_kind", orderKind).putString("driver_active_order_status", status()).putString("driver_active_pickup_address", pickupAddress()).putString("driver_active_delivery_address", deliveryAddress()).putString("driver_active_pickup_lat", String.valueOf(coord("pickup_lat","user_lat"))).putString("driver_active_pickup_lng", String.valueOf(coord("pickup_lng","user_lng"))).putString("driver_active_delivery_lat", String.valueOf(coord("delivery_lat","destination_lat"))).putString("driver_active_delivery_lng", String.valueOf(coord("delivery_lng","destination_lng"))).putString("driver_active_price", String.valueOf(optDouble("price","fare","total"))).apply(); }
    private void clearActiveOrder(){ getSharedPreferences(PREF_NAME,MODE_PRIVATE).edit().remove("driver_active_order_json").remove("driver_active_order_id").remove("driver_active_order_kind").remove("driver_active_order_status").apply(); }
    private String orderId(){ return first(order.optString("order_id"), order.optString("id"), "-"); } private String internalId(){ return first(order.optString("id"), order.optString("order_id"), ""); } private String status(){ return first(order.optString("status"), "taken").toLowerCase(Locale.US).trim(); }
    private String pickupAddress(){ return first(order.optString("pickup_address"), order.optString("pickup"), order.optString("sender_address"), "-"); } private String deliveryAddress(){ return first(order.optString("delivery_address"), order.optString("destination_address"), order.optString("destination"), order.optString("receiver_address"), "-"); }
    private String cleanServiceLabel(){ String s=first(order.optString("service_name"), order.optString("order_type"), orderKind.equals("pickup") ? "TransPickup" : "Food Delivery"); return s.trim(); }
    private double coord(String a, String b){ try{return Double.parseDouble(first(order.optString(a), order.optString(b), "0"));}catch(Exception e){return 0;} } private double optDouble(String... keys){ for(String k: keys){ try{ if(order.has(k)) return Double.parseDouble(order.optString(k,"0")); }catch(Exception ignored){} } return 0; }
    private String statusLabel(String s){ if(s.equals("taken"))return "Menuju Pickup"; if(s.equals("arrived_pickup"))return "Tiba Pickup"; if(s.equals("on_delivery"))return "Menuju Delivery"; if(s.equals("arrived_delivery"))return "Tiba Delivery"; if(s.equals("merchant_accepted"))return "Diterima Merchant"; if(s.equals("finished")||s.equals("completed"))return "Selesai"; return first(s,"Menuju Pickup"); }
    private float distanceTo(double lat, double lng){ if(!valid(lastDriverLat,lastDriverLng)||!valid(lat,lng))return -1; float[] r=new float[1]; Location.distanceBetween(lastDriverLat,lastDriverLng,lat,lng,r); return r[0]; }
    private boolean valid(double lat,double lng){ return lat!=0 && lng!=0 && !Double.isNaN(lat) && !Double.isNaN(lng); } private String meter(float m){ return m>=1000 ? one(m/1000.0)+" km" : Math.round(m)+" meter"; }
    private String rupiah(double v){ return "Rp " + NumberFormat.getNumberInstance(new Locale("id","ID")).format((long)v); } private String one(double v){ return String.format(Locale.US,"%.1f",v); } private String zero(double v){ return String.format(Locale.US,"%.0f",v); } private String pref(String key){ try{return getSharedPreferences(PREF_NAME,MODE_PRIVATE).getString(key,"");}catch(Exception e){return "";} }
    private String first(String... values){ if(values==null)return ""; for(String s: values) if(s!=null && s.trim().length()>0 && !"null".equalsIgnoreCase(s.trim())) return s.trim(); return ""; }
    private int dp(int v){ return (int)(v * getResources().getDisplayMetrics().density + .5f); } private void add(View v,int l,int t,int r,int b){ LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(l,t,r,b); root.addView(v,lp); }
    private LinearLayout.LayoutParams btnLp(int top){ LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52)); lp.setMargins(0, dp(top), 0, 0); return lp; }
    private LinearLayout card(){ LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setBackground(stroke("#FFFFFF", "#D7E6F8", dp(24), 1)); v.setElevation(dp(2)); return v; }
    private TextView text(String s,int sp,String color,boolean bold){ TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(Color.parseColor(color)); if(bold)t.setTypeface(Typeface.DEFAULT_BOLD); return t; }
    private Button primary(String s){ Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT_BOLD); b.setBackground(gradient("#086BFF", "#2EA2FF", dp(18))); return b; } private Button green(String s){ Button b=primary(s); b.setBackground(gradient("#10B981", "#059669", dp(18))); return b; } private Button outline(String s){ Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(Color.parseColor("#0B7CFF")); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT_BOLD); b.setBackground(stroke("#FFFFFF", "#9DCAFF", dp(18), 1)); return b; }
    private GradientDrawable round(String color,int radius){ GradientDrawable g=new GradientDrawable(); g.setColor(Color.parseColor(color)); g.setCornerRadius(radius); return g; } private GradientDrawable stroke(String color,String st,int radius,int sw){ GradientDrawable g=round(color,radius); g.setStroke(dp(sw), Color.parseColor(st)); return g; } private GradientDrawable gradient(String c1,String c2,int radius){ GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor(c1), Color.parseColor(c2)}); g.setCornerRadius(radius); return g; }
    private void setLoading(boolean b){ if(progressBar!=null) progressBar.setVisibility(b?View.VISIBLE:View.GONE); } private void info(String t,String m){ try{ new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("OK", null).show(); }catch(Exception ignored){} }
}
