package com.nex.player.ssk.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nex.player.ssk.R;

import java.util.List;

public class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.VH> {

    public interface OnGroupClick { void onClick(String group); }

    private List<String> items;
    private final OnGroupClick listener;

    public GroupAdapter(List<String> items, OnGroupClick listener) {
        this.items = items;
        this.listener = listener;
    }

    public void update(List<String> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        String group = items.get(position);
        holder.tvName.setText(group);
        holder.itemView.setOnClickListener(v -> listener.onClick(group));

        // Show clock icon for Recent, play icon otherwise
        if ("Recent".equals(group)) {
            holder.ivIcon.setImageResource(R.drawable.ic_history);
            holder.ivIcon.setColorFilter(holder.itemView.getContext().getColor(R.color.iconTint));
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_play);
            holder.ivIcon.setColorFilter(holder.itemView.getContext().getColor(R.color.iconTint));
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        VH(View v) {
            super(v);
            ivIcon = v.findViewById(R.id.ivGroupIcon);
            tvName = v.findViewById(R.id.tvGroupName);
        }
    }
}
