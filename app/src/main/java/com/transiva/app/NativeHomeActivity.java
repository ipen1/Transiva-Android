package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public class NativeHomeActivity extends Activity {

    private SessionManager session;
    private TextView titleText;
    private TextView infoText;
    private TextView statusText;

    private static final String WEB_URL = "https://transiva.my.id/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        session = new SessionManager(this);

        buildLayout();
        loadSessionInfo();
    }

    private void buildLayout() {

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(245, 247, 251));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 48, 32, 32);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        scrollView.addView(root);

        titleText = new TextView(this);
        titleText.setText("Transiva Native");
        titleText.setTextSize(26);
        titleText.setTextColor(Color.rgb(17, 24, 39));
        titleText.setGravity(Gravity.CENTER);
        titleText.setPadding(0, 0, 0, 12);
        root.addView(titleText);

        statusText = new TextView(this);
        statusText.setTextSize(14);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(18, 14, 18, 14);
        root.addView(statusText);

        infoText = new TextView(this);
        infoText.setTextSize(15);
        infoText.setTextColor(Color.rgb(55, 65, 81));
        infoText.setPadding(0, 28, 0, 28);
        root.addView(infoText);

        Button openWebBtn = makeButton("Buka Web Transiva");
        openWebBtn.setOnClickListener(v -> openWebApp());
        root.addView(openWebBtn);

        Button refreshBtn = makeButton("Refresh Session");
        refreshBtn.setOnClickListener(v -> loadSessionInfo());
        root.addView(refreshBtn);

        Button clearBtn = makeButton("Logout / Hapus Session");
        clearBtn.setOnClickListener(v -> {
            session.clearSession();
            Toast.makeText(this, "Session dihapus", Toast.LENGTH_SHORT).show();
            loadSessionInfo();
        });
        root.addView(clearBtn);

        setContentView(scrollView);
    }

    private Button makeButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextSize(16);
        btn.setPadding(12, 16, 12, 16);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(0, 10, 0, 10);
        btn.setLayoutParams(params);

        return btn;
    }

    private void loadSessionInfo() {
        try {
            boolean online = isOnline();

            statusText.setText(
                    online
                            ? "Online • Terhubung ke Transiva"
                            : "Offline • Periksa koneksi internet"
            );

            statusText.setTextColor(
                    online
                            ? Color.rgb(22, 101, 52)
                            : Color.rgb(185, 28, 28)
            );

            JSONObject obj = session.getSessionJson();

            String username = obj.optString("username", "-");
            String name = obj.optString("name", "-");
            String role = obj.optString("role", "-");
            String id = obj.optString("id", "-");
            String restaurantId = obj.optString("restaurant_id", "-");
            String balance = obj.optString("balance", "0");
            String token = obj.optString("token", "");

            String loginStatus = session.isLoggedIn()
                    ? "Sudah login"
                    : "Belum login";

            String info =
                    "Status: " + loginStatus + "\n\n" +
                    "ID User: " + id + "\n" +
                    "Username: " + username + "\n" +
                    "Nama: " + name + "\n" +
                    "Role: " + role + "\n" +
                    "Restaurant ID: " + restaurantId + "\n" +
                    "Balance: " + balance + "\n" +
                    "Token: " + (token.isEmpty() ? "-" : "tersimpan") + "\n\n" +
                    "Base URL:\n" + WEB_URL;

            infoText.setText(info);

        } catch (Exception e) {
            infoText.setText("Gagal membaca session: " + e.getMessage());
        }
    }

    private void openWebApp() {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("url", WEB_URL);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Gagal membuka WebView",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

            if (cm == null) return false;

            NetworkInfo info = cm.getActiveNetworkInfo();

            return info != null && info.isConnected();

        } catch (Exception e) {
            return false;
        }
    }
}
