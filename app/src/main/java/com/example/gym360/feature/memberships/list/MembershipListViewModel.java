package com.example.gym360.feature.memberships.list;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.gym360.data.local.AppDatabase;
import com.example.gym360.data.repository.MembershipRepositoryImpl;
import com.example.gym360.domain.model.Membership;
import com.example.gym360.domain.repository.MembershipRepository;
import java.util.List;


public class MembershipListViewModel extends ViewModel {
    private final MembershipRepository repository;
    private final LiveData<List<Membership>> memberships;

    public MembershipListViewModel(MembershipRepository repository) {
        this.repository = repository;
        this.memberships = repository.observeMemberships();
    }

    public LiveData<List<Membership>> getMemberships() { return memberships; }
    public LiveData<Integer> getPendingCount() { return repository.observePendingCount(); }

    public void create(Membership m, MembershipRepository.Callback cb) {
        repository.insertMembership(m, cb);
    }
    public void update(Membership m, MembershipRepository.Callback cb) {
        repository.updateMembership(m, cb);
    }
    public void delete(Membership m, MembershipRepository.Callback cb) {
        repository.deleteMembership(m, cb);
    }

    public void sync(MembershipRepository.SyncCallback cb) { repository.syncPendingMemberships(cb); }

    public static class Factory implements ViewModelProvider.Factory {
        private final Context context;
        public Factory(Context context) { this.context = context.getApplicationContext(); }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (!modelClass.isAssignableFrom(MembershipListViewModel.class)) {
                throw new IllegalArgumentException("ViewModel desconocido");
            }
            MembershipRepository repository = new MembershipRepositoryImpl(context,
                    AppDatabase.getInstance(context).membershipDao());
            return (T) new MembershipListViewModel(repository);
        }
    }
}
