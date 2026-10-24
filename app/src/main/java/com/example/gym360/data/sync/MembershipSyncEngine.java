package com.example.gym360.data.sync;

import com.example.gym360.data.local.dao.MembershipDao;
import com.example.gym360.data.local.entity.MembershipEntity;
import com.example.gym360.data.remote.firebase.firestore.MembershipFirestoreDataSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.List;

/** Sube cambios Room -> Firestore y refresca Firestore -> Room. */
public final class MembershipSyncEngine {
    private static final Object SYNC_LOCK = new Object();
    private static final int MAX_CHANGES_PER_RUN = 15;
    private final MembershipDao dao;
    private final MembershipFirestoreDataSource remote;

    public MembershipSyncEngine(MembershipDao dao) {
        this.dao = dao;
        this.remote = new MembershipFirestoreDataSource(FirebaseFirestore.getInstance());
    }

    // Debe ejecutarse en un Worker o hilo de I/O, nunca en Main.
    public int syncBlocking() throws Exception {
        synchronized (SYNC_LOCK) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                throw new IllegalStateException("Inicia sesión con Firebase Auth para sincronizar");
            }
            String initialUid = user.getUid();
            int confirmed = 0;
            List<MembershipEntity> pending = dao.getPendingMemberships();
            int limit = Math.min(pending.size(), MAX_CHANGES_PER_RUN);
            for (int index = 0; index < limit; index++) {
                assertSameUser(initialUid);
                MembershipEntity row = pending.get(index);
                if (MembershipEntity.PENDING_UPSERT.equals(row.getSyncStatus())) {
                    remote.upsert(row);
                    assertSameUser(initialUid);
                    confirmed += dao.markAsSynced(row.getId(), row.getLocalRevision());
                } else if (MembershipEntity.PENDING_DELETE.equals(row.getSyncStatus())) {
                    remote.delete(row.getId());
                    assertSameUser(initialUid);
                    confirmed += dao.purgeConfirmedDelete(row.getId(), row.getLocalRevision());
                }
            }
            assertSameUser(initialUid);
            // Descargar desde servidor, no desde la caché del SDK.
            dao.mergeServerSnapshot(remote.fetchAll());
            // El Worker podrá reintentar en caso de que se crearan más cambios durante el envío.
            return confirmed;
        }
    }

    private static void assertSameUser(String expectedUid) {
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current == null || !expectedUid.equals(current.getUid())) {
            throw new IllegalStateException("Cambió la sesión durante la sincronización");
        }
    }
}
