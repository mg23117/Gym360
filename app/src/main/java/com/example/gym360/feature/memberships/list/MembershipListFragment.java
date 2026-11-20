package com.example.gym360.feature.memberships.list;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.gym360.R;
import com.example.gym360.domain.model.Membership;
import com.example.gym360.domain.repository.MembershipRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MembershipListFragment extends Fragment {
    private MembershipListViewModel viewModel;
    private MembershipAdapter adapter;
    private TextView total, empty;
    private EditText search;
    private List<Membership> allMemberships = new ArrayList<>();
    private Integer lastPendingCount;

    public MembershipListFragment() { super(R.layout.fragment_membership_list); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this,
                new MembershipListViewModel.Factory(requireContext()))
                .get(MembershipListViewModel.class);

        RecyclerView recycler = view.findViewById(R.id.recyclerMemberships);
        empty = view.findViewById(R.id.tvMembershipEmpty);
        total = view.findViewById(R.id.tvMembershipTotal);
        search = view.findViewById(R.id.etSearchMembership);
        TextView pending = view.findViewById(R.id.tvPendingSync);
        FloatingActionButton add = view.findViewById(R.id.fabAddMembership);

        adapter = new MembershipAdapter(new MembershipAdapter.Listener() {
            @Override public void onEdit(Membership membership) { showEditor(membership); }
            @Override public void onDelete(Membership membership) { confirmDelete(membership); }
        });
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        viewModel.getMemberships().observe(getViewLifecycleOwner(), memberships -> {
            allMemberships = memberships == null ? new ArrayList<>() : new ArrayList<>(memberships);
            applyFilter();
        });
        viewModel.getPendingCount().observe(getViewLifecycleOwner(), count -> {
            int pendingCount = count == null ? 0 : count;
            pending.setVisibility(pendingCount > 0 ? View.VISIBLE : View.GONE);
            pending.setText(pendingCount == 1
                    ? "1 cambio pendiente. Se sincronizará automáticamente al recuperar Internet."
                    : pendingCount + " cambios pendientes. Se sincronizarán automáticamente al recuperar Internet.");
            if (lastPendingCount != null && lastPendingCount > 0 && pendingCount == 0 && isAdded()) {
                Snackbar.make(view, "Planes sincronizados correctamente", Snackbar.LENGTH_SHORT).show();
            }
            lastPendingCount = pendingCount;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        add.setOnClickListener(v -> showEditor(null));
    }

    private void applyFilter() {
        if (adapter == null || search == null) return;
        String query = search.getText() == null ? "" : search.getText().toString()
                .trim().toLowerCase(Locale.ROOT);
        List<Membership> filtered = new ArrayList<>();
        for (Membership membership : allMemberships) {
            String name = membership.getName() == null ? "" : membership.getName().toLowerCase(Locale.ROOT);
            String id = membership.getId() == null ? "" : membership.getId().toLowerCase(Locale.ROOT);
            if (query.isEmpty() || name.contains(query) || id.contains(query)) filtered.add(membership);
        }
        adapter.submitList(filtered);
        total.setText(query.isEmpty()
                ? allMemberships.size() + (allMemberships.size() == 1 ? " plan" : " planes")
                : filtered.size() + " de " + allMemberships.size() + " planes");
        empty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(allMemberships.isEmpty()
                ? "No hay membresías. Presiona + para crear la primera."
                : "No hay membresías que coincidan con la búsqueda.");
    }

    private void showEditor(@Nullable Membership existing) {
        boolean editing = existing != null;
        View form = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_membership, null);
        ((TextView) form.findViewById(R.id.tvMembershipDialogTitle)).setText(
                editing ? "Editar membresía" : "Nueva membresía");
        EditText name = form.findViewById(R.id.etMembershipName);
        EditText duration = form.findViewById(R.id.etMembershipDuration);
        RadioGroup durationUnit = form.findViewById(R.id.rgDurationUnit);
        TextInputLayout durationLayout = form.findViewById(R.id.tilMembershipDuration);
        durationUnit.setOnCheckedChangeListener((group, checkedId) ->
                durationLayout.setHint(checkedId == R.id.rbDurationDays
                        ? "Duración en días (1 o 15)" : "Duración en meses"));
        EditText price = form.findViewById(R.id.etMembershipPrice);
        EditText benefits = form.findViewById(R.id.etMembershipBenefits);

        if (editing) {
            name.setText(existing.getName());
            if (existing.getDurationDays() > 0) {
                durationUnit.check(R.id.rbDurationDays);
                duration.setText(String.valueOf(existing.getDurationDays()));
            } else {
                durationUnit.check(R.id.rbDurationMonths);
                duration.setText(String.valueOf(existing.getDurationMonths()));
            }
            price.setText(String.valueOf(existing.getPrice()));
            benefits.setText(existing.getBenefits() == null ? "" :
                    TextUtils.join(", ", existing.getBenefits()));
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(form)
                .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                .setPositiveButton(editing ? "Editar" : "Crear", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            int pink = ContextCompat.getColor(requireContext(), R.color.gym_pink);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setBackgroundTintList(ColorStateList.valueOf(pink));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.WHITE);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(pink);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                String title = name.getText().toString().trim();
                if (title.isEmpty() || title.length() > 100) {
                    name.setError("Nombre entre 1 y 100 caracteres"); return;
                }
                int months = 0;
                int days = 0;
                try {
                    int value = Integer.parseInt(duration.getText().toString().trim());
                    if (durationUnit.getCheckedRadioButtonId() == R.id.rbDurationDays) {
                        if (value != 1 && value != 15) throw new NumberFormatException();
                        days = value;
                    } else {
                        if (value <= 0) throw new NumberFormatException();
                        months = value;
                    }
                } catch (NumberFormatException e) {
                    duration.setError("En días: 1 o 15. En meses: entero mayor a 0"); return;
                }
                double amount;
                try {
                    amount = Double.parseDouble(price.getText().toString().trim().replace(',', '.'));
                    if (!Double.isFinite(amount) || amount < 0 || amount > 500000 ||
                            Math.abs(amount * 100.0 - Math.rint(amount * 100.0)) > 0.000001) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException e) {
                    price.setError("Precio USD válido, máximo 2 decimales"); return;
                }
                List<String> benefitList = new ArrayList<>();
                for (String part : benefits.getText().toString().split(",")) {
                    String cleaned = part.trim();
                    if (!cleaned.isEmpty()) benefitList.add(cleaned);
                }
                Membership candidate = new Membership(editing ? existing.getId() : "",
                        title, months, days, amount, benefitList);
                MembershipRepository.Callback callback = new MembershipRepository.Callback() {
                    @Override public void onSuccess() {
                        if (!isAdded()) return;
                        dialog.dismiss();
                        Toast.makeText(requireContext(),
                                "Guardado localmente; se sincronizará automáticamente",
                                Toast.LENGTH_LONG).show();
                    }
                    @Override public void onError(String error) {
                        if (dialog.isShowing())
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        showError(error);
                    }
                };
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                if (editing) viewModel.update(candidate, callback);
                else viewModel.create(candidate, callback);
            });
        });
        dialog.show();
    }

    private void confirmDelete(Membership membership) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Eliminar membresía")
                .setMessage("Se ocultará el plan y se eliminará remotamente cuando vuelva la conexión. Las compras anteriores permanecen registradas.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (dialog, which) -> viewModel.delete(membership,
                        new MembershipRepository.Callback() {
                            @Override
                            public void onSuccess() {
                                if (isAdded()) Toast.makeText(requireContext(),
                                        "Eliminación guardada; sincronización automática", Toast.LENGTH_SHORT).show();
                            }
                            @Override
                            public void onError(String message) {
                                showError(message);
                            }
                        }))
                .show();
    }

    private void showError(String message) {
        if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }
}
