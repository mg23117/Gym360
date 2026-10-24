package com.example.gym360.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import com.example.gym360.data.local.entity.MembershipEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Dao
public interface MembershipDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(MembershipEntity membership);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertRemoteIfMissing(MembershipEntity membership);

    @Update
    int update(MembershipEntity membership);

    @Query("SELECT * FROM memberships WHERE syncStatus != 'PENDING_DELETE' ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<MembershipEntity>> observeVisibleMemberships();

    @Query("SELECT * FROM memberships WHERE syncStatus != 'PENDING_DELETE' ORDER BY name COLLATE NOCASE ASC")
    List<MembershipEntity> getAllVisibleMemberships();

    @Query("SELECT COUNT(*) FROM memberships WHERE syncStatus != 'SYNCED'")
    int getPendingCount();

    @Query("SELECT COUNT(*) FROM memberships WHERE syncStatus != 'SYNCED'")
    LiveData<Integer> observePendingCount();

    @Query("SELECT * FROM memberships WHERE id = :id LIMIT 1")
    MembershipEntity getMembershipById(String id);

    @Query("UPDATE memberships SET syncStatus = 'PENDING_DELETE' WHERE id = :id AND syncStatus != 'PENDING_DELETE'")
    int markPendingDelete(String id);

    @Query("SELECT * FROM memberships WHERE syncStatus IN ('PENDING_UPSERT', 'PENDING_DELETE') ORDER BY name ASC")
    List<MembershipEntity> getPendingMemberships();

    @Query("UPDATE memberships SET syncStatus = 'SYNCED' WHERE id = :id AND syncStatus = 'PENDING_UPSERT' AND localRevision = :revision")
    int markAsSynced(String id, long revision);

    @Query("DELETE FROM memberships WHERE id = :id AND syncStatus = 'PENDING_DELETE' AND localRevision = :revision")
    int purgeConfirmedDelete(String id, long revision);

    @Query("SELECT id FROM memberships WHERE syncStatus = 'SYNCED'")
    List<String> getSyncedIds();

    @Query("DELETE FROM memberships WHERE id = :id AND syncStatus = 'SYNCED'")
    int removeSyncedIfAbsent(String id);

    // La transacción evita que una descarga sobrescriba operaciones locales pendientes.
    @Transaction
    default void mergeServerSnapshot(List<MembershipEntity> remote) {
        Set<String> serverIds = new HashSet<>();
        for (MembershipEntity downloaded : remote) {
            serverIds.add(downloaded.getId());
            MembershipEntity current = getMembershipById(downloaded.getId());
            if (current == null) {
                insertRemoteIfMissing(downloaded);
            } else if (MembershipEntity.SYNCED.equals(current.getSyncStatus())) {
                downloaded.setLocalRevision(current.getLocalRevision());
                update(downloaded);
            }
            // PENDING_UPSERT y PENDING_DELETE no se pisan con los datos del servidor.
        }
        // Descarga completa desde SERVER: propagar borrados remotos solo a filas SYNCED.
        for (String localId : getSyncedIds()) {
            if (!serverIds.contains(localId)) removeSyncedIfAbsent(localId);
        }
    }
}
