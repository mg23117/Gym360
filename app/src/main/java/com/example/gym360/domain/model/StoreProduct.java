package com.example.gym360.domain.model;

import androidx.annotation.NonNull;
import com.example.gym360.data.local.entity.StoreProductEntity;
import com.google.firebase.firestore.DocumentSnapshot;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

public final class StoreProduct {
    public final String id, name, category, description;
    public final long priceCents, stock;
    public final boolean active;
    public final List<String> imageUrls;

    public StoreProduct(String id, String name, String category, String description, long priceCents, long stock,
                        boolean active, List<String> imageUrls) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.priceCents = priceCents;
        this.stock = stock;
        this.active = active;
        this.imageUrls = imageUrls == null ? new ArrayList<>() : new ArrayList<>(imageUrls);
    }

    public String mainImage() {
        return imageUrls.isEmpty() ? "" : imageUrls.get(0);
    }

    public static StoreProduct fromDoc(@NonNull DocumentSnapshot d) {
        List<String> images = new ArrayList<>();
        Object raw = d.get("imageUrls");
        if (raw instanceof List)
            for (Object x : (List<?>) raw)
                if (x instanceof String)
                    images.add((String) x);
        return new StoreProduct(d.getId(), str(d.getString("name")), str(d.getString("category")),
                str(d.getString("description")), num(d.getLong("priceCents")), num(d.getLong("stock")),
                Boolean.TRUE.equals(d.getBoolean("isActive")), images);
    }

    public static StoreProduct fromLocal(StoreProductEntity e) {
        List<String> images = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(e.imagesJson);
            for (int i = 0; i < arr.length(); i++)
                images.add(arr.getString(i));
        } catch (Exception ignored) {
        }
        return new StoreProduct(e.id, e.name, e.category, e.description, e.priceCents, e.stock, e.isActive, images);
    }

    public StoreProductEntity toLocal() {
        return new StoreProductEntity(id, name, category, description, priceCents, stock,
                new JSONArray(imageUrls).toString(), active, System.currentTimeMillis());
    }

    private static String str(String s) {
        return s == null ? "" : s;
    }

    private static long num(Long n) {
        return n == null ? 0 : n;
    }
}