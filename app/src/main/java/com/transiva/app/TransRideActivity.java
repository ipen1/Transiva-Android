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
import android.graphics.Paint;
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
    private FrameLayout centerMarkerBox;
    private TextView pickupText, deliveryText, modeText, fareText, paymentSummaryText;
    private TextView distanceInfoText, durationInfoText, originalPriceText, finalPriceText, discountInfoText;
    private Button voucherChoiceBtn, noteChoiceBtn, paymentChoiceBtn;
    private EditText googleMapInput, noteInput, voucherInput;
    private Button pickupBtn, deliveryBtn, gpsBtn, orderBtn, backBtn, useLinkBtn;
    private ProgressBar progressBar;

    private boolean mapReady = false;
    private boolean ordering = false;
    private String mode = "pickup";

    // Marker tersimpan di peta tetap ringkas.
    private static final int POINT_MARKER_BOX_WIDTH_DP = 42;
    private static final int POINT_MARKER_BOX_HEIGHT_DP = 54;
    private static final int POINT_MARKER_IMAGE_WIDTH_DP = 40;
    private static final int POINT_MARKER_IMAGE_HEIGHT_DP = 52;

    // Marker aktif di tengah peta dibuat lebih besar agar mudah diposisikan.
    // Ubah empat angka ini bila ingin memperbesar atau memperkecil marker center.
    private static final int CENTER_MARKER_BOX_WIDTH_DP = 48;
    private static final int CENTER_MARKER_BOX_HEIGHT_DP = 60;
    private static final int CENTER_MARKER_IMAGE_WIDTH_DP = 46;
    private static final int CENTER_MARKER_IMAGE_HEIGHT_DP = 58;
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
        backBtn = close;
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

        centerMarkerBox = new FrameLayout(this);
        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(
                dp(CENTER_MARKER_BOX_WIDTH_DP),
                dp(CENTER_MARKER_BOX_HEIGHT_DP)
        );
        centerLp.gravity = Gravity.CENTER;
        // Geser ke atas supaya ujung bawah pin tetap tepat di pusat peta.
        centerLp.topMargin = -dp(CENTER_MARKER_BOX_HEIGHT_DP / 2);
        mapBox.addView(centerMarkerBox, centerLp);
        updateCenterMarkerIcon();

        gpsBtn = smallButton("⌖", "#FFFFFF", "#0B7CFF", "#9DCAFF");
        gpsBtn.setTextSize(18);
        FrameLayout.LayoutParams gpsMapLp = new FrameLayout.LayoutParams(dp(42), dp(42));
        gpsMapLp.gravity = Gravity.BOTTOM | Gravity.START;
        gpsMapLp.setMargins(dp(10), 0, 0, dp(10));
        mapBox.addView(gpsBtn, gpsMapLp);

        mapView.loadDataWithBaseURL(BASE_URL, mapHtml(), "text/html", "UTF-8", null);

        /* =========================
         * DRAIV-STYLE BOTTOM CARD
         * ========================= */
        LinearLayout bottomCard = new LinearLayout(this);
        bottomCard.setOrientation(LinearLayout.VERTICAL);
        bottomCard.setPadding(dp(10), dp(9), dp(10), dp(10));
        bottomCard.setBackground(roundStroke("#F8FBFF", "#D7E6F8", dp(14), 1));
        LinearLayout.LayoutParams bottomLp = new LinearLayout.LayoutParams(-1, -2);
        bottomLp.setMargins(0, dp(6), 0, 0);
        root.addView(bottomCard, bottomLp);

        voucherInput = new EditText(this);
        voucherInput.setSingleLine(true);
        noteInput = new EditText(this);
        noteInput.setSingleLine(true);

        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        quickRow.setGravity(Gravity.CENTER_VERTICAL);
        bottomCard.addView(quickRow, new LinearLayout.LayoutParams(-1, dp(44)));

        voucherChoiceBtn = smallButton("🏷 Voucher", "#0B7CFF", "#FFFFFF", "#0B7CFF");
        noteChoiceBtn = smallButton("📝 Note", "#F59E0B", "#FFFFFF", "#F59E0B");
        paymentChoiceBtn = smallButton("💵 Tunai", "#FFFFFF", "#0B3A78", "#C8D9EC");
        voucherChoiceBtn.setTextSize(10);
        noteChoiceBtn.setTextSize(10);
        paymentChoiceBtn.setTextSize(10);

        quickRow.addView(voucherChoiceBtn, new LinearLayout.LayoutParams(0, -1, 0.95f));
        LinearLayout.LayoutParams noteQuickLp = new LinearLayout.LayoutParams(0, -1, 0.82f);
        noteQuickLp.setMargins(dp(6), 0, dp(6), 0);
        quickRow.addView(noteChoiceBtn, noteQuickLp);
        quickRow.addView(paymentChoiceBtn, new LinearLayout.LayoutParams(0, -1, 1.18f));

        voucherChoiceBtn.setOnClickListener(v -> showVoucherDialog());
        noteChoiceBtn.setOnClickListener(v -> showNoteDialog());
        paymentChoiceBtn.setOnClickListener(v -> showPaymentDialog());

        LinearLayout estimateRow = new LinearLayout(this);
        estimateRow.setOrientation(LinearLayout.HORIZONTAL);
        estimateRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams estimateLp = new LinearLayout.LayoutParams(-1, dp(66));
        estimateLp.setMargins(dp(2), dp(6), dp(2), dp(5));
        bottomCard.addView(estimateRow, estimateLp);

        LinearLayout tripInfo = new LinearLayout(this);
        tripInfo.setOrientation(LinearLayout.VERTICAL);
        tripInfo.setGravity(Gravity.CENTER_VERTICAL);
        estimateRow.addView(tripInfo, new LinearLayout.LayoutParams(0, -1, 1));

        distanceInfoText = text("⌁  Jarak : -", 11, "#334155", false);
        durationInfoText = text("◷  Waktu : -", 11, "#334155", false);
        tripInfo.addView(distanceInfoText, new LinearLayout.LayoutParams(-1, dp(28)));
        tripInfo.addView(durationInfoText, new LinearLayout.LayoutParams(-1, dp(28)));

        LinearLayout priceBox = new LinearLayout(this);
        priceBox.setOrientation(LinearLayout.VERTICAL);
        priceBox.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        estimateRow.addView(priceBox, new LinearLayout.LayoutParams(0, -1, 1));

        originalPriceText = text("", 10, "#94A3B8", false);
        originalPriceText.setGravity(Gravity.END);
        originalPriceText.setVisibility(View.GONE);
        priceBox.addView(originalPriceText, new LinearLayout.LayoutParams(-1, dp(20)));

        finalPriceText = text("Rp -", 18, "#0B3A78", true);
        finalPriceText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        priceBox.addView(finalPriceText, new LinearLayout.LayoutParams(-1, dp(30)));

        discountInfoText = text("", 9, "#16A34A", true);
        discountInfoText.setGravity(Gravity.END);
        discountInfoText.setVisibility(View.GONE);
        priceBox.addView(discountInfoText, new LinearLayout.LayoutParams(-1, dp(16)));

        fareText = text("Tarif dihitung dari database", 8, "#64748B", false);
        fareText.setVisibility(View.GONE);
        paymentSummaryText = text("", 8, "#64748B", false);
        paymentSummaryText.setVisibility(View.GONE);

        orderBtn = smallButton("🏍  PESAN SEKARANG", "#0B7CFF", "#FFFFFF", "#0B7CFF");
        orderBtn.setTextSize(13);
        orderBtn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bottomCard.addView(orderBtn, new LinearLayout.LayoutParams(-1, dp(48)));

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressLp = new FrameLayout.LayoutParams(dp(50), dp(50));
        progressLp.gravity = Gravity.CENTER;
        page.addView(progressBar, progressLp);

        setContentView(page);
        bindActions();
        updateModeUI();
    }

    private void showVoucherDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(voucherInput == null ? "" : voucherInput.getText().toString());
        input.setHint("Masukkan kode voucher");
        input.setPadding(dp(14), dp(8), dp(14), dp(8));

        new AlertDialog.Builder(this)
                .setTitle("Voucher TransRide")
                .setMessage("Masukkan kode voucher lalu tekan Gunakan.")
                .setView(input)
                .setNegativeButton("Hapus", (dialog, which) -> {
                    if (voucherInput != null) voucherInput.setText("");
                    voucherChoiceBtn.setText("🏷 Voucher");
                    requestPaymentQuote();
                })
                .setNeutralButton("Batal", null)
                .setPositiveButton("Gunakan", (dialog, which) -> {
                    String code = input.getText().toString().trim().toUpperCase(Locale.US);
                    if (voucherInput != null) voucherInput.setText(code);
                    voucherChoiceBtn.setText(code.isEmpty() ? "🏷 Voucher" : "🏷 " + code);
                    requestPaymentQuote();
                })
                .show();
    }

    private void showNoteDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setMaxLines(5);
        input.setText(noteInput == null ? "" : noteInput.getText().toString());
        input.setHint("Contoh: jemput di depan pagar");
        input.setPadding(dp(14), dp(8), dp(14), dp(8));

        new AlertDialog.Builder(this)
                .setTitle("Catatan untuk driver")
                .setView(input)
                .setNegativeButton("Hapus", (dialog, which) -> {
                    if (noteInput != null) noteInput.setText("");
                    noteChoiceBtn.setText("📝 Note");
                })
                .setNeutralButton("Batal", null)
                .setPositiveButton("Simpan", (dialog, which) -> {
                    String note = input.getText().toString().trim();
                    if (noteInput != null) noteInput.setText(note);
                    noteChoiceBtn.setText(note.isEmpty() ? "📝 Note" : "📝 Ada Note");
                })
                .show();
    }

    private void showPaymentDialog() {
        String[] methods = {"Tunai", "Transiva Pay"};
        int checked = paymentMethod.equals("balance") ? 1 : 0;

        new AlertDialog.Builder(this)
                .setTitle("Pilih metode pembayaran")
                .setSingleChoiceItems(methods, checked, (dialog, which) -> {
                    paymentMethod = which == 1 ? "balance" : "cash";
                    paymentChoiceBtn.setText(which == 1 ? "💳 Transiva Pay" : "💵 Tunai");
                    dialog.dismiss();
                    requestPaymentQuote();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void bindActions() {
        pickupBtn.setOnClickListener(v -> handlePointButtonClick("pickup"));
        deliveryBtn.setOnClickListener(v -> handlePointButtonClick("delivery"));
        gpsBtn.setOnClickListener(v -> goToMyLocation());
        backBtn.setOnClickListener(v -> finish());
        orderBtn.setOnClickListener(v -> createOrder());
        useLinkBtn.setOnClickListener(v -> useGoogleMapLink());
    }

    /**
     * Klik pertama pada tombol yang tidak aktif hanya mengganti mode marker center.
     * Klik kedua pada tombol yang sudah aktif menetapkan titik di posisi tengah peta.
     * Dengan pola ini pengguna selalu dapat kembali memilih ulang Jemput atau Tujuan.
     */
    private void handlePointButtonClick(String requestedMode) {
        if (!requestedMode.equals(mode)) {
            mode = requestedMode;
            updateModeUI();
            return;
        }

        setPointFromCenter();
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
                ".pin{font-size:25px;text-align:center;filter:drop-shadow(0 4px 4px rgba(0,0,0,.24));}" +
                ".assetpin{display:block;width:46px;height:58px;object-fit:contain;filter:drop-shadow(0 4px 4px rgba(0,0,0,.26));}" +
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
                "function iconData(data,fallback){if(data&&data.length>20){return L.divIcon({html:'<img class=assetpin src=\"'+data+'\">',className:'',iconSize:[48,60],iconAnchor:[24,58],popupAnchor:[0,-56]});}return L.divIcon({html:'<div class=pin>'+fallback+'</div>',className:'',iconSize:[48,60],iconAnchor:[24,58],popupAnchor:[0,-56]});}" +
                "function placeData(){if(placeIconData&&placeIconData.length>20){return L.divIcon({html:'<img class=placepin src=\"'+placeIconData+'\">',className:'',iconSize:[42,42],iconAnchor:[21,42],popupAnchor:[0,-40]});}return L.divIcon({html:'<div class=pin>📍</div>',className:'',iconSize:[48,60],iconAnchor:[24,58],popupAnchor:[0,-56]});}" +
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

                // Jangan otomatis menetapkan titik saat halaman baru dibuka.
                // Marker center harus tetap pada mode Jemput sampai pengguna menekan Jemput.
                if (!validCoord(pickupLat, pickupLng)) {
                    mode = "pickup";
                    updateModeUI();
                }
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
                }

                // Quote harus dipanggil setelah koordinat kedua titik sudah tersimpan.
                if (validCoordinate(pickupLat, pickupLng)
                        && validCoordinate(deliveryLat, deliveryLng)) {
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
        if (!validCoordinate(pickupLat, pickupLng)
                || !validCoordinate(deliveryLat, deliveryLng)) {
            if (paymentSummaryText != null) {
                paymentSummaryText.setText("Pilih titik jemput dan tujuan");
            }
            if (distanceInfoText != null) distanceInfoText.setText("⌁  Jarak : -");
            if (durationInfoText != null) durationInfoText.setText("◷  Waktu : -");
            if (finalPriceText != null) finalPriceText.setText("Rp -");
            if (originalPriceText != null) originalPriceText.setVisibility(View.GONE);
            if (discountInfoText != null) discountInfoText.setVisibility(View.GONE);
            return;
        }

        final double fallbackKm = Math.max(
                0.1,
                distanceMeter(
                        pickupLat,
                        pickupLng,
                        deliveryLat,
                        deliveryLng
                ) / 1000.0
        );

        // Tampilkan estimasi jarak/waktu segera, lalu harga diisi dari database.
        final double fallbackMinutes = Math.max(
                1.0,
                (fallbackKm / 25.0) * 60.0
        );

        distanceInfoText.setText(String.format(
                new Locale("id", "ID"),
                "⌁  Jarak : %.1f km",
                fallbackKm
        ));
        durationInfoText.setText(String.format(
                new Locale("id", "ID"),
                "◷  Waktu : %.0f menit",
                fallbackMinutes
        ));
        finalPriceText.setText("Menghitung...");
        finalPriceText.setTextColor(Color.parseColor("#64748B"));
        paymentSummaryText.setText("Mengambil tarif dari database...");

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

                // Field datar ditambahkan untuk kompatibilitas endpoint lama/hosting cache.
                payload.put("pickup_lat", pickupLat);
                payload.put("pickup_lng", pickupLng);
                payload.put("delivery_lat", deliveryLat);
                payload.put("delivery_lng", deliveryLng);
                payload.put("service_type", "Transbike");
                payload.put("payment_method", paymentMethod);
                payload.put(
                        "voucher_code",
                        voucherInput == null
                                ? ""
                                : voucherInput.getText().toString()
                                .trim()
                                .toUpperCase(Locale.US)
                );

                JSONObject res = postJson(PAYMENT_QUOTE_URL, payload);

                mainHandler.post(() -> {
                    if (destroyed) return;

                    if (!res.optBoolean("success", false)) {
                        String message = firstNonEmpty(
                                res.optString("message", ""),
                                "Tarif belum dapat dihitung"
                        );
                        paymentSummaryText.setText(message);
                        finalPriceText.setText("Rp -");
                        finalPriceText.setTextColor(Color.parseColor("#0B3A78"));
                        return;
                    }

                    int original = jsonInt(
                            res,
                            "original_price",
                            jsonInt(res, "standard_price", 0)
                    );
                    int discount = jsonInt(res, "discount", 0);
                    int total = jsonInt(
                            res,
                            "price",
                            jsonInt(res, "final_price", original)
                    );
                    int balance = jsonInt(res, "balance", 0);

                    double distanceKm = jsonDouble(
                            res,
                            "distance_km",
                            fallbackKm
                    );
                    double durationMinutes = jsonDouble(
                            res,
                            "duration_minutes",
                            jsonDouble(res, "estimated_minutes", fallbackMinutes)
                    );

                    distanceKm = Math.max(0.1, distanceKm);
                    durationMinutes = Math.max(1.0, durationMinutes);

                    distanceInfoText.setText(String.format(
                            new Locale("id", "ID"),
                            "⌁  Jarak : %.1f km",
                            distanceKm
                    ));
                    durationInfoText.setText(String.format(
                            new Locale("id", "ID"),
                            "◷  Waktu : %.0f menit",
                            durationMinutes
                    ));

                    if (total <= 0) {
                        paymentSummaryText.setText("Tarif database tidak valid");
                        finalPriceText.setText("Rp -");
                        finalPriceText.setTextColor(Color.parseColor("#0B3A78"));
                        return;
                    }

                    finalPriceText.setText("Rp " + formatMoney(total));

                    if (discount > 0 && original > total) {
                        originalPriceText.setVisibility(View.VISIBLE);
                        originalPriceText.setText("Rp " + formatMoney(original));
                        originalPriceText.setPaintFlags(
                                originalPriceText.getPaintFlags()
                                        | Paint.STRIKE_THRU_TEXT_FLAG
                        );
                        finalPriceText.setTextColor(Color.parseColor("#16A34A"));
                        discountInfoText.setVisibility(View.VISIBLE);
                        discountInfoText.setText(
                                "Hemat Rp " + formatMoney(discount)
                        );
                    } else {
                        originalPriceText.setVisibility(View.GONE);
                        originalPriceText.setPaintFlags(
                                originalPriceText.getPaintFlags()
                                        & ~Paint.STRIKE_THRU_TEXT_FLAG
                        );
                        finalPriceText.setTextColor(Color.parseColor("#0B3A78"));
                        discountInfoText.setVisibility(View.GONE);
                    }

                    String label = paymentMethod.equals("balance")
                            ? "Transiva Pay"
                            : "Tunai";
                    paymentChoiceBtn.setText(
                            paymentMethod.equals("balance")
                                    ? "💳 Transiva Pay"
                                    : "💵 Tunai"
                    );

                    String info = label;
                    if (paymentMethod.equals("balance")) {
                        info += " • Saldo Rp" + formatMoney(balance);
                    }

                    fareText.setText("Tarif database: Rp" + formatMoney(total));
                    paymentSummaryText.setText(info);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (destroyed) return;
                    paymentSummaryText.setText("Gagal terhubung ke server tarif");
                    finalPriceText.setText("Rp -");
                    finalPriceText.setTextColor(Color.parseColor("#0B3A78"));
                });
            }
        }).start();
    }

    private int jsonInt(JSONObject json, String key, int fallback) {
        if (json == null || key == null) return fallback;
        Object value = json.opt(key);
        if (value == null || value == JSONObject.NULL) return fallback;
        try {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            String clean = String.valueOf(value)
                    .replace("Rp", "")
                    .replace(".", "")
                    .replace(",", "")
                    .trim();
            return clean.isEmpty() ? fallback : (int) Math.round(Double.parseDouble(clean));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private double jsonDouble(JSONObject json, String key, double fallback) {
        if (json == null || key == null) return fallback;
        Object value = json.opt(key);
        if (value == null || value == JSONObject.NULL) return fallback;
        try {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            String clean = String.valueOf(value)
                    .replace(",", ".")
                    .trim();
            return clean.isEmpty() ? fallback : Double.parseDouble(clean);
        } catch (Exception ignored) {
            return fallback;
        }
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
        boolean pickupMode = "pickup".equals(mode);

        modeText.setText(
                pickupMode
                        ? "Geser peta lalu tekan Jemput"
                        : "Geser peta lalu tekan Tujuan"
        );

        pickupBtn.setAlpha(pickupMode ? 1f : .80f);
        deliveryBtn.setAlpha(pickupMode ? .80f : 1f);

        updateCenterMarkerIcon();
    }

    /**
     * Tidak memakai marker center generik lagi.
     * Mode pickup  -> ikon jemput berada tepat di tengah peta.
     * Mode delivery -> ikon tujuan berada tepat di tengah peta.
     */
    private void updateCenterMarkerIcon() {
        if (centerMarkerBox == null) {
            return;
        }

        centerMarkerBox.removeAllViews();

        boolean pickupMode = "pickup".equals(mode);

        int markerId = pickupMode
                ? getDrawableId(
                        "map_pickup_pin",
                        "ic_pickup_pin",
                        "pickup_pin",
                        "pickup",
                        "point_pickup",
                        "ic_pickup"
                )
                : getDrawableId(
                        "map_destination_pin",
                        "map_delivery_pin",
                        "ic_delivery_pin",
                        "delivery_pin",
                        "delivery",
                        "point_delivery",
                        "ic_delivery"
                );

        if (markerId > 0) {
            ImageView marker = new ImageView(this);
            marker.setImageResource(markerId);
            marker.setScaleType(ImageView.ScaleType.FIT_CENTER);
            marker.setContentDescription(
                    pickupMode ? "Titik jemput" : "Titik tujuan"
            );
            FrameLayout.LayoutParams markerLp = new FrameLayout.LayoutParams(
                    dp(CENTER_MARKER_IMAGE_WIDTH_DP),
                    dp(CENTER_MARKER_IMAGE_HEIGHT_DP)
            );
            markerLp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;

            centerMarkerBox.addView(marker, markerLp);
            return;
        }

        TextView fallback = text(
                pickupMode ? "●" : "●",
                24,
                pickupMode ? "#16A34A" : "#EF4444",
                true
        );
        fallback.setGravity(Gravity.CENTER);
        fallback.setContentDescription(
                pickupMode ? "Titik jemput" : "Titik tujuan"
        );
        centerMarkerBox.addView(
                fallback,
                new FrameLayout.LayoutParams(-1, -1)
        );
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
