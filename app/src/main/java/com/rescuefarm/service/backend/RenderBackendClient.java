package com.rescuefarm.service.backend;

import android.os.Handler;
import android.os.Looper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.rescuefarm.BuildConfig;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** HTTP bridge to the optional Render-hosted trusted Firebase Admin API. */
public final class RenderBackendClient {
    public interface Callback { void onSuccess(JSONObject body); void onError(String message); }
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private RenderBackendClient() { }

    public static void post(String path, JSONObject body, Callback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { callback.onError("Bạn cần đăng nhập lại."); return; }
        user.getIdToken(false).addOnSuccessListener(token -> send(path, body, token.getToken(), callback))
                .addOnFailureListener(error -> callback.onError("Không lấy được phiên đăng nhập."));
    }

    public static void postGuest(String path, JSONObject body, Callback callback) {
        send(path, body, null, callback);
    }

    private static void send(String path, JSONObject body, String token, Callback callback) {
        String base = BuildConfig.RENDER_BACKEND_URL == null ? "" : BuildConfig.RENDER_BACKEND_URL.trim();
        if (base.isEmpty()) { callback.onError("Chưa cấu hình URL Render backend."); return; }
        IO.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(base.replaceAll("/$", "") + path).openConnection();
                connection.setRequestMethod("POST"); connection.setConnectTimeout(30000); connection.setReadTimeout(120000);
                connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
                byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(data.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(data); }
                int status = connection.getResponseCode();
                java.io.InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                if (stream != null) try (java.io.InputStream input = stream) {
                    byte[] buffer = new byte[4096]; int read;
                    while ((read = input.read(buffer)) != -1) bytes.write(buffer, 0, read);
                }
                JSONObject response = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                if (status < 200 || status >= 300) throw new IllegalStateException("HTTP " + status + ": " + response.optString("error", "Không thực hiện được yêu cầu."));
                MAIN.post(() -> callback.onSuccess(response));
            } catch (Exception error) {
                String message = error.getLocalizedMessage() == null ? "Không kết nối được Render backend." : error.getLocalizedMessage();
                MAIN.post(() -> callback.onError(message));
            } finally { if (connection != null) connection.disconnect(); }
        });
    }
}
