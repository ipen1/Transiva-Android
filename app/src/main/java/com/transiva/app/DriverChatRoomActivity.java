package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DriverChatRoomActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String GET_CHAT_URL =
            BASE_URL + "server/getChat.php";
    private static final String SEND_CHAT_URL =
            BASE_URL + "server/sendChat.php";
    private static final String UPLOAD_IMAGE_URL =
            BASE_URL + "server/upload_chat_image.php";

    private static final String IMAGE_PREFIX = "[[IMAGE]]";
    private static final String IMAGE_V2_PREFIX = "[[IMAGE2]]";
    private static final long REFRESH_MS = 2500L;

    private static final int REQUEST_GALLERY = 5101;
    private static final int REQUEST_INTERNAL_CAMERA = 5102;
    private static final int REQUEST_CAMERA_PERMISSION = 5103;

    private final Handler main = new Handler(Looper.getMainLooper());

    private LinearLayout messagesBox;
    private ScrollView messagesScroll;
    private TextView participantText;
    private TextView statusText;
    private EditText input;
    private Button sendButton;
    private Button attachButton;
    private ProgressBar progress;
    private LinearLayout inputCard;

    private String orderId = "";
    private String roomId = "";
    private String participantName = "Customer";
    private String orderType = "";
    private String orderStatus = "";
    private String orderSource = "orders";
    private SessionManager session;

    private boolean readOnly;
    private boolean loading;
    private boolean sending;
    private boolean uploading;
    private boolean destroyed;
    private int lastId;
    private boolean firstLoad = true;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!destroyed && !readOnly) {
                loadMessages(false);
                main.postDelayed(this, REFRESH_MS);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.parseColor("#0B7CFF"));
        getWindow().setNavigationBarColor(Color.parseColor("#071426"));

        session = new SessionManager(this);
        readIntent();
        setContentView(buildScreen());

        if (roomId.isEmpty()) {
            showMessage(
                    "Chat tidak tersedia",
                    "Room percakapan tidak ditemukan.");
            return;
        }

        applyReadOnlyState();
        loadMessages(true);

        if (!readOnly) {
            main.postDelayed(refreshRunnable, REFRESH_MS);
        }
    }

    private void readIntent() {
        orderId = first(
                getIntent().getStringExtra("order_id"),
                "");
        roomId = normalizeRoom(first(
                getIntent().getStringExtra("room_id"),
                orderId.isEmpty() ? "" : "ROOM-" + orderId));
        participantName = first(
                getIntent().getStringExtra("participant_name"),
                getIntent().getStringExtra("customer_name"),
                "Customer");
        orderType = first(
                getIntent().getStringExtra("order_type"),
                "");
        orderStatus = first(
                getIntent().getStringExtra("order_status"),
                "");

        orderSource = first(
                getIntent().getStringExtra("order_source"),
                "orders"
        );
        readOnly = getIntent().getBooleanExtra("read_only", false)
                || CustomerMessageStatus.isEnded(orderStatus);
    }

    private View buildScreen() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F4F8FD"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(10));
        page.addView(root, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(9), dp(10), dp(9));
        header.setBackground(roundStroke(
                "#FFFFFF", "#DCE8F6", 18, 1));

        Button back = new Button(this);
        back.setText("‹");
        back.setAllCaps(false);
        back.setTextSize(26);
        back.setTextColor(Color.parseColor("#0B7CFF"));
        back.setBackground(round("#EAF4FF", 14));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(10), 0, 0, 0);

        participantText = text(
                participantName, 16, "#0B3A78", true);
        participantText.setSingleLine(true);
        titleBox.addView(participantText);

        statusText = text(
                readOnly ? "Riwayat percakapan" : "Menghubungkan chat...",
                10,
                readOnly ? "#8495A8" : "#0B7CFF",
                true);
        titleBox.addView(statusText);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));

        TextView service = text(
                serviceName(orderType), 10, "#0B7CFF", true);
        service.setPadding(dp(9), dp(5), dp(9), dp(5));
        service.setBackground(round("#EAF4FF", 12));
        header.addView(service);
        root.addView(header);

        messagesScroll = new ScrollView(this);
        messagesScroll.setFillViewport(true);
        LinearLayout.LayoutParams scrollLp =
                new LinearLayout.LayoutParams(-1, 0, 1);
        scrollLp.setMargins(0, dp(10), 0, dp(10));
        root.addView(messagesScroll, scrollLp);

        messagesBox = new LinearLayout(this);
        messagesBox.setOrientation(LinearLayout.VERTICAL);
        messagesBox.setPadding(dp(2), dp(8), dp(2), dp(8));
        messagesScroll.addView(
                messagesBox,
                new ScrollView.LayoutParams(-1, -2));

        inputCard = new LinearLayout(this);
        inputCard.setGravity(Gravity.CENTER_VERTICAL);
        inputCard.setPadding(dp(9), dp(7), dp(9), dp(7));
        inputCard.setBackground(roundStroke(
                "#FFFFFF", "#D7E6F8", 20, 1));

        attachButton = new Button(this);
        attachButton.setText("+");
        attachButton.setAllCaps(false);
        attachButton.setTextSize(22);
        attachButton.setTextColor(Color.parseColor("#0B7CFF"));
        attachButton.setBackground(round("#EAF4FF", 15));
        attachButton.setOnClickListener(v -> showAttachmentMenu());
        inputCard.addView(
                attachButton,
                new LinearLayout.LayoutParams(dp(44), -1));

        input = new EditText(this);
        input.setSingleLine(false);
        input.setMaxLines(3);
        input.setTextSize(13);
        input.setTextColor(Color.parseColor("#0F172A"));
        input.setHintTextColor(Color.parseColor("#94A3B8"));
        input.setHint("Ketik pesan...");
        input.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setPadding(dp(13), 0, dp(13), 0);
        input.setBackground(roundStroke(
                "#F8FBFF", "#D8E4F2", 16, 1));

        LinearLayout.LayoutParams inputLp =
                new LinearLayout.LayoutParams(0, -1, 1);
        inputLp.setMargins(dp(7), 0, 0, 0);
        inputCard.addView(input, inputLp);

        sendButton = primaryButton("Kirim");
        LinearLayout.LayoutParams sendLp =
                new LinearLayout.LayoutParams(dp(74), -1);
        sendLp.setMargins(dp(7), 0, 0, 0);
        inputCard.addView(sendButton, sendLp);

        sendButton.setOnClickListener(v -> sendMessage());
        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;

            if (actionId == EditorInfo.IME_ACTION_SEND || enter) {
                sendMessage();
                return true;
            }
            return false;
        });

        root.addView(inputCard, new LinearLayout.LayoutParams(-1, dp(62)));

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams p =
                new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER);
        page.addView(progress, p);
        return page;
    }

    private void showAttachmentMenu() {
        if (readOnly || uploading) return;

        new AlertDialog.Builder(this)
                .setTitle("Kirim Foto")
                .setItems(
                        new String[]{"Ambil Foto", "Pilih dari Galeri"},
                        (dialog, which) -> {
                            if (which == 0) openCamera();
                            else openGallery();
                        })
                .show();
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
            return;
        }

        launchCamera();
    }

    private void launchCamera() {
        try {
            Intent intent = new Intent(this, ChatCameraActivity.class);
            startActivityForResult(intent, REQUEST_INTERNAL_CAMERA);
        } catch (Exception error) {
            toast("Kamera tidak tersedia");
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_GALLERY);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                toast("Izin kamera diperlukan.");
            }
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_INTERNAL_CAMERA) {
            if (resultCode != RESULT_OK || data == null) return;

            String path = data.getStringExtra("photo_path");
            if (clean(path).isEmpty()) {
                toast("File kamera tidak ditemukan.");
                return;
            }

            processCameraFile(path);
            return;
        }

        if (requestCode == REQUEST_GALLERY
                && resultCode == RESULT_OK
                && data != null
                && data.getData() != null) {
            processSelectedPhoto(data.getData());
        }
    }

    private void processSelectedPhoto(Uri uri) {
        if (uri == null || uploading) return;

        uploading = true;
        setSendingEnabled(false);
        progress.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                ChatImageProcessor.ImagePayload payload =
                        ChatImageProcessor.fromUri(
                                getContentResolver(), uri);

                main.post(() -> {
                    uploading = false;
                    progress.setVisibility(View.GONE);
                    uploadPhoto(payload);
                });

            } catch (Exception error) {
                main.post(() -> {
                    uploading = false;
                    progress.setVisibility(View.GONE);
                    setSendingEnabled(true);
                    toast(first(error.getMessage(),
                            "Foto tidak dapat dibaca."));
                });
            }
        }).start();
    }

    private void processCameraFile(String path) {
        if (clean(path).isEmpty() || uploading) return;

        uploading = true;
        setSendingEnabled(false);
        progress.setVisibility(View.VISIBLE);

        new Thread(() -> {
            File file = new File(path);

            try {
                ChatImageProcessor.ImagePayload payload =
                        ChatImageProcessor.fromFile(file);

                main.post(() -> {
                    uploading = false;
                    progress.setVisibility(View.GONE);
                    uploadPhoto(payload);
                    try { file.delete(); } catch (Exception ignored) {}
                });

            } catch (Exception error) {
                main.post(() -> {
                    uploading = false;
                    progress.setVisibility(View.GONE);
                    setSendingEnabled(true);
                    toast("Foto kamera tidak dapat dibaca.");
                });
            }
        }).start();
    }

    private void uploadPhoto(
            ChatImageProcessor.ImagePayload payload
    ) {
        if (payload == null || readOnly || uploading) return;

        uploading = true;
        setSendingEnabled(false);
        addLocalImageBubble(payload.previewWebp);
        scrollBottom();

        new Thread(() -> {
            try {
                JSONObject response =
                        CustomerMessageApi.uploadImagePair(
                                UPLOAD_IMAGE_URL,
                                roomId,
                                "driver",
                                payload);

                main.post(() -> {
                    uploading = false;
                    setSendingEnabled(true);

                    if (response.optBoolean("success", false)) {
                        main.postDelayed(() -> {
                            firstLoad = true;
                            lastId = 0;
                            loadMessages(false);
                        }, 500L);
                    } else {
                        toast(first(
                                response.optString("message"),
                                "Foto gagal dikirim"));
                    }
                });

            } catch (Exception error) {
                main.post(() -> {
                    uploading = false;
                    setSendingEnabled(true);
                    toast(first(error.getMessage(),
                            "Foto gagal dikirim"));
                });
            }
        }).start();
    }

    private void addLocalImageBubble(byte[] previewBytes) {
        LinearLayout wrapper = messageWrapper(true);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(round("#EAF1FA", 16));

        Bitmap bitmap = BitmapFactory.decodeByteArray(
                previewBytes, 0, previewBytes.length);
        if (bitmap != null) image.setImageBitmap(bitmap);

        wrapper.addView(
                image,
                new LinearLayout.LayoutParams(dp(220), dp(165)));
        wrapper.addView(text(
                "Mengirim foto…",
                9,
                "#64748B",
                true));
    }

    private void loadMessages(boolean showLoading) {
        if (loading) return;

        loading = true;
        if (showLoading) progress.setVisibility(View.VISIBLE);
        int requestedLastId = firstLoad ? 0 : lastId;

        new Thread(() -> {
            try {
                String endpoint = GET_CHAT_URL
                        + "?order_id="
                        + URLEncoder.encode(
                        orderId,
                        StandardCharsets.UTF_8.name()
                )
                        + "&source="
                        + URLEncoder.encode(
                        orderSource,
                        StandardCharsets.UTF_8.name()
                )
                        + "&room_id="
                        + URLEncoder.encode(
                        roomId,
                        StandardCharsets.UTF_8.name()
                );

                if (requestedLastId > 0) {
                    endpoint += "&last_id=" + requestedLastId;
                }

                JSONObject response =
                        DriverMessageApi.get(session, endpoint);

                main.post(() -> {
                    loading = false;
                    progress.setVisibility(View.GONE);
                    handleResponse(response, requestedLastId == 0);
                });

            } catch (Exception error) {
                main.post(() -> {
                    loading = false;
                    progress.setVisibility(View.GONE);
                    statusText.setText("Koneksi chat bermasalah");
                });
            }
        }).start();
    }

    private void handleResponse(
            JSONObject response,
            boolean reset
    ) {
        orderStatus = response.optString("status", orderStatus);

        boolean ended = response.optBoolean("ended", false)
                || CustomerMessageStatus.isEnded(orderStatus);

        if (ended) {
            readOnly = true;
            applyReadOnlyState();
            main.removeCallbacks(refreshRunnable);
        } else {
            statusText.setText(CustomerMessageStatus.orderLabel(
                    orderStatus, orderType));
        }

        JSONObject customer = response.optJSONObject("customer");
        if (customer != null) {
            String serverName = first(
                    customer.optString("name"),
                    customer.optString("username"),
                    customer.optString("customer_name"));
            if (!serverName.isEmpty()) {
                participantName = serverName;
                participantText.setText(serverName);
            }
        }

        if (!response.optBoolean("success", false)) {
            statusText.setText(first(
                    response.optString("message"),
                    "Gagal memuat chat"));
            return;
        }

        JSONArray array = response.optJSONArray("messages");
        if (array == null) return;

        if (reset) messagesBox.removeAllViews();

        boolean added = false;

        for (int i = 0; i < array.length(); i++) {
            JSONObject message = array.optJSONObject(i);
            if (message == null) continue;

            int id = message.optInt("id", 0);
            if (!reset && id <= lastId) continue;
            if (id > lastId) lastId = id;

            addBubble(message);
            added = true;
        }

        if (reset && array.length() == 0) {
            addSystemMessage("Belum ada pesan pada percakapan ini.");
        }

        firstLoad = false;
        if (added || reset) scrollBottom();
    }

    private void addBubble(JSONObject message) {
        String sender = CustomerMessageStatus.normalize(
                message.optString("sender_type", ""));
        boolean mine = sender.equals("driver");
        String content = message.optString("message", "");

        LinearLayout wrapper = messageWrapper(mine);

        if (content.startsWith(IMAGE_V2_PREFIX)
                || content.startsWith(IMAGE_PREFIX)) {
            String previewUrl;
            String hdUrl;

            if (content.startsWith(IMAGE_V2_PREFIX)) {
                String value = content.substring(
                        IMAGE_V2_PREFIX.length()).trim();
                String[] parts = value.split("\\|", 2);
                previewUrl = parts.length > 0 ? parts[0].trim() : "";
                hdUrl = parts.length > 1 ? parts[1].trim() : previewUrl;
            } else {
                previewUrl = content.substring(
                        IMAGE_PREFIX.length()).trim();
                hdUrl = previewUrl;
            }

            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(round("#EAF1FA", 16));
            wrapper.addView(
                    image,
                    new LinearLayout.LayoutParams(dp(220), dp(165)));

            loadRemoteImage(image, previewUrl);

            TextView hint = text(
                    "Ketuk untuk lihat HD",
                    9,
                    "#0B7CFF",
                    true);
            wrapper.addView(hint);

            image.setOnClickListener(v -> showHdImage(hdUrl));
            hint.setOnClickListener(v -> showHdImage(hdUrl));

        } else {
            TextView bubble = text(
                    content,
                    13,
                    mine ? "#FFFFFF" : "#0F172A",
                    false);
            bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
            bubble.setMaxWidth((int)(
                    getResources().getDisplayMetrics().widthPixels * 0.74));
            bubble.setBackground(
                    mine
                            ? gradient("#086BFF", "#2EA2FF", 18)
                            : roundStroke(
                            "#FFFFFF", "#D7E6F8", 18, 1));
            wrapper.addView(bubble);
        }

        String time = formatTime(
                message.optString("created_at", ""));
        if (!time.isEmpty()) {
            TextView view = text(time, 9, "#94A3B8", false);
            view.setPadding(dp(7), dp(3), dp(7), 0);
            wrapper.addView(view);
        }
    }

    private LinearLayout messageWrapper(boolean mine) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(mine ? Gravity.RIGHT : Gravity.LEFT);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(4), 0, dp(4));
        messagesBox.addView(wrapper, lp);
        return wrapper;
    }

    private void loadRemoteImage(ImageView view, String rawUrl) {
        String url = absoluteUrl(rawUrl);
        if (url.isEmpty()) return;

        new Thread(() -> {
            HttpURLConnection connection = null;
            InputStream inputStream = null;

            try {
                connection = (HttpURLConnection)
                        new URL(url).openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(20000);
                connection.setUseCaches(true);
                inputStream = connection.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                if (bitmap != null) {
                    main.post(() -> view.setImageBitmap(bitmap));
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    if (inputStream != null) inputStream.close();
                } catch (Exception ignored) {}
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void showHdImage(String rawUrl) {
        String url = absoluteUrl(rawUrl);
        if (url.isEmpty()) return;

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));

        TextView close = text("✕", 24, "#FFFFFF", true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(round("#66000000", 20));
        close.setOnClickListener(v -> dialog.dismiss());

        FrameLayout.LayoutParams closeLp =
                new FrameLayout.LayoutParams(dp(48), dp(48));
        closeLp.gravity = Gravity.TOP | Gravity.END;
        closeLp.setMargins(0, dp(12), dp(12), 0);
        frame.addView(close, closeLp);

        dialog.setContentView(frame);
        Window window = dialog.getWindow();

        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            window.setLayout(-1, -1);
        }

        dialog.show();

        if (window != null) window.setLayout(-1, -1);
        loadRemoteImage(image, url);
    }

    private void sendMessage() {
        if (readOnly || sending || uploading) return;

        String message = input.getText().toString().trim();
        if (message.isEmpty()) return;

        sending = true;
        setSendingEnabled(false);

        JSONObject body = new JSONObject();

        try {
            body.put("order_id", orderId);
            body.put("source", orderSource);
            body.put("room_id", roomId);
            body.put("sender_type", "driver");
            body.put("message", message);
        } catch (Exception error) {
            sending = false;
            setSendingEnabled(true);
            return;
        }

        new Thread(() -> {
            try {
                JSONObject response =
                        DriverMessageApi.post(session, SEND_CHAT_URL, body);

                main.post(() -> {
                    sending = false;
                    setSendingEnabled(true);

                    if (response.optBoolean("success", false)) {
                        input.setText("");
                        loadMessages(false);
                    } else {
                        toast(first(
                                response.optString("message"),
                                "Pesan gagal dikirim"));
                    }
                });

            } catch (Exception error) {
                main.post(() -> {
                    sending = false;
                    setSendingEnabled(true);
                    toast(first(error.getMessage(),
                            "Pesan gagal dikirim"));
                });
            }
        }).start();
    }

    private void applyReadOnlyState() {
        if (inputCard == null || !readOnly) return;

        input.setEnabled(false);
        input.setHint("Percakapan ini hanya dapat dibaca");
        attachButton.setEnabled(false);
        attachButton.setAlpha(0.45f);
        sendButton.setEnabled(false);
        sendButton.setText("Selesai");
        sendButton.setAlpha(0.55f);
        statusText.setText("Order selesai • riwayat hanya baca");
    }

    private void setSendingEnabled(boolean enabled) {
        attachButton.setEnabled(enabled && !readOnly);
        sendButton.setEnabled(enabled && !readOnly);
        input.setEnabled(enabled && !readOnly);
    }

    private void addSystemMessage(String value) {
        TextView view = text(value, 11, "#64748B", false);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(10), dp(12), dp(10));
        messagesBox.addView(view);
    }

    private void scrollBottom() {
        messagesScroll.post(() ->
                messagesScroll.fullScroll(View.FOCUS_DOWN));
    }

    private String absoluteUrl(String value) {
        String clean = clean(value);
        if (clean.isEmpty()) return "";
        if (clean.startsWith("http://")
                || clean.startsWith("https://")) {
            return clean;
        }
        if (clean.startsWith("/")) {
            return BASE_URL.substring(0, BASE_URL.length() - 1) + clean;
        }
        return BASE_URL + clean;
    }

    private String normalizeRoom(String value) {
        String clean = clean(value);
        if (clean.isEmpty()) return "";
        return clean.toUpperCase(Locale.US).startsWith("ROOM-")
                ? clean
                : "ROOM-" + clean;
    }

    private String serviceName(String type) {
        String value = clean(type).toLowerCase(Locale.US);
        if (value.contains("food")) return "TransFood";
        if (value.contains("car") || value.contains("mobil")) return "TransCar";
        if (value.contains("pickup")) return "TransPickup";
        return "TransRide";
    }

    private String formatTime(String raw) {
        if (clean(raw).isEmpty()) return "";

        String[] formats = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss"
        };

        for (String format : formats) {
            try {
                Date date = new SimpleDateFormat(
                        format, Locale.US).parse(raw);
                if (date != null) {
                    return new SimpleDateFormat(
                            "HH:mm",
                            new Locale("id", "ID")
                    ).format(date);
                }
            } catch (Exception ignored) {}
        }

        return raw;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(round("#0B7CFF", 15));
        return button;
    }

    private TextView text(
            String value,
            int sp,
            String color,
            boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.parseColor(color));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable round(String fill, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.parseColor(fill));
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private GradientDrawable roundStroke(
            String fill,
            String stroke,
            int radius,
            int width
    ) {
        GradientDrawable shape = round(fill, radius);
        shape.setStroke(dp(width), Color.parseColor(stroke));
        return shape;
    }

    private GradientDrawable gradient(
            String start,
            String end,
            int radius
    ) {
        GradientDrawable shape = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.parseColor(start),
                        Color.parseColor(end)
                });
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private int dp(int value) {
        return Math.round(value
                * getResources().getDisplayMetrics().density);
    }

    private String first(String... values) {
        if (values == null) return "";

        for (String value : values) {
            String clean = clean(value);
            if (!clean.isEmpty()
                    && !"null".equalsIgnoreCase(clean)
                    && !"undefined".equalsIgnoreCase(clean)) {
                return clean;
            }
        }

        return "";
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Tutup", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
