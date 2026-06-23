package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CustomerChatActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String PREF_NAME = "transiva";
    private static final int TIMEOUT_MS = 18000;
    private static final int REFRESH_MS = 2000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<ChatMessage> messages = new ArrayList<>();

    private LinearLayout messageList;
    private ScrollView scrollView;
    private EditText inputText;
    private Button sendBtn;
    private Button backBtn;
    private TextView titleText;
    private TextView subTitleText;
    private ProgressBar progressBar;

    private boolean sending = false;
    private boolean loading = false;
    private boolean destroyed = false;
    private String orderId = "";
    private String roomId = "";
    private String driverName = "Driver";

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

        resolveRoomData();
        buildLayout();

        if (roomId.length() == 0) {
            showInfo("Chat Tidak Valid", "Room chat tidak ditemukan.");
            return;
        }

        loadMessages(true);
        mainHandler.postDelayed(refreshRunnable, REFRESH_MS);
    }

    private void resolveRoomData() {
        Intent i = getIntent();
        SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        orderId = firstNonEmpty(
                i.getStringExtra("order_id"),
                i.getStringExtra("active_order_id"),
                sp.getString("active_order_id", ""),
                sp.getString("order_id", "")
        );

        roomId = firstNonEmpty(
                i.getStringExtra("room_id"),
                sp.getString("active_chat_room_id", "")
        );

        driverName = firstNonEmpty(
                i.getStringExtra("driver_name"),
                sp.getString("active_driver_name", ""),
                "Driver"
        );

        if (roomId.length() == 0 && orderId.length() > 0) {
            roomId = "ROOM-" + orderId;
        }

        roomId = normalizeRoomId(roomId);

        sp.edit()
                .putString("active_order_id", orderId)
                .putString("active_chat_room_id", roomId)
                .putString("active_driver_name", driverName)
                .apply();
    }

    private String normalizeRoomId(String value) {
        return firstNonEmpty(value, "")
                .replace("_", "-")
                .trim()
                .toUpperCase(Locale.US);
    }

    private void buildLayout() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F3F8FF"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(16), dp(14), dp(10));
        page.addView(root, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(10), dp(12), dp(10));
        header.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(24), 1));
        header.setElevation(dp(4));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        backBtn = smallButton("‹", "#EAF4FF", "#0B7CFF", "#B9DBFF");
        header.addView(backBtn, new LinearLayout.LayoutParams(dp(42), dp(42)));
        backBtn.setOnClickListener(v -> goBack());

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1);
        titleLp.setMargins(dp(10), 0, 0, 0);
        header.addView(titleBox, titleLp);

        titleText = text("Chat Driver", 18, "#0B3A78", true);
        titleBox.addView(titleText);

        subTitleText = text(driverName + " • Online", 12, "#64748B", false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(3), 0, 0);
        titleBox.addView(subTitleText, subLp);

        TextView roomBadge = text("💬", 22, "#0B7CFF", true);
        roomBadge.setGravity(Gravity.CENTER);
        roomBadge.setBackground(round("#EAF4FF", dp(22)));
        header.addView(roomBadge, new LinearLayout.LayoutParams(dp(44), dp(44)));

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollLp.setMargins(0, dp(12), 0, dp(10));
        root.addView(scrollView, scrollLp);

        messageList = new LinearLayout(this);
        messageList.setOrientation(LinearLayout.VERTICAL);
        messageList.setPadding(dp(2), dp(8), dp(2), dp(8));
        scrollView.addView(messageList, new ScrollView.LayoutParams(-1, -2));

        LinearLayout inputArea = new LinearLayout(this);
        inputArea.setOrientation(LinearLayout.HORIZONTAL);
        inputArea.setGravity(Gravity.CENTER_VERTICAL);
        inputArea.setPadding(dp(10), dp(8), dp(10), dp(8));
        inputArea.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(24), 1));
        inputArea.setElevation(dp(5));
        root.addView(inputArea, new LinearLayout.LayoutParams(-1, dp(66)));

        inputText = new EditText(this);
        inputText.setSingleLine(true);
        inputText.setTextSize(14);
        inputText.setTextColor(Color.parseColor("#0F172A"));
        inputText.setHintTextColor(Color.parseColor("#94A3B8"));
        inputText.setHint("Ketik pesan...");
        inputText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        inputText.setImeOptions(EditorInfo.IME_ACTION_SEND);
        inputText.setPadding(dp(14), 0, dp(14), 0);
        inputText.setBackground(roundStroke("#F8FBFF", "#E2E8F0", dp(18), 1));
        inputArea.addView(inputText, new LinearLayout.LayoutParams(0, dp(46), 1));

        sendBtn = primaryButton("Kirim");
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(dp(82), dp(46));
        sendLp.setMargins(dp(8), 0, 0, 0);
        inputArea.addView(sendBtn, sendLp);
        sendBtn.setOnClickListener(v -> sendMessage());

        inputText.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP;
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
    }

    private void loadMessages(boolean showLoading) {
        if (loading) return;
        loading = true;
        if (showLoading) progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                JSONObject res = getJson(BASE_URL + "server/getChat.php?room_id=" + Uri.encode(roomId));

                if (res.optBoolean("ended", false)) {
                    mainHandler.post(() -> {
                        loading = false;
                        progressBar.setVisibility(View.GONE);
                        handleChatEnded();
                    });
                    return;
                }

                if (!res.optBoolean("success", false)) {
                    mainHandler.post(() -> {
                        loading = false;
                        progressBar.setVisibility(View.GONE);
                        subTitleText.setText("Gagal memuat chat • coba lagi otomatis");
                    });
                    return;
                }

                JSONArray arr = res.optJSONArray("messages");
                List<ChatMessage> newMessages = parseMessages(arr);
                mainHandler.post(() -> {
                    loading = false;
                    progressBar.setVisibility(View.GONE);
                    subTitleText.setText(driverName + " • Online");
                    boolean changed = isMessagesChanged(newMessages);
                    if (changed) {
                        messages.clear();
                        messages.addAll(newMessages);
                        renderMessages();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    loading = false;
                    progressBar.setVisibility(View.GONE);
                    subTitleText.setText("Koneksi terputus • mencoba lagi...");
                });
            }
        }).start();
    }

    private List<ChatMessage> parseMessages(JSONArray arr) {
        List<ChatMessage> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            ChatMessage m = new ChatMessage();
            m.id = firstNonEmpty(o.optString("id"), String.valueOf(i));
            m.senderType = firstNonEmpty(o.optString("sender_type"), o.optString("sender"), "driver").toLowerCase(Locale.US);
            m.message = firstNonEmpty(o.optString("message"), "");
            m.createdAt = firstNonEmpty(o.optString("created_at"), o.optString("time"), "");
            if (m.message.length() > 0) out.add(m);
        }
        return out;
    }

    private boolean isMessagesChanged(List<ChatMessage> newMessages) {
        if (newMessages.size() != messages.size()) return true;
        for (int i = 0; i < newMessages.size(); i++) {
            ChatMessage a = newMessages.get(i);
            ChatMessage b = messages.get(i);
            if (!a.id.equals(b.id) || !a.message.equals(b.message) || !a.senderType.equals(b.senderType)) return true;
        }
        return false;
    }

    private void renderMessages() {
        messageList.removeAllViews();

        if (messages.size() == 0) {
            TextView empty = text("Belum ada pesan. Mulai chat dengan driver.", 13, "#64748B", false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(10), dp(28), dp(10), dp(28));
            messageList.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        for (ChatMessage m : messages) {
            boolean mine = "customer".equalsIgnoreCase(m.senderType);
            addBubble(m, mine);
        }

        mainHandler.postDelayed(() -> scrollView.fullScroll(View.FOCUS_DOWN), 120);
    }

    private void addBubble(ChatMessage m, boolean mine) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(mine ? Gravity.RIGHT : Gravity.LEFT);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.setMargins(0, dp(4), 0, dp(6));
        messageList.addView(row, rowLp);

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(13), dp(9), dp(13), dp(8));
        bubble.setBackground(mine
                ? roundGradient("#086BFF", "#2EA2FF", dp(18))
                : roundStroke("#FFFFFF", "#D7E6F8", dp(18), 1));

        int maxWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.76);
        LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(-2, -2);
        bubbleLp.width = -2;
        bubbleLp.setMargins(mine ? dp(44) : 0, 0, mine ? 0 : dp(44), 0);
        row.addView(bubble, bubbleLp);
        bubble.setMaximumWidth(maxWidth);

        TextView msg = text(m.message, 14, mine ? "#FFFFFF" : "#0F172A", false);
        msg.setLineSpacing(dp(2), 1.0f);
        bubble.addView(msg, new LinearLayout.LayoutParams(-2, -2));

        String time = shortTime(m.createdAt);
        if (time.length() > 0) {
            TextView t = text(time, 10, mine ? "#DCEEFF" : "#94A3B8", false);
            t.setGravity(mine ? Gravity.RIGHT : Gravity.LEFT);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(-1, -2);
            tLp.setMargins(0, dp(4), 0, 0);
            bubble.addView(t, tLp);
        }
    }

    private void sendMessage() {
        if (sending) return;
        String msg = inputText.getText().toString().trim();
        if (msg.length() == 0) return;
        if (roomId.length() == 0) {
            showInfo("Chat Tidak Valid", "Room chat tidak ditemukan.");
            return;
        }

        sending = true;
        sendBtn.setEnabled(false);
        sendBtn.setText("...");

        ChatMessage temp = new ChatMessage();
        temp.id = "tmp-" + System.currentTimeMillis();
        temp.senderType = "customer";
        temp.message = msg;
        temp.createdAt = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
        messages.add(temp);
        renderMessages();
        inputText.setText("");

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("room_id", roomId);
                payload.put("sender_type", "customer");
                payload.put("message", msg);
                if (orderId.length() > 0) payload.put("order_id", orderId);

                JSONObject res = postJson(BASE_URL + "server/sendChat.php", payload);
                mainHandler.post(() -> {
                    sending = false;
                    sendBtn.setEnabled(true);
                    sendBtn.setText("Kirim");
                    if (res.optBoolean("success", false)) {
                        loadMessages(false);
                    } else {
                        Toast.makeText(this, firstNonEmpty(res.optString("message"), "Gagal mengirim pesan"), Toast.LENGTH_LONG).show();
                        removeTemp(temp.id);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    sending = false;
                    sendBtn.setEnabled(true);
                    sendBtn.setText("Kirim");
                    Toast.makeText(this, "Koneksi gagal saat mengirim pesan", Toast.LENGTH_LONG).show();
                    removeTemp(temp.id);
                });
            }
        }).start();
    }

    private void removeTemp(String id) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).id.equals(id)) {
                messages.remove(i);
                break;
            }
        }
        renderMessages();
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

    private void handleChatEnded() {
        mainHandler.removeCallbacks(refreshRunnable);
        new AlertDialog.Builder(this)
                .setTitle("Chat Berakhir")
                .setMessage("Percakapan telah berakhir karena order selesai/dibatalkan.")
                .setCancelable(false)
                .setPositiveButton("OK", (d, w) -> goBack())
                .show();
    }

    private void goBack() {
        mainHandler.removeCallbacks(refreshRunnable);
        try {
            Intent i = new Intent(this, CustomerTripActivity.class);
            if (orderId.length() > 0) i.putExtra("order_id", orderId);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        } catch (Exception e) {
            try { startActivity(new Intent(this, CustomerDashboardActivity.class)); } catch (Exception ignored) {}
        }
        finish();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        mainHandler.removeCallbacks(refreshRunnable);
        super.onDestroy();
    }

    private String shortTime(String value) {
        String v = firstNonEmpty(value, "");
        if (v.length() == 0) return "";
        if (v.matches("^\\d{2}:\\d{2}.*")) return v.substring(0, 5);
        int space = v.indexOf(' ');
        if (space >= 0 && v.length() >= space + 6) return v.substring(space + 1, space + 6);
        if (v.length() >= 16 && v.charAt(10) == 'T') return v.substring(11, 16);
        return v.length() > 16 ? v.substring(11, 16) : "";
    }

    private void showInfo(String title, String message) {
        try {
            new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
        } catch (Exception e) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
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
        b.setBackground(roundStroke(bg, stroke, dp(16), 1));
        return b;
    }

    private TextView text(String value, int sp, String color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value == null ? "" : value);
        tv.setTextSize(sp);
        tv.setTextColor(Color.parseColor(color));
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
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

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && v.trim().length() > 0 && !v.trim().equalsIgnoreCase("null") && !v.trim().equalsIgnoreCase("undefined")) return v.trim();
        }
        return "";
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class ChatMessage {
        String id = "";
        String senderType = "";
        String message = "";
        String createdAt = "";
    }
}
