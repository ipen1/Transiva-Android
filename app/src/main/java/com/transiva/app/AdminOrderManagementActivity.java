package com.transiva.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;

public class AdminOrderManagementActivity extends Activity {
    private static final String ENDPOINT="https://transiva.my.id/server/admin_orders_native.php";
    private final Handler main=new Handler(Looper.getMainLooper());
    private LinearLayout ordersBox, mutationBox, summaryBox;
    private ProgressBar loading;
    private String token="", filter="all";
    private final int NAVY=Color.parseColor("#071426"), BG=Color.parseColor("#F3F8FF");

    @Override protected void onCreate(Bundle b){super.onCreate(b);token=new SessionManager(this).getToken().trim();getWindow().setStatusBarColor(NAVY);getWindow().setNavigationBarColor(NAVY);build();load();}
    private void build(){ScrollView sc=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(16),dp(16),dp(28));root.setBackgroundColor(BG);sc.addView(root);setContentView(sc);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=txt("‹",34,"#0B3A78",true);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout ht=new LinearLayout(this);ht.setOrientation(LinearLayout.VERTICAL);ht.addView(txt("Order & Fee",22,"#0B3A78",true));ht.addView(txt("Pantau order berjalan, selesai, dan mutasi fee aplikasi",12,"#64748B",false));head.addView(ht,new LinearLayout.LayoutParams(0,-2,1));root.addView(head);
        summaryBox=verticalCard();root.addView(summaryBox,top(14));
        LinearLayout filters=new LinearLayout(this);String[][] fs={{"Semua","all"},{"Berjalan","active"},{"Selesai","finished"}};for(String[] f:fs){Button x=smallButton(f[0]);x.setOnClickListener(v->{filter=f[1];load();});filters.addView(x,new LinearLayout.LayoutParams(0,dp(44),1));}root.addView(filters,top(12));
        loading=new ProgressBar(this);root.addView(loading,centered(12));root.addView(txt("Daftar Order",18,"#0B3A78",true),top(16));ordersBox=new LinearLayout(this);ordersBox.setOrientation(LinearLayout.VERTICAL);root.addView(ordersBox,top(8));root.addView(txt("Mutasi Fee Aplikasi",18,"#0B3A78",true),top(18));mutationBox=new LinearLayout(this);mutationBox.setOrientation(LinearLayout.VERTICAL);root.addView(mutationBox,top(8));}
    private void load(){loading.setVisibility(android.view.View.VISIBLE);new Thread(()->{try{JSONObject j=request(ENDPOINT+"?filter="+filter+"&v="+System.currentTimeMillis());main.post(()->render(j));}catch(Exception e){main.post(()->{loading.setVisibility(android.view.View.GONE);toast(e.getMessage());});}}).start();}
    private void render(JSONObject j){loading.setVisibility(android.view.View.GONE);if(!j.optBoolean("success")){toast(j.optString("message","Gagal memuat data"));return;}JSONObject s=j.optJSONObject("summary");summaryBox.removeAllViews();summaryBox.addView(txt("Ringkasan",16,"#0B3A78",true));summaryBox.addView(txt("🟢 Berjalan: "+(s==null?0:s.optInt("active"))+"   ✅ Selesai: "+(s==null?0:s.optInt("finished")),13,"#334155",true),top(7));summaryBox.addView(txt("Fee hari ini  "+rp(s==null?0:s.optLong("fee_today")),19,"#087B4B",true),top(8));summaryBox.addView(txt("Total fee tercatat  "+rp(s==null?0:s.optLong("fee_total")),12,"#64748B",false),top(4));
        ordersBox.removeAllViews();JSONArray a=j.optJSONArray("orders");if(a==null||a.length()==0)ordersBox.addView(empty("Belum ada order pada filter ini."));else for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)ordersBox.addView(orderCard(o),top(i==0?0:8));}
        mutationBox.removeAllViews();JSONArray m=j.optJSONArray("fee_mutations");if(m==null||m.length()==0)mutationBox.addView(empty("Belum ada mutasi fee tercatat."));else for(int i=0;i<m.length();i++){JSONObject x=m.optJSONObject(i);if(x!=null)mutationBox.addView(feeCard(x),top(i==0?0:8));}}
    private LinearLayout orderCard(JSONObject o){LinearLayout c=verticalCard();LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);String id=o.optString("order_id","-");r.addView(txt(id,14,"#0B3A78",true),new LinearLayout.LayoutParams(0,-2,1));TextView st=txt(labelStatus(o.optString("status")),11,"#0B7CFF",true);st.setPadding(dp(8),dp(4),dp(8),dp(4));st.setBackground(round("#EAF4FF","#B9DBFF",14));r.addView(st);c.addView(r);c.addView(txt(o.optString("service","Order")+" • "+rp(o.optLong("price")),13,"#0F172A",true),top(7));c.addView(txt("Customer: "+dash(o.optString("customer"))+"\nDriver: "+dash(o.optString("driver")),12,"#475569",false),top(5));c.addView(txt(dash(o.optString("pickup"))+"\n→ "+dash(o.optString("delivery")),11,"#64748B",false),top(6));c.addView(txt(o.optString("created_at",""),10,"#94A3B8",false),top(6));return c;}
    private LinearLayout feeCard(JSONObject x){LinearLayout c=verticalCard();LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.addView(txt("+ "+rp(x.optLong("amount")),16,"#087B4B",true),new LinearLayout.LayoutParams(0,-2,1));r.addView(txt(x.optString("created_at",""),10,"#94A3B8",false));c.addView(r);c.addView(txt("Order "+x.optString("order_id","-")+" • Driver "+dash(x.optString("driver")),12,"#334155",true),top(5));c.addView(txt("Fee layanan "+rp(x.optLong("app_fee"))+"  •  Gross-up "+rp(x.optLong("grossup_fee")),11,"#64748B",false),top(4));return c;}
    private JSONObject request(String u)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setRequestProperty("Accept","application/json");c.setRequestProperty("Authorization","Bearer "+token);InputStream is=c.getResponseCode()<400?c.getInputStream():c.getErrorStream();BufferedReader br=new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line);br.close();c.disconnect();return new JSONObject(sb.toString());}
    private LinearLayout verticalCard(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(14),dp(14),dp(14),dp(14));x.setBackground(round("#FFFFFF","#D7E6F8",18));x.setElevation(dp(2));return x;}private TextView empty(String s){TextView v=txt(s,12,"#64748B",false);v.setPadding(dp(12),dp(18),dp(12),dp(18));v.setGravity(Gravity.CENTER);return v;}private Button smallButton(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.parseColor("#0B3A78"));b.setBackground(round("#FFFFFF","#D7E6F8",14));return b;}private TextView txt(String s,int z,String c,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(Color.parseColor(c));v.setIncludeFontPadding(false);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}private GradientDrawable round(String f,String st,int r){GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(f));g.setCornerRadius(dp(r));g.setStroke(dp(1),Color.parseColor(st));return g;}private LinearLayout.LayoutParams top(int m){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(m),0,0);return p;}private LinearLayout.LayoutParams centered(int m){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.gravity=Gravity.CENTER_HORIZONTAL;p.setMargins(0,dp(m),0,0);return p;}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}private String rp(long n){return NumberFormat.getCurrencyInstance(new Locale("id","ID")).format(n).replace(",00","");}private String dash(String s){return s==null||s.trim().isEmpty()?"-":s.trim();}private String labelStatus(String s){s=s==null?"":s.toLowerCase();if(s.equals("finished")||s.equals("completed"))return "Selesai";if(s.equals("pending"))return "Mencari driver";if(s.equals("arrived_delivery"))return "Tiba tujuan";if(s.equals("on_trip")||s.equals("on_delivery"))return "Dalam perjalanan";if(s.equals("driver_accepted")||s.equals("taken"))return "Diterima driver";return s.replace('_',' ');}private void toast(String s){Toast.makeText(this,s==null?"Terjadi kesalahan":s,Toast.LENGTH_LONG).show();}
}
