package com.example.gym360.data.sync;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

public final class ProfileSyncScheduler {
    private ProfileSyncScheduler() { }

    public static String uniqueWorkName(@NonNull String uid) {
        return "gym360_profile_sync_" + uid;
    }

    public static void enqueue(@NonNull Context context, @NonNull String uid) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build();
        Data input = new Data.Builder().putString(ProfileSyncWorker.KEY_UID, uid).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ProfileSyncWorker.class)
                .setInputData(input)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                uniqueWorkName(uid), ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }
}
