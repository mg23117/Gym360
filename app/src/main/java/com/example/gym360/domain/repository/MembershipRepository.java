package com.example.gym360.domain.repository;

import androidx.lifecycle.LiveData;

import com.example.gym360.domain.model.Membership;
import java.util.List;

public interface MembershipRepository {
    LiveData<List<Membership>> observeMemberships();
    LiveData<Integer> observePendingCount();
    void insertMembership(Membership membership, Callback callback);
    void updateMembership(Membership membership, Callback callback);
    void deleteMembership(Membership membership, Callback callback);
    void getAllMemberships(DataCallback<List<Membership>> callback);
    void syncPendingMemberships(SyncCallback callback);

    interface Callback {
        void onSuccess();
        void onError(String message);
    }
    interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String message);
    }
    interface SyncCallback {
        void onSuccess(int count);
        void onError(String message);
    }
}
