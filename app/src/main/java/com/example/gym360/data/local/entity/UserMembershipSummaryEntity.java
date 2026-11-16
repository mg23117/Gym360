package com.example.gym360.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** expiresOnExclusive: fecha local en formato yyyy-MM-dd, no inclusiva*/
@Entity(tableName = "user_membership_summaries")
public class UserMembershipSummaryEntity {
    @PrimaryKey
    @NonNull
    public String uid;
    @NonNull
    public String activePlanId;
    @NonNull
    public String activePlanName;
    public int activePlanWeightDays;
    @NonNull
    public String expiresOnExclusive;
    public long cachedAtMillis;

    public UserMembershipSummaryEntity(@NonNull String uid, @NonNull String activePlanId, @NonNull String activePlanName,
                                       int activePlanWeightDays, @NonNull String expiresOnExclusive, long cachedAtMillis) {
        this.uid = uid;
        this.activePlanId = activePlanId;
        this.activePlanName = activePlanName;
        this.activePlanWeightDays = activePlanWeightDays;
        this.expiresOnExclusive = expiresOnExclusive;
        this.cachedAtMillis = cachedAtMillis;
    }
}
