package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
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
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

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

public class CustomerChatRoomActivity extends Activity {

    private static final String BASE_URL =
            "https://transiva.my.id/";

    private static final String GET_CHAT_URL =
            BASE_URL + "server/getChat.php";

    private static final String SEND_CHAT_URL =
            BASE_URL + "server/sendChat.php";

    private static final long REFRESH_MS = 2500L;

    private static final String UPLOAD_IMAGE_URL =
            BASE_URL + "server/upload_chat_image.php";

    private static final int REQUEST_GALLERY = 4101;
    private static final int REQUEST_CAMERA = 4102;
    private static final int REQUEST_CAMERA_PERMISSION = 4103;
    private static final String IMAGE_PREFIX = "[[IMAGE]]";
    private static final String IMAGE_V2_PREFIX = "[[IMAGE2]]";

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

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
    private String participantName = "";
    private String orderType = "";
    private String orderStatus = "";

    private boolean readOnly;
    private boolean loading;
    private boolean sending;
    private boolean uploading;
    private boolean destroyed;
    private int lastId;
    private boolean firstLoad = true;

    private Uri cameraPhotoUri;
    private File cameraPhotoFile;
    private boolean cameraUsesMediaStore;

    private final Runnable refreshRunnable =
            new Runnable() {
                @Override
                public void run() {
                    if (!destroyed && !readOnly) {
                        loadMessages(false);

                        mainHandler.postDelayed(
                                this,
                                REFRESH_MS
                        );
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(
                Color.parseColor("#0B7CFF")
        );

        getWindow().setNavigationBarColor(
                Color.parseColor("#071426")
        );

        readIntent();
        setContentView(buildScreen());

        CustomerChatNotificationPoller.requestPermission(
                this
        );

        int notificationUserId = 0;

        try {
            SessionManager session =
                    new SessionManager(this);

            notificationUserId =
                    Integer.parseInt(
                            first(
                                    session.getId(),
                                    session.getUserId(),
                                    "0"
                            )
                    );

        } catch (Exception ignored) {
        }

        CustomerChatNotificationPoller.start(
                this,
                notificationUserId
        );

        CustomerChatNotificationPoller.setOpenRoom(
                roomId
        );

        if (roomId.isEmpty()) {
            showMessage(
                    "Chat tidak tersedia",
                    "Room percakapan tidak ditemukan.",
                    true
            );

            return;
        }

        applyReadOnlyState();
        loadMessages(true);

        if (!readOnly) {
            mainHandler.postDelayed(
                    refreshRunnable,
                    REFRESH_MS
            );
        }
    }

    private void readIntent() {
        orderId = first(
                getIntent().getStringExtra(
                        "order_id"
                ),
                ""
        );

        roomId = normalizeRoom(
                first(
                        getIntent().getStringExtra(
                                "room_id"
                        ),
                        orderId.isEmpty()
                                ? ""
                                : "ROOM-" + orderId
                )
        );

        participantName = first(
                getIntent().getStringExtra(
                        "participant_name"
                ),
                getIntent().getStringExtra(
                        "driver_name"
                ),
                "Driver"
        );

        orderType = first(
                getIntent().getStringExtra(
                        "order_type"
                ),
                ""
        );

        orderStatus = first(
                getIntent().getStringExtra(
                        "order_status"
                ),
                ""
        );

        readOnly =
                getIntent().getBooleanExtra(
                        "read_only",
                        false
                )
                        || CustomerMessageStatus
                        .isEnded(orderStatus);
    }

    private View buildScreen() {
        FrameLayout page = new FrameLayout(this);

        page.setBackgroundColor(
                Color.parseColor("#F4F8FD")
        );

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(10)
        );

        page.addView(
                root,
                new FrameLayout.LayoutParams(-1, -1)
        );

        LinearLayout header =
                new LinearLayout(this);

        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(
                dp(10),
                dp(9),
                dp(10),
                dp(9)
        );

        header.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#DCE8F6",
                        18,
                        1
                )
        );

        Button back = new Button(this);
        back.setText("‹");
        back.setAllCaps(false);
        back.setTextSize(26);
        back.setTextColor(
                Color.parseColor("#0B7CFF")
        );

        back.setBackground(
                round("#EAF4FF", 14)
        );

        back.setOnClickListener(
                view -> finish()
        );

        header.addView(
                back,
                new LinearLayout.LayoutParams(
                        dp(42),
                        dp(42)
                )
        );

        LinearLayout titleBox =
                new LinearLayout(this);

        titleBox.setOrientation(
                LinearLayout.VERTICAL
        );

        titleBox.setPadding(
                dp(10),
                0,
                0,
                0
        );

        participantText = text(
                participantName,
                16,
                "#0B3A78",
                true
        );

        participantText.setSingleLine(true);
        titleBox.addView(participantText);

        statusText = text(
                readOnly
                        ? "Riwayat percakapan"
                        : "Menghubungkan chat...",
                10,
                readOnly
                        ? "#8495A8"
                        : "#0B7CFF",
                true
        );

        titleBox.addView(statusText);

        header.addView(
                titleBox,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        TextView orderLabel = text(
                serviceName(orderType),
                10,
                "#0B7CFF",
                true
        );

        orderLabel.setPadding(
                dp(9),
                dp(5),
                dp(9),
                dp(5)
        );

        orderLabel.setBackground(
                round("#EAF4FF", 12)
        );

        header.addView(orderLabel);
        root.addView(header);

        messagesScroll = new ScrollView(this);
        messagesScroll.setFillViewport(true);

        LinearLayout.LayoutParams scrollLp =
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                );

        scrollLp.setMargins(
                0,
                dp(10),
                0,
                dp(10)
        );

        root.addView(messagesScroll, scrollLp);

        messagesBox =
                new LinearLayout(this);

        messagesBox.setOrientation(
                LinearLayout.VERTICAL
        );

        messagesBox.setPadding(
                dp(2),
                dp(8),
                dp(2),
                dp(8)
        );

        messagesScroll.addView(
                messagesBox,
                new ScrollView.LayoutParams(
                        -1,
                        -2
                )
        );

        inputCard = new LinearLayout(this);
        inputCard.setGravity(Gravity.CENTER_VERTICAL);
        inputCard.setPadding(
                dp(9),
                dp(7),
                dp(9),
                dp(7)
        );

        inputCard.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#D7E6F8",
                        20,
                        1
                )
        );

        inputCard.setElevation(dp(5));

        attachButton = new Button(this);
        attachButton.setText("+");
        attachButton.setAllCaps(false);
        attachButton.setTextSize(22);
        attachButton.setTextColor(Color.parseColor("#0B7CFF"));
        attachButton.setPadding(0, 0, 0, 0);
        attachButton.setBackground(round("#EAF4FF", 15));
        attachButton.setOnClickListener(view -> showAttachmentMenu());

        inputCard.addView(
                attachButton,
                new LinearLayout.LayoutParams(dp(44), -1)
        );

        input = new EditText(this);
        input.setSingleLine(false);
        input.setMaxLines(3);
        input.setTextSize(13);
        input.setTextColor(
                Color.parseColor("#0F172A")
        );

        input.setHintTextColor(
                Color.parseColor("#94A3B8")
        );

        input.setHint("Ketik pesan...");
        input.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );

        input.setImeOptions(
                EditorInfo.IME_ACTION_SEND
        );

        input.setPadding(
                dp(13),
                0,
                dp(13),
                0
        );

        input.setBackground(
                roundStroke(
                        "#F8FBFF",
                        "#D8E4F2",
                        16,
                        1
                )
        );

        LinearLayout.LayoutParams inputLp =
                new LinearLayout.LayoutParams(0, -1, 1);
        inputLp.setMargins(dp(7), 0, 0, 0);
        inputCard.addView(input, inputLp);

        sendButton = primaryButton("Kirim");

        LinearLayout.LayoutParams sendLp =
                new LinearLayout.LayoutParams(
                        dp(74),
                        -1
                );

        sendLp.setMargins(dp(7), 0, 0, 0);
        inputCard.addView(sendButton, sendLp);

        sendButton.setOnClickListener(
                view -> sendMessage()
        );

        input.setOnEditorActionListener(
                (view, actionId, event) -> {
                    boolean enter =
                            event != null
                                    && event.getKeyCode()
                                    == KeyEvent.KEYCODE_ENTER
                                    && event.getAction()
                                    == KeyEvent.ACTION_DOWN;

                    if (
                            actionId
                                    == EditorInfo.IME_ACTION_SEND
                                    || enter
                    ) {
                        sendMessage();
                        return true;
                    }

                    return false;
                }
        );

        root.addView(
                inputCard,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(62)
                )
        );

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);

        FrameLayout.LayoutParams progressLp =
                new FrameLayout.LayoutParams(
                        dp(44),
                        dp(44)
                );

        progressLp.gravity = Gravity.CENTER;
        page.addView(progress, progressLp);

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
                        }
                )
                .show();
    }

    private void openCamera() {
        if (
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                )
                        != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.CAMERA
                    },
                    REQUEST_CAMERA_PERMISSION
            );

            return;
        }

        launchCameraInternal();
    }

    private void launchCameraInternal() {
        try {
            cleanupCameraFile();

            if (
                    Build.VERSION.SDK_INT
                            >= Build.VERSION_CODES.Q
            ) {
                ContentValues values =
                        new ContentValues();

                values.put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        "transiva_chat_"
                                + System.currentTimeMillis()
                                + ".jpg"
                );

                values.put(
                        MediaStore.Images.Media.MIME_TYPE,
                        "image/jpeg"
                );

                values.put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES
                                + "/Transiva/Chat"
                );

                values.put(
                        MediaStore.Images.Media.IS_PENDING,
                        1
                );

                cameraPhotoUri =
                        getContentResolver().insert(
                                MediaStore.Images.Media
                                        .EXTERNAL_CONTENT_URI,
                                values
                        );

                if (cameraPhotoUri == null) {
                    throw new IllegalStateException(
                            "Gagal membuat lokasi foto kamera"
                    );
                }

                cameraUsesMediaStore = true;
                cameraPhotoFile = null;

            } else {
                File cameraDir = new File(
                        getCacheDir(),
                        "chat_camera"
                );

                if (
                        !cameraDir.exists()
                                && !cameraDir.mkdirs()
                ) {
                    throw new IllegalStateException(
                            "Folder kamera tidak dapat dibuat"
                    );
                }

                cameraPhotoFile =
                        File.createTempFile(
                                "chat_",
                                ".jpg",
                                cameraDir
                        );

                cameraPhotoUri =
                        FileProvider.getUriForFile(
                                this,
                                getPackageName()
                                        + ".chat.fileprovider",
                                cameraPhotoFile
                        );

                cameraUsesMediaStore = false;
            }

            Intent intent = new Intent(
                    MediaStore.ACTION_IMAGE_CAPTURE
            );

            intent.putExtra(
                    MediaStore.EXTRA_OUTPUT,
                    cameraPhotoUri
            );

            intent.setClipData(
                    ClipData.newRawUri(
                            "Transiva chat camera",
                            cameraPhotoUri
                    )
            );

            int uriFlags =
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_READ_URI_PERMISSION;

            intent.addFlags(uriFlags);

            for (
                    android.content.pm.ResolveInfo info
                    : getPackageManager()
                    .queryIntentActivities(
                            intent,
                            android.content.pm.PackageManager
                                    .MATCH_DEFAULT_ONLY
                    )
            ) {
                grantUriPermission(
                        info.activityInfo.packageName,
                        cameraPhotoUri,
                        uriFlags
                );
            }

            if (
                    intent.resolveActivity(
                            getPackageManager()
                    ) == null
            ) {
                throw new IllegalStateException(
                        "Aplikasi kamera tidak ditemukan"
                );
            }

            startActivityForResult(
                    intent,
                    REQUEST_CAMERA
            );

        } catch (Exception error) {
            cleanupCameraFile();

            toast(
                    "Kamera tidak tersedia: "
                            + error.getMessage()
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (
                requestCode
                        == REQUEST_CAMERA_PERMISSION
        ) {
            if (
                    grantResults.length > 0
                            &&
                    grantResults[0]
                            == PackageManager.PERMISSION_GRANTED
            ) {
                launchCameraInternal();
            } else {
                toast(
                        "Izin kamera diperlukan untuk mengambil foto."
                );
            }
        }
    }

    private void openGallery() {
        try {
            Intent intent = new Intent(
                    Intent.ACTION_OPEN_DOCUMENT
            );

            intent.addCategory(
                    Intent.CATEGORY_OPENABLE
            );

            intent.setType("image/*");

            startActivityForResult(
                    intent,
                    REQUEST_GALLERY
            );

        } catch (Exception error) {
            toast("Galeri tidak tersedia");
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (resultCode != RESULT_OK) {
            cleanupCameraFile();
            return;
        }

        try {
            Uri sourceUri = null;

            if (requestCode == REQUEST_CAMERA) {
                sourceUri = cameraPhotoUri;

                if (
                        cameraUsesMediaStore
                                &&
                        Build.VERSION.SDK_INT
                                >= Build.VERSION_CODES.Q
                ) {
                    ContentValues completed =
                            new ContentValues();

                    completed.put(
                            MediaStore.Images.Media.IS_PENDING,
                            0
                    );

                    getContentResolver().update(
                            cameraPhotoUri,
                            completed,
                            null,
                            null
                    );
                }

            } else if (
                    requestCode == REQUEST_GALLERY
                            && data != null
            ) {
                sourceUri = data.getData();
            }

            if (sourceUri == null) {
                toast("Foto tidak dapat dibaca");
                return;
            }

            ChatImageProcessor.ImagePayload payload =
                    ChatImageProcessor.fromUri(
                            getContentResolver(),
                            sourceUri
                    );

            if (requestCode == REQUEST_CAMERA) {
                try {
                    revokeUriPermission(
                            cameraPhotoUri,
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    | Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                } catch (Exception ignored) {
                }
            }

            uploadPhoto(payload);

        } catch (Exception error) {
            toast(
                    "Foto gagal diproses: "
                            + error.getMessage()
            );
        }
    }

    private void uploadPhoto(
            ChatImageProcessor.ImagePayload payload
    ) {
        if (uploading || readOnly) {
            return;
        }

        uploading = true;
        progress.setVisibility(View.VISIBLE);
        attachButton.setEnabled(false);
        sendButton.setEnabled(false);

        new Thread(() -> {
            try {
                JSONObject response =
                        CustomerMessageApi.uploadImagePair(
                                UPLOAD_IMAGE_URL,
                                roomId,
                                "customer",
                                payload
                        );

                mainHandler.post(() -> {
                    uploading = false;
                    progress.setVisibility(View.GONE);
                    attachButton.setEnabled(true);
                    sendButton.setEnabled(true);

                    if (
                            response.optBoolean(
                                    "success",
                                    false
                            )
                    ) {
                        loadMessages(false);
                        cleanupCameraFile();
                        return;
                    }

                    showMessage(
                            "Foto gagal dikirim",
                            first(
                                    response.optString(
                                            "message"
                                    ),
                                    "Coba lagi."
                            ),
                            false
                    );
                });

            } catch (Exception error) {
                mainHandler.post(() -> {
                    uploading = false;
                    progress.setVisibility(View.GONE);
                    attachButton.setEnabled(true);
                    sendButton.setEnabled(true);

                    showMessage(
                            "Foto gagal dikirim",
                            first(
                                    error.getMessage(),
                                    "Periksa koneksi lalu coba lagi."
                            ),
                            false
                    );
                });
            }
        }).start();
    }

    private void cleanupCameraFile() {
        try {
            if (
                    cameraUsesMediaStore
                            && cameraPhotoUri != null
            ) {
                getContentResolver().delete(
                        cameraPhotoUri,
                        null,
                        null
                );

            } else if (
                    cameraPhotoFile != null
                            && cameraPhotoFile.exists()
            ) {
                cameraPhotoFile.delete();
            }

        } catch (Exception ignored) {
        }

        try {
            if (cameraPhotoUri != null) {
                revokeUriPermission(
                        cameraPhotoUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                | Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            }
        } catch (Exception ignored) {
        }

        cameraPhotoFile = null;
        cameraPhotoUri = null;
        cameraUsesMediaStore = false;
    }

    private void applyReadOnlyState() {
        if (inputCard == null) {
            return;
        }

        if (readOnly) {
            input.setEnabled(false);
            input.setHint(
                    "Percakapan ini hanya dapat dibaca"
            );

            attachButton.setEnabled(false);
            attachButton.setAlpha(0.45f);

            sendButton.setEnabled(false);
            sendButton.setText("Selesai");
            sendButton.setAlpha(0.55f);

            statusText.setText(
                    "Order selesai • riwayat hanya baca"
            );
        }
    }

    private void loadMessages(
            boolean showLoading
    ) {
        if (loading) {
            return;
        }

        loading = true;

        if (showLoading) {
            progress.setVisibility(View.VISIBLE);
        }

        int requestedLastId =
                firstLoad ? 0 : lastId;

        new Thread(() -> {
            try {
                String endpoint =
                        GET_CHAT_URL
                                + "?room_id="
                                + URLEncoder.encode(
                                roomId,
                                StandardCharsets.UTF_8.name()
                        );

                if (requestedLastId > 0) {
                    endpoint +=
                            "&last_id="
                                    + requestedLastId;
                }

                JSONObject response =
                        CustomerMessageApi.get(endpoint);

                mainHandler.post(() -> {
                    loading = false;
                    progress.setVisibility(View.GONE);

                    handleResponse(
                            response,
                            requestedLastId == 0
                    );
                });

            } catch (Exception error) {
                mainHandler.post(() -> {
                    loading = false;
                    progress.setVisibility(View.GONE);

                    statusText.setText(
                            "Koneksi chat bermasalah"
                    );
                });
            }
        }).start();
    }

    private void handleResponse(
            JSONObject response,
            boolean reset
    ) {
        orderStatus = response.optString(
                "status",
                orderStatus
        );

        boolean ended =
                response.optBoolean(
                        "ended",
                        false
                )
                        || CustomerMessageStatus
                        .isEnded(orderStatus);

        if (ended) {
            readOnly = true;
            applyReadOnlyState();

            mainHandler.removeCallbacks(
                    refreshRunnable
            );
        } else {
            statusText.setText(
                    CustomerMessageStatus
                            .orderLabel(
                                    orderStatus,
                                    orderType
                            )
            );
        }

        JSONObject driver =
                response.optJSONObject("driver");

        if (driver != null) {
            String serverName = first(
                    driver.optString("name"),
                    driver.optString("username"),
                    ""
            );

            if (!serverName.isEmpty()) {
                participantName = serverName;
                participantText.setText(serverName);
            }
        }

        if (
                !response.optBoolean(
                        "success",
                        false
                )
        ) {
            statusText.setText(
                    first(
                            response.optString(
                                    "message"
                            ),
                            "Gagal memuat chat"
                    )
            );

            return;
        }

        JSONArray array =
                response.optJSONArray(
                        "messages"
                );

        if (array == null) {
            return;
        }

        if (reset) {
            messagesBox.removeAllViews();
        }

        boolean added = false;

        for (
                int i = 0;
                i < array.length();
                i++
        ) {
            JSONObject message =
                    array.optJSONObject(i);

            if (message == null) {
                continue;
            }

            int id = message.optInt("id", 0);

            if (!reset && id <= lastId) {
                continue;
            }

            if (id > lastId) {
                lastId = id;
            }

            addBubble(message);
            added = true;
        }

        if (
                reset
                        && array.length() == 0
        ) {
            addSystemMessage(
                    "Belum ada pesan pada percakapan ini."
            );
        }

        firstLoad = false;

        if (added || reset) {
            scrollBottom();
        }
    }

    private void addBubble(JSONObject message) {
        String sender = CustomerMessageStatus.normalize(
                message.optString("sender_type", "")
        );
        boolean mine = sender.equals("customer");
        String content = message.optString("message", "");

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(mine ? Gravity.RIGHT : Gravity.LEFT);

        LinearLayout.LayoutParams wrapperLp =
                new LinearLayout.LayoutParams(-1, -2);
        wrapperLp.setMargins(0, dp(4), 0, dp(4));
        messagesBox.addView(wrapper, wrapperLp);

        if (
                content.startsWith(IMAGE_V2_PREFIX)
                        || content.startsWith(IMAGE_PREFIX)
        ) {
            String previewUrl;
            String hdUrl;

            if (content.startsWith(IMAGE_V2_PREFIX)) {
                String value = content.substring(
                        IMAGE_V2_PREFIX.length()
                ).trim();

                String[] parts = value.split(
                        "\\|",
                        2
                );

                previewUrl =
                        parts.length > 0
                                ? parts[0].trim()
                                : "";

                hdUrl =
                        parts.length > 1
                                ? parts[1].trim()
                                : previewUrl;

            } else {
                previewUrl = content.substring(
                        IMAGE_PREFIX.length()
                ).trim();

                hdUrl = previewUrl;
            }

            ImageView image = new ImageView(this);

            image.setScaleType(
                    ImageView.ScaleType.CENTER_CROP
            );

            image.setBackground(
                    round("#EAF1FA", 16)
            );

            wrapper.addView(
                    image,
                    new LinearLayout.LayoutParams(
                            dp(220),
                            dp(165)
                    )
            );

            loadRemoteImage(
                    image,
                    previewUrl
            );

            TextView hdHint = text(
                    "Ketuk untuk lihat HD",
                    9,
                    "#0B7CFF",
                    true
            );

            hdHint.setPadding(
                    dp(7),
                    dp(3),
                    dp(7),
                    0
            );

            wrapper.addView(
                    hdHint,
                    new LinearLayout.LayoutParams(
                            -2,
                            -2
                    )
            );

            String finalHdUrl = hdUrl;

            image.setOnClickListener(
                    view -> showHdImage(
                            finalHdUrl
                    )
            );

            hdHint.setOnClickListener(
                    view -> showHdImage(
                            finalHdUrl
                    )
            );

        } else {
            TextView bubble = text(
                    content,
                    13,
                    mine ? "#FFFFFF" : "#0F172A",
                    false
            );
            bubble.setPadding(dp(13), dp(9), dp(13), dp(9));
            bubble.setMaxWidth((int)(
                    getResources().getDisplayMetrics().widthPixels * 0.75
            ));
            bubble.setBackground(
                    mine
                            ? gradient("#086BFF", "#2EA2FF", 17)
                            : roundStroke("#FFFFFF", "#D7E6F8", 17, 1)
            );
            wrapper.addView(
                    bubble,
                    new LinearLayout.LayoutParams(-2, -2)
            );
        }

        String time = formatTime(message.optString("created_at", ""));
        if (!time.isEmpty()) {
            TextView timestamp = text(time, 9, "#94A3B8", false);
            timestamp.setPadding(dp(7), dp(2), dp(7), 0);
            wrapper.addView(
                    timestamp,
                    new LinearLayout.LayoutParams(-2, -2)
            );
        }
    }

    private void loadRemoteImage(ImageView target, String imageUrl) {
        final String fixed = absoluteUrl(imageUrl);

        new Thread(() -> {
            Bitmap bitmap = null;
            HttpURLConnection connection = null;

            try {
                connection = (HttpURLConnection)new URL(fixed).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setUseCaches(true);

                try (InputStream stream = connection.getInputStream()) {
                    bitmap = BitmapFactory.decodeStream(stream);
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }

            Bitmap result = bitmap;
            mainHandler.post(() -> {
                if (result != null) target.setImageBitmap(result);
                else target.setImageResource(
                        android.R.drawable.ic_menu_report_image
                );
            });
        }).start();
    }

    private void showHdImage(
            String imageUrl
    ) {
        final ImageView image =
                new ImageView(this);

        image.setScaleType(
                ImageView.ScaleType.FIT_CENTER
        );

        image.setAdjustViewBounds(true);
        image.setMinimumHeight(dp(260));

        final ProgressBar loadingHd =
                new ProgressBar(this);

        LinearLayout container =
                new LinearLayout(this);

        container.setOrientation(
                LinearLayout.VERTICAL
        );

        container.setGravity(Gravity.CENTER);
        container.setPadding(
                dp(8),
                dp(8),
                dp(8),
                dp(8)
        );

        container.addView(
                loadingHd,
                new LinearLayout.LayoutParams(
                        dp(44),
                        dp(44)
                )
        );

        container.addView(
                image,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Foto HD")
                        .setView(container)
                        .setNegativeButton(
                                "Tutup",
                                null
                        )
                        .create();

        dialog.show();

        final String fixed =
                absoluteUrl(imageUrl);

        new Thread(() -> {
            Bitmap bitmap = null;
            HttpURLConnection connection = null;

            try {
                connection =
                        (HttpURLConnection)
                                new URL(fixed)
                                        .openConnection();

                connection.setConnectTimeout(25000);
                connection.setReadTimeout(30000);
                connection.setUseCaches(true);

                try (
                        InputStream stream =
                                connection.getInputStream()
                ) {
                    bitmap =
                            BitmapFactory.decodeStream(
                                    stream
                            );
                }

            } catch (Exception ignored) {
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            Bitmap finalBitmap = bitmap;

            mainHandler.post(() -> {
                loadingHd.setVisibility(View.GONE);

                if (finalBitmap != null) {
                    image.setImageBitmap(finalBitmap);
                } else {
                    image.setImageResource(
                            android.R.drawable
                                    .ic_menu_report_image
                    );

                    toast(
                            "Gambar HD tidak dapat dimuat"
                    );
                }
            });
        }).start();
    }

    private void addSystemMessage(
            String value
    ) {
        TextView message = text(
                value,
                10,
                "#718096",
                false
        );

        message.setGravity(Gravity.CENTER);
        message.setPadding(
                dp(10),
                dp(8),
                dp(10),
                dp(8)
        );

        message.setBackground(
                round("#EAF1FA", 13)
        );

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        -2,
                        -2
                );

        lp.gravity = Gravity.CENTER;
        lp.setMargins(
                0,
                dp(10),
                0,
                dp(10)
        );

        messagesBox.addView(message, lp);
    }

    private void sendMessage() {
        if (readOnly || sending) return;

        String message = input.getText().toString().trim();
        if (message.isEmpty()) return;

        if (message.length() > 1000) {
            showMessage(
                    "Pesan terlalu panjang",
                    "Maksimal 1000 karakter.",
                    false
            );
            return;
        }

        sending = true;
        sendButton.setEnabled(false);
        sendButton.setText("...");
        final String originalMessage = message;

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("room_id", roomId);
                payload.put("sender_type", "customer");
                payload.put("message", originalMessage);
                payload.put("order_id", orderId);

                JSONObject response = CustomerMessageApi.post(
                        SEND_CHAT_URL,
                        payload
                );

                mainHandler.post(() -> {
                    if (response.optBoolean("success", false)) {
                        sending = false;
                        sendButton.setEnabled(true);
                        sendButton.setText("Kirim");
                        input.setText("");
                        loadMessages(false);
                    } else {
                        verifyDelivered(originalMessage);
                    }
                });

            } catch (Exception error) {
                mainHandler.post(() -> verifyDelivered(originalMessage));
            }
        }).start();
    }

    private void verifyDelivered(String originalMessage) {
        new Thread(() -> {
            boolean delivered = false;

            try {
                String endpoint = GET_CHAT_URL
                        + "?room_id="
                        + URLEncoder.encode(
                                roomId,
                                StandardCharsets.UTF_8.name()
                        );

                JSONObject response = CustomerMessageApi.get(endpoint);
                JSONArray array = response.optJSONArray("messages");

                if (array != null) {
                    for (
                            int i = array.length() - 1;
                            i >= 0 && i >= array.length() - 12;
                            i--
                    ) {
                        JSONObject item = array.optJSONObject(i);
                        if (item == null) continue;

                        if (
                                "customer".equalsIgnoreCase(
                                        item.optString("sender_type", "")
                                )
                                        && originalMessage.equals(
                                        item.optString("message", "")
                                )
                        ) {
                            delivered = true;
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            final boolean found = delivered;
            mainHandler.post(() -> {
                sending = false;
                sendButton.setEnabled(true);
                sendButton.setText("Kirim");

                if (found) {
                    input.setText("");
                    loadMessages(false);
                } else {
                    showMessage(
                            "Pesan belum terkirim",
                            "Koneksi bermasalah. Coba lagi.",
                            false
                    );
                }
            });
        }).start();
    }

    private void scrollBottom() {
        mainHandler.postDelayed(
                () -> {
                    try {
                        messagesScroll.fullScroll(
                                View.FOCUS_DOWN
                        );
                    } catch (Exception ignored) {
                    }
                },
                120
        );
    }

    private String normalizeRoom(String value) {
        String room =
                value == null
                        ? ""
                        : value.trim()
                        .replace('_', '-')
                        .toUpperCase(Locale.US);

        room = room.replaceAll(
                "[^A-Z0-9\\-]",
                ""
        );

        if (
                !room.isEmpty()
                        && !room.startsWith("ROOM-")
        ) {
            room = "ROOM-" + room;
        }

        return room;
    }

    private String serviceName(String type) {
        type = CustomerMessageStatus.normalize(type);

        if (type.contains("food")) {
            return "TransFood";
        }

        if (
                type.contains("car")
                        || type.contains("mobil")
        ) {
            return "TransCar";
        }

        return "TransRide";
    }

    private String formatTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }

        String[] formats = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss"
        };

        for (String format : formats) {
            try {
                Date date =
                        new SimpleDateFormat(
                                format,
                                Locale.US
                        ).parse(value.trim());

                if (date != null) {
                    return new SimpleDateFormat(
                            "dd MMM • HH:mm",
                            new Locale("id", "ID")
                    ).format(date);
                }

            } catch (Exception ignored) {
            }
        }

        return value;
    }

    private String absoluteUrl(String value) {
        String path = value == null
                ? ""
                : value.trim().replace("\\", "/");

        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }

        if (path.startsWith("/")) {
            return "https://transiva.my.id" + path;
        }

        return BASE_URL + path;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(11);
        button.setTextColor(Color.WHITE);
        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        button.setBackground(
                gradient(
                        "#086BFF",
                        "#2EA2FF",
                        13
                )
        );

        return button;
    }

    private TextView text(
            String value,
            int size,
            String color,
            boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(size);
        view.setTextColor(Color.parseColor(color));
        view.setIncludeFontPadding(false);

        if (bold) {
            view.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return view;
    }

    private GradientDrawable round(
            String color,
            int radius
    ) {
        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(Color.parseColor(color));
        drawable.setCornerRadius(dp(radius));

        return drawable;
    }

    private GradientDrawable roundStroke(
            String fill,
            String stroke,
            int radius,
            int width
    ) {
        GradientDrawable drawable =
                round(fill, radius);

        drawable.setStroke(
                dp(width),
                Color.parseColor(stroke)
        );

        return drawable;
    }

    private GradientDrawable gradient(
            String start,
            String end,
            int radius
    ) {
        GradientDrawable drawable =
                new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{
                                Color.parseColor(start),
                                Color.parseColor(end)
                        }
                );

        drawable.setCornerRadius(dp(radius));

        return drawable;
    }

    private int dp(int value) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private String first(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (
                    value != null
                            && !value.trim().isEmpty()
                            && !"null".equalsIgnoreCase(
                                    value.trim()
                            )
            ) {
                return value.trim();
            }
        }

        return "";
    }

    private void showMessage(
            String title,
            String message,
            boolean finishAfter
    ) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(
                        "OK",
                        (dialog, which) -> {
                            if (finishAfter) {
                                finish();
                            }
                        }
                )
                .show();
    }

    private void toast(String message) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        CustomerChatNotificationPoller.setOpenRoom(
                roomId
        );
    }

    @Override
    protected void onPause() {
        CustomerChatNotificationPoller.clearOpenRoom(
                roomId
        );

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;

        mainHandler.removeCallbacks(
                refreshRunnable
        );

        super.onDestroy();
    }
}
