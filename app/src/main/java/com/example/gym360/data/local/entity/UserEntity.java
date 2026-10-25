package com.example.gym360.data.local.entity;

import static com.example.gym360.data.local.entity.MembershipEntity.SYNCED;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class UserEntity {
    public static final String SYNCED = "SYNCED";
    public static final String PENDING = "PENDING";

    @PrimaryKey @NonNull private String uid;
    @NonNull private String fullName;
    @NonNull private String email;
    @NonNull
    @ColumnInfo(defaultValue = "'SYNCED'")
    private String syncStatus = SYNCED;

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
    @NonNull public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(@NonNull String syncStatus) { this.syncStatus = syncStatus; }
}