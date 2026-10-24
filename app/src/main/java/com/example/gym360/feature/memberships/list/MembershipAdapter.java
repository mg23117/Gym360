package com.example.gym360.feature.memberships.list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.gym360.R;
import com.example.gym360.domain.model.Membership;
import com.google.android.material.button.MaterialButton;
import java.util.Locale;
import java.util.Objects;

public class MembershipAdapter extends ListAdapter<Membership, MembershipAdapter.Holder> {
    public interface Listener {
        void onEdit(Membership membership);
        void onDelete(Membership membership);
    }
    private final Listener listener;

    public MembershipAdapter(Listener listener) { super(DIFF); this.listener = listener; }

    private static final DiffUtil.ItemCallback<Membership> DIFF = new DiffUtil.ItemCallback<Membership>() {
        @Override public boolean areItemsTheSame(@NonNull Membership a, @NonNull Membership b) {
            return Objects.equals(a.getId(), b.getId());
        }
        @Override public boolean areContentsTheSame(@NonNull Membership a, @NonNull Membership b) {
            return Objects.equals(a.getName(), b.getName()) && a.getPrice() == b.getPrice()
                    && a.getDurationMonths() == b.getDurationMonths()
                    && Objects.equals(a.getBenefits(), b.getBenefits());
        }
    };

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_membership, parent, false);
        return new Holder(view);
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        Membership m = getItem(position);
        h.name.setText(m.getName());
        h.price.setText(String.format(Locale.US, "$%.2f", m.getPrice()));
        h.duration.setText(m.getDurationMonths() == 1 ? "1 mes" : m.getDurationMonths() + " meses");
        int n = m.getBenefits() == null ? 0 : m.getBenefits().size();
        h.benefits.setText(n == 1 ? "1 beneficio" : n + " beneficios");
        h.edit.setOnClickListener(v -> listener.onEdit(m));
        h.delete.setOnClickListener(v -> listener.onDelete(m));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView name, price, duration, benefits;
        final MaterialButton edit, delete;
        Holder(View view) {
            super(view);
            name = view.findViewById(R.id.tvMembershipName);
            price = view.findViewById(R.id.tvMembershipPrice);
            duration = view.findViewById(R.id.tvMembershipDuration);
            benefits = view.findViewById(R.id.tvMembershipBenefits);
            edit = view.findViewById(R.id.btnEditMembership);
            delete = view.findViewById(R.id.btnDeleteMembership);
        }
    }
}
