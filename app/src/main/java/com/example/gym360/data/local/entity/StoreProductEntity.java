package com.example.gym360.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Solo es una entidad local, nunca será usada como fuente de verdad para el precio, saldo o stock. */
@Entity(tableName = "store_products")
public class StoreProductEntity {
    @PrimaryKey
    @NonNull
    public String id;
    @NonNull
    public String name;
    @NonNull
    public String category;
    @NonNull
    public String description;
    public
    long priceCents;
    public
    long stock;
    @NonNull
    public String imagesJson;
    public
    boolean isActive;
    public
    long cachedAtMillis;

    public StoreProductEntity(@NonNull String id, @NonNull String name, @NonNull String category, @NonNull String description, long priceCents, long stock,
                              @NonNull String imagesJson, boolean isActive, long cachedAtMillis) {
        this.id=id; this.name=name; this.category=category; this.description=description;
        this.priceCents=priceCents; this.stock=stock; this.imagesJson=imagesJson;
        this.isActive=isActive; this.cachedAtMillis=cachedAtMillis;
    }
}
