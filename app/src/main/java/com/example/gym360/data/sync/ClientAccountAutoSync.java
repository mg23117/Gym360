package com.example.gym360.data.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.gym360.data.repository.ClientAccountRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import java.util.ArrayList;
import java.util.List;

public class ClientAccountAutoSync {
    public interface StatusListener {
        void onStatus(@NonNull String message, boolean offline);
    }

    private final ClientAccountRepository repository;
    private final String uid;
    private final List<ListenerRegistration> registrations = new ArrayList<>();
    @Nullable private final StatusListener listener;
    private boolean active;
    private boolean loading;
    private boolean dirty;
    private boolean hasCompleted;

    public ClientAccountAutoSync(@NonNull ClientAccountRepository repository, @NonNull String uid, @Nullable StatusListener listener) {
        this.repository = repository;
        this.uid = uid;
        this.listener = listener;
    }

    public void start() {
        if (active) return;
        active = true;
        FirebaseFirestore store = FirebaseFirestore.getInstance();
        registrations.add(store.collection("wallets").document(uid)
                .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
                    if (error != null) { offline(); return; }
                    if (snapshot != null && !snapshot.getMetadata().isFromCache()) requestRefresh();}));
        registrations.add(store.collection("user_memberships").document(uid)
                .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
                    if (error != null) { offline(); return; }
                    if (snapshot != null && !snapshot.getMetadata().isFromCache()) requestRefresh();}));
        registrations.add(store.collection("users").document(uid)
                .collection("membership_purchases")
                .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
                    if (error != null) { offline(); return; }
                    if (snapshot != null && !snapshot.getMetadata().isFromCache()) requestRefresh();}));
        requestRefresh();
    }

    public void stop() {
        active = false;
        for (ListenerRegistration item : registrations) item.remove();
        registrations.clear();
    }

    private void requestRefresh() {
        com.google.firebase.auth.FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (!active || current == null || !uid.equals(current.getUid())) return;
        if (loading) { dirty = true; return; }
        loading = true;
        if (!hasCompleted) status("Actualizando información…", false);
        repository.refreshFromServer(new ClientAccountRepository.Callback() {
            @Override public void onSuccess() {
                loading = false;
                if (!active) return;
                hasCompleted = true;
                status("Saldo y membresía actualizados", false);
                repeatIfNeeded();
            }
            @Override public void onError(String error) {
                loading = false;
                if (!active) return;
                status("Sin conexión: mostrando la última copia guardada", true);
                // El listener de Firestore reintenta al reconectar
                dirty = false;
            }
        });
    }

    private void repeatIfNeeded() {
        if (dirty) {
            dirty = false;
            requestRefresh();
        }
    }

    private void offline() {
        if (active) status("Sin conexión: mostrando la última copia guardada", true);
    }

    private void status(String message, boolean offline) {
        if (listener != null && active) listener.onStatus(message, offline);
    }
}
