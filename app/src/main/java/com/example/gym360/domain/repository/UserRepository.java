package com.example.gym360.domain.repository;

import androidx.annotation.NonNull;

public interface UserRepository {
    interface RegisterCallback {
        void onSuccess();
        void onError(@NonNull String message);
    }

    void register(@NonNull String fullName, @NonNull String email,
                  @NonNull String password, @NonNull RegisterCallback callback);
}
