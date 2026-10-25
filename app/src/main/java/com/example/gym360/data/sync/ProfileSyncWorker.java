package com.example.gym360.data.sync;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.gym360.data.local.AppDatabase;
import com.example.gym360.data.local.dao.UserDao;
import com.example.gym360.data.local.entity.UserEntity;
import com.google.firebase.firestore.FirebaseFirestoreException;

public final class ProfileSyncWorker extends Worker {
    public static final String KEY_UID = "user_uid";
    private static final String TAG = "Gym360ProfileSync";

    public ProfileSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override public Result doWork() {
        String uid = getInputData().getString(KEY_UID);
        if (uid == null || uid.isEmpty()) return Result.failure();
        try {
            UserDao dao = AppDatabase.getInstance(getApplicationContext()).userDao();
            new ProfileSyncEngine(dao).syncBlocking(uid);
            UserEntity state = dao.findByUid(uid);
            if (state != null && UserEntity.PENDING.equals(state.getSyncStatus())) {
                return Result.retry();
            }
            return Result.success();
        } catch (Exception e) {
            Log.w(TAG, "No se pudo sincronizar el perfil", e);
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof IllegalStateException) return Result.failure();
                if (cause instanceof FirebaseFirestoreException) {
                    FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) cause).getCode();
                    if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                            code == FirebaseFirestoreException.Code.UNAUTHENTICATED ||
                            code == FirebaseFirestoreException.Code.NOT_FOUND ||
                            code == FirebaseFirestoreException.Code.INVALID_ARGUMENT) {
                        return Result.failure();
                    }
                }
            }
            return Result.retry();
        }
    }
}
