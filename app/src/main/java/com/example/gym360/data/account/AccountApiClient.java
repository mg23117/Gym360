package com.example.gym360.data.account;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Este va a ser el cliente HTTPS autenticado para el backend */
public class AccountApiClient {
    private static final String BASE_URL = "https://gym360-8rj7.onrender.com";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);

    public interface Callback {
        void onSuccess(@NonNull Map<String, Object> response);
        void onError(@NonNull String message);
    }

    public void post(@NonNull String route, @NonNull Map<String, Object> payload, @NonNull Callback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Debes iniciar sesión.");
            return;
        }
        String userUid = user.getUid();
        user.getIdToken(false).addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null ||
                    task.getResult().getToken() == null) {
                callback.onError("No se pudo obtener la sesión de Firebase.");
                return;
            }

            String token = task.getResult().getToken();
            IO.execute(() -> {
                try {
                    Map<String, Object> receipt = request(route, payload, token);
                    MAIN.post(() -> {
                        if (!sameSession(userUid)) {
                            callback.onError("La sesión cambió durante la operación. Verifica tu historial.");
                            return;
                        }
                        callback.onSuccess(receipt);
                    });
                } catch (Exception error) {
                    String message = error.getMessage();
                    MAIN.post(() -> callback.onError(message == null
                            ? "No se pudo completar la solicitud." : message));
                }
            });
        });
    }

    private static boolean sameSession(String uid) {
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        return current != null && uid.equals(current.getUid());
    }

    private static Map<String, Object> request(String route, Map<String, Object> data, String token) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(BASE_URL + route);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20_000);
            connection.setReadTimeout(100_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + token);

            byte[] bytes = new JSONObject(data).toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = readBody(stream);
            JSONObject json = body.isEmpty() ? new JSONObject() : new JSONObject(body);

            if (code < 200 || code >= 300) {
                String errorMessage = json.optString("error", "Error HTTP " + code);
                throw new IOException(errorMessage);
            }

            Map<String, Object> response = new HashMap<>();
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                response.put(key, json.get(key));
            }
            return response;

        } catch (SocketTimeoutException e) {
            throw new IOException(
                    "Tiempo de espera agotado. La operación podría haberse completado; " +
                            "reintenta sin cambiar los datos para evitar cobros duplicados.", e);
        } catch (JSONException e) {
            throw new IOException("La API devolvió una respuesta JSON inválida.", e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readBody(InputStream input) throws IOException {
        if (input == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
                if (builder.length() > 65_536) {
                    throw new IOException("Respuesta del servidor demasiado grande.");
                }
            }
        }
        return builder.toString();
    }

    public static String baseUrl() {
        return BASE_URL.replaceAll("/+$", "");
    }
}
