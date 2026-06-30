package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.util.Base64;
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

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
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

    private TextView statusBadge, distanceInfo, distanceHint, distanceMini;
    private WebView mapView;
    private Button chatBtn, navPickupBtn, navDeliveryBtn, arrivedPickupBtn, startDeliveryBtn, arrivedDeliveryBtn, finishBtn;

    private JSONObject order;
    private String orderKind = "order";
    private String driverUsername = "";
    private LocationManager locationManager;
    private LocationListener locationListener;
    private double lastDriverLat = 0;
    private double lastDriverLng = 0;
    private boolean updatingStatus = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setStatusBarColor(Color.WHITE);
            getWindow().setNavigationBarColor(Color.WHITE);
            if (Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        } catch (Exception ignored) {}
        loadSession();
        loadOrderFromIntentOrPrefs();
        buildUi();
        if (order == null) { renderEmpty(); return; }
        renderOrder();
        refreshButtonsByStatusAndDistance();
        startLocationWatch();
    }

    @Override protected void onResume() { super.onResume(); if (order != null) startLocationWatch(); }
    @Override protected void onPause() { stopLocationWatch(); super.onPause(); }
    @Override protected void onDestroy() { stopLocationWatch(); try { if (mapView != null) mapView.destroy(); } catch(Exception ignored){} super.onDestroy(); }

    private void loadSession() {
        try { SessionManager s = new SessionManager(this); driverUsername = firstNonEmpty(s.getUsername(), s.getName(), ""); } catch(Exception ignored) {}
        if (driverUsername.length() == 0) driverUsername = getSharedPreferences(PREF_NAME, MODE_PRIVATE).getString("username", "");
    }

    private void loadOrderFromIntentOrPrefs() {
        try {
            String raw = firstNonEmpty(getIntent().getStringExtra("order_json"), getIntent().getStringExtra("active_order_json"), getStringPref("driver_active_order_json"), getStringPref("active_order_json"), getStringPref("activeOrder"));
            if (raw.length() > 0 && raw.trim().startsWith("{")) order = new JSONObject(raw);
        } catch(Exception ignored) {}
        if (order == null) {
            String id = firstNonEmpty(getIntent().getStringExtra("order_id"), getStringPref("driver_active_order_id"));
            if (id.length() > 0) {
                order = new JSONObject();
                try {
                    order.put("id", id); order.put("order_id", id); order.put("status", firstNonEmpty(getStringPref("driver_active_order_status"), "taken"));
                    order.put("pickup_address", getStringPref("driver_active_pickup_address")); order.put("delivery_address", getStringPref("driver_active_delivery_address"));
                    order.put("pickup_lat", getStringPref("driver_active_pickup_lat")); order.put("pickup_lng", getStringPref("driver_active_pickup_lng"));
                    order.put("delivery_lat", getStringPref("driver_active_delivery_lat")); order.put("delivery_lng", getStringPref("driver_active_delivery_lng"));
                    order.put("price", getStringPref("driver_active_price"));
                } catch(Exception ignored) {}
            }
        }
        if (order != null) {
            orderKind = firstNonEmpty(getIntent().getStringExtra("order_kind"), order.optString("order_kind"), order.optString("source_table"), order.optString("type"), getStringPref("driver_active_order_kind"), "order").toLowerCase(Locale.US);
            orderKind = orderKind.contains("pickup") ? "pickup" : "order";
            saveActiveOrder();
        }
    }

    private void buildUi() {
        FrameLayout page = new FrameLayout(this); page.setBackgroundColor(Color.parseColor("#F3F8FF"));
        ScrollView scroll = new ScrollView(this); page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18), dp(22), dp(18), dp(26));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        progressBar = new ProgressBar(this); progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(48), dp(48)); pp.gravity = Gravity.CENTER; page.addView(progressBar, pp);
        setContentView(page);
    }

    private void renderEmpty() {
        root.removeAllViews(); buildTop("Driver Trip", "Status perjalanan order native");
        LinearLayout c = card(); c.setPadding(dp(18), dp(16), dp(18), dp(16)); c.addView(text("Order tidak ditemukan.", 16, "#64748B", false));
        Button back = outlineButton("Kembali ke Dashboard"); back.setOnClickListener(v -> finish()); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50)); lp.setMargins(0, dp(14), 0, 0); c.addView(back, lp); add(c,0,dp(8),0,0);
    }

    private void renderOrder() {
        root.removeAllViews(); buildTop("Driver Trip", "Status perjalanan order native"); addHeaderCard(); addLocationCard("📍 Lokasi Pickup", pickupAddress(), true); addLocationCard("🏁 Lokasi Delivery", deliveryAddress(), false); addLeafletMapCard(); addNoteCard(); addActions();
    }

    private void buildTop(String title, String sub) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0,0,0,dp(14));
        TextView back = text("‹", 38, "#0B3A78", true); back.setGravity(Gravity.CENTER); back.setBackground(round("#FFFFFF", dp(22))); back.setOnClickListener(v -> finish()); row.addView(back, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL); col.setPadding(dp(14),0,0,0); col.addView(text(title, 27, "#0B3A78", true)); col.addView(text(sub, 14, "#64748B", false)); row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        TextView online = text("• Online", 13, "#059669", true); online.setGravity(Gravity.CENTER); online.setPadding(dp(12), dp(8), dp(12), dp(8)); online.setBackground(round("#DCFCE7", dp(22))); row.addView(online, new LinearLayout.LayoutParams(-2, -2));
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addHeaderCard() {
        LinearLayout header = card(); header.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); header.addView(top, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout left = new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL); top.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        left.addView(text(cleanServiceLabel(), 14, "#64748B", true));
        TextView id = text("#" + orderId(), 24, "#0B3A78", true); id.setMaxLines(2); left.addView(id);
        statusBadge = text(statusLabel(status()), 12, "#FFFFFF", true); statusBadge.setGravity(Gravity.CENTER); statusBadge.setPadding(dp(12), dp(7), dp(12), dp(7)); statusBadge.setBackground(roundGradient("#086BFF", "#2EA2FF", dp(18))); top.addView(statusBadge, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout stats = new LinearLayout(this); stats.setOrientation(LinearLayout.HORIZONTAL); stats.setGravity(Gravity.CENTER); LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2); sp.setMargins(0, dp(14), 0, 0); header.addView(stats, sp);
        addMiniStat(stats, "💰", "Total Bayar", rupiah(optDouble("price", "fare", "total")));
        addMiniStat(stats, "🛵", "Jarak", oneDecimal(optDouble("distance_km")) + " KM");
        addMiniStat(stats, "⏱️", "Estimasi", zeroDecimal(optDouble("duration_minutes")) + " menit");
        distanceMini = addMiniStat(stats, "📍", "Jarak", "...");

        distanceInfo = text("📍 Mengukur jarak driver...", 13, "#64748B", false); distanceInfo.setPadding(0, dp(10), 0, 0); header.addView(distanceInfo);
        distanceHint = text("", 13, "#059669", true); distanceHint.setPadding(dp(12), dp(9), dp(12), dp(9)); LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2); hp.setMargins(0, dp(8), 0, 0); distanceHint.setBackground(roundStroke("#ECFDF5", "#86EFAC", dp(14), 1)); header.addView(distanceHint, hp);
        add(header,0,dp(8),0,dp(12));
    }

    private TextView addMiniStat(LinearLayout parent, String icon, String label, String value) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER); box.setPadding(dp(3),0,dp(3),0);
        box.addView(text(icon, 21, "#0B3A78", false));
        TextView l = text(label, 11, "#64748B", false); l.setGravity(Gravity.CENTER); box.addView(l);
        TextView v = text(value, 13, "#111827", true); v.setGravity(Gravity.CENTER); box.addView(v);
        parent.addView(box, new LinearLayout.LayoutParams(0, -2, 1)); return v;
    }

    private void addLocationCard(String title, String body, boolean pickup) {
        LinearLayout c = card(); c.setPadding(dp(16), dp(13), dp(16), dp(13));
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); c.addView(row, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout txt = new LinearLayout(this); txt.setOrientation(LinearLayout.VERTICAL); row.addView(txt, new LinearLayout.LayoutParams(0, -2, 1));
        txt.addView(text(title, 16, "#0B3A78", true)); TextView b = text(firstNonEmpty(body,"-"), 15, "#111827", false); b.setPadding(0, dp(6), 0, 0); txt.addView(b);
        Button nav = outlineButton("➤ Navigasi"); nav.setOnClickListener(v -> openLeafletNavigation(pickup)); row.addView(nav, new LinearLayout.LayoutParams(dp(132), dp(48)));
        if (pickup) navPickupBtn = nav; else navDeliveryBtn = nav; add(c,0,0,0,dp(12));
    }

    private void addLeafletMapCard() {
        LinearLayout c = card(); c.setPadding(dp(12), dp(12), dp(12), dp(12));
        c.addView(text("🗺️ Peta Perjalanan", 16, "#0B3A78", true)); TextView sub = text("Marker pickup, delivery, dan kendaraan memakai icon drawable aplikasi.", 12, "#64748B", false); sub.setPadding(0, dp(4), 0, dp(10)); c.addView(sub);
        mapView = new WebView(this); mapView.setBackgroundColor(Color.TRANSPARENT); try { WebSettings st = mapView.getSettings(); st.setJavaScriptEnabled(true); st.setDomStorageEnabled(true); st.setLoadWithOverviewMode(true); st.setUseWideViewPort(true); st.setBuiltInZoomControls(false); st.setDisplayZoomControls(false); if(Build.VERSION.SDK_INT >= 21) st.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW); } catch(Exception ignored) {}
        mapView.loadDataWithBaseURL("https://transiva.my.id/", leafletHtml(false, true), "text/html", "UTF-8", null);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, dp(230)); mp.setMargins(0, dp(8), 0, 0); c.addView(mapView, mp);
        add(c,0,0,0,dp(12)); mainHandler.postDelayed(this::updateLeafletMap, 900);
    }

    private String leafletHtml(boolean navigationMode, boolean autoFit) {
        String startIcon = drawableDataUri("map_pickup_pin", "ic_pickup_marker", "ic_marker_start", "marker_start");
        String endIcon = drawableDataUri("map_destination_pin", "ic_destination_marker", "ic_marker_end", "marker_end");
        String driverIcon = drawableDataUri(isCar() ? "map_car_top" : "map_motor_top", "ic_driver_motor", "ic_driver_marker", "ic_vehicle_marker");
        double pLat = coord("pickup_lat", "user_lat"), pLng = coord("pickup_lng", "user_lng"), dLat = coord("delivery_lat", "destination_lat"), dLng = coord("delivery_lng", "destination_lng");
        double centerLat = validCoord(lastDriverLat,lastDriverLng) ? lastDriverLat : (validCoord(pLat,pLng) ? pLat : (validCoord(dLat,dLng) ? dLat : -0.9));
        double centerLng = validCoord(lastDriverLat,lastDriverLng) ? lastDriverLng : (validCoord(pLat,pLng) ? pLng : (validCoord(dLat,dLng) ? dLng : 119.87));
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'>"+
                "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'><script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>"+
                "<style>html,body,#map{height:100%;width:100%;margin:0;background:#eaf4ff}.leaflet-control-attribution{display:none}.leaflet-popup-content{font-weight:700}.ctl{position:absolute;right:12px;top:12px;z-index:999;background:#fff;border-radius:16px;box-shadow:0 5px 15px #0002;overflow:hidden}.ctl button{display:block;width:44px;height:44px;border:0;background:white;font-size:23px}.badge{position:absolute;left:12px;top:12px;right:72px;z-index:999;background:#fff;border-radius:14px;padding:10px;font:14px sans-serif;color:#0B3A78;box-shadow:0 5px 15px #0002}</style></head><body><div id='map'></div>"+
                (navigationMode ? "<div class='badge'>Ikuti rute menuju lokasi • GPS driver realtime</div>" : "")+
                "<div class='ctl'><button onclick='map.zoomIn()'>+</button><button onclick='map.zoomOut()'>−</button></div><script>"+
                "var map=L.map('map',{zoomControl:false,attributionControl:false}).setView(["+centerLat+","+centerLng+"],16);"+
                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19}).addTo(map);"+
                "function ico(u,w,h){return L.icon({iconUrl:u,iconSize:[w,h],iconAnchor:[w/2,h/2],popupAnchor:[0,-h/2]});}"+
                "var startIcon=ico('"+startIcon+"',42,42), endIcon=ico('"+endIcon+"',42,42), driverIcon=ico('"+driverIcon+"',48,48);"+
                "var pickup=null,delivery=null,driver=null,line=null;function ok(a,b){return !isNaN(a)&&!isNaN(b)&&a!=0&&b!=0;}"+
                "function update(dl,dk,pl,pk,el,ek,fit){var pts=[];if(ok(pl,pk)){if(!pickup)pickup=L.marker([pl,pk],{icon:startIcon}).addTo(map).bindPopup('Pickup');else pickup.setLatLng([pl,pk]);pts.push([pl,pk]);}if(ok(el,ek)){if(!delivery)delivery=L.marker([el,ek],{icon:endIcon}).addTo(map).bindPopup('Delivery');else delivery.setLatLng([el,ek]);pts.push([el,ek]);}if(ok(dl,dk)){if(!driver)driver=L.marker([dl,dk],{icon:driverIcon}).addTo(map).bindPopup('Driver');else driver.setLatLng([dl,dk]);pts.push([dl,dk]);}"+
                "var route=[];if(ok(dl,dk))route.push([dl,dk]);if(ok(pl,pk))route.push([pl,pk]);if(ok(el,ek))route.push([el,ek]);if(line)line.remove();if(route.length>=2)line=L.polyline(route,{color:'#086BFF',weight:6,opacity:.85}).addTo(map);if(fit){if(pts.length>1)map.fitBounds(pts,{padding:[35,35],maxZoom:17});else if(pts.length==1)map.setView(pts[0],16);}}window.updateTripMap=update;setTimeout(function(){update("+lastDriverLat+","+lastDriverLng+","+pLat+","+pLng+","+dLat+","+dLng+","+autoFit+");},300);"+
                "</script></body></html>";
    }

    private void updateLeafletMap() { if(mapView==null||order==null)return; double pLat=coord("pickup_lat","user_lat"),pLng=coord("pickup_lng","user_lng"),dLat=coord("delivery_lat","destination_lat"),dLng=coord("delivery_lng","destination_lng"); String js="if(window.updateTripMap){updateTripMap("+lastDriverLat+","+lastDriverLng+","+pLat+","+pLng+","+dLat+","+dLng+",false);}"; try{ if(Build.VERSION.SDK_INT>=19) mapView.evaluateJavascript(js,null); else mapView.loadUrl("javascript:"+js); }catch(Exception ignored){} }

    private void openLeafletNavigation(boolean pickup) {
        double targetLat = pickup ? coord("pickup_lat","user_lat") : coord("delivery_lat","destination_lat"); double targetLng = pickup ? coord("pickup_lng","user_lng") : coord("delivery_lng","destination_lng");
        if(!validCoord(targetLat,targetLng)){ showInfo("Lokasi", "Koordinat belum tersedia."); return; }
        Intent i = new Intent(this, DriverLeafletNavigationActivity.class); i.putExtra("order_json", order.toString()); i.putExtra("order_kind", orderKind); i.putExtra("target", pickup ? "pickup" : "delivery"); startActivity(i);
    }

    private String drawableDataUri(String... names) {
        for(String n:names){ try{ int id=getResources().getIdentifier(n,"drawable",getPackageName()); if(id!=0){ InputStream is=getResources().openRawResource(id); ByteArrayOutputStream bos=new ByteArrayOutputStream(); byte[] buf=new byte[4096]; int len; while((len=is.read(buf))>0)bos.write(buf,0,len); is.close(); return "data:image/png;base64,"+ Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP); }}catch(Exception ignored){} }
        return "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png";
    }

    private void addNoteCard(){ String note=firstNonEmpty(order.optString("note"),order.optString("item_note"),order.optString("description"),"-"); LinearLayout c=card(); c.setPadding(dp(16),dp(13),dp(16),dp(13)); c.addView(text(orderKind.equals("pickup")?"📦 Detail Paket":"📝 Catatan Customer",16,"#0B3A78",true)); TextView n=text(note,14,"#111827",false); n.setPadding(0,dp(6),0,0); c.addView(n); add(c,0,0,0,dp(12)); }

    private void addActions(){ LinearLayout c=card(); c.setPadding(dp(16),dp(16),dp(16),dp(16)); c.addView(text("⚡ Aksi Perjalanan",18,"#0B3A78",true)); chatBtn=primaryButton("💬 Chat Customer"); chatBtn.setOnClickListener(v->openChat()); addButtonTo(c,chatBtn,dp(12)); arrivedPickupBtn=greenButton("📍 Tiba di Lokasi Pickup"); arrivedPickupBtn.setOnClickListener(v->confirmUpdate("Tiba di lokasi pickup?","arrived_pickup")); addButtonTo(c,arrivedPickupBtn,dp(10)); startDeliveryBtn=greenButton(orderKind.equals("pickup")?"📦 Paket Sudah Diambil":"🛵 Lanjutkan Perjalanan"); startDeliveryBtn.setOnClickListener(v->confirmUpdate("Mulai perjalanan ke lokasi delivery?","on_delivery")); addButtonTo(c,startDeliveryBtn,dp(10)); arrivedDeliveryBtn=greenButton("🏁 Tiba di Lokasi Delivery"); arrivedDeliveryBtn.setOnClickListener(v->confirmUpdate("Tiba di lokasi delivery?","arrived_delivery")); addButtonTo(c,arrivedDeliveryBtn,dp(10)); finishBtn=greenButton(orderKind.equals("pickup")?"✅ Selesaikan dengan OTP":"✅ Selesaikan Order"); finishBtn.setOnClickListener(v->confirmUpdate("Selesaikan order ini sekarang?","finished")); addButtonTo(c,finishBtn,dp(10)); Button back=outlineButton("← Kembali ke Dashboard"); back.setOnClickListener(v->finish()); addButtonTo(c,back,dp(10)); TextView info=text("ⓘ Tombol aksi akan muncul sesuai jarak dan status perjalanan.",12,"#0B3A78",false); info.setPadding(dp(10),dp(8),dp(10),dp(8)); LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2); ip.setMargins(0,dp(10),0,0); info.setBackground(roundStroke("#F8FBFF","#D7E6F8",dp(12),1)); c.addView(info,ip); add(c,0,0,0,dp(18)); }
    private void addButtonTo(LinearLayout p, Button b, int top){ LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52)); lp.setMargins(0,top,0,0); p.addView(b,lp); }

    private void startLocationWatch(){ if(order==null)return; if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED){ requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},701); return;} try{ locationManager=(LocationManager)getSystemService(LOCATION_SERVICE); if(locationManager==null)return; stopLocationWatch(); locationListener=new LocationListener(){ @Override public void onLocationChanged(Location l){ if(l==null)return; lastDriverLat=l.getLatitude(); lastDriverLng=l.getLongitude(); updateDriverLocation(l); refreshButtonsByStatusAndDistance(); updateLeafletMap(); } @Override public void onStatusChanged(String p,int s,Bundle e){} @Override public void onProviderEnabled(String p){} @Override public void onProviderDisabled(String p){} }; try{locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,3000,2,locationListener,Looper.getMainLooper());}catch(Exception ignored){} try{locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,3000,2,locationListener,Looper.getMainLooper());}catch(Exception ignored){} Location last=null; try{last=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);}catch(Exception ignored){} if(last==null)try{last=locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);}catch(Exception ignored){} if(last!=null){lastDriverLat=last.getLatitude(); lastDriverLng=last.getLongitude(); refreshButtonsByStatusAndDistance(); updateLeafletMap();} }catch(Exception ignored){} }
    private void stopLocationWatch(){ try{ if(locationManager!=null&&locationListener!=null)locationManager.removeUpdates(locationListener);}catch(Exception ignored){} locationListener=null; }

    private void refreshButtonsByStatusAndDistance(){ if(arrivedPickupBtn==null||startDeliveryBtn==null||arrivedDeliveryBtn==null||finishBtn==null)return; String st=status(); float pd=distanceToPickup(), dd=distanceToDelivery(); arrivedPickupBtn.setVisibility(View.GONE); startDeliveryBtn.setVisibility(View.GONE); arrivedDeliveryBtn.setVisibility(View.GONE); finishBtn.setVisibility(View.GONE); if(statusBadge!=null)statusBadge.setText(statusLabel(st)); if(st.equals("taken")){ if(validCoord(lastDriverLat,lastDriverLng)&&pd>=0){ distanceInfo.setText("📍 Jarak ke pickup: "+meterText(pd)); if(distanceMini!=null)distanceMini.setText(meterText(pd)); if(pd<=ARRIVE_RADIUS_METER){ distanceHint.setText("✓ Kamu sudah dekat pickup. Tombol tiba pickup aktif."); arrivedPickupBtn.setVisibility(View.VISIBLE);} else distanceHint.setText("Tombol tiba pickup aktif saat jarak ≤ "+(int)ARRIVE_RADIUS_METER+" meter."); } else { distanceInfo.setText("📍 Menunggu GPS untuk mengukur jarak pickup..."); if(distanceMini!=null)distanceMini.setText("..."); distanceHint.setText("Pastikan GPS aktif dan izin lokasi diberikan."); } return;} if(st.equals("arrived_pickup")){ distanceInfo.setText("✅ Driver sudah tiba di pickup."); distanceHint.setText("Lanjutkan perjalanan setelah pesanan/paket siap."); startDeliveryBtn.setVisibility(View.VISIBLE); return;} if(st.equals("on_delivery")){ if(validCoord(lastDriverLat,lastDriverLng)&&dd>=0){ distanceInfo.setText("🏁 Jarak ke delivery: "+meterText(dd)); if(distanceMini!=null)distanceMini.setText(meterText(dd)); if(dd<=ARRIVE_RADIUS_METER){ distanceHint.setText("✓ Kamu sudah dekat delivery. Tombol tiba delivery aktif."); arrivedDeliveryBtn.setVisibility(View.VISIBLE);} else distanceHint.setText("Tombol tiba delivery aktif saat jarak ≤ "+(int)ARRIVE_RADIUS_METER+" meter."); } else { distanceInfo.setText("🏁 Menunggu GPS untuk mengukur jarak delivery..."); distanceHint.setText("Pastikan GPS aktif dan koordinat delivery tersedia."); } return;} if(st.equals("arrived_delivery")){ distanceInfo.setText("🏁 Driver sudah tiba di delivery."); distanceHint.setText("Selesaikan order setelah barang/pesanan diterima customer."); finishBtn.setVisibility(View.VISIBLE); return;} if(st.equals("finished")||st.equals("completed")){distanceInfo.setText("✅ Order selesai."); distanceHint.setText("Terima kasih sudah menyelesaikan perjalanan.");} }

    private void confirmUpdate(String msg,String next){ if(updatingStatus)return; new AlertDialog.Builder(this).setTitle("Konfirmasi").setMessage(msg).setNegativeButton("Batal",null).setPositiveButton("Ya",(d,w)->updateStatus(next)).show(); }
    private void updateStatus(String next){ updatingStatus=true; setLoading(true); new Thread(()->{ try{ JSONObject payload=new JSONObject(); payload.put("id",internalId()); payload.put("order_id",orderId()); payload.put("driver",driverUsername); payload.put("order_kind",orderKind); payload.put("status",next); String endpoint=orderKind.equals("pickup")?"driver_update_pickup_status.php":endpointForRegular(next); JSONObject res; try{res=postJson(BASE_URL+endpoint,payload);}catch(Exception e){res=postJson(BASE_URL+"driver_update_unified_status.php",payload);} boolean ok=res.optBoolean("success",false); String message=firstNonEmpty(res.optString("message"),ok?"Status berhasil diperbarui.":"Gagal update status."); mainHandler.post(()->{updatingStatus=false; setLoading(false); if(ok){ try{order.put("status",next);}catch(Exception ignored){} saveActiveOrder(); refreshButtonsByStatusAndDistance(); showInfo("Berhasil",message); if(next.equals("finished")||next.equals("completed")){clearActiveOrder(); finish();}} else showInfo("Gagal",message);}); }catch(Exception e){mainHandler.post(()->{updatingStatus=false; setLoading(false); showInfo("Koneksi gagal","Tidak bisa update status ke server.");});}}).start(); }
    private String endpointForRegular(String n){ if(n.equals("arrived_pickup"))return"driverArrivedPickup.php"; if(n.equals("on_delivery"))return"driverStartDelivery.php"; if(n.equals("arrived_delivery"))return"driverArrivedDelivery.php"; if(n.equals("finished")||n.equals("completed"))return"finishOrder.php"; return"driver_update_unified_status.php"; }
    private void updateDriverLocation(Location loc){ new Thread(()->{ try{ JSONObject p=new JSONObject(); p.put("username",driverUsername); p.put("driver",driverUsername); p.put("order_id",orderId()); p.put("latitude",loc.getLatitude()); p.put("longitude",loc.getLongitude()); postJson(BASE_URL+"updateDriverLocation.php",p);}catch(Exception ignored){} }).start(); }
    private void openChat(){ try{ String roomId=firstNonEmpty(order.optString("room_id"),getStringPref("active_chat_room_id"),"ROOM-"+orderId()).trim().replace("_","-").toUpperCase(Locale.US).replaceAll("[^A-Z0-9\\-]",""); if(!roomId.startsWith("ROOM-"))roomId="ROOM-"+roomId; String customerName=firstNonEmpty(order.optString("customer_name"),order.optString("customer"),order.optString("username"),order.optString("user_id"),"Customer"); getSharedPreferences(PREF_NAME,MODE_PRIVATE).edit().putString("active_order_id",orderId()).putString("active_chat_order_id",orderId()).putString("active_chat_room_id",roomId).putString("active_chat_driver_name",driverUsername).putString("active_chat_customer_name",customerName).putString("active_chat_order_status",status()).apply(); Intent i=new Intent(this,DriverChatActivity.class); i.putExtra("order_id",orderId()); i.putExtra("room_id",roomId); i.putExtra("driver_name",driverUsername); i.putExtra("customer_name",customerName); i.putExtra("order_status",status()); startActivity(i);}catch(Exception e){showInfo("Chat","Gagal membuka chat.");} }

    private void saveActiveOrder(){ if(order==null)return; getSharedPreferences(PREF_NAME,MODE_PRIVATE).edit().putString("driver_active_order_json",order.toString()).putString("driver_active_order_id",orderId()).putString("driver_active_order_kind",orderKind).putString("driver_active_order_status",status()).putString("driver_active_pickup_address",pickupAddress()).putString("driver_active_delivery_address",deliveryAddress()).putString("driver_active_pickup_lat",String.valueOf(coord("pickup_lat","user_lat"))).putString("driver_active_pickup_lng",String.valueOf(coord("pickup_lng","user_lng"))).putString("driver_active_delivery_lat",String.valueOf(coord("delivery_lat","destination_lat"))).putString("driver_active_delivery_lng",String.valueOf(coord("delivery_lng","destination_lng"))).putString("driver_active_price",String.valueOf(optDouble("price","fare","total"))).apply(); }
    private void clearActiveOrder(){ getSharedPreferences(PREF_NAME,MODE_PRIVATE).edit().remove("driver_active_order_json").remove("driver_active_order_id").remove("driver_active_order_kind").remove("driver_active_order_status").apply(); }

    private boolean isCar(){String s=firstNonEmpty(order.optString("driver_type"),order.optString("service_type"),order.optString("order_type"),"").toLowerCase(Locale.US); return s.contains("car")||s.contains("mobil");}
    private String orderId(){return firstNonEmpty(order.optString("order_id"),order.optString("id"),"-");} private String internalId(){return firstNonEmpty(order.optString("id"),order.optString("order_id"),"");} private String status(){return firstNonEmpty(order.optString("status"),"taken").toLowerCase(Locale.US).trim();} private String pickupAddress(){return firstNonEmpty(order.optString("pickup_address"),order.optString("pickup"),order.optString("sender_address"),"-");} private String deliveryAddress(){return firstNonEmpty(order.optString("delivery_address"),order.optString("destination_address"),order.optString("destination"),order.optString("receiver_address"),"-");} private String cleanServiceLabel(){String s=firstNonEmpty(order.optString("service_name"),order.optString("order_type"),orderKind.equals("pickup")?"TransPickup":"Transbike"); return s.replace("📦","").replace("🛵","").trim();}
    private double coord(String a,String b){try{return Double.parseDouble(firstNonEmpty(order.optString(a),order.optString(b),"0"));}catch(Exception e){return 0;}} private double optDouble(String...keys){for(String k:keys){try{if(order.has(k))return Double.parseDouble(order.optString(k,"0"));}catch(Exception ignored){}}return 0;} private String statusLabel(String s){if(s.equals("taken"))return"Menuju Pickup"; if(s.equals("arrived_pickup"))return"Tiba Pickup"; if(s.equals("on_delivery"))return"Menuju Delivery"; if(s.equals("arrived_delivery"))return"Tiba Delivery"; if(s.equals("finished")||s.equals("completed"))return"Selesai"; return firstNonEmpty(s,"Menuju Pickup");}
    private float distanceToPickup(){return distanceTo(coord("pickup_lat","user_lat"),coord("pickup_lng","user_lng"));} private float distanceToDelivery(){return distanceTo(coord("delivery_lat","destination_lat"),coord("delivery_lng","destination_lng"));} private float distanceTo(double lat,double lng){if(!validCoord(lastDriverLat,lastDriverLng)||!validCoord(lat,lng))return-1; float[] r=new float[1]; Location.distanceBetween(lastDriverLat,lastDriverLng,lat,lng,r); return r[0];}
    private JSONObject postJson(String urlText,JSONObject payload)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(urlText).openConnection(); c.setConnectTimeout(TIMEOUT_MS); c.setReadTimeout(TIMEOUT_MS); c.setRequestMethod("POST"); c.setRequestProperty("Content-Type","application/json; charset=utf-8"); c.setRequestProperty("Accept","application/json"); c.setDoOutput(true); OutputStream os=c.getOutputStream(); os.write(payload.toString().getBytes(StandardCharsets.UTF_8)); os.flush(); os.close(); InputStream is=c.getResponseCode()>=400?c.getErrorStream():c.getInputStream(); BufferedReader br=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8)); StringBuilder sb=new StringBuilder(); String line; while((line=br.readLine())!=null)sb.append(line); br.close(); c.disconnect(); String body=sb.toString().trim(); return body.length()==0?new JSONObject():new JSONObject(body);}
    private String getStringPref(String key){try{return getSharedPreferences(PREF_NAME,MODE_PRIVATE).getString(key,"");}catch(Exception e){return"";}} private boolean validCoord(double lat,double lng){return lat!=0&&lng!=0&&!Double.isNaN(lat)&&!Double.isNaN(lng);} private String meterText(float m){return m>=1000?oneDecimal(m/1000.0)+" km":Math.round(m)+" meter";} private String rupiah(double v){return"Rp "+ NumberFormat.getNumberInstance(new Locale("id","ID")).format((long)v);} private String oneDecimal(double v){return String.format(Locale.US,"%.1f",v);} private String zeroDecimal(double v){return String.format(Locale.US,"%.0f",v);} private String firstNonEmpty(String...values){if(values==null)return""; for(String s:values)if(s!=null&&s.trim().length()>0&&!"null".equalsIgnoreCase(s.trim()))return s.trim(); return"";} private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+0.5f);} private void add(View v,int l,int t,int r,int b){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(l,t,r,b); root.addView(v,lp);} private LinearLayout card(){LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setBackground(roundStroke("#FFFFFF","#D7E6F8",dp(24),1)); v.setElevation(dp(2)); return v;} private TextView text(String s,int sp,String color,boolean bold){TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(Color.parseColor(color)); if(bold)t.setTypeface(Typeface.DEFAULT_BOLD); return t;} private Button primaryButton(String s){Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT_BOLD); b.setBackground(roundGradient("#086BFF","#2EA2FF",dp(18))); return b;} private Button greenButton(String s){Button b=primaryButton(s); b.setBackground(roundGradient("#10B981","#059669",dp(18))); return b;} private Button outlineButton(String s){Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(Color.parseColor("#0B7CFF")); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT_BOLD); b.setBackground(roundStroke("#FFFFFF","#9DCAFF",dp(18),1)); return b;} private GradientDrawable round(String color,int radius){GradientDrawable g=new GradientDrawable(); g.setColor(Color.parseColor(color)); g.setCornerRadius(radius); return g;} private GradientDrawable roundStroke(String color,String stroke,int radius,int sw){GradientDrawable g=round(color,radius); g.setStroke(dp(sw),Color.parseColor(stroke)); return g;} private GradientDrawable roundGradient(String c1,String c2,int radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.parseColor(c1),Color.parseColor(c2)}); g.setCornerRadius(radius); return g;} private void setLoading(boolean b){if(progressBar!=null)progressBar.setVisibility(b?View.VISIBLE:View.GONE);} private void showInfo(String t,String m){try{new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("OK",null).show();}catch(Exception ignored){}}
}
