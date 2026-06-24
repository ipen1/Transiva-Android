package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PassengerCarActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String CREATE_ORDER_URL = BASE_URL + "server/create_order.php";
    private static final String RESOLVE_MAPS_URL = BASE_URL + "server/resolve_google_maps.php";
    private static final int REQ_LOCATION = 44;
    private static final int TIMEOUT_MS = 25000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WebView mapView;
    private TextView pickupText, deliveryText, modeText, fareText;
    private EditText googleMapInput, noteInput;
    private Button pickupBtn, deliveryBtn, gpsBtn, orderBtn, backBtn, useLinkBtn;
    private ProgressBar progressBar;

    private boolean mapReady = false;
    private boolean ordering = false;
    private String mode = "pickup";
    private String username = "";

    private double pickupLat = 0, pickupLng = 0;
    private double deliveryLat = 0, deliveryLng = 0;
    private double centerLat = -0.018137, centerLng = 120.087380;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setStatusBarColor(Color.parseColor("#071426"));
            getWindow().setNavigationBarColor(Color.parseColor("#071426"));
        } catch (Exception ignored) {}

        readUser();
        buildLayout();
        requestLocationIfNeeded();
    }

    private void readUser() {
        SharedPreferences sp = getSharedPreferences("transiva", MODE_PRIVATE);
        username = firstNonEmpty(
                sp.getString("username", ""),
                sp.getString("user_username", ""),
                sp.getString("player_username", "")
        );
    }

    private void buildLayout() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#071426"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(14), dp(12), dp(12));
        page.addView(root, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout topCard = new LinearLayout(this);
        topCard.setOrientation(LinearLayout.VERTICAL);
        topCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        topCard.setBackground(roundStroke("#F8FBFF", "#D7E6F8", dp(22), 1));
        root.addView(topCard, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        topCard.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        TextView title = text("Transcar", 23, "#0B3A78", true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Button close = smallButton("×", "#FEE2E2", "#DC2626", "#FECACA");
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        close.setOnClickListener(v -> finish());

        modeText = text("Pilih lokasi jemput", 13, "#64748B", false);
        modeText.setPadding(0, dp(2), 0, dp(8));
        topCard.addView(modeText);

        pickupBtn = rowButton("●  Lokasi Jemput", "#16A34A");
        deliveryBtn = rowButton("●  Lokasi Tujuan", "#EF4444");
        topCard.addView(pickupBtn, new LinearLayout.LayoutParams(-1, dp(46)));
        topCard.addView(deliveryBtn, new LinearLayout.LayoutParams(-1, dp(46)));

        pickupText = text("Belum dipilih", 12, "#334155", false);
        deliveryText = text("Belum dipilih", 12, "#334155", false);
        pickupText.setPadding(dp(8), dp(4), dp(8), dp(2));
        deliveryText.setPadding(dp(8), dp(2), dp(8), dp(8));
        topCard.addView(pickupText);
        topCard.addView(deliveryText);

        LinearLayout linkRow = new LinearLayout(this);
        linkRow.setOrientation(LinearLayout.HORIZONTAL);
        linkRow.setGravity(Gravity.CENTER_VERTICAL);
        linkRow.setPadding(0, dp(8), 0, 0);
        topCard.addView(linkRow, new LinearLayout.LayoutParams(-1, -2));

        googleMapInput = new EditText(this);
        googleMapInput.setSingleLine(true);
        googleMapInput.setTextSize(12);
        googleMapInput.setHint("Paste link Google Maps tujuan...");
        googleMapInput.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(14), 1));
        googleMapInput.setPadding(dp(10), 0, dp(10), 0);
        linkRow.addView(googleMapInput, new LinearLayout.LayoutParams(0, dp(44), 1));

        useLinkBtn = smallButton("Pakai", "#EAF4FF", "#0B7CFF", "#9DCAFF");
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(dp(74), dp(44));
        ulp.setMargins(dp(8), 0, 0, 0);
        linkRow.addView(useLinkBtn, ulp);

        FrameLayout mapBox = new FrameLayout(this);
        LinearLayout.LayoutParams mapLp = new LinearLayout.LayoutParams(-1, 0, 1);
        mapLp.setMargins(0, dp(12), 0, dp(12));
        root.addView(mapBox, mapLp);

        mapView = new WebView(this);
        mapView.setBackgroundColor(Color.parseColor("#EAF4FF"));
        mapView.setWebViewClient(new WebViewClient());
        WebSettings s = mapView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (android.os.Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        mapView.addJavascriptInterface(new MapBridge(), "AndroidCar");
        mapBox.addView(mapView, new FrameLayout.LayoutParams(-1, -1));

        TextView centerMarker = text("📍", 33, "#EF4444", true);
        centerMarker.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(64), dp(64));
        clp.gravity = Gravity.CENTER;
        mapBox.addView(centerMarker, clp);

        mapView.loadDataWithBaseURL(BASE_URL, mapHtml(), "text/html", "UTF-8", null);

        LinearLayout bottomCard = new LinearLayout(this);
        bottomCard.setOrientation(LinearLayout.VERTICAL);
        bottomCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        bottomCard.setBackground(roundStroke("#F8FBFF", "#D7E6F8", dp(22), 1));
        root.addView(bottomCard, new LinearLayout.LayoutParams(-1, -2));

        fareText = text("Tarif dihitung server setelah order dibuat", 12, "#64748B", false);
        bottomCard.addView(fareText);

        noteInput = new EditText(this);
        noteInput.setMinLines(2);
        noteInput.setMaxLines(3);
        noteInput.setTextSize(13);
        noteInput.setHint("Catatan untuk driver mobil...");
        noteInput.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(14), 1));
        noteInput.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, dp(64));
        nlp.setMargins(0, dp(8), 0, dp(10));
        bottomCard.addView(noteInput, nlp);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomCard.addView(actionRow, new LinearLayout.LayoutParams(-1, -2));

        backBtn = smallButton("Kembali", "#FFFFFF", "#0B7CFF", "#9DCAFF");
        gpsBtn = smallButton("GPS", "#FFFFFF", "#0B7CFF", "#9DCAFF");
        orderBtn = smallButton("Order Mobil", "#0B7CFF", "#FFFFFF", "#0B7CFF");
        actionRow.addView(backBtn, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(72), dp(48)); glp.setMargins(dp(8), 0, dp(8), 0);
        actionRow.addView(gpsBtn, glp);
        actionRow.addView(orderBtn, new LinearLayout.LayoutParams(0, dp(48), 1.3f));

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(54), dp(54));
        plp.gravity = Gravity.CENTER;
        page.addView(progressBar, plp);

        setContentView(page);
        bindActions();
        updateModeUI();
    }

    private void bindActions() {
        pickupBtn.setOnClickListener(v -> { mode = "pickup"; updateModeUI(); setPointFromCenter(); });
        deliveryBtn.setOnClickListener(v -> { mode = "delivery"; updateModeUI(); setPointFromCenter(); });
        gpsBtn.setOnClickListener(v -> goToMyLocation());
        backBtn.setOnClickListener(v -> finish());
        orderBtn.setOnClickListener(v -> createOrder());
        useLinkBtn.setOnClickListener(v -> useGoogleMapLink());
    }

    private String mapHtml() {
        return "<!DOCTYPE html><html><head>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>" +
                "<link rel='stylesheet' href='" + BASE_URL + "js/leaflet.css'>" +
                "<script src='" + BASE_URL + "js/leaflet.js'></script>" +
                "<style>html,body,#map{height:100%;margin:0;padding:0;background:#eef6ff;}" +
                ".leaflet-control-attribution,.leaflet-control-zoom{display:none!important;}" +
                ".leaflet-container{font-family:Arial,sans-serif;border-radius:22px;}" +
                ".pin{font-size:30px;text-align:center;filter:drop-shadow(0 4px 4px rgba(0,0,0,.30));}" +
                ".car{width:44px;height:44px;object-fit:contain;filter:drop-shadow(0 4px 6px rgba(0,0,0,.38));}" +
                ".route{stroke-linecap:round;stroke-linejoin:round;}" +
                "</style></head><body><div id='map'></div><script>" +
                "var map, pickup=null, delivery=null, route=null;" +
                "function ready(){try{map=L.map('map',{zoomControl:false,attributionControl:false}).setView(["+centerLat+","+centerLng+"],16);" +
                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:20,attribution:''}).addTo(map);" +
                "map.on('moveend',function(){var c=map.getCenter();try{AndroidCar.onCenterChanged(c.lat,c.lng);}catch(e){}});" +
                "setTimeout(function(){map.invalidateSize();var c=map.getCenter();try{AndroidCar.onMapReady(c.lat,c.lng);}catch(e){}},600);" +
                "}catch(e){setTimeout(ready,700);}}" +
                "function icon(t){return L.divIcon({html:'<div class=pin>'+t+'</div>',className:'',iconSize:[42,42],iconAnchor:[21,38]});}" +
                "function setPickup(lat,lng){lat=+lat;lng=+lng;if(!lat||!lng)return;if(pickup)pickup.setLatLng([lat,lng]);else pickup=L.marker([lat,lng],{icon:icon('🟢'),zIndexOffset:600}).addTo(map);draw();}" +
                "function setDelivery(lat,lng){lat=+lat;lng=+lng;if(!lat||!lng)return;if(delivery)delivery.setLatLng([lat,lng]);else delivery=L.marker([lat,lng],{icon:icon('🔴'),zIndexOffset:600}).addTo(map);draw();}" +
                "function moveTo(lat,lng,z){if(!map)return;map.setView([+lat,+lng],z||17,{animate:true});}" +
                "function draw(){if(route){map.removeLayer(route);route=null;}if(pickup&&delivery){route=L.polyline([pickup.getLatLng(),delivery.getLatLng()],{color:'#0B7CFF',weight:5,opacity:.85,dashArray:'8,8',className:'route'}).addTo(map);map.fitBounds([pickup.getLatLng(),delivery.getLatLng()],{padding:[70,70],maxZoom:16,animate:true});}}" +
                "ready();" +
                "</script></body></html>";
    }

    public class MapBridge {
        @JavascriptInterface public void onMapReady(double lat, double lng) {
            mainHandler.post(() -> { mapReady = true; centerLat = lat; centerLng = lng; goToMyLocation(); });
        }
        @JavascriptInterface public void onCenterChanged(double lat, double lng) {
            centerLat = lat; centerLng = lng;
        }
    }

    private void setPointFromCenter() {
        if (!validCoord(centerLat, centerLng)) return;
        if ("pickup".equals(mode)) {
            pickupLat = centerLat; pickupLng = centerLng;
            pickupText.setText(String.format(Locale.US, "Pickup: %.6f, %.6f", pickupLat, pickupLng));
            eval("setPickup(" + pickupLat + "," + pickupLng + ")");
            mode = "delivery";
        } else {
            deliveryLat = centerLat; deliveryLng = centerLng;
            deliveryText.setText(String.format(Locale.US, "Tujuan: %.6f, %.6f", deliveryLat, deliveryLng));
            eval("setDelivery(" + deliveryLat + "," + deliveryLng + ")");
        }
        updateModeUI();
    }

    private void goToMyLocation() {
        if (checkSelfPermissionCompat(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        setLoading(true);
        try {
            android.location.LocationManager lm = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
            Location best = null;
            for (String p : lm.getProviders(true)) {
                try {
                    Location l = lm.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getAccuracy() < best.getAccuracy())) best = l;
                } catch (Exception ignored) {}
            }
            if (best != null) {
                centerLat = best.getLatitude(); centerLng = best.getLongitude();
                eval("moveTo(" + centerLat + "," + centerLng + ",17)");
                if (!validCoord(pickupLat, pickupLng)) { mode = "pickup"; setPointFromCenter(); }
            } else {
                toastDialog("GPS belum mendapatkan lokasi. Aktifkan lokasi lalu tekan GPS lagi.");
            }
        } catch (Exception e) {
            toastDialog("GPS tidak tersedia di perangkat ini.");
        } finally {
            setLoading(false);
        }
    }

    private void useGoogleMapLink() {
        String link = googleMapInput.getText().toString().trim();
        if (link.length() == 0) { toastDialog("Masukkan link Google Maps tujuan terlebih dahulu."); return; }
        hideKeyboard();
        setLoading(true);
        new Thread(() -> {
            String finalLink = link;
            try {
                if (link.contains("maps.app.goo.gl") || link.contains("goo.gl/maps")) {
                    JSONObject res = postJson(RESOLVE_MAPS_URL, new JSONObject().put("url", link));
                    if (res.optBoolean("success", false) && res.optString("url", "").length() > 0) finalLink = res.optString("url");
                }
                double[] c = extractLatLng(finalLink);
                mainHandler.post(() -> {
                    setLoading(false);
                    if (c == null || !validCoord(c[0], c[1])) { toastDialog("Link Google Maps tidak bisa dibaca."); return; }
                    deliveryLat = c[0]; deliveryLng = c[1];
                    deliveryText.setText(String.format(Locale.US, "Tujuan: %.6f, %.6f", deliveryLat, deliveryLng));
                    googleMapInput.setText("");
                    eval("setDelivery(" + deliveryLat + "," + deliveryLng + ");moveTo(" + deliveryLat + "," + deliveryLng + ",17)");
                    mode = "delivery"; updateModeUI();
                });
            } catch (Exception e) {
                mainHandler.post(() -> { setLoading(false); toastDialog("Gagal membaca link Google Maps."); });
            }
        }).start();
    }

    private double[] extractLatLng(String link) {
        try {
            String decoded = java.net.URLDecoder.decode(link, "UTF-8");
            String[] patterns = new String[]{
                    "[?&]q=(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)",
                    "@(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)",
                    "!3d(-?\\d+(?:\\.\\d+)?)!4d(-?\\d+(?:\\.\\d+)?)"
            };
            for (String p : patterns) {
                Matcher m = Pattern.compile(p).matcher(decoded);
                if (m.find()) return new double[]{Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2))};
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void createOrder() {
        if (ordering) return;
        if (!validCoord(pickupLat, pickupLng)) { toastDialog("Pilih lokasi jemput terlebih dahulu."); return; }
        if (!validCoord(deliveryLat, deliveryLng)) { toastDialog("Pilih lokasi tujuan terlebih dahulu."); return; }
        ordering = true; setLoading(true); orderBtn.setEnabled(false); orderBtn.setText("Proses...");
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("username", username);
                payload.put("customer", username);
                payload.put("pickup_lat", pickupLat);
                payload.put("pickup_lng", pickupLng);
                payload.put("delivery_lat", deliveryLat);
                payload.put("delivery_lng", deliveryLng);
                payload.put("driver_type", "car");
                payload.put("order_type", "Transcar");
                payload.put("service_type", "Transcar");
                payload.put("service_name", "Transcar");
                payload.put("price_mode", "server");
                payload.put("note", noteInput.getText().toString().trim());
                JSONObject res = postJson(CREATE_ORDER_URL, payload);
                mainHandler.post(() -> handleOrderResult(res));
            } catch (Exception e) {
                mainHandler.post(() -> { resetOrderButton(); toastDialog("Gagal membuat order Transcar."); });
            }
        }).start();
    }

    private void handleOrderResult(JSONObject res) {
        resetOrderButton();
        if (res == null || !res.optBoolean("success", false)) {
            toastDialog(res != null ? res.optString("message", "Gagal membuat order mobil.") : "Gagal membuat order mobil.");
            return;
        }
        String orderId = firstNonEmpty(res.optString("order_id", ""), res.optString("id", ""));
        if (orderId.length() == 0) { toastDialog("Order berhasil, tetapi ID order tidak ditemukan."); return; }
        getSharedPreferences("transiva", MODE_PRIVATE).edit()
                .putString("active_order_id", orderId)
                .putString("active_order_type", "Transcar")
                .putString("active_driver_type", "car")
                .putString("active_service_name", "Transcar")
                .putString("pickup_lat", String.valueOf(pickupLat))
                .putString("pickup_lng", String.valueOf(pickupLng))
                .putString("delivery_lat", String.valueOf(deliveryLat))
                .putString("delivery_lng", String.valueOf(deliveryLng))
                .apply();
        try {
            Intent i = new Intent(this, SearchDriverActivity.class);
            i.putExtra("order_id", orderId);
            i.putExtra("active_driver_type", "car");
            i.putExtra("active_order_type", "Transcar");
            startActivity(i);
            finish();
        } catch (Exception e) {
            toastDialog("Order Transcar berhasil dibuat. ID: " + orderId);
        }
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
            writer.write(payload.toString()); writer.flush(); writer.close(); os.close();
            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
            String body = readStream(is).trim();
            return body.length() == 0 ? new JSONObject() : new JSONObject(body);
        } finally { if (conn != null) conn.disconnect(); }
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close(); return sb.toString();
    }

    private void updateModeUI() {
        boolean p = "pickup".equals(mode);
        modeText.setText(p ? "Geser peta lalu tekan Lokasi Jemput" : "Geser peta lalu tekan Lokasi Tujuan");
        pickupBtn.setAlpha(p ? 1f : .72f); deliveryBtn.setAlpha(p ? .72f : 1f);
    }

    private void eval(String js) { if (mapView != null && mapReady) try { mapView.evaluateJavascript(js, null); } catch (Exception ignored) {} }
    private boolean validCoord(double lat, double lng) { return Double.isFinite(lat) && Double.isFinite(lng) && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180 && lat != 0 && lng != 0; }
    private void resetOrderButton() { ordering = false; setLoading(false); orderBtn.setEnabled(true); orderBtn.setText("Order Mobil"); }
    private void setLoading(boolean b) { if (progressBar != null) progressBar.setVisibility(b ? View.VISIBLE : View.GONE); }
    private int checkSelfPermissionCompat(String p) { return android.os.Build.VERSION.SDK_INT >= 23 ? checkSelfPermission(p) : PackageManager.PERMISSION_GRANTED; }
    private void requestLocationIfNeeded() { if (checkSelfPermissionCompat(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 23) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION); }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) goToMyLocation();
    }

    private void hideKeyboard() { try { ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(googleMapInput.getWindowToken(), 0); } catch (Exception ignored) {} }
    private void toastDialog(String msg) { try { new AlertDialog.Builder(this).setTitle("Transiva").setMessage(msg).setPositiveButton("OK", null).show(); } catch (Exception ignored) {} }
    private String firstNonEmpty(String... v) { if (v == null) return ""; for (String s : v) if (s != null && s.trim().length() > 0 && !"null".equalsIgnoreCase(s.trim())) return s.trim(); return ""; }

    private Button rowButton(String value, String color) { Button b = smallButton(value, "#FFFFFF", color, "#E2E8F0"); b.setGravity(Gravity.CENTER_VERTICAL); b.setPadding(dp(12), 0, dp(12), 0); return b; }
    private Button smallButton(String value, String bg, String fg, String stroke) { Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextSize(13); b.setTypeface(Typeface.DEFAULT_BOLD); b.setTextColor(Color.parseColor(fg)); b.setBackground(roundStroke(bg, stroke, dp(16), 1)); return b; }
    private TextView text(String value, int sp, String color, boolean bold) { TextView tv = new TextView(this); tv.setText(value); tv.setTextSize(sp); tv.setTextColor(Color.parseColor(color)); if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD); return tv; }
    private GradientDrawable round(String color, int radius) { GradientDrawable gd = new GradientDrawable(); gd.setColor(Color.parseColor(color)); gd.setCornerRadius(radius); return gd; }
    private GradientDrawable roundStroke(String color, String stroke, int radius, int width) { GradientDrawable gd = round(color, radius); gd.setStroke(dp(width), Color.parseColor(stroke)); return gd; }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }

    @Override protected void onDestroy() { try { if (mapView != null) { mapView.stopLoading(); mapView.destroy(); } } catch (Exception ignored) {} super.onDestroy(); }
}
