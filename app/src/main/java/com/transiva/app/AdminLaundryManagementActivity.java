package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AdminLaundryManagementActivity extends Activity implements LocationListener {
    private static final String API = "https://transiva.my.id/server/admin_laundry_native.php";
    private static final String RESOLVE_API = "https://transiva.my.id/server/resolve_google_maps.php";
    private static final int REQ_LOCATION = 9101;

    private WebView map;
    private EditText link, name, price, address;
    private TextView coord;
    private LinearLayout listBox;
    private Button save, lockButton;
    private double lat = -0.805480, lng = 120.158949;
    private boolean autoLock = false;
    private LocationManager locationManager;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.parseColor("#071426"));
        getWindow().setNavigationBarColor(Color.parseColor("#071426"));
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        setContentView(build());
        loadLaundries();
    }

    @Override protected void onDestroy() {
        stopAutoLock();
        if (map != null) { map.destroy(); map = null; }
        super.onDestroy();
    }

    private View build() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(30));
        root.setBackgroundColor(Color.parseColor("#F3F8FF"));
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 34, "#0B3A78", true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text("TransLaundry", 22, "#0B3A78", true));
        copy.addView(text("Kelola lokasi dan layanan laundry", 12, "#64748B", false));
        header.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header); gap(root, 14);

        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(dp(18), dp(16), dp(18), dp(16));
        banner.setBackground(gradient());
        banner.addView(text("🧺 Tambah Laundry Baru", 18, "#FFFFFF", true));
        banner.addView(text("Tentukan titik laundry lalu simpan data layanan", 12, "#FFFFFF", false));
        root.addView(banner); gap(root, 14);

        LinearLayout linkCard = card();
        linkCard.addView(text("Pakai Link Google Maps", 16, "#0B3A78", true)); gap(linkCard, 8);
        LinearLayout linkRow = new LinearLayout(this);
        link = input("Paste link Google Maps laundry...", false, false);
        linkRow.addView(link, new LinearLayout.LayoutParams(0, dp(50), 1));
        Button use = button("Pakai", "#0878F9");
        LinearLayout.LayoutParams useLp = new LinearLayout.LayoutParams(dp(88), dp(50)); useLp.setMargins(dp(8), 0, 0, 0);
        linkRow.addView(use, useLp); use.setOnClickListener(v -> useLink());
        linkCard.addView(linkRow); root.addView(linkCard); gap(root, 14);

        map = new WebView(this);
        map.setBackgroundColor(Color.parseColor("#EAF4FF"));
        map.setWebViewClient(new WebViewClient()); map.setWebChromeClient(new WebChromeClient());
        WebSettings ws = map.getSettings(); ws.setJavaScriptEnabled(true); ws.setDomStorageEnabled(true);
        map.addJavascriptInterface(new Bridge(), "Android");
        map.loadDataWithBaseURL("https://transiva.my.id/", html(), "text/html", "UTF-8", null);
        root.addView(map, new LinearLayout.LayoutParams(-1, dp(410)));

        lockButton = button("Lock Posisi", "#16A34A");
        lockButton.setOnClickListener(v -> toggleAutoLock());
        LinearLayout.LayoutParams lockLp = new LinearLayout.LayoutParams(-1, dp(48)); lockLp.setMargins(0, dp(10), 0, 0);
        root.addView(lockButton, lockLp); gap(root, 12);

        LinearLayout cc = card();
        cc.addView(text("Titik Tengah Peta", 16, "#0B3A78", true)); gap(cc, 7);
        coord = text("Latitude: -\nLongitude: -", 13, "#334155", false); cc.addView(coord);
        root.addView(cc); gap(root, 14); updateCoord();

        LinearLayout form = card(); form.addView(text("Data Laundry", 18, "#0B3A78", true)); gap(form, 10);
        name = input("Nama Laundry", false, false);
        price = input("Harga Laundry Mulai", false, true);
        address = input("Alamat / Keterangan Lokasi", false, false);
        form.addView(name); gap(form, 8); form.addView(price); gap(form, 8); form.addView(address); gap(form, 12);
        save = button("Simpan Laundry", "#0878F9"); save.setOnClickListener(v -> createLaundry());
        form.addView(save, new LinearLayout.LayoutParams(-1, dp(50))); root.addView(form); gap(root, 14);

        LinearLayout listCard = card(); listCard.addView(text("Daftar Laundry", 18, "#0B3A78", true)); gap(listCard, 10);
        listBox = new LinearLayout(this); listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.addView(text("Memuat laundry...", 13, "#64748B", false)); listCard.addView(listBox); root.addView(listCard);
        return scroll;
    }

    private void useLink() {
        String value = link.getText().toString().trim();
        if (value.isEmpty()) { toast("Masukkan link Google Maps laundry terlebih dahulu"); return; }
        double[] direct = parseCoords(value);
        if (direct != null) { setMapCenter(direct[0], direct[1]); return; }
        new Thread(() -> {
            try {
                JSONObject b = new JSONObject().put("url", value);
                JSONObject r = requestUrl(RESOLVE_API, "POST", b, false);
                double[] resolved = parseCoords(r.optString("url", ""));
                runOnUiThread(() -> { if (resolved != null) setMapCenter(resolved[0], resolved[1]); else toast("Link Google Maps tidak bisa dibaca"); });
            } catch (Exception e) { runOnUiThread(() -> toast("Gagal membaca link Google Maps")); }
        }).start();
    }

    private double[] parseCoords(String value) {
        try {
            String s = URLDecoder.decode(value, "UTF-8");
            Pattern[] patterns = new Pattern[]{
                    Pattern.compile("[?&]q=(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)"),
                    Pattern.compile("@(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)"),
                    Pattern.compile("!3d(-?\\d+(?:\\.\\d+)?)!4d(-?\\d+(?:\\.\\d+)?)"),
                    Pattern.compile("(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)")
            };
            for (Pattern p : patterns) { Matcher m = p.matcher(s); if (m.find()) return new double[]{Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2))}; }
        } catch (Exception ignored) {}
        return null;
    }

    private void setMapCenter(double a, double b) {
        lat = a; lng = b; updateCoord();
        if (map != null) map.evaluateJavascript("setCenter(" + lat + "," + lng + ")", null);
        toast("Lokasi laundry berhasil digunakan");
    }

    private void toggleAutoLock() { if (autoLock) stopAutoLock(); else startAutoLock(); }
    private void startAutoLock() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION); return;
        }
        try {
            autoLock = true; lockButton.setText("Unlock Posisi"); lockButton.setBackground(round("#DC2626", "#DC2626", 15));
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1500L, 1f, this);
        } catch (Exception e) { stopAutoLock(); toast("Gagal mengaktifkan GPS"); }
    }
    private void stopAutoLock() {
        autoLock = false;
        try { if (locationManager != null) locationManager.removeUpdates(this); } catch (Exception ignored) {}
        if (lockButton != null) { lockButton.setText("Lock Posisi"); lockButton.setBackground(round("#16A34A", "#16A34A", 15)); }
    }
    @Override public void onLocationChanged(Location location) { if (!autoLock || location == null) return; lat = location.getLatitude(); lng = location.getLongitude(); updateCoord(); if (map != null) map.evaluateJavascript("setCenter("+lat+","+lng+");setMy("+lat+","+lng+")", null); }
    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) { super.onRequestPermissionsResult(r,p,g); if (r==REQ_LOCATION && g.length>0 && g[0]==PackageManager.PERMISSION_GRANTED) startAutoLock(); }

    private void createLaundry() {
        String n = name.getText().toString().trim(), p = price.getText().toString().trim(), a = address.getText().toString().trim();
        if (n.isEmpty()) { toast("Nama laundry wajib diisi"); return; }
        int amount = 0; try { amount = p.isEmpty()?0:Integer.parseInt(p); } catch(Exception e){ toast("Harga laundry tidak valid"); return; }
        final int finalAmount = Math.max(0, amount);
        save.setEnabled(false); save.setText("Menyimpan...");
        new Thread(() -> {
            try {
                JSONObject b = new JSONObject().put("action","create").put("name",n).put("price",finalAmount).put("address",a).put("latitude",lat).put("longitude",lng);
                JSONObject r = request("POST", b);
                runOnUiThread(() -> { toast(r.optString("message","Selesai")); if(r.optBoolean("success")){ name.setText("");price.setText("");address.setText("");link.setText("");loadLaundries(); } });
            } catch(Exception e){ runOnUiThread(() -> toast("Gagal terhubung ke server")); }
            finally { runOnUiThread(() -> { save.setEnabled(true); save.setText("Simpan Laundry"); }); }
        }).start();
    }

    private void loadLaundries() {
        if (listBox != null) { listBox.removeAllViews(); listBox.addView(text("Memuat laundry...",13,"#64748B",false)); }
        new Thread(() -> {
            try {
                JSONObject r = request("GET", null); JSONArray a = r.optJSONArray("laundries");
                runOnUiThread(() -> renderList(a));
            } catch(Exception e){ runOnUiThread(() -> { if(listBox!=null){listBox.removeAllViews();listBox.addView(text("Gagal memuat laundry",13,"#DC2626",false));} }); }
        }).start();
    }

    private void renderList(JSONArray items) {
        if (listBox == null) return; listBox.removeAllViews();
        if (items == null || items.length()==0) { listBox.addView(text("Belum ada laundry",13,"#64748B",false)); setMarkers(new JSONArray()); return; }
        for(int i=0;i<items.length();i++){
            JSONObject o=items.optJSONObject(i); if(o==null)continue;
            LinearLayout item=card(); item.setElevation(0); item.addView(text(o.optString("name","Laundry"),16,"#0B3A78",true));
            item.addView(text(formatMoney(o.optDouble("price",0))+"\n"+o.optString("address","-")+"\n"+o.optString("latitude","-")+", "+o.optString("longitude","-"),12,"#64748B",false)); gap(item,9);
            Button focus=button("Lihat di Map","#0878F9"); double a=o.optDouble("latitude",0),b=o.optDouble("longitude",0); focus.setOnClickListener(v->setMapCenter(a,b)); item.addView(focus,new LinearLayout.LayoutParams(-1,dp(44))); gap(item,7);
            boolean active=o.optInt("is_active",0)==1; Button toggle=button(active?"Nonaktifkan":"Aktifkan",active?"#64748B":"#16A34A"); int id=o.optInt("id",0); toggle.setOnClickListener(v->toggleStatus(id,active?0:1)); item.addView(toggle,new LinearLayout.LayoutParams(-1,dp(44)));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,dp(10)); listBox.addView(item,lp);
        }
        setMarkers(items);
    }

    private void toggleStatus(int id,int active){new Thread(()->{try{JSONObject r=request("POST",new JSONObject().put("action","status").put("id",id).put("is_active",active));runOnUiThread(()->{toast(r.optString("message","Selesai"));loadLaundries();});}catch(Exception e){runOnUiThread(()->toast("Gagal memperbarui status"));}}).start();}
    private void setMarkers(JSONArray a){if(map!=null)map.evaluateJavascript("setMarkers("+JSONObject.quote(a==null?"[]":a.toString())+")",null);}
    private void updateCoord(){if(coord!=null)coord.setText(String.format(Locale.US,"Latitude: %.6f\nLongitude: %.6f",lat,lng));}

    private JSONObject request(String method,JSONObject body)throws Exception{return requestUrl(API+("GET".equals(method)?"?t="+System.currentTimeMillis():""),method,body,true);}
    private JSONObject requestUrl(String url,String method,JSONObject body,boolean auth)throws Exception{
        HttpURLConnection h=(HttpURLConnection)new URL(url).openConnection();h.setConnectTimeout(15000);h.setReadTimeout(25000);h.setRequestMethod(method);h.setRequestProperty("Accept","application/json");h.setRequestProperty("Content-Type","application/json; charset=UTF-8");
        if(auth){String t=new SessionManager(this).getToken().trim();if(!t.isEmpty())h.setRequestProperty("Authorization","Bearer "+t);} if(body!=null){h.setDoOutput(true);BufferedWriter w=new BufferedWriter(new OutputStreamWriter(h.getOutputStream(),"UTF-8"));w.write(body.toString());w.close();}
        InputStream is=h.getResponseCode()<400?h.getInputStream():h.getErrorStream();BufferedReader r=new BufferedReader(new InputStreamReader(is));StringBuilder sb=new StringBuilder();String line;while((line=r.readLine())!=null)sb.append(line);r.close();h.disconnect();return new JSONObject(sb.toString());
    }

    public class Bridge { @JavascriptInterface public void center(String a,String b){try{lat=Double.parseDouble(a);lng=Double.parseDouble(b);runOnUiThread(AdminLaundryManagementActivity.this::updateCoord);}catch(Exception ignored){}} @JavascriptInterface public void drag(){runOnUiThread(()->{if(autoLock)stopAutoLock();});} }
    private String html(){return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'><link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'><style>html,body,#m{height:100%;margin:0}#pin{position:absolute;z-index:999;left:50%;top:50%;transform:translate(-50%,-100%);font-size:42px;pointer-events:none;filter:drop-shadow(0 5px 7px #555)}</style></head><body><div id='m'></div><div id='pin'>📍</div><script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script><script>var m=L.map('m',{zoomControl:false,attributionControl:false}).setView(["+lat+","+lng+"],16),marks=[],me=null;L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:22}).addTo(m);m.on('dragstart',function(){Android.drag();});m.on('moveend',function(){var c=m.getCenter();Android.center(String(c.lat),String(c.lng));});function setCenter(a,b){m.setView([a,b],18)}function setMy(a,b){if(me)me.setLatLng([a,b]);else me=L.circleMarker([a,b],{radius:8,weight:3,color:'#fff',fillColor:'#0878F9',fillOpacity:1}).addTo(m)}function esc(s){return String(s||'').replace(/[&<>\"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;',\"'\":'&#39;'}[c]})}function setMarkers(raw){marks.forEach(x=>m.removeLayer(x));marks=[];try{JSON.parse(raw).forEach(x=>{var a=parseFloat(x.latitude),b=parseFloat(x.longitude);if(a&&b)marks.push(L.marker([a,b]).addTo(m).bindPopup('<b>'+esc(x.name||'Laundry')+'</b><br>Rp '+Number(x.price||0).toLocaleString('id-ID')+'<br>'+esc(x.address||'-')));});}catch(e){}}setTimeout(()=>m.invalidateSize(),300);</script></body></html>";}

    private String formatMoney(double v){return "Rp "+String.format(Locale.US,"%,.0f",v).replace(',', '.');}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(15),dp(15),dp(15),dp(15));c.setBackground(round("#FFFFFF","#D7E6F8",20));c.setElevation(dp(2));return c;}
    private EditText input(String hint,boolean pass,boolean number){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(14);e.setTextColor(Color.parseColor("#142033"));e.setHintTextColor(Color.parseColor("#9AA6B5"));e.setPadding(dp(14),0,dp(14),0);e.setSingleLine(true);e.setBackground(round("#F9FBFF","#D7E6F8",14));if(pass)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);else if(number)e.setInputType(InputType.TYPE_CLASS_NUMBER);return e;}
    private Button button(String s,String c){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.DEFAULT_BOLD);b.setBackground(round(c,c,15));return b;}
    private TextView text(String s,int z,String c,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(Color.parseColor(c));if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private GradientDrawable round(String f,String s,int r){GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(f));g.setCornerRadius(dp(r));g.setStroke(dp(1),Color.parseColor(s));return g;}
    private GradientDrawable gradient(){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.parseColor("#0878F9"),Color.parseColor("#0B3A78")});g.setCornerRadius(dp(22));return g;}
    private void gap(LinearLayout l,int h){android.widget.Space s=new android.widget.Space(this);l.addView(s,new LinearLayout.LayoutParams(1,dp(h)));}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
