package com.example.gym360.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.example.gym360.data.account.AccountApiClient;
import com.example.gym360.data.local.AppDatabase;
import com.example.gym360.data.local.entity.StoreCartItemEntity;
import com.example.gym360.data.local.entity.StoreProductEntity;
import com.example.gym360.domain.model.StoreOrder;
import com.example.gym360.domain.model.StoreProduct;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Catalogo en tiempo real + copia Room, carrito local y operaciones protegidas
 * en Render.
 */
public final class StoreRepository {
    public static final String[] CATEGORIES = { "Todas", "Suplementos", "Bebidas", "Ropa", "Accesorios", "Equipamiento",
            "Snacks", "Otros" };
    private final AppDatabase local;
    private final FirebaseFirestore remote = FirebaseFirestore.getInstance();
    private final AccountApiClient api = new AccountApiClient();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess();

        void onError(String error);
    }

    public interface CatalogCallback {
        void onState(boolean showingCache, int pendingDocuments);

        void onError(String error);
    }

    public interface ReceiptCallback {
        void onSuccess(Map<String, Object> receipt);

        void onError(String error);
    }

    public interface OrdersCallback {
        void onOrders(List<StoreOrder> orders, boolean fromCache);

        void onError(String error);
    }

    public interface CartStatus {
        void onState(boolean showingCache, int pendingDocuments);
    }

    public StoreRepository(Context context) {
        local = AppDatabase.getInstance(context.getApplicationContext());
    }

    public LiveData<List<StoreProductEntity>> observeProducts() {
        return local.storeDao().observeProducts();
    }

    public LiveData<List<StoreCartItemEntity>> observeCart(String uid) {
        return local.storeDao().observeCart(uid);
    }

    public static String currentUid() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        return u == null ? "" : u.getUid();
    }

    public ListenerRegistration listenProducts(boolean admin, CatalogCallback callback) {
        Query query = remote.collection("products");
        if (!admin)
            query = query.whereEqualTo("isActive", true);
        return query.addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
            if (error != null) {
                callback.onError(error.getMessage());
                return;
            }
            if (snapshot == null)
                return;
            List<StoreProductEntity> entities = new ArrayList<>();
            int pending = 0;
            for (com.google.firebase.firestore.DocumentSnapshot d : snapshot.getDocuments()) {
                entities.add(StoreProduct.fromDoc(d).toLocal());
                if (d.getMetadata().hasPendingWrites())
                    pending++;
            }
            boolean cache = snapshot.getMetadata().isFromCache();
            // Evitar reemplazar el cache Room por una consulta de cache Firebase aún vacía.
            if (!cache || !entities.isEmpty())
                IO.execute(() -> local.runInTransaction(() -> {
                    local.storeDao().clearProducts();
                    local.storeDao().upsertProducts(entities);
                }));
            callback.onState(cache, pending);
        });
    }

    public ListenerRegistration listenCart(String uid) {
        return listenCart(uid, null);
    }

    public ListenerRegistration listenCart(String uid, CartStatus callback) {
        return remote.collection("users").document(uid).collection("cart")
                .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
                    if (error != null || snapshot == null || !uid.equals(currentUid()))
                        return;
                    int pending = 0;
                    for (com.google.firebase.firestore.DocumentSnapshot d : snapshot.getDocuments()) {
                        if (d.getMetadata().hasPendingWrites())
                            pending++;
                    }
                    if (callback != null)
                        callback.onState(snapshot.getMetadata().isFromCache(), pending);
                    List<StoreCartItemEntity> values = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                        Long qty = doc.getLong("quantity");
                        if (qty != null && qty > 0 && qty <= 99)
                            values.add(new StoreCartItemEntity(
                                    uid + "|" + doc.getId(), uid, doc.getId(), qty.intValue()));
                    }
                    if (snapshot.getMetadata().isFromCache() && values.isEmpty())
                        return;
                    IO.execute(() -> local.runInTransaction(() -> {
                        if (!uid.equals(currentUid()))
                            return;
                        local.storeDao().clearCart(uid);
                        for (StoreCartItemEntity value : values)
                            local.storeDao().upsertCart(value);
                    }));
                });
    }

    public void addToCart(String uid, String productId, int delta) {
        if (uid.isEmpty() || delta == 0)
            return;
        IO.execute(() -> {
            String key = uid + "|" + productId;
            StoreCartItemEntity old = local.storeDao().getCartItem(key);
            int amount = (old == null ? 0 : old.quantity) + delta;
            if (!uid.equals(currentUid()))
                return;
            if (amount <= 0)
                local.storeDao().deleteCartItem(key);
            else
                local.storeDao().upsertCart(new StoreCartItemEntity(key, uid, productId, Math.min(99, amount)));
            DocumentReference ref = remote.collection("users").document(uid).collection("cart").document(productId);
            if (amount <= 0)
                ref.delete();
            else {
                Map<String, Object> map = new HashMap<>();
                map.put("productId", productId);
                map.put("quantity", Math.min(99, amount));
                map.put("updatedAt", FieldValue.serverTimestamp());
                ref.set(map);
            }
        });
    }

    public void removeFromCart(String uid, String productId) {
        IO.execute(() -> {
            if (!uid.equals(currentUid()))
                return;
            local.storeDao().deleteCartItem(uid + "|" + productId);
            remote.collection("users").document(uid).collection("cart").document(productId).delete();
        });
    }

    public void clearCart(String uid) {
        IO.execute(() -> {
            if (!uid.equals(currentUid()))
                return;
            List<StoreCartItemEntity> cart = local.storeDao().getCart(uid);
            local.storeDao().clearCart(uid);
            com.google.firebase.firestore.WriteBatch batch = remote.batch();
            for (StoreCartItemEntity item : cart)
                batch.delete(remote.collection("users").document(uid)
                        .collection("cart").document(item.productId));
            batch.commit();
        });
    }

    /**
     * Create/Update de metadatos: Firestore cola cambios offline. Stock nunca se
     * escribe aquí.
     */
    public void saveProduct(StoreProduct p, boolean create, Callback cb) {
        DocumentReference ref = remote.collection("products").document(p.id);
        Map<String, Object> map = new HashMap<>();
        map.put("name", p.name.trim());
        map.put("category", p.category);
        map.put("description", p.description);
        map.put("priceCents", p.priceCents);
        map.put("imageUrls", p.imageUrls);
        map.put("isActive", p.active);
        map.put("updatedAt", FieldValue.serverTimestamp());
        if (create) {
            map.put("id", p.id);
            map.put("stock", 0);
            map.put("createdAt", FieldValue.serverTimestamp());
        }
        com.google.android.gms.tasks.Task<Void> task = create ? ref.set(map) : ref.update(map);
        task.addOnSuccessListener(x -> cb.onSuccess()).addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public void softDelete(StoreProduct p, Callback cb) {
        remote.collection("products").document(p.id).update("isActive", false,
                        "updatedAt", FieldValue.serverTimestamp())
                .addOnSuccessListener(x -> cb.onSuccess()).addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public void stockAdjust(String productId, int delta, String reason, String operationId, ReceiptCallback cb) {
        Map<String, Object> json = new HashMap<>();
        json.put("productId", productId);
        json.put("delta", delta);
        json.put("reason", reason);
        json.put("operationId", operationId);
        api.post("/api/inventory/adjust", json, wrap(cb));
    }

    public void checkout(List<Map<String, Object>> items, String operationId, ReceiptCallback cb) {
        Map<String, Object> json = new HashMap<>();
        json.put("items", items);
        json.put("operationId", operationId);
        api.post("/api/orders/checkout", json, wrap(cb));
    }

    public void counterSale(String targetUid, String paymentMethod, List<Map<String, Object>> items,
                            String operationId, ReceiptCallback cb) {
        Map<String, Object> json = new HashMap<>();
        json.put("targetUid", targetUid);
        json.put("paymentMethod", paymentMethod);
        json.put("items", items);
        json.put("operationId", operationId);
        api.post("/api/orders/counter-sale", json, wrap(cb));
    }

    public void transition(String orderId, String action, String note, String operationId, ReceiptCallback cb) {
        Map<String, Object> json = new HashMap<>();
        json.put("action", action);
        json.put("note", note);
        json.put("operationId", operationId);
        api.post("/api/orders/" + orderId + "/transition", json, wrap(cb));
    }

    public void resolveCustomer(String emailOrUid, ReceiptCallback cb) {
        Map<String, Object> json = new HashMap<>();
        json.put("q", emailOrUid);
        api.post("/api/customers/resolve", json, wrap(cb));
    }

    public ListenerRegistration listenOrders(boolean admin, OrdersCallback cb) {
        String uid = currentUid();
        Query q = remote.collection("orders");
        if (!admin)
            q = q.whereEqualTo("userUid", uid);
        return q.addSnapshotListener(MetadataChanges.INCLUDE, (docs, error) -> {
            if (error != null) {
                cb.onError(error.getMessage());
                return;
            }
            if (docs == null)
                return;
            List<StoreOrder> orders = new ArrayList<>();
            for (com.google.firebase.firestore.DocumentSnapshot d : docs.getDocuments())
                orders.add(new StoreOrder(d));
            orders.sort((a, b) -> Long.compare(b.createdAtMillis, a.createdAtMillis));
            cb.onOrders(orders, docs.getMetadata().isFromCache());
        });
    }

    public static String operationId() {
        return UUID.randomUUID().toString();
    }

    public static String money(long cents) {
        return String.format(java.util.Locale.US, "$%.2f", cents / 100.0);
    }

    public static List<Map<String, Object>> toServerItems(List<StoreCartItemEntity> cart) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (StoreCartItemEntity item : cart) {
            Map<String, Object> m = new HashMap<>();
            m.put("productId", item.productId);
            m.put("quantity", item.quantity);
            items.add(m);
        }
        return items;
    }

    private AccountApiClient.Callback wrap(ReceiptCallback cb) {
        return new AccountApiClient.Callback() {
            @Override
            public void onSuccess(@NonNull Map<String, Object> receipt) {
                cb.onSuccess(receipt);
            }

            @Override
            public void onError(@NonNull String error) {
                cb.onError(error);
            }
        };
    }
}

