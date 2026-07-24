package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.Locale;

public final class ChatVoiceNote {
    public static final String PREFIX = "[[VOICE]]";

    public interface Listener {
        void onState(String text, boolean recording, boolean cancelArmed);
        void onReady(File file, long durationMs);
        void onError(String message);
    }

    private ChatVoiceNote() {}

    public static void attachRecorder(
            Activity activity,
            Button button,
            int permissionRequestCode,
            Listener listener
    ) {
        button.setOnTouchListener(new View.OnTouchListener() {
            MediaRecorder recorder;
            File output;
            float downX;
            long startedAt;
            boolean recording;
            boolean cancelArmed;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (!button.isEnabled()) return true;

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                                != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(
                                    activity,
                                    new String[]{Manifest.permission.RECORD_AUDIO},
                                    permissionRequestCode
                            );
                            listener.onState("Izinkan mikrofon, lalu tahan lagi", false, false);
                            return true;
                        }

                        try {
                            output = new File(
                                    activity.getCacheDir(),
                                    "voice_" + System.currentTimeMillis() + ".m4a"
                            );
                            recorder = new MediaRecorder();
                            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                            recorder.setAudioEncodingBitRate(96000);
                            recorder.setAudioSamplingRate(44100);
                            recorder.setOutputFile(output.getAbsolutePath());
                            recorder.prepare();
                            recorder.start();
                            recording = true;
                            cancelArmed = false;
                            downX = event.getRawX();
                            startedAt = System.currentTimeMillis();
                            listener.onState("Merekam… geser kiri untuk batal", true, false);
                        } catch (Exception e) {
                            safeRelease(recorder);
                            recorder = null;
                            recording = false;
                            if (output != null) output.delete();
                            listener.onError("Gagal memulai rekaman suara");
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (recording) {
                            float distance = downX - event.getRawX();
                            boolean nextCancel = distance >= dp(activity, 90);
                            if (nextCancel != cancelArmed) {
                                cancelArmed = nextCancel;
                                listener.onState(
                                        cancelArmed ? "Lepas untuk membatalkan" : "Merekam… geser kiri untuk batal",
                                        true,
                                        cancelArmed
                                );
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!recording) return true;
                        long duration = Math.max(0, System.currentTimeMillis() - startedAt);
                        boolean cancel = cancelArmed || event.getActionMasked() == MotionEvent.ACTION_CANCEL;
                        recording = false;
                        try {
                            recorder.stop();
                        } catch (Exception ignored) {
                            cancel = true;
                        }
                        safeRelease(recorder);
                        recorder = null;

                        if (cancel || duration < 450) {
                            if (output != null) output.delete();
                            listener.onState(cancel ? "Voice note dibatalkan" : "Rekaman terlalu singkat", false, false);
                        } else {
                            listener.onState("Mengirim voice note…", false, false);
                            listener.onReady(output, duration);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    public static String encode(String url, long durationMs) {
        return PREFIX + (url == null ? "" : url.trim()) + "|" + Math.max(0, durationMs);
    }

    public static boolean isVoice(String message) {
        return message != null && message.startsWith(PREFIX);
    }

    public static String voiceUrl(String message) {
        if (!isVoice(message)) return "";
        String raw = message.substring(PREFIX.length()).trim();
        int split = raw.lastIndexOf('|');
        return split >= 0 ? raw.substring(0, split).trim() : raw;
    }

    public static long voiceDuration(String message) {
        if (!isVoice(message)) return 0;
        String raw = message.substring(PREFIX.length()).trim();
        int split = raw.lastIndexOf('|');
        if (split < 0) return 0;
        try { return Long.parseLong(raw.substring(split + 1).trim()); }
        catch (Exception ignored) { return 0; }
    }

    public static View createPlayerBubble(Activity activity, String message, boolean mine) {
        String url = voiceUrl(message);
        long durationMs = voiceDuration(message);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(activity, 10), dp(activity, 7), dp(activity, 12), dp(activity, 7));
        box.setBackground(background(
                mine ? "#0B7CFF" : "#FFFFFF",
                mine ? "#0B7CFF" : "#D7E6F8"
        ));

        Button play = new Button(activity);
        play.setAllCaps(false);
        play.setText("▶");
        play.setTextSize(16);
        play.setTextColor(Color.parseColor(mine ? "#0B7CFF" : "#0F172A"));
        play.setPadding(0, 0, 0, 0);
        play.setBackground(background("#FFFFFF", "#FFFFFF"));
        box.addView(play, new LinearLayout.LayoutParams(dp(activity, 38), dp(activity, 38)));

        TextView label = new TextView(activity);
        label.setText(String.format(Locale.US, "Voice note  %s", durationLabel(durationMs)));
        label.setTextSize(12);
        label.setTextColor(Color.parseColor(mine ? "#FFFFFF" : "#0F172A"));
        label.setPadding(dp(activity, 9), 0, 0, 0);
        box.addView(label, new LinearLayout.LayoutParams(dp(activity, 150), -2));

        play.setOnClickListener(v -> {
            if (url.isEmpty()) return;
            play.setEnabled(false);
            play.setText("…");
            MediaPlayer player = new MediaPlayer();
            try {
                player.setDataSource(url);
                player.setOnPreparedListener(mp -> {
                    play.setText("■");
                    play.setEnabled(true);
                    mp.start();
                });
                player.setOnCompletionListener(mp -> {
                    play.setText("▶");
                    try { mp.release(); } catch (Exception ignored) {}
                });
                player.setOnErrorListener((mp, what, extra) -> {
                    play.setText("▶");
                    play.setEnabled(true);
                    try { mp.release(); } catch (Exception ignored) {}
                    return true;
                });
                player.prepareAsync();
            } catch (Exception e) {
                play.setText("▶");
                play.setEnabled(true);
                try { player.release(); } catch (Exception ignored) {}
            }
        });

        return box;
    }

    private static String durationLabel(long durationMs) {
        long total = Math.max(0, durationMs / 1000L);
        return String.format(Locale.US, "%d:%02d", total / 60L, total % 60L);
    }

    private static GradientDrawable background(String fill, String stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(fill));
        d.setCornerRadius(36f);
        d.setStroke(1, Color.parseColor(stroke));
        return d;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static void safeRelease(MediaRecorder recorder) {
        if (recorder == null) return;
        try { recorder.reset(); } catch (Exception ignored) {}
        try { recorder.release(); } catch (Exception ignored) {}
    }
}
