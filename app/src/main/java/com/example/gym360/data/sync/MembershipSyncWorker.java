package com.example.gym360.data.sync;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.example.gym360.data.local.AppDatabase;
import com.example.gym360.data.local.dao.MembershipDao;
import com.google.firebase.firestore.FirebaseFirestoreException;

public final class MembershipSyncWorker extends Worker {
    private static final String TAG = "Gym360MembershipSync";

    public MembershipSyncWorker(@NonNull Context appContext,
                                @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull @Override
    public Result doWork() {
        MembershipDao dao = AppDatabase.getInstance(getApplicationContext()).membershipDao();
        try {
            new MembershipSyncEngine(dao).syncBlocking();
            return dao.getPendingCount() == 0 ? Result.success() : Result.retry();
        } catch (Exception e) {
            Log.w(TAG, "No se pudieron sincronizar las membresías", e);
            if (isPermanentError(e)) return Result.failure();
            return Result.retry();
        }
    }

    private boolean isPermanentError(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof IllegalStateException) return true; // Sesión ausente o cambiada.
            if (t instanceof FirebaseFirestoreException) {
                FirebaseFirestoreException.Code code =
                        ((FirebaseFirestoreException) t).getCode();
                return code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                        || code == FirebaseFirestoreException.Code.UNAUTHENTICATED
                        || code == FirebaseFirestoreException.Code.INVALID_ARGUMENT
                        || code == FirebaseFirestoreException.Code.FAILED_PRECONDITION;
            }
        }
        return false;
    }
}