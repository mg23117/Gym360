package com.example.gym360;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class HomeActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private MaterialToolbar topAppBar;
    private NavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        drawerLayout = findViewById(R.id.drawerLayout);
        topAppBar = findViewById(R.id.topAppBar);
        navigationView = findViewById(R.id.navigationView);

        // Abrir el SideNav al tocar el icono de hamburguesa
        topAppBar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        // Cargar el HomeFragment por defecto al iniciar
        if (savedInstanceState == null) {
            showFragment(new HomeFragment(), "Inicio");
            navigationView.setCheckedItem(R.id.nav_home);
        }

        // Manejar los clics del menú
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                showFragment(new HomeFragment(), "Inicio");
            } else if (id == R.id.nav_profile) {
                showFragment(new ProfileFragment(), "Mi Perfil");
            } else if (id == R.id.nav_logout) {
                showLogoutConfirmation();
            } else if (id == R.id.nav_client_memberships) {
                showFragment(new ClientMembershipsFragment(), "Membresías y saldo");
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    // Método reutilizable para cambiar fragments y actualizar el título de la Toolbar
    public void showFragment(Fragment fragment, String title) {
        topAppBar.setTitle(title);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.clientFragmentContainer, fragment)
                .commit();
    }

    public void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Estás seguro de que deseas cerrar sesión?")
                .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Cerrar sesión", (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
                    // No borrar Gym360Prefs: el usuario decidió recordar credenciales.
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }).show();
    }
}