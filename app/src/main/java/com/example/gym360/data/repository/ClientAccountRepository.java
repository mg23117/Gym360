package com.example.gym360.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.example.gym360.data.account.AccountApiClient;
import com.example.gym360.data.local.AppDatabase;
import com.example.gym360.data.local.entity.MembershipPurchaseEntity;
import com.example.gym360.data.local.entity.UserMembershipSummaryEntity;
import com.example.gym360.data.local.entity.WalletSnapshotEntity;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Para el saldo y las compras ya confirmadas: siempre vamos a usar firestore como la fuente de la verdad
 * y el room para guardar una copia por UID, pero jamás un saldo editable o una compra editable. */
public class ClientAccountRepository {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private final AppDatabase local;
    private final FirebaseFirestore remote = FirebaseFirestore.getInstance();
    private final AccountApiClient api = new AccountApiClient();

    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    public interface PurchaseCallback {
        void onSuccess(@NonNull Map<String, Object> receipt);
        void onError(String message);
    }

    public ClientAccountRepository(@NonNull Context context) {
        local = AppDatabase.getInstance(context.getApplicationContext());
    }

    public LiveData<WalletSnapshotEntity> observeWallet(@NonNull String uid) {
        return local.walletSnapshotDao().observe(uid);
    }

    public LiveData<UserMembershipSummaryEntity> observeMembership(@NonNull String uid) {
        return local.userMembershipSummaryDao().observe(uid);
    }

    public LiveData<List<MembershipPurchaseEntity>> observePurchases(@NonNull String uid) {
        return local.membershipPurchaseDao().observe(uid);
    }

    /** Para poder decargar solo los datos del usuario autenticado desde el servidor. */
    public void refreshFromServer(Callback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            fail(callback, "Debes iniciar sesión.");
            return;
        }
        String uid = user.getUid();
        Task<DocumentSnapshot> walletTask = remote.collection("wallets")
                .document(uid).get(Source.SERVER);
        Task<DocumentSnapshot> summaryTask = remote.collection("user_memberships")
                .document(uid).get(Source.SERVER);
        Task<QuerySnapshot> purchaseTask = remote.collection("users").document(uid)
                .collection("membership_purchases").get(Source.SERVER);

        Tasks.whenAllSuccess(walletTask, summaryTask, purchaseTask)
                .addOnSuccessListener(items -> IO.execute(() -> {
                    try {
                        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                        if (current == null || !uid.equals(current.getUid())) {
                            throw new IllegalStateException("La sesión cambió durante la sincronización.");
                        }
                        DocumentSnapshot walletDoc = (DocumentSnapshot) items.get(0);
                        DocumentSnapshot summaryDoc = (DocumentSnapshot) items.get(1);
                        QuerySnapshot purchases = (QuerySnapshot) items.get(2);
                        Long cents = walletDoc.exists() ? walletDoc.getLong("balanceCents") : 0L;
                        if (cents == null || cents < 0) {
                            throw new IllegalStateException("Saldo remoto inválido.");
                        }
                        long now = System.currentTimeMillis();
                        List<MembershipPurchaseEntity> history = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : purchases) {
                            Timestamp timestamp = doc.getTimestamp("purchasedAt");
                            Long days = doc.getLong("daysGranted");
                            Long priceCents = doc.getLong("priceCents");
                            String planId = doc.getString("membershipId");
                            String planName = doc.getString("membershipName");
                            String expiry = doc.getString("expiresOnExclusiveAfter");
                            if (days == null || priceCents == null || planId == null ||
                                    planName == null || expiry == null) {
                                throw new IllegalStateException("Historial remoto incompleto.");
                            }
                            history.add(new MembershipPurchaseEntity(doc.getId(), uid,
                                    planId, planName, days.intValue(), priceCents,
                                    timestamp == null ? 0L : timestamp.toDate().getTime(), expiry));
                        }
                        UserMembershipSummaryEntity summary = null;
                        if (summaryDoc.exists()) {
                            String planId = summaryDoc.getString("activePlanId");
                            String name = summaryDoc.getString("activePlanName");
                            String expiry = summaryDoc.getString("expiresOnExclusive");
                            Long weight = summaryDoc.getLong("activePlanWeightDays");
                            if (planId == null || name == null || expiry == null || weight == null) {
                                throw new IllegalStateException("Resumen remoto incompleto.");
                            }
                            summary = new UserMembershipSummaryEntity(uid, planId, name,
                                    weight.intValue(), expiry, now);
                        }
                        final UserMembershipSummaryEntity toSave = summary;
                        final long balance = cents;
                        local.runInTransaction(() -> {
                            local.walletSnapshotDao().upsert(
                                    new WalletSnapshotEntity(uid, balance, now));
                            if (toSave == null) local.userMembershipSummaryDao().delete(uid);
                            else local.userMembershipSummaryDao().upsert(toSave);
                            local.membershipPurchaseDao().deleteForUser(uid);
                            local.membershipPurchaseDao().insertAll(history);
                        });
                        if (callback != null) MAIN.post(callback::onSuccess);
                    } catch (Exception e) {
                        fail(callback, e.getMessage());
                    }
                }))
                .addOnFailureListener(e -> fail(callback, e.getMessage()));
    }

    /**
     * Generamos un operationId UNA VEZ por cada intención de compra. Y si la app reintenta
     * después de un timeout raro, debe usarse de nuevo exactamente ese mismo ID.
     */
    public void purchaseMembership(@NonNull String planId, @NonNull String operationId, @NonNull PurchaseCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("membershipId", planId);
        data.put("operationId", operationId);
        api.post("/api/memberships/purchase", data, new AccountApiClient.Callback() {
            @Override public void onSuccess(@NonNull Map<String, Object> receipt) {
                // Como el cobro YA fue confirmado en el servidor. Un error de Room no lo va a rollbackear.
                refreshFromServer(null);
                callback.onSuccess(receipt);
            }
            @Override public void onError(@NonNull String message) {
                callback.onError("No se pudo confirmar la compra: " + message);
            }
        });
    }

    /** Cuando el administrador registra una recarga física o ajuste de saldo. */
    public void adjustWallet(@NonNull String targetUid, long deltaCents, @NonNull String reason, @NonNull String operationId, @NonNull PurchaseCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("targetUid", targetUid);
        data.put("deltaCents", deltaCents);
        data.put("reason", reason);
        data.put("operationId", operationId);
        api.post("/api/wallet/adjust", data, new AccountApiClient.Callback() {
            @Override public void onSuccess(@NonNull Map<String, Object> receipt) {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user != null && targetUid.equals(user.getUid())) refreshFromServer(null);
                callback.onSuccess(receipt);
            }
            @Override public void onError(@NonNull String message) {
                callback.onError("No se pudo ajustar el saldo: " + message);
            }
        });
    }

    public static String newOperationId() {
        return UUID.randomUUID().toString();
    }

    private static void fail(Callback callback, String message) {
        if (callback != null) MAIN.post(() -> callback.onError(
                message == null ? "Error de sincronización" : message));
    }
}
