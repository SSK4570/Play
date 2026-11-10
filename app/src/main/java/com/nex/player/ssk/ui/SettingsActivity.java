package com.nex.player.ssk.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nex.player.ssk.R;
import com.nex.player.ssk.provider.AddProviderActivity;
import com.nex.player.ssk.provider.ChannelCache;
import com.nex.player.ssk.utils.AppPrefs;
import com.nex.player.ssk.utils.Provider;

import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Simple settings layout
        setContentView(R.layout.activity_settings);

        setupProviderList();
        findViewById(R.id.btnAddProvider).setOnClickListener(v ->
                startActivityForResult(new Intent(this, AddProviderActivity.class), 1));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupProviderList();
    }

    private void setupProviderList() {
        List<Provider> providers = AppPrefs.get(this).getProviders();
        RecyclerView rv = findViewById(R.id.rvProviders);
        if (rv == null) return;
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new ProviderListAdapter(providers,
                provider -> {
                    AppPrefs.get(this).setActiveProviderId(provider.getId());
                    Toast.makeText(this, "Active: " + provider.getName(), Toast.LENGTH_SHORT).show();
                },
                provider -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Delete Provider")
                            .setMessage("Delete \"" + provider.getName() + "\"?")
                            .setPositiveButton("Delete", (d, w) -> {
                                ChannelCache.clear(this, provider.getId());
                                AppPrefs.get(this).removeProvider(provider.getId());
                                setupProviderList();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
        ));

        TextView tvEmpty = findViewById(R.id.tvNoProviders);
        if (providers.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rv.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rv.setVisibility(View.VISIBLE);
        }
    }
}
