package com.example.gym360.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.gym360.data.local.entity.StoreProductEntity;
import com.example.gym360.data.local.entity.StoreCartItemEntity;
import java.util.List;

@Dao
public interface StoreDao {
    @Query("SELECT * FROM store_products ORDER BY name ASC")
    LiveData<List<StoreProductEntity>> observeProducts();
    @Query("SELECT * FROM store_cart_items WHERE uid = :uid ORDER BY productId")
    LiveData<List<StoreCartItemEntity>> observeCart(String uid);
    @Query("SELECT * FROM store_cart_items WHERE uid = :uid ORDER BY productId")
    List<StoreCartItemEntity> getCart(String uid);
    @Query("SELECT * FROM store_cart_items WHERE id = :id LIMIT 1")
    StoreCartItemEntity getCartItem(String id);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertCart(StoreCartItemEntity cartItem);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertProducts(List<StoreProductEntity> products);
    @Query("DELETE FROM store_products")
    void clearProducts();
    @Query("DELETE FROM store_cart_items WHERE id = :id")
    void deleteCartItem(String id);
    @Query("DELETE FROM store_cart_items WHERE uid = :uid")
    void clearCart(String uid);
}
