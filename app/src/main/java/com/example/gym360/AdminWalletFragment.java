package com.example.gym360;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.gym360.data.account.ClientAccountRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

/** MVP solo para probar que un administrador pueda ajustar el saldo prepago y acreditar un pago presencial. */
public class AdminWalletFragment extends Fragment {
    public AdminWalletFragment() { super(R.layout.fragment_admin_wallet); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        TextInputEditText customer = view.findViewById(R.id.etWalletCustomerUid);
        TextInputEditText amount = view.findViewById(R.id.etWalletAmount);
        TextInputEditText reason = view.findViewById(R.id.etWalletReason);
        RadioGroup action = view.findViewById(R.id.rgWalletAction);
        MaterialButton button = view.findViewById(R.id.btnApplyWalletAdjustment);
        TextView result = view.findViewById(R.id.tvWalletAdjustmentResult);
        ClientAccountRepository repository = new ClientAccountRepository(requireContext());
        SharedPreferences prefs = requireContext().getSharedPreferences(
                "Gym360WalletRequests", Context.MODE_PRIVATE);

        button.setOnClickListener(v -> {
            String uid = customer.getText() == null ? "" : customer.getText().toString().trim();
            String note = reason.getText() == null ? "" : reason.getText().toString().trim();
            String usd = amount.getText() == null ? "" : amount.getText().toString().trim();
            if (uid.isEmpty() || note.length() < 3) {
                Toast.makeText(requireContext(), "UID y motivo (mínimo 3 caracteres) son requeridos", Toast.LENGTH_LONG).show();
                return;
            }
            long cents;
            try {
                cents = new BigDecimal(usd.replace(',', '.'))
                        .setScale(2, RoundingMode.UNNECESSARY)
                        .movePointRight(2).longValueExact();
                if (cents <= 0 || cents > 500000) throw new IllegalArgumentException();
            } catch (Exception e) {
                amount.setError("Monto válido entre $0.01 y $5000.00");
                return;
            }
            if (action.getCheckedRadioButtonId() == R.id.rbWalletDebit) cents = -cents;
            final long delta = cents;
            final String key = uid + "_" + delta + "_" + note;
            String operationId = prefs.getString(key, null);
            if (operationId == null) {
                operationId = ClientAccountRepository.newOperationId();
                if (!prefs.edit().putString(key, operationId).commit()) {
                    Toast.makeText(requireContext(), "No se pudo preparar la operación", Toast.LENGTH_LONG).show();
                    return;
                }
            }
            button.setEnabled(false);
            repository.adjustWallet(uid, delta, note, operationId,
                    new ClientAccountRepository.PurchaseCallback() {
                        @Override public void onSuccess(@NonNull Map<String, Object> reply) {
                            prefs.edit().remove(key).apply();
                            if (!isAdded()) return;
                            button.setEnabled(true);
                            Object balance = reply.get("balanceCents");
                            if (balance instanceof Number) {
                                result.setText(String.format(Locale.US,
                                        "Operación confirmada. Saldo: $%.2f",
                                        ((Number) balance).doubleValue() / 100.0));
                            } else result.setText("Operación confirmada.");
                        }
                        @Override public void onError(String message) {
                            if (!isAdded()) return;
                            button.setEnabled(true);
                            result.setText(message + " Reintenta sin modificar los datos de la operación.");
                        }
                    });
        });
    }
}
