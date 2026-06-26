package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import android.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PassengerCarActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String CREATE_ORDER_URL = BASE_URL + "server/createOrder.php";
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
    private int userId = 0;

    private double pickupLat = 0, pickupLng = 0;
    private double deliveryLat = 0, deliveryLng = 0;
    private double centerLat = -0.018137, centerLng = 120.087380;
    private double pickLat = 0, pickLng = 0;

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

        try {
            SessionManager session = new SessionManager(this);
            if (session.isLoggedIn()) {
                username = firstNonEmpty(
                        session.getUsername(),
                        session.getName(),
                        sp.getString("username", ""),
                        sp.getString("user_username", ""),
                        sp.getString("player_username", "")
                );

                try {
                    userId = Integer.parseInt(firstNonEmpty(
                            session.getId(),
                            session.getUserId(),
                            String.valueOf(sp.getInt("user_id", 0)),
                            String.valueOf(sp.getInt("id", 0)),
                            "0"
                    ));
                } catch (Exception ignored) {
                    userId = 0;
                }

                return;
            }
        } catch (Exception ignored) {}

        username = firstNonEmpty(
                sp.getString("username", ""),
                sp.getString("user_username", ""),
                sp.getString("player_username", "")
        );

        userId = sp.getInt(
                "user_id",
                sp.getInt("id", 0)
        );

        if (userId <= 0) {
            try {
                userId = Integer.parseInt(firstNonEmpty(
                        sp.getString("user_id", ""),
                        sp.getString("id", ""),
                        "0"
                ));
            } catch (Exception ignored) {
                userId = 0;
            }
        }
    }

    private void buildLayout() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#071426"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(10), dp(10), dp(10));
        page.addView(root, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout topCard = new LinearLayout(this);
        topCard.setOrientation(LinearLayout.VERTICAL);
        topCard.setPadding(dp(10), dp(8), dp(10), dp(8));
        topCard.setBackground(roundStroke("#F8FBFF", "#D7E6F8", dp(18), 1));
        root.addView(topCard, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        topCard.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        TextView title = text("Transcar", 18, "#0B3A78", true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, dp(34), 1));

        Button close = smallButton("×", "#FEE2E2", "#DC2626", "#FECACA");
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(34), dp(34)));
        close.setOnClickListener(v -> finish());

        modeText = text("Geser peta lalu pilih titik jemput / tujuan", 11, "#64748B", false);
        modeText.setPadding(0, 0, 0, dp(6));
        topCard.addView(modeText);

        LinearLayout pointRow = new LinearLayout(this);
        pointRow.setOrientation(LinearLayout.HORIZONTAL);
        topCard.addView(pointRow, new LinearLayout.LayoutParams(-1, dp(56)));

        pickupBtn = compactPointButton("●  Lokasi Jemput", "Belum dipilih", "#16A34A");
        deliveryBtn = compactPointButton("●  Lokasi Tujuan", "Belum dipilih", "#EF4444");

        pointRow.addView(pickupBtn, new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, -1, 1);
        dlp.setMargins(dp(6), 0, 0, 0);
        pointRow.addView(deliveryBtn, dlp);

        pickupText = text("Pickup: belum dipilih", 10, "#334155", false);
        deliveryText = text("Tujuan: belum dipilih", 10, "#334155", false);
        pickupText.setVisibility(View.GONE);
        deliveryText.setVisibility(View.GONE);

        LinearLayout linkRow = new LinearLayout(this);
        linkRow.setOrientation(LinearLayout.HORIZONTAL);
        linkRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams linkLp = new LinearLayout.LayoutParams(-1, dp(42));
        linkLp.setMargins(0, dp(7), 0, 0);
        topCard.addView(linkRow, linkLp);

        googleMapInput = new EditText(this);
        googleMapInput.setSingleLine(true);
        googleMapInput.setTextSize(11);
        googleMapInput.setHint("🔗 Link Google Maps tujuan...");
        googleMapInput.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(14), 1));
        googleMapInput.setPadding(dp(10), 0, dp(10), 0);
        linkRow.addView(googleMapInput, new LinearLayout.LayoutParams(0, -1, 1));

        useLinkBtn = smallButton("Pakai", "#EAF4FF", "#0B7CFF", "#9DCAFF");
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(dp(70), -1);
        ulp.setMargins(dp(7), 0, 0, 0);
        linkRow.addView(useLinkBtn, ulp);

        FrameLayout mapBox = new FrameLayout(this);
        LinearLayout.LayoutParams mapLp = new LinearLayout.LayoutParams(-1, 0, 1);
        mapLp.setMargins(0, dp(8), 0, dp(8));
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

        FrameLayout centerMarkerBox = new FrameLayout(this);

        /*
         * Ukuran center marker dibuat sama dengan marker hijau/merah Leaflet.
         * Jika ingin semua marker terlihat seimbang, cukup ubah CENTER_PIN_W/H
         * dan pastikan nilainya sama dengan assetpin pada CSS mapHtml().
         */
        final int CENTER_PIN_W = 54;
        final int CENTER_PIN_H = 66;

        FrameLayout.LayoutParams clp =
                new FrameLayout.LayoutParams(
                        dp(CENTER_PIN_W),
                        dp(CENTER_PIN_H)
                );

        clp.gravity = Gravity.CENTER;

        /*
         * Karena icon center berupa overlay native di atas WebView,
         * topMargin mengatur posisi visual agar ujung pin berada di titik tengah map.
         * Nilai ini dibuat lebih kecil agar icon biru tidak terlihat terlalu besar/naik.
         */
        clp.topMargin = -dp(18);
        clp.leftMargin = 0;

        mapBox.addView(centerMarkerBox, clp);

        int centerPinId = getDrawableId("map_center_pin", "ic_center_pin", "center_pin", "map_destination_pin");
        if (centerPinId > 0) {
            ImageView centerMarker = new ImageView(this);
            centerMarker.setImageResource(centerPinId);
            centerMarker.setScaleType(ImageView.ScaleType.FIT_CENTER);
            centerMarker.setAdjustViewBounds(false);
            centerMarker.setPadding(0, 0, 0, 0);
            centerMarkerBox.addView(centerMarker, new FrameLayout.LayoutParams(-1, -1));
        } else {
            TextView centerMarker = text("📍", 28, "#EF4444", true);
            centerMarker.setGravity(Gravity.CENTER);
            centerMarkerBox.addView(centerMarker, new FrameLayout.LayoutParams(-1, -1));
        }

        mapView.loadDataWithBaseURL(BASE_URL, mapHtml(), "text/html", "UTF-8", null);

        LinearLayout bottomCard = new LinearLayout(this);
        bottomCard.setOrientation(LinearLayout.VERTICAL);
        bottomCard.setPadding(dp(10), dp(8), dp(10), dp(8));
        bottomCard.setBackground(roundStroke("#F8FBFF", "#D7E6F8", dp(18), 1));
        root.addView(bottomCard, new LinearLayout.LayoutParams(-1, -2));

        fareText = text("Tarif dihitung server setelah order dibuat", 10, "#64748B", false);
        bottomCard.addView(fareText);

        noteInput = new EditText(this);
        noteInput.setSingleLine(true);
        noteInput.setTextSize(12);
        noteInput.setHint("Catatan untuk driver mobil...");
        noteInput.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(14), 1));
        noteInput.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, dp(42));
        nlp.setMargins(0, dp(7), 0, dp(8));
        bottomCard.addView(noteInput, nlp);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomCard.addView(actionRow, new LinearLayout.LayoutParams(-1, -2));

        backBtn = smallButton("← Kembali", "#FFFFFF", "#0B7CFF", "#9DCAFF");
        gpsBtn = smallButton("⌖ GPS", "#FFFFFF", "#0B7CFF", "#9DCAFF");
        orderBtn = smallButton("🚘 Order Mobil", "#0B7CFF", "#FFFFFF", "#0B7CFF");
        actionRow.addView(backBtn, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(0, dp(44), 1);
        glp.setMargins(dp(7), 0, dp(7), 0);
        actionRow.addView(gpsBtn, glp);
        actionRow.addView(orderBtn, new LinearLayout.LayoutParams(0, dp(44), 1.45f));

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
        String pickupIcon = drawableDataUri("map_pickup_pin", "ic_pickup_pin", "pickup_pin", "pickup", "point_pickup", "ic_pickup");
        String deliveryIcon = drawableDataUri("map_destination_pin", "map_delivery_pin", "ic_delivery_pin", "delivery_pin", "delivery", "point_delivery", "ic_delivery");
        String carIcon = drawableDataUri("map_car_top", "ic_car_top", "car_top", "ic_transcar", "transcar", "car", "transcar_marker");
        String motorIcon = drawableDataUri("map_motor_top", "ic_motor_top", "motor_top", "ic_transbike", "transbike", "motor", "bike_marker");

        return "<!DOCTYPE html><html><head>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>" +
                "<link rel='stylesheet' href='" + BASE_URL + "js/leaflet.css'>" +
                "<script src='" + BASE_URL + "js/leaflet.js'></script>" +
                "<style>html,body,#map{height:100%;margin:0;padding:0;background:#eef6ff;overflow:hidden;}" +
                ".leaflet-control-attribution,.leaflet-control-zoom{display:none!important;}" +
                ".leaflet-container{font-family:Arial,sans-serif;border-radius:18px;background:#eef6ff;}" +
                ".pin{font-size:34px;text-align:center;filter:drop-shadow(0 6px 6px rgba(0,0,0,.28));}" +
                ".assetpin{width:54px;height:66px;object-fit:contain;filter:drop-shadow(0 6px 6px rgba(0,0,0,.30));}" +
                ".carpin{width:46px;height:46px;object-fit:contain;filter:drop-shadow(0 6px 6px rgba(0,0,0,.30));}" +
                ".route{stroke-linecap:round;stroke-linejoin:round;}" +
                "</style></head><body><div id='map'></div><script>" +
                "var map, pickup=null, delivery=null, route=null, allowRoute=false;" +
                "var pickupIconData='" + js(pickupIcon) + "', deliveryIconData='" + js(deliveryIcon) + "', carIconData='" + js(carIcon) + "', motorIconData='" + js(motorIcon) + "';" +
                "function ready(){try{map=L.map('map',{zoomControl:false,attributionControl:false}).setView(["+centerLat+","+centerLng+"],17);" +
                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:20,attribution:''}).addTo(map);" +
                "function notifyCenter(){var c=map.getCenter();try{AndroidCar.onCenterChanged(c.lat,c.lng,c.lat,c.lng);}catch(e){}}" +
                "map.on('moveend',notifyCenter);map.on('zoomend',notifyCenter);" +
                "setTimeout(function(){map.invalidateSize();var c=map.getCenter();try{AndroidCar.onMapReady(c.lat,c.lng,c.lat,c.lng);}catch(e){}},600);" +
                "}catch(e){setTimeout(ready,700);}}" +
                "function iconData(data, fallback){if(data&&data.length>20){return L.divIcon({html:'<img class=assetpin src=\"'+data+'\">',className:'',iconSize:[54,66],iconAnchor:[27,54]});}return L.divIcon({html:'<div class=pin>'+fallback+'</div>',className:'',iconSize:[46,46],iconAnchor:[23,40]});}" +
                "function carData(){if(carIconData&&carIconData.length>20){return L.divIcon({html:'<img class=carpin src=\"'+carIconData+'\">',className:'',iconSize:[46,46],iconAnchor:[23,23]});}return L.divIcon({html:'<div class=pin>🚘</div>',className:'',iconSize:[46,46],iconAnchor:[23,28]});}" +
                "function setPickup(lat,lng){lat=+lat;lng=+lng;if(!lat||!lng)return;if(pickup)pickup.setLatLng([lat,lng]);else pickup=L.marker([lat,lng],{icon:iconData(pickupIconData,'🟢'),zIndexOffset:600}).addTo(map);}" +
                "function setDelivery(lat,lng){lat=+lat;lng=+lng;if(!lat||!lng)return;if(delivery)delivery.setLatLng([lat,lng]);else delivery=L.marker([lat,lng],{icon:iconData(deliveryIconData,'🔴'),zIndexOffset:600}).addTo(map);}" +
                "function moveTo(lat,lng,z){if(!map)return;map.setView([+lat,+lng],z||17,{animate:true});}" +
                "function drawRouteAfterOrder(){if(route){map.removeLayer(route);route=null;}if(pickup&&delivery){var a=pickup.getLatLng(),b=delivery.getLatLng();route=L.polyline([a,b],{color:'#0B7CFF',weight:5,opacity:.9,dashArray:'8,8',className:'route'}).addTo(map);map.fitBounds([a,b],{padding:[60,60],maxZoom:17,animate:true});}}" +
                "ready();" +
                "</script></body></html>";
    }

    public class MapBridge {
        @JavascriptInterface public void onMapReady(double lat, double lng, double pLat, double pLng) {
            mainHandler.post(() -> {
                mapReady = true;
                centerLat = lat;
                centerLng = lng;
                pickLat = validCoord(pLat, pLng) ? pLat : lat;
                pickLng = validCoord(pLat, pLng) ? pLng : lng;
                goToMyLocation();
            });
        }
        @JavascriptInterface public void onCenterChanged(double lat, double lng, double pLat, double pLng) {
            centerLat = lat;
            centerLng = lng;
            pickLat = validCoord(pLat, pLng) ? pLat : lat;
            pickLng = validCoord(pLat, pLng) ? pLng : lng;
        }
    }

    private void setPointFromCenter() {
        double selectedLat = validCoord(pickLat, pickLng) ? pickLat : centerLat;
        double selectedLng = validCoord(pickLat, pickLng) ? pickLng : centerLng;
        if (!validCoord(selectedLat, selectedLng)) return;

        if ("pickup".equals(mode)) {
            pickupLat = selectedLat;
            pickupLng = selectedLng;
            pickupText.setText(String.format(Locale.US, "Pickup: %.6f, %.6f", pickupLat, pickupLng));
            pickupBtn.setText("●  Jemput\n" + shortCoord(pickupLat, pickupLng));
            eval("setPickup(" + pickupLat + "," + pickupLng + ")");
            mode = "delivery";
        } else {
            deliveryLat = selectedLat;
            deliveryLng = selectedLng;
            deliveryText.setText(String.format(Locale.US, "Tujuan: %.6f, %.6f", deliveryLat, deliveryLng));
            deliveryBtn.setText("●  Tujuan\n" + shortCoord(deliveryLat, deliveryLng));
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
                centerLat = best.getLatitude(); centerLng = best.getLongitude(); pickLat = centerLat; pickLng = centerLng;
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

        if (userId <= 0) {
            readUser();
        }

        if (userId <= 0) {
            toastDialog("User ID tidak ditemukan. Silakan login ulang.");
            return;
        }

        if (!validCoord(pickupLat, pickupLng)) {
            toastDialog("Pilih lokasi jemput terlebih dahulu.");
            return;
        }

        if (!validCoord(deliveryLat, deliveryLng)) {
            toastDialog("Pilih lokasi tujuan terlebih dahulu.");
            return;
        }

        ordering = true;
        setLoading(true);
        orderBtn.setEnabled(false);
        orderBtn.setText("Proses...");

        new Thread(() -> {
            try {
                String orderId = "ORD-" + System.currentTimeMillis();

                JSONObject pickup = new JSONObject();
                pickup.put("latitude", pickupLat);
                pickup.put("longitude", pickupLng);
                pickup.put("address", "Lokasi Jemput");

                JSONObject delivery = new JSONObject();
                delivery.put("latitude", deliveryLat);
                delivery.put("longitude", deliveryLng);
                delivery.put("address", "Lokasi Tujuan");

                JSONObject userLocation = new JSONObject();
                userLocation.put("latitude", pickupLat);
                userLocation.put("longitude", pickupLng);

                JSONObject payload = new JSONObject();
                payload.put("id", orderId);
                payload.put("user_id", userId);
                payload.put("username", username);
                payload.put("customer", username);
                payload.put("order_type", "Transcar");
                payload.put("driver_type", "car");
                payload.put("service_type", "Transcar");
                payload.put("service_name", "Transcar");
                payload.put("price_mode", "server");
                payload.put("pickup", pickup);
                payload.put("delivery", delivery);
                payload.put("userLocation", userLocation);
                payload.put("note", noteInput.getText().toString().trim());

                JSONObject res = postJson(CREATE_ORDER_URL, payload);
                mainHandler.post(() -> handleOrderResult(res));
            } catch (Exception e) {
                mainHandler.post(() -> {
                    resetOrderButton();
                    toastDialog("Gagal membuat order Transcar. " + cleanError(e.getMessage()));
                });
            }
        }).start();
    }

    private void handleOrderResult(JSONObject res) {
        resetOrderButton();

        if (res == null || !res.optBoolean("success", false)) {
            String msg = res != null
                    ? res.optString("message", "Gagal membuat order mobil.")
                    : "Gagal membuat order mobil.";
            toastDialog(msg);
            return;
        }

        String orderId = firstNonEmpty(res.optString("order_id", ""), res.optString("id", ""));
        if (orderId.length() == 0) {
            toastDialog("Order berhasil, tetapi ID order tidak ditemukan.");
            return;
        }

        getSharedPreferences("transiva", MODE_PRIVATE).edit()
                .putString("active_order_id", orderId)
                .putString("active_order_type", "Transcar")
                .putString("active_driver_type", "car")
                .putString("active_service_name", "Transcar")
                .putString("pickup_lat", String.valueOf(pickupLat))
                .putString("pickup_lng", String.valueOf(pickupLng))
                .putString("delivery_lat", String.valueOf(deliveryLat))
                .putString("delivery_lng", String.valueOf(deliveryLng))
                .putString("active_order_price", res.optString("price", ""))
                .apply();

        try {
            Intent i = new Intent(this, SearchDriverActivity.class);
            i.putExtra("order_id", orderId);
            i.putExtra("active_order_id", orderId);
            i.putExtra("active_driver_type", "car");
            i.putExtra("driver_type", "car");
            i.putExtra("active_order_type", "Transcar");
            i.putExtra("pickup_lat", String.valueOf(pickupLat));
            i.putExtra("pickup_lng", String.valueOf(pickupLng));
            i.putExtra("delivery_lat", String.valueOf(deliveryLat));
            i.putExtra("delivery_lng", String.valueOf(deliveryLng));
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
        modeText.setText(p ? "Geser peta lalu tekan Jemput" : "Geser peta lalu tekan Tujuan");
        pickupBtn.setAlpha(p ? 1f : .80f);
        deliveryBtn.setAlpha(p ? .80f : 1f);
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

    private Button compactPointButton(String title, String sub, String color) {
        Button b = new Button(this);
        b.setText(title + "\n" + sub);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(10), 0, dp(8), 0);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.parseColor(color));
        b.setBackground(roundStroke("#FFFFFF", "#E2E8F0", dp(14), 1));
        return b;
    }

    private String shortCoord(double lat, double lng) {
        return String.format(Locale.US, "%.5f, %.5f", lat, lng);
    }

    private String cleanError(String value) {
        String v = firstNonEmpty(value, "");
        if (v.length() == 0) return "";
        if (v.length() > 160) v = v.substring(0, 160);
        return v;
    }

    private String js(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "");
    }

    private int getDrawableId(String... names) {
        try {
            for (String name : names) {
                int id = getResources().getIdentifier(name, "drawable", getPackageName());
                if (id > 0) return id;
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private String drawableDataUri(String... names) {
        try {
            for (String name : names) {
                int id = getResources().getIdentifier(name, "drawable", getPackageName());
                if (id <= 0) continue;

                Bitmap bm = BitmapFactory.decodeResource(getResources(), id);
                if (bm == null) continue;

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                bm.compress(Bitmap.CompressFormat.PNG, 100, out);
                String b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
                try { bm.recycle(); } catch (Exception ignored) {}
                return "data:image/png;base64," + b64;
            }
        } catch (Exception ignored) {}

        return "";
    }

    private Button rowButton(String value, String color) { Button b = smallButton(value, "#FFFFFF", color, "#E2E8F0"); b.setGravity(Gravity.CENTER_VERTICAL); b.setPadding(dp(12), 0, dp(12), 0); return b; }
    private Button smallButton(String value, String bg, String fg, String stroke) { Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextSize(13); b.setTypeface(Typeface.DEFAULT_BOLD); b.setTextColor(Color.parseColor(fg)); b.setBackground(roundStroke(bg, stroke, dp(16), 1)); return b; }
    private TextView text(String value, int sp, String color, boolean bold) { TextView tv = new TextView(this); tv.setText(value); tv.setTextSize(sp); tv.setTextColor(Color.parseColor(color)); if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD); return tv; }
    private GradientDrawable round(String color, int radius) { GradientDrawable gd = new GradientDrawable(); gd.setColor(Color.parseColor(color)); gd.setCornerRadius(radius); return gd; }
    private GradientDrawable roundStroke(String color, String stroke, int radius, int width) { GradientDrawable gd = round(color, radius); gd.setStroke(dp(width), Color.parseColor(stroke)); return gd; }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }

    @Override protected void onDestroy() { try { if (mapView != null) { mapView.stopLoading(); mapView.destroy(); } } catch (Exception ignored) {} super.onDestroy(); }
}