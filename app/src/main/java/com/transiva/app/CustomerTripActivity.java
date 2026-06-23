package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class CustomerTripActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String CHECK_STATUS_URL = BASE_URL + "server/check_order_status.php";
    private static final int TIMEOUT_MS = 25000;
    private static final long TRACKING_MS = 3000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable trackingRunnable = new Runnable() {
        @Override public void run() {
            fetchDriverPosition();
            mainHandler.postDelayed(this, TRACKING_MS);
        }
    };

    private WebView mapView;
    private TextView statusText;
    private TextView driverNameText;
    private TextView driverTypeText;
    private TextView driverPlateText;
    private TextView tripInfoText;
    private ImageView driverPhotoView;
    private Button backBtn;
    private ProgressBar progressBar;

    private String orderId = "";
    private String activeDriverType = "motor";
    private boolean mapReady = false;
    private boolean firstFocus = true;
    private boolean trackingStarted = false;
    private boolean finishedCountdownStarted = false;
    private int finishSeconds = 5;

    private double pickupLat = 0;
    private double pickupLng = 0;
    private double deliveryLat = 0;
    private double deliveryLng = 0;
    private double lastDriverLat = 0;
    private double lastDriverLng = 0;
    private double lastBearing = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setStatusBarColor(Color.parseColor("#071426"));
            getWindow().setNavigationBarColor(Color.parseColor("#071426"));
        } catch (Exception ignored) {}

        readIntentAndSavedData();
        buildLayout();

        if (orderId.length() == 0) {
            showInfo("Order tidak ditemukan", "ID order tidak ditemukan. Silakan ulangi order.");
            return;
        }
        if (!validCoord(pickupLat, pickupLng)) {
            statusText.setText("Lokasi pickup belum tersedia. Menunggu data order dari server...");
        }
    }

    private void readIntentAndSavedData() {
        SharedPreferences sp = getSharedPreferences("transiva", MODE_PRIVATE);
        Intent i = getIntent();

        orderId = firstNonEmpty(
                i.getStringExtra("order_id"),
                i.getStringExtra("active_order_id"),
                sp.getString("active_order_id", "")
        );

        pickupLat = getDoubleExtraOrPref(i, sp, "pickup_lat", 0);
        pickupLng = getDoubleExtraOrPref(i, sp, "pickup_lng", 0);
        deliveryLat = getDoubleExtraOrPref(i, sp, "delivery_lat", 0);
        deliveryLng = getDoubleExtraOrPref(i, sp, "delivery_lng", 0);
        activeDriverType = firstNonEmpty(
                i.getStringExtra("active_driver_type"),
                sp.getString("active_driver_type", "motor"),
                "motor"
        );
        if (!"car".equals(activeDriverType)) activeDriverType = "motor";
    }

    private double getDoubleExtraOrPref(Intent i, SharedPreferences sp, String key, double def) {
        try {
            if (i != null && i.hasExtra(key)) return i.getDoubleExtra(key, def);
            return Double.longBitsToDouble(sp.getLong(key, Double.doubleToLongBits(def)));
        } catch (Exception e) {
            try { return Double.parseDouble(sp.getString(key, String.valueOf(def))); } catch (Exception ignored) {}
        }
        return def;
    }

    private void buildLayout() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F3F8FF"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(18), dp(14), dp(12));
        page.addView(root, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(roundStroke("#FAFCFF", "#D7E6F8", dp(24), 1));
        card.setElevation(dp(7));
        root.addView(card, new LinearLayout.LayoutParams(-1, -1));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(head, new LinearLayout.LayoutParams(-1, -2));

        TextView title = text("Driver Ditemukan", 20, "#0B3A78", true);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Button close = smallButton("×", "#FEE2E2", "#DC2626", "#FECACA");
        head.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
        close.setOnClickListener(v -> finish());

        LinearLayout driverBox = new LinearLayout(this);
        driverBox.setOrientation(LinearLayout.HORIZONTAL);
        driverBox.setGravity(Gravity.CENTER_VERTICAL);
        driverBox.setPadding(dp(10), dp(10), dp(10), dp(10));
        driverBox.setBackground(roundStroke("#FFFFFF", "#E2E8F0", dp(18), 1));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
        dlp.setMargins(0, dp(12), 0, 0);
        card.addView(driverBox, dlp);

        driverPhotoView = new ImageView(this);
        driverPhotoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        driverPhotoView.setImageResource(android.R.drawable.ic_menu_myplaces);
        driverPhotoView.setBackground(round("#EAF4FF", dp(26)));
        driverBox.addView(driverPhotoView, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, -2, 1);
        ilp.setMargins(dp(10), 0, 0, 0);
        driverBox.addView(info, ilp);

        driverNameText = text("Driver", 15, "#0B3A78", true);
        driverTypeText = text("🏍️ Motor / Bike", 12, "#2563EB", true);
        driverPlateText = text("🔢 Plat: -", 12, "#64748B", false);
        info.addView(driverNameText);
        info.addView(driverTypeText);
        info.addView(driverPlateText);

        statusText = text("Menghubungkan lokasi driver...", 13, "#334155", true);
        statusText.setPadding(dp(4), dp(10), dp(4), dp(8));
        card.addView(statusText, new LinearLayout.LayoutParams(-1, -2));

        mapView = new WebView(this);
        mapView.setBackgroundColor(Color.parseColor("#EAF4FF"));
        mapView.setWebViewClient(new WebViewClient());
        WebSettings s = mapView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        mapView.addJavascriptInterface(new TripBridge(), "AndroidTrip");
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, 0, 1);
        mlp.setMargins(0, dp(4), 0, dp(10));
        card.addView(mapView, mlp);
        mapView.loadDataWithBaseURL(BASE_URL, mapHtml(), "text/html", "UTF-8", null);

        tripInfoText = text("Rute akan tampil otomatis saat lokasi driver tersedia", 12, "#64748B", false);
        tripInfoText.setPadding(dp(4), 0, dp(4), dp(8));
        card.addView(tripInfoText, new LinearLayout.LayoutParams(-1, -2));

        backBtn = outlineButton("Kembali");
        card.addView(backBtn, new LinearLayout.LayoutParams(-1, dp(48)));
        backBtn.setOnClickListener(v -> finish());

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(54), dp(54));
        plp.gravity = Gravity.CENTER;
        page.addView(progressBar, plp);

        setContentView(page);
    }

    private String mapHtml() {
        return "<!DOCTYPE html><html><head>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>" +
                "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'>" +
                "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>" +
                "<style>html,body,#map{height:100%;margin:0;padding:0;background:#eef6ff;}" +
                ".leaflet-control-attribution,.leaflet-control-zoom{display:none!important;}" +
                ".leaflet-container{font-family:Arial,sans-serif;border-radius:18px;}" +
                ".pin{font-size:30px;text-align:center;filter:drop-shadow(0 4px 4px rgba(0,0,0,.28));}" +
                ".vehicle{width:42px;height:42px;object-fit:contain;transition:transform .35s linear;filter:drop-shadow(0 4px 5px rgba(0,0,0,.35));}" +
                ".popup{font-weight:700;color:#0B3A78;min-width:120px;}" +
                "</style></head><body><div id='map'></div><script>" +
                "var map=L.map('map',{zoomControl:false,attributionControl:false}).setView([-0.018137,120.087380],15);" +
                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:20,attribution:''}).addTo(map);" +
                "var pickup=null,delivery=null,driver=null,line=null,routeCoords=[],lastRouteKey='',drawing=false,lastRouteTime=0,lastDriver=[0,0];" +
                "function esc(s){return String(s||'').replace(/</g,'&lt;').replace(/>/g,'&gt;');}" +
                "function ic(t){return L.divIcon({html:'<div class=pin>'+t+'</div>',className:'',iconSize:[42,42],iconAnchor:[21,38],popupAnchor:[0,-36]});}" +
                "function vehicleIcon(type){var img=(type==='car')?'assets/transcar.png':'assets/transbike.png';return L.divIcon({html:'<img class=vehicle src=\''+img+'\'>',className:'',iconSize:[46,46],iconAnchor:[23,23],popupAnchor:[0,-34]});}" +
                "function setPickup(lat,lng,label){lat=+lat;lng=+lng;if(!lat||!lng)return;if(pickup){pickup.setLatLng([lat,lng]);}else{pickup=L.marker([lat,lng],{icon:ic('👤'),zIndexOffset:500}).addTo(map);}pickup.bindPopup('<div class=popup>'+esc(label||'Lokasi Pickup')+'</div>');}" +
                "function setDelivery(lat,lng,label){lat=+lat;lng=+lng;if(!lat||!lng)return;if(delivery){delivery.setLatLng([lat,lng]);}else{delivery=L.marker([lat,lng],{icon:ic('📍'),zIndexOffset:500}).addTo(map);}delivery.bindPopup('<div class=popup>'+esc(label||'Lokasi Delivery')+'</div>');}" +
                "function setDriver(lat,lng,bearing,type,name,status){lat=+lat;lng=+lng;bearing=+bearing||0;if(!lat||!lng)return;var pos=[lat,lng];if(!driver){driver=L.marker(pos,{icon:vehicleIcon(type),zIndexOffset:9999}).addTo(map);}else{driver.setLatLng(pos);}var el=driver.getElement();if(el){var img=el.querySelector('.vehicle');if(img){img.style.transform='rotate('+bearing+'deg)';}}driver.bindPopup('<div class=popup><b>'+esc(name||'Driver')+'</b><br><br>'+esc(status||'Dalam perjalanan')+'</div>');lastDriver=pos;}" +
                "function clearLine(){if(line){map.removeLayer(line);line=null;}}" +
                "function fitAll(){var p=[];if(driver)p.push(driver.getLatLng());if(pickup)p.push(pickup.getLatLng());if(delivery)p.push(delivery.getLatLng());if(p.length===1)map.setView(p[0],16,{animate:true});else if(p.length>1)map.fitBounds(p,{padding:[55,55],maxZoom:16,animate:true});}" +
                "function fitTrip(){fitAll();}" +
                "function drawStraight(a,b,color){clearLine();line=L.polyline([a,b],{color:color||'#4285F4',weight:5,opacity:.78,dashArray:'8,8',lineCap:'round'}).addTo(map);line.bringToBack();}" +
                "function drawRoute(dLat,dLng,tLat,tLng,status){var now=Date.now();var key=dLat.toFixed(3)+','+dLng.toFixed(3)+','+tLat.toFixed(3)+','+tLng.toFixed(3)+','+status;if(drawing||key===lastRouteKey||now-lastRouteTime<8000)return;drawing=true;lastRouteKey=key;lastRouteTime=now;var color=status==='on_delivery'?'#16a34a':(status==='arrived_pickup'?'#f59e0b':'#4285F4');var url='https://router.project-osrm.org/route/v1/driving/'+dLng+','+dLat+';'+tLng+','+tLat+'?overview=full&geometries=geojson';var timer=null;var ctrl=new AbortController();timer=setTimeout(function(){try{ctrl.abort();}catch(e){}},7000);fetch(url,{signal:ctrl.signal}).then(function(r){return r.json();}).then(function(j){clearTimeout(timer);if(!j||!j.routes||!j.routes[0])throw new Error('no route');var cs=j.routes[0].geometry.coordinates;var pts=[];for(var i=0;i<cs.length;i++){pts.push([cs[i][1],cs[i][0]]);}clearLine();line=L.polyline(pts,{color:color,weight:5,opacity:.9,lineCap:'round',lineJoin:'round'}).addTo(map);line.bringToBack();try{AndroidTrip.onRoute(j.routes[0].distance/1000,j.routes[0].duration);}catch(e){};}).catch(function(){drawStraight([dLat,dLng],[tLat,tLng],color);}).finally(function(){drawing=false;});}" +
                "setTimeout(function(){map.invalidateSize();try{AndroidTrip.onMapReady();}catch(e){}},800);" +
                "</script></body></html>";
    }

    public class TripBridge {
        @JavascriptInterface public void onMapReady() {
            mainHandler.post(() -> {
                mapReady = true;
                pushInitialMarkers();
                startTrackingOnce();
            });
        }
        @JavascriptInterface public void onRoute(double km, double seconds) {
            mainHandler.post(() -> tripInfoText.setText("Estimasi rute driver: " + String.format(Locale.US, "%.1f", km) + " KM • " + Math.max(1, (int)Math.ceil(seconds / 60.0)) + " menit"));
        }
    }

    private void pushInitialMarkers() {
        if (!mapReady || mapView == null) return;
        if (validCoord(pickupLat, pickupLng)) eval("setPickup(" + pickupLat + "," + pickupLng + ",'Lokasi Pickup')");
        if (validCoord(deliveryLat, deliveryLng)) eval("setDelivery(" + deliveryLat + "," + deliveryLng + ",'Lokasi Delivery')");
        eval("fitTrip()");
    }

    private void startTrackingOnce() {
        if (trackingStarted || orderId.length() == 0) return;
        trackingStarted = true;
        setLoading(true);
        fetchDriverPosition();
        mainHandler.postDelayed(trackingRunnable, TRACKING_MS);
    }

    private void fetchDriverPosition() {
        if (orderId.length() == 0 || finishedCountdownStarted) return;
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject().put("order_id", orderId);
                JSONObject res = postJson(CHECK_STATUS_URL, payload);
                mainHandler.post(() -> {
                    setLoading(false);
                    if (res.optBoolean("success", false)) handleStatusResponse(res);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setLoading(false);
                    statusText.setText("Koneksi tracking belum stabil. Mencoba lagi...");
                });
            }
        }).start();
    }

    private void handleStatusResponse(JSONObject res) {
        JSONObject driver = res.optJSONObject("driver");
        if (driver == null) driver = new JSONObject();
        JSONObject order = res.optJSONObject("order");
        if (order == null) order = new JSONObject();

        String status = firstNonEmpty(order.optString("status", ""), res.optString("status", "")).toLowerCase(Locale.US);
        String driverName = firstNonEmpty(driver.optString("name", ""), driver.optString("username", ""), order.optString("driver_username", ""), res.optString("driver_username", ""), "Driver");
        String plate = firstNonEmpty(driver.optString("plate", ""), driver.optString("vehicle_plate", ""), order.optString("driver_plate", ""), "-");
        activeDriverType = resolveDriverType(order, driver);

        double pLat = getJsonDouble(order, "pickup_lat", "pickupLatitude", "pickup_latitude");
        double pLng = getJsonDouble(order, "pickup_lng", "pickupLongitude", "pickup_longitude");
        double dLat = getJsonDouble(order, "delivery_lat", "deliveryLatitude", "delivery_latitude");
        double dLng = getJsonDouble(order, "delivery_lng", "deliveryLongitude", "delivery_longitude");
        if (validCoord(pLat, pLng)) { pickupLat = pLat; pickupLng = pLng; }
        if (validCoord(dLat, dLng)) { deliveryLat = dLat; deliveryLng = dLng; }
        saveTripPrefs();
        pushInitialMarkers();

        double lat = getJsonDouble(driver, "driver_lat", "latitude", "lat");
        double lng = getJsonDouble(driver, "driver_lng", "longitude", "lng");
        boolean hasLocation = validCoord(lat, lng);

        driverNameText.setText(driverName);
        driverTypeText.setText("car".equals(activeDriverType) ? "🚘 Mobil / Car" : "🏍️ Motor / Bike");
        driverPlateText.setText("🔢 Plat: " + plate);
        setStatusText(status, driverName, hasLocation);

        if ("finished".equals(status) || "completed".equals(status)) {
            startFinishCountdown();
            return;
        }
        if (!hasLocation) return;

        double[] target = getTarget(status);
        double bearing = calcBearing(lat, lng, target[0], target[1]);
        if (validCoord(lastDriverLat, lastDriverLng)) {
            double moveBearing = calcBearing(lastDriverLat, lastDriverLng, lat, lng);
            if (distanceMeters(lastDriverLat, lastDriverLng, lat, lng) > 2) bearing = moveBearing;
        }
        lastBearing = smoothBearing(lastBearing, bearing);
        lastDriverLat = lat;
        lastDriverLng = lng;

        String popup = popupText(status);
        eval("setDriver(" + lat + "," + lng + "," + lastBearing + ",'" + activeDriverType + "','" + esc(driverName) + "','" + esc(popup) + "')");
        if (validCoord(target[0], target[1])) {
            eval("drawRoute(" + lat + "," + lng + "," + target[0] + "," + target[1] + ",'" + esc(status) + "')");
        }
        if (firstFocus) {
            firstFocus = false;
            eval("fitTrip()");
        }
    }

    private String resolveDriverType(JSONObject order, JSONObject driver) {
        String type = firstNonEmpty(order.optString("driver_type", ""), order.optString("price_mode", ""), driver.optString("driver_type", ""), activeDriverType, "motor").toLowerCase(Locale.US);
        return "car".equals(type) || "mobil".equals(type) ? "car" : "motor";
    }

    private double[] getTarget(String status) {
        if (("on_delivery".equals(status) || "arrived_delivery".equals(status) || "finished".equals(status) || "completed".equals(status)) && validCoord(deliveryLat, deliveryLng)) {
            return new double[]{deliveryLat, deliveryLng};
        }
        return new double[]{pickupLat, pickupLng};
    }

    private void setStatusText(String status, String driverName, boolean hasLocation) {
        if (!hasLocation) {
            statusText.setText("🛰️ Menunggu lokasi terbaru dari " + driverName);
            return;
        }
        if ("taken".equals(status)) statusText.setText("🛵 " + driverName + " sedang menuju lokasi pickup");
        else if ("arrived_pickup".equals(status)) statusText.setText("✅ " + driverName + " sudah tiba di lokasi pickup");
        else if ("on_delivery".equals(status)) statusText.setText("🛵 " + driverName + " sedang menuju lokasi delivery");
        else if ("arrived_delivery".equals(status)) statusText.setText("🏁 " + driverName + " sudah tiba di lokasi delivery");
        else statusText.setText(driverName + " sedang dalam perjalanan");
    }

    private String popupText(String status) {
        if ("arrived_pickup".equals(status)) return "✅ Sudah tiba di pickup";
        if ("on_delivery".equals(status)) return "🛵 Menuju lokasi delivery";
        if ("arrived_delivery".equals(status)) return "🏁 Sudah tiba di delivery";
        if ("finished".equals(status) || "completed".equals(status)) return "✅ Order selesai";
        return "Sedang menuju pickup";
    }

    private void startFinishCountdown() {
        if (finishedCountdownStarted) return;
        finishedCountdownStarted = true;
        mainHandler.removeCallbacks(trackingRunnable);
        finishSeconds = 5;
        Runnable r = new Runnable() {
            @Override public void run() {
                if (finishSeconds <= 0) {
                    clearActiveOrder();
                    finish();
                    return;
                }
                statusText.setText("✅ Order selesai\nKembali ke home dalam " + finishSeconds + " detik...");
                finishSeconds--;
                mainHandler.postDelayed(this, 1000);
            }
        };
        mainHandler.post(r);
    }

    private void saveTripPrefs() {
        try {
            getSharedPreferences("transiva", MODE_PRIVATE).edit()
                    .putString("active_order_id", orderId)
                    .putString("active_driver_type", activeDriverType)
                    .putLong("pickup_lat", Double.doubleToLongBits(pickupLat))
                    .putLong("pickup_lng", Double.doubleToLongBits(pickupLng))
                    .putLong("delivery_lat", Double.doubleToLongBits(deliveryLat))
                    .putLong("delivery_lng", Double.doubleToLongBits(deliveryLng))
                    .apply();
        } catch (Exception ignored) {}
    }

    private void clearActiveOrder() {
        try {
            getSharedPreferences("transiva", MODE_PRIVATE).edit()
                    .remove("active_order_id")
                    .remove("pickup_lat")
                    .remove("pickup_lng")
                    .remove("delivery_lat")
                    .remove("delivery_lng")
                    .apply();
        } catch (Exception ignored) {}
    }

    private JSONObject postJson(String urlText, JSONObject payload) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlText);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
            writer.write(payload.toString());
            writer.flush();
            writer.close();
            os.close();
            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
            String body = readStream(is).trim();
            if (body.isEmpty()) return new JSONObject();
            return new JSONObject(body);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }

    private double getJsonDouble(JSONObject obj, String... keys) {
        if (obj == null || keys == null) return 0;
        for (String k : keys) {
            try {
                if (obj.has(k) && !obj.isNull(k)) {
                    Object v = obj.opt(k);
                    if (v instanceof Number) return ((Number) v).doubleValue();
                    String s = String.valueOf(v).trim();
                    if (s.length() > 0 && !"null".equalsIgnoreCase(s)) return Double.parseDouble(s);
                }
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private boolean validCoord(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng) && lat != 0 && lng != 0;
    }

    private double calcBearing(double lat1, double lng1, double lat2, double lng2) {
        if (!validCoord(lat1, lng1) || !validCoord(lat2, lng2)) return lastBearing;
        double dLng = Math.toRadians(lng2 - lng1);
        double y = Math.sin(dLng) * Math.cos(Math.toRadians(lat2));
        double x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) - Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLng);
        return normalize(Math.toDegrees(Math.atan2(y, x)));
    }

    private double smoothBearing(double oldB, double newB) {
        oldB = normalize(oldB);
        newB = normalize(newB);
        double diff = newB - oldB;
        if (diff > 180) diff -= 360;
        if (diff < -180) diff += 360;
        return normalize(oldB + diff * 0.35);
    }

    private double normalize(double v) {
        v = v % 360;
        return v < 0 ? v + 360 : v;
    }

    private double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    private void eval(String js) {
        if (mapView == null || !mapReady) return;
        try { mapView.evaluateJavascript(js, null); } catch (Exception ignored) {}
    }

    private void setLoading(boolean show) {
        if (progressBar != null) progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private String esc(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\r", " ").replace("\n", " ");
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && v.trim().length() > 0 && !v.trim().equalsIgnoreCase("null")) return v.trim();
        }
        return "";
    }

    private Button outlineButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.parseColor("#0B7CFF"));
        b.setBackground(roundStroke("#FFFFFF", "#9DCAFF", dp(18), 1));
        return b;
    }

    private Button smallButton(String value, String bg, String fg, String stroke) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.parseColor(fg));
        b.setBackground(roundStroke(bg, stroke, dp(16), 1));
        return b;
    }

    private TextView text(String value, int sp, String color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(Color.parseColor(color));
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private GradientDrawable round(String color, int radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(color));
        gd.setCornerRadius(radius);
        return gd;
    }

    private GradientDrawable roundStroke(String color, String stroke, int radius, int width) {
        GradientDrawable gd = round(color, radius);
        gd.setStroke(dp(width), Color.parseColor(stroke));
        return gd;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showInfo(String title, String message) {
        try {
            new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
        } catch (Exception ignored) {}
    }

    @Override protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(trackingRunnable);
    }

    @Override protected void onResume() {
        super.onResume();
        if (trackingStarted && !finishedCountdownStarted) mainHandler.postDelayed(trackingRunnable, TRACKING_MS);
    }

    @Override protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        try {
            if (mapView != null) {
                mapView.stopLoading();
                mapView.destroy();
                mapView = null;
            }
        } catch (Exception ignored) {}
        super.onDestroy();
    }
}
