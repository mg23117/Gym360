package com.example.gym360.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;

import com.example.gym360.data.local.AppDatabase;
import com.example.gym360.data.local.dao.UserDao;
import com.example.gym360.data.local.entity.UserEntity;
import com.example.gym360.domain.model.UserProfile;
import com.example.gym360.domain.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UserRepositoryImpl implements UserRepository {
    private static final String TAG = "Gym360UserRegister";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final UserDao userDao;

    // Recuperacion del alta de Auth si la escritura Firestore falla en esta pantalla.
    // No es un sistema completo de sincronizacion de usuarios.
    private String pendingRegistrationUid;

    public UserRepositoryImpl(@NonNull Context context) {
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        userDao = AppDatabase.getInstance(context.getApplicationContext()).userDao();
    }

    @Override
    public void register(@NonNull String fullName, @NonNull String email,
                         @NonNull String password, @NonNull RegisterCallback callback) {
        final String name = fullName.trim();
        final String address = email.trim();
        if (name.length() < 7 || address.isEmpty() || password.isEmpty()) {
            callback.onError("Revisa los datos del registro");
            return;
        }

        FirebaseUser existing = auth.getCurrentUser();
        if (existing != null) {
            // Permite reintentar Firestore/Room sin crear una segunda cuenta Auth.
            if (pendingRegistrationUid != null
                    && pendingRegistrationUid.equals(existing.getUid())
                    && address.equalsIgnoreCase(existing.getEmail())) {
                persistProfile(existing, name, callback);
            } else {
                callback.onError("Hay una sesion activa. Cierra sesion antes de crear otra cuenta.");
            }
            return;
        }

        auth.createUserWithEmailAndPassword(address, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser newUser = result.getUser();
                    if (newUser == null) {
                        callback.onError("La cuenta se creo, pero no se pudo recuperar la sesion");
                        return;
                    }
                    pendingRegistrationUid = newUser.getUid();
                    // El nombre en Firebase Auth permite mostrarlo sin leer Firestore.
                    UserProfileChangeRequest change = new UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build();
                    newUser.updateProfile(change).addOnCompleteListener(profileTask -> {
                        if (!profileTask.isSuccessful()) {
                            Log.w(TAG, "No se actualizo displayName en Auth", profileTask.getException());
                        }
                        persistProfile(newUser, name, callback);
                    });
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "No se pudo crear la cuenta Auth", e);
                    callback.onError("No se pudo crear la cuenta. Comprueba el correo, la clave y la conexion.");
                });
    }

    private void persistProfile(@NonNull FirebaseUser user,
                                @NonNull String fullName,
                                @NonNull RegisterCallback callback) {
        if (user.getEmail() == null) {
            callback.onError("La cuenta no tiene un correo valido");
            return;
        }
        UserProfile profile = new UserProfile(user.getUid(), fullName, user.getEmail());

        Map<String, Object> document = new HashMap<>();
        document.put("uid", profile.getUid());
        document.put("fullName", profile.getFullName());
        document.put("email", profile.getEmail());
        document.put("createdAt", FieldValue.serverTimestamp());

        // Si Firestore ya guardo el perfil antes de un fallo local, no repetir
        // .set(): las reglas de users solo permiten create, no update.
        DocumentReference reference = firestore.collection("users")
                .document(profile.getUid());
        reference.get(Source.SERVER)
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        if (!profile.getUid().equals(snapshot.getString("uid"))
                                || !profile.getEmail().equals(snapshot.getString("email"))) {
                            callback.onError("El perfil remoto no coincide con la sesion actual");
                            return;
                        }
                        saveLocally(profile, callback);
                    } else {
                        reference.set(document)
                                .addOnSuccessListener(unused -> saveLocally(profile, callback))
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Auth creado, pero Firestore rechazo el perfil", e);
                                    callback.onError("Cuenta Auth creada, pero fallo el perfil " +
                                            "Firestore. Revisa las reglas y vuelve a pulsar " +
                                            "Registrar sin salir de esta pantalla.");
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error consultando perfil Firestore", e);
                    callback.onError("No se pudo comprobar el perfil remoto. " +
                            "Comprueba la conexion y vuelve a pulsar Registrar.");
                });
    }

    private void saveLocally(@NonNull UserProfile profile,
                             @NonNull RegisterCallback callback) {
        IO.execute(() -> {
            try {
                userDao.upsert(new UserEntity(
                        profile.getUid(), profile.getFullName(), profile.getEmail()));
                pendingRegistrationUid = null;
                MAIN.post(callback::onSuccess);
            } catch (Exception e) {
                Log.e(TAG, "Perfil remoto creado, pero fallo Room", e);
                MAIN.post(() -> callback.onError(
                        "Cuenta y perfil remoto creados; fallo el guardado local. " +
                                "No vuelvas a registrar el correo: revisa Room."));
            }
        });

    }
}

