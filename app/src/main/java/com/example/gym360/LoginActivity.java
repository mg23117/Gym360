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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import com.example.gym360.core.auth.AdminRoleChecker;
import com.example.gym360.data.sync.MembershipSyncScheduler;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private TextInputLayout tilEmail, tilPassword;
    private MaterialCheckBox cbRememberMe;
    private MaterialButton btnLogin;
    private TextView tvGoToRegister;
    private SharedPreferences sharedPreferences;

    private FirebaseAuth firebaseAuth;

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

        firebaseAuth = FirebaseAuth.getInstance();

        // Inicializar SharedPreferences
        sharedPreferences = getSharedPreferences("Gym360Prefs", MODE_PRIVATE);
        loadSavedCredentials();

        // Botón de Iniciar Sesión

        btnLogin.setOnClickListener(v -> {

            if (!validateInputs()) {
                return;
            }

            String email = etEmail.getText()
                    .toString().trim();

            String password = etPassword.getText()
                    .toString();

            btnLogin.setEnabled(false);

            firebaseAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {

                        if (!task.isSuccessful()) {

                            btnLogin.setEnabled(true);

                            Toast.makeText(
                                    this,
                                    "Correo o contraseña incorrectos",
                                    Toast.LENGTH_LONG
                            ).show();

                            return;
                        }

                        FirebaseUser user = firebaseAuth.getCurrentUser();

                        if (user == null) {

                            btnLogin.setEnabled(true);

                            Toast.makeText(
                                    this,
                                    "No se pudo obtener la sesión",
                                    Toast.LENGTH_LONG
                            ).show();

                            return;
                        }

                        // Guardar únicamente el correo para autocompletar.
                        if (cbRememberMe.isChecked()) {
                            sharedPreferences.edit()
                                    .putString("saved_email", email)
                                    .putBoolean("remember_email", true)
                                    .apply();
                        } else {
                            sharedPreferences.edit()
                                    .remove("saved_email")
                                    .putBoolean("remember_email", false)
                                    .apply();
                        }

                        // Nunca conservar la contraseña del login antiguo.
                        sharedPreferences.edit()
                                .remove("saved_password")
                                .remove("user_role")
                                .remove("is_remembered")
                                .apply();

                        // Verificar privilegios desde Firestore.
                        AdminRoleChecker.check(
                                user,
                                new AdminRoleChecker.Callback() {

                                    @Override
                                    public void onSuccess(boolean isAdmin) {

                                        btnLogin.setEnabled(true);

                                        Intent intent;

                                        if (isAdmin) {

                                            // Reanudar sincronizaciones pendientes.
                                            MembershipSyncScheduler.enqueue(
                                                    getApplicationContext()
                                            );

                                            intent = new Intent(
                                                    LoginActivity.this,
                                                    AdminDashboardActivity.class
                                            );

                                        } else {

                                            intent = new Intent(
                                                    LoginActivity.this,
                                                    HomeActivity.class
                                            );
                                        }

                                        startActivity(intent);
                                        finish();
                                    }

                                    @Override
                                    public void onError(Exception exception) {

                                        btnLogin.setEnabled(true);

                                        Toast.makeText(
                                                LoginActivity.this,
                                                "No se pudieron verificar los permisos. " +
                                                        "Comprueba tu conexión.",
                                                Toast.LENGTH_LONG
                                        ).show();
                                    }
                                }
                        );
                    });
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

        boolean rememberEmail = sharedPreferences.getBoolean(
                "remember_email",
                false
        );

        // Si no hay credenciales guardadas, no hacer nada
        sharedPreferences.edit()
                .remove("saved_password")
                .remove("user_role")
                .remove("is_remembered")
                .apply();

        if (rememberEmail) {

            etEmail.setText(
                    sharedPreferences.getString("saved_email", "")
            );

            cbRememberMe.setChecked(true);
        }
    }
}
