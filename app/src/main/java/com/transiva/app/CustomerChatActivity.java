package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public class CustomerChatActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String GET_CHAT_URL = BASE_URL + "server/getChat.php";
    private static final String SEND_CHAT_URL = BASE_URL + "server/sendChat.php";
    private static final int TIMEOUT_MS = 20000;
    private static final long REFRESH_MS = 2000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private LinearLayout messagesBox;
    private ScrollView messagesScroll;
    private TextView driverNameText;
    private TextView driverInfoText;
    private TextView statusText;
    private ImageView driverPhotoView;
    private EditText chatInput;
    private Button sendBtn;
    private ProgressBar progressBar;

    private String orderId = "";
    private String roomId = "";
    private String driverName = "Driver";
    private String driverPlate = "";
    private String driverPhoto = "";
    private String lastHeaderName = "";
    private String lastHeaderInfo = "";
    private String loadedDriverPhotoUrl = "";
    private String loadingDriverPhotoUrl = "";
    private boolean defaultAvatarApplied = false;
    private static final Map<String, Bitmap> imageCache = new HashMap<>();
    private String orderStatus = "";

    private int lastId = 0;
    private boolean sending = false;
    private boolean loading = false;
    private boolean destroyed = false;
    private boolean firstLoad = true;

    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            if (!destroyed) {
                loadMessages(false);
                mainHandler.postDelayed(this, REFRESH_MS);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setStatusBarColor(Color.parseColor("#071426"));
            getWindow().setNavigationBarColor(Color.parseColor("#071426"));
        } catch (Exception ignored) {}

        readIntentAndPrefs();
        buildLayout();

        if (roomId.length() == 0) {
            showInfo("Chat", "Room chat tidak ditemukan.", true);
            return;
        }

        loadMessages(true);
        mainHandler.postDelayed(refreshRunnable, REFRESH_MS);
    }

    private void readIntentAndPrefs() {
        SharedPreferences sp = getSharedPreferences("transiva", MODE_PRIVATE);

        orderId = firstNonEmpty(
                getIntent().getStringExtra("order_id"),
                sp.getString("active_order_id", ""),
                sp.getString("order_id", "")
        );

        roomId = firstNonEmpty(
                getIntent().getStringExtra("room_id"),
                sp.getString("active_chat_room_id", ""),
                orderId.length() > 0 ? "ROOM-" + orderId : ""
        );

        driverName = firstNonEmpty(
                getIntent().getStringExtra("driver_name"),
                sp.getString("active_driver_name", ""),
                "Driver"
        );

        driverPhoto = firstNonEmpty(getIntent().getStringExtra("driver_photo"), sp.getString("active_driver_photo", ""));
        driverPlate = firstNonEmpty(getIntent().getStringExtra("driver_plate"), sp.getString("active_driver_plate", ""));

        roomId = normalizeRoomId(roomId);
        if (orderId.length() == 0 && roomId.startsWith("ROOM-")) {
            orderId = roomId.replaceFirst("(?i)^ROOM-", "");
        }

        sp.edit()
                .putString("active_order_id", orderId)
                .putString("active_chat_room_id", roomId)
                .apply();
    }

    private void buildLayout() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F3F8FF"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(10));
        page.addView(root, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(14), dp(12), dp(14), dp(12));
        header.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(24), 1));
        header.setElevation(dp(4));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(topRow, new LinearLayout.LayoutParams(-1, -2));

        Button backBtn = smallButton("‹", "#EAF4FF", "#0B7CFF", "#B9DBFF");
        topRow.addView(backBtn, new LinearLayout.LayoutParams(dp(42), dp(42)));
        backBtn.setOnClickListener(v -> finish());

        driverPhotoView = new ImageView(this);
        driverPhotoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        driverPhotoView.setBackground(round("#EAF4FF", dp(22)));
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        imgLp.setMargins(dp(10), 0, dp(10), 0);
        topRow.addView(driverPhotoView, imgLp);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        topRow.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));

        driverNameText = text(driverName, 17, "#0B3A78", true);
        driverNameText.setSingleLine(true);
        titleCol.addView(driverNameText);

        driverInfoText = text("Chat Driver", 12, "#64748B", false);
        titleCol.addView(driverInfoText);

        statusText = text("Menghubungkan chat...", 12, "#0B7CFF", true);
        statusText.setPadding(0, dp(8), 0, 0);
        header.addView(statusText);

        messagesScroll = new ScrollView(this);
        messagesScroll.setFillViewport(true);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollLp.setMargins(0, dp(10), 0, dp(10));
        root.addView(messagesScroll, scrollLp);

        messagesBox = new LinearLayout(this);
        messagesBox.setOrientation(LinearLayout.VERTICAL);
        messagesBox.setPadding(dp(2), dp(8), dp(2), dp(8));
        messagesScroll.addView(messagesBox, new ScrollView.LayoutParams(-1, -2));

        LinearLayout inputCard = new LinearLayout(this);
        inputCard.setGravity(Gravity.CENTER_VERTICAL);
        inputCard.setPadding(dp(10), dp(8), dp(10), dp(8));
        inputCard.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(24), 1));
        inputCard.setElevation(dp(6));
        root.addView(inputCard, new LinearLayout.LayoutParams(-1, dp(66)));

        chatInput = new EditText(this);
        chatInput.setSingleLine(false);
        chatInput.setMaxLines(3);
        chatInput.setTextSize(14);
        chatInput.setTextColor(Color.parseColor("#0F172A"));
        chatInput.setHintTextColor(Color.parseColor("#94A3B8"));
        chatInput.setHint("Ketik pesan...");
        chatInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        chatInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        chatInput.setPadding(dp(14), 0, dp(14), 0);
        chatInput.setBackground(roundStroke("#F8FBFF", "#D8E4F2", dp(18), 1));
        inputCard.addView(chatInput, new LinearLayout.LayoutParams(0, -1, 1));

        sendBtn = primaryButton("Kirim");
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(dp(78), -1);
        sendLp.setMargins(dp(8), 0, 0, 0);
        inputCard.addView(sendBtn, sendLp);
        sendBtn.setOnClickListener(v -> sendMessage());

        chatInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEND || enter) {
                sendMessage();
                return true;
            }
            return false;
        });

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams pLp = new FrameLayout.LayoutParams(dp(48), dp(48));
        pLp.gravity = Gravity.CENTER;
        page.addView(progressBar, pLp);

        setContentView(page);
        setDriverHeader();
    }

    private void setDriverHeader() {
        String name = firstNonEmpty(driverName, "Driver");
        String info = "Chat Driver";
        if (driverPlate.length() > 0) info = "Plat: " + driverPlate;
        if (orderStatus.length() > 0) info += " • " + statusLabel(orderStatus);

        if (!name.equals(lastHeaderName)) {
            driverNameText.setText(name);
            lastHeaderName = name;
        }

        if (!info.equals(lastHeaderInfo)) {
            driverInfoText.setText(info);
            lastHeaderInfo = info;
        }

        updateDriverAvatarStable(driverPhoto);
    }

    private void setDefaultAvatarOnce() {
        if (defaultAvatarApplied || driverPhotoView == null) return;
        try {
            driverPhotoView.setImageResource(android.R.drawable.ic_menu_myplaces);
            driverPhotoView.setColorFilter(Color.parseColor("#0B7CFF"));
            defaultAvatarApplied = true;
        } catch (Exception ignored) {}
    }

    private void updateDriverAvatarStable(String rawUrl) {
        String urlText = fixImageUrl(rawUrl);

        if (urlText.length() == 0) {
            setDefaultAvatarOnce();
            return;
        }

        if (urlText.equals(loadedDriverPhotoUrl)) {
            return;
        }

        Bitmap cached = imageCache.get(urlText);
        if (cached != null && !cached.isRecycled()) {
            try {
                driverPhotoView.clearColorFilter();
                driverPhotoView.setImageBitmap(cached);
                loadedDriverPhotoUrl = urlText;
                defaultAvatarApplied = false;
            } catch (Exception ignored) {}
            return;
        }

        if (urlText.equals(loadingDriverPhotoUrl)) {
            return;
        }

        if (loadedDriverPhotoUrl.length() == 0) {
            setDefaultAvatarOnce();
        }

        loadImageStable(urlText);
    }

    private String fixImageUrl(String value) {
        String path = firstNonEmpty(value, "").replace("\\", "/").trim();
        if (path.length() == 0 || "null".equalsIgnoreCase(path) || "undefined".equalsIgnoreCase(path)) return "";
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        if (path.startsWith("/")) return BASE_URL.substring(0, BASE_URL.length() - 1) + path;
        return BASE_URL + path;
    }

    private void loadImageStable(String urlText) {
        loadingDriverPhotoUrl = urlText;

        new Thread(() -> {
            Bitmap bmp = null;
            HttpURLConnection conn = null;
            InputStream is = null;

            try {
                URL url = new URL(urlText);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);
                conn.setUseCaches(true);
                conn.setRequestProperty("Accept", "image/*");
                is = conn.getInputStream();
                bmp = BitmapFactory.decodeStream(is);
            } catch (Exception ignored) {
            } finally {
                try { if (is != null) is.close(); } catch (Exception ignored) {}
                try { if (conn != null) conn.disconnect(); } catch (Exception ignored) {}
            }

            Bitmap finalBmp = bmp;
            mainHandler.post(() -> {
                if (!urlText.equals(loadingDriverPhotoUrl)) return;
                loadingDriverPhotoUrl = "";

                if (finalBmp != null && !finalBmp.isRecycled()) {
                    imageCache.put(urlText, finalBmp);
                    try {
                        driverPhotoView.clearColorFilter();
                        driverPhotoView.setImageBitmap(finalBmp);
                        loadedDriverPhotoUrl = urlText;
                        defaultAvatarApplied = false;
                    } catch (Exception ignored) {}
                } else if (loadedDriverPhotoUrl.length() == 0) {
                    setDefaultAvatarOnce();
                }
            });
        }).start();
    }

    private void loadMessages(boolean showLoading) {
        if (loading) return;
        loading = true;
        if (showLoading) progressBar.setVisibility(View.VISIBLE);

        final int requestLastId = firstLoad ? 0 : lastId;

        new Thread(() -> {
            try {
                String url = GET_CHAT_URL + "?room_id=" + urlEncode(roomId) + (requestLastId > 0 ? "&last_id=" + requestLastId : "");
                JSONObject res = getJson(url);

                mainHandler.post(() -> {
                    loading = false;
                    progressBar.setVisibility(View.GONE);
                    handleChatResponse(res, requestLastId == 0);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    loading = false;
                    progressBar.setVisibility(View.GONE);
                    statusText.setText("Koneksi chat bermasalah, mencoba ulang...");
                });
            }
        }).start();
    }

    private void handleChatResponse(JSONObject res, boolean resetList) {
        if (res == null) return;

        if (res.optBoolean("ended", false)) {
            orderStatus = res.optString("status", orderStatus);
            statusText.setText("Percakapan telah berakhir");
            chatInput.setEnabled(false);
            sendBtn.setEnabled(false);
            mainHandler.removeCallbacks(refreshRunnable);
        } else {
            orderStatus = res.optString("status", orderStatus);
            statusText.setText(statusLabel(orderStatus));
        }

        JSONObject driver = res.optJSONObject("driver");
        if (driver != null) {
            driverName = firstNonEmpty(driver.optString("name", ""), driver.optString("username", ""), driverName, "Driver");
            driverPlate = firstNonEmpty(driver.optString("plate", ""), driverPlate);
            driverPhoto = firstNonEmpty(driver.optString("driver_photo", ""), driver.optString("photo", ""), driverPhoto);
            setDriverHeader();
            saveDriverPrefs();
        }

        if (!res.optBoolean("success", false)) {
            String msg = firstNonEmpty(res.optString("message", ""), "Gagal memuat chat");
            statusText.setText(msg);
            return;
        }

        JSONArray arr = res.optJSONArray("messages");
        if (arr == null) return;

        if (resetList) {
            messagesBox.removeAllViews();
        }

        boolean added = false;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject m = arr.optJSONObject(i);
            if (m == null) continue;
            int id = m.optInt("id", 0);
            if (!resetList && id <= lastId) continue;
            if (id > lastId) lastId = id;
            addMessageBubble(m);
            added = true;
        }

        firstLoad = false;
        if (added || resetList) scrollToBottom();
    }

    private void addMessageBubble(JSONObject m) {
        String sender = m.optString("sender_type", "").trim().toLowerCase(Locale.US);
        String message = m.optString("message", "");
        String createdAt = m.optString("created_at", "");
        boolean mine = "customer".equals(sender);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(mine ? Gravity.RIGHT : Gravity.LEFT);
        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(-1, -2);
        wrapLp.setMargins(0, dp(4), 0, dp(4));
        messagesBox.addView(wrap, wrapLp);

        TextView bubble = text(message, 14, mine ? "#FFFFFF" : "#0F172A", false);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setMaxWidth((int)(getResources().getDisplayMetrics().widthPixels * 0.74));
        bubble.setBackground(mine
                ? roundGradient("#086BFF", "#2EA2FF", dp(18))
                : roundStroke("#FFFFFF", "#D7E6F8", dp(18), 1));
        wrap.addView(bubble, new LinearLayout.LayoutParams(-2, -2));

        String time = formatTime(createdAt);
        if (time.length() > 0) {
            TextView t = text(time, 10, "#94A3B8", false);
            t.setPadding(dp(8), dp(2), dp(8), 0);
            wrap.addView(t, new LinearLayout.LayoutParams(-2, -2));
        }
    }

    private void sendMessage() {
        if (sending) return;
        String message = chatInput.getText().toString().trim();
        if (message.length() == 0) return;
        if (message.length() > 1000) {
            showInfo("Pesan Terlalu Panjang", "Maksimal 1000 karakter.", false);
            return;
        }

        sending = true;
        sendBtn.setEnabled(false);
        sendBtn.setText("...");

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("room_id", roomId);
                payload.put("sender_type", "customer");
                payload.put("message", message);

                JSONObject res = postJson(SEND_CHAT_URL, payload);

                mainHandler.post(() -> {
                    sending = false;
                    sendBtn.setEnabled(true);
                    sendBtn.setText("Kirim");

                    if (res.optBoolean("success", false)) {
                        chatInput.setText("");
                        loadMessages(false);
                    } else {
                        if (res.optBoolean("ended", false)) {
                            chatInput.setEnabled(false);
                            sendBtn.setEnabled(false);
                            mainHandler.removeCallbacks(refreshRunnable);
                        }
                        showInfo("Chat", firstNonEmpty(res.optString("message", ""), "Gagal mengirim pesan"), false);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    sending = false;
                    sendBtn.setEnabled(true);
                    sendBtn.setText("Kirim");
                    showInfo("Koneksi Gagal", "Pesan belum terkirim. Coba lagi.", false);
                });
            }
        }).start();
    }

    private void saveDriverPrefs() {
        getSharedPreferences("transiva", MODE_PRIVATE).edit()
                .putString("active_driver_name", driverName)
                .putString("active_driver_plate", driverPlate)
                .putString("active_driver_photo", driverPhoto)
                .apply();
    }

    private JSONObject getJson(String urlText) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlText);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
            String body = readStream(is).trim();
            if (body.length() == 0) return new JSONObject();
            return new JSONObject(body);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private JSONObject postJson(String urlText, JSONObject payload) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlText);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
            writer.write(payload == null ? "{}" : payload.toString());
            writer.flush();
            writer.close();
            os.close();

            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
            String body = readStream(is).trim();
            if (body.length() == 0) return new JSONObject();
            return new JSONObject(body);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }

    private void scrollToBottom() {
        mainHandler.postDelayed(() -> {
            try { messagesScroll.fullScroll(View.FOCUS_DOWN); } catch (Exception ignored) {}
        }, 120);
    }

    private String normalizeRoomId(String value) {
        String v = firstNonEmpty(value, "").trim().replace("_", "-").toUpperCase(Locale.US);
        v = v.replaceAll("[^A-Z0-9\\-]", "");
        if (v.length() > 0 && !v.startsWith("ROOM-")) v = "ROOM-" + v;
        return v;
    }

    private String statusLabel(String status) {
        String s = firstNonEmpty(status, "").toLowerCase(Locale.US);
        if (s.equals("taken")) return "Driver menuju pickup";
        if (s.equals("arrived_pickup")) return "Driver tiba di pickup";
        if (s.equals("on_delivery")) return "Dalam perjalanan ke tujuan";
        if (s.equals("arrived_delivery")) return "Driver tiba di tujuan";
        if (s.equals("merchant_accepted")) return "Pesanan diproses merchant";
        if (s.equals("finished") || s.equals("completed") || s.equals("finish")) return "Order selesai";
        if (s.equals("canceled") || s.equals("cancelled")) return "Order dibatalkan";
        if (s.length() == 0) return "Chat aktif";
        return "Status: " + s;
    }

    private String formatTime(String value) {
        try {
            if (value == null || value.trim().length() == 0) return "";
            String v = value.trim();
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            Date d = in.parse(v);
            if (d == null) return "";
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(d);
        } catch (Exception e) {
            return "";
        }
    }

    private String urlEncode(String value) {
        try { return java.net.URLEncoder.encode(value, "UTF-8"); } catch (Exception e) { return value; }
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && v.trim().length() > 0 && !v.trim().equalsIgnoreCase("null")) return v.trim();
        }
        return "";
    }

    private TextView text(String value, int sp, String color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value == null ? "" : value);
        tv.setTextSize(sp);
        tv.setTextColor(Color.parseColor(color));
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private Button primaryButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackground(roundGradient("#086BFF", "#2EA2FF", dp(18)));
        return b;
    }

    private Button smallButton(String value, String bg, String fg, String stroke) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(22);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.parseColor(fg));
        b.setBackground(roundStroke(bg, stroke, dp(18), 1));
        return b;
    }

    private GradientDrawable round(String color, int radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(color));
        gd.setCornerRadius(radius);
        return gd;
    }

    private GradientDrawable roundStroke(String color, String stroke, int radius, int width) {
        GradientDrawable gd = round(color, radius);
        gd.setStroke(dp(width), Color.parseColor(stroke));
        return gd;
    }

    private GradientDrawable roundGradient(String start, String end, int radius) {
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor(start), Color.parseColor(end)});
        gd.setCornerRadius(radius);
        return gd;
    }

    private void showInfo(String title, String message, boolean closeAfter) {
        try {
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("OK", (d, w) -> { if (closeAfter) finish(); })
                    .show();
        } catch (Exception ignored) {}
    }

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        mainHandler.removeCallbacks(refreshRunnable);
        super.onDestroy();
    }
}
