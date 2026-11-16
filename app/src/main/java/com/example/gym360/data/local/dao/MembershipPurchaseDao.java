package com.example.gym360.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.example.gym360.data.local.entity.MembershipPurchaseEntity;
import java.util.List;

@Dao
public interface MembershipPurchaseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<MembershipPurchaseEntity> purchases);
    @Query("SELECT * FROM membership_purchases WHERE uid = :uid ORDER BY purchasedAtMillis DESC")
    LiveData<List<MembershipPurchaseEntity>> observe(String uid);
    @Query("DELETE FROM membership_purchases WHERE uid = :uid")
    void deleteForUser(String uid);
}
