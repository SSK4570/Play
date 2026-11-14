package com.nex.player.ssk.player;

import android.app.PictureInPictureParams;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.PlayerView;

import com.nex.player.ssk.R;
import com.nex.player.ssk.utils.Channel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_CHANNEL = "channel";

    private ExoPlayer player;
    private PlayerView playerView;
    private ProgressBar pbBuffering;
    private View controlsOverlay, errorOverlay;
    private TextView tvError, tvChannelNamePlayer, tvBitrate, tvHdBadge;
    private TextView tvChannelNameEpg, tvHdBadgeEpg, tvBitrateEpg, tvEpgTitlePlayer;
    private TextView tvCurrentTime, tvProviderBadge;
    private ProgressBar epgProgress;

    private Channel channel;
    private boolean controlsVisible = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideControls = () -> hideControlsOverlay();
    private final Runnable updateTime = new Runnable() {
        @Override public void run() {
            updateCurrentTime();
            handler.postDelayed(this, 30000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        setContentView(R.layout.activity_player);

        channel = (Channel) getIntent().getSerializableExtra(EXTRA_CHANNEL);
        if (channel == null) { finish(); return; }

        initViews();
        setupChannel();
        initPlayer();
        handler.post(updateTime);
    }

    private void initViews() {
        playerView = findViewById(R.id.playerView);
        pbBuffering = findViewById(R.id.pbBuffering);
        controlsOverlay = findViewById(R.id.controlsOverlay);
        errorOverlay = findViewById(R.id.errorOverlay);
        tvError = findViewById(R.id.tvError);
        tvChannelNamePlayer = findViewById(R.id.tvChannelNamePlayer);
        tvBitrate = findViewById(R.id.tvBitrate);
        tvHdBadge = findViewById(R.id.tvHdBadge);
        tvChannelNameEpg = findViewById(R.id.tvChannelNameEpg);
        tvHdBadgeEpg = findViewById(R.id.tvHdBadgeEpg);
        tvBitrateEpg = findViewById(R.id.tvBitrateEpg);
        tvEpgTitlePlayer = findViewById(R.id.tvEpgTitlePlayer);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvProviderBadge = findViewById(R.id.tvProviderBadge);
        epgProgress = findViewById(R.id.epgProgress);

        // Touch to show/hide controls
        playerView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) toggleControls();
            return true;
        });

        // Control buttons
        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.btnPip).setOnClickListener(v -> enterPip());
        findViewById(R.id.btnAspect).setOnClickListener(v -> cycleAspectRatio());
        findViewById(R.id.btnMore).setOnClickListener(v -> showStreamInfo());
        findViewById(R.id.btnRetry).setOnClickListener(v -> {
            errorOverlay.setVisibility(View.GONE);
            player.prepare();
            player.play();
        });
    }

    private void setupChannel() {
        String name = channel.getName();
        tvChannelNamePlayer.setText(name);
        tvChannelNameEpg.setText(name);

        if (channel.isHD()) {
            tvHdBadge.setVisibility(View.VISIBLE);
            tvHdBadgeEpg.setVisibility(View.VISIBLE);
        }

        if (channel.hasEpg()) {
            tvEpgTitlePlayer.setText(channel.getEpgTitle());
        } else {
            tvEpgTitlePlayer.setText(R.string.no_epg);
        }
    }

    private void initPlayer() {
        // HTTP data source with custom headers
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory();
        Map<String, String> headers = new HashMap<>();
        if (channel.getUserAgent() != null) {
            httpFactory.setUserAgent(channel.getUserAgent());
            headers.put("User-Agent", channel.getUserAgent());
        }
        if (channel.getReferer() != null) headers.put("Referer", channel.getReferer());
        if (channel.getOrigin() != null) headers.put("Origin", channel.getOrigin());
        if (channel.getCookie() != null) headers.put("Cookie", channel.getCookie());
        if (!headers.isEmpty()) httpFactory.setDefaultRequestProperties(headers);

        // Build ExoPlayer
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(this, new AdaptiveTrackSelection.Factory());
        ExoPlayer.Builder builder = new ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setLoadControl(new DefaultLoadControl());

        // DRM support
        String drmUrl = channel.getDrmLicenseUrl();
        String drmKeys = channel.getDrmKeys();

        if (drmUrl != null && !drmUrl.isEmpty()) {
            HttpMediaDrmCallback drmCallback = new HttpMediaDrmCallback(drmUrl, httpFactory);
            DefaultDrmSessionManager drmManager = new DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .build(drmCallback);
            builder.setMediaSourceFactory(
                    new DefaultMediaSourceFactory(this)
                            .setDataSourceFactory(httpFactory)
            );
        } else {
            builder.setMediaSourceFactory(
                    new DefaultMediaSourceFactory(this).setDataSourceFactory(httpFactory)
            );
        }

        player = builder.build();
        playerView.setPlayer(player);

        // Build MediaItem
        MediaItem.Builder miBuilder = new MediaItem.Builder()
                .setUri(Uri.parse(channel.getUrl()));

        // DRM clearkey (key:iv pairs like hjjk:hhh)
        if (drmKeys != null && !drmKeys.isEmpty()) {
            try {
                String[] pairs = drmKeys.split(",");
                MediaItem.DrmConfiguration.Builder drmConfig =
                        new MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID);
                for (String pair : pairs) {
                    String[] kv = pair.trim().split(":");
                    if (kv.length == 2) {
                        drmConfig.setKeySetId(pair.trim().getBytes());
                    }
                }
                miBuilder.setDrmConfiguration(drmConfig.build());
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (drmUrl != null && !drmUrl.isEmpty()) {
            miBuilder.setDrmConfiguration(
                    new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                            .setLicenseUri(drmUrl)
                            .build()
            );
        }

        player.setMediaItem(miBuilder.build());
        player.prepare();
        player.setPlayWhenReady(true);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    pbBuffering.setVisibility(View.VISIBLE);
                } else {
                    pbBuffering.setVisibility(View.GONE);
                }
                if (state == Player.STATE_READY) {
                    updateBitrateInfo();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                pbBuffering.setVisibility(View.GONE);
                tvError.setText(error.getMessage());
                errorOverlay.setVisibility(View.VISIBLE);
            }
        });
    }

    private void toggleControls() {
        if (controlsVisible) {
            hideControlsOverlay();
        } else {
            showControlsOverlay();
        }
    }

    private void showControlsOverlay() {
        controlsVisible = true;
        controlsOverlay.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideControls);
        handler.postDelayed(hideControls, 4000);
    }

    private void hideControlsOverlay() {
        controlsVisible = false;
        controlsOverlay.setVisibility(View.GONE);
    }

    private void updateBitrateInfo() {
        if (player == null) return;
        long bitrate = player.getVideoFormat() != null ? player.getVideoFormat().bitrate : -1;
        if (bitrate > 0) {
            String br = formatBitrate(bitrate);
            tvBitrate.setText(br);
            tvBitrateEpg.setText(br);
        }
    }

    private String formatBitrate(long bps) {
        if (bps >= 1_000_000) return String.format(Locale.US, "%.1f Mbps", bps / 1_000_000.0);
        if (bps >= 1_000) return String.format(Locale.US, "%d Kbps", bps / 1000);
        return bps + " bps";
    }

    private void updateCurrentTime() {
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        tvCurrentTime.setText(time);
    }

    private void cycleAspectRatio() {
        if (playerView.getResizeMode() == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) {
            playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL);
        } else if (playerView.getResizeMode() == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL) {
            playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        } else {
            playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
        }
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PictureInPictureParams.Builder pipBuilder = new PictureInPictureParams.Builder();
            pipBuilder.setAspectRatio(new Rational(16, 9));
            enterPictureInPictureMode(pipBuilder.build());
        }
    }

    private void showStreamInfo() {
        StringBuilder info = new StringBuilder();
        info.append("URL: ").append(channel.getUrl()).append("\n");
        if (channel.getUserAgent() != null) info.append("UA: ").append(channel.getUserAgent()).append("\n");
        if (channel.getReferer() != null) info.append("Referer: ").append(channel.getReferer()).append("\n");
        if (channel.getDrmLicenseUrl() != null) info.append("DRM: ").append(channel.getDrmLicenseUrl()).append("\n");

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Stream Info")
                .setMessage(info.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null && !isInPictureInPictureMode()) player.pause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null && !isInPictureInPictureMode()) player.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        if (isInPictureInPictureMode) {
            controlsOverlay.setVisibility(View.GONE);
        }
    }
}
