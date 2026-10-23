package com.example.gym360;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                // Leer SharedPreferences para ver si guardó sesión
                android.content.SharedPreferences prefs = getSharedPreferences("Gym360Prefs", MODE_PRIVATE);
                boolean isRemembered = prefs.getBoolean("is_remembered", false);

                Intent intent;
                if (isRemembered) {
                    // Si ya tiene sesión, mandarlo directo al Home
                    intent = new Intent(MainActivity.this, HomeActivity.class);
                } else {
                    // Si no, mandarlo al Login
                    intent = new Intent(MainActivity.this, LoginActivity.class);
                }
                startActivity(intent);

                finish(); // Destruye el Splash para no poder volver con el botón atrás
            }
        }, 2500);
    }
}