package com.example.gym360.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Es un carrito persistente aislado por UID. No es una venta confirmada. */
@Entity(tableName = "store_cart_items", indices = {@Index("uid")})
public class StoreCartItemEntity {
    @PrimaryKey
    @NonNull
    public String id;
    @NonNull
    public String uid;
    @NonNull
    public String productId;
    public int quantity;
    public StoreCartItemEntity(@NonNull String id, @NonNull String uid, @NonNull String productId, int quantity) {
        this.id=id; this.uid=uid; this.productId=productId; this.quantity=quantity;
    }
}
