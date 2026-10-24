package com.example.gym360.feature.memberships.list;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.gym360.R;
import com.example.gym360.domain.model.Membership;
import com.example.gym360.domain.repository.MembershipRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.List;

public class MembershipListFragment extends Fragment {
    private MembershipListViewModel viewModel;
    private MembershipAdapter adapter;

    public MembershipListFragment() { super(R.layout.fragment_membership_list); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this,
                new MembershipListViewModel.Factory(requireContext()))
                .get(MembershipListViewModel.class);

        RecyclerView recycler = view.findViewById(R.id.recyclerMemberships);
        TextView empty = view.findViewById(R.id.tvMembershipEmpty);
        TextView total = view.findViewById(R.id.tvMembershipTotal);
        TextView pending = view.findViewById(R.id.tvPendingSync);
        FloatingActionButton add = view.findViewById(R.id.fabAddMembership);
        Button syncButton = view.findViewById(R.id.btnSyncMemberships);

        adapter = new MembershipAdapter(new MembershipAdapter.Listener() {
            @Override public void onEdit(Membership membership) { showEditor(membership); }
            @Override public void onDelete(Membership membership) { confirmDelete(membership); }
        });
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        viewModel.getMemberships().observe(getViewLifecycleOwner(), memberships -> {
            List<Membership> safeList = memberships == null ? new ArrayList<>() : memberships;
            adapter.submitList(safeList);
            empty.setVisibility(safeList.isEmpty() ? View.VISIBLE : View.GONE);
            total.setText(safeList.size() == 1 ? "1 plan" : safeList.size() + " planes");
        });
        viewModel.getPendingCount().observe(getViewLifecycleOwner(), count -> {
            int n = count == null ? 0 : count;
            pending.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
            pending.setText(n == 1 ? "1 cambio pendiente de sincronización"
                    : n + " cambios pendientes de sincronización");
        });
        add.setOnClickListener(v -> showEditor(null));
        syncButton.setOnClickListener(v -> {
            syncButton.setEnabled(false);
            viewModel.sync(new MembershipRepository.SyncCallback() {
                @Override public void onSuccess(int count) {
                    if (!isAdded()) return;
                    syncButton.setEnabled(true);
                    Toast.makeText(requireContext(),
                            "Sincronización completada. Cambios confirmados: " + count,
                            Toast.LENGTH_LONG).show();
                }
                @Override public void onError(String message) {
                    if (!isAdded()) return;
                    syncButton.setEnabled(true);
                    showError("No se pudo sincronizar: " + message);
                }
            });
        });
    }

    private void showEditor(@Nullable Membership existing) {
        boolean editing = existing != null;
        View form = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_membership, null);
        EditText name = form.findViewById(R.id.etMembershipName);
        EditText duration = form.findViewById(R.id.etMembershipDuration);
        EditText price = form.findViewById(R.id.etMembershipPrice);
        EditText benefits = form.findViewById(R.id.etMembershipBenefits);

        if (editing) {
            name.setText(existing.getName());
            duration.setText(String.valueOf(existing.getDurationMonths()));
            price.setText(String.valueOf(existing.getPrice()));
            benefits.setText(existing.getBenefits() == null ? "" :
                    TextUtils.join(", ", existing.getBenefits()));
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(editing ? "Editar membresía" : "Nueva membresía")
                .setView(form)
                .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                .setPositiveButton("Guardar", null)
                .create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String title = name.getText().toString().trim();
                    if (title.isEmpty()) { name.setError("El nombre es obligatorio"); return; }
                    int months;
                    double amount;
                    try {
                        months = Integer.parseInt(duration.getText().toString().trim());
                        if (months <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        duration.setError("Ingresa un número de meses mayor que 0"); return;
                    }
                    try {
                        amount = Double.parseDouble(price.getText().toString().trim().replace(',', '.'));
                        if (!Double.isFinite(amount) || amount < 0) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        price.setError("Ingresa un precio válido"); return;
                    }
                    List<String> benefitList = new ArrayList<>();
                    for (String part : benefits.getText().toString().split(",")) {
                        String cleaned = part.trim();
                        if (!cleaned.isEmpty()) benefitList.add(cleaned);
                    }
                    Membership candidate = new Membership(editing ? existing.getId() : "",
                            title, months, amount, benefitList);
                    MembershipRepository.Callback cb = new MembershipRepository.Callback() {
                        @Override public void onSuccess() {
                            if (!isAdded()) return;
                            dialog.dismiss();
                            Toast.makeText(requireContext(),
                                    "Guardado en el dispositivo; pendiente de sincronizar",
                                    Toast.LENGTH_LONG).show();
                        }
                        @Override public void onError(String message) {
                            if (dialog.isShowing()) {
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            }
                            showError(message);
                        }
                    };
                    if (editing) viewModel.update(candidate, cb);
                    else viewModel.create(candidate, cb);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    // Evita guardar dos veces mientras Room procesa la operación.
                }));
        dialog.show();
    }

    private void confirmDelete(Membership membership) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Eliminar membresía")
                .setMessage("El plan dejará de mostrarse. El borrado remoto quedará pendiente de sincronización.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (dialog, which) -> viewModel.delete(membership,
                        new MembershipRepository.Callback() {
                            @Override public void onSuccess() {
                                if (isAdded()) Toast.makeText(requireContext(),
                                        "Eliminación local registrada", Toast.LENGTH_SHORT).show();
                            }
                            @Override public void onError(String message) { showError(message); }
                        }))
                .show();
    }

    private void showError(String message) {
        if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }
}
