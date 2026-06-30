package com.transiva.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DriverChatActivity extends Activity {
    private static final String BASE="https://transiva.my.id/server/";
    private final Handler h=new Handler(Looper.getMainLooper());
    private LinearLayout list; private EditText input; private String orderId,roomId,driverName,customerName; private boolean running=true;
    private final Runnable poll=new Runnable(){public void run(){ if(!running)return; loadMessages(); h.postDelayed(this,3000); }};
    @Override protected void onCreate(Bundle b){super.onCreate(b); orderId=getIntent().getStringExtra("order_id"); roomId=getIntent().getStringExtra("room_id"); driverName=getIntent().getStringExtra("driver_name"); customerName=getIntent().getStringExtra("customer_name"); build(); loadMessages(); h.postDelayed(poll,3000);}
    private void build(){FrameLayout page=new FrameLayout(this);page.setBackgroundColor(Color.parseColor("#F3F8FF"));LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(18),dp(16),dp(12));page.addView(root,new FrameLayout.LayoutParams(-1,-1));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView back=t("‹",36,"#0B3A78",true);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(50),dp(50)));LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);col.addView(t("Chat Customer",23,"#0B3A78",true));col.addView(t("Order #"+(orderId==null?"-":orderId),12,"#64748B",false));top.addView(col,new LinearLayout.LayoutParams(0,-2,1));root.addView(top);ScrollView sv=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sv.addView(list);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));LinearLayout send=new LinearLayout(this);send.setGravity(Gravity.CENTER_VERTICAL);input=new EditText(this);input.setHint("Tulis pesan...");send.addView(input,new LinearLayout.LayoutParams(0,dp(52),1));Button btn=new Button(this);btn.setText("Kirim");btn.setAllCaps(false);btn.setOnClickListener(v->sendMessage());send.addView(btn,new LinearLayout.LayoutParams(dp(92),dp(52)));root.addView(send);setContentView(page);} 
    private void loadMessages(){new Thread(()->{try{JSONObject p=new JSONObject();p.put("room_id",roomId);p.put("order_id",orderId);JSONObject r=post("get_chat_messages.php",p);JSONArray arr=r.optJSONArray("messages");h.post(()->render(arr));}catch(Exception ignored){}}).start();}
    private void render(JSONArray arr){if(arr==null)return;list.removeAllViews();for(int i=0;i<arr.length();i++){JSONObject m=arr.optJSONObject(i);if(m==null)continue;String sender=m.optString("sender",m.optString("from",""));String msg=m.optString("message",m.optString("text",""));boolean me=sender.equalsIgnoreCase(driverName)||sender.toLowerCase().contains("driver");TextView v=t(msg,15,"#111827",false);v.setPadding(dp(12),dp(9),dp(12),dp(9));android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(Color.parseColor(me?"#DCEBFF":"#FFFFFF"));bg.setCornerRadius(dp(14));v.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMargins(me?dp(54):0,dp(5),me?0:dp(54),dp(5));lp.gravity=me?Gravity.RIGHT:Gravity.LEFT;list.addView(v,lp);}}
    private void sendMessage(){String msg=input.getText().toString().trim();if(msg.length()==0)return;input.setText("");new Thread(()->{try{JSONObject p=new JSONObject();p.put("room_id",roomId);p.put("order_id",orderId);p.put("sender",driverName);p.put("sender_type","driver");p.put("receiver",customerName);p.put("message",msg);post("send_chat_message.php",p);loadMessages();}catch(Exception ignored){}}).start();}
    private JSONObject post(String endpoint,JSONObject payload)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(BASE+endpoint).openConnection();c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setDoOutput(true);OutputStream os=c.getOutputStream();os.write(payload.toString().getBytes(StandardCharsets.UTF_8));os.close();InputStream is=c.getResponseCode()>=400?c.getErrorStream():c.getInputStream();BufferedReader br=new BufferedReader(new InputStreamReader(is));StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line);br.close();String body=sb.toString().trim();return body.length()==0?new JSONObject():new JSONObject(body);} 
    @Override protected void onDestroy(){running=false;h.removeCallbacks(poll);super.onDestroy();} private TextView t(String s,int sp,String color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(Color.parseColor(color));if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;} private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+0.5f);} }
