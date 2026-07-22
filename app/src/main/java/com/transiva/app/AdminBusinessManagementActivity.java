package com.transiva.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AdminBusinessManagementActivity extends Activity {
    private static final String API="https://transiva.my.id/server/admin_business_native.php";
    private WebView map;
    private EditText link,name,category,username,password;
    private TextView coord;
    private Button save;
    private double lat=-0.805480,lng=120.158949;

    @Override protected void onCreate(Bundle s){super.onCreate(s);getWindow().setStatusBarColor(Color.parseColor("#071426"));getWindow().setNavigationBarColor(Color.parseColor("#071426"));setContentView(build());loadMarkers();}
    @Override protected void onDestroy(){if(map!=null){map.destroy();map=null;}super.onDestroy();}

    private View build(){
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(18),dp(16),dp(30));root.setBackgroundColor(Color.parseColor("#F3F8FF"));scroll.addView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);TextView back=text("‹",34,"#0B3A78",true);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout cp=new LinearLayout(this);cp.setOrientation(LinearLayout.VERTICAL);cp.addView(text("Business Merchant",22,"#0B3A78",true));cp.addView(text("Tentukan lokasi dan buat akun restoran",12,"#64748B",false));header.addView(cp,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);gap(root,14);
        LinearLayout banner=new LinearLayout(this);banner.setOrientation(LinearLayout.VERTICAL);banner.setPadding(dp(18),dp(16),dp(18),dp(16));banner.setBackground(gradient());banner.addView(text("📍 Tambah Merchant Baru",18,"#FFFFFF",true));banner.addView(text("Gunakan titik tengah peta sebagai lokasi restoran",12,"#FFFFFF",false));root.addView(banner);gap(root,14);

        LinearLayout linkCard=card();linkCard.addView(text("Pakai Link Google Maps",16,"#0B3A78",true));gap(linkCard,8);LinearLayout row=new LinearLayout(this);link=input("Paste link Google Maps restoran...",false);row.addView(link,new LinearLayout.LayoutParams(0,dp(50),1));Button use=button("Pakai","#0878F9");LinearLayout.LayoutParams ulp=new LinearLayout.LayoutParams(dp(88),dp(50));ulp.setMargins(dp(8),0,0,0);row.addView(use,ulp);use.setOnClickListener(v->useLink());linkCard.addView(row);root.addView(linkCard);gap(root,14);

        map=new WebView(this);map.setBackgroundColor(Color.parseColor("#EAF4FF"));map.setWebViewClient(new WebViewClient());map.setWebChromeClient(new WebChromeClient());WebSettings ws=map.getSettings();ws.setJavaScriptEnabled(true);ws.setDomStorageEnabled(true);map.addJavascriptInterface(new Bridge(),"Android");map.loadDataWithBaseURL("https://transiva.my.id/",html(),"text/html","UTF-8",null);root.addView(map,new LinearLayout.LayoutParams(-1,dp(410)));gap(root,10);
        coord=text("Latitude: -\nLongitude: -",13,"#334155",false);LinearLayout cc=card();cc.addView(text("Titik Tengah Peta",16,"#0B3A78",true));gap(cc,7);cc.addView(coord);root.addView(cc);gap(root,14);

        LinearLayout form=card();form.addView(text("Data Merchant",18,"#0B3A78",true));gap(form,10);name=input("Nama Restoran",false);category=input("Kategori",false);username=input("Username Merchant",false);password=input("Password Merchant",true);form.addView(name);gap(form,8);form.addView(category);gap(form,8);form.addView(username);gap(form,8);form.addView(password);gap(form,12);save=button("Buat Merchant","#0878F9");save.setOnClickListener(v->create());form.addView(save,new LinearLayout.LayoutParams(-1,dp(50)));root.addView(form);
        return scroll;
    }

    private void useLink(){String s=link.getText().toString().trim();double[] c=parse(s);if(c==null){Toast.makeText(this,"Link belum memuat koordinat. Gunakan link panjang Google Maps yang berisi titik lokasi.",Toast.LENGTH_LONG).show();return;}lat=c[0];lng=c[1];map.evaluateJavascript("setCenter("+lat+","+lng+")",null);updateCoord();}
    private double[] parse(String s){try{s=java.net.URLDecoder.decode(s,"UTF-8");Pattern[] p={Pattern.compile("[?&]q=(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)"),Pattern.compile("@(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)"),Pattern.compile("!3d(-?\\d+(?:\\.\\d+)?)!4d(-?\\d+(?:\\.\\d+)?)")};for(Pattern x:p){Matcher m=x.matcher(s);if(m.find())return new double[]{Double.parseDouble(m.group(1)),Double.parseDouble(m.group(2))};}}catch(Exception ignored){}return null;}
    private void updateCoord(){coord.setText(String.format(java.util.Locale.US,"Latitude: %.6f\nLongitude: %.6f",lat,lng));}

    private void loadMarkers(){new Thread(()->{try{JSONObject r=request("GET",null);JSONArray a=r.optJSONArray("businesses");if(a!=null)runOnUiThread(()->map.evaluateJavascript("setMarkers("+JSONObject.quote(a.toString())+")",null));}catch(Exception ignored){}}).start();}
    private void create(){String n=name.getText().toString().trim(),c=category.getText().toString().trim(),u=username.getText().toString().trim(),p=password.getText().toString().trim();if(n.isEmpty()||c.isEmpty()||u.isEmpty()||p.isEmpty()){Toast.makeText(this,"Lengkapi seluruh data merchant",Toast.LENGTH_LONG).show();return;}save.setEnabled(false);save.setText("Menyimpan...");new Thread(()->{try{JSONObject b=new JSONObject().put("action","create").put("name",n).put("category",c).put("username",u).put("password",p).put("latitude",lat).put("longitude",lng);JSONObject r=request("POST",b);runOnUiThread(()->{Toast.makeText(this,r.optString("message","Selesai"),Toast.LENGTH_LONG).show();if(r.optBoolean("success")){name.setText("");category.setText("");username.setText("");password.setText("");link.setText("");loadMarkers();}});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Gagal terhubung ke server",Toast.LENGTH_LONG).show());}finally{runOnUiThread(()->{save.setEnabled(true);save.setText("Buat Merchant");});}}).start();}

    private JSONObject request(String method,JSONObject body)throws Exception{HttpURLConnection h=(HttpURLConnection)new URL(API+("GET".equals(method)?"?t="+System.currentTimeMillis():"")).openConnection();h.setConnectTimeout(15000);h.setReadTimeout(25000);h.setRequestMethod(method);h.setRequestProperty("Accept","application/json");h.setRequestProperty("Content-Type","application/json; charset=UTF-8");String t=new SessionManager(this).getToken().trim();if(!t.isEmpty())h.setRequestProperty("Authorization","Bearer "+t);if(body!=null){h.setDoOutput(true);BufferedWriter w=new BufferedWriter(new OutputStreamWriter(h.getOutputStream(),"UTF-8"));w.write(body.toString());w.close();}InputStream is=h.getResponseCode()<400?h.getInputStream():h.getErrorStream();BufferedReader r=new BufferedReader(new InputStreamReader(is));StringBuilder sb=new StringBuilder();String line;while((line=r.readLine())!=null)sb.append(line);r.close();h.disconnect();return new JSONObject(sb.toString());}

    public class Bridge{@JavascriptInterface public void center(String a,String b){try{lat=Double.parseDouble(a);lng=Double.parseDouble(b);runOnUiThread(()->updateCoord());}catch(Exception ignored){}}}
    private String html(){return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'><link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'><style>html,body,#m{height:100%;margin:0}#pin{position:absolute;z-index:999;left:50%;top:50%;transform:translate(-50%,-100%);font-size:42px;pointer-events:none;filter:drop-shadow(0 5px 7px #555)}</style></head><body><div id='m'></div><div id='pin'>📍</div><script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script><script>var m=L.map('m',{zoomControl:false,attributionControl:false}).setView(["+lat+","+lng+"],16),marks=[];L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:22}).addTo(m);m.on('moveend',function(){var c=m.getCenter();Android.center(String(c.lat),String(c.lng));});function setCenter(a,b){m.setView([a,b],18)}function setMarkers(raw){marks.forEach(x=>m.removeLayer(x));marks=[];try{JSON.parse(raw).forEach(x=>{var a=parseFloat(x.latitude),b=parseFloat(x.longitude);if(a&&b)marks.push(L.marker([a,b]).addTo(m).bindPopup('<b>'+String(x.name||'Merchant')+'</b><br>'+String(x.category||'Restoran')));});}catch(e){}}setTimeout(()=>m.invalidateSize(),300);</script></body></html>";}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(15),dp(15),dp(15),dp(15));c.setBackground(round("#FFFFFF","#D7E6F8",20));c.setElevation(dp(2));return c;}
    private EditText input(String hint,boolean pass){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(14);e.setTextColor(Color.parseColor("#142033"));e.setHintTextColor(Color.parseColor("#9AA6B5"));e.setPadding(dp(14),0,dp(14),0);e.setSingleLine(true);e.setBackground(round("#F9FBFF","#D7E6F8",14));if(pass)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return e;}
    private Button button(String s,String c){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.DEFAULT_BOLD);b.setBackground(round(c,c,15));return b;}
    private TextView text(String s,int z,String c,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(Color.parseColor(c));if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private GradientDrawable round(String f,String s,int r){GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(f));g.setCornerRadius(dp(r));g.setStroke(dp(1),Color.parseColor(s));return g;}
    private GradientDrawable gradient(){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.parseColor("#0878F9"),Color.parseColor("#0B3A78")});g.setCornerRadius(dp(22));return g;}
    private void gap(LinearLayout l,int h){android.widget.Space s=new android.widget.Space(this);l.addView(s,new LinearLayout.LayoutParams(1,dp(h)));}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
