package com.example.gym360.data.remote.firebase.firestore;

import com.example.gym360.data.local.entity.MembershipEntity;
import com.example.gym360.data.mapper.MembershipMapper;
import com.example.gym360.data.model.MembershipDto;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Operaciones remotas BLOQUEANTES: invocar exclusivamente fuera del hilo principal. */
public final class MembershipFirestoreDataSource {
    private static final long TIMEOUT_SECONDS = 25;
    private final FirebaseFirestore firestore;

    public MembershipFirestoreDataSource(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public void upsert(MembershipEntity membership) throws Exception {
        Tasks.await(firestore.collection("memberships").document(membership.getId())
                .set(MembershipMapper.toDto(membership)), TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public void delete(String id) throws Exception {
        Tasks.await(firestore.collection("memberships").document(id)
                .delete(), TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public List<MembershipEntity> fetchAll() throws Exception {
        QuerySnapshot snapshot = Tasks.await(
                firestore.collection("memberships").get(Source.SERVER),
                TIMEOUT_SECONDS, TimeUnit.SECONDS);
        List<MembershipEntity> result = new ArrayList<>();
        for (QueryDocumentSnapshot doc : snapshot) {
            MembershipDto dto = doc.toObject(MembershipDto.class);
            // El ID canónico del documento evita desacuerdos con el campo "id".
            dto.setId(doc.getId());
            result.add(MembershipMapper.fromDto(dto));
        }
        return result;
    }
}
