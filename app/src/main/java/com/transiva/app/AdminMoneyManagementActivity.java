package com.transiva.app;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import android.content.*;
import java.text.*;
import java.util.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class AdminMoneyManagementActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private LinearLayout list;
    private Handler handler = new Handler(Looper.getMainLooper());

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
        root.setPadding(dp(16),dp(20),dp(16),dp(24));
        root.setBackgroundColor(Color.parseColor("#F3F8FF"));

        sv.addView(root);
        setContentView(sv);


        root.addView(txt("🏦 Money Management",22,"#0B3A78",true));
        root.addView(txt("Kelola deposit customer Transiva",13,"#64748B",false));

        Space sp=new Space(this);
        root.addView(sp,new LinearLayout.LayoutParams(1,dp(14)));


        LinearLayout banner=new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(dp(16),dp(16),dp(16),dp(16));
        banner.setBackground(gradient());


        banner.addView(
                txt("Transiva Pay Control",17,"#FFFFFF",true)
        );

        banner.addView(
                txt("Approve dan monitor transaksi deposit",12,"#EAF4FF",false)
        );


        root.addView(banner);



        list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams lp=
                new LinearLayout.LayoutParams(-1,-2);

        lp.setMargins(0,dp(14),0,dp(10));

        root.addView(list,lp);


        Button back=new Button(this);
        back.setText("Kembali");
        back.setAllCaps(false);
        back.setBackground(outline());

        back.setOnClickListener(v->finish());

        root.addView(back,new LinearLayout.LayoutParams(-1,dp(48)));

    }



    private void load(){

        new Thread(()->{

            JSONArray arr=new JSONArray();

            try{

                JSONObject r=get(
                        BASE_URL+"server/getDeposits.php"
                );

                if(r.optBoolean("success"))
                    arr=r.optJSONArray("deposits");

            }catch(Exception ignored){}


            JSONArray result=arr;

            handler.post(()->render(result));


        }).start();

    }



    private void render(JSONArray arr){

        list.removeAllViews();

        if(arr==null || arr.length()==0){

            list.addView(
                    txt(
                    "Belum ada deposit",
                    14,
                    "#64748B",
                    false)
            );

            return;
        }


        for(int i=0;i<arr.length();i++){

            try{
                addCard(arr.getJSONObject(i));
            }catch(Exception ignored){}

        }

    }



    private void addCard(JSONObject d){

        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14),dp(14),dp(14),dp(14));
        card.setBackground(cardBg());
        card.setElevation(dp(2));


        card.addView(
                txt(
                d.optString("username"),
                16,
                "#0B3A78",
                true)
        );


        card.addView(
                txt(
                rupiah(d.optDouble("amount")),
                19,
                "#086BFF",
                true)
        );


        card.addView(
                txt(
                "Status : "+d.optString("status"),
                12,
                "#64748B",
                false)
        );


        card.addView(
                txt(
                "Waktu : "+d.optString("created_at"),
                12,
                "#64748B",
                false)
        );



        if(d.optString("status")
                .equalsIgnoreCase("PENDING")){


            Button ok=new Button(this);
            ok.setText("✓ Approve");
            ok.setAllCaps(false);
            ok.setBackground(green());

            ok.setOnClickListener(
                    v->action(
                    "approveDeposit.php",
                    d.optInt("id"))
            );


            card.addView(ok);



            Button no=new Button(this);
            no.setText("✕ Reject");
            no.setAllCaps(false);
            no.setBackground(red());

            no.setOnClickListener(
                    v->action(
                    "rejectDeposit.php",
                    d.optInt("id"))
            );

            card.addView(no);

        }


        LinearLayout.LayoutParams lp=
                new LinearLayout.LayoutParams(-1,-2);

        lp.setMargins(0,0,0,dp(10));

        list.addView(card,lp);

    }



    private void action(String api,int id){

        new Thread(()->{

            try{

                JSONObject p=new JSONObject();
                p.put("id",id);

                post(
                BASE_URL+"server/"+api,
                p);

            }catch(Exception ignored){}

            handler.post(this::load);

        }).start();

    }



    private JSONObject get(String url)throws Exception{

        HttpURLConnection c=
        (HttpURLConnection)new URL(url).openConnection();

        BufferedReader r=
        new BufferedReader(
        new InputStreamReader(c.getInputStream()));

        StringBuilder s=new StringBuilder();

        String l;

        while((l=r.readLine())!=null)
            s.append(l);

        return new JSONObject(s.toString());
    }



    private void post(String url,JSONObject data)throws Exception{

        HttpURLConnection c=
        (HttpURLConnection)new URL(url).openConnection();

        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty(
        "Content-Type","application/json");

        c.getOutputStream()
        .write(data.toString()
        .getBytes(StandardCharsets.UTF_8));

    }



    private TextView txt(String s,int size,String color,boolean bold){

        TextView t=new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(Color.parseColor(color));

        if(bold)
            t.setTypeface(null,Typeface.BOLD);

        t.setPadding(0,dp(3),0,dp(3));

        return t;
    }



    private GradientDrawable cardBg(){

        GradientDrawable g=new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(20));
        g.setStroke(dp(1),Color.parseColor("#D7E6F8"));
        return g;
    }



    private GradientDrawable gradient(){

        GradientDrawable g=
        new GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        new int[]{
        Color.parseColor("#086BFF"),
        Color.parseColor("#2EA2FF")});

        g.setCornerRadius(dp(22));

        return g;
    }



    private GradientDrawable outline(){

        GradientDrawable g=new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(18));
        g.setStroke(dp(1),Color.parseColor("#9DCAFF"));
        return g;
    }



    private GradientDrawable green(){
        return color("#16A34A");
    }

    private GradientDrawable red(){
        return color("#EF4444");
    }


    private GradientDrawable color(String c){

        GradientDrawable g=new GradientDrawable();
        g.setColor(Color.parseColor(c));
        g.setCornerRadius(dp(18));
        return g;
    }


    private String rupiah(double n){

        return "Rp "+
        NumberFormat.getInstance(
        new Locale("id","ID"))
        .format(n);

    }


    private int dp(int x){

        return (int)(x*
        getResources()
        .getDisplayMetrics()
        .density);

    }

}
