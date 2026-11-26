package com.ausgram1.arqms.nativeapp;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiClient {

    public static final String API_URL =
            "https://script.google.com/macros/s/AKfycbx6j-gS7p807DJLwf6AlMl-7x1NKiUjq80mJMZ5271UzLRy6lG4i-0gHXJkalAMM94z/exec";

    private static final ExecutorService EXEC = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onResult(JSONObject json);
        void onError(String message);
    }

    public static void post(JSONObject payload, Callback cb) {
        EXEC.execute(() -> {
            try {
                if (SessionStore.token != null && !SessionStore.token.isEmpty() && !payload.has("token")) {
                    payload.put("token", SessionStore.token);
                }
                String body = payload.toString();
                String respText = postFollowGasRedirect(API_URL, body);
                JSONObject json = new JSONObject(respText);
                MAIN.post(() -> cb.onResult(json));
            } catch (Exception e) {
                String msg = (e.getMessage() != null && !e.getMessage().isEmpty())
                        ? e.getMessage() : "Network error. Check internet connection.";
                MAIN.post(() -> cb.onError(msg));
            }
        });
    }

    private static String postFollowGasRedirect(String urlStr, String jsonBody) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        byte[] outBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(outBytes);
            os.flush();
        }

        int code = conn.getResponseCode();
        if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
            String location = conn.getHeaderField("Location");
            conn.disconnect();
            if (location == null || location.isEmpty()) {
                throw new Exception("Empty redirect from Apps Script.");
            }
            HttpURLConnection getConn = (HttpURLConnection) new URL(location).openConnection();
            getConn.setRequestMethod("GET");
            getConn.setConnectTimeout(20000);
            getConn.setReadTimeout(30000);
            return readStream(getConn);
        }
        return readStream(conn);
    }

    private static String readStream(HttpURLConnection conn) throws Exception {
        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) throw new Exception("Empty response (HTTP " + status + ")");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } finally {
            conn.disconnect();
        }
        String text = sb.toString().trim();
        if (!text.startsWith("{")) {
            throw new Exception("Server returned non-JSON. Make sure MobileApi.gs is deployed as New Version in Apps Script.");
        }
        return text;
    }
}
