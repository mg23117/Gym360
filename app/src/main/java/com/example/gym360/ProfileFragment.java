package com.example.gym360;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.gym360.data.local.entity.UserEntity;
import com.example.gym360.data.profile.data.ProfileRepository;
import com.example.gym360.data.sync.ProfileSyncScheduler;
import androidx.work.WorkManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ProfileFragment extends Fragment {
    private TextInputEditText etUpdateName;
    private TextView tvProfileEmail;
    private MaterialCardView cvSyncAlert;
    private MaterialButton btnSaveChanges;
    private ProfileRepository repository;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        etUpdateName = view.findViewById(R.id.etUpdateName);
        tvProfileEmail = view.findViewById(R.id.tvProfileEmail);
        cvSyncAlert = view.findViewById(R.id.cvSyncAlert);
        btnSaveChanges = view.findViewById(R.id.btnSaveChanges);
        MaterialButton btnManagePassword = view.findViewById(R.id.btnManagePassword);
        MaterialButton btnSyncNow = view.findViewById(R.id.btnSyncNow);
        MaterialButton btnLogout = view.findViewById(R.id.btnLogout);
        repository = new ProfileRepository(requireContext());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            tvProfileEmail.setText(user.getEmail());
            repository.loadProfile(new ProfileRepository.Callback() {
                @Override public void onSuccess(@NonNull UserEntity profile) {
                    if (!isAdded() || getView() == null) return;
                    // Evitar sobreescribir el nombre que el usuario esté editando.
                    if (!etUpdateName.hasFocus()) etUpdateName.setText(profile.getFullName());
                    cvSyncAlert.setVisibility(UserEntity.PENDING.equals(profile.getSyncStatus())
                            ? View.VISIBLE : View.GONE);
                }
                @Override public void onError(@NonNull String message) {
                    if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                }
            });
        }

        btnSaveChanges.setOnClickListener(v -> {
            String newName = etUpdateName.getText() == null ? "" : etUpdateName.getText().toString().trim();
            btnSaveChanges.setEnabled(false);
            repository.updateName(newName, new ProfileRepository.ActionCallback() {
                @Override public void onSuccess() {
                    if (!isAdded()) return;
                    btnSaveChanges.setEnabled(true);
                    cvSyncAlert.setVisibility(View.VISIBLE);
                    etUpdateName.clearFocus();
                    Toast.makeText(requireContext(),
                            "Nombre guardado en Room; sincronización programada",
                            Toast.LENGTH_LONG).show();
                }
                @Override public void onError(@NonNull String message) {
                    if (!isAdded()) return;
                    btnSaveChanges.setEnabled(true);
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                }
            });
        });

        btnSyncNow.setOnClickListener(v -> repository.requestSync(
                new ProfileRepository.ActionCallback() {
                    @Override public void onSuccess() {
                        if (isAdded()) Toast.makeText(requireContext(),
                                "Sincronización solicitada; se ejecutará con Internet",
                                Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onError(@NonNull String message) {
                        if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                }));

        btnManagePassword.setOnClickListener(v -> {
            if (getActivity() instanceof HomeActivity) {
                ((HomeActivity) getActivity()).showFragment(
                        new ManagePasswordFragment(), "Gestionar Contraseña");
            }
        });
        btnLogout.setOnClickListener(v -> {
            if (getActivity() instanceof HomeActivity) {
                ((HomeActivity) getActivity()).showLogoutConfirmation();
            }
        });
        return view;
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        WorkManager.getInstance(requireContext())
                .getWorkInfosForUniqueWorkLiveData(ProfileSyncScheduler.uniqueWorkName(user.getUid()))
                .observe(getViewLifecycleOwner(), infos -> refreshSyncStatus());
    }

    private void refreshSyncStatus() {
        if (repository == null || cvSyncAlert == null) return;
        repository.getLocalStatus(new ProfileRepository.Callback() {
            @Override public void onSuccess(@NonNull UserEntity profile) {
                if (isAdded() && getView() != null) {
                    cvSyncAlert.setVisibility(UserEntity.PENDING.equals(profile.getSyncStatus())
                            ? View.VISIBLE : View.GONE);
                }
            }
            @Override public void onError(@NonNull String message) { }
        });
    }

    @Override public void onResume() {
        super.onResume();
        refreshSyncStatus();
    }
}
