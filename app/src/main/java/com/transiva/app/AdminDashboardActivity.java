package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

public class AdminDashboardActivity extends Activity {

    private int navy = Color.parseColor("#06142E");
    private int blue = Color.parseColor("#1976D2");
    private int bg = Color.parseColor("#F4F7FB");

    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(navy);
        getWindow().setNavigationBarColor(navy);

        buildUI();
    }

    private void buildUI() {

        ScrollView scroll = new ScrollView(this);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,30,24,30);
        root.setBackgroundColor(bg);

        scroll.addView(root);
        setContentView(scroll);


        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(20,20,20,20);
        header.setBackground(cardBackground(Color.WHITE));

        LinearLayout headText = new LinearLayout(this);
        headText.setOrientation(LinearLayout.VERTICAL);

        headText.addView(text("Admin Panel",24,navy,true));
        headText.addView(text("Administrator",16,Color.DKGRAY,false));

        header.addView(headText,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(text("🛠",30,navy,true));

        root.addView(header);



        root.addView(space());


        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(25,25,25,25);
        banner.setBackground(cardBackground(blue));

        banner.addView(text("Monitoring Driver & Sistem",
                20,Color.WHITE,true));

        banner.addView(text(
                "Kelola Driver • Pantau Aktivitas • Admin Center",
                14,Color.WHITE,false));

        root.addView(banner);



        root.addView(text("Menu Admin",20,navy,true));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);

        addMenu(grid,"🛵🚗","TransDriver");
        addMenu(grid,"👥","Customer");
        addMenu(grid,"🏪","Merchant");
        addMenu(grid,"📍","Business");
        addMenu(grid,"🧺","TransLaundry");
        addMenu(grid,"🏝️","TransWisata");
        addMenu(grid,"🏦","Money Management");
        addMenu(grid,"💸","WD Driver");
        addMenu(grid,"✅","Verifikasi User");

        root.addView(grid);



        root.addView(text("Status Sistem",20,navy,true));

        TextView status = text(
                "Admin panel aktif\nSistem berjalan normal",
                16,Color.DKGRAY,false);

        status.setPadding(20,20,20,20);
        status.setBackground(cardBackground(Color.WHITE));

        root.addView(status);



        Button profile = button("Profil Admin");
        root.addView(profile);


        Button logout = button("Keluar");
        logout.setOnClickListener(v -> {

            Intent i = new Intent(
                    this,
                    LoginActivity.class
            );

            i.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
            );

            startActivity(i);
            finish();

        });

        root.addView(logout);
    }



    private void addMenu(GridLayout grid,String icon,String title){

        Button b = new Button(this);

        b.setText(icon+"\n"+title);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);

        b.setBackground(cardBackground(Color.WHITE));

        GridLayout.LayoutParams lp =
                new GridLayout.LayoutParams();

        lp.width = 0;
        lp.height = 150;
        lp.columnSpec =
                GridLayout.spec(
                        GridLayout.UNDEFINED,
                        1f
                );

        lp.setMargins(8,8,8,8);

        b.setLayoutParams(lp);

        grid.addView(b);

    }



    private Button button(String txt){

        Button b=new Button(this);

        b.setText(txt);
        b.setAllCaps(false);
        b.setTextSize(16);

        return b;
    }



    private TextView text(
            String t,
            int size,
            int color,
            boolean bold){

        TextView v=new TextView(this);

        v.setText(t);
        v.setTextSize(size);
        v.setTextColor(color);

        if(bold)
            v.setTypeface(null,1);

        v.setPadding(5,8,5,8);

        return v;
    }



    private View space(){

        TextView v=new TextView(this);

        v.setHeight(20);

        return v;
    }



    private GradientDrawable cardBackground(int color){

        GradientDrawable g =
                new GradientDrawable();

        g.setColor(color);
        g.setCornerRadius(30);

        return g;
    }

}
