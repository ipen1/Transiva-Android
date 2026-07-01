package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;

public class DriverTopUpActivity extends Activity {
    private static final String UPLOAD_URL = "https://transiva.my.id/server/uploadDeposit.php";
    private static final int PICK_IMAGE = 4107;
    private static final int TIMEOUT_MS = 30000;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SessionManager session;
    private EditText amountInput;
    private TextView fileText;
    private ProgressBar progressBar;
    private Uri proofUri;
    private String username = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { getWindow().setStatusBarColor(Color.WHITE); getWindow().setNavigationBarColor(Color.WHITE); if (Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR); } catch(Exception ignored){}
        session = new SessionManager(this);
        try { username = session.getUsername(); } catch(Exception ignored){}
        buildUi();
    }

    private void buildUi() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F3F8FF"));
        ScrollView scroll = new ScrollView(this); page.addView(scroll, new FrameLayout.LayoutParams(-1,-1));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(24),dp(18),dp(30)); scroll.addView(root);
        root.addView(text("Deposit Driver", 28, "#0B3A78", true));
        add(root, text("Upload bukti transfer. Saldo akan bertambah otomatis setelah admin approve.", 13, "#64748B", false), 0, dp(5), 0, dp(14));

        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16),dp(16),dp(16),dp(16)); card.setBackground(roundStroke("#FFFFFF","#D7E6F8",dp(24),1));
        root.addView(card, new LinearLayout.LayoutParams(-1,-2));
        card.addView(text("Nominal Deposit", 14, "#0B3A78", true));
        amountInput = new EditText(this); amountInput.setHint("Contoh: 50000"); amountInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); amountInput.setTextColor(Color.parseColor("#0F172A")); amountInput.setHintTextColor(Color.parseColor("#94A3B8")); amountInput.setTextSize(15); amountInput.setPadding(dp(14),0,dp(14),0); amountInput.setBackground(roundStroke("#FFFFFF","#D8E4F2",dp(16),1));
        add(card, amountInput, 0, dp(8), 0, dp(12));
        fileText = text("Belum ada bukti transfer", 13, "#64748B", false); fileText.setPadding(dp(12),dp(12),dp(12),dp(12)); fileText.setBackground(roundStroke("#F8FBFF","#D7E6F8",dp(16),1)); add(card, fileText, 0, 0, 0, dp(12));
        Button pick = outlineButton("Pilih Bukti Transfer"); pick.setOnClickListener(v -> chooseImage()); add(card, pick, 0, 0, 0, dp(10));
        Button send = primaryButton("Kirim Deposit"); send.setOnClickListener(v -> submit()); add(card, send, 0, 0, 0, 0);
        Button back = outlineButton("Kembali"); back.setOnClickListener(v -> finish()); add(root, back, 0, dp(14), 0, 0);
        progressBar = new ProgressBar(this); progressBar.setVisibility(View.GONE); FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(52),dp(52)); plp.gravity = android.view.Gravity.CENTER; page.addView(progressBar, plp);
        setContentView(page);
    }

    private void chooseImage() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(Intent.createChooser(i, "Pilih bukti deposit"), PICK_IMAGE);
    }

    @Override protected void onActivityResult(int r, int c, Intent d) {
        super.onActivityResult(r,c,d);
        if (r == PICK_IMAGE && c == RESULT_OK && d != null && d.getData() != null) {
            proofUri = d.getData();
            fileText.setText("Bukti dipilih: " + getFileName(proofUri));
        }
    }

    private void submit() {
        String amount = amountInput.getText().toString().trim();
        if (username == null || username.trim().isEmpty()) { info("Sesi Berakhir", "Silakan login ulang."); return; }
        if (amount.isEmpty() || Long.parseLong(amount) <= 0) { info("Nominal Salah", "Isi nominal deposit dengan benar."); return; }
        if (proofUri == null) { info("Bukti Kosong", "Pilih gambar bukti transfer dulu."); return; }
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            String msg;
            boolean ok;
            try {
                JSONObject res = upload(amount, proofUri);
                ok = res.optBoolean("success", false);
                msg = res.optString("message", ok ? "Deposit berhasil dikirim." : "Deposit gagal.");
            } catch (Exception e) { ok=false; msg="Koneksi gagal mengirim deposit."; }
            final boolean fOk = ok; final String fMsg = msg;
            mainHandler.post(() -> { progressBar.setVisibility(View.GONE); new AlertDialog.Builder(this).setTitle(fOk ? "Berhasil" : "Gagal").setMessage(fMsg + (fOk ? "\n\nDashboard akan auto update setelah saldo diapprove." : "")).setPositiveButton("OK", (di,w)->{ if(fOk) finish(); }).show(); });
        }).start();
    }

    private JSONObject upload(String amount, Uri uri) throws Exception {
        String boundary = "----Transiva" + System.currentTimeMillis();
        HttpURLConnection c = (HttpURLConnection)new URL(UPLOAD_URL).openConnection();
        c.setConnectTimeout(TIMEOUT_MS); c.setReadTimeout(TIMEOUT_MS); c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary); c.setRequestProperty("Accept", "application/json");
        OutputStream os = c.getOutputStream();
        writeField(os, boundary, "username", username);
        writeField(os, boundary, "amount", amount);
        String name = getFileName(uri); if (name.length()==0) name="proof.jpg";
        os.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"proof\"; filename=\""+name+"\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        InputStream in = getContentResolver().openInputStream(uri); byte[] buf = new byte[8192]; int n; while(in != null && (n=in.read(buf))>0) os.write(buf,0,n); if(in!=null) in.close();
        os.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8)); os.flush(); os.close();
        InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream(); String body = read(is); c.disconnect(); return new JSONObject(body);
    }
    private void writeField(OutputStream os, String b, String k, String v) throws Exception { os.write(("--"+b+"\r\nContent-Disposition: form-data; name=\""+k+"\"\r\n\r\n"+v+"\r\n").getBytes(StandardCharsets.UTF_8)); }
    private String read(InputStream is) throws Exception { if(is==null)return""; ByteArrayOutputStream bos=new ByteArrayOutputStream(); byte[] buf=new byte[4096]; int n; while((n=is.read(buf))>0) bos.write(buf,0,n); is.close(); return bos.toString("UTF-8"); }
    private String getFileName(Uri uri) { try { Cursor c=getContentResolver().query(uri,null,null,null,null); if(c!=null){ int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if(c.moveToFirst()&&i>=0){String n=c.getString(i); c.close(); return n;} c.close(); } } catch(Exception ignored){} return "proof.jpg"; }
    private void info(String t,String m){ new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("OK",null).show(); }
    private Button primaryButton(String s){ Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(15); b.setTypeface(Typeface.DEFAULT_BOLD); b.setTextColor(Color.WHITE); b.setBackground(roundGradient("#086BFF","#2EA2FF",dp(18))); return b; }
    private Button outlineButton(String s){ Button b=primaryButton(s); b.setTextColor(Color.parseColor("#0B7CFF")); b.setBackground(roundStroke("#FFFFFF","#9DCAFF",dp(18),1)); return b; }
    private TextView text(String s,int sp,String color,boolean bold){ TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(Color.parseColor(color)); if(bold)t.setTypeface(Typeface.DEFAULT_BOLD); return t; }
    private void add(LinearLayout p, View v, int l,int t,int r,int b){ LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1, v instanceof EditText ? dp(52) : -2); lp.setMargins(l,t,r,b); p.addView(v,lp); }
    private GradientDrawable round(String c,int r){ GradientDrawable g=new GradientDrawable(); g.setColor(Color.parseColor(c)); g.setCornerRadius(r); return g; }
    private GradientDrawable roundStroke(String c,String s,int r,int w){ GradientDrawable g=round(c,r); g.setStroke(dp(w),Color.parseColor(s)); return g; }
    private GradientDrawable roundGradient(String a,String b,int r){ GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.parseColor(a),Color.parseColor(b)}); g.setCornerRadius(r); return g; }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
}
