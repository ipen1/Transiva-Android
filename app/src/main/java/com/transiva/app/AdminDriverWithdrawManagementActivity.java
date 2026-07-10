package com.transiva.app;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.widget.*;
import android.view.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public class AdminDriverWithdrawManagementActivity extends Activity {

    private static final String BASE_URL="https://transiva.my.id/";
    private LinearLayout list;
    private final Handler handler=new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#071426"));
        getWindow().setNavigationBarColor(Color.parseColor("#071426"));
        build();
        load();
    }

    private void build(){
        ScrollView sv=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(20),dp(16),dp(20));
        root.setBackgroundColor(Color.parseColor("#F3F8FF"));
        sv.addView(root);
        setContentView(sv);

        root.addView(txt("💸 WD Driver",22,true));
        root.addView(txt("Approve / Reject penarikan saldo driver",13,false));

        LinearLayout banner=new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(dp(16),dp(16),dp(16),dp(16));
        banner.setBackground(grad());
        TextView t=new TextView(this);
        t.setText("Withdraw Driver");
        t.setTextColor(Color.WHITE);
        t.setTextSize(18);
        t.setTypeface(null,Typeface.BOLD);
        banner.addView(t);
        root.addView(banner);

        list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        Button back=new Button(this);
        back.setText("Kembali");
        back.setOnClickListener(v->finish());
        root.addView(back);
    }

    private void load(){
        new Thread(()->{
            JSONArray arr=new JSONArray();
            try{
                JSONObject o=get(BASE_URL+"server/getDriverWithdrawals.php");
                if(o.optBoolean("success")) arr=o.optJSONArray("withdrawals");
            }catch(Exception ignored){}
            JSONArray data=arr;
            handler.post(()->render(data));
        }).start();
    }

    private void render(JSONArray arr){
        list.removeAllViews();
        if(arr==null||arr.length()==0){
            list.addView(txt("Belum ada pengajuan WD.",14,false));
            return;
        }
        for(int i=0;i<arr.length();i++){
            try{ card(arr.getJSONObject(i)); }catch(Exception ignored){}
        }
    }

    private void card(JSONObject o){
        LinearLayout c=new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14),dp(14),dp(14),dp(14));
        GradientDrawable g=new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(18));
        g.setStroke(2,Color.parseColor("#D7E6F8"));
        c.setBackground(g);

        c.addView(txt(o.optString("username"),16,true));
        c.addView(txt("Rp "+NumberFormat.getInstance(new Locale("id","ID")).format(o.optDouble("amount")),20,true));
        c.addView(txt("🏦 "+o.optString("bank_name"),13,false));
        c.addView(txt("🔢 "+o.optString("account_number"),13,false));
        c.addView(txt("👤 "+o.optString("account_name"),13,false));
        c.addView(txt("🕒 "+o.optString("requested_at"),13,false));
        c.addView(txt("Status : "+o.optString("status"),13,false));

        if("pending".equalsIgnoreCase(o.optString("status"))){
            Button a=new Button(this);
            a.setText("Approve");
            a.setOnClickListener(v->process(o.optInt("id"),"approve"));
            c.addView(a);

            Button r=new Button(this);
            r.setText("Reject");
            r.setOnClickListener(v->process(o.optInt("id"),"reject"));
            c.addView(r);
        }

        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,0,0,dp(10));
        list.addView(c,lp);
    }

    private void process(int id,String action){
        final EditText input=new EditText(this);
        new AlertDialog.Builder(this)
            .setTitle(action.equals("approve")?"Approve WD":"Reject WD")
            .setMessage("Catatan admin (opsional)")
            .setView(input)
            .setPositiveButton("Proses",(d,w)->{
                new Thread(()->{
                    try{
                        JSONObject p=new JSONObject();
                        p.put("id",id);
                        p.put("action",action);
                        p.put("admin","admin");
                        p.put("admin_note",input.getText().toString());
                        post(BASE_URL+"server/processDriverWithdraw.php",p);
                    }catch(Exception ignored){}
                    handler.post(this::load);
                }).start();
            })
            .setNegativeButton("Batal",null).show();
    }

    private JSONObject get(String u)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();
        BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb=new StringBuilder(); String l;
        while((l=br.readLine())!=null) sb.append(l);
        return new JSONObject(sb.toString());
    }
    private void post(String u,JSONObject j)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json");
        c.getOutputStream().write(j.toString().getBytes(StandardCharsets.UTF_8));
    }

    private TextView txt(String s,int sz,boolean b){
        TextView t=new TextView(this);
        t.setText(s); t.setTextSize(sz);
        t.setTextColor(Color.parseColor("#0B3A78"));
        if(b)t.setTypeface(null,Typeface.BOLD);
        return t;
    }
    private GradientDrawable grad(){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.parseColor("#086BFF"),Color.parseColor("#2EA2FF")});
        d.setCornerRadius(dp(20)); return d;
    }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
}
