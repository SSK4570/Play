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
import java.util.Map;

public class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.VH> {

    public interface OnGroupClick { void onClick(String group); }

    private List<String> items;
    private Map<String, Integer> countMap;
    private final OnGroupClick listener;

    public GroupAdapter(List<String> items, Map<String, Integer> countMap, OnGroupClick listener) {
        this.items = items;
        this.countMap = countMap;
        this.listener = listener;
    }

    public void update(List<String> newItems, Map<String, Integer> newCountMap) {
        this.items = newItems;
        this.countMap = newCountMap;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        String group = items.get(position);
        h.tvName.setText(group);

        // Channel count
        int count = (countMap != null && countMap.containsKey(group)) ? countMap.get(group) : 0;
        h.tvCount.setText(String.valueOf(count));

        // Icon by group name keyword
        int iconRes = iconForGroup(group);
        h.ivIcon.setImageResource(iconRes);
        h.ivIcon.setColorFilter(h.itemView.getContext().getColor(R.color.iconTint));

        h.itemView.setOnClickListener(v -> listener.onClick(group));
    }

    private int iconForGroup(String group) {
        if (group == null) return R.drawable.ic_tv;
        String g = group.toLowerCase();
        if (g.contains("recent") || g.contains("history"))  return R.drawable.ic_history;
        if (g.contains("news"))                              return R.drawable.ic_search;
        if (g.contains("sport") || g.contains("cricket") ||
            g.contains("football") || g.contains("fancode")) return R.drawable.ic_play;
        if (g.contains("movie") || g.contains("film") ||
            g.contains("cinema"))                            return R.drawable.ic_play;
        if (g.contains("kids") || g.contains("child"))      return R.drawable.ic_play;
        if (g.contains("music"))                             return R.drawable.ic_play;
        if (g.contains("entertain"))                         return R.drawable.ic_tv;
        if (g.contains("info"))                              return R.drawable.ic_tv;
        if (g.contains("english"))                           return R.drawable.ic_tv;
        if (g.contains("devotion") || g.contains("religion")) return R.drawable.ic_tv;
        return R.drawable.ic_tv;
    }

    @Override
    public int getItemCount() { return items == null ? 0 : items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName, tvCount;
        VH(View v) {
            super(v);
            ivIcon   = v.findViewById(R.id.ivGroupIcon);
            tvName   = v.findViewById(R.id.tvGroupName);
            tvCount  = v.findViewById(R.id.tvChannelCount);
        }
    }
}
