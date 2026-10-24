package com.example.gym360.data.sync;

import android.content.Context;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

public final class MembershipSyncScheduler {
    private static final String UNIQUE_WORK_NAME = "gym360_membership_sync";
    private MembershipSyncScheduler() { }

    public static void enqueue(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(MembershipSyncWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        // Si un Worker ya está ejecutándose, el nuevo se encola detrás;
        // NO cancela escrituras que podrían estar a mitad de camino.
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(UNIQUE_WORK_NAME,
                        ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }
}
