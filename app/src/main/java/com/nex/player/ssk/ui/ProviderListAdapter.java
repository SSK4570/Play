package com.nex.player.ssk.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nex.player.ssk.R;
import com.nex.player.ssk.utils.Provider;

import java.util.List;

public class ProviderListAdapter extends RecyclerView.Adapter<ProviderListAdapter.VH> {

    public interface OnProviderClick  { void onClick(Provider p); }
    public interface OnProviderMore   { void onMore(Provider p); }

    private final List<Provider> items;
    private final OnProviderClick onActivate;
    private final OnProviderMore  onMore;
    private long activeId;

    public ProviderListAdapter(List<Provider> items, long activeId,
                               OnProviderClick onActivate, OnProviderMore onMore) {
        this.items      = items;
        this.activeId   = activeId;
        this.onActivate = onActivate;
        this.onMore     = onMore;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_provider, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Provider p      = items.get(pos);
        boolean active  = (p.getId() == activeId)
                || (activeId == -1 && pos == 0);

        h.tvName.setText(p.getName().isEmpty() ? p.getDisplayUrl() : p.getName());

        // Show URL below name (like image 2)
        String url = p.getDisplayUrl();
        if (url != null && !url.isEmpty()) {
            h.tvUrl.setText(url);
            h.tvUrl.setVisibility(View.VISIBLE);
        } else {
            h.tvUrl.setVisibility(View.GONE);
        }

        h.rbActive.setChecked(active);

        // Clicking the row: only activate if NOT already active
        h.itemView.setOnClickListener(v -> {
            if (!active) onActivate.onClick(p);
            // If already active (ticked), do nothing — don't reload
        });

        h.btnMore.setOnClickListener(v -> onMore.onMore(p));
    }

    @Override public int getItemCount() { return items == null ? 0 : items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        RadioButton rbActive;
        TextView tvName, tvUrl;
        ImageButton btnMore;
        VH(View v) {
            super(v);
            rbActive = v.findViewById(R.id.rbActive);
            tvName   = v.findViewById(R.id.tvProviderName);
            tvUrl    = v.findViewById(R.id.tvProviderUrl);
            btnMore  = v.findViewById(R.id.btnProviderMore);
        }
    }
}
