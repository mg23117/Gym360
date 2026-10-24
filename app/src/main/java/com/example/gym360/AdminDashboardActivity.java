package com.example.gym360;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.NestedScrollView;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;

import com.example.gym360.feature.memberships.list.MembershipListFragment;
import com.google.android.material.navigation.NavigationView;

import android.util.Log;
import com.google.firebase.FirebaseApp;

public class AdminDashboardActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;

    private NestedScrollView dashboardContent;

    private FragmentContainerView fragmentContainer;

    private static final String MEMBERSHIP_TAG =
            "membership_list";

    private static final String MEMBERSHIP_BACKSTACK =
            "membership_navigation";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_admin_dashboard);

        // Verificación temporal de Firebase
        if (FirebaseApp.getApps(this).isEmpty()) {

            Log.e(
                    "Gym360Firebase",
                    "Firebase no está inicializado. Revisar Gradle y google-services.json"
            );

        } else {

            FirebaseApp app = FirebaseApp.getInstance();

            Log.d(
                    "Gym360Firebase",
                    "Firebase inicializado correctamente. Proyecto: "
                            + app.getOptions().getProjectId()
            );
        }

        initializeViews();
        setupToolbar();
        setupNavigation();
        setupFragmentNavigation();
    }

    private void initializeViews() {

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        toolbar = findViewById(R.id.toolbar);

        dashboardContent = findViewById(R.id.dashboardContent);
        fragmentContainer = findViewById(
                R.id.adminFragmentContainer
        );
    }

    private void setupToolbar() {

        toolbar.setNavigationOnClickListener(v ->
                drawerLayout.openDrawer(navigationView)
        );
    }

    private void setupNavigation() {

        navigationView.setNavigationItemSelectedListener(item -> {

            int itemId = item.getItemId();

            if (itemId == R.id.nav_dashboard) {

                showDashboard();

            } else if (itemId == R.id.nav_memberships) {

                showMemberships();

            } else {

                // Otras secciones aún no implementadas.
                return false;
            }

            drawerLayout.closeDrawer(navigationView);
            return true;
        });
    }

    private void setupFragmentNavigation() {

        getSupportFragmentManager()
                .addOnBackStackChangedListener(
                        this::updateContentVisibility
                );

        // Recuperar el estado al recrear la Activity.
        updateContentVisibility();
    }

    private void showMemberships() {

        FragmentManager fragmentManager =
                getSupportFragmentManager();

        Fragment currentFragment =
                fragmentManager.findFragmentById(
                        R.id.adminFragmentContainer
                );

        // Evitar abrir repetidamente la misma pantalla.
        if (currentFragment instanceof MembershipListFragment) {
            return;
        }

        fragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.adminFragmentContainer,
                        new MembershipListFragment(),
                        MEMBERSHIP_TAG
                )
                .addToBackStack(MEMBERSHIP_BACKSTACK)
                .commit();
    }

    private void showDashboard() {

        FragmentManager fragmentManager =
                getSupportFragmentManager();

        if (fragmentManager.getBackStackEntryCount() > 0) {

            fragmentManager.popBackStack(
                    MEMBERSHIP_BACKSTACK,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE
            );

        } else {

            updateContentVisibility();
        }
    }

    private void updateContentVisibility() {

        Fragment fragment = getSupportFragmentManager()
                .findFragmentById(R.id.adminFragmentContainer);

        boolean showingFragment = fragment != null;

        dashboardContent.setVisibility(
                showingFragment ? View.GONE : View.VISIBLE
        );

        fragmentContainer.setVisibility(
                showingFragment ? View.VISIBLE : View.GONE
        );

        toolbar.setTitle(
                showingFragment
                        ? "Membresías"
                        : "Panel Administrativo"
        );
    }
}
