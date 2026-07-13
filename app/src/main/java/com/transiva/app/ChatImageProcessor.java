package com.transiva.app;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public final class ChatImageProcessor {

    private static final int PREVIEW_MAX_SIDE = 960;
    private static final int HD_MAX_SIDE = 2560;

    private static final int PREVIEW_QUALITY = 80;
    private static final int HD_QUALITY = 90;

    private ChatImageProcessor() {
    }

    public static final class ImagePayload {
        public final byte[] previewWebp;
        public final byte[] hdWebp;
        public final int originalWidth;
        public final int originalHeight;

        private ImagePayload(
                byte[] previewWebp,
                byte[] hdWebp,
                int originalWidth,
                int originalHeight
        ) {
            this.previewWebp = previewWebp;
            this.hdWebp = hdWebp;
            this.originalWidth = originalWidth;
            this.originalHeight = originalHeight;
        }
    }

    public static ImagePayload fromUri(
            ContentResolver resolver,
            Uri uri
    ) throws Exception {
        if (resolver == null || uri == null) {
            throw new IllegalArgumentException(
                    "Foto tidak ditemukan"
            );
        }

        Bitmap source = decode(
                resolver,
                uri,
                HD_MAX_SIDE
        );

        if (source == null) {
            throw new IllegalStateException(
                    "Foto tidak dapat dibaca"
            );
        }

        return createPayload(source);
    }

    public static ImagePayload fromBitmap(
            Bitmap bitmap
    ) throws Exception {
        if (bitmap == null) {
            throw new IllegalArgumentException(
                    "Foto tidak ditemukan"
            );
        }

        return createPayload(bitmap);
    }

    private static ImagePayload createPayload(
            Bitmap source
    ) throws Exception {
        int originalWidth = source.getWidth();
        int originalHeight = source.getHeight();

        Bitmap hd = scaleInside(
                source,
                HD_MAX_SIDE
        );

        Bitmap preview = scaleInside(
                hd,
                PREVIEW_MAX_SIDE
        );

        byte[] previewBytes = compress(
                preview,
                PREVIEW_QUALITY
        );

        byte[] hdBytes = compress(
                hd,
                HD_QUALITY
        );

        if (
                preview != hd
                        && preview != source
        ) {
            preview.recycle();
        }

        if (hd != source) {
            hd.recycle();
        }

        if (!source.isRecycled()) {
            source.recycle();
        }

        return new ImagePayload(
                previewBytes,
                hdBytes,
                originalWidth,
                originalHeight
        );
    }

    private static Bitmap decode(
            ContentResolver resolver,
            Uri uri,
            int maxSide
    ) throws Exception {
        BitmapFactory.Options bounds =
                new BitmapFactory.Options();

        bounds.inJustDecodeBounds = true;

        try (
                InputStream stream =
                        resolver.openInputStream(uri)
        ) {
            BitmapFactory.decodeStream(
                    stream,
                    null,
                    bounds
            );
        }

        BitmapFactory.Options options =
                new BitmapFactory.Options();

        options.inSampleSize =
                sampleSize(
                        bounds.outWidth,
                        bounds.outHeight,
                        maxSide
                );

        options.inPreferredConfig =
                Bitmap.Config.ARGB_8888;

        try (
                InputStream stream =
                        resolver.openInputStream(uri)
        ) {
            return BitmapFactory.decodeStream(
                    stream,
                    null,
                    options
            );
        }
    }

    private static byte[] compress(
            Bitmap bitmap,
            int quality
    ) throws Exception {
        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        boolean success = bitmap.compress(
                Bitmap.CompressFormat.WEBP,
                quality,
                output
        );

        if (!success) {
            throw new IllegalStateException(
                    "Foto gagal dikompres"
            );
        }

        return output.toByteArray();
    }

    private static Bitmap scaleInside(
            Bitmap source,
            int maxSide
    ) {
        int width = source.getWidth();
        int height = source.getHeight();

        if (
                width <= maxSide
                        && height <= maxSide
        ) {
            return source;
        }

        float ratio = Math.min(
                (float) maxSide / width,
                (float) maxSide / height
        );

        int targetWidth =
                Math.max(
                        1,
                        Math.round(width * ratio)
                );

        int targetHeight =
                Math.max(
                        1,
                        Math.round(height * ratio)
                );

        return Bitmap.createScaledBitmap(
                source,
                targetWidth,
                targetHeight,
                true
        );
    }

    private static int sampleSize(
            int width,
            int height,
            int maxSide
    ) {
        int sample = 1;

        while (
                width / sample > maxSide * 2
                        || height / sample > maxSide * 2
        ) {
            sample *= 2;
        }

        return Math.max(1, sample);
    }
}
