package com.transiva.app;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.InputStream;

public final class ChatImageProcessor {
    private static final int MAX_SIDE = 1280;
    private ChatImageProcessor() {}

    public static Bitmap fromUri(ContentResolver resolver, Uri uri) throws Exception {
        if (resolver == null || uri == null) throw new IllegalArgumentException("Foto tidak ditemukan");

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = resolver.openInputStream(uri)) {
            BitmapFactory.decodeStream(stream, null, bounds);
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
        options.inPreferredConfig = Bitmap.Config.RGB_565;

        Bitmap decoded;
        try (InputStream stream = resolver.openInputStream(uri)) {
            decoded = BitmapFactory.decodeStream(stream, null, options);
        }

        if (decoded == null) throw new IllegalStateException("Foto tidak dapat dibaca");
        return scale(decoded);
    }

    public static Bitmap fromCamera(Bitmap bitmap) {
        return bitmap == null ? null : scale(bitmap);
    }

    private static Bitmap scale(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= MAX_SIDE && height <= MAX_SIDE) return source;

        float ratio = Math.min((float)MAX_SIDE / width, (float)MAX_SIDE / height);
        Bitmap scaled = Bitmap.createScaledBitmap(
                source,
                Math.max(1, Math.round(width * ratio)),
                Math.max(1, Math.round(height * ratio)),
                true
        );
        if (scaled != source) source.recycle();
        return scaled;
    }

    private static int sampleSize(int width, int height) {
        int sample = 1;
        while (width / sample > MAX_SIDE * 2 || height / sample > MAX_SIDE * 2) sample *= 2;
        return Math.max(1, sample);
    }
}
