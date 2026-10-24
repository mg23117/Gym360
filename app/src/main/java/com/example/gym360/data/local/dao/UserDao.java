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
}

