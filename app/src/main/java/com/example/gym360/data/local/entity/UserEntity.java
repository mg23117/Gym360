package com.example.gym360.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class UserEntity {
    @PrimaryKey @NonNull private String uid;
    @NonNull private String fullName;
    @NonNull private String email;

    public UserEntity(@NonNull String uid, @NonNull String fullName, @NonNull String email) {
        this.uid = uid;
        this.fullName = fullName;
        this.email = email;
    }

    @NonNull public String getUid() { return uid; }
    public void setUid(@NonNull String uid) { this.uid = uid; }
    @NonNull public String getFullName() { return fullName; }
    public void setFullName(@NonNull String fullName) { this.fullName = fullName; }
    @NonNull public String getEmail() { return email; }
    public void setEmail(@NonNull String email) { this.email = email; }
}