package com.transiva.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.Gravity;

public class AdminDriverTrackerMapActivity extends Activity {
    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.parseColor("#071426"));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.WHITE);
        TextView title=new TextView(this);title.setText("‹  Lacak Driver • "+getIntent().getStringExtra("driver_name"));title.setTextSize(18);title.setTextColor(Color.WHITE);title.setGravity(Gravity.CENTER_VERTICAL);title.setPadding(dp(16),0,dp(12),0);title.setBackgroundColor(Color.parseColor("#0B7CFF"));title.setOnClickListener(v->finish());root.addView(title,new LinearLayout.LayoutParams(-1,dp(58)));
        WebView map=new WebView(this);WebSettings s=map.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);map.setWebViewClient(new WebViewClient());root.addView(map,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        double dlat=getIntent().getDoubleExtra("driver_lat",0),dlng=getIntent().getDoubleExtra("driver_lng",0),plat=getIntent().getDoubleExtra("pickup_lat",0),plng=getIntent().getDoubleExtra("pickup_lng",0),tlat=getIntent().getDoubleExtra("destination_lat",0),tlng=getIntent().getDoubleExtra("destination_lng",0);
        String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'><style>html,body,#m{height:100%;margin:0}.info{position:absolute;z-index:9999;left:12px;right:12px;bottom:18px;background:#fff;padding:12px;border-radius:14px;box-shadow:0 4px 20px #0003;font:13px sans-serif}</style></head><body><div id='m'></div><div class='info'><b>Mode Navigasi Admin</b><br>Posisi driver diperbarui dari server. Garis biru mengikuti rute jalan OSRM.</div><script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script><script>var d=["+dlat+","+dlng+"],p=["+plat+","+plng+"],t=["+tlat+","+tlng+"];var m=L.map('m').setView(d[0]?d:p,15);L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:20,attribution:'© OpenStreetMap'}).addTo(m);var pts=[];function mk(x,n,c){if(x[0]&&x[1]){L.circleMarker(x,{radius:9,color:c,fillColor:c,fillOpacity:1}).addTo(m).bindPopup(n);pts.push(x)}}mk(d,'Driver','#0B7CFF');mk(p,'Titik jemput','#10B981');mk(t,'Tujuan','#EF4444');if(pts.length>1)m.fitBounds(pts,{padding:[40,40]});var route=[];if(d[0])route.push(d);if(p[0])route.push(p);if(t[0])route.push(t);if(route.length>1){var c=route.map(x=>x[1]+','+x[0]).join(';');fetch('https://router.project-osrm.org/route/v1/driving/'+c+'?overview=full&geometries=geojson').then(r=>r.json()).then(j=>{if(j.routes&&j.routes[0])L.geoJSON(j.routes[0].geometry,{style:{color:'#0B7CFF',weight:6,opacity:.85}}).addTo(m)}).catch(()=>L.polyline(route,{color:'#0B7CFF',weight:5}).addTo(m));}</script></body></html>";
        map.loadDataWithBaseURL("https://transiva.my.id/",html,"text/html","UTF-8",null);
    }
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
}
