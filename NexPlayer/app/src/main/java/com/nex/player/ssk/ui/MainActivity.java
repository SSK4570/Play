package com.nex.player.ssk.ui;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.nex.player.ssk.R;
import com.nex.player.ssk.provider.AddProviderActivity;
import com.nex.player.ssk.utils.AppPrefs;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupToolbar();
        setupCenterClick();
        setupBottomButtons();
        updateCacheBadge();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCacheBadge();
    }

    private void setupToolbar() {
        findViewById(R.id.btnSearch).setOnClickListener(v -> openChannelList(true));
        findViewById(R.id.btnLiveStream).setOnClickListener(v -> handleLiveStream());
        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnPower).setOnClickListener(v -> finishAffinity());
    }

    private void setupCenterClick() {
        View center = findViewById(R.id.ivLiveStreamIcon);
        View label = findViewById(R.id.tvLiveStream);
        View.OnClickListener cl = v -> handleLiveStream();
        center.setOnClickListener(cl);
        label.setOnClickListener(cl);
    }

    private void setupBottomButtons() {
        findViewById(R.id.btnBottomSearch).setOnClickListener(v -> openChannelList(true));
        findViewById(R.id.btnBottomSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void handleLiveStream() {
        if (!AppPrefs.get(this).hasProviders()) {
            showNoProviderDialog();
        } else {
            openChannelList(false);
        }
    }

    private void openChannelList(boolean searchMode) {
        if (!AppPrefs.get(this).hasProviders()) {
            showNoProviderDialog();
            return;
        }
        Intent intent = new Intent(this, ChannelListActivity.class);
        intent.putExtra("search_mode", searchMode);
        startActivity(intent);
    }

    private void showNoProviderDialog() {
        Dialog dialog = new Dialog(this, R.style.NexDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_no_provider);
        dialog.getWindow().setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
        );
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        dialog.findViewById(R.id.btnConfigureProvider).setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(this, AddProviderActivity.class));
        });

        dialog.show();
    }

    private void updateCacheBadge() {
        TextView tvCache = findViewById(R.id.tvCacheLabel);
        if (AppPrefs.get(this).isFromCache()) {
            tvCache.setVisibility(View.VISIBLE);
        } else {
            tvCache.setVisibility(View.GONE);
        }
    }
}
