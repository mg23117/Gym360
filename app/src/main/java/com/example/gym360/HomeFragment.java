package com.example.gym360;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.gym360.data.account.ClientAccountRepository;
import com.example.gym360.data.sync.ClientAccountAutoSync;
import com.example.gym360.domain.model.MembershipDateLabels;
import com.example.gym360.domain.model.MembershipTime;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Locale;

public class HomeFragment extends Fragment implements OnMapReadyCallback {
    private ClientAccountAutoSync autoSync;
    private TextView syncStatus;
    private boolean sawOffline;

    public HomeFragment() { super(R.layout.fragment_home); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        syncStatus = view.findViewById(R.id.tvHomeSyncStatus);
        TextView name = view.findViewById(R.id.tvHomeMembershipName);
        TextView remaining = view.findViewById(R.id.tvHomeDays);
        TextView expiry = view.findViewById(R.id.tvHomeExpiry);
        TextView balance = view.findViewById(R.id.tvHomeBalance);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user != null) {
            String uid = user.getUid();
            ClientAccountRepository account = new ClientAccountRepository(requireContext());
            account.observeWallet(uid).observe(getViewLifecycleOwner(), wallet -> {
                balance.setText(wallet == null ? "Sin datos" : String.format(Locale.US, "$%.2f", wallet.balanceCents / 100.0));});
            account.observeMembership(uid).observe(getViewLifecycleOwner(), membership -> {
                if (membership == null) {
                    name.setText("Sin membresía activa");
                    remaining.setText("Explora nuestros planes");
                    expiry.setText("");

                    return;
                }
                try {
                    int days = MembershipTime.remainingDays(membership.expiresOnExclusive);
                    name.setText(days > 0 ? membership.activePlanName : "Membresía vencida");
                    remaining.setText(days > 0 ? days + (days == 1 ? " día restante" : " días restantes") : "0 días restantes");
                    expiry.setText(days > 0 ? "Vence el " + MembershipDateLabels.lastValidDay(membership.expiresOnExclusive) : "Renueva para continuar entrenando");
                } catch (IllegalArgumentException ex) {
                    name.setText("Membresía sin fecha válida");
                    remaining.setText("");
                    expiry.setText("");
                }
            });
            autoSync = new ClientAccountAutoSync(account, uid, (text, offline) -> {
                if (isAdded() && syncStatus != null) {
                    syncStatus.setText(text);
                    if (offline) sawOffline = true;
                    else if (sawOffline && !text.startsWith("Actualizando")) {
                        sawOffline = false;
                        com.google.android.material.snackbar.Snackbar.make(
                                requireView(), "Tu información está actualizada",
                                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                    }
                }
            });
        } else {
            syncStatus.setText("Inicia sesión para consultar tus datos");
        }
        view.findViewById(R.id.btnExtendMembership).setOnClickListener(v -> openMemberships());
        view.findViewById(R.id.cardQuickMemberships).setOnClickListener(v -> openMemberships());
        view.findViewById(R.id.tvGoWallet).setOnClickListener(v -> openWallet());
        view.findViewById(R.id.cardQuickHistory).setOnClickListener(v -> openWallet());
        view.findViewById(R.id.cardQuickExercises).setOnClickListener(v ->
                Toast.makeText(requireContext(), "Módulo de ejercicios próximamente", Toast.LENGTH_SHORT).show());
        view.findViewById(R.id.cardQuickStore).setOnClickListener(v ->
                Toast.makeText(requireContext(), "Tienda próximamente", Toast.LENGTH_SHORT).show());
        SupportMapFragment map = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.mapFragment);

        if (map != null) map.getMapAsync(this);
        view.findViewById(R.id.tvDirections).setOnClickListener(v -> {
            android.net.Uri uri = android.net.Uri.parse("google.navigation:q=13.673410,-89.295624");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setPackage("com.google.android.apps.maps");
            if (intent.resolveActivity(requireActivity().getPackageManager()) != null) startActivity(intent);
            else Toast.makeText(requireContext(), "Google Maps no está instalado", Toast.LENGTH_SHORT).show();
        });
        ((TextView) view.findViewById(R.id.tvHomeAttendance)).setText("Aún no hay asistencias disponibles en este módulo");
    }

    @Override public void onStart() {
        super.onStart();
        if (autoSync != null) autoSync.start();
    }

    @Override public void onStop() {
        if (autoSync != null) autoSync.stop();
        super.onStop();
    }

    private void openMemberships() {
        if (getActivity() instanceof HomeActivity) ((HomeActivity) getActivity()).openMemberships();
    }
    private void openWallet() {
        if (getActivity() instanceof HomeActivity) ((HomeActivity) getActivity()).openWallet();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        LatLng gymLocation = new LatLng(13.673410, -89.295624);
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.addMarker(new MarkerOptions().position(gymLocation).title("Ubicación Gym360"));
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(gymLocation, 16f));
    }
}