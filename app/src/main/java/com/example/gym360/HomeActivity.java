package com.example.gym360;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class HomeActivity extends AppCompatActivity implements OnMapReadyCallback {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Vincular el mapa desde el XML
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        TextView tvDirections = findViewById(R.id.tvDirections);
        tvDirections.setOnClickListener(v -> {
            // URI para iniciar navegación paso a paso
            android.net.Uri gmmIntentUri = android.net.Uri.parse("google.navigation:q=13.673410,-89.295624");
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");

            // Verifica que Google Maps esté instalado antes de lanzarlo
            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                android.widget.Toast.makeText(this, "Google Maps no está instalado", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {

        // Coordenadas de la ubicación
        LatLng gymLocation = new LatLng(13.673410, -89.295624);

        // Colocar marcador y acercar cámara
        googleMap.addMarker(new MarkerOptions().position(gymLocation).title("Ubicación Gym360"));
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(gymLocation, 16f));

        googleMap.getUiSettings().setZoomControlsEnabled(true); // Botones de zoom (+/-)
    }
}