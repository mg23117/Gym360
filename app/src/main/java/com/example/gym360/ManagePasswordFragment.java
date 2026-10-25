package com.example.gym360;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.gym360.core.auth.AccountSecurityManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;

public class ManagePasswordFragment extends Fragment {
    private TextInputEditText etCurrentPassword, etNewPassword, etConfirmNewPassword;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manage_password, container, false);
        etCurrentPassword = view.findViewById(R.id.etCurrentPassword);
        etNewPassword = view.findViewById(R.id.etNewPassword);
        etConfirmNewPassword = view.findViewById(R.id.etConfirmNewPassword);
        MaterialButton save = view.findViewById(R.id.btnSaveNewPassword);
        MaterialButton btnBackToProfile = view.findViewById(R.id.btnBackToProfile);

        btnBackToProfile.setOnClickListener(v -> {
            if (getActivity() instanceof HomeActivity) {
                ((HomeActivity) getActivity()).showFragment(new ProfileFragment(), "Mi Perfil");
            }
        });

        save.setOnClickListener(v -> changePassword(save));
        return view;
    }

    private void changePassword(MaterialButton save) {
        String current = etCurrentPassword.getText() == null ? "" : etCurrentPassword.getText().toString();
        String next = etNewPassword.getText() == null ? "" : etNewPassword.getText().toString();
        String confirm = etConfirmNewPassword.getText() == null ? "" : etConfirmNewPassword.getText().toString();
        if (current.isEmpty() || next.isEmpty() || confirm.isEmpty()) {
            Toast.makeText(requireContext(), "Completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!next.equals(confirm)) {
            etConfirmNewPassword.setError("Las contraseñas nuevas no coinciden");
            return;
        }
        save.setEnabled(false);
        AccountSecurityManager.changePassword(requireContext(), current, next)
                .addOnCompleteListener(task -> {
                    if (!isAdded() || getView() == null) return;
                    save.setEnabled(true);
                    if (task.isSuccessful()) {
                        etCurrentPassword.setText("");
                        etNewPassword.setText("");
                        etConfirmNewPassword.setText("");
                        Toast.makeText(requireContext(), "Contraseña actualizada", Toast.LENGTH_LONG).show();
                        if (requireActivity() instanceof HomeActivity) {
                            ((HomeActivity) requireActivity()).showFragment(
                                    new ProfileFragment(), "Mi Perfil");
                        }
                        return;
                    }
                    Exception error = task.getException();
                    if (error instanceof FirebaseAuthInvalidCredentialsException) {
                        etCurrentPassword.setError("Contraseña actual incorrecta");
                    } else if (error instanceof IllegalArgumentException) {
                        Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(requireContext(),
                                "No fue posible cambiar la contraseña. Revisa la conexión.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
