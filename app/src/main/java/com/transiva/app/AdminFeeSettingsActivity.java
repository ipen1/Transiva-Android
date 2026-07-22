package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminFeeSettingsActivity extends Activity {
    private static final String ENDPOINT = "https://transiva.my.id/server/admin_fee_settings_native.php";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<PricingForm> pricingForms = new ArrayList<>();
    private final List<FeeForm> feeForms = new ArrayList<>();
    private final List<GrossupForm> grossupForms = new ArrayList<>();
    private LinearLayout grossupContainer;
    private ProgressBar progress;
    private Button saveButton;
    private SessionManager session;
    private String token = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#0B7CFF"));
        getWindow().setNavigationBarColor(Color.parseColor("#071426"));
        session = new SessionManager(this);
        token = clean(session.getToken());
        if (!session.isLoggedIn() || !"admin".equals(session.normalizeRole(session.getRole())) || token.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Sesi tidak valid").setMessage("Silakan login kembali sebagai admin.").setCancelable(false).setPositiveButton("Tutup", (d,w)->finish()).show();
            return;
        }
        setContentView(buildScreen());
        loadSettings();
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.parseColor("#F3F8FF"));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(14),dp(16),dp(14),dp(28)); scroll.addView(root);
        TextView back = text("‹  Pengaturan Fee & Harga",22,"#0B3A78",true); back.setPadding(0,dp(4),0,dp(8)); back.setOnClickListener(v->finish()); root.addView(back);
        LinearLayout banner = card(); banner.setBackground(gradient("#0B7CFF","#064FB9",22)); banner.addView(text("⚙ Sistem Harga Transiva",19,"#FFFFFF",true)); banner.addView(text("Atur ongkir, potongan aplikasi, dan gross-up merchant langsung dari admin.",12,"#EAF4FF",false)); root.addView(banner, top(8));
        progress = new ProgressBar(this); root.addView(progress, centered(12));
        root.addView(sectionTitle("Harga Ongkir Berdasarkan Jarak"), top(8));
        root.addView(text("Minimum ongkir berlaku sampai Base KM. Setelah itu tarif memakai harga per KM tahap 1 dan tahap 2.",11,"#64748B",false));
        root.addView(pricingCard("Transbike"), top(8)); root.addView(pricingCard("Transcar"), top(10));
        root.addView(sectionTitle("Potongan Aplikasi"), top(16));
        root.addView(text("Pilih persen dari ongkir atau nominal tetap yang dipotong saat order selesai.",11,"#64748B",false));
        root.addView(feeCard("Transbike"), top(8)); root.addView(feeCard("Transcar"), top(10));
        root.addView(sectionTitle("Gross-up Harga Merchant"), top(16));
        root.addView(text("Tambahan harga aplikasi berdasarkan rentang harga asli menu merchant.",11,"#64748B",false));
        grossupContainer = new LinearLayout(this); grossupContainer.setOrientation(LinearLayout.VERTICAL); root.addView(grossupContainer);
        Button add = secondaryButton("+ Tambah Rentang Gross-up"); add.setOnClickListener(v->addGrossup(null)); root.addView(add, top(10));
        saveButton = primaryButton("Simpan Semua Pengaturan"); saveButton.setOnClickListener(v->saveSettings()); root.addView(saveButton, top(18));
        return scroll;
    }

    private LinearLayout pricingCard(String service) {
        LinearLayout box=card(); box.addView(text(service,16,"#0B3A78",true));
        PricingForm f=new PricingForm(service); f.min=input("Minimum ongkir (Rp)",true); f.base=input("Base KM",false); f.mid=input("Batas KM tahap 1",false); f.midPrice=input("Harga/KM tahap 1 (Rp)",true); f.nextPrice=input("Harga/KM tahap 2 (Rp)",true);
        box.addView(f.min,top(8)); box.addView(row(f.base,f.mid),top(7)); box.addView(row(f.midPrice,f.nextPrice),top(7)); pricingForms.add(f); return box;
    }

    private LinearLayout feeCard(String service) {
        LinearLayout box=card(); box.addView(text(service,16,"#0B3A78",true)); FeeForm f=new FeeForm(service);
        f.type=new Spinner(this); f.type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Persen (%)","Nominal tetap (Rp)"})); f.type.setBackground(round("#F9FBFF","#D7E6F8",14));
        f.value=input("Nilai potongan",false); box.addView(f.type,new LinearLayout.LayoutParams(-1,dp(48))); box.addView(f.value,top(7)); feeForms.add(f); return box;
    }

    private void addGrossup(JSONObject data) {
        GrossupForm f=new GrossupForm(); LinearLayout box=card();
        LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL); head.addView(text("Rentang Gross-up",15,"#0B3A78",true),new LinearLayout.LayoutParams(0,-2,1));
        TextView del=text("Hapus",12,"#D92D20",true); head.addView(del); box.addView(head);
        f.min=input("Harga minimum (Rp)",true); f.max=input("Harga maksimum; kosong = tanpa batas",true); f.fee=input("Tambahan gross-up (Rp)",true);
        box.addView(row(f.min,f.max),top(8)); box.addView(f.fee,top(7));
        del.setOnClickListener(v->{grossupForms.remove(f);grossupContainer.removeView(box);});
        if(data!=null){f.min.setText(data.optString("min_amount","0")); if(!data.isNull("max_amount"))f.max.setText(data.optString("max_amount","")); f.fee.setText(data.optString("fee","0"));}
        grossupForms.add(f); grossupContainer.addView(box,top(8));
    }

    private void loadSettings(){setLoading(true);new Thread(()->{try{JSONObject j=request("GET",null);main.post(()->{if(!j.optBoolean("success")){toast(j.optString("message","Gagal memuat"));setLoading(false);return;}applyData(j);setLoading(false);});}catch(Exception e){main.post(()->{toast("Gagal terhubung: "+e.getMessage());setLoading(false);});}}).start();}
    private void applyData(JSONObject j){JSONArray p=j.optJSONArray("pricing");if(p!=null)for(int i=0;i<p.length();i++){JSONObject r=p.optJSONObject(i);PricingForm f=findPricing(r.optString("service_type"));if(f!=null){f.min.setText(r.optString("min_price"));f.base.setText(r.optString("base_km"));f.mid.setText(r.optString("mid_to_km"));f.midPrice.setText(r.optString("mid_price_per_km"));f.nextPrice.setText(r.optString("next_price_per_km"));}}
        JSONArray fees=j.optJSONArray("service_fees");if(fees!=null)for(int i=0;i<fees.length();i++){JSONObject r=fees.optJSONObject(i);FeeForm f=findFee(r.optString("service_type"));if(f!=null){f.type.setSelection("fixed".equalsIgnoreCase(r.optString("fee_type"))?1:0);f.value.setText(r.optString("fee_value"));}}
        grossupContainer.removeAllViews();grossupForms.clear();JSONArray g=j.optJSONArray("grossup_rules");if(g!=null&&g.length()>0)for(int i=0;i<g.length();i++)addGrossup(g.optJSONObject(i));else addGrossup(null);
    }

    private void saveSettings(){try{JSONObject body=new JSONObject();JSONArray p=new JSONArray();for(PricingForm f:pricingForms){validate(f.min,"Minimum ongkir");validate(f.base,"Base KM");validate(f.mid,"Batas KM");validate(f.midPrice,"Harga/KM tahap 1");validate(f.nextPrice,"Harga/KM tahap 2");JSONObject r=new JSONObject();r.put("service_type",f.service);r.put("min_price",number(f.min));r.put("base_km",decimal(f.base));r.put("mid_to_km",decimal(f.mid));r.put("mid_price_per_km",number(f.midPrice));r.put("next_price_per_km",number(f.nextPrice));p.put(r);}body.put("pricing",p);
        JSONArray fs=new JSONArray();for(FeeForm f:feeForms){validate(f.value,"Nilai potongan");JSONObject r=new JSONObject();r.put("service_type",f.service);r.put("fee_type",f.type.getSelectedItemPosition()==1?"fixed":"percent");r.put("fee_value",decimal(f.value));fs.put(r);}body.put("service_fees",fs);
        JSONArray gs=new JSONArray();for(GrossupForm f:grossupForms){validate(f.min,"Harga minimum");validate(f.fee,"Gross-up");JSONObject r=new JSONObject();r.put("min_amount",number(f.min));String max=clean(f.max.getText().toString());if(max.isEmpty())r.put("max_amount",JSONObject.NULL);else r.put("max_amount",parseLong(max));r.put("fee",number(f.fee));gs.put(r);}body.put("grossup_rules",gs);
        setLoading(true);new Thread(()->{try{JSONObject j=request("POST",body);main.post(()->{toast(j.optString("message",j.optBoolean("success")?"Tersimpan":"Gagal"));setLoading(false);if(j.optBoolean("success"))applyData(j);});}catch(Exception e){main.post(()->{toast("Gagal menyimpan: "+e.getMessage());setLoading(false);});}}).start();
    }catch(Exception e){toast(e.getMessage());}}

    private JSONObject request(String method,JSONObject body)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection();c.setRequestMethod(method);c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setRequestProperty("Accept","application/json");c.setRequestProperty("Authorization","Bearer "+token);if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream os=c.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}}int code=c.getResponseCode();InputStream is=code>=200&&code<400?c.getInputStream():c.getErrorStream();BufferedReader br=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();String line;while((line=br.readLine())!=null)s.append(line);br.close();c.disconnect();return new JSONObject(s.toString());}
    private void setLoading(boolean b){progress.setVisibility(b?View.VISIBLE:View.GONE);if(saveButton!=null)saveButton.setEnabled(!b);}
    private PricingForm findPricing(String s){for(PricingForm f:pricingForms)if(f.service.equalsIgnoreCase(s))return f;return null;} private FeeForm findFee(String s){for(FeeForm f:feeForms)if(f.service.equalsIgnoreCase(s))return f;return null;}
    private void validate(EditText e,String name){if(clean(e.getText().toString()).isEmpty()){e.requestFocus();throw new IllegalArgumentException(name+" wajib diisi");}} private long number(EditText e){return parseLong(e.getText().toString());} private double decimal(EditText e){return Double.parseDouble(clean(e.getText().toString()).replace(",","."));} private long parseLong(String s){return Long.parseLong(clean(s).replace(".","").replace(",",""));}
    private EditText input(String hint,boolean integer){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(13);e.setSingleLine(true);e.setPadding(dp(12),0,dp(12),0);e.setInputType(integer?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);e.setBackground(round("#F9FBFF","#D7E6F8",14));e.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(48)));return e;}
    private LinearLayout row(View a,View b){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.addView(a,new LinearLayout.LayoutParams(0,dp(48),1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);lp.setMargins(dp(7),0,0,0);r.addView(b,lp);return r;}
    private LinearLayout card(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(14),dp(14),dp(14),dp(14));x.setBackground(round("#FFFFFF","#D7E6F8",20));x.setElevation(dp(2));return x;}
    private TextView sectionTitle(String s){return text(s,18,"#0B3A78",true);} private TextView text(String s,int z,String c,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(Color.parseColor(c));v.setIncludeFontPadding(false);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private Button primaryButton(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.DEFAULT_BOLD);b.setBackground(gradient("#0B7CFF","#064FB9",16));b.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(50)));return b;} private Button secondaryButton(String s){Button b=primaryButton(s);b.setTextColor(Color.parseColor("#0B7CFF"));b.setBackground(round("#EAF4FF","#8EC5FF",16));return b;}
    private GradientDrawable round(String fill,String stroke,int radius){GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(fill));g.setCornerRadius(dp(radius));g.setStroke(dp(1),Color.parseColor(stroke));return g;} private GradientDrawable gradient(String a,String b,int radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.parseColor(a),Color.parseColor(b)});g.setCornerRadius(dp(radius));return g;}
    private LinearLayout.LayoutParams top(int m){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(m),0,0);return p;} private LinearLayout.LayoutParams centered(int m){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.gravity=Gravity.CENTER_HORIZONTAL;p.setMargins(0,dp(m),0,0);return p;} private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);} private String clean(Object x){String s=x==null?"":String.valueOf(x).trim();return "null".equalsIgnoreCase(s)||"undefined".equalsIgnoreCase(s)?"":s;} private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    static class PricingForm{final String service;EditText min,base,mid,midPrice,nextPrice;PricingForm(String s){service=s;}} static class FeeForm{final String service;Spinner type;EditText value;FeeForm(String s){service=s;}} static class GrossupForm{EditText min,max,fee;}
}
