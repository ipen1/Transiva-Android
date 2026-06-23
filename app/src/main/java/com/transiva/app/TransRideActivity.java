package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
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
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransRideActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String CREATE_ORDER_URL = BASE_URL + "server/createOrder.php";
    private static final String RESOLVE_MAP_URL = BASE_URL + "server/resolve_google_maps.php";
    private static final int TIMEOUT_MS = 25000;
    private static final int REQ_LOCATION = 2201;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WebView mapView;
    private TextView pickupText;
    private TextView deliveryText;
    private TextView estimateText;
    private TextView modeBadge;
    private TextView centerMarker;
    private EditText googleLinkInput;
    private EditText noteInput;
    private Button pickupModeBtn;
    private Button deliveryModeBtn;
    private Button linkBtn;
    private Button gpsBtn;
    private Button orderBtn;
    private ProgressBar progressBar;

    private LocationManager locationManager;
    private boolean loading = false;
    private boolean mapReady = false;
    private boolean suppressBridgeOnce = false;
    private String pickingMode = "pickup";

    private double userLat = 0;
    private double userLng = 0;
    private double pickupLat = 0;
    private double pickupLng = 0;
    private double deliveryLat = 0;
    private double deliveryLng = 0;
    private String pickupAddress = "";
    private String deliveryAddress = "";

    private int userId = 0;
    private String username = "User";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setStatusBarColor(Color.parseColor("#071426"));
            getWindow().setNavigationBarColor(Color.parseColor("#071426"));
        } catch (Exception ignored) {}

        loadSession();
        buildLayout();
        ensureLocationPermissionAndGps();
    }

    private void loadSession() {
        try {
            SessionManager session = new SessionManager(this);
            username = firstNonEmpty(session.getUsername(), session.getName(), "User");
            userId = Integer.parseInt(firstNonEmpty(session.getId(), session.getUserId(), "0"));
        } catch (Exception ignored) {
            userId = 0;
        }
    }

    private void buildLayout() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F3F8FF"));

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
        mapView.addJavascriptInterface(new MapBridge(), "AndroidTransRide");
        page.addView(mapView, new FrameLayout.LayoutParams(-1, -1));
        mapView.loadDataWithBaseURL("https://transiva.my.id/", mapHtml(), "text/html", "UTF-8", null);

        buildTopPanel(page);
        buildCenterMarker(page);
        buildBottomPanel(page);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams pLp = new FrameLayout.LayoutParams(dp(54), dp(54));
        pLp.gravity = Gravity.CENTER;
        page.addView(progressBar, pLp);

        setContentView(page);
    }

    private void buildTopPanel(FrameLayout page) {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(14), dp(12), dp(14), dp(12));
        top.setBackground(roundStroke("#FAFCFF", "#D7E6F8", dp(24), 1));
        top.setElevation(dp(6));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(head, new LinearLayout.LayoutParams(-1, -2));

        TextView title = text("TransRide", 18, "#0B3A78", true);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        modeBadge = text("Geser peta: Jemput", 11, "#0B7CFF", true);
        modeBadge.setGravity(Gravity.CENTER);
        modeBadge.setPadding(dp(10), dp(6), dp(10), dp(6));
        modeBadge.setBackground(round("#EAF4FF", dp(16)));
        head.addView(modeBadge, new LinearLayout.LayoutParams(-2, -2));

        Button close = smallButton("×", "#FEE2E2", "#DC2626", "#FECACA");
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        closeLp.setMargins(dp(8), 0, 0, 0);
        head.addView(close, closeLp);
        close.setOnClickListener(v -> finish());

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(-1, dp(44));
        modeLp.setMargins(0, dp(10), 0, dp(10));
        top.addView(modeRow, modeLp);

        pickupModeBtn = smallButton("Jemput", "#0B7CFF", "#FFFFFF", "#0B7CFF");
        deliveryModeBtn = smallButton("Tujuan", "#FFFFFF", "#0B7CFF", "#9DCAFF");
        modeRow.addView(pickupModeBtn, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, -1, 1);
        dlp.setMargins(dp(8), 0, 0, 0);
        modeRow.addView(deliveryModeBtn, dlp);
        pickupModeBtn.setOnClickListener(v -> setMode("pickup"));
        deliveryModeBtn.setOnClickListener(v -> setMode("delivery"));

        pickupText = compactLocationText("👤 Lokasi Jemput", "Geser peta atau tekan GPS");
        top.addView(pickupText);
        deliveryText = compactLocationText("📍 Lokasi Tujuan", "Belum dipilih");
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(-1, -2);
        delLp.setMargins(0, dp(6), 0, 0);
        top.addView(deliveryText, delLp);

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-1, -2);
        topLp.gravity = Gravity.TOP;
        topLp.setMargins(dp(14), dp(18), dp(14), 0);
        page.addView(top, topLp);
    }

    private TextView compactLocationText(String title, String value) {
        TextView tv = text(title + "\n" + value, 12, "#0B3A78", true);
        tv.setMaxLines(3);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        tv.setBackground(roundStroke("#FFFFFF", "#E2E8F0", dp(16), 1));
        return tv;
    }

    private void buildCenterMarker(FrameLayout page) {
        centerMarker = text("📍", 42, "#EF4444", true);
        centerMarker.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(72), dp(72));
        lp.gravity = Gravity.CENTER;
        lp.topMargin = -dp(36);
        page.addView(centerMarker, lp);
    }

    private void buildBottomPanel(FrameLayout page) {
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(14), dp(14), dp(14), dp(14));
        bottom.setBackground(roundStroke("#FAFCFF", "#D7E6F8", dp(24), 1));
        bottom.setElevation(dp(8));

        LinearLayout linkRow = new LinearLayout(this);
        linkRow.setOrientation(LinearLayout.HORIZONTAL);
        linkRow.setGravity(Gravity.CENTER_VERTICAL);
        bottom.addView(linkRow, new LinearLayout.LayoutParams(-1, dp(48)));

        googleLinkInput = new EditText(this);
        googleLinkInput.setSingleLine(true);
        googleLinkInput.setTextSize(13);
        googleLinkInput.setHint("Paste link Google Maps tujuan...");
        googleLinkInput.setHintTextColor(Color.parseColor("#94A3B8"));
        googleLinkInput.setTextColor(Color.parseColor("#0F172A"));
        googleLinkInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        googleLinkInput.setPadding(dp(14), 0, dp(14), 0);
        googleLinkInput.setBackground(roundStroke("#F8FBFF", "#D8E4F2", dp(16), 1));
        linkRow.addView(googleLinkInput, new LinearLayout.LayoutParams(0, -1, 1));

        linkBtn = smallButton("Pakai", "#0B7CFF", "#FFFFFF", "#0B7CFF");
        LinearLayout.LayoutParams linkLp = new LinearLayout.LayoutParams(dp(76), -1);
        linkLp.setMargins(dp(8), 0, 0, 0);
        linkRow.addView(linkBtn, linkLp);
        linkBtn.setOnClickListener(v -> useGoogleMapsLink());

        noteInput = new EditText(this);
        noteInput.setSingleLine(true);
        noteInput.setTextSize(13);
        noteInput.setHint("Pesan untuk kurir...");
        noteInput.setHintTextColor(Color.parseColor("#94A3B8"));
        noteInput.setTextColor(Color.parseColor("#0F172A"));
        noteInput.setPadding(dp(14), 0, dp(14), 0);
        noteInput.setBackground(roundStroke("#F8FBFF", "#D8E4F2", dp(16), 1));
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, dp(48));
        noteLp.setMargins(0, dp(10), 0, 0);
        bottom.addView(noteInput, noteLp);

        estimateText = text("Geser peta untuk memilih jemput/tujuan", 12, "#64748B", false);
        estimateText.setPadding(dp(10), dp(8), dp(10), dp(2));
        bottom.addView(estimateText, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(-1, dp(52));
        actLp.setMargins(0, dp(8), 0, 0);
        bottom.addView(actions, actLp);

        Button back = outlineButton("Kembali");
        gpsBtn = outlineButton("◎ GPS");
        orderBtn = primaryButton("Order");
        actions.addView(back, new LinearLayout.LayoutParams(0, -1, 0.95f));
        LinearLayout.LayoutParams gpsLp = new LinearLayout.LayoutParams(0, -1, 0.85f);
        gpsLp.setMargins(dp(8), 0, 0, 0);
        actions.addView(gpsBtn, gpsLp);
        LinearLayout.LayoutParams orderLp = new LinearLayout.LayoutParams(0, -1, 1.05f);
        orderLp.setMargins(dp(8), 0, 0, 0);
        actions.addView(orderBtn, orderLp);

        back.setOnClickListener(v -> finish());
        gpsBtn.setOnClickListener(v -> moveGpsToCurrentMode());
        orderBtn.setOnClickListener(v -> confirmAndCreateOrder());

        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(-1, -2);
        bottomLp.gravity = Gravity.BOTTOM;
        bottomLp.setMargins(dp(14), 0, dp(14), dp(14));
        page.addView(bottom, bottomLp);
    }

    private String mapHtml() {
        return "<!DOCTYPE html>" +
                "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>" +
                "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'>" +
                "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>" +
                "<style>html,body,#map{height:100%;margin:0;padding:0;background:#eef6ff;}" +
                ".leaflet-control-attribution{font-size:9px}.leaflet-control-zoom{margin-top:190px!important}.pin{font-size:27px;text-align:center;filter:drop-shadow(0 4px 4px rgba(0,0,0,.25));}.leaflet-popup-content{font-family:Arial;font-weight:700;color:#0B3A78;}</style>" +
                "</head><body><div id='map'></div><script>" +
                "var map=L.map('map',{zoomControl:true,attributionControl:true}).setView([-0.7765,120.1329],16);" +
                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:20,attribution:'OSM'}).addTo(map);" +
                "var pickup=null,delivery=null,line=null,mode='pickup',timer=null,ready=false;" +
                "function ic(t){return L.divIcon({html:'<div class=pin>'+t+'</div>',className:'',iconSize:[32,32],iconAnchor:[16,30]});}" +
                "function setMode(m){mode=m;}" +
                "function setCenter(lat,lng){map.setView([parseFloat(lat),parseFloat(lng)],17);sendCenter();}" +
                "function setPoint(type,lat,lng,label){lat=parseFloat(lat);lng=parseFloat(lng);var mk=(type==='pickup')?pickup:delivery;if(mk){map.removeLayer(mk);}mk=L.marker([lat,lng],{icon:ic(type==='pickup'?'👤':'📍')}).addTo(map).bindPopup(label||'Lokasi');if(type==='pickup'){pickup=mk;}else{delivery=mk;}draw();}" +
                "function draw(){if(line){map.removeLayer(line);line=null;}if(pickup&&delivery){var a=pickup.getLatLng(),b=delivery.getLatLng();line=L.polyline([a,b],{weight:5,opacity:.85,color:'#0B7CFF'}).addTo(map);}}" +
                "function sendCenter(){if(!ready)return;var c=map.getCenter();try{AndroidTransRide.onCenterChanged(mode,c.lat,c.lng);}catch(e){}}" +
                "map.on('moveend',function(){clearTimeout(timer);timer=setTimeout(sendCenter,250);});" +
                "setTimeout(function(){ready=true;map.invalidateSize();sendCenter();},900);" +
                "</script></body></html>";
    }

    public class MapBridge {
        @JavascriptInterface
        public void onCenterChanged(String mode, double lat, double lng) {
            if (suppressBridgeOnce) {
                suppressBridgeOnce = false;
                return;
            }
            applyPickedPoint(mode, lat, lng, true);
        }
    }

    private void setMode(String mode) {
        pickingMode = "delivery".equals(mode) ? "delivery" : "pickup";
        if (mapView != null) {
            mapView.evaluateJavascript("setMode('" + pickingMode + "')", null);
        }
        updateModeUI();
    }

    private void updateModeUI() {
        if ("delivery".equals(pickingMode)) {
            pickupModeBtn.setTextColor(Color.parseColor("#0B7CFF"));
            pickupModeBtn.setBackground(roundStroke("#FFFFFF", "#9DCAFF", dp(16), 1));
            deliveryModeBtn.setTextColor(Color.WHITE);
            deliveryModeBtn.setBackground(roundGradient("#086BFF", "#2EA2FF", dp(16)));
            modeBadge.setText("Geser peta: Tujuan");
            centerMarker.setText("📍");
        } else {
            pickupModeBtn.setTextColor(Color.WHITE);
            pickupModeBtn.setBackground(roundGradient("#086BFF", "#2EA2FF", dp(16)));
            deliveryModeBtn.setTextColor(Color.parseColor("#0B7CFF"));
            deliveryModeBtn.setBackground(roundStroke("#FFFFFF", "#9DCAFF", dp(16), 1));
            modeBadge.setText("Geser peta: Jemput");
            centerMarker.setText("👤");
        }
    }

    private void applyPickedPoint(String mode, double lat, double lng, boolean fromMapMove) {
        if (lat == 0 || lng == 0) return;
        String cleanMode = "delivery".equals(mode) ? "delivery" : "pickup";
        if ("delivery".equals(cleanMode)) {
            deliveryLat = lat;
            deliveryLng = lng;
            deliveryAddress = "Mengambil alamat...";
        } else {
            pickupLat = lat;
            pickupLng = lng;
            pickupAddress = "Mengambil alamat...";
        }
        updateLocationTexts();

        new Thread(() -> {
            String addr = getAddress(lat, lng);
            mainHandler.post(() -> {
                if ("delivery".equals(cleanMode)) {
                    deliveryAddress = addr;
                } else {
                    pickupAddress = addr;
                }
                updateLocationTexts();
                updateMapPoint(cleanMode, lat, lng, shortAddr(addr));
            });
        }).start();
    }

    private void updateMapPoint(String type, double lat, double lng, String label) {
        if (mapView == null || lat == 0 || lng == 0) return;
        String js = "setPoint('" + type + "'," + lat + "," + lng + ",'" + escapeJs(label) + "')";
        mainHandler.post(() -> {
            try { mapView.evaluateJavascript(js, null); } catch (Exception ignored) {}
        });
    }

    private void centerMap(double lat, double lng) {
        if (mapView == null || lat == 0 || lng == 0) return;
        suppressBridgeOnce = true;
        mapView.evaluateJavascript("setCenter(" + lat + "," + lng + ")", null);
    }

    private void ensureLocationPermissionAndGps() {
        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        loadGpsToPickup();
    }

    private void loadGpsToPickup() {
        Location last = getBestLastLocation();
        if (last != null) {
            userLat = last.getLatitude();
            userLng = last.getLongitude();
            centerMap(userLat, userLng);
            applyPickedPoint("pickup", userLat, userLng, false);
        }
        requestFreshLocation("pickup");
    }

    private void moveGpsToCurrentMode() {
        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        requestFreshLocation(pickingMode);
    }

    private Location getBestLastLocation() {
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) return null;
            boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            if (!gps && !network) {
                showGpsDialog();
                return null;
            }
            Location last = null;
            try { last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
            if (last == null) {
                try { last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); } catch (Exception ignored) {}
            }
            return last;
        } catch (Exception e) {
            return null;
        }
    }

    private void requestFreshLocation(String mode) {
        setLoading(true, "GPS...");
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) {
                setLoading(false, null);
                return;
            }
            boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            if (!gps && !network) {
                setLoading(false, null);
                showGpsDialog();
                return;
            }
            String provider = gps ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            locationManager.requestSingleUpdate(provider, new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    setLoading(false, null);
                    if (location == null) return;
                    userLat = location.getLatitude();
                    userLng = location.getLongitude();
                    setMode(mode);
                    centerMap(userLat, userLng);
                    applyPickedPoint(mode, userLat, userLng, false);
                }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            }, Looper.getMainLooper());
            mainHandler.postDelayed(() -> setLoading(false, null), 7000);
        } catch (Exception e) {
            setLoading(false, null);
            showInfo("GPS", "Gagal mengambil lokasi GPS.");
        }
    }

    private void useGoogleMapsLink() {
        String link = googleLinkInput.getText().toString().trim();
        if (link.isEmpty()) {
            showInfo("Link Kosong", "Paste link Google Maps tujuan terlebih dahulu.");
            return;
        }
        setLoading(true, "Cek link...");
        new Thread(() -> {
            try {
                String finalLink = link;
                if (link.contains("maps.app.goo.gl") || link.contains("goo.gl/maps")) {
                    JSONObject res = postJson(RESOLVE_MAP_URL, new JSONObject().put("url", link));
                    if (res.optBoolean("success", false)) finalLink = res.optString("url", link);
                }
                double[] coords = extractLatLng(finalLink);
                if (coords == null) {
                    mainHandler.post(() -> {
                        setLoading(false, null);
                        showInfo("Lokasi Tidak Valid", "Link Google Maps tidak bisa dibaca.");
                    });
                    return;
                }
                String addr = getAddress(coords[0], coords[1]);
                mainHandler.post(() -> {
                    setMode("delivery");
                    deliveryLat = coords[0];
                    deliveryLng = coords[1];
                    deliveryAddress = addr;
                    centerMap(deliveryLat, deliveryLng);
                    updateLocationTexts();
                    updateMapPoint("delivery", deliveryLat, deliveryLng, shortAddr(addr));
                    setLoading(false, null);
                    googleLinkInput.setText("");
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setLoading(false, null);
                    showInfo("Gagal", "Gagal membaca link Google Maps.");
                });
            }
        }).start();
    }

    private double[] extractLatLng(String link) {
        try {
            String decoded = Uri.decode(link);
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

    private void updateLocationTexts() {
        pickupText.setText("👤 Lokasi Jemput\n" + (pickupLat != 0 && pickupLng != 0 ? shortAddr(pickupAddress) + " • " + fmt(pickupLat) + ", " + fmt(pickupLng) : "Geser peta atau tekan GPS"));
        deliveryText.setText("📍 Lokasi Tujuan\n" + (deliveryLat != 0 && deliveryLng != 0 ? shortAddr(deliveryAddress) + " • " + fmt(deliveryLat) + ", " + fmt(deliveryLng) : "Pilih tab Tujuan lalu geser peta"));

        if (pickupLat != 0 && pickupLng != 0 && deliveryLat != 0 && deliveryLng != 0) {
            double km = haversineKm(pickupLat, pickupLng, deliveryLat, deliveryLng) * 1.25;
            int minutes = Math.max(1, (int) Math.ceil(km * 4));
            estimateText.setText("Estimasi " + String.format(Locale.US, "%.1f", km) + " KM • " + minutes + " menit");
        } else {
            estimateText.setText("Geser peta untuk memilih jemput/tujuan");
        }
    }

    private void confirmAndCreateOrder() {
        if (loading) return;
        if (userId <= 0) {
            showInfo("Sesi Berakhir", "Sesi login tidak valid. Silakan login ulang.");
            return;
        }
        if (pickupLat == 0 || pickupLng == 0) {
            showInfo("Pickup Belum Ada", "Pilih titik jemput terlebih dahulu.");
            return;
        }
        if (deliveryLat == 0 || deliveryLng == 0) {
            showInfo("Tujuan Belum Ada", "Pilih titik tujuan terlebih dahulu.");
            return;
        }
        double km = haversineKm(pickupLat, pickupLng, deliveryLat, deliveryLng) * 1.25;
        int minutes = Math.max(1, (int) Math.ceil(km * 4));
        new AlertDialog.Builder(this)
                .setTitle("Konfirmasi Order")
                .setMessage("Layanan: Transbike\nJarak estimasi: " + String.format(Locale.US, "%.1f", km) + " KM\nEstimasi waktu: " + minutes + " menit\n\nLanjut order sekarang?")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Order", (d, w) -> createOrder())
                .show();
    }

    private void createOrder() {
        setLoading(true, "Order...");
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("user_id", userId);
                payload.put("id", "ORD-" + System.currentTimeMillis());
                payload.put("order_type", "Transbike");
                payload.put("driver_type", "bike");
                payload.put("service_name", "Transbike");
                payload.put("note", noteInput.getText().toString().trim());
                payload.put("status", "pending");

                JSONObject pickup = new JSONObject();
                pickup.put("latitude", pickupLat);
                pickup.put("longitude", pickupLng);
                pickup.put("address", pickupAddress);
                payload.put("pickup", pickup);

                JSONObject delivery = new JSONObject();
                delivery.put("latitude", deliveryLat);
                delivery.put("longitude", deliveryLng);
                delivery.put("address", deliveryAddress);
                payload.put("delivery", delivery);

                double km = haversineKm(pickupLat, pickupLng, deliveryLat, deliveryLng) * 1.25;
                JSONObject route = new JSONObject();
                route.put("km", km);
                route.put("minutes", Math.max(1, (int) Math.ceil(km * 4)));
                route.put("price", 0);
                payload.put("route", route);

                JSONObject userLocation = new JSONObject();
                userLocation.put("latitude", userLat == 0 ? pickupLat : userLat);
                userLocation.put("longitude", userLng == 0 ? pickupLng : userLng);
                payload.put("userLocation", userLocation);

                JSONObject save = postJson(CREATE_ORDER_URL, payload);
                mainHandler.post(() -> {
                    setLoading(false, null);
                    if (save.optBoolean("success", false)) {
                        String orderId = firstNonEmpty(save.optString("order_id", ""), save.optString("id", ""));
                        showSuccessOrder(orderId, rupiah(save.optDouble("price", 0)));
                    } else {
                        showInfo("Order Gagal", firstNonEmpty(save.optString("message", ""), "Gagal membuat order."));
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setLoading(false, null);
                    showInfo("Order Gagal", "Koneksi server bermasalah. Silakan coba lagi.");
                });
            }
        }).start();
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

    private String getAddress(double lat, double lng) {
        try {
            Geocoder geocoder = new Geocoder(this, new Locale("id", "ID"));
            List<Address> list = geocoder.getFromLocation(lat, lng, 1);
            if (list != null && list.size() > 0) {
                Address a = list.get(0);
                return firstNonEmpty(a.getAddressLine(0), a.getSubAdminArea(), a.getLocality(), a.getAdminArea(), "Lokasi dipilih");
            }
        } catch (Exception ignored) {}
        return "Lokasi dipilih";
    }

    private void showSuccessOrder(String orderId, String price) {
        new AlertDialog.Builder(this)
                .setTitle("Order Berhasil")
                .setMessage("Order berhasil dibuat.\nOrder ID: " + orderId + "\nOngkir: " + price + "\n\nTransiva akan mencarikan driver terdekat.")
                .setCancelable(false)
                .setPositiveButton("Cari Driver", (d, w) -> openSearchDriver(orderId))
                .show();
    }

    private void openSearchDriver(String orderId) {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("native_route", "?route=searchDriver");
            intent.putExtra("url", BASE_URL + "?route=searchDriver");
            intent.putExtra("active_order_id", orderId);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            finish();
        }
    }

    private void showGpsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("GPS Belum Aktif")
                .setMessage("Aktifkan GPS untuk memakai TransRide.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Aktifkan", (d, w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                .show();
    }

    private int checkPermission(String permission) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 23) return checkSelfPermission(permission);
            return PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return PackageManager.PERMISSION_DENIED;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            boolean ok = false;
            if (grantResults != null) {
                for (int g : grantResults) if (g == PackageManager.PERMISSION_GRANTED) ok = true;
            }
            if (ok) ensureLocationPermissionAndGps();
            else showInfo("Izin Lokasi Ditolak", "TransRide membutuhkan izin lokasi agar titik jemput akurat.");
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (mapView != null) {
                mapView.stopLoading();
                mapView.destroy();
                mapView = null;
            }
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    private void setLoading(boolean value, String text) {
        loading = value;
        if (progressBar != null) progressBar.setVisibility(value ? View.VISIBLE : View.GONE);
        if (orderBtn != null) orderBtn.setEnabled(!value);
        if (gpsBtn != null) gpsBtn.setEnabled(!value);
        if (linkBtn != null) linkBtn.setEnabled(!value);
        if (orderBtn != null) orderBtn.setText(value ? firstNonEmpty(text, "Memuat...") : "Order");
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

    private String shortAddr(String value) {
        String clean = firstNonEmpty(value, "Lokasi dipilih");
        clean = clean.replace("Kabupaten ", "").replace("Kota ", "").replace("Provinsi ", "");
        if (clean.length() > 54) clean = clean.substring(0, 54).trim() + "...";
        return clean;
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    private String rupiah(double value) {
        try {
            NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));
            nf.setMaximumFractionDigits(0);
            return nf.format(value).replace("Rp", "Rp ");
        } catch (Exception e) {
            return "Rp " + Math.round(value);
        }
    }

    private String fmt(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && v.trim().length() > 0 && !v.trim().equalsIgnoreCase("null")) return v.trim();
        }
        return "";
    }

    private String escapeJs(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\r", " ").replace("\n", " ");
    }

    private Button primaryButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackground(roundGradient("#086BFF", "#2EA2FF", dp(18)));
        return b;
    }

    private Button outlineButton(String value) {
        Button b = primaryButton(value);
        b.setTextColor(Color.parseColor("#0B7CFF"));
        b.setBackground(roundStroke("#FFFFFF", "#9DCAFF", dp(18), 1));
        return b;
    }

    private Button smallButton(String value, String bg, String fg, String stroke) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(12);
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

    private GradientDrawable roundGradient(String start, String end, int radius) {
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor(start), Color.parseColor(end)});
        gd.setCornerRadius(radius);
        return gd;
    }

    private void showInfo(String title, String message) {
        try {
            new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
        } catch (Exception e) {
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
