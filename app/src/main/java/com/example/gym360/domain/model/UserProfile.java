package com.example.gym360.domain.model;

import androidx.annotation.NonNull;

public final class UserProfile {
    @NonNull private final String uid;
    @NonNull private final String fullName;
    @NonNull private final String email;

    public UserProfile(@NonNull String uid, @NonNull String fullName, @NonNull String email) {
        this.uid = uid;
        this.fullName = fullName;
        this.email = email;
    }

    @NonNull public String getUid() { return uid; }
    @NonNull public String getFullName() { return fullName; }
    @NonNull public String getEmail() { return email; }
}
