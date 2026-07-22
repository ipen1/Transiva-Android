package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
import java.text.NumberFormat;
import java.util.Locale;

public class AdminMerchantManagementActivity extends Activity {
    private static final String API = "https://transiva.my.id/server/admin_merchants_native.php";
    private LinearLayout listBox;
    private ProgressBar loading;
    private boolean busy;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.parseColor("#071426"));
        getWindow().setNavigationBarColor(Color.parseColor("#071426"));
        setContentView(build());
        load();
    }

    private View build() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        root.setBackgroundColor(Color.parseColor("#F3F8FF"));
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 34, "#0B3A78", true); back.setGravity(Gravity.CENTER); back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text("Daftar Merchant", 22, "#0B3A78", true));
        copy.addView(text("Kelola akun, restoran, saldo, dan menu", 12, "#64748B", false));
        header.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView refresh = text("⟳", 27, "#0B7CFF", true); refresh.setGravity(Gravity.CENTER); refresh.setOnClickListener(v -> load());
        header.addView(refresh, new LinearLayout.LayoutParams(dp(44), dp(44)));
        root.addView(header); gap(root, 14);

        LinearLayout banner = new LinearLayout(this); banner.setOrientation(LinearLayout.VERTICAL); banner.setPadding(dp(18),dp(16),dp(18),dp(16)); banner.setBackground(gradient());
        banner.addView(text("🏪 Semua Merchant", 18, "#FFFFFF", true));
        banner.addView(text("Data merchant tersinkron langsung dengan server", 12, "#FFFFFF", false));
        root.addView(banner); gap(root, 14);

        loading = new ProgressBar(this); loading.setVisibility(View.GONE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(38),dp(38)); lp.gravity=Gravity.CENTER; root.addView(loading, lp);
        listBox = new LinearLayout(this); listBox.setOrientation(LinearLayout.VERTICAL); root.addView(listBox);
        return scroll;
    }

    private void load() {
        if (busy) return; busy=true; loading.setVisibility(View.VISIBLE); listBox.removeAllViews();
        new Thread(() -> {
            try {
                JSONObject res = request("GET", null);
                runOnUiThread(() -> {
                    if (!res.optBoolean("success")) { empty(res.optString("message", "Gagal memuat merchant")); return; }
                    JSONArray rows = res.optJSONArray("merchants");
                    if (rows == null || rows.length()==0) { empty("Belum ada merchant"); return; }
                    for (int i=0;i<rows.length();i++) addMerchant(rows.optJSONObject(i));
                });
            } catch(Exception e) { runOnUiThread(() -> empty("Gagal terhubung ke server")); }
            finally { runOnUiThread(() -> {busy=false; loading.setVisibility(View.GONE);}); }
        }).start();
    }

    private void addMerchant(JSONObject item) {
        if(item==null)return;
        int userId=item.optInt("user_id", item.optInt("id",0));
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16),dp(15),dp(16),dp(15)); card.setBackground(round("#FFFFFF","#D7E6F8",21)); card.setElevation(dp(2));
        card.addView(text("🏪 " + item.optString("restaurant_name","Restoran belum diatur"),18,"#0B3A78",true)); gap(card,8);
        card.addView(text("Username:  " + item.optString("username","-"),13,"#334155",false));
        card.addView(text("Saldo:  " + money(item.optDouble("balance",0)),13,"#334155",true));
        card.addView(text("Kategori:  " + item.optString("category","-"),13,"#64748B",false));
        card.addView(text("Lokasi:  " + item.optString("latitude","-") + ", " + item.optString("longitude","-"),12,"#64748B",false)); gap(card,12);
        Button del=button("Hapus Merchant", "#DC2626");
        del.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Hapus Merchant").setMessage("Akun merchant, restoran, dan seluruh menu akan ikut terhapus. Lanjutkan?").setNegativeButton("Batal",null).setPositiveButton("Hapus",(d,w)->delete(userId,del)).show());
        card.addView(del,new LinearLayout.LayoutParams(-1,dp(48)));
        listBox.addView(card); gap(listBox,12);
    }

    private void delete(int id, Button btn) {
        btn.setEnabled(false); btn.setText("Menghapus...");
        new Thread(() -> {
            try { JSONObject body=new JSONObject().put("action","delete").put("user_id",id); JSONObject res=request("POST",body);
                runOnUiThread(() -> { Toast.makeText(this,res.optString("message","Selesai"),Toast.LENGTH_LONG).show(); if(res.optBoolean("success")) load(); else {btn.setEnabled(true);btn.setText("Hapus Merchant");} });
            } catch(Exception e){runOnUiThread(() -> {btn.setEnabled(true);btn.setText("Hapus Merchant");Toast.makeText(this,"Gagal terhubung ke server",Toast.LENGTH_LONG).show();});}
        }).start();
    }

    private JSONObject request(String method, JSONObject body) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(API + ("GET".equals(method)?"?t="+System.currentTimeMillis():"")).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(25000); c.setRequestMethod(method); c.setRequestProperty("Accept","application/json"); c.setRequestProperty("Content-Type","application/json; charset=UTF-8");
        String token=new SessionManager(this).getToken().trim(); if(!token.isEmpty()) c.setRequestProperty("Authorization","Bearer "+token);
        if(body!=null){c.setDoOutput(true);BufferedWriter w=new BufferedWriter(new OutputStreamWriter(c.getOutputStream(),"UTF-8"));w.write(body.toString());w.close();}
        InputStream s=c.getResponseCode()<400?c.getInputStream():c.getErrorStream(); BufferedReader r=new BufferedReader(new InputStreamReader(s)); StringBuilder b=new StringBuilder(); String line; while((line=r.readLine())!=null)b.append(line); r.close(); c.disconnect(); return new JSONObject(b.toString());
    }

    private void empty(String s){listBox.removeAllViews();TextView v=text(s,14,"#64748B",false);v.setGravity(Gravity.CENTER);v.setPadding(0,dp(30),0,dp(30));listBox.addView(v);}
    private String money(double v){return "Rp " + NumberFormat.getIntegerInstance(new Locale("id","ID")).format(v);}
    private TextView text(String s,int z,String c,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(Color.parseColor(c));if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);v.setLineSpacing(0,1.12f);return v;}
    private Button button(String s,String color){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.DEFAULT_BOLD);b.setBackground(round(color,color,15));return b;}
    private GradientDrawable round(String fill,String stroke,int radius){GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(fill));g.setCornerRadius(dp(radius));g.setStroke(dp(1),Color.parseColor(stroke));return g;}
    private GradientDrawable gradient(){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.parseColor("#0878F9"),Color.parseColor("#0B3A78")});g.setCornerRadius(dp(22));return g;}
    private void gap(LinearLayout l,int h){android.widget.Space s=new android.widget.Space(this);l.addView(s,new LinearLayout.LayoutParams(1,dp(h)));}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
