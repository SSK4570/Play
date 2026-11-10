package com.nex.player.ssk.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nex.player.ssk.R;
import com.nex.player.ssk.utils.Provider;

import java.util.List;

public class ProviderListAdapter extends RecyclerView.Adapter<ProviderListAdapter.VH> {

    public interface OnSelect { void select(Provider p); }
    public interface OnDelete { void delete(Provider p); }

    private final List<Provider> items;
    private final OnSelect onSelect;
    private final OnDelete onDelete;

    public ProviderListAdapter(List<Provider> items, OnSelect onSelect, OnDelete onDelete) {
        this.items = items;
        this.onSelect = onSelect;
        this.onDelete = onDelete;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_provider, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Provider p = items.get(position);
        holder.tvName.setText(p.getName());
        holder.tvUrl.setText(p.getDisplayUrl());
        if (p.getChannelCount() > 0) {
            holder.tvCount.setText(p.getChannelCount() + " channels");
        } else {
            holder.tvCount.setText("");
        }
        holder.itemView.setOnClickListener(v -> onSelect.select(p));
        holder.btnDelete.setOnClickListener(v -> onDelete.delete(p));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvUrl, tvCount;
        ImageButton btnDelete;
        VH(View v) {
            super(v);
            tvName = v.findViewById(R.id.tvProviderName);
            tvUrl = v.findViewById(R.id.tvProviderUrl);
            tvCount = v.findViewById(R.id.tvProviderCount);
            btnDelete = v.findViewById(R.id.btnDeleteProvider);
        }
    }
}
