package com.example.gym360.data.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import com.example.gym360.data.account.AccountApiClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;

/** Solo para los administradores, porque el backend valida el rol y sube imágenes a Cloudinary. */
public final class StoreImageUploader {
    public interface Callback {
        void success(String imageUrl);

        void failure(String reason);
    }

    private StoreImageUploader() {
    }

    public static void upload(@NonNull Context context, @NonNull Uri uri, @NonNull Callback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.failure("Debes iniciar sesión.");
            return;
        }
        user.getIdToken(false).addOnSuccessListener(token -> {
            Executors.newSingleThreadExecutor().execute(() -> {
                HttpURLConnection conn = null;
                try {
                    byte[] bytes;
                    try (InputStream in = context.getContentResolver().openInputStream(uri);
                         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                        if (in == null)
                            throw new IllegalArgumentException("No se pudo abrir la imagen.");
                        byte[] chunk = new byte[8192];
                        int n;
                        while ((n = in.read(chunk)) != -1) {
                            if (out.size() + n > 5 * 1024 * 1024)
                                throw new IllegalArgumentException("Imagen mayor de 5 MB.");
                            out.write(chunk, 0, n);
                        }
                        bytes = out.toByteArray();
                    }
                    String base = AccountApiClient.baseUrl();
                    String boundary = "gym360" + UUID.randomUUID().toString().replace("-", "");
                    conn = (HttpURLConnection) new URL(base + "/api/products/image").openConnection();
                    conn.setInstanceFollowRedirects(false);
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(20000);
                    conn.setReadTimeout(100000);
                    conn.setRequestProperty("Authorization", "Bearer " + token.getToken());
                    conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                    String mime = context.getContentResolver().getType(uri);
                    if (!"image/png".equals(mime) && !"image/webp".equals(mime))
                        mime = "image/jpeg";
                    String header = "--" + boundary
                            + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"producto\"\r\n" +
                            "Content-Type: " + mime + "\r\n\r\n";
                    byte[] a = header.getBytes(StandardCharsets.UTF_8),
                            b = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
                    try (OutputStream out = conn.getOutputStream()) {
                        out.write(a);
                        out.write(bytes);
                        out.write(b);
                    }
                    int code = conn.getResponseCode();
                    InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    try (InputStream in = stream) {
                        if (in != null) {
                            byte[] chunk = new byte[2048];
                            int n;
                            while ((n = in.read(chunk)) != -1) {
                                if (output.size() + n > 65536)
                                    break;
                                output.write(chunk, 0, n);
                            }
                        }
                    }
                    JSONObject json = new JSONObject(output.toString("UTF-8"));
                    if (code < 200 || code >= 300)
                        throw new IllegalStateException(json.optString("error", "Error al subir fotografía."));
                    String imageUrl = json.getString("imageUrl");
                    new Handler(Looper.getMainLooper()).post(() -> callback.success(imageUrl));
                } catch (Exception ex) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.failure(ex.getMessage()));
                } finally {
                    if (conn != null)
                        conn.disconnect();
                }
            });
        }).addOnFailureListener(e -> callback.failure("No se pudo validar la sesión."));
    }
}
