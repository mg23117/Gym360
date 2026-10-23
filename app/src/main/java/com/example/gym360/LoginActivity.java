package com.example.gym360;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private TextInputLayout tilEmail, tilPassword;
    private MaterialCheckBox cbRememberMe;
    private MaterialButton btnLogin;
    private TextView tvGoToRegister;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Vincular vistas
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        cbRememberMe = findViewById(R.id.cbRememberMe);
        btnLogin = findViewById(R.id.btnLogin);
        tvGoToRegister = findViewById(R.id.tvGoToRegister);

        // Inicializar SharedPreferences
        sharedPreferences = getSharedPreferences("Gym360Prefs", MODE_PRIVATE);
        loadSavedCredentials();

        // Botón de Iniciar Sesión
        btnLogin.setOnClickListener(v -> {
            if (validateInputs()) {
                if (cbRememberMe.isChecked()) {
                    saveCredentials(etEmail.getText().toString().trim(), etPassword.getText().toString().trim());
                } else {
                    clearCredentials();
                }

                // Aquí irá luego la validación con Firebase/Room.
                // Por ahora pasamos directo al Home.
                Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                startActivity(intent);
                finish();
            }
        });

        // Enlace a Registro
        tvGoToRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    private boolean validateInputs() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        boolean isValid = true;

        // Validar Email
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Ingrese un correo electrónico válido");
            isValid = false;
        } else {
            tilEmail.setError(null);
        }

        // Validar Contraseña (Min 8 caracteres, alfanumérica)
        String passwordRegex = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$";
        if (password.isEmpty() || !password.matches(passwordRegex)) {
            tilPassword.setError("Mínimo 8 caracteres, debe contener letras y números");
            isValid = false;
        } else {
            tilPassword.setError(null);
        }

        return isValid;
    }

    private void saveCredentials(String email, String password) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("saved_email", email);
        editor.putString("saved_password", password);
        editor.putBoolean("is_remembered", true);
        editor.apply();
    }

    private void clearCredentials() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
    }

    private void loadSavedCredentials() {
        boolean isRemembered = sharedPreferences.getBoolean("is_remembered", false);
        if (isRemembered) {
            etEmail.setText(sharedPreferences.getString("saved_email", ""));
            etPassword.setText(sharedPreferences.getString("saved_password", ""));
            cbRememberMe.setChecked(true);
        }
    }
}
