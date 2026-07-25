package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AdminDriverTrackerActivity extends Activity {
    private static final String ENDPOINT="https://transiva.my.id/server/admin_driver_tracker_native.php";
    private final Handler main=new Handler(Looper.getMainLooper());
    private SessionManager session; private String token=""; private LinearLayout list; private ProgressBar progress; private TextView summary; private boolean loading;

    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.parseColor("#071426"));getWindow().setNavigationBarColor(Color.parseColor("#071426"));session=new SessionManager(this);token=clean(session.getToken());if(!session.isLoggedIn()||!"admin".equals(session.normalizeRole(session.getRole()))||token.isEmpty()){toast("Sesi admin tidak valid");finish();return;}setContentView(build());load();}
    @Override protected void onResume(){super.onResume();if(list!=null&&!loading)load();}

    private View build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.parseColor("#F3F8FF"));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(14),0,dp(14),0);bar.setBackgroundColor(Color.parseColor("#071426"));TextView back=txt("‹",34,"#FFFFFF",true);back.setOnClickListener(v->finish());bar.addView(back,new LinearLayout.LayoutParams(dp(44),-1));LinearLayout h=new LinearLayout(this);h.setOrientation(LinearLayout.VERTICAL);h.addView(txt("Tracker Driver",19,"#FFFFFF",true));h.addView(txt("Aktivitas, order, lokasi & pesan admin",11,"#B9D7FF",false));bar.addView(h,new LinearLayout.LayoutParams(0,-2,1));Button refresh=button("↻");refresh.setOnClickListener(v->load());bar.addView(refresh,new LinearLayout.LayoutParams(dp(50),dp(40)));root.addView(bar,new LinearLayout.LayoutParams(-1,dp(70)));
        ScrollView sc=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(14),dp(14),dp(14),dp(28));summary=txt("Memuat aktivitas driver...",14,"#0B3A78",true);summary.setPadding(dp(14),dp(13),dp(14),dp(13));summary.setBackground(card("#FFFFFF","#D7E6F8",18));body.addView(summary);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(12),0,0);body.addView(list,lp);sc.addView(body);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));progress=new ProgressBar(this);progress.setVisibility(View.GONE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));return root;}

    private void load(){if(loading)return;loading=true;progress.setVisibility(View.VISIBLE);new Thread(()->{try{JSONObject r=request("GET",null);main.post(()->{loading=false;progress.setVisibility(View.GONE);if(!r.optBoolean("success")){toast(r.optString("message","Gagal memuat tracker"));return;}render(r.optJSONArray("drivers"));});}catch(Exception e){main.post(()->{loading=false;progress.setVisibility(View.GONE);toast("Tracker gagal: "+e.getMessage());});}}).start();}
    private void render(JSONArray a){list.removeAllViews();int total=a==null?0:a.length(),online=0,busy=0;for(int i=0;i<total;i++){JSONObject d=a.optJSONObject(i);if(d==null)continue;if(d.optInt("is_online")==1)online++;if(d.optInt("is_busy")==1)busy++;list.addView(driverCard(d),top(9));}summary.setText("Driver: "+total+"   •   Online: "+online+"   •   Menjalankan order: "+busy);if(total==0)list.addView(txt("Belum ada data driver.",14,"#64748B",false));}
    private View driverCard(JSONObject d){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(15),dp(14),dp(15),dp(14));c.setBackground(card("#FFFFFF","#D7E6F8",18));
        String name=first(d.optString("name"),d.optString("username"),"Driver");String activity=d.optString("activity","Offline");boolean online=d.optInt("is_online")==1,busy=d.optInt("is_busy")==1;TextView n=txt((online?"🟢 ":"⚫ ")+name,16,"#0B3A78",true);c.addView(n);c.addView(txt("@"+d.optString("username")+" • "+d.optString("driver_type","bike")+" • "+first(d.optString("plate"),"Tanpa plat"),11,"#64748B",false),top(3));c.addView(txt(busy?"🚘 "+activity:(online?"🕒 Standby menunggu order":"⛔ Offline"),13,busy?"#C25B00":(online?"#0A8F4C":"#64748B"),true),top(9));
        JSONObject o=d.optJSONObject("active_order");if(o!=null){c.addView(txt(o.optString("service","Order")+" #"+o.optString("order_id")+" • "+o.optString("status"),12,"#0B7CFF",true),top(7));c.addView(txt("User: "+o.optString("customer","Customer")+"\nJemput: "+o.optString("pickup_address","-")+"\nTujuan: "+o.optString("destination_address","-"),11,"#475569",false),top(4));}
        String last=first(d.optString("last_location_at"),"Belum ada lokasi");c.addView(txt("Lokasi terakhir: "+last,10,"#64748B",false),top(7));
        LinearLayout actions=new LinearLayout(this);Button track=primary("Lacak");track.setEnabled(d.optDouble("latitude",0)!=0&&d.optDouble("longitude",0)!=0);track.setOnClickListener(v->openMap(d));actions.addView(track,new LinearLayout.LayoutParams(0,dp(45),1));Button msg=outline("Kirim Pesan");msg.setOnClickListener(v->messageDialog(d));LinearLayout.LayoutParams mlp=new LinearLayout.LayoutParams(0,dp(45),1);mlp.setMargins(dp(8),0,0,0);actions.addView(msg,mlp);c.addView(actions,top(12));return c;}
    private void openMap(JSONObject d){JSONObject o=d.optJSONObject("active_order");Intent i=new Intent(this,AdminDriverTrackerMapActivity.class);i.putExtra("driver_name",first(d.optString("name"),d.optString("username")));i.putExtra("driver_lat",d.optDouble("latitude",0));i.putExtra("driver_lng",d.optDouble("longitude",0));if(o!=null){i.putExtra("pickup_lat",o.optDouble("pickup_lat",0));i.putExtra("pickup_lng",o.optDouble("pickup_lng",0));i.putExtra("destination_lat",o.optDouble("destination_lat",0));i.putExtra("destination_lng",o.optDouble("destination_lng",0));}startActivity(i);}
    private void messageDialog(JSONObject d){LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.VERTICAL);f.setPadding(dp(8),dp(4),dp(8),0);f.addView(txt("Kirim kata-kata, arahan, atau motivasi kepada "+first(d.optString("name"),d.optString("username"))+".",12,"#64748B",false));EditText input=new EditText(this);input.setHint("Tulis pesan admin...");input.setMinLines(3);input.setGravity(Gravity.TOP);f.addView(input,new LinearLayout.LayoutParams(-1,dp(110)));LinearLayout quick=new LinearLayout(this);String[][] presets={{"Semangat","Tetap semangat dan utamakan keselamatan."},{"Apresiasi","Terima kasih atas pelayanan terbaik hari ini."},{"Aman","Mohon selesaikan order dengan aman dan ramah."}};for(String[] p:presets){Button b=outline(p[0]);b.setOnClickListener(v->input.setText(p[1]));quick.addView(b,new LinearLayout.LayoutParams(0,dp(42),1));}f.addView(quick,top(8));AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Pesan untuk Driver").setView(f).setNegativeButton("Batal",null).setPositiveButton("Kirim",null).create();dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String m=clean(input.getText().toString());if(m.isEmpty()){toast("Pesan belum diisi");return;}dialog.dismiss();sendMessage(d.optInt("user_id"),m);}));dialog.show();}
    private void sendMessage(int id,String message){progress.setVisibility(View.VISIBLE);new Thread(()->{try{JSONObject b=new JSONObject();b.put("action","notify");b.put("driver_user_id",id);b.put("title","Pesan dari Admin Transiva");b.put("message",message);JSONObject r=request("POST",b);main.post(()->{progress.setVisibility(View.GONE);toast(r.optString("message",r.optBoolean("success")?"Pesan terkirim":"Pesan gagal"));});}catch(Exception e){main.post(()->{progress.setVisibility(View.GONE);toast("Gagal mengirim: "+e.getMessage());});}}).start();}
    private JSONObject request(String method,JSONObject body)throws Exception{URL u=new URL("GET".equals(method)?ENDPOINT+"?v="+System.currentTimeMillis():ENDPOINT);HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setRequestMethod(method);c.setUseCaches(false);c.setRequestProperty("Accept","application/json");c.setRequestProperty("Authorization","Bearer "+token);if("POST".equals(method)){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream os=c.getOutputStream()){os.write(bytes);}}int s=c.getResponseCode();InputStream is=s>=200&&s<400?c.getInputStream():c.getErrorStream();BufferedReader br=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();String line;while((line=br.readLine())!=null)out.append(line);br.close();c.disconnect();return new JSONObject(out.toString());}
    private TextView txt(String s,int z,String color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(Color.parseColor(color));if(bold)t.setTypeface(null,1);return t;}private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}private Button primary(String s){Button b=button(s);b.setTextColor(Color.WHITE);b.setBackground(card("#0B7CFF","#0B7CFF",14));return b;}private Button outline(String s){Button b=button(s);b.setTextColor(Color.parseColor("#0B7CFF"));b.setBackground(card("#FFFFFF","#8EC5FF",14));return b;}private GradientDrawable card(String fill,String stroke,int r){GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(fill));g.setStroke(dp(1),Color.parseColor(stroke));g.setCornerRadius(dp(r));return g;}private LinearLayout.LayoutParams top(int m){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(m),0,0);return p;}private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}private String clean(String s){if(s==null)return"";s=s.trim();return "null".equalsIgnoreCase(s)||"undefined".equalsIgnoreCase(s)?"":s;}private String first(String...v){for(String s:v){s=clean(s);if(!s.isEmpty())return s;}return"";}
}
