package com.example.gym360;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import com.example.gym360.core.auth.AdminRoleChecker;
import com.example.gym360.data.sync.MembershipSyncScheduler;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        // 1. Asignamos la vista del Splash Screen
        setContentView(R.layout.activity_splash);

        // Ajuste para la pantalla completa
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Lógica del Splash: Esperar 2.5 segundos y decidir a dónde ir

        new Handler(Looper.getMainLooper()).postDelayed(() -> {

            FirebaseUser user = FirebaseAuth.getInstance()
                    .getCurrentUser();

            // No existe sesión Firebase.
            if (user == null) {

                openActivity(LoginActivity.class);
                return;
            }

            // Existe una sesión. Consultamos el rol real.
            AdminRoleChecker.check(
                    user,
                    new AdminRoleChecker.Callback() {

                        @Override
                        public void onSuccess(boolean isAdmin) {

                            if (isAdmin) {

                                MembershipSyncScheduler.enqueue(
                                        getApplicationContext()
                                );

                                openActivity(AdminDashboardActivity.class);

                            } else {

                                openActivity(HomeActivity.class);
                            }
                        }

                        @Override
                        public void onError(Exception exception) {

                            // No conceder acceso administrativo
                            // sin verificar permisos.
                            Toast.makeText(
                                    MainActivity.this,
                                    "No se pudo verificar la sesión. " +
                                            "Comprueba tu conexión.",
                                    Toast.LENGTH_LONG
                            ).show();

                            openActivity(LoginActivity.class);
                        }
                    }
            );

        }, 2500);

    }

    private void openActivity(Class<?> destination) {

        Intent intent = new Intent(this, destination);

        startActivity(intent);
        finish();
    }
}