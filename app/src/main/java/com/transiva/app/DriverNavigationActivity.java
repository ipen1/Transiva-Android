package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.Icon;
import org.maplibre.android.annotations.IconFactory;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapLibreMapOptions;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.sources.GeoJsonSource;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

import static org.maplibre.android.style.layers.PropertyFactory.lineCap;
import static org.maplibre.android.style.layers.PropertyFactory.lineColor;
import static org.maplibre.android.style.layers.PropertyFactory.lineJoin;
import static org.maplibre.android.style.layers.PropertyFactory.lineOpacity;
import static org.maplibre.android.style.layers.PropertyFactory.lineWidth;
import static org.maplibre.android.style.layers.Property.LINE_CAP_ROUND;
import static org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND;

/**
 * Transiva Native Navigation.
 *
 * Fast path:
 * 1. No WebView / Leaflet / JavaScript.
 * 2. MapLibre Native renders with the Android native/GPU pipeline.
 * 3. OpenFreeMap provides the basemap without API keys.
 * 4. StableRouteEngine starts before the map style is ready; cached routes are
 *    normally available immediately after DriverTripActivity.
 * 5. Camera is heading-up: the road ahead stays at the top of the screen.
 */
public class DriverNavigationActivity extends Activity {
    private static final String MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty";
    private static final String LOCATION_API =
            "https://transiva.my.id/server/driver_update_location_native.php";

    private static final String ROUTE_SOURCE = "transiva-route-source";
    private static final String ROUTE_CASE_LAYER = "transiva-route-case";
    private static final String ROUTE_LAYER = "transiva-route-line";

    private static final long LOCATION_UPLOAD_MS = 2500L;
    private static final long ROUTE_REFRESH_MS = 15000L;
    private static final float ROUTE_REFRESH_DISTANCE_M = 35f;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final SmoothLocationEngine smoothLocation = new SmoothLocationEngine(1800L);

    private MapView mapView;
    private MapLibreMap map;
    private Style style;
    private Marker driverMarker;
    private Marker pickupMarker;
    private Marker deliveryMarker;

    private TextView routeBadge;
    private TextView speedBadge;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location lastLocation;
    private Location lastRouteLocation;
    private Location lastSpeedLocation;

    private JSONObject order = new JSONObject();
    private SessionManager session;
    private String username = "";
    private String vehicleType = "motor";
    private String targetMode = "pickup";

    private double driverLat;
    private double driverLng;
    private double currentBearing;
    private double currentSpeedKmh;
    private double averageSpeedKmh;
    private double speedSum;
    private long speedSamples;
    private long lastUploadAt;
    private long lastRouteRequestAt;

    private boolean styleReady;
    private boolean routeInFlight;
    private boolean userAdjustedZoom = false;

    // Final navigation smoothing: frequent small interpolation steps instead of GPS-sized jumps.
    private static final long VISUAL_FRAME_MS = 16L;          // ~60 FPS
    private static final long POSITION_EASE_MS = 900L;
    private static final float POSITION_EASE_ALPHA = 0.16f;
    private static final float BEARING_EASE_ALPHA = 0.10f;
    private static final float CAMERA_BEARING_ALPHA = 0.075f;
    private double smoothCameraBearing = Double.NaN;
    private double smoothMarkerBearing = Double.NaN;
    private String pendingRouteGeoJson = "";
    private double pendingRouteKm;
    private double pendingRouteSeconds;
    private final List<double[]> routePoints = new ArrayList<>();
    private int routeProgressIndex = 0;
    private double snappedBearing = 0d;
    private long lastRouteSuccessAt = 0L;
    private int routeFailureCount = 0;

    private final Runnable routeRetryTick = new Runnable() {
        @Override public void run() {
            if (!isFinishing()) {
                if (routePoints.size() < 2 || System.currentTimeMillis() - lastRouteSuccessAt > 30000L) {
                    requestRoute(true);
                }
                main.postDelayed(this, routePoints.size() < 2 ? 2200L : 10000L);
            }
        }
    };

    private final Runnable animationTick = new Runnable() {
        @Override public void run() {
            animateTowardLatestFix();
            main.postDelayed(this, VISUAL_FRAME_MS);
        }
    };

    private double displayLat;
    private double displayLng;
    private boolean displayInitialized;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            if (getActionBar() != null) getActionBar().hide();
            getWindow().setStatusBarColor(Color.parseColor("#0B3A78"));
            getWindow().setNavigationBarColor(Color.BLACK);
        } catch (Exception ignored) {}

        session = new SessionManager(this);
        readOrder();
        readIdentity();
        seedLastKnownLocation();

        // Initialise MapLibre before MapView.onCreate().
        MapLibre.getInstance(getApplicationContext());

        buildUi(savedInstanceState);

        // Start route calculation immediately; it runs in parallel with native map/style loading.
        requestRoute(true);
        startLocationWatch();
        main.post(animationTick);
        main.postDelayed(routeRetryTick, 2200L);
    }

    private void readOrder() {
        try {
            String raw = getIntent().getStringExtra("order_json");
            if (raw != null && raw.trim().startsWith("{")) order = new JSONObject(raw);
        } catch (Exception ignored) {}
        try {
            String mode = getIntent().getStringExtra("target_mode");
            if (mode != null && mode.toLowerCase(Locale.US).contains("delivery")) targetMode = "delivery";
            else targetMode = routeTargetMode();
        } catch (Exception ignored) {}

        driverLat = getIntent().getDoubleExtra("driver_lat", 0d);
        driverLng = getIntent().getDoubleExtra("driver_lng", 0d);
    }

    private void readIdentity() {
        try {
            username = first(session.getUsername(), session.getName(),
                    getSharedPreferences("transiva", MODE_PRIVATE).getString("username", ""));
            vehicleType = normalizeVehicle(first(session.getDriverType(),
                    getSharedPreferences("transiva", MODE_PRIVATE).getString("driver_type", "motor")));
        } catch (Exception ignored) {}
    }

    private void seedLastKnownLocation() {
        if (valid(driverLat, driverLng)) return;
        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location gps = null, net = null;
            try { if (lm != null) gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
            try { if (lm != null) net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); } catch (Exception ignored) {}
            Location best = gps != null ? gps : net;
            if (gps != null && net != null && net.getTime() > gps.getTime()) best = net;
            if (best != null && valid(best.getLatitude(), best.getLongitude())) {
                driverLat = best.getLatitude();
                driverLng = best.getLongitude();
                lastLocation = new Location(best);
            }
        } catch (Exception ignored) {}
    }

    private void buildUi(Bundle state) {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#EAF4FF"));

        MapLibreMapOptions options = MapLibreMapOptions.createFromAttributes(this)
                .compassEnabled(false)
                .attributionEnabled(false)
                .logoEnabled(false)
                .rotateGesturesEnabled(false)
                .tiltGesturesEnabled(false)
                .scrollGesturesEnabled(false)
                .zoomGesturesEnabled(true);

        mapView = new MapView(this, options);
        page.addView(mapView, new FrameLayout.LayoutParams(-1, -1));
        mapView.onCreate(state);
        mapView.getMapAsync(m -> {
            map = m;
            map.getUiSettings().setCompassEnabled(false);
            map.getUiSettings().setRotateGesturesEnabled(false);
            map.getUiSettings().setTiltGesturesEnabled(false);
            map.getUiSettings().setZoomGesturesEnabled(true);
            map.getUiSettings().setAttributionEnabled(false);
            map.getUiSettings().setLogoEnabled(false);
            map.setStyle(new Style.Builder().fromUri(MAP_STYLE), s -> {
                style = s;
                styleReady = true;
                installRouteLayers();
                installMarkers();
                drawPendingRoute();
                updateNativePosition(true);
            });
        });

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(38);
        back.setTextColor(Color.parseColor("#0B3A78"));
        back.setGravity(Gravity.CENTER);
        back.setBackgroundColor(Color.WHITE);
        back.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(dp(54), dp(54));
        backLp.leftMargin = dp(16);
        backLp.topMargin = dp(18);
        page.addView(back, backLp);

        routeBadge = new TextView(this);
        routeBadge.setText(targetMode.equals("delivery") ? "Menyiapkan rute ke tujuan…" : "Menyiapkan rute ke pickup…");
        routeBadge.setTextColor(Color.parseColor("#0B3A78"));
        routeBadge.setTextSize(16);
        routeBadge.setGravity(Gravity.CENTER_VERTICAL);
        routeBadge.setPadding(dp(18), 0, dp(18), 0);
        routeBadge.setBackground(roundRect(Color.WHITE, 26));
        FrameLayout.LayoutParams routeLp = new FrameLayout.LayoutParams(-1, dp(58));
        routeLp.leftMargin = dp(84);
        routeLp.rightMargin = dp(18);
        routeLp.topMargin = dp(18);
        page.addView(routeBadge, routeLp);

        speedBadge = new TextView(this);
        speedBadge.setText("0 km/j\nRata-rata 0 km/j");
        speedBadge.setTextColor(Color.WHITE);
        speedBadge.setTextSize(16);
        speedBadge.setPadding(dp(16), dp(10), dp(16), dp(10));
        speedBadge.setBackground(roundRect(Color.parseColor("#E6071426"), 22));
        FrameLayout.LayoutParams speedLp = new FrameLayout.LayoutParams(-2, -2);
        speedLp.leftMargin = dp(18);
        speedLp.bottomMargin = dp(28);
        speedLp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        page.addView(speedBadge, speedLp);

        TextView attribution = new TextView(this);
        attribution.setText("© OpenStreetMap contributors");
        attribution.setTextColor(Color.parseColor("#7A475569"));
        attribution.setTextSize(8);
        FrameLayout.LayoutParams attrLp = new FrameLayout.LayoutParams(-2, -2);
        attrLp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        attrLp.rightMargin = dp(8);
        attrLp.bottomMargin = dp(6);
        page.addView(attribution, attrLp);

        setContentView(page);
    }

    private android.graphics.drawable.GradientDrawable roundRect(int color, int radiusDp) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private void installRouteLayers() {
        if (style == null) return;
        try {
            if (style.getSource(ROUTE_SOURCE) == null) {
                style.addSource(new GeoJsonSource(ROUTE_SOURCE, emptyFeatureCollection()));
            }
            if (style.getLayer(ROUTE_CASE_LAYER) == null) {
                style.addLayer(new LineLayer(ROUTE_CASE_LAYER, ROUTE_SOURCE).withProperties(
                        lineColor(Color.parseColor("#174A7E")),
                        lineOpacity(0.32f),
                        lineWidth(10f),
                        lineCap(LINE_CAP_ROUND),
                        lineJoin(LINE_JOIN_ROUND)
                ));
            }
            if (style.getLayer(ROUTE_LAYER) == null) {
                style.addLayer(new LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(
                        lineColor(Color.parseColor("#087CFF")),
                        lineOpacity(0.98f),
                        lineWidth(6f),
                        lineCap(LINE_CAP_ROUND),
                        lineJoin(LINE_JOIN_ROUND)
                ));
            }
        } catch (Exception ignored) {}
    }

    private void installMarkers() {
        if (map == null) return;
        IconFactory f = IconFactory.getInstance(this);
        try {
            double pLat = coord("pickup_lat", "user_lat");
            double pLng = coord("pickup_lng", "user_lng");
            if (valid(pLat, pLng) && pickupMarker == null) {
                Icon icon = iconFromDrawable(f, "map_pickup_pin", android.R.drawable.ic_menu_mylocation);
                pickupMarker = map.addMarker(new MarkerOptions().position(new LatLng(pLat, pLng)).icon(icon));
            }
        } catch (Exception ignored) {}

        try {
            double dLat = coord("delivery_lat", "destination_lat");
            double dLng = coord("delivery_lng", "destination_lng");
            if (valid(dLat, dLng) && deliveryMarker == null) {
                Icon icon = iconFromDrawable(f, "map_destination_pin", android.R.drawable.ic_menu_mylocation);
                deliveryMarker = map.addMarker(new MarkerOptions().position(new LatLng(dLat, dLng)).icon(icon));
            }
        } catch (Exception ignored) {}

        if (valid(driverLat, driverLng) && driverMarker == null) {
            String drawable = vehicleType.equals("car") ? "map_car_top" : "map_motor_top";
            Icon icon = iconFromDrawableScaled(f, drawable,
                    android.R.drawable.ic_menu_directions, dp(56), dp(56));
            driverMarker = map.addMarker(new MarkerOptions()
                    .position(new LatLng(driverLat, driverLng))
                    .icon(icon));
        }
    }

    private Icon iconFromDrawable(IconFactory factory, String name, int fallback) {
        int id = getResources().getIdentifier(name, "drawable", getPackageName());
        if (id <= 0) id = fallback;
        Bitmap b = BitmapFactory.decodeResource(getResources(), id);
        return factory.fromBitmap(b);
    }

    private Icon iconFromDrawableScaled(IconFactory factory, String name, int fallback,
                                        int widthPx, int heightPx) {
        int id = getResources().getIdentifier(name, "drawable", getPackageName());
        if (id <= 0) id = fallback;
        Bitmap raw = BitmapFactory.decodeResource(getResources(), id);
        if (raw == null) return factory.defaultMarker();
        Bitmap scaled = Bitmap.createScaledBitmap(raw,
                Math.max(1, widthPx), Math.max(1, heightPx), true);
        return factory.fromBitmap(scaled);
    }

    private void startLocationWatch() {
        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 801);
            return;
        }
        try {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager == null) return;
            stopLocationWatch();

            locationListener = new LocationListener() {
                @Override public void onLocationChanged(Location raw) {
                    SmoothLocationEngine.Fix fix = smoothLocation.offer(raw);
                    if (fix == null) return;
                    Location l = fix.location;
                    lastLocation = new Location(l);
                    driverLat = l.getLatitude();
                    driverLng = l.getLongitude();
                    updateSpeed(l);

                    if (l.hasBearing() && l.getSpeed() > 1.2f) currentBearing = l.getBearing();
                    else if (displayInitialized) currentBearing = bearing(displayLat, displayLng, driverLat, driverLng);

                    if (fix.upload) uploadLocation(l);
                    if (fix.render) updateNativePosition(false);
                    maybeRefreshRoute();
                }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            };

            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        700L, 0f, locationListener, Looper.getMainLooper());
            } catch (Exception ignored) {}
            try {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        1300L, 0f, locationListener, Looper.getMainLooper());
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    private void stopLocationWatch() {
        try {
            if (locationManager != null && locationListener != null) {
                locationManager.removeUpdates(locationListener);
            }
        } catch (Exception ignored) {}
        locationListener = null;
    }

    /**
     * Native continuous interpolation. The latest GPS fix becomes a moving
     * target; the display advances a fraction every 50 ms instead of stopping
     * between 1-second fixes.
     */
    private void animateTowardLatestFix() {
        if (!valid(driverLat, driverLng) || map == null || !styleReady) return;
        if (!displayInitialized) {
            SnapPoint initial = snapToRoute(driverLat, driverLng);
            displayLat = initial.lat;
            displayLng = initial.lng;
            if (initial.onRoute) snappedBearing = initial.bearing;
            displayInitialized = true;
            updateNativePosition(true);
            return;
        }

        SnapPoint target = snapToRoute(driverLat, driverLng);
        double targetLat = target.lat;
        double targetLng = target.lng;
        if (target.onRoute) snappedBearing = target.bearing;

        float distance = meters(displayLat, displayLng, targetLat, targetLng);
        if (distance < 0.25f) return;

        // Continuous native interpolation toward the route-matched target.
        double fraction = distance > 40f ? 0.38d : (distance > 10f ? 0.20d : 0.13d);
        // Low-pass interpolation makes slow movement continuous instead of move/stop/move.
        // Never teleport visually to a fresh GPS sample unless this is initial acquisition.
        float visualAlpha = Math.max(0.08f, Math.min(POSITION_EASE_ALPHA, fraction));
        displayLat = easePosition(displayLat, targetLat, visualAlpha);
        displayLng = easePosition(displayLng, targetLng, visualAlpha);
        updateNativePosition(false);
        updateRemainingRouteLine();
    }


    private double easeBearing(double current, double target, float alpha) {
        target = ((target % 360d) + 360d) % 360d;
        if (Double.isNaN(current)) return target;
        current = ((current % 360d) + 360d) % 360d;
        double delta = ((target - current + 540d) % 360d) - 180d;
        return (current + delta * alpha + 360d) % 360d;
    }

    private double easePosition(double current, double target, float alpha) {
        return current + (target - current) * alpha;
    }

    private void updateNativePosition(boolean immediate) {
        if (map == null || !styleReady) return;
        installMarkers();

        double lat = displayInitialized ? displayLat : driverLat;
        double lng = displayInitialized ? displayLng : driverLng;
        if (!valid(lat, lng)) return;

        if (driverMarker != null) driverMarker.setPosition(new LatLng(lat, lng));

        double cameraBearing = routePoints.size() >= 2 && Double.isFinite(snappedBearing)
                ? snappedBearing
                : (Double.isFinite(currentBearing) ? currentBearing : 0d);
        double zoom = 18.4d;
        try {
            if (map != null && map.getCameraPosition() != null && map.getCameraPosition().zoom > 2d) {
                zoom = map.getCameraPosition().zoom;
            }
        } catch (Exception ignored) {}

        CameraPosition cp = new CameraPosition.Builder()
                .target(new LatLng(lat, lng))
                .zoom(zoom)
                .bearing(cameraBearing)
                .tilt(42d)
                .build();

        if (immediate) map.moveCamera(CameraUpdateFactory.newCameraPosition(cp));
        else map.easeCamera(CameraUpdateFactory.newCameraPosition(cp), 260);

        speedBadge.setText(String.format(Locale.US, "%.0f km/j\nRata-rata %.0f km/j",
                currentSpeedKmh, averageSpeedKmh));
    }

    private void updateSpeed(Location l) {
        double instant = 0d;
        if (l.hasSpeed() && l.getSpeed() >= 0f) {
            instant = l.getSpeed() * 3.6d;
        } else if (lastSpeedLocation != null) {
            long dt = l.getTime() - lastSpeedLocation.getTime();
            if (dt > 250L && dt < 10000L) {
                instant = lastSpeedLocation.distanceTo(l) / (dt / 1000d) * 3.6d;
            }
        }
        if (!Double.isFinite(instant) || instant < 0d) instant = 0d;
        if (instant > 180d) instant = 180d;
        currentSpeedKmh = currentSpeedKmh <= 0 ? instant :
                currentSpeedKmh * 0.68d + instant * 0.32d;
        if (currentSpeedKmh >= 1d) {
            speedSum += currentSpeedKmh;
            speedSamples++;
            averageSpeedKmh = speedSum / Math.max(1L, speedSamples);
        }
        lastSpeedLocation = new Location(l);
    }

    private void requestRoute(boolean force) {
        if (!valid(driverLat, driverLng) || routeInFlight) return;

        final double toLat = targetLat();
        final double toLng = targetLng();
        if (!valid(toLat, toLng)) return;

        long now = System.currentTimeMillis();
        if (!force && lastRouteLocation != null &&
                meters(lastRouteLocation.getLatitude(), lastRouteLocation.getLongitude(),
                        driverLat, driverLng) < ROUTE_REFRESH_DISTANCE_M &&
                now - lastRouteRequestAt < ROUTE_REFRESH_MS) {
            return;
        }

        routeInFlight = true;
        lastRouteRequestAt = now;
        final double fromLat = driverLat, fromLng = driverLng;

        new Thread(() -> {
            try {
                StableRouteEngine.Result r = StableRouteEngine.fetch(fromLat, fromLng, toLat, toLng);
                pendingRouteGeoJson = routeGeoJson(r.pointsJson());
                setRoutePoints(r.latLngPoints);
                pendingRouteKm = r.distanceMeters / 1000d;
                pendingRouteSeconds = r.durationSeconds;
                lastRouteSuccessAt = System.currentTimeMillis();
                routeFailureCount = 0;

                Location rl = new Location("route");
                rl.setLatitude(fromLat);
                rl.setLongitude(fromLng);
                lastRouteLocation = rl;

                main.post(() -> {
                    updateRemainingRouteLine();
                    // Reproject immediately so the vehicle cannot remain beside the road
                    // after the first route arrives.
                    if (valid(driverLat, driverLng)) {
                        SnapPoint s = snapToRoute(driverLat, driverLng);
                        displayLat = s.lat;
                        displayLng = s.lng;
                        displayInitialized = true;
                        if (s.onRoute) snappedBearing = s.bearing;
                        updateNativePosition(true);
                    }
                    int mins = Math.max(1, (int) Math.ceil(pendingRouteSeconds / 60d));
                    routeBadge.setText(String.format(Locale.US, "%s • %.1f km • %d menit",
                            targetMode.equals("delivery") ? "Menuju tujuan" : "Menuju pickup",
                            pendingRouteKm, mins));
                });
            } catch (Exception ignored) {
                routeFailureCount++;
                main.post(() -> routeBadge.setText(
                        routeFailureCount <= 1 ? "Menyiapkan rute…" : "Rute belum tersedia • mencoba kembali…"));
            } finally {
                routeInFlight = false;
            }
        }, "transiva-native-route").start();
    }

    private void maybeRefreshRoute() {
        if (routePoints.size() >= 2) {
            SnapPoint s = snapToRoute(driverLat, driverLng);
            if (!s.onRoute) {
                requestRoute(true);
                return;
            }
        }
        requestRoute(false);
    }


    /**
     * Keep only the untraveled section visible.
     * The already-passed route is removed from the GeoJSON source as routeProgressIndex advances.
     */
    private void updateRemainingRouteLine() {
        if (!styleReady || style == null) return;
        String geo = remainingRouteGeoJson();
        if (geo == null || geo.isEmpty()) return;
        try {
            GeoJsonSource s = style.getSourceAs(ROUTE_SOURCE);
            if (s != null) s.setGeoJson(geo);
        } catch (Exception ignored) {}
    }

    private String remainingRouteGeoJson() {
        synchronized (routePoints) {
            if (routePoints.size() < 2) return pendingRouteGeoJson;
            try {
                JSONArray coords = new JSONArray();

                // Include current projected position first so the blue line starts at the vehicle.
                if (displayInitialized && valid(displayLat, displayLng)) {
                    JSONArray c0 = new JSONArray();
                    c0.put(displayLng);
                    c0.put(displayLat);
                    coords.put(c0);
                }

                int start = Math.max(0, Math.min(routeProgressIndex + 1, routePoints.size() - 1));
                for (int i = start; i < routePoints.size(); i++) {
                    double[] rp = routePoints.get(i);
                    JSONArray c = new JSONArray();
                    c.put(rp[1]);
                    c.put(rp[0]);
                    coords.put(c);
                }

                if (coords.length() < 2) return pendingRouteGeoJson;

                JSONObject geometry = new JSONObject();
                geometry.put("type", "LineString");
                geometry.put("coordinates", coords);

                JSONObject feature = new JSONObject();
                feature.put("type", "Feature");
                feature.put("properties", new JSONObject());
                feature.put("geometry", geometry);
                return feature.toString();
            } catch (Exception e) {
                return pendingRouteGeoJson;
            }
        }
    }

    private void drawPendingRoute() {
        if (!styleReady || style == null || pendingRouteGeoJson.isEmpty()) return;
        try {
            GeoJsonSource s = style.getSourceAs(ROUTE_SOURCE);
            if (s != null) s.setGeoJson(remainingRouteGeoJson());
        } catch (Exception ignored) {}
    }


    private void setRoutePoints(JSONArray points) {
        synchronized (routePoints) {
            routePoints.clear();
            routeProgressIndex = 0;
            if (points == null) return;
            for (int i = 0; i < points.length(); i++) {
                JSONArray p = points.optJSONArray(i);
                if (p == null || p.length() < 2) continue;
                double lat = p.optDouble(0, Double.NaN);
                double lng = p.optDouble(1, Double.NaN);
                if (valid(lat, lng)) routePoints.add(new double[]{lat, lng});
            }
        }
    }

    /**
     * Lightweight route map-matching.
     * Projects the raw GPS fix onto the nearest route segment and keeps progress
     * mostly forward so GPS noise cannot pull the vehicle back to an old segment.
     */
    private SnapPoint snapToRoute(double lat, double lng) {
        synchronized (routePoints) {
            if (routePoints.size() < 2 || !valid(lat, lng)) {
                return new SnapPoint(lat, lng, currentBearing, false);
            }

            int start = Math.max(0, routeProgressIndex - 3);
            int end = Math.min(routePoints.size() - 2, routeProgressIndex + 140);

            double latScale = 111320d;
            double lngScale = 111320d * Math.max(0.15d, Math.cos(Math.toRadians(lat)));
            double px = lng * lngScale;
            double py = lat * latScale;

            double bestDist2 = Double.MAX_VALUE;
            double bestLat = lat, bestLng = lng, bestBearing = currentBearing;
            int bestIndex = routeProgressIndex;

            for (int i = start; i <= end; i++) {
                double[] a = routePoints.get(i);
                double[] b = routePoints.get(i + 1);

                double ax = a[1] * lngScale, ay = a[0] * latScale;
                double bx = b[1] * lngScale, by = b[0] * latScale;
                double vx = bx - ax, vy = by - ay;
                double len2 = vx * vx + vy * vy;
                double t = len2 <= 0.000001d ? 0d :
                        ((px - ax) * vx + (py - ay) * vy) / len2;
                t = Math.max(0d, Math.min(1d, t));

                double qx = ax + vx * t;
                double qy = ay + vy * t;
                double dx = px - qx, dy = py - qy;
                double d2 = dx * dx + dy * dy;

                if (d2 < bestDist2) {
                    bestDist2 = d2;
                    bestLng = qx / lngScale;
                    bestLat = qy / latScale;
                    bestBearing = bearing(a[0], a[1], b[0], b[1]);
                    bestIndex = i;
                }
            }

            double distanceToRoute = Math.sqrt(bestDist2);
            // If GPS is extremely far from the route, use raw position and ask for reroute.
            if (distanceToRoute > 90d) {
                return new SnapPoint(lat, lng, currentBearing, false);
            }

            // Do not jump backward because of GPS/network noise.
            if (bestIndex >= routeProgressIndex - 1) {
                int oldProgress = routeProgressIndex;
                routeProgressIndex = Math.max(routeProgressIndex, bestIndex);
                if (routeProgressIndex != oldProgress) {
                    main.post(this::updateRemainingRouteLine);
                }
            }

            return new SnapPoint(bestLat, bestLng, bestBearing, true);
        }
    }

    private static final class SnapPoint {
        final double lat, lng, bearing;
        final boolean onRoute;
        SnapPoint(double lat, double lng, double bearing, boolean onRoute) {
            this.lat = lat;
            this.lng = lng;
            this.bearing = bearing;
            this.onRoute = onRoute;
        }
    }

    private String routeGeoJson(String pointsJson) throws Exception {
        JSONArray pts = new JSONArray(pointsJson);
        JSONArray coords = new JSONArray();
        for (int i = 0; i < pts.length(); i++) {
            JSONArray p = pts.optJSONArray(i);
            if (p == null || p.length() < 2) continue;
            JSONArray c = new JSONArray();
            c.put(p.optDouble(1)); // lng
            c.put(p.optDouble(0)); // lat
            coords.put(c);
        }
        JSONObject geometry = new JSONObject();
        geometry.put("type", "LineString");
        geometry.put("coordinates", coords);
        JSONObject feature = new JSONObject();
        feature.put("type", "Feature");
        feature.put("properties", new JSONObject());
        feature.put("geometry", geometry);
        return feature.toString();
    }

    private String emptyFeatureCollection() {
        return "{\"type\":\"FeatureCollection\",\"features\":[]}";
    }

    private void uploadLocation(Location l) {
        long now = System.currentTimeMillis();
        if (now - lastUploadAt < LOCATION_UPLOAD_MS) return;
        lastUploadAt = now;

        final double lat = l.getLatitude(), lng = l.getLongitude();
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                JSONObject body = new JSONObject();
                body.put("username", username);
                body.put("driver", username);
                body.put("latitude", lat);
                body.put("longitude", lng);
                body.put("driver_type", vehicleType);
                body.put("accuracy", l.hasAccuracy() ? l.getAccuracy() : JSONObject.NULL);
                body.put("speed", currentSpeedKmh);
                body.put("speed_kmh", currentSpeedKmh);
                body.put("average_speed_kmh", averageSpeedKmh);
                body.put("bearing", currentBearing);
                body.put("location_time", l.getTime());
                body.put("order_id", first(order.optString("order_id"), order.optString("id"), ""));

                c = (HttpURLConnection) new URL(LOCATION_API).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(5000);
                c.setReadTimeout(6000);
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("Accept", "application/json");
                try {
                    String token = session == null ? "" : session.getToken();
                    if (token != null && !token.trim().isEmpty()) {
                        c.setRequestProperty("Authorization", "Bearer " + token.trim());
                    }
                } catch (Exception ignored) {}
                c.setDoOutput(true);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                c.getResponseCode();
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
        }, "transiva-location-upload").start();
    }

    private double targetLat() {
        return targetMode.equals("delivery") ?
                coord("delivery_lat", "destination_lat") :
                coord("pickup_lat", "user_lat");
    }

    private double targetLng() {
        return targetMode.equals("delivery") ?
                coord("delivery_lng", "destination_lng") :
                coord("pickup_lng", "user_lng");
    }

    private String routeTargetMode() {
        String st = first(order.optString("status"), "taken").toLowerCase(Locale.US);
        if (st.equals("arrived_pickup") || st.equals("on_delivery") || st.equals("arrived_delivery")) {
            return "delivery";
        }
        return "pickup";
    }

    private double coord(String a, String b) {
        try { return Double.parseDouble(first(order.optString(a), order.optString(b), "0")); }
        catch (Exception e) { return 0d; }
    }

    private String normalizeVehicle(String value) {
        value = first(value, "motor").toLowerCase(Locale.US);
        return value.contains("car") || value.contains("mobil") ? "car" : "motor";
    }

    private float meters(double aLat, double aLng, double bLat, double bLng) {
        try {
            float[] r = new float[1];
            Location.distanceBetween(aLat, aLng, bLat, bLng, r);
            return r[0];
        } catch (Exception e) {
            return 999999f;
        }
    }

    private double bearing(double lat1, double lng1, double lat2, double lng2) {
        if (!valid(lat1, lng1) || !valid(lat2, lng2)) return currentBearing;
        double dl = Math.toRadians(lng2 - lng1);
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) -
                Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return (Math.toDegrees(Math.atan2(y, x)) + 360d) % 360d;
    }

    private boolean valid(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng) && lat != 0d && lng != 0d;
    }

    private String first(String... values) {
        if (values == null) return "";
        for (String s : values) {
            if (s != null && !s.trim().isEmpty() && !"null".equalsIgnoreCase(s.trim())) return s.trim();
        }
        return "";
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + .5f);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 801 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationWatch();
        }
    }

    @Override protected void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStart();
    }

    @Override protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
        startLocationWatch();
    }

    @Override protected void onPause() {
        stopLocationWatch();
        if (mapView != null) mapView.onPause();
        super.onPause();
    }

    @Override protected void onStop() {
        if (mapView != null) mapView.onStop();
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }

    @Override protected void onDestroy() {
        stopLocationWatch();
        main.removeCallbacks(animationTick);
        main.removeCallbacks(routeRetryTick);
        main.removeCallbacksAndMessages(null);
        if (mapView != null) mapView.onDestroy();
        super.onDestroy();
    }
}
