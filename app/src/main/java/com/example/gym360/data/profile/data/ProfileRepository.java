package com.example.gym360.data.profile.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.gym360.data.local.AppDatabase;
import com.example.gym360.data.local.dao.UserDao;
import com.example.gym360.data.local.entity.UserEntity;
import com.example.gym360.data.sync.ProfileSyncScheduler;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Perfil del usuario autenticado: Room local primero, Firestore remoto después. */
public final class ProfileRepository {
    private static final String TAG = "Gym360Profile";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(@NonNull UserEntity profile);
        void onError(@NonNull String message);
    }
    public interface ActionCallback {
        void onSuccess();
        void onError(@NonNull String message);
    }

    private final Context appContext;
    private final UserDao dao;

    public ProfileRepository(@NonNull Context context) {
        appContext = context.getApplicationContext();
        dao = AppDatabase.getInstance(appContext).userDao();
    }

    private FirebaseUser currentUser() {
        return FirebaseAuth.getInstance().getCurrentUser();
    }

    public void loadProfile(@NonNull Callback callback) {
        FirebaseUser user = currentUser();
        if (user == null) {
            callback.onError("Debes iniciar sesión");
            return;
        }
        String uid = user.getUid();
        // Mostrar inmediatamente la copia local, incluso si no hay Internet.
        IO.execute(() -> {
            UserEntity local = dao.findByUid(uid);
            if (local != null) MAIN.post(() -> callback.onSuccess(local));
        });
        // Actualizar desde el servidor, sin sobreescribir cambios locales pendientes.
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .get(Source.SERVER)
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        callback.onError("No existe el perfil remoto de esta cuenta");
                        return;
                    }
                    String name = snapshot.getString("fullName");
                    String email = snapshot.getString("email");
                    if (name == null || email == null) {
                        callback.onError("El perfil remoto está incompleto");
                        return;
                    }
                    IO.execute(() -> {
                        if (currentUser() == null || !uid.equals(currentUser().getUid())) return;
                        UserEntity local = dao.findByUid(uid);
                        if (local != null && UserEntity.PENDING.equals(local.getSyncStatus())) {
                            MAIN.post(() -> callback.onSuccess(local));
                            ProfileSyncScheduler.enqueue(appContext, uid);
                        } else {
                            UserEntity serverCopy = new UserEntity(uid, name, email);
                            dao.upsert(serverCopy);
                            MAIN.post(() -> callback.onSuccess(serverCopy));
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    Log.d(TAG, "Perfil remoto no disponible; se usa Room", e);
                    // Si no hay red se usa la copia local sin bloquear la pantalla.
                });
    }

    public void updateName(@NonNull String value, @NonNull ActionCallback callback) {
        String newName = value.trim();
        if (newName.length() < 7 || newName.length() > 120) {
            callback.onError("El nombre debe tener entre 7 y 120 caracteres");
            return;
        }
        FirebaseUser user = currentUser();
        if (user == null || user.getEmail() == null) {
            callback.onError("Debes iniciar sesión para editar el perfil");
            return;
        }
        String uid = user.getUid();
        String email = user.getEmail();
        IO.execute(() -> {
            try {
                UserEntity local = dao.findByUid(uid);
                if (local == null) {
                    local = new UserEntity(uid, newName, email);
                    local.setSyncStatus(UserEntity.PENDING);
                    dao.upsert(local);
                } else {
                    dao.updateNamePending(uid, newName);
                }
                MAIN.post(() -> {
                    callback.onSuccess();
                    ProfileSyncScheduler.enqueue(appContext, uid);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error guardando el nombre en Room", e);
                MAIN.post(() -> callback.onError("No se pudo guardar el nombre localmente"));
            }
        });
    }

    public void requestSync(@NonNull ActionCallback callback) {
        FirebaseUser user = currentUser();
        if (user == null) {
            callback.onError("Debes iniciar sesión para sincronizar");
            return;
        }
        try {
            ProfileSyncScheduler.enqueue(appContext, user.getUid());
            callback.onSuccess(); // Programado, NO confirmado en Firestore todavía.
        } catch (RuntimeException e) {
            callback.onError("No se pudo programar la sincronización");
        }
    }

    public void getLocalStatus(@NonNull Callback callback) {
        FirebaseUser user = currentUser();
        if (user == null) {
            callback.onError("Sin sesión");
            return;
        }
        IO.execute(() -> {
            UserEntity local = dao.findByUid(user.getUid());
            if (local != null) MAIN.post(() -> callback.onSuccess(local));
            else MAIN.post(() -> callback.onError("Sin perfil local"));
        });
    }
}
