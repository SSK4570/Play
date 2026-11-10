package com.nex.player.ssk.provider;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.nex.player.ssk.R;
import com.nex.player.ssk.utils.AppPrefs;
import com.nex.player.ssk.utils.Channel;
import com.nex.player.ssk.utils.M3UParser;
import com.nex.player.ssk.utils.Provider;

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddProviderActivity extends AppCompatActivity {

    private static final int REQ_PICK_FILE = 101;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_provider);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Playlist URL option
        LinearLayout optUrl = findViewById(R.id.optPlaylistUrl);
        optUrl.setOnClickListener(v -> showAddM3uUrlDialog());

        // Playlist from file option
        LinearLayout optFile = findViewById(R.id.optPlaylistFile);
        optFile.setOnClickListener(v -> pickM3uFile());
    }

    private void showAddM3uUrlDialog() {
        Dialog dialog = new Dialog(this, R.style.NexDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_m3u);
        dialog.getWindow().setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
        );
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        EditText etName = dialog.findViewById(R.id.etName);
        EditText etUrl = dialog.findViewById(R.id.etUrl);
        EditText etEpg = dialog.findViewById(R.id.etEpgUrl);

        dialog.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        dialog.findViewById(R.id.btnAdd).setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if (TextUtils.isEmpty(url)) {
                etUrl.setError("URL is required");
                return;
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                etUrl.setError("Must be a valid HTTP/HTTPS URL");
                return;
            }

            String name = etName.getText().toString().trim();
            if (TextUtils.isEmpty(name)) name = extractNameFromUrl(url);

            String epgUrl = etEpg.getText().toString().trim();

            dialog.dismiss();

            Provider provider = new Provider(name, url, TextUtils.isEmpty(epgUrl) ? null : epgUrl, Provider.TYPE_M3U_URL);
            loadAndSaveProvider(provider);
        });

        dialog.show();
    }

    private void pickM3uFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "audio/x-mpegurl", "application/x-mpegurl",
                "application/vnd.apple.mpegurl", "*/*"
        });
        try {
            startActivityForResult(Intent.createChooser(intent, "Select M3U File"), REQ_PICK_FILE);
        } catch (Exception e) {
            Toast.makeText(this, "File picker not available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_FILE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                String name = getFileNameFromUri(uri);
                Provider provider = new Provider(name, null, null, Provider.TYPE_M3U_FILE);
                provider.setFilePath(uri.toString());
                loadAndSaveProviderFromUri(provider, uri);
            }
        }
    }

    private void loadAndSaveProvider(Provider provider) {
        // Show loading
        ProgressBar pb = new ProgressBar(this);
        Toast.makeText(this, "Loading playlist…", Toast.LENGTH_LONG).show();

        executor.execute(() -> {
            try {
                InputStream is = new URL(provider.getUrl()).openStream();
                List<Channel> channels = M3UParser.parse(is);
                provider.setChannelCount(channels.size());
                AppPrefs.get(AddProviderActivity.this).addProvider(provider);

                // Cache channels
                ChannelCache.save(AddProviderActivity.this, provider.getId(), channels);

                handler.post(() -> {
                    Toast.makeText(this, "Added: " + channels.size() + " channels", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            } catch (Exception e) {
                // Save anyway - try loading later
                AppPrefs.get(AddProviderActivity.this).addProvider(provider);
                handler.post(() -> {
                    Toast.makeText(this, "Saved. Couldn't load now: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK);
                    finish();
                });
            }
        });
    }

    private void loadAndSaveProviderFromUri(Provider provider, Uri uri) {
        Toast.makeText(this, "Loading file…", Toast.LENGTH_LONG).show();

        executor.execute(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                List<Channel> channels = M3UParser.parse(is);
                provider.setChannelCount(channels.size());
                AppPrefs.get(AddProviderActivity.this).addProvider(provider);
                ChannelCache.save(AddProviderActivity.this, provider.getId(), channels);

                handler.post(() -> {
                    Toast.makeText(this, "Added: " + channels.size() + " channels", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            } catch (Exception e) {
                AppPrefs.get(AddProviderActivity.this).addProvider(provider);
                handler.post(() -> {
                    Toast.makeText(this, "Error reading file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK);
                    finish();
                });
            }
        });
    }

    private String extractNameFromUrl(String url) {
        try {
            String path = new URL(url).getPath();
            String[] parts = path.split("/");
            String last = parts[parts.length - 1];
            if (last.contains(".")) last = last.substring(0, last.lastIndexOf('.'));
            return last.isEmpty() ? new URL(url).getHost() : last;
        } catch (Exception e) {
            return "My Playlist";
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null) return "Playlist";
        if (name.contains(".")) name = name.substring(0, name.lastIndexOf('.'));
        return name;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
