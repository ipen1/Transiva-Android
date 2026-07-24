package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.Locale;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/*
 * Transiva Driver Navigation Premium FINAL
 *
 * Stable build base:
 * - Database location update
 * - Motor / Car vehicle support
 * - Route snap
 * - Navigation style white theme
 * - Safe WebView HTML structure
 *
 * Generated from stable DriverNavigationActivity version.
 */
public class DriverNavigationActivity extends Activity {
    private static final String LOCATION_API = "https://transiva.my.id/server/driver_update_location_native.php";
    private static final long LOCATION_INTERVAL = 3000;
    private static final float MAP_MOVE_THRESHOLD_M = 5.0f;
    private static final long GPS_PRIORITY_MS = 8000L;

    private WebView mapView;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private JSONObject order;
    private String targetMode = "pickup";
    private String driverUsername = "";
    private String vehicleType = "motor";
    private long lastUpload = 0;
    private double lastDriverLat = 0, lastDriverLng = 0;
    private double renderedDriverLat = 0, renderedDriverLng = 0;
    private double prevDriverLat = 0, prevDriverLng = 0;
    private long lastGpsFixAt = 0L;
    private Location lastAcceptedLocation = null;
    private SessionManager session;
    private final SmoothLocationEngine smoothLocation = new SmoothLocationEngine(2500L);
    private volatile boolean routeRequestInFlight = false;
    private long lastRouteRequestAt = 0L;
    private double lastRouteFromLat = 0d, lastRouteFromLng = 0d;
    private String lastRouteMode = "";
    private volatile boolean navigationOpened = false;
    private volatile boolean prefetchFinished = false;
    private String prefetchedRoutePoints = "";
    private double prefetchedRouteKm = 0d;
    private double prefetchedRouteSeconds = 0d;
    private double currentSpeedKmh = 0d;
    private double averageSpeedKmh = 0d;
    private double speedSampleSum = 0d;
    private long speedSampleCount = 0L;
    private Location lastSpeedLocation = null;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        try{
            getWindow().setStatusBarColor(Color.parseColor("#0B3A78"));
            getWindow().setNavigationBarColor(Color.BLACK);
            if(Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(0);
        }catch(Exception ignored){}

        session = new SessionManager(this);
        loadData();
        loadDriverIdentity();
        prepareRouteBeforeOpeningMap();
    }

    @Override protected void onResume(){ super.onResume(); if(navigationOpened) startLocationWatch(); }
    @Override protected void onPause(){ stopLocationWatch(); super.onPause(); }
    @Override protected void onDestroy(){ stopLocationWatch(); try{ if(mapView != null) mapView.destroy(); }catch(Exception ignored){} super.onDestroy(); }

    private void loadData(){
        try{
            String raw = getIntent().getStringExtra("order_json");
            if(raw != null && raw.trim().startsWith("{")) order = new JSONObject(raw);
        }catch(Exception ignored){}
        if(order == null) order = new JSONObject();
        targetMode = first(getIntent().getStringExtra("target_mode"), routeTargetMode()).toLowerCase(Locale.US);
        if(!targetMode.equals("delivery")) targetMode = "pickup";
        lastDriverLat = getIntent().getDoubleExtra("driver_lat", 0);
        lastDriverLng = getIntent().getDoubleExtra("driver_lng", 0);
    }

    
    private void loadDriverIdentity(){
        try{
            driverUsername = getSharedPreferences("transiva", MODE_PRIVATE)
                    .getString("username","");
            vehicleType = getSharedPreferences("transiva", MODE_PRIVATE)
                    .getString("driver_type","motor");
        }catch(Exception ignored){}
    }


    /**
     * Build the first route BEFORE showing the navigation map.
     * This removes the old behaviour where the blue route appeared only after
     * the driver had already moved several metres.
     */
    private void prepareRouteBeforeOpeningMap(){
        showRoutePreparingScreen();

        // Prefer the coordinate sent by DriverTripActivity. If it is missing,
        // try a recent Android last-known fix without blocking the UI.
        if(!valid(lastDriverLat,lastDriverLng)){
            try{
                if(Build.VERSION.SDK_INT < 23 ||
                        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
                    LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);
                    Location gps=null, net=null;
                    try{ if(lm!=null) gps=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER); }catch(Exception ignored){}
                    try{ if(lm!=null) net=lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); }catch(Exception ignored){}
                    Location best=gps!=null?gps:net;
                    if(gps!=null && net!=null){
                        float ga=gps.hasAccuracy()?gps.getAccuracy():9999f;
                        float na=net.hasAccuracy()?net.getAccuracy():9999f;
                        best=ga<=na?gps:net;
                    }
                    if(best!=null && valid(best.getLatitude(),best.getLongitude())){
                        lastDriverLat=best.getLatitude();
                        lastDriverLng=best.getLongitude();
                    }
                }
            }catch(Exception ignored){}
        }

        // Never leave the user on the preparation screen indefinitely.
        mainHandler.postDelayed(this::openNavigationMapOnce, 5500L);

        final String mode=routeTargetMode();
        final double toLat=mode.equals("delivery") ? coord("delivery_lat","destination_lat") : coord("pickup_lat","user_lat");
        final double toLng=mode.equals("delivery") ? coord("delivery_lng","destination_lng") : coord("pickup_lng","user_lng");

        if(!valid(lastDriverLat,lastDriverLng) || !valid(toLat,toLng)){
            prefetchFinished=true;
            openNavigationMapOnce();
            return;
        }

        final double fromLat=lastDriverLat, fromLng=lastDriverLng;
        new Thread(() -> {
            try{
                StableRouteEngine.Result r=StableRouteEngine.fetch(fromLat,fromLng,toLat,toLng);
                prefetchedRoutePoints=r.pointsJson();
                prefetchedRouteKm=r.distanceMeters/1000d;
                prefetchedRouteSeconds=r.durationSeconds;
                lastRouteFromLat=fromLat;
                lastRouteFromLng=fromLng;
                lastRouteMode=mode;
                lastRouteRequestAt=System.currentTimeMillis();
            }catch(Exception ignored){
                // Map still opens after the timeout/fallback and normal rerouting continues.
            }finally{
                prefetchFinished=true;
                mainHandler.post(this::openNavigationMapOnce);
            }
        },"transiva-nav-prefetch").start();
    }

    private void showRoutePreparingScreen(){
        FrameLayout page=new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F3F8FF"));
        TextView t=new TextView(this);
        t.setText("Menyiapkan rute perjalanan…");
        t.setTextColor(Color.parseColor("#0B3A78"));
        t.setTextSize(18);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(24),dp(24),dp(24),dp(24));
        page.addView(t,new FrameLayout.LayoutParams(-1,-1));
        setContentView(page);
    }

    private void openNavigationMapOnce(){
        if(navigationOpened || isFinishing()) return;
        navigationOpened=true;
        buildView();
        startLocationWatch();
    }

    private void applyPrefetchedRoute(){
        if(mapView==null || Build.VERSION.SDK_INT<19) return;
        if(prefetchedRoutePoints==null || prefetchedRoutePoints.length()<4) return;
        final String js="if(window.applyNativeRoute)applyNativeRoute("+
                JSONObject.quote(prefetchedRoutePoints)+","+
                prefetchedRouteKm+","+
                prefetchedRouteSeconds+");";
        try{ mapView.evaluateJavascript(js,null); }catch(Exception ignored){}
    }

    private void buildView(){
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.WHITE);

        mapView = new WebView(this);
        try{
            WebSettings st = mapView.getSettings();
            st.setJavaScriptEnabled(true);
            st.setDomStorageEnabled(true);
            st.setAllowFileAccess(true);
            st.setAllowContentAccess(true);
            if(Build.VERSION.SDK_INT >= 16) st.setAllowFileAccessFromFileURLs(true);
            if(Build.VERSION.SDK_INT >= 16) st.setAllowUniversalAccessFromFileURLs(true);
            if(Build.VERSION.SDK_INT >= 21) st.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }catch(Exception ignored){}
        mapView.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view, String url){
                mainHandler.postDelayed(() -> { applyPrefetchedRoute(); updateMap(); if(prefetchedRoutePoints.isEmpty()) requestStableRoute(true); }, 120);
            }
        });
        mapView.loadDataWithBaseURL("https://transiva.my.id/", fullMapHtml(), "text/html", "UTF-8", null);
        page.addView(mapView, new FrameLayout.LayoutParams(-1, -1));

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(38);
        back.setTextColor(Color.WHITE);
        back.setGravity(Gravity.CENTER);
        back.setBackgroundColor(Color.WHITE);
        back.setTextColor(Color.parseColor("#0B3A78"));
        back.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(52), dp(52));
        bp.leftMargin = dp(14);
        bp.topMargin = dp(18);
        page.addView(back, bp);

        setContentView(page);
    }

    private String fullMapHtml(){
        double pLat = coord("pickup_lat","user_lat"), pLng = coord("pickup_lng","user_lng"), dLat = coord("delivery_lat","destination_lat"), dLng = coord("delivery_lng","destination_lng");
        double cLat = valid(lastDriverLat,lastDriverLng) ? lastDriverLat : (valid(pLat,pLng) ? pLat : (valid(dLat,dLng) ? dLat : -0.9));
        double cLng = valid(lastDriverLat,lastDriverLng) ? lastDriverLng : (valid(pLat,pLng) ? pLng : (valid(dLat,dLng) ? dLng : 119.87));
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>"+
                "<link rel='stylesheet' href='https://transiva.my.id/js/leaflet.css?v=native_route_1'>"+
                "<script src='https://transiva.my.id/js/leaflet.js?v=native_route_1'></script>"+
                "<style>html,body{height:100%;margin:0;background:#EAF4FF;overflow:hidden}#viewport{position:absolute;inset:0;overflow:hidden;background:#EAF4FF}#mapRotator{position:absolute;left:-22%;top:-22%;width:144%;height:144%;transform-origin:50% 50%;transition:transform .22s linear;will-change:transform}#map{height:100%;width:100%}.leaflet-control-attribution,.leaflet-control-zoom{display:none!important}.leaflet-container{font-family:Arial,sans-serif;background:#EAF4FF}.motorWrap{width:64px;height:64px;display:flex;align-items:center;justify-content:center}.motorWrap img{width:62px;height:62px;transform-origin:center center;filter:drop-shadow(0 6px 12px rgba(0,0,0,.35))}.topBadge{position:absolute;z-index:999;top:20px;left:76px;right:14px;background:rgba(255,255,255,.96);border:1px solid #D7E6F8;border-radius:18px;padding:12px 14px;color:#0B3A78;font-size:14px;font-weight:800;box-shadow:0 8px 24px rgba(15,23,42,.18)}.centerDot{position:absolute;z-index:998;left:50%;top:50%;width:8px;height:8px;margin-left:-4px;margin-top:-4px;border-radius:50%;background:#087CFF;box-shadow:0 0 0 10px rgba(8,124,255,.16);pointer-events:none}.speedBadge{position:absolute;z-index:1001;left:16px;bottom:24px;background:rgba(7,20,38,.88);color:white;border-radius:18px;padding:10px 14px;font:800 18px Arial;box-shadow:0 5px 18px rgba(0,0,0,.2)}.speedBadge small{font-size:11px;font-weight:600;opacity:.85;display:block;margin-top:2px}</style></head><body><div id='viewport'><div id='mapRotator'><div id='map'></div></div></div><div id='badge' class='topBadge'>Menyiapkan navigasi...</div><div id='speed' class='speedBadge'>0 km/j<small>Rata-rata 0 km/j</small></div><div class='centerDot'></div><script>"+
                "var mapHeading=0;var pickup=["+pLat+","+pLng+"];var dest=["+dLat+","+dLng+"];var targetMode='"+targetMode+"';var lastDriver=[0,0];var currentPos=null;var currentDeg=0;var routePts=[];var routeLine1=null;var routeLine2=null;var driverMarker=null;var lastRouteKey='';var routeProgress=0;var animToken=0;var vehicleType='"+vehicleType+"';"+
                "var map=L.map('map',{zoomControl:false,attributionControl:false,dragging:false,tap:false,touchZoom:false,doubleClickZoom:false,scrollWheelZoom:false,boxZoom:false,keyboard:false}).setView(["+cLat+","+cLng+"],18);"+
                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:20}).addTo(map);"+
                "var pickupIcon=L.icon({iconUrl:'file:///android_res/drawable/map_pickup_pin.png',iconSize:[58,58],iconAnchor:[29,55]});var destIcon=L.icon({iconUrl:'file:///android_res/drawable/map_destination_pin.png',iconSize:[58,58],iconAnchor:[29,55]});"+
                "function motorIcon(deg){return L.divIcon({className:'',iconSize:[64,64],iconAnchor:[32,32],html:'<div class=\"motorWrap\"><img src=\"file:///android_res/drawable/map_motor_top.png\" style=\"transform:rotate('+deg+'deg)\"></div>'});}"+
                "if(pickup[0]&&pickup[1])L.marker(pickup,{icon:pickupIcon}).addTo(map);if(dest[0]&&dest[1])L.marker(dest,{icon:destIcon}).addTo(map);"+
                "function setBadge(t){var b=document.getElementById('badge');if(b)b.innerHTML=t;}function targetPoint(){return targetMode==='delivery'?dest:pickup;}function rad(x){return x*Math.PI/180;}"+
                "function dist(a,b,c,d){var R=6371000;var p1=rad(a),p2=rad(c),dp=rad(c-a),dl=rad(d-b);var q=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);return R*2*Math.atan2(Math.sqrt(q),Math.sqrt(1-q));}"+
                "function bear(a,b,c,d){var y=Math.sin(rad(d-b))*Math.cos(rad(c));var x=Math.cos(rad(a))*Math.sin(rad(c))-Math.sin(rad(a))*Math.cos(rad(c))*Math.cos(rad(d-b));return (Math.atan2(y,x)*180/Math.PI+360)%360;}function setHeading(deg){if(!isFinite(deg))return;deg=((+deg%360)+360)%360;var diff=deg-mapHeading;if(diff>180)diff-=360;if(diff<-180)diff+=360;mapHeading+=diff;var r=document.getElementById('mapRotator');if(r)r.style.transform='rotate('+(-mapHeading)+'deg)';}function setSpeed(kmh,avg){var e=document.getElementById('speed');if(e)e.innerHTML=Math.round(Math.max(0,+kmh||0))+' km/j<small>Rata-rata '+Math.round(Math.max(0,+avg||0))+' km/j</small>';}window.setSpeed=setSpeed;"+
                "function nearestOnRoute(a,b){if(!routePts||routePts.length<2)return [a,b,currentDeg];var cos=Math.cos(rad(a));if(!isFinite(cos)||Math.abs(cos)<.00001)cos=1;var px=b*cos,py=a,best=[a,b,currentDeg],bd=1e99,bestI=routeProgress;var start=Math.max(0,routeProgress-2),end=Math.min(routePts.length-2,routeProgress+50);for(var i=start;i<=end;i++){var A=routePts[i],B=routePts[i+1],ax=A[1]*cos,ay=A[0],bx=B[1]*cos,by=B[0],vx=bx-ax,vy=by-ay,wx=px-ax,wy=py-ay,len=vx*vx+vy*vy,t=len?((wx*vx+wy*vy)/len):0;if(t<0)t=0;if(t>1)t=1;var qx=ax+vx*t,qy=ay+vy*t,dx=px-qx,dy=py-qy,dd=dx*dx+dy*dy;if(dd<bd){bd=dd;best=[qy,qx/cos,bear(A[0],A[1],B[0],B[1])];bestI=i;}}if(Math.sqrt(bd)*111320>80)return [a,b,currentDeg];if(bestI>=routeProgress)routeProgress=bestI;return best;}"+
                "function animateTo(pos,deg){animToken++;var token=animToken;if(!driverMarker){currentPos=pos;currentDeg=isFinite(deg)?deg:0;driverMarker=L.marker(pos,{icon:motorIcon(currentDeg)}).addTo(map);map.setView(pos,18,{animate:false});return;}var ll=driverMarker.getLatLng(),from=[ll.lat,ll.lng];if(dist(from[0],from[1],pos[0],pos[1])>500)from=pos;var d=dist(from[0],from[1],pos[0],pos[1]);var dur=Math.max(900,Math.min(1700,1050+d*28));var start=performance.now();function step(now){if(token!==animToken)return;var t=Math.min(1,(now-start)/dur);var lat=from[0]+(pos[0]-from[0])*t,lng=from[1]+(pos[1]-from[1])*t;driverMarker.setLatLng([lat,lng]);driverMarker.setIcon(motorIcon(isFinite(deg)?deg:currentDeg));map.panTo([lat,lng],{animate:false});if(t<1)requestAnimationFrame(step);else{currentPos=pos;currentDeg=isFinite(deg)?deg:currentDeg;}}requestAnimationFrame(step);}"+
                "function applyNativeRoute(pts,km,sec){try{if(typeof pts==='string')pts=JSON.parse(pts);if(!pts||pts.length<2)return;routePts=pts;routeProgress=0;if(routeLine1)map.removeLayer(routeLine1);if(routeLine2)map.removeLayer(routeLine2);routeLine1=L.polyline(routePts,{weight:10,opacity:.22,color:'#003B7A',lineCap:'round',lineJoin:'round'}).addTo(map);routeLine2=L.polyline(routePts,{weight:6,opacity:.96,color:'#087CFF',lineCap:'round',lineJoin:'round'}).addTo(map);try{routeLine1.bringToBack();routeLine2.bringToBack();}catch(e){}var mins=Math.max(1,Math.round((+sec||0)/60));setBadge((targetMode==='delivery'?'Menuju tujuan':'Menuju pickup')+' • '+(+km||0).toFixed(1)+' km • '+mins+' menit');if(lastDriver[0]&&lastDriver[1]){var s=nearestOnRoute(lastDriver[0],lastDriver[1]);animateTo([s[0],s[1]],s[2]);}}catch(e){}}"+
                "window.applyNativeRoute=applyNativeRoute;window.routePending=function(){if(routePts.length<2)setBadge(targetMode==='delivery'?'Membuat rute ke tujuan...':'Membuat rute ke pickup...');};"+
                "window.updateDrv=function(a,b,deg){if(!a||!b)return;lastDriver=[a,b];var s=nearestOnRoute(a,b),pos=[s[0],s[1]],finalDeg=isFinite(s[2])?s[2]:(isFinite(deg)?deg:currentDeg);setHeading(finalDeg);animateTo(pos,finalDeg);};"+
                "</script></body></html>";
    }

    private void startLocationWatch(){
        if(Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 801);
            return;
        }
        try{
            locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
            if(locationManager == null) return;
            stopLocationWatch();
            locationListener = new LocationListener(){
                @Override public void onLocationChanged(Location l){
                    SmoothLocationEngine.Fix fix = smoothLocation.offer(l);
                    if(fix == null) return;
                    Location accepted = fix.location;
                    lastAcceptedLocation = new Location(accepted);
                    lastDriverLat = accepted.getLatitude();
                    lastDriverLng = accepted.getLongitude();
                    updateSpeedMetrics(accepted);
                    pushSpeedToMap();
                    if(fix.upload) uploadLocation(accepted);
                    if(fix.render){
                        updateMap();
                        renderedDriverLat = lastDriverLat;
                        renderedDriverLng = lastDriverLng;
                    }
                }
                @Override public void onStatusChanged(String p,int s,Bundle e){}
                @Override public void onProviderEnabled(String p){}
                @Override public void onProviderDisabled(String p){}
            };
            try{ locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 900, 0, locationListener, Looper.getMainLooper()); }catch(Exception ignored){}
            try{ locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1400, 0, locationListener, Looper.getMainLooper()); }catch(Exception ignored){}
            try{
                Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if(last == null) last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if(last != null){ SmoothLocationEngine.Fix fix=smoothLocation.offer(last); if(fix!=null){ Location a=fix.location; lastAcceptedLocation=new Location(a); lastDriverLat=a.getLatitude(); lastDriverLng=a.getLongitude(); updateSpeedMetrics(a); pushSpeedToMap(); updateMap(); renderedDriverLat=lastDriverLat; renderedDriverLng=lastDriverLng; if(fix.upload) uploadLocation(a); }}
            }catch(Exception ignored){}
        }catch(Exception ignored){}
    }

    private void stopLocationWatch(){
        try{ if(locationManager != null && locationListener != null) locationManager.removeUpdates(locationListener); }catch(Exception ignored){}
        locationListener = null;
    }

    

    private void updateSpeedMetrics(Location loc){
        if(loc==null) return;
        double instant=0d;
        if(loc.hasSpeed() && loc.getSpeed()>=0f){
            instant=loc.getSpeed()*3.6d;
        }else if(lastSpeedLocation!=null){
            long dt=loc.getTime()-lastSpeedLocation.getTime();
            if(dt>300L && dt<15000L){
                instant=(lastSpeedLocation.distanceTo(loc)/(dt/1000d))*3.6d;
            }
        }
        if(!Double.isFinite(instant) || instant<0d) instant=0d;
        if(instant>180d) instant=180d;
        // Low-pass speed meter to suppress 0/20/0/20 oscillation at slow GPS speeds.
        currentSpeedKmh = currentSpeedKmh<=0d ? instant : (currentSpeedKmh*0.62d + instant*0.38d);
        if(currentSpeedKmh>=1d){
            speedSampleSum += currentSpeedKmh;
            speedSampleCount++;
            averageSpeedKmh = speedSampleSum / Math.max(1L,speedSampleCount);
        }
        lastSpeedLocation=new Location(loc);
    }

    private void pushSpeedToMap(){
        if(mapView==null || Build.VERSION.SDK_INT<19) return;
        final String js="if(window.setSpeed)setSpeed("+currentSpeedKmh+","+averageSpeedKmh+");";
        mainHandler.post(() -> { try{ mapView.evaluateJavascript(js,null); }catch(Exception ignored){} });
    }

    private void uploadLocation(Location loc){
        if(loc == null) return;
        final double lat = loc.getLatitude(), lng = loc.getLongitude();
        long now = System.currentTimeMillis();
        if(now-lastUpload < LOCATION_INTERVAL) return;
        lastUpload = now;

        new Thread(() -> {
            try{
                JSONObject body = new JSONObject();
                body.put("username",driverUsername);
                body.put("driver",driverUsername);
                body.put("latitude",lat);
                body.put("longitude",lng);
                body.put("driver_type",vehicleType);
                body.put("accuracy", loc.hasAccuracy()?loc.getAccuracy():JSONObject.NULL);
                body.put("speed", currentSpeedKmh);
                body.put("speed_kmh", currentSpeedKmh);
                body.put("average_speed_kmh", averageSpeedKmh);
                body.put("bearing", loc.hasBearing()?loc.getBearing():JSONObject.NULL);
                body.put("location_time", loc.getTime());
                body.put("order_id", first(order.optString("order_id"), order.optString("id"), ""));

                HttpURLConnection c =
                    (HttpURLConnection)new URL(LOCATION_API).openConnection();

                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type","application/json");
                c.setRequestProperty("Accept","application/json");
                try{ String token=session==null?"":session.getToken(); if(token!=null&&!token.trim().isEmpty()) c.setRequestProperty("Authorization","Bearer "+token.trim()); }catch(Exception ignored){}
                c.setDoOutput(true);

                try(OutputStream os=c.getOutputStream()){
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                c.getResponseCode();
                c.disconnect();

            }catch(Exception ignored){}
        }).start();
    }

    private boolean acceptLocation(Location l){
        if(l == null || !valid(l.getLatitude(), l.getLongitude())) return false;
        long now = System.currentTimeMillis();
        if(LocationManager.NETWORK_PROVIDER.equals(l.getProvider()) && now - lastGpsFixAt < GPS_PRIORITY_MS) return false;
        if(lastAcceptedLocation != null){
            long nt=l.getTime(), ot=lastAcceptedLocation.getTime();
            if(nt>0 && ot>0 && nt + 3000L < ot) return false;
            float jump = lastAcceptedLocation.distanceTo(l);
            long dt = Math.max(1L, nt-ot);
            if(jump > 500f && dt < 5000L) return false;
        }
        return true;
    }

    private float distanceBetween(double aLat,double aLng,double bLat,double bLng){
        try{ float[] r=new float[1]; Location.distanceBetween(aLat,aLng,bLat,bLng,r); return r[0]; }catch(Exception e){ return 999f; }
    }

    private void updateMap(){
        try{
            if(mapView == null || Build.VERSION.SDK_INT < 19 || !valid(lastDriverLat,lastDriverLng)) return;
            double deg = 0;
            if(valid(prevDriverLat,prevDriverLng)) deg = bearing(prevDriverLat, prevDriverLng, lastDriverLat, lastDriverLng);
            final String js = "if(window.updateDrv)updateDrv("+lastDriverLat+","+lastDriverLng+","+deg+");";
            mainHandler.post(() -> mapView.evaluateJavascript(js, null));
            requestStableRoute(false);
            prevDriverLat = lastDriverLat;
            prevDriverLng = lastDriverLng;
        }catch(Exception ignored){}
    }

    private void requestStableRoute(boolean force){
        if(mapView == null || !valid(lastDriverLat,lastDriverLng)) return;
        final String mode = routeTargetMode();
        final double toLat = mode.equals("delivery") ? coord("delivery_lat","destination_lat") : coord("pickup_lat","user_lat");
        final double toLng = mode.equals("delivery") ? coord("delivery_lng","destination_lng") : coord("pickup_lng","user_lng");
        if(!valid(toLat,toLng) || routeRequestInFlight) return;
        long now = System.currentTimeMillis();
        float moved = valid(lastRouteFromLat,lastRouteFromLng) ? distanceBetween(lastRouteFromLat,lastRouteFromLng,lastDriverLat,lastDriverLng) : 999f;
        if(!force && mode.equals(lastRouteMode) && moved < 25f && now-lastRouteRequestAt < 15000L) return;
        routeRequestInFlight = true;
        lastRouteRequestAt = now;
        final double fromLat=lastDriverLat, fromLng=lastDriverLng;
        mainHandler.post(() -> { try{ mapView.evaluateJavascript("if(window.routePending)routePending();",null); }catch(Exception ignored){} });
        new Thread(() -> {
            try{
                StableRouteEngine.Result r=StableRouteEngine.fetch(fromLat,fromLng,toLat,toLng);
                lastRouteFromLat=fromLat; lastRouteFromLng=fromLng; lastRouteMode=mode;
                final String pts=r.pointsJson(); final double km=r.distanceMeters/1000d, sec=r.durationSeconds;
                mainHandler.post(() -> { try{ mapView.evaluateJavascript("if(window.applyNativeRoute)applyNativeRoute("+JSONObject.quote(pts)+","+km+","+sec+");",null); }catch(Exception ignored){} });
            }catch(Exception ignored){} finally{ routeRequestInFlight=false; }
        },"transiva-route-nav").start();
    }

    private String routeTargetMode(){
        String st = first(order.optString("status"), "taken").toLowerCase(Locale.US).trim();
        if(st.equals("arrived_pickup") || st.equals("on_delivery") || st.equals("arrived_delivery")) return "delivery";
        return "pickup";
    }

    private double bearing(double lat1, double lng1, double lat2, double lng2){
        double dLng = Math.toRadians(lng2 - lng1);
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);
        double y = Math.sin(dLng) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    private double coord(String a, String b){
        try{ return Double.parseDouble(first(order.optString(a), order.optString(b), "0")); }catch(Exception e){ return 0; }
    }

    private boolean valid(double lat,double lng){ return lat != 0 && lng != 0 && !Double.isNaN(lat) && !Double.isNaN(lng); }
    private String first(String... values){ if(values == null) return ""; for(String s: values) if(s != null && s.trim().length() > 0 && !"null".equalsIgnoreCase(s.trim())) return s.trim(); return ""; }
    private int dp(int v){ return (int)(v * getResources().getDisplayMetrics().density + .5f); }
}