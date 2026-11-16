package com.example.gym360;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.example.gym360.data.account.ClientAccountRepository;
import com.example.gym360.data.mapper.MembershipMapper;
import com.example.gym360.data.model.MembershipDto;
import com.example.gym360.domain.model.Membership;
import com.example.gym360.domain.model.MembershipTime;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import java.util.Locale;

/** MVP cliente para probar lectura en Room y compra confirmada en servidor. */
public class ClientMembershipsFragment extends Fragment {
    private ClientAccountRepository repository;
    private LinearLayout plansContainer;
    private MaterialButton refreshButton;
    private String uid;

    public ClientMembershipsFragment() { super(R.layout.fragment_client_memberships); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(), "Debes iniciar sesión", Toast.LENGTH_LONG).show();
            return;
        }
        uid = user.getUid();
        repository = new ClientAccountRepository(requireContext());
        TextView balance = view.findViewById(R.id.tvClientWalletBalance);
        TextView membership = view.findViewById(R.id.tvCurrentMembership);
        TextView purchaseCount = view.findViewById(R.id.tvClientPurchaseCount);
        plansContainer = view.findViewById(R.id.llAvailableMemberships);
        refreshButton = view.findViewById(R.id.btnRefreshClientAccount);

        repository.observeWallet(uid).observe(getViewLifecycleOwner(), snapshot -> {
            if (snapshot == null) balance.setText("Saldo prepago: sin datos locales");
            else balance.setText(String.format(Locale.US,
                    "Saldo prepago: $%.2f (copia local)", snapshot.balanceCents / 100.0));
        });
        repository.observeMembership(uid).observe(getViewLifecycleOwner(), summary -> {
            if (summary == null) {
                membership.setText("Membresía actual: ninguna");
                return;
            }
            try {
                int days = MembershipTime.remainingDays(summary.expiresOnExclusive);
                if (days <= 0) membership.setText("Membresía: vencida");
                else membership.setText(summary.activePlanName + " · " + days +
                        " días restantes · Vigente hasta " + summary.expiresOnExclusive + " (exclusiva)");
            } catch (Exception ignored) {
                membership.setText("No se pudo interpretar la fecha de membresía");
            }
        });
        repository.observePurchases(uid).observe(getViewLifecycleOwner(), history ->
                purchaseCount.setText("Compras confirmadas: " +
                        (history == null ? 0 : history.size())));
        refreshButton.setOnClickListener(v -> refresh());
        refresh();
        loadCatalog();
    }

    private void refresh() {
        refreshButton.setEnabled(false);
        repository.refreshFromServer(new ClientAccountRepository.Callback() {
            @Override public void onSuccess() {
                if (isAdded()) refreshButton.setEnabled(true);
            }
            @Override public void onError(String message) {
                if (!isAdded()) return;
                refreshButton.setEnabled(true);
                Toast.makeText(requireContext(),
                        "Sin conexión: mostrando la última copia local. " + message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadCatalog() {
        FirebaseFirestore.getInstance().collection("memberships")
                .get(Source.SERVER)
                .addOnSuccessListener(query -> {
                    if (!isAdded()) return;
                    plansContainer.removeAllViews();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : query) {
                        MembershipDto dto = doc.toObject(MembershipDto.class);
                        dto.setId(doc.getId());
                        if (!MembershipMapper.validDuration(dto.getDurationMonths(),
                                dto.getDurationDays())) continue;
                        Membership plan = new Membership(dto.getId(), dto.getName(),
                                dto.getDurationMonths(), dto.getDurationDays(),
                                dto.getPrice(), dto.getBenefits());
                        MaterialButton button = new MaterialButton(requireContext());
                        button.setText(String.format(Locale.US,
                                "%s · %s · $%.2f  —  Comprar",
                                plan.getName(), plan.getDurationLabel(), plan.getPrice()));
                        plansContainer.addView(button);
                        button.setOnClickListener(v -> confirmPurchase(plan, button));
                    }
                    if (query.isEmpty()) addLabel("No hay planes disponibles.");
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) addLabel("Catálogo no disponible sin conexión.");
                });
    }

    private void addLabel(String text) {
        TextView label = new TextView(requireContext());
        label.setText(text);
        plansContainer.addView(label);
    }

    private void confirmPurchase(Membership plan, MaterialButton button) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Confirmar membresía")
                .setMessage("Se descontarán " +
                        String.format(Locale.US, "$%.2f", plan.getPrice()) +
                        " del saldo prepago para agregar " + plan.getWeightDays() + " días.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Comprar", (dialog, which) -> performPurchase(plan, button))
                .show();
    }

    private void performPurchase(Membership plan, MaterialButton button) {
        // Guardar UUID por usuario/plan: reintentos tras error conservan la operación.
        SharedPreferences prefs = requireContext().getSharedPreferences(
                "Gym360PurchaseRequests", Context.MODE_PRIVATE);
        String key = "pending_" + uid + "_" + plan.getId();
        String operationId = prefs.getString(key, null);
        if (operationId == null) {
            operationId = ClientAccountRepository.newOperationId();
            if (!prefs.edit().putString(key, operationId).commit()) {
                Toast.makeText(requireContext(), "No se pudo guardar el identificador de compra", Toast.LENGTH_LONG).show();
                return;
            }
        }
        button.setEnabled(false);
        repository.purchaseMembership(plan.getId(), operationId,
                new ClientAccountRepository.PurchaseCallback() {
                    @Override public void onSuccess(@NonNull java.util.Map<String, Object> receipt) {
                        prefs.edit().remove(key).apply();
                        if (!isAdded()) return;
                        button.setEnabled(true);
                        Toast.makeText(requireContext(),
                                "Compra confirmada. Vigencia hasta: " +
                                        receipt.get("expiresOnExclusive"),
                                Toast.LENGTH_LONG).show();
                    }
                    @Override public void onError(String message) {
                        if (!isAdded()) return;
                        button.setEnabled(true);
                        Toast.makeText(requireContext(),
                                message + " Reintentar utiliza el mismo identificador.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
