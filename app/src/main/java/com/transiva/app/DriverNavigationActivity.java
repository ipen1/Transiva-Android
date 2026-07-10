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

public class DriverNavigationActivity extends Activity {
    private static final String LOCATION_API = "https://transiva.my.id/server/updateDriverLocation.php";
    private static final long LOCATION_INTERVAL = 5000;

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
    private double prevDriverLat = 0, prevDriverLng = 0;
    private boolean routeAnimationAdded = false;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        try{
            getWindow().setStatusBarColor(Color.parseColor("#0B3A78"));
            getWindow().setNavigationBarColor(Color.BLACK);
            if(Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(0);
        }catch(Exception ignored){}

        loadData();
        loadDriverIdentity();
        buildView();
        startLocationWatch();
    }

    @Override protected void onResume(){ super.onResume(); startLocationWatch(); }
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

    private void buildView(){
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#EAF4FF"));

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
        mapView.loadDataWithBaseURL("https://transiva.my.id/", fullMapHtml(), "text/html", "UTF-8", null);
        page.addView(mapView, new FrameLayout.LayoutParams(-1, -1));

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(38);
        back.setTextColor(Color.WHITE);
        back.setGravity(Gravity.CENTER);
        back.setBackgroundColor(Color.parseColor("#0B3A78"));
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
                "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'>"+
                "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>"+
                "<style>html,body,#map{height:100%;margin:0;background:#EAF4FF;overflow:hidden}.leaflet-control-attribution,.leaflet-control-zoom{display:none!important}.leaflet-container{font-family:Arial,sans-serif;background:#EAF4FF}.motorWrap{width:64px;height:64px;display:flex;align-items:center;justify-content:center}.motorWrap img{width:62px;height:62px;transform-origin:center center;filter:drop-shadow(0 6px 12px rgba(0,0,0,.35))}.topBadge{position:absolute;z-index:999;top:20px;left:76px;right:14px;background:rgba(255,255,255,.96);border:1px solid #D7E6F8;border-radius:18px;padding:12px 14px;color:#0B3A78;font-size:14px;font-weight:800;box-shadow:0 8px 24px rgba(15,23,42,.18)}.centerDot{position:absolute;z-index:998;left:50%;top:50%;width:8px;height:8px;margin-left:-4px;margin-top:-4px;border-radius:50%;background:#087CFF;box-shadow:0 0 0 10px rgba(8,124,255,.16);pointer-events:none}</style></head><body><div id='map'></div><div id='badge' class='topBadge'>Menyiapkan navigasi...</div><div id='turnInfo' style='position:absolute;z-index:999;top:82px;left:15px;right:15px;background:white;border-radius:18px;padding:12px;color:#0B3A78;font-size:16px;font-weight:bold;box-shadow:0 6px 20px rgba(0,0,0,.2)'>⬆️ Menyiapkan arah</div><div class='centerDot'></div><script>"+
                "var pickup=["+pLat+","+pLng+"];var dest=["+dLat+","+dLng+"];var targetMode='"+targetMode+"';var lastDriver=[0,0];var currentPos=null;var currentDeg=0;var routePts=[];var routeLine1=null;var routeLine2=null;var driverMarker=null;var lastRouteKey='';"+
                "var map=L.map('map',{zoomControl:false,attributionControl:false,dragging:true,tap:true}).setView(["+cLat+","+cLng+"],18);"+
                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:20}).addTo(map);"+
                "var pickupIcon=L.icon({iconUrl:'file:///android_res/drawable/map_pickup_pin.png',iconSize:[58,58],iconAnchor:[29,55]});var destIcon=L.icon({iconUrl:'file:///android_res/drawable/map_destination_pin.png',iconSize:[58,58],iconAnchor:[29,55]});"+
                "function motorIcon(deg){return L.divIcon({className:'',iconSize:[64,64],iconAnchor:[32,32],html:'<div class=\"motorWrap\"><img src=\"file:///android_res/drawable/map_motor_top.png\" style=\"transform:rotate('+deg+'deg)\"></div>'});}"+
                "if(pickup[0]&&pickup[1])L.marker(pickup,{icon:pickupIcon}).addTo(map);if(dest[0]&&dest[1])L.marker(dest,{icon:destIcon}).addTo(map);"+
                "function setBadge(t){var b=document.getElementById('badge');if(b)b.innerHTML=t;}function targetPoint(){return targetMode==='delivery'?dest:pickup;}function rad(x){return x*Math.PI/180;}"+
                "function dist(a,b,c,d){var R=6371000;var p1=rad(a),p2=rad(c),dp=rad(c-a),dl=rad(d-b);var q=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);return R*2*Math.atan2(Math.sqrt(q),Math.sqrt(1-q));}"+
                "function bear(a,b,c,d){var y=Math.sin(rad(d-b))*Math.cos(rad(c));var x=Math.cos(rad(a))*Math.sin(rad(c))-Math.sin(rad(a))*Math.cos(rad(c))*Math.cos(rad(d-b));return (Math.atan2(y,x)*180/Math.PI+360)%360;}"+
                "function nearestOnRoute(a,b){if(!routePts.length)return [a,b];var best=[a,b],bd=1e12;for(var i=0;i<routePts.length;i++){var p=routePts[i],dd=dist(a,b,p[0],p[1]);if(dd<bd){bd=dd;best=p;}}return bd<=90?best:[a,b];}"+
                "function animateTo(pos,deg){if(!driverMarker){currentPos=pos;currentDeg=deg||0;driverMarker=L.marker(pos,{icon:motorIcon(currentDeg,vehicleType)}).addTo(map);map.setView(pos,18,{animate:false});return;}if(currentPos&&dist(currentPos[0],currentPos[1],pos[0],pos[1])>450){return;}var from=currentPos||driverMarker.getLatLng();from=Array.isArray(from)?from:[from.lat,from.lng];var start=performance.now(),dur=950;function step(now){var t=Math.min(1,(now-start)/dur);var e=t<.5?2*t*t:1-Math.pow(-2*t+2,2)/2;var lat=from[0]+(pos[0]-from[0])*e,lng=from[1]+(pos[1]-from[1])*e;driverMarker.setLatLng([lat,lng]);driverMarker.setIcon(motorIcon(deg||currentDeg,vehicleType));map.panTo([lat,lng],{animate:false});if(t<1)requestAnimationFrame(step);else{currentPos=pos;currentDeg=deg||currentDeg;}}requestAnimationFrame(step);}"+
                "function drawRoute(a,b,force){var t=targetPoint();if(!a||!b||!t[0]||!t[1]){setBadge('Koordinat belum lengkap');return;}var key=a.toFixed(3)+','+b.toFixed(3)+'-'+t[0].toFixed(4)+','+t[1].toFixed(4)+'-'+targetMode;if(!force&&key===lastRouteKey)return;lastRouteKey=key;setBadge(targetMode==='delivery'?'Membuat rute ke tujuan':'Membuat rute ke pickup');fetch('https://router.project-osrm.org/route/v1/driving/'+b+','+a+';'+t[1]+','+t[0]+'?overview=full&geometries=geojson').then(function(r){return r.json();}).then(function(j){if(!j.routes||!j.routes[0])return;routePts=j.routes[0].geometry.coordinates.map(function(x){return[x[1],x[0]];});if(routeLine1)map.removeLayer(routeLine1);if(routeLine2)map.removeLayer(routeLine2);routeLine1=L.polyline(routePts,{weight:10,opacity:.22,color:'#003B7A'}).addTo(map);routeLine2=L.polyline(routePts,{weight:6,opacity:.96,color:'#087CFF'}).addTo(map);animateRoute();var km=(j.routes[0].distance/1000).toFixed(1);var min=Math.round(j.routes[0].duration/60);setBadge((targetMode==='delivery'?'Menuju tujuan':'Menuju pickup')+' • '+km+' km • '+min+' menit');}).catch(function(){setBadge('Rute online gagal dimuat');});}"+
                "function animateRoute(){
 if(!routeLine2)return;
 var offset=0;
 setInterval(function(){
  offset+=2;
  var el=routeLine2.getElement();
  if(el){
   el.style.strokeDasharray='12 12';
   el.style.strokeDashoffset=offset;
  }
 },100);
}
function updateTurnInfo(pos){
 if(!routePts.length)return;
 var idx=0,min=999999;
 for(var i=0;i<routePts.length;i++){
  var d=dist(pos[0],pos[1],routePts[i][0],routePts[i][1]);
  if(d<min){min=d;idx=i;}
 }
 var box=document.getElementById('turnInfo');
 if(!box)return;
 if(idx>routePts.length-10){box.innerHTML='🏁 Mendekati tujuan';return;}
 var a=routePts[idx];
 var b=routePts[Math.min(idx+5,routePts.length-1)];
 var h=bear(a[0],a[1],b[0],b[1]);
 if(h>45&&h<135) box.innerHTML='➡️ Belok kanan';
 else if(h>225&&h<315) box.innerHTML='⬅️ Belok kiri';
 else box.innerHTML='⬆️ Lurus terus';
}

window.updateDrv=function(a,b,deg){if(!a||!b)return;lastDriver=[a,b];drawRoute(a,b,false);var pos=nearestOnRoute(a,b);updateTurnInfo(pos);var finalDeg=deg||currentDeg;if(currentPos)finalDeg=bear(currentPos[0],currentPos[1],pos[0],pos[1]);animateTo(pos,finalDeg);};"+
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
                    if(l == null) return;
                    lastDriverLat = l.getLatitude();
                    lastDriverLng = l.getLongitude();
                    updateMap();
                    uploadLocation(lastDriverLat,lastDriverLng);
                }
                @Override public void onStatusChanged(String p,int s,Bundle e){}
                @Override public void onProviderEnabled(String p){}
                @Override public void onProviderDisabled(String p){}
            };
            try{ locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500, 1, locationListener, Looper.getMainLooper()); }catch(Exception ignored){}
            try{ locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1800, 1, locationListener, Looper.getMainLooper()); }catch(Exception ignored){}
            try{
                Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if(last == null) last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if(last != null){ lastDriverLat = last.getLatitude(); lastDriverLng = last.getLongitude(); updateMap(); }
            }catch(Exception ignored){}
        }catch(Exception ignored){}
    }

    private void stopLocationWatch(){
        try{ if(locationManager != null && locationListener != null) locationManager.removeUpdates(locationListener); }catch(Exception ignored){}
        locationListener = null;
    }

    
    private void uploadLocation(double lat,double lng){
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

                HttpURLConnection c =
                    (HttpURLConnection)new URL(LOCATION_API).openConnection();

                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type","application/json");
                c.setDoOutput(true);

                try(OutputStream os=c.getOutputStream()){
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                c.getResponseCode();
                c.disconnect();

            }catch(Exception ignored){}
        }).start();
    }

    private void updateMap(){
        try{
            if(mapView == null || Build.VERSION.SDK_INT < 19 || !valid(lastDriverLat,lastDriverLng)) return;
            double deg = 0;
            if(valid(prevDriverLat,prevDriverLng)) deg = bearing(prevDriverLat, prevDriverLng, lastDriverLat, lastDriverLng);
            final String js = "if(window.updateDrv)updateDrv("+lastDriverLat+","+lastDriverLng+","+deg+");";
            mainHandler.post(() -> mapView.evaluateJavascript(js, null));
            prevDriverLat = lastDriverLat;
            prevDriverLng = lastDriverLng;
        }catch(Exception ignored){}
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