package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.text.NumberFormat;
import java.util.Locale;

public class CustomerDashboardActivity extends Activity {

  private static final String BASE = "https://transiva.my.id/server/";
  private SessionManager session;
  private LinearLayout root, orderBox;
  private TextView nameText, balanceText, statusText;

  @Override
  protected void onCreate(Bundle b){
    super.onCreate(b);

    getWindow().setStatusBarColor(Color.parseColor("#06142E"));
    getWindow().setNavigationBarColor(Color.parseColor("#06142E"));

    session = new SessionManager(this);
    buildUi();
    loadDashboard();
  }

  private void buildUi(){
    ScrollView scroll = new ScrollView(this);
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(28, 34, 28, 34);
    root.setBackgroundColor(Color.parseColor("#F4F7FB"));
    scroll.addView(root);
    setContentView(scroll);

    TextView title = title("Transiva Customer");
    root.addView(title);

    nameText = text("Memuat akun...");
    balanceText = cardText("💳 Transiva Pay\nMemuat saldo...");
    statusText = text("");

    root.addView(nameText);
    root.addView(balanceText);

    LinearLayout grid = new LinearLayout(this);
    grid.setOrientation(LinearLayout.VERTICAL);
    root.addView(grid);

    addBtn(grid, "🛵 TransRide", "https://transiva.my.id/?app=1#kurir");
    addBtn(grid, "🚗 TransCar", "https://transiva.my.id/?app=1#mobil");
    addBtn(grid, "🍔 TransFood", "https://transiva.my.id/?app=1#food");
    addBtn(grid, "🏝️ TransTour", "https://transiva.my.id/?app=1#wisata");
    addBtn(grid, "👕 TransLaundry", "https://transiva.my.id/?app=1#laundry");
    addBtn(grid, "📦 TransPickup", "https://transiva.my.id/?app=1#pickup");

    root.addView(statusText);

    TextView h = title("Status Pesanan");
    h.setTextSize(20);
    root.addView(h);

    orderBox = new LinearLayout(this);
    orderBox.setOrientation(LinearLayout.VERTICAL);
    root.addView(orderBox);

    Button refresh = btn("Refresh Dashboard");
    refresh.setOnClickListener(v -> loadDashboard());
    root.addView(refresh);

    Button web = btn("Buka Dashboard Web");
    web.setOnClickListener(v -> openWeb("https://transiva.my.id/?app=1"));
    root.addView(web);
  }

  private void loadDashboard(){
    String username = session.getUsername();
    String userId = session.getUserId();

    nameText.setText("Halo, " + (username.isEmpty() ? "Customer" : username));
    statusText.setText("Memuat data...");
    orderBox.removeAllViews();

    new Thread(() -> {
      try {
        String balanceJson = get(BASE + "getBalance.php?username=" + enc(username));
        String ordersJson = get(BASE + "get_user_orders.php?user_id=" + enc(userId));

        runOnUiThread(() -> {
          showBalance(balanceJson);
          showOrders(ordersJson);
          statusText.setText("");
        });

      } catch(Exception e){
        runOnUiThread(() -> statusText.setText("Gagal memuat dashboard: " + e.getMessage()));
      }
    }).start();
  }

  private void showBalance(String json){
    try{
      JSONObject o = new JSONObject(json);
      int balance = o.optInt("balance", 0);
      balanceText.setText("💳 Transiva Pay\n" + rupiah(balance));
    }catch(Exception e){
      balanceText.setText("💳 Transiva Pay\nSaldo tidak terbaca");
    }
  }

  private void showOrders(String json){
    try{
      JSONObject o = new JSONObject(json);
      JSONArray arr = o.optJSONArray("orders");

      if(arr == null || arr.length() == 0){
        orderBox.addView(cardText("Belum ada pesanan aktif / riwayat."));
        return;
      }

      int max = Math.min(arr.length(), 5);

      for(int i = 0; i < max; i++){
        JSONObject item = arr.getJSONObject(i);

        String text =
          "Order #" + item.optString("id") + "\n" +
          "Layanan: " + item.optString("order_type", "kurir") + "\n" +
          "Status: " + item.optString("status") + "\n" +
          "Driver: " + item.optString("driver", "-") + "\n" +
          "Harga: " + rupiah(item.optInt("price", 0));

        TextView c = cardText(text);
        c.setOnClickListener(v -> openWeb("https://transiva.my.id/?app=1"));
        orderBox.addView(c);
      }

    }catch(Exception e){
      orderBox.addView(cardText("Riwayat pesanan tidak terbaca."));
    }
  }

  private void addBtn(LinearLayout parent, String label, String url){
    Button b = btn(label);
    b.setOnClickListener(v -> openWeb(url));
    parent.addView(b);
  }

  private void openWeb(String url){
    Intent i = new Intent(this, MainActivity.class);
    i.putExtra("url", url);
    startActivity(i);
  }

  private String get(String link) throws Exception{
    HttpURLConnection c = (HttpURLConnection) new URL(link).openConnection();
    c.setConnectTimeout(12000);
    c.setReadTimeout(12000);
    c.setRequestMethod("GET");

    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
    StringBuilder sb = new StringBuilder();
    String line;

    while((line = br.readLine()) != null){
      sb.append(line);
    }

    br.close();
    c.disconnect();

    return sb.toString();
  }

  private String enc(String s){
    try{
      return URLEncoder.encode(s == null ? "" : s, "UTF-8");
    }catch(Exception e){
      return "";
    }
  }

  private String rupiah(int v){
    return "Rp " + NumberFormat.getNumberInstance(new Locale("id", "ID")).format(v);
  }

  private TextView title(String s){
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(26);
    t.setTextColor(Color.parseColor("#06142E"));
    t.setTypeface(null, 1);
    t.setPadding(0, 0, 0, 18);
    return t;
  }

  private TextView text(String s){
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(16);
    t.setTextColor(Color.parseColor("#1F2937"));
    t.setPadding(0, 8, 0, 14);
    return t;
  }

  private TextView cardText(String s){
    TextView t = text(s);
    t.setTextSize(16);
    t.setBackgroundColor(Color.WHITE);
    t.setPadding(26, 22, 26, 22);

    LinearLayout.LayoutParams lp =
      new LinearLayout.LayoutParams(-1, -2);

    lp.setMargins(0, 10, 0, 14);
    t.setLayoutParams(lp);

    return t;
  }

  private Button btn(String s){
    Button b = new Button(this);
    b.setText(s);
    b.setAllCaps(false);
    b.setTextSize(16);
    b.setGravity(Gravity.CENTER);
    return b;
  }
      }
