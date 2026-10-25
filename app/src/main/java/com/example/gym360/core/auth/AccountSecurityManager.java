
package com.example.gym360.core.auth;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public final class AccountSecurityManager {

    private AccountSecurityManager() {
    }

    /**
     * Cambia la contraseña usando Firebase Authentication.
     * La contraseña actual es comprobada por Firebase.
     */
    public static Task<Void> changePassword(@NonNull Context context, @NonNull String currentPassword, @NonNull String newPassword) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            return Tasks.forException(new IllegalStateException("Debes iniciar sesión."));
        }

        String email = user.getEmail();
        if (email == null || email.isEmpty()) {
            return Tasks.forException(new IllegalStateException("No se encontró el correo del usuario."));
        }

        if (currentPassword.isEmpty() || newPassword.isEmpty()) {
            return Tasks.forException(new IllegalArgumentException("Completa todos los campos."));
        }

        if (currentPassword.equals(newPassword)) {
            return Tasks.forException(new IllegalArgumentException("La nueva contraseña debe ser diferente."));
        }

        // Validación consistente con el Login actual.
        String passwordRegex = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$";

        if (!newPassword.matches(passwordRegex)) {
            return Tasks.forException(new IllegalArgumentException("La nueva contraseña debe tener al menos " + "8 caracteres, letras y números."));
        }

        // Firebase verifica la contraseña actual.
        AuthCredential credential =
                EmailAuthProvider.getCredential(email, currentPassword);

        Context appContext = context.getApplicationContext();

        return user.reauthenticate(credential).continueWithTask(task -> {

                    if (!task.isSuccessful()) {
                        Exception error = task.getException();

                        return Tasks.<Void>forException(
                                error != null ? error : new IllegalStateException("No se pudo verificar la contraseña.")
                        );
                    }

                    // Solo se ejecuta cuando Firebase
                    // confirma la contraseña actual.
                    return user.updatePassword(newPassword);
                })
                .addOnSuccessListener(unused -> {
                    // La contraseña anterior recordada
                    // ya no debe autocompletarse.
                    clearRememberedPassword(appContext);
                });
    }

    private static void clearRememberedPassword(Context context) {
        SharedPreferences preferences = context.getSharedPreferences("Gym360Prefs", Context.MODE_PRIVATE);

        preferences.edit()
                .remove("saved_password")
                .remove("saved_password_encrypted")
                .remove("remember_email")
                .remove("is_remembered")
                .apply();
    }
}
