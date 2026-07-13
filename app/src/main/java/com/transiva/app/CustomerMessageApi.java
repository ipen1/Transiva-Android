package com.transiva.app;

import android.graphics.Bitmap;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class CustomerMessageApi {
    private static final int TIMEOUT_MS = 30000;
    private CustomerMessageApi() {}

    public static JSONObject get(String endpoint) throws Exception {
        return request("GET", endpoint, null);
    }

    public static JSONObject post(String endpoint, JSONObject body) throws Exception {
        return request("POST", endpoint, body);
    }

    public static JSONObject uploadWebp(
            String endpoint,
            String roomId,
            String senderType,
            Bitmap bitmap
    ) throws Exception {
        String boundary = "----TransivaChat" + System.currentTimeMillis();
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection)new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty(
                    "Content-Type",
                    "multipart/form-data; boundary=" + boundary
            );

            ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.WEBP, 72, imageBytes);

            try (OutputStream output = connection.getOutputStream()) {
                writeField(output, boundary, "room_id", roomId);
                writeField(output, boundary, "sender_type", senderType);
                output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                output.write(("Content-Disposition: form-data; name=\"image\"; filename=\"chat.webp\"\r\n").getBytes(StandardCharsets.UTF_8));
                output.write("Content-Type: image/webp\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                output.write(imageBytes.toByteArray());
                output.write("\r\n".getBytes(StandardCharsets.UTF_8));
                output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                output.flush();
            }

            return readResponse(connection);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static JSONObject request(
            String method,
            String endpoint,
            JSONObject body
    ) throws Exception {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection)new URL(endpoint).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Cache-Control", "no-cache");

            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );

                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
            }

            return readResponse(connection);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static JSONObject readResponse(HttpURLConnection connection) throws Exception {
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 400
                ? connection.getInputStream()
                : connection.getErrorStream();

        if (stream == null) throw new IllegalStateException("Respons server kosong");

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        );
        StringBuilder raw = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) raw.append(line);
        reader.close();

        String body = raw.toString().trim();
        if (body.isEmpty()) throw new IllegalStateException("Respons server kosong");

        int firstBrace = body.indexOf('{');
        int lastBrace = body.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            body = body.substring(firstBrace, lastBrace + 1);
        }

        JSONObject response = new JSONObject(body);
        if (status < 200 || status >= 400) {
            throw new IllegalStateException(
                    response.optString("message", "HTTP " + status)
            );
        }
        return response;
    }

    private static void writeField(
            OutputStream output,
            String boundary,
            String name,
            String value
    ) throws Exception {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
}
