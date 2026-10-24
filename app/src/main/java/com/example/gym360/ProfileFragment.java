package com.example.gym360;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileFragment extends Fragment {

    private TextInputEditText etUpdateName;
    private TextView tvProfileEmail;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        etUpdateName = view.findViewById(R.id.etUpdateName);
        tvProfileEmail = view.findViewById(R.id.tvProfileEmail);

        MaterialButton btnSaveChanges = view.findViewById(R.id.btnSaveChanges);
        MaterialButton btnManagePassword = view.findViewById(R.id.btnManagePassword);
        MaterialButton btnSyncNow = view.findViewById(R.id.btnSyncNow);
        MaterialButton btnLogout = view.findViewById(R.id.btnLogout);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        // Obtener el Nombre real desde Firestore
        if (currentUser != null) {
            tvProfileEmail.setText(currentUser.getEmail());

            db.collection("users").document(currentUser.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists() && isAdded()) {
                            String fullName = documentSnapshot.getString("fullName");
                            etUpdateName.setText(fullName);
                        }
                    });
        }

        // 2. Botón para Actualizar Perfil (Solo Nombre)
        btnSaveChanges.setOnClickListener(v -> {
            String newName = etUpdateName.getText().toString().trim();
            if(!newName.isEmpty() && currentUser != null) {
                db.collection("users").document(currentUser.getUid()).update("fullName", newName)
                        .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Nombre actualizado exitosamente", Toast.LENGTH_SHORT).show());
            }
        });

        // 3. Botón para Sincronizar datos locales de Room
        btnSyncNow.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Llamando a la sincronización...", Toast.LENGTH_SHORT).show();
            // TODO: Invocar aquí el método par sincronizar Ej: SyncRepository.syncPendingData(requireContext());
        });

        // Navegar a Gestionar Contraseña
        btnManagePassword.setOnClickListener(v -> {
            if (getActivity() instanceof HomeActivity) {
                ((HomeActivity) getActivity()).showFragment(new ManagePasswordFragment(), "Gestionar Contraseña");
            }
        });

        // 4. Cerrar Sesión
        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            SharedPreferences sharedPreferences = requireActivity().getSharedPreferences("Gym360Prefs", Context.MODE_PRIVATE);
            sharedPreferences.edit().clear().apply();

            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        return view;
    }
}