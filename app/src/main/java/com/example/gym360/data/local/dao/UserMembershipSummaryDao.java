package com.example.gym360.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.example.gym360.data.local.entity.UserMembershipSummaryEntity;

@Dao
public interface UserMembershipSummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(UserMembershipSummaryEntity entity);
    @Query("SELECT * FROM user_membership_summaries WHERE uid = :uid LIMIT 1")
    LiveData<UserMembershipSummaryEntity> observe(String uid);
    @Query("DELETE FROM user_membership_summaries WHERE uid = :uid")
    void delete(String uid);
}
