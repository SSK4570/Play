package com.nex.player.ssk.ui;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nex.player.ssk.R;
import com.nex.player.ssk.provider.AddProviderActivity;
import com.nex.player.ssk.provider.ChannelCache;

import java.util.List;
import com.nex.player.ssk.utils.AppPrefs;
import com.nex.player.ssk.utils.Provider;


public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setupToolbar();
        setupProviderSection();
        setupVisualStyle();
        setupPlayerSection();
        setupEpgSection();
        setupAbout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupProviderSection();
    }

    private void setupToolbar() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    // ── Provider ────────────────────────────────────────────────────────────
    private void setupProviderSection() {
        RecyclerView rv = findViewById(R.id.rvProviders);
        TextView tvEmpty = findViewById(R.id.tvNoProviders);
        List<Provider> providers = AppPrefs.get(this).getProviders();

        if (providers.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rv.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rv.setVisibility(View.VISIBLE);
            rv.setLayoutManager(new LinearLayoutManager(this));
            long activeId = AppPrefs.get(this).getActiveProviderId();
            rv.setAdapter(new ProviderListAdapter(providers, activeId,
                    provider -> activateProvider(provider),
                    provider -> showProviderActions(provider)));
        }
        findViewById(R.id.btnAddProvider).setOnClickListener(v ->
                startActivityForResult(new Intent(this, AddProviderActivity.class), 1));
    }

    private void activateProvider(Provider provider) {
        AppPrefs.get(this).setActiveProviderId(provider.getId());
        // Clear cache so it reloads fresh
        ChannelCache.clear(this, provider.getId());
        Toast.makeText(this, "Activated: " + provider.getName(), Toast.LENGTH_SHORT).show();
        setupProviderSection();
    }

    private void showProviderActions(Provider provider) {
        // Build action dialog: title = provider name, options = Edit / Activate / Delete
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_provider_actions, null);
        Dialog dialog = new Dialog(this, R.style.NexDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(v);
        dialog.getWindow().setLayout(
                (int)(getResources().getDisplayMetrics().widthPixels * 0.9f),
                WindowManager.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        ((TextView) v.findViewById(R.id.tvProviderActionTitle)).setText(provider.getName());
        ((TextView) v.findViewById(R.id.tvProviderUrl)).setText(provider.getDisplayUrl());

        v.findViewById(R.id.btnBack).setOnClickListener(x -> dialog.dismiss());

        // Show "Active" label if already active
        long activeId = AppPrefs.get(this).getActiveProviderId();
        boolean isAlreadyActive = provider.getId() == activeId
                || (activeId == -1 && AppPrefs.get(this).getProviders().indexOf(provider) == 0);
        TextView tvActivateLabel = v.findViewById(R.id.tvActivateLabel);
        if (isAlreadyActive) {
            tvActivateLabel.setText("Active ✓");
            tvActivateLabel.setTextColor(0xFF00CC44);
        } else {
            tvActivateLabel.setText("Activate");
            tvActivateLabel.setTextColor(0xFFFFFFFF);
        }

        v.findViewById(R.id.btnEdit).setOnClickListener(x -> {
            dialog.dismiss();
            showEditProviderDialog(provider);
        });
        v.findViewById(R.id.btnActivate).setOnClickListener(x -> {
            dialog.dismiss();
            activateProvider(provider);
        });
        v.findViewById(R.id.btnDelete).setOnClickListener(x -> {
            dialog.dismiss();
            new AlertDialog.Builder(this, R.style.NexAlertDialog)
                    .setTitle("Delete")
                    .setMessage("Delete \"" + provider.getName() + "\"?")
                    .setPositiveButton("Delete", (d, w) -> {
                        ChannelCache.clear(this, provider.getId());
                        AppPrefs.get(this).removeProvider(provider.getId());
                        setupProviderSection();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        v.findViewById(R.id.btnReload).setOnClickListener(x -> {
            dialog.dismiss();
            ChannelCache.clear(this, provider.getId());
            Toast.makeText(this, "Cache cleared — will reload on next open", Toast.LENGTH_SHORT).show();
        });
        dialog.show();
    }

    private void showEditProviderDialog(Provider provider) {
        // Reuse add M3U dialog pre-filled with existing values
        android.app.Dialog dialog = new android.app.Dialog(this, R.style.NexDialog);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_m3u);
        dialog.getWindow().setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        android.widget.EditText etName = dialog.findViewById(R.id.etName);
        android.widget.EditText etUrl  = dialog.findViewById(R.id.etUrl);
        android.widget.EditText etEpg  = dialog.findViewById(R.id.etEpgUrl);

        etName.setText(provider.getName());
        etUrl.setText(provider.getUrl());
        etEpg.setText(provider.getEpgUrl() != null ? provider.getEpgUrl() : "");

        dialog.findViewById(R.id.btnDialogBack).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btnAdd).setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if (url.isEmpty()) { etUrl.setError("URL required"); return; }
            provider.setName(etName.getText().toString().trim().isEmpty()
                    ? url : etName.getText().toString().trim());
            provider.setUrl(url);
            provider.setEpgUrl(etEpg.getText().toString().trim());
            // Update in list
            List<com.nex.player.ssk.utils.Provider> providers = AppPrefs.get(this).getProviders();
            for (int i = 0; i < providers.size(); i++) {
                if (providers.get(i).getId() == provider.getId()) {
                    providers.set(i, provider); break;
                }
            }
            AppPrefs.get(this).saveProviders(providers);
            com.nex.player.ssk.provider.ChannelCache.clear(this, provider.getId());
            Toast.makeText(this, "Provider updated", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            setupProviderSection();
        });
        dialog.show();
    }

    // ── Visual Style ────────────────────────────────────────────────────────
    private void setupVisualStyle() {
        findViewById(R.id.rowVisualStyle).setOnClickListener(v -> showThemeDialog());
        updateThemeSubtitle();
    }

    private void updateThemeSubtitle() {
        TextView sub = findViewById(R.id.tvThemeSubtitle);
        switch (AppPrefs.get(this).getTheme()) {
            case AppPrefs.THEME_DARK_RED:    sub.setText("Dark & Red");    break;
            case AppPrefs.THEME_DARK_YELLOW: sub.setText("Dark & Yellow"); break;
            case AppPrefs.THEME_DARK_PURPLE: sub.setText("Dark & Purple"); break;
            case AppPrefs.THEME_DARK_BLUE:   sub.setText("Dark & Blue");   break;
        }
    }

    private void showThemeDialog() {
        String[] themes = {"Dark & Red", "Dark & Yellow", "Dark & Purple", "Dark & Blue"};
        String[] keys   = {AppPrefs.THEME_DARK_RED, AppPrefs.THEME_DARK_YELLOW,
                           AppPrefs.THEME_DARK_PURPLE, AppPrefs.THEME_DARK_BLUE};
        String current  = AppPrefs.get(this).getTheme();
        int checked = 0;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(current)) { checked = i; break; }

        new AlertDialog.Builder(this, R.style.NexAlertDialog)
                .setTitle("Visual Style")
                .setSingleChoiceItems(themes, checked, (d, which) -> {
                    AppPrefs.get(this).setTheme(keys[which]);
                    updateThemeSubtitle();
                    applyThemeAccent(keys[which]);
                    d.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyThemeAccent(String theme) {
        // Theme accent color is applied via AppPrefs at next launch
        Toast.makeText(this, "Theme saved — restart app to apply", Toast.LENGTH_SHORT).show();
    }

    // ── Player ──────────────────────────────────────────────────────────────
    private void setupPlayerSection() {
        // Quality row
        updateQualitySubtitle();
        findViewById(R.id.rowPlayerQuality).setOnClickListener(v -> showQualityDialog());

        // Auto reload
        Switch swReload = findViewById(R.id.swAutoReload);
        swReload.setChecked(AppPrefs.get(this).isAutoReload());
        swReload.setOnCheckedChangeListener((b, checked) ->
                AppPrefs.get(this).setAutoReload(checked));
    }

    private void updateQualitySubtitle() {
        TextView sub = findViewById(R.id.tvQualitySubtitle);
        switch (AppPrefs.get(this).getPlayerQuality()) {
            case AppPrefs.QUALITY_LOW:    sub.setText("Low");    break;
            case AppPrefs.QUALITY_MEDIUM: sub.setText("Medium"); break;
            case AppPrefs.QUALITY_HIGH:   sub.setText("High");   break;
        }
    }

    private void showQualityDialog() {
        String[] labels = {"Low", "Medium", "High"};
        String[] keys   = {AppPrefs.QUALITY_LOW, AppPrefs.QUALITY_MEDIUM, AppPrefs.QUALITY_HIGH};
        String current  = AppPrefs.get(this).getPlayerQuality();
        int checked = 2;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(current)) { checked = i; break; }

        new AlertDialog.Builder(this, R.style.NexAlertDialog)
                .setTitle("Stream Quality")
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    AppPrefs.get(this).setPlayerQuality(keys[which]);
                    updateQualitySubtitle();
                    d.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── EPG ─────────────────────────────────────────────────────────────────
    private void setupEpgSection() {
        Switch swEpg = findViewById(R.id.swEpgInPlayer);
        swEpg.setChecked(AppPrefs.get(this).isEpgInPlayer());
        swEpg.setOnCheckedChangeListener((b, checked) ->
                AppPrefs.get(this).setEpgInPlayer(checked));
    }

    // ── About ────────────────────────────────────────────────────────────────
    private void setupAbout() {
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            ((TextView) findViewById(R.id.tvVersion)).setText("v" + version);
        } catch (Exception e) {
            ((TextView) findViewById(R.id.tvVersion)).setText("v1.0.0");
        }
    }
}
