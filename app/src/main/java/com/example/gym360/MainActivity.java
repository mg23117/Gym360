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

        // 2. Lógica del Splash: Esperar 2.5 segundos y pasar al Login
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                // Navegar a la pantalla de Login
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                startActivity(intent);

                // Finalizar el Splash Screen para que el usuario no pueda volver a él con el botón "atrás"
                finish();
            }
        }, 2500);
    }
}