package com.example.gym360.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Historial de compras confirmadas por el servidor, por UID. */
@Entity(tableName = "membership_purchases", indices = {@Index(value = "uid")})
public class MembershipPurchaseEntity {
    @PrimaryKey
    @NonNull
    public String id;
    @NonNull
    public String uid;
    @NonNull
    public String membershipId;
    @NonNull
    public String membershipName;
    public int daysGranted;
    public long priceCents;
    public long purchasedAtMillis;
    @NonNull
    public String expiresOnExclusiveAfter;

    public MembershipPurchaseEntity(@NonNull String id, @NonNull String uid, @NonNull String membershipId, @NonNull String membershipName,
                                    int daysGranted, long priceCents, long purchasedAtMillis, @NonNull String expiresOnExclusiveAfter) {
        this.id = id;
        this.uid = uid;
        this.membershipId = membershipId;
        this.membershipName = membershipName;
        this.daysGranted = daysGranted;
        this.priceCents = priceCents;
        this.purchasedAtMillis = purchasedAtMillis;
        this.expiresOnExclusiveAfter = expiresOnExclusiveAfter;
    }
}
