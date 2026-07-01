package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.util.Locale;

public class MerchantBaseActivity extends Activity {
    protected static final String BASE = "https://transiva.my.id/server/";
    protected SessionManager sessionManager;
    protected final int BLUE = Color.parseColor("#1677F2");
    protected final int NAVY = Color.parseColor("#0A1A2E");
    protected final int TEXT = Color.parseColor("#172033");
    protected final int MUTED = Color.parseColor("#667085");
    protected final int BG = Color.parseColor("#F5F8FF");

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        try {
            getWindow().setStatusBarColor(Color.WHITE);
            getWindow().setNavigationBarColor(Color.WHITE);
        } catch(Exception ignored){}
        sessionManager = new SessionManager(this);
    }

    protected String username(){
        try { String v = sessionManager.getUsername(); if(v != null && !v.trim().isEmpty()) return v.trim(); } catch(Exception ignored){}
        return getSharedPreferences("transiva_fcm", MODE_PRIVATE).getString("username", "");
    }

    protected int userId(){
        try {
            Method m = sessionManager.getClass().getMethod("getUserId");
            Object v = m.invoke(sessionManager);
            if(v instanceof Integer && (Integer)v > 0) return (Integer)v;
            if(v instanceof String && Integer.parseInt((String)v) > 0) return Integer.parseInt((String)v);
        } catch(Exception ignored){}
        try {
            Method m = sessionManager.getClass().getMethod("getId");
            Object v = m.invoke(sessionManager);
            if(v instanceof Integer && (Integer)v > 0) return (Integer)v;
            if(v instanceof String && Integer.parseInt((String)v) > 0) return Integer.parseInt((String)v);
        } catch(Exception ignored){}
        int id = getSharedPreferences("transiva_fcm", MODE_PRIVATE).getInt("user_id", 0);
        if(id > 0) return id;
        return getSharedPreferences("transiva", MODE_PRIVATE).getInt("user_id", 0);
    }

    protected ScrollView page(LinearLayout root){
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(24));
        root.setBackgroundColor(BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        return scroll;
    }

    protected TextView title(String text){
        TextView v = tv(text, 24, NAVY, true);
        v.setPadding(dp(4), dp(4), dp(4), dp(2));
        return v;
    }

    protected TextView sub(String text){
        TextView v = tv(text, 13, MUTED, false);
        v.setPadding(dp(4), 0, dp(4), dp(14));
        return v;
    }

    protected TextView tv(String text, int sp, int color, boolean bold){
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setIncludeFontPadding(true);
        if(bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    protected TextView card(String text){
        TextView v = tv(text, 14, TEXT, false);
        v.setPadding(dp(16), dp(14), dp(16), dp(14));
        v.setBackground(round(Color.WHITE, dp(18)));
        v.setLineSpacing(dp(2), 1f);
        v.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(12));
        v.setLayoutParams(lp);
        return v;
    }

    protected Button btn(String text){
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackground(round(BLUE, dp(16)));
        b.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.setMargins(0, dp(5), 0, dp(8));
        b.setLayoutParams(lp);
        return b;
    }

    protected Button outlineBtn(String text){
        Button b = btn(text);
        b.setTextColor(BLUE);
        b.setBackground(stroke(Color.WHITE, Color.parseColor("#BBD9FF"), dp(16)));
        return b;
    }

    protected EditText input(String hint, int type){
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(false);
        e.setMinLines(1);
        e.setTextSize(14);
        e.setTextColor(TEXT);
        e.setHintTextColor(Color.parseColor("#98A2B3"));
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setBackground(stroke(Color.WHITE, Color.parseColor("#DDE7F3"), dp(14)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.setMargins(0, dp(4), 0, dp(12));
        e.setLayoutParams(lp);
        return e;
    }

    protected TextView label(String s){
        TextView v = tv(s, 13, NAVY, true);
        v.setPadding(dp(4), dp(4), dp(4), 0);
        return v;
    }

    protected LinearLayout row(){
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        r.setPadding(0, 0, 0, dp(8));
        return r;
    }

    protected GradientDrawable round(int color, int radius){
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }
    protected GradientDrawable stroke(int color, int stroke, int radius){
        GradientDrawable g = round(color, radius);
        g.setStroke(dp(1), stroke);
        return g;
    }

    protected int dp(int v){ return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    protected String enc(String v){ try{return URLEncoder.encode(v == null ? "" : v, "UTF-8");}catch(Exception e){return "";} }
    protected String rupiah(long v){ return "Rp " + NumberFormat.getNumberInstance(new Locale("id","ID")).format(v); }
    protected String s(JSONObject o, String... keys){
        if(o == null) return "";
        for(String k: keys){ String v = o.optString(k, ""); if(v != null && !v.trim().isEmpty() && !"null".equalsIgnoreCase(v)) return v; }
        return "";
    }

    protected void toast(String msg){ Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
    protected void alert(String title, String msg){ new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show(); }
    protected void open(Class<?> c){ startActivity(new Intent(this, c)); }
    protected void backMerchant(){ finish(); }

    protected String get(String link) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(link).openConnection();
        c.setConnectTimeout(20000); c.setReadTimeout(20000); c.setUseCaches(false);
        c.setRequestProperty("Accept","application/json");
        InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        String out = read(is); c.disconnect(); return out;
    }

    protected String postJson(String link, JSONObject payload) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(link).openConnection();
        c.setConnectTimeout(20000); c.setReadTimeout(20000); c.setDoOutput(true); c.setUseCaches(false);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type","application/json; charset=UTF-8");
        c.setRequestProperty("Accept","application/json");
        OutputStream os = c.getOutputStream();
        os.write(payload.toString().getBytes("UTF-8")); os.flush(); os.close();
        InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        String out = read(is); c.disconnect(); return out;
    }

    protected String postForm(String link, JSONObject fields, Uri fileUri, String fileField, String fileName) throws Exception {
        String boundary = "----Transiva" + System.currentTimeMillis();
        HttpURLConnection c = (HttpURLConnection)new URL(link).openConnection();
        c.setConnectTimeout(30000); c.setReadTimeout(30000); c.setDoOutput(true); c.setUseCaches(false);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        OutputStream os = c.getOutputStream();
        java.util.Iterator<String> keys = fields.keys();
        while(keys.hasNext()){
            String key = keys.next();
            write(os, "--"+boundary+"\r\n");
            write(os, "Content-Disposition: form-data; name=\"" + key + "\"\r\n\r\n");
            write(os, fields.optString(key, "") + "\r\n");
        }
        if(fileUri != null){
            write(os, "--"+boundary+"\r\n");
            write(os, "Content-Disposition: form-data; name=\"" + fileField + "\"; filename=\"" + fileName + "\"\r\n");
            write(os, "Content-Type: image/jpeg\r\n\r\n");
            InputStream in = getContentResolver().openInputStream(fileUri);
            byte[] buf = new byte[8192]; int n;
            while(in != null && (n = in.read(buf)) > 0) os.write(buf, 0, n);
            if(in != null) in.close();
            write(os, "\r\n");
        }
        write(os, "--"+boundary+"--\r\n");
        os.flush(); os.close();
        InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        String out = read(is); c.disconnect(); return out;
    }

    private void write(OutputStream os, String s) throws Exception { os.write(s.getBytes("UTF-8")); }
    protected String read(InputStream is) throws Exception {
        if(is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder(); String line;
        while((line = br.readLine()) != null) sb.append(line);
        br.close(); return sb.toString();
    }

    protected void logout(){
        try { sessionManager.clearSession(); } catch(Exception ignored){}
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i); finish();
    }
}
