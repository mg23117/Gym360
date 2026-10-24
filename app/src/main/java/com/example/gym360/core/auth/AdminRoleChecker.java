
package com.example.gym360.core.auth;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

public final class AdminRoleChecker {

    private AdminRoleChecker() {}

    public interface Callback {
        void onSuccess(boolean isAdmin);
        void onError(Exception exception);
    }

    public static void check(
            @NonNull FirebaseUser user,
            @NonNull Callback callback) {

        FirebaseFirestore.getInstance()
                .collection("admin_users")
                .document(user.getUid())
                .get(Source.SERVER)
                .addOnSuccessListener(document -> {
                    callback.onSuccess(document.exists());
                })
                .addOnFailureListener(callback::onError);
    }
}
