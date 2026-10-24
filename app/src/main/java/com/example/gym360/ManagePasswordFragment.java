package com.example.gym360;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

//import org.mindrot.jbcrypt.BCrypt;

public class ManagePasswordFragment extends Fragment {

    private TextInputEditText etCurrentPassword, etNewPassword, etConfirmNewPassword;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manage_password, container, false);

        etCurrentPassword = view.findViewById(R.id.etCurrentPassword);
        etNewPassword = view.findViewById(R.id.etNewPassword);
        etConfirmNewPassword = view.findViewById(R.id.etConfirmNewPassword);
        MaterialButton btnSaveNewPassword = view.findViewById(R.id.btnSaveNewPassword);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        db = FirebaseFirestore.getInstance();

        btnSaveNewPassword.setOnClickListener(v -> updatePasswordLogic());

        return view;
    }

    private void updatePasswordLogic() {
        String currentPass = etCurrentPassword.getText().toString().trim();
        String newPass = etNewPassword.getText().toString().trim();
        String confirmPass = etConfirmNewPassword.getText().toString().trim();

        if (currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
            Toast.makeText(getContext(), "Llene todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newPass.equals(confirmPass)) {
            Toast.makeText(getContext(), "Las contraseñas nuevas no coinciden", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentUser != null && currentUser.getEmail() != null) {
            // 1. Reautenticar al usuario
            AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), currentPass);
            currentUser.reauthenticate(credential).addOnSuccessListener(aVoid -> {

                // 2. Actualizar en Firebase Auth
                currentUser.updatePassword(newPass).addOnSuccessListener(aVoid1 -> {

//                    // 3. Encriptar y actualizar en Firestore
//                    String encryptedPassword = BCrypt.hashpw(newPass, BCrypt.gensalt());
//                    db.collection("users").document(currentUser.getUid()).update("password", encryptedPassword)
//                            .addOnSuccessListener(aVoid2 -> {
//                                Toast.makeText(getContext(), "Contraseña actualizada exitosamente", Toast.LENGTH_SHORT).show();
//                                // Regresar al Perfil
//                                if (getActivity() instanceof HomeActivity) {
//                                    ((HomeActivity) getActivity()).showFragment(new ProfileFragment(), "Mi Perfil");
//                                }
//                            });
                }).addOnFailureListener(e -> Toast.makeText(getContext(), "Error al actualizar clave", Toast.LENGTH_SHORT).show());

            }).addOnFailureListener(e -> etCurrentPassword.setError("Contraseña actual incorrecta"));
        }
    }
}