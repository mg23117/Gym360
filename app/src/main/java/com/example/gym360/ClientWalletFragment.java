package com.example.gym360;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.gym360.data.account.ClientAccountRepository;
import com.example.gym360.data.local.entity.MembershipPurchaseEntity;
import com.example.gym360.data.sync.ClientAccountAutoSync;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.Timestamp;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Lectura de billetera y de movimientos; no contiene ninguna operación de recarga local. */
public class ClientWalletFragment extends Fragment {
    private ClientAccountAutoSync autoSync;
    private ListenerRegistration transactionsListener;
    private String uid;
    private LinearLayout movements, purchases;
    private TextView syncStatus;

    public ClientWalletFragment() { super(R.layout.fragment_client_wallet); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        movements = view.findViewById(R.id.llWalletTransactions);
        purchases = view.findViewById(R.id.llMembershipPurchases);
        syncStatus = view.findViewById(R.id.tvWalletSyncStatus);
        TextView balance = view.findViewById(R.id.tvWalletPageBalance);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            syncStatus.setText("Debes iniciar sesión");
            return;
        }
        uid = user.getUid();
        ClientAccountRepository repo = new ClientAccountRepository(requireContext());
        repo.observeWallet(uid).observe(getViewLifecycleOwner(), wallet ->
                balance.setText(wallet == null ? "Sin datos" : money(wallet.balanceCents)));
        repo.observePurchases(uid).observe(getViewLifecycleOwner(), this::displayPurchases);
        autoSync = new ClientAccountAutoSync(repo, uid, (message, offline) -> {
            if (isAdded() && syncStatus != null) syncStatus.setText(message);
        });
    }

    @Override public void onStart() {
        super.onStart();
        if (autoSync != null) autoSync.start();
        if (uid != null) watchTransactions();
    }

    @Override public void onStop() {
        if (transactionsListener != null) { transactionsListener.remove(); transactionsListener = null; }
        if (autoSync != null) autoSync.stop();
        super.onStop();
    }

    private void watchTransactions() {
        transactionsListener = FirebaseFirestore.getInstance()
                .collection("wallets").document(uid).collection("transactions")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
                    if (!isAdded() || movements == null) return;
                    if (error != null || snapshot == null) {
                        if (movements.getChildCount() == 0) addEmpty(movements,
                                "No se pudieron consultar los movimientos. Mostrando datos disponibles en caché.");
                        return;
                    }
                    movements.removeAllViews();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String type = doc.getString("type");
                        Long delta = doc.getLong("deltaCents");
                        Timestamp time = doc.getTimestamp("createdAt");
                        String title = "Movimiento de saldo";
                        if ("ADMIN_ADJUSTMENT".equals(type)) title = delta != null && delta >= 0 ? "Recarga presencial" : "Ajuste de saldo";
                        else if ("MEMBERSHIP_PURCHASE".equals(type)) title = "Compra de membresía";
                        else if ("PRODUCT_PURCHASE".equals(type)) title = "Compra en tienda";
                        String reason = doc.getString("reason");
                        String detail = time == null ? "Fecha pendiente" :
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, new Locale("es", "SV"))
                                        .format(time.toDate());
                        if (reason != null && !reason.isEmpty()) detail += " · " + reason;
                        addMovement(movements, title, detail, delta == null ? "—" : signedMoney(delta));
                    }
                    if (snapshot.isEmpty()) addEmpty(movements, "Aún no tienes movimientos registrados.");
                });
    }

    private void displayPurchases(List<MembershipPurchaseEntity> history) {
        if (!isAdded() || purchases == null) return;
        purchases.removeAllViews();
        if (history == null || history.isEmpty()) {
            addEmpty(purchases, "Todavía no has comprado membresías.");
            return;
        }
        for (MembershipPurchaseEntity p : history) {
            String detail = (p.purchasedAtMillis <= 0 ? "Fecha no disponible" :
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT,
                            new Locale("es", "SV")).format(new Date(p.purchasedAtMillis)))
                    + " · +" + p.daysGranted + " días";
            addMovement(purchases, p.membershipName, detail, "-" + money(p.priceCents));
        }
    }

    private void addMovement(LinearLayout parent, String title, String detail, String amount) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_wallet_transaction, parent, false);
        ((TextView) row.findViewById(R.id.tvTransactionTitle)).setText(title);
        ((TextView) row.findViewById(R.id.tvTransactionDetail)).setText(detail);
        ((TextView) row.findViewById(R.id.tvTransactionAmount)).setText(amount);
        parent.addView(row);
    }

    private void addEmpty(LinearLayout parent, String text) {
        TextView empty = new TextView(requireContext());
        empty.setText(text);
        empty.setTextColor(android.graphics.Color.DKGRAY);
        empty.setPadding(12, 14, 12, 14);
        parent.addView(empty);
    }

    private static String money(long cents) {
        return String.format(Locale.US, "$%.2f", cents / 100.0);
    }
    private static String signedMoney(long cents) {
        return (cents > 0 ? "+" : cents < 0 ? "-" : "") + money(Math.abs(cents));
    }
}
