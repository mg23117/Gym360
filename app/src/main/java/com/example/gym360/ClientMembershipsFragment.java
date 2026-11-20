package com.example.gym360;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
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
import com.example.gym360.data.sync.ClientAccountAutoSync;
import com.example.gym360.domain.model.Membership;
import com.example.gym360.domain.model.MembershipDateLabels;
import com.example.gym360.domain.model.MembershipTime;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ClientMembershipsFragment extends Fragment {
    private ClientAccountRepository repository;
    private ClientAccountAutoSync autoSync;
    private ListenerRegistration catalogListener;
    private LinearLayout plansContainer;
    private TextView status, balance, planName, days, expiry, badge;
    private Long cachedBalanceCents;
    private boolean sawOffline;
    private String uid;
    @Nullable
    private AlertDialog currentDialog;

    public ClientMembershipsFragment() { super(R.layout.fragment_client_memberships); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        super.onViewCreated(view, saved);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(), "Debes iniciar sesión", Toast.LENGTH_LONG).show();
            return;
        }
        uid = user.getUid();
        repository = new ClientAccountRepository(requireContext());
        plansContainer = view.findViewById(R.id.llAvailableMemberships);
        status = view.findViewById(R.id.tvClientSyncStatus);
        balance = view.findViewById(R.id.tvClientWalletBalance);
        planName = view.findViewById(R.id.tvCurrentMembership);
        days = view.findViewById(R.id.tvCurrentDays);
        expiry = view.findViewById(R.id.tvCurrentExpiry);
        badge = view.findViewById(R.id.tvCurrentPlanBadge);
        view.findViewById(R.id.btnViewWallet).setOnClickListener(v -> {
            if (getActivity() instanceof HomeActivity) ((HomeActivity) getActivity()).openWallet();
        });
        repository.observeWallet(uid).observe(getViewLifecycleOwner(), wallet -> {
            cachedBalanceCents = wallet == null ? null : wallet.balanceCents;
            balance.setText(wallet == null ? "Sin datos" : money(wallet.balanceCents));
        });
        repository.observeMembership(uid).observe(getViewLifecycleOwner(), summary -> {
            if (summary == null) {
                badge.setText("COMIENZA A ENTRENAR");
                planName.setText("Aún no tienes una membresía");
                days.setText("Elige tu primer plan");
                expiry.setText("Los días se acumularán en próximas compras.");
                return;
            }
            try {
                int remaining = MembershipTime.remainingDays(summary.expiresOnExclusive);
                if (remaining == 0) {
                    badge.setText("MEMBRESÍA VENCIDA");
                    planName.setText(summary.activePlanName);
                    days.setText("0 días restantes");
                    expiry.setText("Adquiere otro plan para reactivar tu membresía.");
                } else {
                    badge.setText("TU PLAN PRINCIPAL");
                    planName.setText(summary.activePlanName);
                    days.setText(remaining + (remaining == 1 ? " día restante" : " días restantes"));
                    expiry.setText("Vence el " + MembershipDateLabels.lastValidDay(summary.expiresOnExclusive));
                }
            } catch (Exception ignored) {
                badge.setText("ESTADO DE MEMBRESÍA");
                planName.setText("Fecha de vigencia no disponible");
                days.setText("");
                expiry.setText("");
            }
        });
        autoSync = new ClientAccountAutoSync(repository, uid, (message, offline) -> {
            if (isAdded() && status != null) {
                status.setText(message);
                if (offline) sawOffline = true;
                else if (sawOffline && !message.startsWith("Actualizando")) {
                    sawOffline = false;
                    com.google.android.material.snackbar.Snackbar.make(
                            requireView(), "Datos actualizados desde el servidor",
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (autoSync != null) autoSync.start();
        if (plansContainer != null) observeCatalog();
    }

    @Override
    public void onStop() {
        if (catalogListener != null) { catalogListener.remove(); catalogListener = null; }
        if (autoSync != null) autoSync.stop();
        if (currentDialog != null) { currentDialog.dismiss(); currentDialog = null; }
        super.onStop();
    }

    private void observeCatalog() {
        catalogListener = FirebaseFirestore.getInstance().collection("memberships")
                .addSnapshotListener(MetadataChanges.INCLUDE, (query, error) -> {
                    if (!isAdded() || plansContainer == null) return;
                    if (error != null || query == null) {
                        if (plansContainer.getChildCount() == 0) addLabel("No hay catálogo disponible. Comprueba tu conexión.");
                        return;
                    }
                    plansContainer.removeAllViews();
                    List<Membership> list = new ArrayList<>();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : query) {
                        try {
                            MembershipDto dto = doc.toObject(MembershipDto.class);
                            if (!MembershipMapper.validDuration(dto.getDurationMonths(), dto.getDurationDays())) continue;
                            list.add(new Membership(doc.getId(), dto.getName(), dto.getDurationMonths(),
                                    dto.getDurationDays(), dto.getPrice(), dto.getBenefits()));
                        } catch (RuntimeException ignored) { /* Un plan inválido no bloquea el catálogo. */ }
                    }
                    list.sort((a, b) -> Integer.compare(a.getWeightDays(), b.getWeightDays()));
                    for (Membership membership : list) addPlan(membership);
                    if (list.isEmpty()) addLabel("No hay planes disponibles.");
                });
    }

    private void addPlan(Membership plan) {
        if (!isAdded()) return;
        View item = LayoutInflater.from(requireContext()).inflate(R.layout.item_client_membership,
                plansContainer, false);
        ((TextView) item.findViewById(R.id.tvClientPlanName)).setText(plan.getName());
        ((TextView) item.findViewById(R.id.tvClientPlanDuration)).setText(plan.getDurationLabel());
        ((TextView) item.findViewById(R.id.tvClientPlanPrice)).setText(price(plan.getPrice()));
        List<String> benefits = plan.getBenefits();
        StringBuilder benefitsText = new StringBuilder();
        if (benefits != null) for (String benefit : benefits) {
            if (benefit == null || benefit.trim().isEmpty()) continue;
            if (benefitsText.length() > 0) benefitsText.append('\n');
            benefitsText.append("• ").append(benefit.trim());
        }
        ((TextView) item.findViewById(R.id.tvClientPlanBenefits)).setText(
                benefitsText.length() == 0 ? "Beneficios a consultar en recepción" : benefitsText.toString());
        MaterialButton buy = item.findViewById(R.id.btnBuyClientPlan);
        buy.setOnClickListener(v -> confirmPurchase(plan, buy));
        plansContainer.addView(item);
    }

    private void addLabel(String text) {
        TextView label = new TextView(requireContext());
        label.setText(text);
        label.setTextSize(14f);
        label.setPadding(12, 16, 12, 16);
        plansContainer.addView(label);
    }

    private void confirmPurchase(Membership plan, MaterialButton buy) {
        if (!isAdded()) return;
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm_purchase, null);
        TextView message = content.findViewById(R.id.tvPurchaseDialogMessage);
        long cents = Math.round(plan.getPrice() * 100);
        String walletText = cachedBalanceCents == null ? "por confirmar" : money(cachedBalanceCents);
        String afterText = cachedBalanceCents == null ? "se calculará en el servidor" :
                cachedBalanceCents < cents ? "saldo insuficiente según la última copia" :
                        money(cachedBalanceCents - cents);
        message.setText("Plan: " + plan.getName() + " (" + plan.getDurationLabel() + ")\n\n" +
                "Importe: " + price(plan.getPrice()) + "\n" +
                "Saldo disponible: " + walletText + "\n" +
                "Saldo estimado después: " + afterText + "\n\n" +
                "Los días se sumarán a tu membresía vigente.");
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(content).create();
        currentDialog = dialog;
        content.findViewById(R.id.btnCancelPlanPurchase).setOnClickListener(v -> dialog.dismiss());
        content.findViewById(R.id.btnConfirmPlanPurchase).setOnClickListener(v -> {
            dialog.dismiss();
            performPurchase(plan, buy);
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
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
                    @Override
                    public void onSuccess(@NonNull Map<String, Object> receipt) {
                        prefs.edit().remove(key).apply();
                        if (!isAdded()) return;
                        button.setEnabled(true);
                        showPurchaseSuccess(plan, receipt);
                    }
                    @Override public void onError(String message) {
                        if (!isAdded()) return;
                        button.setEnabled(true);
                        Toast.makeText(requireContext(), message + " Si reintentas, no se duplicará el cobro.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showPurchaseSuccess(Membership plan, Map<String, Object> receipt) {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_purchase_success, null);
        Object endDate = receipt.get("expiresOnExclusive");
        Object dominantPlan = receipt.get("activePlanName");
        ((TextView) content.findViewById(R.id.tvPurchaseSuccessMessage)).setText(
                "Has adquirido " + plan.getName() + ".\n" +
                        "Plan principal: " + (dominantPlan == null ? "Actualizándose" : dominantPlan) + "\n" +
                        "Vence el: " + (endDate == null ? "Actualizándose" :
                        MembershipDateLabels.lastValidDay(endDate.toString())));
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(content).create();
        currentDialog = dialog;
        content.findViewById(R.id.btnPurchaseSuccessOk).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    private static String price(double amount) {
        return String.format(Locale.US, "$%.2f", amount);
    }
    private static String money(long cents) {
        return String.format(Locale.US, "$%.2f", cents / 100.0);
    }
}
