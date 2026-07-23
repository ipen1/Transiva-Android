package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.transiva.app.driver.data.DriverApiClient;

import org.json.JSONObject;

import java.util.Locale;

/**
 * Premium in-app navigation for active driver trips.
 * Location source of truth: driver_profiles through driver_update_location_native.php.
 */
public class DriverNavigationActivity extends Activity {
    private static final long LOCATION_UPLOAD_INTERVAL_MS = 3000L;
    private static final long MAX_LOCATION_AGE_MS = 30000L;
    private static final float MAX_ACCURACY_M = 120f;
    private static final long LOCATION_TIME_TOLERANCE_MS = 2000L;
    private static final float MAX_REASONABLE_SPEED_MPS = 55f;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WebView mapView;
    private TextView routeTitle;
    private TextView routeSub;
    private TextView syncText;
    private TextView targetText;
    private TextView recenter;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private JSONObject order;
    private String targetMode = "pickup";
    private String vehicleType = "motor";
    private double lastDriverLat = 0, lastDriverLng = 0;
    private double prevDriverLat = 0, prevDriverLng = 0;
    private long lastUploadAt = 0L;
    private boolean mapReady = false;
    private Location acceptedLocation;
    private double lastBearingDeg = 0.0;

    private SessionManager session;
    private DriverApiClient api;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            getWindow().setStatusBarColor(Color.parseColor("#0B3A78"));
            getWindow().setNavigationBarColor(Color.BLACK);
            if (Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(0);
        } catch (Exception ignored) {}

        session = new SessionManager(this);
        api = new DriverApiClient(session);
        loadData();
        buildView();
        startLocationWatch();
    }

    @Override protected void onResume() {
        super.onResume();
        startLocationWatch();
    }

    @Override protected void onPause() {
        stopLocationWatch();
        super.onPause();
    }

    @Override protected void onDestroy() {
        stopLocationWatch();
        try { if (api != null) api.shutdown(); } catch (Exception ignored) {}
        try { if (mapView != null) mapView.destroy(); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 801 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationWatch();
        }
    }

    private void loadData() {
        try {
            String raw = getIntent().getStringExtra("order_json");
            if (raw != null && raw.trim().startsWith("{")) order = new JSONObject(raw);
        } catch (Exception ignored) {}
        if (order == null) order = new JSONObject();

        targetMode = first(
                getIntent().getStringExtra("target"),
                getIntent().getStringExtra("target_mode"),
                routeTargetMode()
        ).toLowerCase(Locale.US);
        if (!"delivery".equals(targetMode)) targetMode = "pickup";

        lastDriverLat = getIntent().getDoubleExtra("driver_lat", 0);
        lastDriverLng = getIntent().getDoubleExtra("driver_lng", 0);
        vehicleType = normalizeVehicle(first(
                order.optString("driver_type"),
                order.optString("vehicle_type"),
                session.getDriverType(),
                session.get("driver_type"),
                "motor"
        ));
    }

    private void buildView() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.WHITE);

        mapView = new WebView(this);
        try {
            WebSettings st = mapView.getSettings();
            st.setJavaScriptEnabled(true);
            st.setDomStorageEnabled(true);
            st.setLoadWithOverviewMode(true);
            st.setUseWideViewPort(true);
            st.setAllowFileAccess(true);
            st.setAllowContentAccess(true);
            st.setCacheMode(WebSettings.LOAD_NO_CACHE);
            if (Build.VERSION.SDK_INT >= 21) st.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            mapView.setWebChromeClient(new WebChromeClient());
        } catch (Exception ignored) {}
        mapView.addJavascriptInterface(new Object() {
            @JavascriptInterface public void routeInfo(double distanceMeters, double durationSeconds) {
                mainHandler.post(() -> {
                    if (routeTitle != null) routeTitle.setText(targetMode.equals("delivery") ? "Menuju tujuan" : "Menuju pickup");
                    if (routeSub != null) {
                        double km = distanceMeters / 1000.0;
                        long min = Math.max(1L, Math.round(durationSeconds / 60.0));
                        routeSub.setText(String.format(Locale.US, "%.1f km • %d menit", km, min));
                    }
                });
            }
        }, "AndroidNav");
        mapView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                mapReady = true;
                updateMap(true);
            }
        });
        mapView.loadDataWithBaseURL("https://transiva.my.id/", fullMapHtml(), "text/html", "UTF-8", null);
        page.addView(mapView, new FrameLayout.LayoutParams(-1, -1));

        // Top compact navigation banner.
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(10), dp(10), dp(14), dp(10));
        top.setBackground(round("#F8FFFFFF", 22));
        top.setElevation(dp(8));

        TextView back = pill("‹", 32, "#0B3A78", true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout topText = new LinearLayout(this);
        topText.setOrientation(LinearLayout.VERTICAL);
        topText.setPadding(dp(10), 0, 0, 0);
        routeTitle = label(targetMode.equals("delivery") ? "Menuju tujuan" : "Menuju pickup", 16, "#0B3A78", true);
        routeSub = label("Mencari rute terbaik…", 12, "#64748B", false);
        topText.addView(routeTitle);
        topText.addView(routeSub);
        top.addView(topText, new LinearLayout.LayoutParams(0, -2, 1));

        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, dp(72));
        tp.leftMargin = dp(14); tp.rightMargin = dp(14); tp.topMargin = dp(14);
        page.addView(top, tp);

        // Right floating recenter control.
        recenter = pill("◎", 24, "#0B3A78", true);
        recenter.setGravity(Gravity.CENTER);
        recenter.setElevation(dp(7));
        recenter.setBackground(round("#F8FFFFFF", 24));
        recenter.setOnClickListener(v -> evaluate("if(window.recenterDriver)recenterDriver();"));
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(dp(50), dp(50));
        rp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        rp.rightMargin = dp(14);
        page.addView(recenter, rp);

        // Bottom trip card keeps useful context visible without covering the route.
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(18), dp(14), dp(18), dp(14));
        bottom.setBackground(round("#FAFFFFFF", 24));
        bottom.setElevation(dp(10));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView service = label(serviceLabel(), 14, "#0B7CFF", true);
        statusRow.addView(service, new LinearLayout.LayoutParams(0, -2, 1));
        syncText = label("● Menunggu GPS", 11, "#64748B", true);
        statusRow.addView(syncText);
        bottom.addView(statusRow);

        targetText = label(targetMode.equals("delivery") ? deliveryAddress() : pickupAddress(), 15, "#111827", true);
        targetText.setPadding(0, dp(6), 0, dp(3));
        bottom.addView(targetText);
        TextView hint = label(targetMode.equals("delivery") ? "Navigasi aktif ke lokasi tujuan" : "Navigasi aktif ke lokasi pickup", 12, "#64748B", false);
        bottom.addView(hint);

        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, -2);
        bp.gravity = Gravity.BOTTOM;
        bp.leftMargin = dp(14); bp.rightMargin = dp(14); bp.bottomMargin = dp(18);
        page.addView(bottom, bp);

        setContentView(page);
    }

    private String fullMapHtml() {
        double pLat = coord("pickup_lat", "user_lat"), pLng = coord("pickup_lng", "user_lng");
        double dLat = coord("delivery_lat", "destination_lat"), dLng = coord("delivery_lng", "destination_lng");
        double cLat = valid(lastDriverLat,lastDriverLng) ? lastDriverLat : (valid(pLat,pLng) ? pLat : (valid(dLat,dLng) ? dLat : -0.9));
        double cLng = valid(lastDriverLat,lastDriverLng) ? lastDriverLng : (valid(pLat,pLng) ? pLng : (valid(dLat,dLng) ? dLng : 119.87));
        String tile = "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png";

        return "<!doctype html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>"+
                "<link rel='stylesheet' href='https://transiva.my.id/js/leaflet.css'><script src='https://transiva.my.id/js/leaflet.js'></script>"+
                "<style>html,body,#map{height:100%;width:100%;margin:0;background:#EAF4FF;overflow:hidden}.leaflet-control-attribution,.leaflet-control-zoom{display:none!important}.leaflet-container{font-family:Arial,sans-serif;background:#EAF4FF}.veh{width:58px;height:58px;object-fit:contain;filter:drop-shadow(0 6px 8px rgba(0,0,0,.35));transform-origin:center}.vehF{width:50px;height:50px;border-radius:25px;background:#fff;display:flex;align-items:center;justify-content:center;font-size:28px;box-shadow:0 6px 16px rgba(0,0,0,.25)}.pin{width:44px;height:44px;border-radius:22px;color:white;display:flex;align-items:center;justify-content:center;font-size:22px;border:3px solid white;box-shadow:0 5px 12px rgba(0,0,0,.25)}.pickup{background:#16A34A}.delivery{background:#EF4444}</style></head><body><div id='map'></div><script>"+
                "var pickup=["+pLat+","+pLng+"],dest=["+dLat+","+dLng+"];var targetMode='"+targetMode+"',vehicleType='"+vehicleType+"';var current=null,marker=null,route=null,routePts=[],lastKey='';"+
                "var map=L.map('map',{zoomControl:false,attributionControl:false,preferCanvas:true}).setView(["+cLat+","+cLng+"],18);L.tileLayer('"+tile+"',{maxZoom:20,crossOrigin:true}).addTo(map);"+
                "function ok(a,b){a=+a;b=+b;return isFinite(a)&&isFinite(b)&&a!==0&&b!==0;}function target(){return targetMode==='delivery'?dest:pickup;}"+
                "function pinIcon(t){return L.divIcon({className:'',html:'<div class=\"pin '+(t==='pickup'?'pickup':'delivery')+'\">'+(t==='pickup'?'⚑':'⌂')+'</div>',iconSize:[44,44],iconAnchor:[22,38]});}"+
                "if(ok(pickup[0],pickup[1]))L.marker(pickup,{icon:pinIcon('pickup')}).addTo(map);if(ok(dest[0],dest[1]))L.marker(dest,{icon:pinIcon('delivery')}).addTo(map);"+
                "function vehIcon(deg){var f=vehicleType==='car'?'map_car_top.png':'map_motor_top.png';var emoji=vehicleType==='car'?'🚘':'🏍️';return L.divIcon({className:'',html:'<img class=\"veh\" src=\"file:///android_res/drawable/'+f+'\" onerror=\"this.outerHTML=\\'<div class=vehF>'+emoji+'</div>\\'\" style=\"transform:rotate('+(+deg||0)+'deg)\">',iconSize:[58,58],iconAnchor:[29,29]});}"+
                "function routeTo(a,b,force){var t=target();if(!ok(a,b)||!ok(t[0],t[1]))return;var k=a.toFixed(4)+','+b.toFixed(4)+'-'+t[0].toFixed(4)+','+t[1].toFixed(4)+'-'+targetMode;if(!force&&k===lastKey)return;lastKey=k;fetch('https://router.project-osrm.org/route/v1/driving/'+b+','+a+';'+t[1]+','+t[0]+'?overview=full&geometries=geojson').then(r=>r.json()).then(j=>{if(!j.routes||!j.routes[0])throw 0;routePts=j.routes[0].geometry.coordinates.map(x=>[x[1],x[0]]);if(route)map.removeLayer(route);route=L.polyline(routePts,{weight:7,opacity:.9,color:'#087CFF',lineCap:'round',lineJoin:'round'}).addTo(map);if(window.AndroidNav&&AndroidNav.routeInfo)AndroidNav.routeInfo(j.routes[0].distance,j.routes[0].duration);}).catch(()=>{if(route)map.removeLayer(route);route=L.polyline([[a,b],t],{weight:5,opacity:.65,color:'#087CFF',dashArray:'8,8'}).addTo(map);});}"+
                "var moveAnim=null;function moveMarker(p,deg){if(!marker){marker=L.marker(p,{icon:vehIcon(deg),zIndexOffset:9999}).addTo(map);current=p;map.setView(p,18,{animate:false});return;}if(moveAnim)cancelAnimationFrame(moveAnim);var q=marker.getLatLng(),from=[q.lat,q.lng],to=p,start=performance.now(),dur=420;marker.setIcon(vehIcon(deg));function step(now){var t=Math.min(1,(now-start)/dur),e=t<.5?2*t*t:1-Math.pow(-2*t+2,2)/2;var x=[from[0]+(to[0]-from[0])*e,from[1]+(to[1]-from[1])*e];marker.setLatLng(x);current=x;map.panTo(x,{animate:false});if(t<1)moveAnim=requestAnimationFrame(step);else{moveAnim=null;marker.setLatLng(to);current=to;}}moveAnim=requestAnimationFrame(step);}"+
                "window.updateDrv=function(a,b,deg){if(!ok(a,b))return;routeTo(+a,+b,false);var p=[+a,+b];moveMarker(p,+deg||0);};"+
                "window.recenterDriver=function(){if(current)map.setView(current,18,{animate:true});};setTimeout(()=>map.invalidateSize(true),500);"+
                "</script></body></html>";
    }

    private void startLocationWatch() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 801);
            return;
        }
        try {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager == null) return;
            stopLocationWatch();
            locationListener = new LocationListener() {
                @Override public void onLocationChanged(Location location) { handleLocation(location); }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            };
            // 0 meter: even slow walking / mock-GPS movement must move the vehicle icon.
            try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 700L, 0f, locationListener, Looper.getMainLooper()); } catch (Exception ignored) {}
            try { locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1200L, 0f, locationListener, Looper.getMainLooper()); } catch (Exception ignored) {}
            try {
                Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (last == null) last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (usable(last)) handleLocation(last);
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    private void handleLocation(Location location) {
        if (!shouldAcceptLocation(location)) return;

        Location previous = acceptedLocation == null ? null : new Location(acceptedLocation);
        acceptedLocation = new Location(location);
        lastDriverLat = location.getLatitude();
        lastDriverLng = location.getLongitude();

        if (location.hasBearing() && location.hasSpeed() && location.getSpeed() >= 0.8f) {
            lastBearingDeg = normalizeBearing(location.getBearing());
        } else if (previous != null && previous.distanceTo(location) >= 3f) {
            lastBearingDeg = bearing(previous.getLatitude(), previous.getLongitude(),
                    location.getLatitude(), location.getLongitude());
        }

        updateMap(false);
        uploadLocation(location, false);
    }

    /**
     * Accepts slow continuous movement while still rejecting genuinely stale fixes and
     * obvious coarse teleports. Provider switching itself is never a reason to freeze.
     */
    private boolean shouldAcceptLocation(Location candidate) {
        if (!usable(candidate)) return false;
        if (acceptedLocation == null) return true;

        long candidateTime = candidate.getTime();
        long acceptedTime = acceptedLocation.getTime();

        // Reject only a genuinely older fix. Do not reject by provider (GPS/NETWORK),
        // because some phones and mock-GPS apps alternate providers while moving slowly.
        if (candidateTime > 0L && acceptedTime > 0L
                && candidateTime + LOCATION_TIME_TOLERANCE_MS < acceptedTime) {
            return false;
        }

        float distance = acceptedLocation.distanceTo(candidate);
        if (distance < 0.15f) {
            // Still accept a newer almost-identical point so bearing/database timestamps
            // stay fresh, but WebView will simply remain visually stable.
            return candidateTime >= acceptedTime;
        }

        float oldAccuracy = acceptedLocation.hasAccuracy()
                ? acceptedLocation.getAccuracy() : MAX_ACCURACY_M;
        float newAccuracy = candidate.hasAccuracy()
                ? candidate.getAccuracy() : MAX_ACCURACY_M;

        // Only block a clearly bad coarse jump. Small/slow movements are always allowed.
        if (distance > 120f && newAccuracy > Math.max(80f, oldAccuracy * 2.5f)) {
            return false;
        }

        // Impossible teleport protection, intentionally applied only to large jumps.
        long dtMs = candidateTime > 0L && acceptedTime > 0L
                ? Math.max(1L, candidateTime - acceptedTime) : 0L;
        if (dtMs > 0L && distance > 120f) {
            float impliedSpeed = distance / (dtMs / 1000f);
            if (impliedSpeed > MAX_REASONABLE_SPEED_MPS) return false;
        }

        return true;
    }

    private void stopLocationWatch() {
        try { if (locationManager != null && locationListener != null) locationManager.removeUpdates(locationListener); } catch (Exception ignored) {}
        locationListener = null;
    }

    private void uploadLocation(Location location, boolean force) {
        if (location == null || !valid(location.getLatitude(), location.getLongitude())) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastUploadAt < LOCATION_UPLOAD_INTERVAL_MS) return;
        lastUploadAt = now;

        JSONObject body = new JSONObject();
        try {
            body.put("latitude", location.getLatitude());
            body.put("longitude", location.getLongitude());
            body.put("accuracy", location.hasAccuracy() ? location.getAccuracy() : JSONObject.NULL);
            body.put("speed", location.hasSpeed() ? location.getSpeed() : JSONObject.NULL);
            body.put("bearing", location.hasBearing() ? location.getBearing() : JSONObject.NULL);
            body.put("location_time", location.getTime());
            body.put("order_id", orderId());
            body.put("order_source", orderSource());
        } catch (Exception ignored) {}

        api.executor().execute(() -> {
            try {
                DriverApiClient.Result result = api.post("driver_update_location_native.php", body);
                mainHandler.post(() -> {
                    session.saveLastLocation(String.valueOf(location.getLatitude()), String.valueOf(location.getLongitude()));
                    if (syncText != null) {
                        syncText.setText("● Lokasi tersinkron");
                        syncText.setTextColor(Color.parseColor("#16A34A"));
                    }
                });
            } catch (DriverApiClient.ApiException e) {
                mainHandler.post(() -> {
                    if (syncText != null) {
                        syncText.setText("● Sinkron tertunda");
                        syncText.setTextColor(Color.parseColor("#F59E0B"));
                    }
                });
            }
        });
    }

    private boolean usable(Location l) {
        if (l == null || !valid(l.getLatitude(), l.getLongitude())) return false;
        if (l.hasAccuracy() && l.getAccuracy() > MAX_ACCURACY_M) return false;
        long age = Math.abs(System.currentTimeMillis() - l.getTime());
        return age <= MAX_LOCATION_AGE_MS;
    }

    private void updateMap(boolean forceRoute) {
        try {
            if (mapView == null || Build.VERSION.SDK_INT < 19 || !mapReady || !valid(lastDriverLat,lastDriverLng)) return;
            double deg = lastBearingDeg;
            if (deg == 0.0 && valid(prevDriverLat,prevDriverLng)) {
                double moved = distanceMeters(prevDriverLat, prevDriverLng, lastDriverLat, lastDriverLng);
                if (moved >= 3.0) deg = bearing(prevDriverLat, prevDriverLng, lastDriverLat, lastDriverLng);
            }
            final String js = "if(window.updateDrv)updateDrv("+lastDriverLat+","+lastDriverLng+","+deg+");";
            mainHandler.post(() -> mapView.evaluateJavascript(js, null));
            prevDriverLat = lastDriverLat;
            prevDriverLng = lastDriverLng;
        } catch (Exception ignored) {}
    }

    private double normalizeBearing(double value) {
        value %= 360.0;
        return value < 0 ? value + 360.0 : value;
    }

    private double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        float[] out = new float[1];
        Location.distanceBetween(lat1, lng1, lat2, lng2, out);
        return out[0];
    }

    private void evaluate(String js) {
        try { if (mapView != null && Build.VERSION.SDK_INT >= 19) mapView.evaluateJavascript(js, null); } catch (Exception ignored) {}
    }

    private String routeTargetMode() {
        String st = first(order.optString("status"), "taken").toLowerCase(Locale.US).trim();
        if (st.equals("arrived_pickup") || st.equals("on_delivery") || st.equals("arrived_delivery") || st.equals("picked_up")) return "delivery";
        return "pickup";
    }

    private String normalizeVehicle(String value) {
        value = first(value, "motor").toLowerCase(Locale.US);
        return value.contains("car") || value.contains("mobil") ? "car" : "motor";
    }

    private String orderId() {
        return first(order.optString("order_id"), order.optString("id"), session.get("current_order_id"));
    }

    private String orderSource() {
        String s = first(order.optString("source_table"), order.optString("source"), order.optString("order_kind"), "orders").toLowerCase(Locale.US);
        return s.contains("pickup") ? "pickup_orders" : "orders";
    }

    private String serviceLabel() {
        return first(order.optString("service_name"), order.optString("order_type"), orderSource().equals("pickup_orders") ? "TransPickup" : ("car".equals(vehicleType) ? "TransCar" : "TransRide"));
    }

    private String pickupAddress() { return first(order.optString("pickup_address"), order.optString("pickup"), order.optString("sender_address"), "Lokasi pickup"); }
    private String deliveryAddress() { return first(order.optString("delivery_address"), order.optString("destination_address"), order.optString("destination"), order.optString("receiver_address"), "Lokasi tujuan"); }

    private double coord(String a, String b) {
        try { return Double.parseDouble(first(order.optString(a), order.optString(b), "0")); } catch (Exception e) { return 0; }
    }

    private double bearing(double lat1, double lng1, double lat2, double lng2) {
        double dLng = Math.toRadians(lng2 - lng1);
        lat1 = Math.toRadians(lat1); lat2 = Math.toRadians(lat2);
        double y = Math.sin(dLng) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    private boolean valid(double lat,double lng) { return lat != 0 && lng != 0 && !Double.isNaN(lat) && !Double.isNaN(lng) && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180; }
    private String first(String... values) { if (values == null) return ""; for (String s: values) if (s != null && !s.trim().isEmpty() && !"null".equalsIgnoreCase(s.trim())) return s.trim(); return ""; }
    private TextView label(String text, int sp, String color, boolean bold) { TextView v = new TextView(this); v.setText(text); v.setTextSize(sp); v.setTextColor(Color.parseColor(color)); if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); return v; }
    private TextView pill(String text, int sp, String color, boolean bold) { TextView v = label(text, sp, color, bold); v.setBackground(round("#FFFFFFFF", 18)); return v; }
    private GradientDrawable round(String color, int radiusDp) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(color)); g.setCornerRadius(dp(radiusDp)); return g; }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
}
