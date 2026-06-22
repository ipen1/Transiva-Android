package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

public class NativeMapActivity extends Activity implements OnMapReadyCallback {

    private static final String MAP_BUNDLE_KEY = "map_state";
    private static final int REQ_LOCATION = 712;

    private static final String ACTIVE_ORDER_URL =
            "https://transiva.my.id/server/getActiveDriverOrder.php?driver=";

    private static final String UPDATE_LOCATION_URL =
            "https://transiva.my.id/server/updateDriverLocation.php";

    private MapView mapView;
    private GoogleMap map;
    private FusedLocationProviderClient fusedLocation;
    private LocationCallback locationCallback;
    private Handler handler = new Handler(Looper.getMainLooper());

    private TextView statusText;
    private TextView addressText;
    private Button refreshBtn;
    private Button googleNavBtn;
    private Button backBtn;

    private SessionManager sessionManager;
    private String username = "";

    private Marker driverMarker;
    private Marker pickupMarker;
    private Marker deliveryMarker;
    private Polyline routeLine;

    private LatLng myLatLng;
    private JSONObject activeOrder;
    private boolean firstCameraMove = true;

    private final Runnable orderPoller = new Runnable() {
        @Override
        public void run() {
            fetchActiveOrder();
            handler.postDelayed(this, 7000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            getWindow().setStatusBarColor(Color.parseColor("#06142E"));
            getWindow().setNavigationBarColor(Color.parseColor("#06142E"));
        } catch (Exception ignored) {}

        sessionManager = new SessionManager(this);
        username = getSessionUsername();

        if (username.trim().isEmpty()) {
            Toast.makeText(this, "Session driver tidak ditemukan", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        fusedLocation = LocationServices.getFusedLocationProviderClient(this);

        Bundle mapBundle = null;
        if (savedInstanceState != null) {
            mapBundle = savedInstanceState.getBundle(MAP_BUNDLE_KEY);
        }

        buildUi(mapBundle);
        setupLocationCallback();
    }

    private void buildUi(Bundle mapBundle) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#06142E"));

        mapView = new MapView(this);
        mapView.onCreate(mapBundle);
        mapView.getMapAsync(this);
        root.addView(mapView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackgroundColor(Color.WHITE);

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(-1, -2);
        panelLp.gravity = Gravity.BOTTOM;
        panelLp.setMargins(dp(14), dp(14), dp(14), dp(14));
        root.addView(panel, panelLp);

        statusText = new TextView(this);
        statusText.setText("Memuat native map...");
        statusText.setTextSize(16);
        statusText.setTextColor(Color.parseColor("#111827"));
        statusText.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(statusText, new LinearLayout.LayoutParams(-1, -2));

        addressText = new TextView(this);
        addressText.setText("Driver: " + username);
        addressText.setTextSize(13);
        addressText.setTextColor(Color.parseColor("#4B5563"));
        addressText.setPadding(0, dp(6), 0, dp(10));
        panel.addView(addressText, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(row, new LinearLayout.LayoutParams(-1, -2));

        refreshBtn = smallButton("Refresh");
        refreshBtn.setOnClickListener(v -> fetchActiveOrder());
        row.addView(refreshBtn, new LinearLayout.LayoutParams(0, -2, 1));

        googleNavBtn = smallButton("Navigasi");
        googleNavBtn.setEnabled(false);
        googleNavBtn.setOnClickListener(v -> openGoogleNavigation());
        row.addView(googleNavBtn, new LinearLayout.LayoutParams(0, -2, 1));

        backBtn = smallButton("Kembali");
        backBtn.setOnClickListener(v -> finish());
        row.addView(backBtn, new LinearLayout.LayoutParams(0, -2, 1));

        setContentView(root);
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        return b;
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;

        map.getUiSettings().setZoomControlsEnabled(false);
        map.getUiSettings().setCompassEnabled(true);
        map.getUiSettings().setMyLocationButtonEnabled(true);

        if (hasLocationPermission()) {
            enableMyLocation();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQ_LOCATION
            );
        }

        fetchActiveOrder();
        handler.post(orderPoller);
    }

    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;

                Location location = result.getLastLocation();
                if (location == null) return;

                myLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                updateDriverMarker();
                uploadDriverLocation(location.getLatitude(), location.getLongitude());

                if (firstCameraMove && map != null) {
                    firstCameraMove = false;
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(myLatLng, 16f));
                }
            }
        };
    }

    private void enableMyLocation() {
        if (map == null || !hasLocationPermission()) return;

        try {
            map.setMyLocationEnabled(true);
        } catch (SecurityException ignored) {}

        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                5000
        )
                .setMinUpdateIntervalMillis(2500)
                .setMinUpdateDistanceMeters(8)
                .build();

        try {
            fusedLocation.requestLocationUpdates(
                    request,
                    locationCallback,
                    Looper.getMainLooper()
            );
        } catch (SecurityException ignored) {}
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_LOCATION) {
            if (hasLocationPermission()) {
                enableMyLocation();
            } else {
                Toast.makeText(this, "Izin lokasi wajib untuk native map driver", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void fetchActiveOrder() {
        if (username.trim().isEmpty()) return;

        statusText.setText("Mengecek order berjalan...");

        new Thread(() -> {
            try {
                String url = ACTIVE_ORDER_URL + URLEncoder.encode(username, "UTF-8");
                String response = get(url);
                JSONObject root = new JSONObject(response);

                boolean success = root.optBoolean("success", false);
                JSONObject order = root.optJSONObject("order");

                runOnUiThread(() -> {
                    if (!success || order == null) {
                        activeOrder = null;
                        clearOrderMarkers();
                        googleNavBtn.setEnabled(false);
                        statusText.setText("Tidak ada order berjalan");
                        addressText.setText("Driver: " + username);
                        return;
                    }

                    activeOrder = order;
                    drawOrder(order);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusText.setText("Gagal memuat order");
                    addressText.setText(cleanError(e.getMessage()));
                });
            }
        }).start();
    }

    private void drawOrder(JSONObject order) {
        if (map == null) return;

        double pickupLat = optDoubleAny(order, "pickup_lat", "pickupLatitude", "pickupLat");
        double pickupLng = optDoubleAny(order, "pickup_lng", "pickupLongitude", "pickupLng");
        double deliveryLat = optDoubleAny(order, "delivery_lat", "drop_lat", "destination_lat", "deliveryLatitude");
        double deliveryLng = optDoubleAny(order, "delivery_lng", "drop_lng", "destination_lng", "deliveryLongitude");

        LatLng pickup = validLatLng(pickupLat, pickupLng) ? new LatLng(pickupLat, pickupLng) : null;
        LatLng delivery = validLatLng(deliveryLat, deliveryLng) ? new LatLng(deliveryLat, deliveryLng) : null;

        if (pickupMarker != null) pickupMarker.remove();
        if (deliveryMarker != null) deliveryMarker.remove();
        if (routeLine != null) routeLine.remove();

        if (pickup != null) {
            pickupMarker = map.addMarker(new MarkerOptions()
                    .position(pickup)
                    .title("Pickup")
                    .snippet(order.optString("pickup_address", "Lokasi jemput"))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        }

        if (delivery != null) {
            deliveryMarker = map.addMarker(new MarkerOptions()
                    .position(delivery)
                    .title("Tujuan")
                    .snippet(order.optString("delivery_address", "Lokasi tujuan"))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
        }

        PolylineOptions line = new PolylineOptions()
                .width(8f)
                .color(Color.parseColor("#2563EB"))
                .geodesic(true);

        if (myLatLng != null) line.add(myLatLng);
        if (pickup != null) line.add(pickup);
        if (delivery != null) line.add(delivery);

        if (line.getPoints().size() >= 2) {
            routeLine = map.addPolyline(line);
        }

        String status = order.optString("status", "-");
        String orderId = order.optString("order_id", order.optString("id", "-"));
        String pickupAddress = order.optString("pickup_address", "-");
        String deliveryAddress = order.optString("delivery_address", "-");

        statusText.setText("Order #" + orderId + " • " + status);
        addressText.setText("Pickup: " + pickupAddress + "\nTujuan: " + deliveryAddress);
        googleNavBtn.setEnabled(pickup != null || delivery != null);

        fitCamera(pickup, delivery);
    }

    private void updateDriverMarker() {
        if (map == null || myLatLng == null) return;

        if (driverMarker == null) {
            driverMarker = map.addMarker(new MarkerOptions()
                    .position(myLatLng)
                    .title("Posisi saya")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        } else {
            driverMarker.setPosition(myLatLng);
        }
    }

    private void fitCamera(LatLng pickup, LatLng delivery) {
        if (map == null) return;

        try {
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            boolean hasPoint = false;

            if (myLatLng != null) {
                builder.include(myLatLng);
                hasPoint = true;
            }

            if (pickup != null) {
                builder.include(pickup);
                hasPoint = true;
            }

            if (delivery != null) {
                builder.include(delivery);
                hasPoint = true;
            }

            if (hasPoint) {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), dp(80)));
            }
        } catch (Exception ignored) {}
    }

    private void clearOrderMarkers() {
        if (pickupMarker != null) pickupMarker.remove();
        if (deliveryMarker != null) deliveryMarker.remove();
        if (routeLine != null) routeLine.remove();

        pickupMarker = null;
        deliveryMarker = null;
        routeLine = null;
    }

    private void uploadDriverLocation(double lat, double lng) {
        String orderId = "";
        try {
            if (activeOrder != null) {
                orderId = activeOrder.optString("order_id", activeOrder.optString("id", ""));
            }
        } catch (Exception ignored) {}

        String finalOrderId = orderId;

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("username", username);
                payload.put("order_id", finalOrderId);
                payload.put("latitude", lat);
                payload.put("longitude", lng);
                postJson(UPDATE_LOCATION_URL, payload.toString());
            } catch (Exception ignored) {}
        }).start();
    }

    private void openGoogleNavigation() {
        if (activeOrder == null) return;

        double pickupLat = optDoubleAny(activeOrder, "pickup_lat", "pickupLatitude", "pickupLat");
        double pickupLng = optDoubleAny(activeOrder, "pickup_lng", "pickupLongitude", "pickupLng");
        double deliveryLat = optDoubleAny(activeOrder, "delivery_lat", "drop_lat", "destination_lat", "deliveryLatitude");
        double deliveryLng = optDoubleAny(activeOrder, "delivery_lng", "drop_lng", "destination_lng", "deliveryLongitude");

        String status = activeOrder.optString("status", "").toLowerCase(Locale.US);

        double navLat;
        double navLng;

        if (status.equals("taken") || status.equals("arrived_pickup")) {
            navLat = pickupLat;
            navLng = pickupLng;
        } else {
            navLat = deliveryLat;
            navLng = deliveryLng;
        }

        if (!validLatLng(navLat, navLng)) {
            Toast.makeText(this, "Koordinat navigasi tidak valid", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri = Uri.parse("google.navigation:q=" + navLat + "," + navLng + "&mode=d");
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage("com.google.android.apps.maps");

        try {
            startActivity(intent);
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + navLat + "," + navLng)));
        }
    }

    private String get(String urlText) throws Exception {
        HttpURLConnection conn = null;

        try {
            conn = (HttpURLConnection) new URL(urlText).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("GET");
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "TransivaAndroidNativeMap/1.0");

            int code = conn.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String result = readStream(stream).trim();

            if (result.isEmpty()) throw new Exception("Server kosong HTTP " + code);
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + limit(result));

            return result;

        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String postJson(String urlText, String bodyText) throws Exception {
        HttpURLConnection conn = null;

        try {
            byte[] body = bodyText.getBytes("UTF-8");

            conn = (HttpURLConnection) new URL(urlText).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "TransivaAndroidNativeMap/1.0");
            conn.setRequestProperty("Content-Length", String.valueOf(body.length));

            OutputStream os = conn.getOutputStream();
            os.write(body);
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String result = readStream(stream).trim();

            if (result.isEmpty()) throw new Exception("Server kosong HTTP " + code);
            return result;

        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";

        BufferedReader br = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null) {
            sb.append(line);
        }

        br.close();
        return sb.toString();
    }

    private String getSessionUsername() {
        try {
            String[] methodNames = {
                    "getUsername",
                    "getUserName",
                    "getName",
                    "username"
            };

            for (String name : methodNames) {
                try {
                    Method m = sessionManager.getClass().getMethod(name);
                    Object value = m.invoke(sessionManager);
                    if (value != null && !String.valueOf(value).trim().isEmpty()) {
                        return String.valueOf(value).trim();
                    }
                } catch (Exception ignored) {}
            }

            String[] jsonMethods = {
                    "getUserJson",
                    "getSession",
                    "getSessionJson",
                    "getUserData",
                    "getUser"
            };

            for (String name : jsonMethods) {
                try {
                    Method m = sessionManager.getClass().getMethod(name);
                    Object value = m.invoke(sessionManager);

                    if (value != null) {
                        JSONObject user = new JSONObject(String.valueOf(value));
                        String username = firstNonEmpty(
                                user.optString("username", ""),
                                user.optString("name", ""),
                                user.optString("driver", "")
                        );

                        if (!username.isEmpty()) return username;
                    }
                } catch (Exception ignored) {}
            }

        } catch (Exception ignored) {}

        return "";
    }

    private double optDoubleAny(JSONObject object, String... keys) {
        if (object == null || keys == null) return 0;

        for (String key : keys) {
            try {
                String raw = object.optString(key, "");
                if (raw != null && !raw.trim().isEmpty() && !raw.equals("null")) {
                    return Double.parseDouble(raw.trim());
                }
            } catch (Exception ignored) {}
        }

        return 0;
    }

    private boolean validLatLng(double lat, double lng) {
        return lat != 0
                && lng != 0
                && lat >= -90
                && lat <= 90
                && lng >= -180
                && lng <= 180;
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";

        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !value.equals("null")) {
                return value.trim();
            }
        }

        return "";
    }

    private String cleanError(String message) {
        if (message == null || message.trim().isEmpty()) return "Koneksi/server bermasalah";

        return message
                .replace("java.lang.", "")
                .replace("org.json.", "")
                .replace("Value ", "Data ");
    }

    private String limit(String text) {
        if (text == null) return "";
        text = text.trim();
        return text.length() <= 180 ? text : text.substring(0, 180) + "...";
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();

        if (hasLocationPermission()) {
            enableMyLocation();
        }

        handler.post(orderPoller);
    }

    @Override
    protected void onPause() {
        try {
            fusedLocation.removeLocationUpdates(locationCallback);
        } catch (Exception ignored) {}

        handler.removeCallbacks(orderPoller);
        mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            fusedLocation.removeLocationUpdates(locationCallback);
        } catch (Exception ignored) {}

        handler.removeCallbacks(orderPoller);
        mapView.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Bundle mapBundle = outState.getBundle(MAP_BUNDLE_KEY);

        if (mapBundle == null) {
            mapBundle = new Bundle();
            outState.putBundle(MAP_BUNDLE_KEY, mapBundle);
        }

        mapView.onSaveInstanceState(mapBundle);
        super.onSaveInstanceState(outState);
    }
}
