package com.nex.player.ssk.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.nex.player.ssk.R;
import com.nex.player.ssk.utils.Channel;

import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.VH> {

    public interface OnChannelClick { void onClick(Channel channel); }

    private List<Channel> items;
    private final OnChannelClick listener;

    public ChannelAdapter(List<Channel> items, OnChannelClick listener) {
        this.items = items;
        this.listener = listener;
    }

    public void update(List<Channel> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Channel ch = items.get(position);

        holder.tvName.setText(ch.getName());

        // EPG
        if (ch.hasEpg()) {
            holder.tvEpg.setText(ch.getEpgTitle());
            if (ch.getEpgStart() != null && ch.getEpgEnd() != null) {
                holder.tvTime.setText(ch.getEpgStart() + "-" + ch.getEpgEnd());
                holder.tvTime.setVisibility(View.VISIBLE);
            } else {
                holder.tvTime.setVisibility(View.GONE);
            }
        } else {
            holder.tvEpg.setText(R.string.no_epg);
            holder.tvTime.setVisibility(View.GONE);
        }

        // Logo
        if (ch.getLogoUrl() != null && !ch.getLogoUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(ch.getLogoUrl())
                    .placeholder(R.drawable.ic_play)
                    .error(R.drawable.ic_play)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(holder.ivLogo);
            holder.ivLogo.setColorFilter(null);
        } else {
            holder.ivLogo.setImageResource(R.drawable.ic_play);
            holder.ivLogo.setColorFilter(holder.itemView.getContext().getColor(R.color.iconTint));
        }

        holder.itemView.setOnClickListener(v -> listener.onClick(ch));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivLogo;
        TextView tvName, tvEpg, tvTime;
        VH(View v) {
            super(v);
            ivLogo = v.findViewById(R.id.ivChannelLogo);
            tvName = v.findViewById(R.id.tvChannelName);
            tvEpg = v.findViewById(R.id.tvEpgTitle);
            tvTime = v.findViewById(R.id.tvEpgTime);
        }
    }
}
