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
        this.items    = items;
        this.countMap = countMap;
        this.listener = listener;
    }

    public void update(List<String> newItems, Map<String, Integer> newCountMap) {
        this.items    = newItems;
        this.countMap = newCountMap;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_group, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        String group = items.get(position);
        h.tvName.setText(group);
        int count = (countMap != null && countMap.containsKey(group)) ? countMap.get(group) : 0;
        h.tvCount.setText(String.valueOf(count));
        h.ivIcon.setImageResource(iconForGroup(group));
        h.ivIcon.setColorFilter(h.itemView.getContext().getColor(R.color.iconTint));
        h.itemView.setOnClickListener(v -> listener.onClick(group));
    }

    /**
     * Returns the best matching icon drawable for a group name.
     * Covers common IPTV group titles in multiple languages.
     */
    private static int iconForGroup(String group) {
        if (group == null) return R.drawable.ic_tv;
        String g = group.toLowerCase();

        // ── Special built-in groups ──────────────────────────────────────────
        if (g.equals("recent") || g.contains("history"))  return R.drawable.ic_history;
        if (g.equals("all channels") || g.equals("all"))  return R.drawable.ic_all_channels;

        // ── Sports ───────────────────────────────────────────────────────────
        if (g.contains("sport") || g.contains("cricket") || g.contains("football")
                || g.contains("soccer") || g.contains("tennis") || g.contains("golf")
                || g.contains("racing") || g.contains("f1") || g.contains("nba")
                || g.contains("nfl") || g.contains("fancode") || g.contains("espn")
                || g.contains("star sport") || g.contains("willow"))
            return R.drawable.ic_sports;

        // ── Movies / Films ───────────────────────────────────────────────────
        if (g.contains("movie") || g.contains("film") || g.contains("cinema")
                || g.contains("flick") || g.contains("flix") || g.contains("zee cinema")
                || g.contains("star gold") || g.contains("b4u"))
            return R.drawable.ic_movie;

        // ── Music ────────────────────────────────────────────────────────────
        if (g.contains("music") || g.contains("mtv") || g.contains("vh1")
                || g.contains("9xm") || g.contains("zing"))
            return R.drawable.ic_music;

        // ── Kids / Children ───────────────────────────────────────────────────
        if (g.contains("kids") || g.contains("child") || g.contains("cartoon")
                || g.contains("disney") || g.contains("nickelodeon") || g.contains("nick")
                || g.contains("pogo") || g.contains("baby") || g.contains("junior"))
            return R.drawable.ic_kids;

        // ── News ─────────────────────────────────────────────────────────────
        if (g.contains("news") || g.contains("live") && g.contains("24")
                || g.contains("cnbc") || g.contains("ndtv") || g.contains("aaj tak")
                || g.contains("times now") || g.contains("republic") || g.contains("wion"))
            return R.drawable.ic_news;

        // ── Devotional / Religious ────────────────────────────────────────────
        if (g.contains("devot") || g.contains("religion") || g.contains("spiritual")
                || g.contains("dharma") || g.contains("aastha") || g.contains("sanskar")
                || g.contains("ishwar") || g.contains("peace") || g.contains("god"))
            return R.drawable.ic_devotional;

        // ── Entertainment / Infotainment ─────────────────────────────────────
        if (g.contains("entertain") || g.contains("comedy") || g.contains("lifestyle")
                || g.contains("reality") || g.contains("drama"))
            return R.drawable.ic_entertainment;

        if (g.contains("info") || g.contains("discover") || g.contains("natgeo")
                || g.contains("animal") || g.contains("history") || g.contains("document"))
            return R.drawable.ic_info;

        // ── International / English ───────────────────────────────────────────
        if (g.contains("english") || g.contains("internation") || g.contains("world")
                || g.contains("global") || g.contains("bbc") || g.contains("cnn")
                || g.contains("fox") || g.contains("hbo") || g.contains("sky"))
            return R.drawable.ic_world;

        // ── Regional languages ────────────────────────────────────────────────
        if (g.contains("hindi") || g.contains("tamil") || g.contains("telugu")
                || g.contains("kannada") || g.contains("malayalam") || g.contains("punjabi")
                || g.contains("bengali") || g.contains("bangla") || g.contains("marathi")
                || g.contains("gujarati") || g.contains("assam") || g.contains("oriya")
                || g.contains("bhojpuri") || g.contains("urdu") || g.contains("sindhi")
                || g.contains("french") || g.contains("arabic") || g.contains("spanish")
                || g.contains("german") || g.contains("turkish"))
            return R.drawable.ic_regional;

        // ── Default fallback ──────────────────────────────────────────────────
        return R.drawable.ic_tv;
    }

    @Override public int getItemCount() { return items == null ? 0 : items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView  tvName, tvCount;
        VH(View v) {
            super(v);
            ivIcon  = v.findViewById(R.id.ivGroupIcon);
            tvName  = v.findViewById(R.id.tvGroupName);
            tvCount = v.findViewById(R.id.tvChannelCount);
        }
    }
}
