package com.example.gym360;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;

public class AdminDashboardActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        toolbar = findViewById(R.id.toolbar);

        // Abrir el menú al darle click a la hamburguesa
        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(navigationView));

        // Manejar los clicks del menú (TODO: hacerlo luego para poder navegar entre las pantallas)
        navigationView.setNavigationItemSelectedListener(item -> {
            // Ahorita solo cerramos el menú, pero aquí va a ir la lógica de la navegación
            drawerLayout.closeDrawer(navigationView);
            return true;
        });
    }
}
