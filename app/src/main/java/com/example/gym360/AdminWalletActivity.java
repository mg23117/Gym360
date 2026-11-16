package com.example.gym360;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

/** Esta es solo una pantalla MVP temporal de caja. Está puesto con android:exported="false" en el manifest. */
public class AdminWalletActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_wallet);
        MaterialButton back = findViewById(R.id.btnBackFromWallet);
        back.setOnClickListener(v -> finish());
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.adminWalletFragmentContainer, new AdminWalletFragment())
                    .commit();
        }
    }
}

