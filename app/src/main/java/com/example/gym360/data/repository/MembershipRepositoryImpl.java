package com.example.gym360.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import com.example.gym360.data.local.dao.MembershipDao;
import com.example.gym360.data.local.entity.MembershipEntity;
import com.example.gym360.data.mapper.MembershipMapper;
import com.example.gym360.data.sync.MembershipSyncEngine;
import com.example.gym360.data.sync.MembershipSyncScheduler;
import com.example.gym360.domain.model.Membership;
import com.example.gym360.domain.repository.MembershipRepository;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Repositorio LOCAL-ONLY: nunca marca SYNCED ni simula escrituras remotas. */
public class MembershipRepositoryImpl implements MembershipRepository {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private final MembershipDao dao;
    private final Context context;
    private final LiveData<List<Membership>> memberships;

    public MembershipRepositoryImpl(@NonNull Context context, @NonNull MembershipDao dao) {
        this.dao = dao;
        this.context = context.getApplicationContext();

        memberships = Transformations.map(dao.observeVisibleMemberships(), entities -> {
            List<Membership> result = new ArrayList<>();
            if (entities != null) {
                for (MembershipEntity entity : entities) {
                    result.add(MembershipMapper.fromEntityToDomain(entity));
                }
            }
            return result;
        });
        // Refrescar al abrir el módulo solo si hay sesión Firebase válida.
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            MembershipSyncScheduler.enqueue(this.context);
        }
    }

    @Override
    public LiveData<List<Membership>> observeMemberships() { return memberships; }

    @Override
    public LiveData<Integer> observePendingCount() { return dao.observePendingCount(); }

    @Override
    public void insertMembership(Membership membership, Callback callback) {
        IO.execute(() -> {
            try {
                validate(membership);
                String id = membership.getId();
                if (id == null || id.trim().isEmpty()) id = UUID.randomUUID().toString();
                MembershipEntity entity = new MembershipEntity(id, membership.getName().trim(),
                        membership.getDurationMonths(), membership.getPrice(),
                        safeBenefits(membership.getBenefits()), MembershipEntity.PENDING_UPSERT, 1);
                dao.insert(entity);
                scheduleSyncSafely();
                MAIN.post(callback::onSuccess);
            } catch (Exception e) {
                MAIN.post(() -> callback.onError(message(e)));
            }
        });
    }

    @Override
    public void updateMembership(Membership membership, Callback callback) {
        IO.execute(() -> {
            try {
                validate(membership);
                if (membership.getId() == null || membership.getId().trim().isEmpty()) {
                    throw new IllegalArgumentException("Falta el identificador de la membresía");
                }
                MembershipEntity previous = dao.getMembershipById(membership.getId());
                if (previous == null || MembershipEntity.PENDING_DELETE.equals(previous.getSyncStatus())) {
                    throw new IllegalArgumentException("La membresía no existe o fue eliminada");
                }
                MembershipEntity updated = new MembershipEntity(membership.getId(),
                        membership.getName().trim(), membership.getDurationMonths(),
                        membership.getPrice(), safeBenefits(membership.getBenefits()),
                        MembershipEntity.PENDING_UPSERT, previous.getLocalRevision() + 1);
                if (dao.update(updated) == 0) throw new IllegalStateException("No se pudo actualizar");
                scheduleSyncSafely();
                MAIN.post(callback::onSuccess);
            } catch (Exception e) {
                MAIN.post(() -> callback.onError(message(e)));
            }
        });
    }

    @Override
    public void deleteMembership(Membership membership, Callback callback) {
        IO.execute(() -> {
            try {
                if (membership == null || membership.getId() == null) {
                    throw new IllegalArgumentException("Membresía inválida");
                }
                int affected = dao.markPendingDelete(membership.getId());
                if (affected == 0) throw new IllegalArgumentException("La membresía ya no existe");
                scheduleSyncSafely();
                MAIN.post(callback::onSuccess);
            } catch (Exception e) {
                MAIN.post(() -> callback.onError(message(e)));
            }
        });
    }

    @Override
    public void getAllMemberships(DataCallback<List<Membership>> callback) {
        IO.execute(() -> {
            try {
                List<Membership> result = new ArrayList<>();
                for (MembershipEntity entity : dao.getAllVisibleMemberships()) {
                    result.add(MembershipMapper.fromEntityToDomain(entity));
                }
                MAIN.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                MAIN.post(() -> callback.onError(message(e)));
            }
        });
    }

    @Override
    public void syncPendingMemberships(SyncCallback callback) {
        IO.execute(() -> {
            try {
                int count = new MembershipSyncEngine(dao).syncBlocking();
                MAIN.post(() -> callback.onSuccess(count));
                if (dao.getPendingCount() != 0) MembershipSyncScheduler.enqueue(context);
            } catch (Exception e) {
                // No crear un ciclo de reintentos por permisos denegados o falta de sesión;
                // el Worker ya programado gestiona los fallos transitorios.
                MAIN.post(() -> callback.onError(message(e)));
            }
        });
    }

    private void scheduleSyncSafely() {

        // La operación local ya fue guardada en Room.
        // No intentar sincronizar sin sesión Firebase.
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {

            Log.d(
                    "Gym360MembershipSync",
                    "Sin sesión Firebase. " +
                            "Los cambios quedan pendientes en Room."
            );

            return;
        }

        try {

            MembershipSyncScheduler.enqueue(context);

        } catch (RuntimeException exception) {

            Log.e(
                    "Gym360MembershipSync",
                    "No se pudo programar la sincronización",
                    exception
            );
        }
    }

    private static void validate(Membership model) {
        if (model == null) throw new IllegalArgumentException("Membresía inválida");
        if (model.getName() == null || model.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Ingresa el nombre de la membresía");
        }
        if (model.getDurationMonths() <= 0) {
            throw new IllegalArgumentException("La duración debe ser mayor que cero");
        }
        if (!Double.isFinite(model.getPrice()) || model.getPrice() < 0) {
            throw new IllegalArgumentException("El precio debe ser un número válido no negativo");
        }
    }

    private static List<String> safeBenefits(List<String> benefits) {
        return benefits == null ? Collections.emptyList() : new ArrayList<>(benefits);
    }

    private static String message(Exception e) {
        Throwable t = e;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? "Ocurrió un error" : t.getMessage();
    }
}
