package com.example.gym360;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.gym360.data.repository.UserRepositoryImpl;
import com.example.gym360.domain.repository.UserRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etFullName, etEmail, etPassword, etConfirmPassword;
    private TextInputLayout tilFullName, tilEmail, tilPassword, tilConfirmPassword;
    private MaterialButton btnRegister;
    private TextView tvGoToLogin;

    private UserRepository userRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Vincular vistas
        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmailRegister);
        etPassword = findViewById(R.id.etPasswordRegister);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);

        tilFullName = findViewById(R.id.tilFullName);
        tilEmail = findViewById(R.id.tilEmailRegister);
        tilPassword = findViewById(R.id.tilPasswordRegister);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);

        btnRegister = findViewById(R.id.btnRegister);
        tvGoToLogin = findViewById(R.id.tvGoToLogin);

        userRepository = new UserRepositoryImpl(getApplicationContext());

        btnRegister.setOnClickListener(v -> {
            if (!validateInputs()) return;
            btnRegister.setEnabled(false);
            String name = etFullName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString();

            userRepository.register(name, email, password, new UserRepository.RegisterCallback() {
                @Override
                public void onSuccess() {
                    btnRegister.setEnabled(true);
                    // Retirar cualquier credencial/rol simulado heredado.
                    getSharedPreferences("Gym360Prefs", MODE_PRIVATE).edit()
                            .remove("saved_password")
                            .remove("user_role")
                            .remove("is_remembered")
                            .apply();
                    Toast.makeText(RegisterActivity.this,
                            "Cuenta creada correctamente", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(RegisterActivity.this, HomeActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                }

                @Override
                public void onError(String message) {
                    btnRegister.setEnabled(true);
                    Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        });

        // Enlace a Login
        tvGoToLogin.setOnClickListener(v -> {
            finish(); // Cierra esta actividad y regresa al Login
        });
    }

    private boolean validateInputs() {
        String fullName = etFullName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();
        boolean isValid = true;

        // Validar Nombre Completo (Min 7 caracteres)
        if (fullName.isEmpty() || fullName.length() < 7) {
            tilFullName.setError("El nombre debe tener al menos 7 caracteres");
            isValid = false;
        } else {
            tilFullName.setError(null);
        }

        // Validar Email
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Ingrese un correo electrónico válido");
            isValid = false;
        } else {
            tilEmail.setError(null);
        }

        // Validar Contraseña
        String passwordRegex = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$";
        if (password.isEmpty() || !password.matches(passwordRegex)) {
            tilPassword.setError("Mínimo 8 caracteres, letras y números");
            isValid = false;
        } else {
            tilPassword.setError(null);
        }

        // Validar Confirmación de Contraseña
        if (!password.equals(confirmPassword)) {
            tilConfirmPassword.setError("Las contraseñas no coinciden");
            isValid = false;
        } else {
            tilConfirmPassword.setError(null);
        }

        return isValid;
    }
}