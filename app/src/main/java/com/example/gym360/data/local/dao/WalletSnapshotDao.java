package com.example.gym360.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.example.gym360.data.local.entity.WalletSnapshotEntity;

@Dao
public interface WalletSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(WalletSnapshotEntity entity);
    @Query("SELECT * FROM wallet_snapshots WHERE uid = :uid LIMIT 1")
    LiveData<WalletSnapshotEntity> observe(String uid);
}
