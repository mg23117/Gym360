package com.example.gym360.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/* Será una copia informativa del servidor. No se va a usar para aprobar compras. */
@Entity(tableName = "wallet_snapshots")
public class WalletSnapshotEntity {

    @PrimaryKey
    @NonNull
    public String uid;
    public long balanceCents;
    public long cachedAtMillis;

    public WalletSnapshotEntity(@NonNull String uid, long balanceCents, long cachedAtMillis) {
        this.uid = uid;
        this.balanceCents = balanceCents;
        this.cachedAtMillis = cachedAtMillis;
    }
}
