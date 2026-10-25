package com.example.gym360.data.local.dao;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.example.gym360.data.local.entity.UserEntity;

@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(@NonNull UserEntity user);

    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    UserEntity findByUid(@NonNull String uid);

    @Query("UPDATE users SET fullName = :fullName, syncStatus = 'PENDING' WHERE uid = :uid")
    int updateNamePending(@NonNull String uid, @NonNull String fullName);

    // Evita confirmar como sincronizado un nombre que se modificó de nuevo.
    @Query("UPDATE users SET syncStatus = 'SYNCED' " +
            "WHERE uid = :uid AND fullName = :expectedName AND syncStatus = 'PENDING'")
    int markNameSynced(@NonNull String uid, @NonNull String expectedName);

}

