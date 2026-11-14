package com.nex.player.ssk.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
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
    public void onBindViewHolder(@NonNull VH h, int position) {
        Channel ch = items.get(position);

        h.tvName.setText(ch.getName());

        // Logo
        if (ch.getLogoUrl() != null && !ch.getLogoUrl().isEmpty()) {
            h.ivLogo.clearColorFilter();
            Glide.with(h.itemView.getContext())
                    .load(ch.getLogoUrl())
                    .placeholder(R.drawable.ic_tv)
                    .error(R.drawable.ic_tv)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(h.ivLogo);
        } else {
            h.ivLogo.setImageResource(R.drawable.ic_tv);
            h.ivLogo.setColorFilter(h.itemView.getContext().getColor(R.color.iconTint));
        }

        // EPG
        if (ch.hasEpg() && ch.getEpgTitle() != null && !ch.getEpgTitle().isEmpty()) {
            h.tvEpgTitle.setText(ch.getEpgTitle());
            h.tvEpgTitle.setVisibility(View.VISIBLE);

            // Time range
            if (ch.getEpgStart() != null && ch.getEpgEnd() != null) {
                h.tvEpgTime.setText(ch.getEpgStart() + " - " + ch.getEpgEnd());
                h.layoutNowTime.setVisibility(View.VISIBLE);
            } else {
                h.layoutNowTime.setVisibility(View.GONE);
            }

            // Progress
            int prog = ch.getEpgProgress();
            if (prog >= 0) {
                h.pbEpg.setProgress(prog);
                h.pbEpg.setVisibility(View.VISIBLE);
            } else {
                h.pbEpg.setVisibility(View.GONE);
            }

            // Next show
            if (ch.getEpgNextTitle() != null && !ch.getEpgNextTitle().isEmpty()) {
                h.tvEpgNext.setText("Next  " + ch.getEpgNextTime() + "  ·  " + ch.getEpgNextTitle());
                h.tvEpgNext.setVisibility(View.VISIBLE);
            } else {
                h.tvEpgNext.setVisibility(View.GONE);
            }

        } else {
            h.tvEpgTitle.setText(R.string.no_epg);
            h.tvEpgTitle.setVisibility(View.VISIBLE);
            h.layoutNowTime.setVisibility(View.GONE);
            h.pbEpg.setVisibility(View.GONE);
            h.tvEpgNext.setVisibility(View.GONE);
        }

        h.itemView.setOnClickListener(v -> listener.onClick(ch));
    }

    @Override
    public int getItemCount() { return items == null ? 0 : items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivLogo;
        TextView tvName, tvEpgTitle, tvEpgTime, tvEpgNext;
        View layoutNowTime;
        ProgressBar pbEpg;

        VH(View v) {
            super(v);
            ivLogo        = v.findViewById(R.id.ivChannelLogo);
            tvName        = v.findViewById(R.id.tvChannelName);
            tvEpgTitle    = v.findViewById(R.id.tvEpgTitle);
            tvEpgTime     = v.findViewById(R.id.tvEpgTime);
            tvEpgNext     = v.findViewById(R.id.tvEpgNext);
            layoutNowTime = v.findViewById(R.id.layoutNowTime);
            pbEpg         = v.findViewById(R.id.pbEpg);
        }
    }
}
