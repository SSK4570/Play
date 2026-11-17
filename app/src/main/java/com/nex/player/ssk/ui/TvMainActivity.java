package com.nex.player.ssk.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nex.player.ssk.R;
import com.nex.player.ssk.player.PlayerActivity;
import com.nex.player.ssk.provider.ChannelCache;
import com.nex.player.ssk.provider.EpgParser;
import com.nex.player.ssk.utils.AppPrefs;
import com.nex.player.ssk.utils.Channel;
import com.nex.player.ssk.utils.M3UParser;
import com.nex.player.ssk.utils.Provider;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Android TV main activity.
 * Reuses same adapters and channel loading as ChannelListActivity,
 * but rendered full-screen without touch-only toolbar.
 * D-pad navigation is handled by RecyclerView focus traversal natively.
 */
public class TvMainActivity extends AppCompatActivity {

    private static final String GROUP_ALL    = "All Channels";
    private static final String GROUP_RECENT = "Recent";

    private RecyclerView  rvGroups, rvChannels;
    private ProgressBar   progressBar;
    private View          tvCategoryHeader;
    private TextView      tvCategoryHeaderText, tvAppTitle;

    private GroupAdapter   groupAdapter;
    private ChannelAdapter channelAdapter;

    private List<Channel>              allChannels     = new ArrayList<>();
    private Map<String, List<Channel>> groupedChannels = new LinkedHashMap<>();
    private String                     currentGroup    = null;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler         handler  = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_main);

        rvGroups             = findViewById(R.id.rvGroups);
        rvChannels           = findViewById(R.id.rvChannels);
        progressBar          = findViewById(R.id.progressBar);
        tvCategoryHeader     = findViewById(R.id.tvCategoryHeader);
        tvCategoryHeaderText = findViewById(R.id.tvCategoryHeaderText);
        tvAppTitle           = findViewById(R.id.tvAppTitle);
        tvCategoryHeader.setOnClickListener(v -> showGroupList());

        rvGroups.setLayoutManager(new LinearLayoutManager(this));
        rvChannels.setLayoutManager(new LinearLayoutManager(this));

        groupAdapter = new GroupAdapter(new ArrayList<>(), new HashMap<>(), group -> {
            currentGroup = group;
            showChannelsForGroup(group);
        });
        rvGroups.setAdapter(groupAdapter);

        channelAdapter = new ChannelAdapter(new ArrayList<>(), ch -> {
            AppPrefs.get(this).addRecentChannel(ch.getName());
            startActivity(new Intent(this, PlayerActivity.class)
                    .putExtra(PlayerActivity.EXTRA_CHANNEL, ch));
        });
        rvChannels.setAdapter(channelAdapter);

        loadChannels();
    }

    private void loadChannels() {
        Provider provider = AppPrefs.get(this).getActiveProvider();
        if (provider == null) {
            Toast.makeText(this, "No provider set. Add one in Settings.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        if (ChannelCache.hasCached(this, provider.getId())) {
            List<Channel> cached = ChannelCache.load(this, provider.getId());
            if (!cached.isEmpty()) { displayChannels(cached); return; }
        }
        progressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                List<Channel> channels;
                if (provider.getType() == Provider.TYPE_M3U_FILE) {
                    InputStream is = getContentResolver().openInputStream(
                            android.net.Uri.parse(provider.getFilePath()));
                    channels = M3UParser.parse(is);
                } else {
                    HttpURLConnection conn = (HttpURLConnection) new URL(provider.getUrl()).openConnection();
                    conn.setConnectTimeout(15_000);
                    conn.setReadTimeout(30_000);
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10)");
                    channels = M3UParser.parse(conn.getInputStream());
                    conn.disconnect();
                }
                ChannelCache.save(this, provider.getId(), channels);
                final List<Channel> result = channels;
                handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    displayChannels(result);
                });
                // EPG
                String epgUrl = provider.getEpgUrl();
                if (epgUrl == null || epgUrl.isEmpty()) epgUrl = M3UParser.getLastParsedEpgUrl();
                if (epgUrl != null && !epgUrl.isEmpty()) {
                    final String eu = epgUrl;
                    executor.execute(() -> {
                        EpgParser.applyEpg(eu, result);
                        handler.post(() -> { if (channelAdapter != null) channelAdapter.notifyDataSetChanged(); });
                    });
                }
            } catch (Exception e) {
                handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void displayChannels(List<Channel> channels) {
        allChannels     = channels;
        groupedChannels = M3UParser.groupChannels(channels);
        List<String> groups = new ArrayList<>();
        groups.add(GROUP_RECENT); groups.add(GROUP_ALL);
        groups.addAll(groupedChannels.keySet());
        Map<String, Integer> countMap = new HashMap<>();
        countMap.put(GROUP_RECENT, Math.min(20, channels.size()));
        countMap.put(GROUP_ALL,    channels.size());
        for (Map.Entry<String, List<Channel>> e : groupedChannels.entrySet())
            countMap.put(e.getKey(), e.getValue().size());
        groupAdapter.update(groups, countMap);
        rvGroups.setVisibility(View.VISIBLE);
        rvChannels.setVisibility(View.GONE);
    }

    private void showChannelsForGroup(String group) {
        List<Channel> channels;
        if (GROUP_RECENT.equals(group)) {
            List<String> names = AppPrefs.get(this).getRecentChannelNames();
            List<Channel> list = new ArrayList<>();
            for (String n : names) for (Channel c : allChannels) if (n.equals(c.getName())) { list.add(c); break; }
            channels = list.isEmpty() ? allChannels.subList(0, Math.min(20, allChannels.size())) : list;
        } else if (GROUP_ALL.equals(group)) {
            channels = allChannels;
        } else {
            channels = groupedChannels.getOrDefault(group, new ArrayList<>());
        }
        channelAdapter.update(channels);
        rvGroups.setVisibility(View.GONE);
        rvChannels.setVisibility(View.VISIBLE);
        tvCategoryHeaderText.setText(".. / " + group);
        tvCategoryHeader.setVisibility(View.VISIBLE);
        // Focus first channel for D-pad
        handler.postDelayed(() -> {
            RecyclerView.ViewHolder vh = rvChannels.findViewHolderForAdapterPosition(0);
            if (vh != null) vh.itemView.requestFocus();
        }, 100);
    }

    private void showGroupList() {
        currentGroup = null;
        rvChannels.setVisibility(View.GONE);
        rvGroups.setVisibility(View.VISIBLE);
        tvCategoryHeader.setVisibility(View.GONE);
        handler.postDelayed(() -> {
            RecyclerView.ViewHolder vh = rvGroups.findViewHolderForAdapterPosition(0);
            if (vh != null) vh.itemView.requestFocus();
        }, 100);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
            if (currentGroup != null) { showGroupList(); return true; }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() { super.onDestroy(); executor.shutdown(); }
}
