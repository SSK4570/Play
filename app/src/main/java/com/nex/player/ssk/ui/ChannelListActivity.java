package com.nex.player.ssk.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nex.player.ssk.R;
import com.nex.player.ssk.player.PlayerActivity;
import com.nex.player.ssk.provider.ChannelCache;
import com.nex.player.ssk.utils.AppPrefs;
import com.nex.player.ssk.utils.Channel;
import com.nex.player.ssk.utils.M3UParser;
import com.nex.player.ssk.utils.Provider;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChannelListActivity extends AppCompatActivity {

    private static final String GROUP_ALL    = "All Channels";
    private static final String GROUP_RECENT = "Recent";

    private RecyclerView  rvGroups, rvChannels;
    private ProgressBar   progressBar;
    private View          tvCategoryHeader, emptyState, searchBar;
    private TextView      tvCategoryHeaderText;

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
        setContentView(R.layout.activity_channel_list);
        initViews();
        setupToolbar();
        setupSearch();
        if (getIntent().getBooleanExtra("search_mode", false)) openSearch();
        loadChannels();
    }

    private void initViews() {
        rvGroups             = findViewById(R.id.rvGroups);
        rvChannels           = findViewById(R.id.rvChannels);
        progressBar          = findViewById(R.id.progressBar);
        tvCategoryHeader     = findViewById(R.id.tvCategoryHeader);
        tvCategoryHeaderText = findViewById(R.id.tvCategoryHeaderText);
        emptyState           = findViewById(R.id.emptyState);
        searchBar            = findViewById(R.id.searchBar);

        rvGroups.setLayoutManager(new LinearLayoutManager(this));
        rvChannels.setLayoutManager(new LinearLayoutManager(this));

        groupAdapter = new GroupAdapter(new ArrayList<>(), new HashMap<>(), group -> {
            currentGroup = group;
            showChannelsForGroup(group);
        });
        rvGroups.setAdapter(groupAdapter);

        channelAdapter = new ChannelAdapter(new ArrayList<>(), this::openPlayer);
        rvChannels.setAdapter(channelAdapter);

        // Category header click = go back to group list
        tvCategoryHeader.setOnClickListener(v -> showGroupList());
    }

    private void setupToolbar() {
        findViewById(R.id.btnSearch).setOnClickListener(v -> openSearch());
        findViewById(R.id.btnLiveTab).setOnClickListener(v -> showGroupList());
        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnPower).setOnClickListener(v -> finishAffinity());
    }

    private void setupSearch() {
        EditText etSearch = findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) { filterChannels(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void openSearch() {
        searchBar.setVisibility(View.VISIBLE);
        rvGroups.setVisibility(View.GONE);
        rvChannels.setVisibility(View.VISIBLE);
        channelAdapter.update(allChannels);
        tvCategoryHeaderText.setText("Search");
        tvCategoryHeader.setVisibility(View.VISIBLE);
    }

    private void filterChannels(String query) {
        if (query.isEmpty()) { channelAdapter.update(allChannels); return; }
        List<Channel> filtered = new ArrayList<>();
        String q = query.toLowerCase();
        for (Channel ch : allChannels)
            if (ch.getName() != null && ch.getName().toLowerCase().contains(q))
                filtered.add(ch);
        channelAdapter.update(filtered);
        rvGroups.setVisibility(View.GONE);
        rvChannels.setVisibility(View.VISIBLE);
    }

    private void loadChannels() {
        Provider provider = AppPrefs.get(this).getActiveProvider();
        if (provider == null) { showEmpty(); return; }

        // Try cache first — fast path
        if (ChannelCache.hasCached(this, provider.getId())) {
            List<Channel> cached = ChannelCache.load(this, provider.getId());
            if (!cached.isEmpty()) {
                AppPrefs.get(this).setFromCache(true);
                displayChannels(cached);
                return;
            }
        }

        AppPrefs.get(this).setFromCache(false);
        progressBar.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            try {
                List<Channel> channels;
                if (provider.getType() == Provider.TYPE_M3U_FILE) {
                    InputStream is = getContentResolver().openInputStream(
                            android.net.Uri.parse(provider.getFilePath()));
                    channels = M3UParser.parse(is);
                } else {
                    // Use buffered connection with timeout
                    java.net.HttpURLConnection conn =
                            (java.net.HttpURLConnection) new URL(provider.getUrl()).openConnection();
                    conn.setConnectTimeout(15_000);
                    conn.setReadTimeout(30_000);
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestProperty("User-Agent",
                            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
                    channels = M3UParser.parse(conn.getInputStream());
                    conn.disconnect();
                }
                ChannelCache.save(this, provider.getId(), channels);
                final List<Channel> result = channels;
                handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    displayChannels(result);
                });
            } catch (Exception e) {
                handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    showEmpty();
                });
            }
        });
    }

    private void refreshChannels() {
        Provider provider = AppPrefs.get(this).getActiveProvider();
        if (provider != null) ChannelCache.clear(this, provider.getId());
        currentGroup = null;
        allChannels.clear();
        loadChannels();
    }

    private void displayChannels(List<Channel> channels) {
        allChannels     = channels;
        groupedChannels = M3UParser.groupChannels(channels);
        if (channels.isEmpty()) { showEmpty(); return; }
        emptyState.setVisibility(View.GONE);

        List<String> groups = new ArrayList<>();
        groups.add(GROUP_RECENT);
        groups.add(GROUP_ALL);
        groups.addAll(groupedChannels.keySet());

        Map<String, Integer> countMap = new HashMap<>();
        countMap.put(GROUP_RECENT, Math.min(20, channels.size()));
        countMap.put(GROUP_ALL,    channels.size());
        for (Map.Entry<String, List<Channel>> e : groupedChannels.entrySet())
            countMap.put(e.getKey(), e.getValue().size());

        groupAdapter.update(groups, countMap);
        rvGroups.setVisibility(View.VISIBLE);
        rvChannels.setVisibility(View.GONE);
        tvCategoryHeader.setVisibility(View.GONE);
    }

    private void showChannelsForGroup(String group) {
        List<Channel> channels;
        if (GROUP_RECENT.equals(group)) {
            List<String> recentNames = AppPrefs.get(this).getRecentChannelNames();
            List<Channel> recentList = new ArrayList<>();
            for (String name : recentNames)
                for (Channel ch : allChannels)
                    if (name.equals(ch.getName())) { recentList.add(ch); break; }
            channels = recentList.isEmpty()
                    ? allChannels.subList(0, Math.min(20, allChannels.size()))
                    : recentList;
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
    }

    private void showGroupList() {
        currentGroup = null;
        searchBar.setVisibility(View.GONE);
        rvChannels.setVisibility(View.GONE);
        rvGroups.setVisibility(View.VISIBLE);
        tvCategoryHeader.setVisibility(View.GONE);
        EditText et = findViewById(R.id.etSearch);
        if (et != null) et.setText("");
    }

    private void showEmpty() {
        emptyState.setVisibility(View.VISIBLE);
        rvGroups.setVisibility(View.GONE);
        rvChannels.setVisibility(View.GONE);
    }

    private void openPlayer(Channel channel) {
        AppPrefs.get(this).addRecentChannel(channel.getName());
        startActivity(new Intent(this, PlayerActivity.class)
                .putExtra(PlayerActivity.EXTRA_CHANNEL, channel));
    }

    @Override
    public void onBackPressed() {
        if (searchBar.getVisibility() == View.VISIBLE) {
            searchBar.setVisibility(View.GONE);
            showGroupList();
        } else if (currentGroup != null) {
            showGroupList();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
