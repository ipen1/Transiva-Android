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
import org.json.JSONArray;

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

public class TransRideActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String CREATE_ORDER_URL = BASE_URL + "server/createOrder.php";
    private static final String PAYMENT_QUOTE_URL = BASE_URL + "server/ride_payment_quote.php";
    private static final String RESOLVE_MAPS_URL = BASE_URL + "server/resolve_google_maps.php";
    private static final String GET_BUSINESSES_URL = BASE_URL + "server/getBusinesses.php";
    private static final String GET_LAUNDRIES_URL = BASE_URL + "server/admin_get_laundries.php";
    private static final String GET_ONLINE_DRIVERS_URL = BASE_URL + "server/get_map_drivers.php";
    private static final int REQ_LOCATION = 44;
    private static final int TIMEOUT_MS = 25000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WebView mapView;
    private TextView pickupText, deliveryText, modeText, fareText, paymentSummaryText;
    private TextView originalFareText, finalFareText, distanceEstimateText;
    private EditText googleMapInput, noteInput, voucherInput;
    private Button pickupBtn, deliveryBtn, gpsBtn, orderBtn, backBtn, useLinkBtn;
    private ProgressBar progressBar;

    private boolean mapReady = false;
    private boolean ordering = false;
    private String mode = "pickup";
    private String username = "";
    private String authToken = "";
    private String paymentMethod = "cash";
    private int userId = 0;

    private double pickupLat = 0, pickupLng = 0;
    private double deliveryLat = 0, deliveryLng = 0;
    private double centerLat = -0.018137, centerLng = 120.087380;
    private double pickLat = 0, pickLng = 0;

    private String pickupAddress = "Lokasi Jemput";
    private String deliveryAddress = "Lokasi Tujuan";
    private boolean destroyed = false;

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
                authToken = session.getToken();
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
        root.setPadding(dp(7), dp(7), dp(7), dp(7));
        page.addView(root, new FrameLayout.LayoutParams(-1, -1));

        /* =========================
         * HEADER COMPACT
         * ========================= */
        LinearLayout topCard = new LinearLayout(this);
        topCard.setOrientation(LinearLayout.VERTICAL);
        topCard.setPadding(dp(9), dp(7), dp(9), dp(7));
        topCard.setBackground(roundStroke("#F8FBFF", "#D7E6F8", dp(14), 1));
        root.addView(topCard, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        topCard.addView(titleRow, new LinearLayout.LayoutParams(-1, dp(30)));

        TextView title = text("TransRide", 17, "#0B3A78", true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        modeText = text("Geser peta, lalu pilih titik", 10, "#64748B", false);
        modeText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        titleRow.addView(modeText, new LinearLayout.LayoutParams(0, -1, 1.25f));

        Button close = smallButton("×", "#FEE2E2", "#DC2626", "#FECACA");
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(30), dp(30));
        closeLp.setMargins(dp(5), 0, 0, 0);
        titleRow.addView(close, closeLp);
        close.setOnClickListener(v -> finish());

        LinearLayout pointRow = new LinearLayout(this);
        pointRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams pointRowLp = new LinearLayout.LayoutParams(-1, dp(50));
        pointRowLp.setMargins(0, dp(4), 0, 0);
        topCard.addView(pointRow, pointRowLp);

        pickupBtn = compactPointButton("●  Jemput", "Belum dipilih", "#16A34A");
        deliveryBtn = compactPointButton("●  Tujuan", "Belum dipilih", "#EF4444");

        pointRow.addView(pickupBtn, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams deliveryLp = new LinearLayout.LayoutParams(0, -1, 1);
        deliveryLp.setMargins(dp(5), 0, 0, 0);
        pointRow.addView(deliveryBtn, deliveryLp);

        pickupText = text("Pickup: belum dipilih", 9, "#334155", false);
        deliveryText = text("Tujuan: belum dipilih", 9, "#334155", false);
        pickupText.setVisibility(View.GONE);
        deliveryText.setVisibility(View.GONE);

        LinearLayout linkRow = new LinearLayout(this);
        linkRow.setOrientation(LinearLayout.HORIZONTAL);
        linkRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams linkLp = new LinearLayout.LayoutParams(-1, dp(36));
        linkLp.setMargins(0, dp(5), 0, 0);
        topCard.addView(linkRow, linkLp);

        googleMapInput = new EditText(this);
        googleMapInput.setSingleLine(true);
        googleMapInput.setTextSize(10);
        googleMapInput.setHint("Link Google Maps tujuan (opsional)");
        googleMapInput.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(11), 1));
        googleMapInput.setPadding(dp(9), 0, dp(9), 0);
        linkRow.addView(googleMapInput, new LinearLayout.LayoutParams(0, -1, 1));

        useLinkBtn = smallButton("Pakai", "#EAF4FF", "#0B7CFF", "#9DCAFF");
        LinearLayout.LayoutParams useLinkLp = new LinearLayout.LayoutParams(dp(62), -1);
        useLinkLp.setMargins(dp(5), 0, 0, 0);
        linkRow.addView(useLinkBtn, useLinkLp);

        /* =========================
         * MAP - PRIORITAS RUANG UTAMA
         * ========================= */
        FrameLayout mapBox = new FrameLayout(this);
        mapBox.setBackground(roundStroke("#EAF4FF", "#AFCFF2", dp(14), 1));
        LinearLayout.LayoutParams mapLp = new LinearLayout.LayoutParams(-1, 0, 1);
        mapLp.setMargins(0, dp(6), 0, dp(6));
        root.addView(mapBox, mapLp);

        mapView = new WebView(this);
        mapView.setBackgroundColor(Color.parseColor("#EAF4FF"));
        mapView.setWebViewClient(new WebViewClient());
        WebSettings settings = mapView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        mapView.addJavascriptInterface(new MapBridge(), "AndroidBike");

        FrameLayout.LayoutParams webLp = new FrameLayout.LayoutParams(-1, -1);
        webLp.setMargins(dp(1), dp(1), dp(1), dp(1));
        mapBox.addView(mapView, webLp);

        FrameLayout centerMarkerBox = new FrameLayout(this);
        final int CENTER_PIN_W = 46;
        final int CENTER_PIN_H = 58;
        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(
                dp(CENTER_PIN_W),
                dp(CENTER_PIN_H)
        );
        centerLp.gravity = Gravity.CENTER;
        centerLp.topMargin = -dp(15);
        centerLp.leftMargin = dp(4);
        mapBox.addView(centerMarkerBox, centerLp);

        int centerPinId = getDrawableId(
                "map_center_pin",
                "ic_center_pin",
                "center_pin",
                "map_destination_pin"
        );
        if (centerPinId > 0) {
            ImageView centerMarker = new ImageView(this);
            centerMarker.setImageResource(centerPinId);
            centerMarker.setScaleType(ImageView.ScaleType.FIT_CENTER);
            centerMarkerBox.addView(centerMarker, new FrameLayout.LayoutParams(-1, -1));
        } else {
            TextView centerMarker = text("📍", 25, "#EF4444", true);
            centerMarker.setGravity(Gravity.CENTER);
            centerMarkerBox.addView(centerMarker, new FrameLayout.LayoutParams(-1, -1));
        }

        mapView.loadDataWithBaseURL(BASE_URL, mapHtml(), "text/html", "UTF-8", null);

        /* =========================
         * PAYMENT CARD COMPACT
         * ========================= */
        LinearLayout bottomCard = new LinearLayout(this);
        bottomCard.setOrientation(LinearLayout.VERTICAL);
        bottomCard.setPadding(dp(9), dp(7), dp(9), dp(7));
        bottomCard.setBackground(roundStroke("#F8FBFF", "#D7E6F8", dp(14), 1));
        root.addView(bottomCard, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout paymentHeader = new LinearLayout(this);
        paymentHeader.setGravity(Gravity.CENTER_VERTICAL);
        bottomCard.addView(paymentHeader, new LinearLayout.LayoutParams(-1, dp(22)));

        TextView paymentTitle = text("Metode Pembayaran", 11, "#0B3A78", true);
        paymentHeader.addView(paymentTitle, new LinearLayout.LayoutParams(0, -1, 1));

        fareText = text("Menunggu tujuan", 9, "#64748B", false);
        fareText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        paymentHeader.addView(fareText, new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout paymentRow = new LinearLayout(this);
        paymentRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams paymentRowLp = new LinearLayout.LayoutParams(-1, dp(36));
        paymentRowLp.setMargins(0, dp(3), 0, 0);
        bottomCard.addView(paymentRow, paymentRowLp);

        Button cashButton = smallButton("Tunai", "#0B7CFF", "#FFFFFF", "#0B7CFF");
        Button balanceButton = smallButton("Transiva Pay", "#FFFFFF", "#0B7CFF", "#9DCAFF");
        cashButton.setTextSize(11);
        balanceButton.setTextSize(11);

        paymentRow.addView(cashButton, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams balanceLp = new LinearLayout.LayoutParams(0, -1, 1);
        balanceLp.setMargins(dp(5), 0, 0, 0);
        paymentRow.addView(balanceButton, balanceLp);

        cashButton.setOnClickListener(v -> {
            paymentMethod = "cash";
            cashButton.setBackground(roundStroke("#0B7CFF", "#0B7CFF", dp(10), 1));
            cashButton.setTextColor(Color.WHITE);
            balanceButton.setBackground(roundStroke("#FFFFFF", "#9DCAFF", dp(10), 1));
            balanceButton.setTextColor(Color.parseColor("#0B7CFF"));
            requestPaymentQuote();
        });

        balanceButton.setOnClickListener(v -> {
            paymentMethod = "balance";
            balanceButton.setBackground(roundStroke("#0B7CFF", "#0B7CFF", dp(10), 1));
            balanceButton.setTextColor(Color.WHITE);
            cashButton.setBackground(roundStroke("#FFFFFF", "#9DCAFF", dp(10), 1));
            cashButton.setTextColor(Color.parseColor("#0B7CFF"));
            requestPaymentQuote();
        });

        // Panel estimasi tarif ringkas. Nilai selalu berasal dari server/database.
        LinearLayout fareEstimateBox = new LinearLayout(this);
        fareEstimateBox.setOrientation(LinearLayout.HORIZONTAL);
        fareEstimateBox.setGravity(Gravity.CENTER_VERTICAL);
        fareEstimateBox.setPadding(dp(9), dp(4), dp(9), dp(4));
        fareEstimateBox.setBackground(roundStroke("#EDF6FF", "#C8E1FF", dp(10), 1));
        LinearLayout.LayoutParams fareBoxLp = new LinearLayout.LayoutParams(-1, dp(43));
        fareBoxLp.setMargins(0, dp(5), 0, 0);
        bottomCard.addView(fareEstimateBox, fareBoxLp);

        LinearLayout fareInfoColumn = new LinearLayout(this);
        fareInfoColumn.setOrientation(LinearLayout.VERTICAL);
        fareInfoColumn.setGravity(Gravity.CENTER_VERTICAL);
        fareEstimateBox.addView(fareInfoColumn, new LinearLayout.LayoutParams(0, -1, 1));

        TextView estimateLabel = text("Estimasi ongkir", 9, "#64748B", false);
        fareInfoColumn.addView(estimateLabel, new LinearLayout.LayoutParams(-1, dp(17)));

        distanceEstimateText = text("Pilih lokasi tujuan", 9, "#0B3A78", true);
        fareInfoColumn.addView(distanceEstimateText, new LinearLayout.LayoutParams(-1, dp(18)));

        LinearLayout priceColumn = new LinearLayout(this);
        priceColumn.setOrientation(LinearLayout.VERTICAL);
        priceColumn.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        fareEstimateBox.addView(priceColumn, new LinearLayout.LayoutParams(dp(145), -1));

        originalFareText = text("", 9, "#94A3B8", false);
        originalFareText.setGravity(Gravity.END);
        originalFareText.setVisibility(View.GONE);
        originalFareText.setPaintFlags(originalFareText.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        priceColumn.addView(originalFareText, new LinearLayout.LayoutParams(-1, dp(16)));

        finalFareText = text("Rp0", 14, "#0B7CFF", true);
        finalFareText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        priceColumn.addView(finalFareText, new LinearLayout.LayoutParams(-1, dp(21)));

        LinearLayout voucherRow = new LinearLayout(this);
        voucherRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams voucherRowLp = new LinearLayout.LayoutParams(-1, dp(36));
        voucherRowLp.setMargins(0, dp(5), 0, 0);
        bottomCard.addView(voucherRow, voucherRowLp);

        voucherInput = new EditText(this);
        voucherInput.setSingleLine(true);
        voucherInput.setTextSize(10);
        voucherInput.setHint("Kode voucher");
        voucherInput.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(11), 1));
        voucherInput.setPadding(dp(9), 0, dp(9), 0);
        voucherRow.addView(voucherInput, new LinearLayout.LayoutParams(0, -1, 1));

        Button voucherButton = smallButton("Cek", "#E8F3FF", "#0B7CFF", "#9DCAFF");
        voucherButton.setTextSize(10);
        LinearLayout.LayoutParams voucherButtonLp = new LinearLayout.LayoutParams(dp(56), -1);
        voucherButtonLp.setMargins(dp(5), 0, 0, 0);
        voucherRow.addView(voucherButton, voucherButtonLp);
        voucherButton.setOnClickListener(v -> requestPaymentQuote());

        paymentSummaryText = text(
                "Voucher belum digunakan • Tunai",
                9,
                "#64748B",
                false
        );
        paymentSummaryText.setSingleLine(true);
        LinearLayout.LayoutParams summaryLp = new LinearLayout.LayoutParams(-1, dp(18));
        summaryLp.setMargins(0, dp(2), 0, 0);
        bottomCard.addView(paymentSummaryText, summaryLp);

        noteInput = new EditText(this);
        noteInput.setSingleLine(true);
        noteInput.setTextSize(10);
        noteInput.setHint("Catatan driver (opsional)");
        noteInput.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(11), 1));
        noteInput.setPadding(dp(9), 0, dp(9), 0);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, dp(36));
        noteLp.setMargins(0, dp(3), 0, dp(5));
        bottomCard.addView(noteInput, noteLp);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomCard.addView(actionRow, new LinearLayout.LayoutParams(-1, dp(40)));

        backBtn = smallButton("← Kembali", "#FFFFFF", "#0B7CFF", "#9DCAFF");
        gpsBtn = smallButton("⌖ GPS", "#FFFFFF", "#0B7CFF", "#9DCAFF");
        orderBtn = smallButton("🏍 Order", "#0B7CFF", "#FFFFFF", "#0B7CFF");
        backBtn.setTextSize(10);
        gpsBtn.setTextSize(10);
        orderBtn.setTextSize(11);

        actionRow.addView(backBtn, new LinearLayout.LayoutParams(0, -1, 0.92f));
        LinearLayout.LayoutParams gpsLp = new LinearLayout.LayoutParams(0, -1, 0.78f);
        gpsLp.setMargins(dp(5), 0, dp(5), 0);
        actionRow.addView(gpsBtn, gpsLp);
        actionRow.addView(orderBtn, new LinearLayout.LayoutParams(0, -1, 1.25f));

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressLp = new FrameLayout.LayoutParams(dp(50), dp(50));
        progressLp.gravity = Gravity.CENTER;
        page.addView(progressBar, progressLp);

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
        String bikeIcon = drawableDataUri("map_motor_top", "ic_motor_top", "motor_top", "ic_transbike", "transbike", "motor", "bike_marker");
        String placeIcon = drawableDataUri("mark", "map_place_pin", "business_pin", "merchant_pin", "laundry_pin");
        String driverIcon = drawableDataUri("user", "driver_online", "ic_driver_online", "map_motor_top", "ic_motor_top", "motor_top");

        return "<!DOCTYPE html><html><head>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>" +
                "<link rel='stylesheet' href='" + BASE_URL + "js/leaflet.css'>" +
                "<script src='" + BASE_URL + "js/leaflet.js'></script>" +
                "<style>html,body,#map{height:100%;margin:0;padding:0;background:#eef6ff;overflow:hidden;}" +
                ".leaflet-control-attribution,.leaflet-control-zoom{display:none!important;}" +
                ".leaflet-container{font-family:Arial,sans-serif;border-radius:14px;background:#eef6ff;}" +
                ".pin{font-size:34px;text-align:center;filter:drop-shadow(0 6px 6px rgba(0,0,0,.28));}" +
                ".assetpin{width:46px;height:58px;object-fit:contain;filter:drop-shadow(0 6px 6px rgba(0,0,0,.30));}" +
                ".bikepin{width:46px;height:46px;object-fit:contain;filter:drop-shadow(0 6px 6px rgba(0,0,0,.30));}" +
                ".placepin{width:42px;height:42px;object-fit:contain;filter:drop-shadow(0 6px 6px rgba(0,0,0,.30));}" +
                ".driverpin{width:42px;height:42px;object-fit:contain;border-radius:50%;filter:drop-shadow(0 6px 6px rgba(0,0,0,.30));}" +
                ".route{stroke-linecap:round;stroke-linejoin:round;}" +
                ".popup{min-width:170px;line-height:1.55;font-size:13px;color:#0f172a;}" +
                ".popup b{font-size:15px;color:#0B3A78;}" +
                "</style></head><body><div id='map'></div><script>" +
                "var map,pickup=null,delivery=null,route=null;" +
                "var placeMarkers=[],driverMarkers=[];" +
                "var pickupIconData='" + js(pickupIcon) + "',deliveryIconData='" + js(deliveryIcon) + "',bikeIconData='" + js(bikeIcon) + "',placeIconData='" + js(placeIcon) + "',driverIconData='" + js(driverIcon) + "';" +
                "function esc(v){return String(v||'').replace(/[&<>\"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;',\"'\":'&#39;'}[c];});}" +
                "function ready(){try{map=L.map('map',{zoomControl:false,attributionControl:false}).setView([" + centerLat + "," + centerLng + "],17);" +
                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:22,attribution:''}).addTo(map);" +
                "function notifyCenter(){var c=map.getCenter();try{AndroidBike.onCenterChanged(c.lat,c.lng,c.lat,c.lng);}catch(e){}}" +
                "map.on('moveend',notifyCenter);map.on('zoomend',notifyCenter);" +
                "setTimeout(function(){map.invalidateSize(true);var c=map.getCenter();try{AndroidBike.onMapReady(c.lat,c.lng,c.lat,c.lng);}catch(e){}},600);" +
                "}catch(e){setTimeout(ready,700);}}" +
                "function iconData(data,fallback){if(data&&data.length>20){return L.divIcon({html:'<img class=assetpin src=\"'+data+'\">',className:'',iconSize:[54,66],iconAnchor:[27,54],popupAnchor:[0,-52]});}return L.divIcon({html:'<div class=pin>'+fallback+'</div>',className:'',iconSize:[46,46],iconAnchor:[23,40],popupAnchor:[0,-38]});}" +
                "function placeData(){if(placeIconData&&placeIconData.length>20){return L.divIcon({html:'<img class=placepin src=\"'+placeIconData+'\">',className:'',iconSize:[42,42],iconAnchor:[21,42],popupAnchor:[0,-40]});}return L.divIcon({html:'<div class=pin>📍</div>',className:'',iconSize:[46,46],iconAnchor:[23,40],popupAnchor:[0,-38]});}" +
                "function driverData(){if(driverIconData&&driverIconData.length>20){return L.divIcon({html:'<img class=driverpin src=\"'+driverIconData+'\">',className:'',iconSize:[42,42],iconAnchor:[21,21],popupAnchor:[0,-24]});}return L.divIcon({html:'<div class=pin>🏍️</div>',className:'',iconSize:[46,46],iconAnchor:[23,28],popupAnchor:[0,-28]});}" +
                "function setPickup(lat,lng,label){lat=+lat;lng=+lng;if(!lat||!lng)return;if(pickup)pickup.setLatLng([lat,lng]);else pickup=L.marker([lat,lng],{icon:iconData(pickupIconData,'🟢'),zIndexOffset:700}).addTo(map);if(label)pickup.bindPopup('<div class=popup><b>Lokasi Jemput</b><br>'+esc(label)+'</div>');}" +
                "function setDelivery(lat,lng,label){lat=+lat;lng=+lng;if(!lat||!lng)return;if(delivery)delivery.setLatLng([lat,lng]);else delivery=L.marker([lat,lng],{icon:iconData(deliveryIconData,'🔴'),zIndexOffset:700}).addTo(map);if(label)delivery.bindPopup('<div class=popup><b>Lokasi Tujuan</b><br>'+esc(label)+'</div>');}" +
                "function moveTo(lat,lng,z){if(!map)return;map.setView([+lat,+lng],z||17,{animate:true});}" +
                "function clearPlaces(){for(var i=0;i<placeMarkers.length;i++){try{map.removeLayer(placeMarkers[i]);}catch(e){}}placeMarkers=[];}" +
                "function addPlace(lat,lng,name,type,address){if(!map||!lat||!lng)return;var html='<div class=popup><b>'+esc(name)+'</b><br>'+esc(type||'Transiva')+(address?'<br>'+esc(address):'')+'</div>';var m=L.marker([+lat,+lng],{icon:placeData(),zIndexOffset:350}).addTo(map).bindPopup(html);placeMarkers.push(m);}" +
                "function clearDrivers(){for(var i=0;i<driverMarkers.length;i++){try{map.removeLayer(driverMarkers[i]);}catch(e){}}driverMarkers=[];}" +
                "function addDriver(lat,lng,name){if(!map||!lat||!lng)return;var m=L.marker([+lat,+lng],{icon:driverData(),zIndexOffset:500}).addTo(map).bindPopup('<div class=popup><b>'+esc(name||'Driver')+'</b><br>Driver online</div>');driverMarkers.push(m);}" +
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
                loadMapPlaces();
                loadOnlineDrivers();
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
            pickupAddress = "Mencari alamat jemput...";

            pickupText.setText("Pickup: " + pickupAddress);
            pickupBtn.setText("●  Jemput\nMencari alamat...");
            eval("setPickup(" + pickupLat + "," + pickupLng + ",'" + js(pickupAddress) + "')");

            resolveAddressAsync(true, pickupLat, pickupLng);
            mode = "delivery";
        } else {
            deliveryLat = selectedLat;
            deliveryLng = selectedLng;
            deliveryAddress = "Mencari alamat tujuan...";

            deliveryText.setText("Tujuan: " + deliveryAddress);
            deliveryBtn.setText("●  Tujuan\nMencari alamat...");
            eval("setDelivery(" + deliveryLat + "," + deliveryLng + ",'" + js(deliveryAddress) + "')");

            resolveAddressAsync(false, deliveryLat, deliveryLng);
            requestPaymentQuote();
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
        if (link.length() == 0) {
            toastDialog("Masukkan link Google Maps tujuan terlebih dahulu.");
            return;
        }

        hideKeyboard();
        setLoading(true);

        new Thread(() -> {
            String finalLink = link;

            try {
                if (link.contains("maps.app.goo.gl") || link.contains("goo.gl/maps")) {
                    JSONObject res = postJson(RESOLVE_MAPS_URL, new JSONObject().put("url", link));
                    if (res.optBoolean("success", false) && res.optString("url", "").length() > 0) {
                        finalLink = res.optString("url");
                    }
                }

                double[] c = extractLatLng(finalLink);

                mainHandler.post(() -> {
                    setLoading(false);

                    if (c == null || !validCoord(c[0], c[1])) {
                        toastDialog("Link Google Maps tidak bisa dibaca.");
                        return;
                    }

                    deliveryLat = c[0];
                    deliveryLng = c[1];
                    deliveryAddress = "Mencari alamat tujuan...";

                    deliveryText.setText("Tujuan: " + deliveryAddress);
                    deliveryBtn.setText("●  Tujuan\nMencari alamat...");
                    googleMapInput.setText("");

                    eval("setDelivery(" + deliveryLat + "," + deliveryLng + ",'" + js(deliveryAddress) + "');moveTo(" + deliveryLat + "," + deliveryLng + ",17)");

                    resolveAddressAsync(false, deliveryLat, deliveryLng);
                    requestPaymentQuote();
                    mode = "delivery";
                    updateModeUI();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setLoading(false);
                    toastDialog("Gagal membaca link Google Maps.");
                });
            }
        }).start();
    }

    private double[] extractLatLng(String link) {
        try {
            String decoded = java.net.URLDecoder.decode(link, "UTF-8");
            String[] patterns = new String[]{
                    "[?&]q=(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)",
                    "@(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)",
                    "!3d(-?\\d+(?:\\.\\d+)?)!4d(-?\\d+(?:\\.\\d+)?)",
                    "(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)"
            };
            for (String p : patterns) {
                Matcher m = Pattern.compile(p).matcher(decoded);
                if (m.find()) return new double[]{Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2))};
            }
        } catch (Exception ignored) {}
        return null;
    }


    private void resolveAddressAsync(boolean isPickup, double lat, double lng) {
        new Thread(() -> {
            String address = buildSmartAddress(lat, lng);

            mainHandler.post(() -> {
                if (destroyed) return;

                if (isPickup) {
                    pickupAddress = address;
                    pickupText.setText("Pickup: " + address);
                    pickupBtn.setText("●  Jemput\n" + shortAddress(address));
                    eval("setPickup(" + pickupLat + "," + pickupLng + ",'" + js(address) + "')");
                } else {
                    deliveryAddress = address;
                    deliveryText.setText("Tujuan: " + address);
                    deliveryBtn.setText("●  Tujuan\n" + shortAddress(address));
                    eval("setDelivery(" + deliveryLat + "," + deliveryLng + ",'" + js(address) + "')");
                    requestPaymentQuote();
                }
            });
        }).start();
    }

    private String buildSmartAddress(double lat, double lng) {
        String nearName = findNearestPlaceName(lat, lng);
        String roadName = reverseAddress(lat, lng);

        if (nearName.length() > 0 && roadName.length() > 0) {
            return "Dekat " + nearName + ", " + roadName;
        }

        if (nearName.length() > 0) {
            return "Dekat " + nearName;
        }

        if (roadName.length() > 0) {
            return roadName;
        }

        return String.format(Locale.US, "%.6f, %.6f", lat, lng);
    }

    private String reverseAddress(double lat, double lng) {
        HttpURLConnection conn = null;

        try {
            String urlText =
                    "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat="
                            + lat + "&lon=" + lng + "&zoom=18&addressdetails=1";

            URL url = new URL(urlText);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(12000);
            conn.setUseCaches(false);
            conn.setRequestProperty("User-Agent", "TransivaAndroid/1.0");
            conn.setRequestProperty("Accept", "application/json");
            if (authToken != null && !authToken.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + authToken.trim());
            }
            conn.setRequestProperty("X-Device-UUID", DeviceIdentityManager.getInstallationUuid(this));

            String body = readStream(conn.getInputStream());
            JSONObject json = new JSONObject(body);
            JSONObject a = json.optJSONObject("address");

            if (a != null) {
                String road = firstNonEmpty(
                        a.optString("road", ""),
                        a.optString("pedestrian", ""),
                        a.optString("footway", ""),
                        a.optString("neighbourhood", "")
                );

                String area = firstNonEmpty(
                        a.optString("village", ""),
                        a.optString("suburb", ""),
                        a.optString("town", ""),
                        a.optString("city", ""),
                        a.optString("county", "")
                );

                if (road.length() > 0 && area.length() > 0) {
                    return road + ", " + area;
                }

                return firstNonEmpty(road, area, compactDisplayName(json.optString("display_name", "")));
            }

            return compactDisplayName(json.optString("display_name", ""));
        } catch (Exception ignored) {
            return "";
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String compactDisplayName(String value) {
        String v = firstNonEmpty(value, "");
        if (v.length() == 0) return "";

        String[] parts = v.split(",");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < parts.length && i < 3; i++) {
            String part = parts[i].trim();
            if (part.length() == 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(part);
        }

        return sb.length() > 0 ? sb.toString() : v;
    }

    private String findNearestPlaceName(double lat, double lng) {
        String food = findNearestFromUrl(GET_BUSINESSES_URL, "businesses", lat, lng, 150);
        if (food.length() > 0) return food;

        String laundry = findNearestFromUrl(GET_LAUNDRIES_URL, "laundries", lat, lng, 150);
        if (laundry.length() > 0) return laundry;

        return "";
    }

    private String findNearestFromUrl(String urlText, String arrayKey, double lat, double lng, double maxMeter) {
        try {
            JSONObject res = getJson(urlText + "?v=" + System.currentTimeMillis());

            if (!res.optBoolean("success", false)) return "";

            JSONArray arr = res.optJSONArray(arrayKey);
            if (arr == null) return "";

            String bestName = "";
            double bestDistance = maxMeter;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;

                double itemLat = item.optDouble("latitude", 0);
                double itemLng = item.optDouble("longitude", 0);

                if (!validCoord(itemLat, itemLng)) continue;

                double d = distanceMeter(lat, lng, itemLat, itemLng);

                if (d <= bestDistance) {
                    bestDistance = d;
                    bestName = firstNonEmpty(
                            item.optString("name", ""),
                            item.optString("business_name", ""),
                            item.optString("title", "")
                    );
                }
            }

            return bestName;
        } catch (Exception ignored) {
            return "";
        }
    }

    private void loadMapPlaces() {
        new Thread(() -> {
            try {
                JSONObject food = getJson(GET_BUSINESSES_URL + "?v=" + System.currentTimeMillis());
                JSONObject laundry = getJson(GET_LAUNDRIES_URL + "?v=" + System.currentTimeMillis());

                mainHandler.post(() -> {
                    if (destroyed || !mapReady) return;

                    eval("clearPlaces()");
                    drawPlaces(food.optJSONArray("businesses"), "TransFood");
                    drawPlaces(laundry.optJSONArray("laundries"), "TransLaundry");
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private void drawPlaces(JSONArray arr, String type) {
        if (arr == null) return;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) continue;

            double lat = item.optDouble("latitude", 0);
            double lng = item.optDouble("longitude", 0);

            if (!validCoord(lat, lng)) continue;

            String name = firstNonEmpty(
                    item.optString("name", ""),
                    item.optString("business_name", ""),
                    type
            );

            String address = firstNonEmpty(
                    item.optString("address", ""),
                    item.optString("category", ""),
                    ""
            );

            eval("addPlace(" + lat + "," + lng + ",'" + js(name) + "','" + js(type) + "','" + js(address) + "')");
        }
    }

    private void loadOnlineDrivers() {
        if (destroyed) return;

        new Thread(() -> {
            try {
                JSONObject res = getJson(GET_ONLINE_DRIVERS_URL + "?type=bike&v=" + System.currentTimeMillis());
                JSONArray arr = res.optJSONArray("drivers");

                mainHandler.post(() -> {
                    if (destroyed || !mapReady) return;

                    eval("clearDrivers()");

                    if (arr == null) return;

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject d = arr.optJSONObject(i);
                        if (d == null) continue;

                        double lat = d.optDouble("latitude", 0);
                        double lng = d.optDouble("longitude", 0);

                        if (!validCoord(lat, lng)) continue;

                        String name = firstNonEmpty(
                                d.optString("name", ""),
                                d.optString("username", ""),
                                "Driver"
                        );

                        eval("addDriver(" + lat + "," + lng + ",'" + js(name) + "')");
                    }
                });
            } catch (Exception ignored) {}
        }).start();

        mainHandler.postDelayed(() -> {
            if (!destroyed && mapReady && !isFinishing()) {
                loadOnlineDrivers();
            }
        }, 15000);
    }

    private JSONObject getJson(String urlText) throws Exception {
        HttpURLConnection conn = null;

        try {
            URL url = new URL(urlText);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept", "application/json");
            if (authToken != null && !authToken.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + authToken.trim());
            }
            conn.setRequestProperty("X-Device-UUID", DeviceIdentityManager.getInstallationUuid(this));

            String body = readStream(conn.getInputStream()).trim();
            return body.length() == 0 ? new JSONObject() : new JSONObject(body);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private double distanceMeter(double lat1, double lng1, double lat2, double lng2) {
        double earth = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLng / 2)
                        * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earth * c;
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
                pickup.put("address", firstNonEmpty(pickupAddress, "Lokasi Jemput"));

                JSONObject delivery = new JSONObject();
                delivery.put("latitude", deliveryLat);
                delivery.put("longitude", deliveryLng);
                delivery.put("address", firstNonEmpty(deliveryAddress, "Lokasi Tujuan"));

                JSONObject userLocation = new JSONObject();
                userLocation.put("latitude", pickupLat);
                userLocation.put("longitude", pickupLng);

                JSONObject payload = new JSONObject();
                payload.put("id", orderId);
                payload.put("user_id", userId);
                payload.put("username", username);
                payload.put("customer", username);
                payload.put("order_type", "TransRide");
                payload.put("driver_type", "bike");
                payload.put("service_type", "TransRide");
                payload.put("service_name", "TransRide");
                payload.put("price_mode", "server");
                payload.put("pickup", pickup);
                payload.put("delivery", delivery);
                payload.put("pickup_address", firstNonEmpty(pickupAddress, "Lokasi Jemput"));
                payload.put("delivery_address", firstNonEmpty(deliveryAddress, "Lokasi Tujuan"));
                payload.put("userLocation", userLocation);
                payload.put("note", noteInput.getText().toString().trim());
                payload.put("payment_method", paymentMethod);
                payload.put("voucher_code", voucherInput == null ? "" : voucherInput.getText().toString().trim().toUpperCase(Locale.US));

                JSONObject res = postJson(CREATE_ORDER_URL, payload);
                mainHandler.post(() -> handleOrderResult(res));
            } catch (Exception e) {
                mainHandler.post(() -> {
                    resetOrderButton();
                    toastDialog("Gagal membuat order TransRide. " + cleanError(e.getMessage()));
                });
            }
        }).start();
    }

    private void handleOrderResult(JSONObject res) {
        resetOrderButton();

        if (res == null || !res.optBoolean("success", false)) {
            String msg = res != null
                    ? res.optString("message", "Gagal membuat order motor.")
                    : "Gagal membuat order motor.";
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
                .putString("active_order_type", "TransRide")
                .putString("active_driver_type", "bike")
                .putString("active_service_name", "TransRide")
                .putString("pickup_lat", String.valueOf(pickupLat))
                .putString("pickup_lng", String.valueOf(pickupLng))
                .putString("delivery_lat", String.valueOf(deliveryLat))
                .putString("delivery_lng", String.valueOf(deliveryLng))
                .putString("pickup_address", firstNonEmpty(pickupAddress, "Lokasi Jemput"))
                .putString("delivery_address", firstNonEmpty(deliveryAddress, "Lokasi Tujuan"))
                .putString("active_order_price", res.optString("price", ""))
                .putString("active_order_payment_method", res.optString("payment_method", paymentMethod))
                .putString("active_order_voucher", res.optString("voucher_code", ""))
                .apply();

        try {
            Intent i = new Intent(this, SearchDriverActivity.class);
            i.putExtra("order_id", orderId);
            i.putExtra("active_order_id", orderId);
            i.putExtra("active_driver_type", "bike");
            i.putExtra("driver_type", "bike");
            i.putExtra("active_order_type", "TransRide");
            i.putExtra("pickup_lat", String.valueOf(pickupLat));
            i.putExtra("pickup_lng", String.valueOf(pickupLng));
            i.putExtra("delivery_lat", String.valueOf(deliveryLat));
            i.putExtra("delivery_lng", String.valueOf(deliveryLng));
            i.putExtra("pickup_address", firstNonEmpty(pickupAddress, "Lokasi Jemput"));
            i.putExtra("delivery_address", firstNonEmpty(deliveryAddress, "Lokasi Tujuan"));
            startActivity(i);
            finish();
        } catch (Exception e) {
            toastDialog("Order TransRide berhasil dibuat. ID: " + orderId);
        }
    }

    private void requestPaymentQuote() {
        if (!validCoordinate(pickupLat, pickupLng) || !validCoordinate(deliveryLat, deliveryLng)) {
            if (fareText != null) fareText.setText("Menunggu tujuan");
            if (distanceEstimateText != null) distanceEstimateText.setText("Pilih lokasi tujuan");
            if (originalFareText != null) originalFareText.setVisibility(View.GONE);
            if (finalFareText != null) finalFareText.setText("Rp0");
            if (paymentSummaryText != null) {
                paymentSummaryText.setText("Pilih titik jemput dan tujuan untuk menghitung ongkir");
            }
            return;
        }

        if (fareText != null) fareText.setText("Menghitung...");
        if (distanceEstimateText != null) distanceEstimateText.setText("Mengambil tarif database...");

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                JSONObject pickup = new JSONObject();
                pickup.put("latitude", pickupLat);
                pickup.put("longitude", pickupLng);
                JSONObject delivery = new JSONObject();
                delivery.put("latitude", deliveryLat);
                delivery.put("longitude", deliveryLng);
                payload.put("pickup", pickup);
                payload.put("delivery", delivery);

                String voucherCode = voucherInput == null
                        ? ""
                        : voucherInput.getText().toString().trim().toUpperCase(Locale.US);
                payload.put("voucher_code", voucherCode);

                JSONObject res = postJson(PAYMENT_QUOTE_URL, payload);
                mainHandler.post(() -> {
                    if (!res.optBoolean("success", false)) {
                        String message = res.optString("message", "Tarif tidak dapat dihitung");
                        fareText.setText("Gagal menghitung");
                        distanceEstimateText.setText(message);
                        originalFareText.setVisibility(View.GONE);
                        finalFareText.setText("Rp0");
                        paymentSummaryText.setText(message);
                        return;
                    }

                    int original = res.optInt("original_price", 0);
                    int discount = res.optInt("discount", 0);
                    int total = res.optInt("price", original);
                    int balance = res.optInt("balance", 0);
                    double distanceKm = res.optDouble("distance_km", 0.0);
                    String appliedVoucher = res.optString("voucher_code", "");
                    String voucherTitle = res.optString("voucher_title", "");
                    String label = paymentMethod.equals("balance") ? "Transiva Pay" : "Tunai";

                    fareText.setText(String.format(
                            new Locale("id", "ID"),
                            "%.1f km",
                            Math.max(0.0, distanceKm)
                    ));
                    distanceEstimateText.setText(
                            String.format(new Locale("id", "ID"), "Jarak estimasi %.1f km", Math.max(0.0, distanceKm))
                    );

                    if (discount > 0 && total < original) {
                        originalFareText.setText("Rp" + formatMoney(original));
                        originalFareText.setVisibility(View.VISIBLE);
                        finalFareText.setText("Rp" + formatMoney(total));
                        finalFareText.setTextColor(Color.parseColor("#16A34A"));

                        String voucherInfo = appliedVoucher.isEmpty() ? "Voucher aktif" : appliedVoucher;
                        if (!voucherTitle.isEmpty()) voucherInfo += " - " + voucherTitle;
                        paymentSummaryText.setText(
                                voucherInfo + " • Hemat Rp" + formatMoney(discount) + " • " + label
                        );
                    } else {
                        originalFareText.setVisibility(View.GONE);
                        finalFareText.setText("Rp" + formatMoney(total));
                        finalFareText.setTextColor(Color.parseColor("#0B7CFF"));

                        String info = "Estimasi dari database • " + label;
                        if (paymentMethod.equals("balance")) {
                            info += " • Saldo Rp" + formatMoney(balance);
                        }
                        paymentSummaryText.setText(info);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    fareText.setText("Gagal menghitung");
                    distanceEstimateText.setText("Periksa koneksi lalu coba lagi");
                    originalFareText.setVisibility(View.GONE);
                    finalFareText.setText("Rp0");
                    paymentSummaryText.setText("Gagal mengambil estimasi ongkir");
                });
            }
        }).start();
    }

    private String formatMoney(int value) {
        return String.format(new Locale("id", "ID"), "%,d", Math.max(0, value)).replace(',', '.');
    }

    private boolean validCoordinate(double latitude, double longitude) {
        return latitude >= -90.0 && latitude <= 90.0
                && longitude >= -180.0 && longitude <= 180.0
                && latitude != 0.0 && longitude != 0.0;
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
            if (authToken != null && !authToken.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + authToken.trim());
            }
            conn.setRequestProperty("X-Device-UUID", DeviceIdentityManager.getInstallationUuid(this));
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
    private void resetOrderButton() { ordering = false; setLoading(false); orderBtn.setEnabled(true); orderBtn.setText("🏍️ Order Motor"); }
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

    private String shortAddress(String value) {
        String v = firstNonEmpty(value, "");
        if (v.length() > 38) return v.substring(0, 38) + "...";
        return v;
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

    @Override protected void onDestroy() {
        destroyed = true;
        try {
            mainHandler.removeCallbacksAndMessages(null);
            if (mapView != null) {
                mapView.stopLoading();
                mapView.destroy();
                mapView = null;
            }
        } catch (Exception ignored) {}
        super.onDestroy();
    }
}
