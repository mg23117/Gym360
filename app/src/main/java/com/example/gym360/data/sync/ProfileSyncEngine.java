package com.example.gym360.data.sync;

import androidx.annotation.NonNull;
import com.example.gym360.data.local.dao.UserDao;
import com.example.gym360.data.local.entity.UserEntity;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.TimeUnit;

/** Sincroniza SOLO el nombre pendiente del UID autenticado. Nunca sincroniza otra cuenta. */
public final class ProfileSyncEngine {
    private static final Object LOCK = new Object();
    private final UserDao dao;

    public ProfileSyncEngine(@NonNull UserDao dao) {
        this.dao = dao;
    }

    /** Llamar solo desde un hilo en segundo plano. */
    public void syncBlocking(@NonNull String uid) throws Exception {
        synchronized (LOCK) {
            FirebaseUser user = requireSameUser(uid);
            UserEntity local = dao.findByUid(uid);
            if (local == null || !UserEntity.PENDING.equals(local.getSyncStatus())) return;

            String nameToUpload = local.getFullName();
            Tasks.await(FirebaseFirestore.getInstance()
                            .collection("users").document(uid)
                            .update("fullName", nameToUpload),
                    25, TimeUnit.SECONDS);
            user = requireSameUser(uid);
            // Mantener también FirebaseAuth.displayName consistente con el perfil.
            Tasks.await(user.updateProfile(new UserProfileChangeRequest.Builder()
                            .setDisplayName(nameToUpload).build()),
                    25, TimeUnit.SECONDS);
            requireSameUser(uid);
            dao.markNameSynced(uid, nameToUpload);
        }
    }

    private static FirebaseUser requireSameUser(String uid) {
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current == null || !uid.equals(current.getUid())) {
            throw new IllegalStateException("No hay una sesión válida para este perfil");
        }
        return current;
    }
}
