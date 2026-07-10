package com.transiva.app;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;

public class AdminDashboardActivity extends Activity {

    private LinearLayout root;

    private static final String CHANNEL_ID = "transiva_admin_updates";

    private final int NAVY = Color.parseColor("#071426");
    private final int BLUE = Color.parseColor("#086BFF");
    private final int BG = Color.parseColor("#F3F8FF");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);

        createNotificationChannel();

        buildLayout();

        sendAdminNotification(
                "Admin Dashboard Aktif",
                "Panel administrator Transiva berhasil dibuka."
        );
    }


    private void buildLayout(){

        ScrollView scroll = new ScrollView(this);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(22),dp(16),dp(28));
        root.setBackgroundColor(BG);

        scroll.addView(root);
        setContentView(scroll);


        addHeader();
        gap(14);

        addBanner();
        gap(14);

        addMenu();

        gap(14);

        addStatus();

        gap(14);

        Button logout = button("Keluar");

        logout.setOnClickListener(v -> {
            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });

        root.addView(logout,
                new LinearLayout.LayoutParams(-1,dp(50)));

    }


    private void addHeader(){

        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        left.addView(txt("Selamat Datang 👋",13,"#64748B",false));
        left.addView(txt("Admin Transiva",23,"#0B3A78",true));

        TextView badge = txt(
                "Verified Admin",
                11,
                "#0B7CFF",
                true
        );

        badge.setPadding(dp(10),dp(4),dp(10),dp(4));
        badge.setBackground(card("#EAF4FF","#B9DBFF"));

        left.addView(badge);

        h.addView(left,new LinearLayout.LayoutParams(0,-2,1));

        h.addView(
                txt("🛠️",32,"#0B3A78",true)
        );

        root.addView(h);
    }


    private void addBanner(){

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18),dp(18),dp(18),dp(18));
        card.setBackground(gradient());

        card.addView(txt(
                "🛡 Admin Control Center",
                18,
                "#FFFFFF",
                true
        ));

        card.addView(txt(
                "Monitoring Driver • Customer • Merchant • Sistem",
                13,
                "#FFFFFF",
                false
        ));

        root.addView(card);
    }


    private void addMenu(){

        root.addView(txt(
                "Menu Admin",
                18,
                "#0B3A78",
                true
        ));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);

        String[][] menu={
                {"🛵","Driver"},
                {"👥","Customer"},
                {"🏪","Merchant"},
                {"📍","Business"},
                {"🧺","Laundry"},
                {"🏝️","Wisata"},
                {"🏦","Money"},
                {"💸","WD Driver"},
                {"✅","Verifikasi"}
        };


        for(String[] m:menu){

            LinearLayout item=new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(8),dp(12),dp(8),dp(10));
            item.setBackground(card("#FFFFFF","#D7E6F8"));

            item.addView(txt(m[0],30,"#086BFF",true));
            item.addView(txt(m[1],12,"#0B3A78",true));

            item.setClickable(true);
            item.setFocusable(true);

            if ("Money".equals(m[1])) {
                item.setOnClickListener(v -> {
                    Intent intent = new Intent(
                            AdminDashboardActivity.this,
                            AdminMoneyManagementActivity.class
                    );
                    startActivity(intent);
                });
            }

            GridLayout.LayoutParams lp =
                    new GridLayout.LayoutParams();

            lp.width=(getResources().getDisplayMetrics().widthPixels-dp(50))/3;
            lp.height=dp(105);
            lp.setMargins(dp(4),dp(4),dp(4),dp(8));

            grid.addView(item,lp);
        }

        root.addView(grid);
    }


    private void addStatus(){

        LinearLayout c=new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16),dp(16),dp(16),dp(16));
        c.setBackground(card("#FFFFFF","#D7E6F8"));

        c.addView(txt(
                "Status Sistem",
                17,
                "#0B3A78",
                true
        ));

        c.addView(txt(
                "🟢 Admin panel aktif\nNotifikasi sistem aktif",
                13,
                "#64748B",
                false
        ));

        root.addView(c);
    }


    private Button button(String s){

        Button b=new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(gradient());

        return b;
    }


    private TextView txt(String s,int z,String c,boolean bold){

        TextView t=new TextView(this);
        t.setText(s);
        t.setTextSize(z);
        t.setTextColor(Color.parseColor(c));

        if(bold)
            t.setTypeface(Typeface.DEFAULT_BOLD);

        return t;
    }


    private GradientDrawable card(String bg,String stroke){

        GradientDrawable g=new GradientDrawable();
        g.setColor(Color.parseColor(bg));
        g.setCornerRadius(dp(22));
        g.setStroke(dp(1),Color.parseColor(stroke));

        return g;
    }


    private GradientDrawable gradient(){

        GradientDrawable g =
                new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{
                                Color.parseColor("#086BFF"),
                                Color.parseColor("#2EA2FF")
                        });

        g.setCornerRadius(dp(24));

        return g;
    }


    private void gap(int h){
        Space s=new Space(this);
        root.addView(s,new LinearLayout.LayoutParams(1,dp(h)));
    }


    private void createNotificationChannel(){

        if(Build.VERSION.SDK_INT>=26){

            NotificationChannel c =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Transiva Admin Updates",
                            NotificationManager.IMPORTANCE_DEFAULT
                    );

            NotificationManager nm =
                    getSystemService(NotificationManager.class);

            if(nm!=null)
                nm.createNotificationChannel(c);
        }
    }


    private void sendAdminNotification(String title,String msg){

        try{

            NotificationManager nm =
                    (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

            Intent i=new Intent(this,AdminDashboardActivity.class);

            PendingIntent pi =
                    PendingIntent.getActivity(
                            this,
                            1,
                            i,
                            PendingIntent.FLAG_IMMUTABLE
                    );


            android.app.Notification.Builder b =
                    Build.VERSION.SDK_INT>=26 ?
                            new android.app.Notification.Builder(this,CHANNEL_ID):
                            new android.app.Notification.Builder(this);


            b.setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(msg)
                    .setContentIntent(pi)
                    .setAutoCancel(true);


            if(nm!=null)
                nm.notify(1001,b.build());

        }catch(Exception ignored){}
    }


    private int dp(int v){
        return (int)(v*getResources()
                .getDisplayMetrics()
                .density+0.5f);
    }
}
