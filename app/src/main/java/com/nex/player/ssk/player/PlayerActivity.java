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
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback;
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

    private ExoPlayer  player;
    private PlayerView playerView;
    private ProgressBar pbBuffering, epgProgress;
    private View controlsOverlay, errorOverlay;
    private TextView tvError, tvChannelNamePlayer, tvBitrate, tvHdBadge;
    private TextView tvChannelNameEpg, tvHdBadgeEpg, tvBitrateEpg;
    private TextView tvEpgTitlePlayer, tvCurrentTime;

    private Channel channel;
    private boolean controlsVisible = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideControls = this::hideControlsOverlay;
    private final Runnable updateTime   = new Runnable() {
        @Override public void run() {
            updateCurrentTime();
            handler.postDelayed(this, 30_000);
        }
    };

    // ── Lifecycle ───────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        setContentView(R.layout.activity_player);

        channel = (Channel) getIntent().getSerializableExtra(EXTRA_CHANNEL);
        if (channel == null) { finish(); return; }

        initViews();
        setupChannel();
        initPlayer();
        handler.post(updateTime);
    }

    @Override protected void onPause()   { super.onPause();   if (player != null && !isPip()) player.pause(); }
    @Override protected void onStop()    { super.onStop();    if (player != null && !isPip()) player.stop();  }
    @Override protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (player != null) { player.release(); player = null; }
    }

    private boolean isPip() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode();
    }

    // ── Init ────────────────────────────────────────────────────────────────

    private void initViews() {
        playerView           = findViewById(R.id.playerView);
        pbBuffering          = findViewById(R.id.pbBuffering);
        controlsOverlay      = findViewById(R.id.controlsOverlay);
        errorOverlay         = findViewById(R.id.errorOverlay);
        tvError              = findViewById(R.id.tvError);
        tvChannelNamePlayer  = findViewById(R.id.tvChannelNamePlayer);
        tvBitrate            = findViewById(R.id.tvBitrate);
        tvHdBadge            = findViewById(R.id.tvHdBadge);
        tvChannelNameEpg     = findViewById(R.id.tvChannelNameEpg);
        tvHdBadgeEpg         = findViewById(R.id.tvHdBadgeEpg);
        tvBitrateEpg         = findViewById(R.id.tvBitrateEpg);
        tvEpgTitlePlayer     = findViewById(R.id.tvEpgTitlePlayer);
        tvCurrentTime        = findViewById(R.id.tvCurrentTime);
        epgProgress          = findViewById(R.id.epgProgress);

        playerView.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP) toggleControls();
            return true;
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.btnPip).setOnClickListener(v -> enterPip());
        findViewById(R.id.btnAspect).setOnClickListener(v -> cycleAspectRatio());
        findViewById(R.id.btnMore).setOnClickListener(v -> showStreamInfo());
        findViewById(R.id.btnRetry).setOnClickListener(v -> retryPlay());
    }

    private void setupChannel() {
        String name = channel.getName() != null ? channel.getName() : "";
        tvChannelNamePlayer.setText(name);
        tvChannelNameEpg.setText(name);
        tvHdBadge.setVisibility(channel.isHD() ? View.VISIBLE : View.GONE);
        tvHdBadgeEpg.setVisibility(channel.isHD() ? View.VISIBLE : View.GONE);
        tvEpgTitlePlayer.setText(channel.hasEpg() ? channel.getEpgTitle() : getString(R.string.no_epg));
        int prog = channel.getEpgProgress();
        if (epgProgress != null && prog >= 0) {
            epgProgress.setProgress(prog);
            epgProgress.setVisibility(View.VISIBLE);
        }
    }

    // ── Player ──────────────────────────────────────────────────────────────

    private void initPlayer() {
        // Build HTTP headers
        Map<String, String> headers = buildHeaders();

        // HTTP datasource factory with all custom headers
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)
                .setAllowCrossProtocolRedirects(true);

        if (channel.getUserAgent() != null && !channel.getUserAgent().isEmpty()) {
            httpFactory.setUserAgent(channel.getUserAgent());
        }
        if (!headers.isEmpty()) {
            httpFactory.setDefaultRequestProperties(headers);
        }

        // Faster load control: shorter buffers for live TV
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        3_000,   // min buffer before playback starts
                        15_000,  // max buffer
                        1_500,   // buffer after rebuffer
                        2_000)   // buffer before playback resumes
                .build();

        DefaultTrackSelector trackSelector =
                new DefaultTrackSelector(this, new AdaptiveTrackSelection.Factory());

        ExoPlayer.Builder builder = new ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl);

        // Widevine DRM
        String drmUrl  = channel.getDrmLicenseUrl();
        String drmKeys = channel.getDrmKeys();

        if (drmUrl != null && !drmUrl.isEmpty()) {
            HttpMediaDrmCallback drmCallback = new HttpMediaDrmCallback(drmUrl, httpFactory);
            DefaultDrmSessionManager drmManager = new DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .build(drmCallback);
            builder.setMediaSourceFactory(
                    new DefaultMediaSourceFactory(this)
                            .setDataSourceFactory(httpFactory)
                            .setDrmSessionManagerProvider(mp -> drmManager));
        } else {
            builder.setMediaSourceFactory(
                    new DefaultMediaSourceFactory(this).setDataSourceFactory(httpFactory));
        }

        player = builder.build();
        playerView.setPlayer(player);
        playerView.setUseController(false); // use our own controls overlay

        // Build MediaItem
        MediaItem.Builder miBuilder = new MediaItem.Builder().setUri(Uri.parse(channel.getUrl()));

        if (drmKeys != null && !drmKeys.isEmpty() && !drmKeys.startsWith("http")) {
            // ClearKey: "KEY:IV" or "KEY:IV,KEY2:IV2"
            miBuilder.setDrmConfiguration(
                    new MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                            .setLicenseUri(buildClearKeyUri(drmKeys))
                            .build());
        } else if (drmUrl != null && !drmUrl.isEmpty()) {
            miBuilder.setDrmConfiguration(
                    new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                            .setLicenseUri(drmUrl)
                            .build());
        }

        player.setMediaItem(miBuilder.build());
        player.prepare();
        player.setPlayWhenReady(true);

        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                pbBuffering.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                if (state == Player.STATE_READY) updateBitrateInfo();
            }
            @Override public void onPlayerError(PlaybackException error) {
                pbBuffering.setVisibility(View.GONE);
                tvError.setText("Error: " + error.getMessage());
                errorOverlay.setVisibility(View.VISIBLE);
            }
        });
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> h = new HashMap<>();
        if (notEmpty(channel.getUserAgent())) h.put("User-Agent", channel.getUserAgent());
        if (notEmpty(channel.getReferer()))   h.put("Referer",    channel.getReferer());
        if (notEmpty(channel.getOrigin()))    h.put("Origin",     channel.getOrigin());
        if (notEmpty(channel.getCookie()))    h.put("Cookie",     channel.getCookie());
        return h;
    }

    private static boolean notEmpty(String s) { return s != null && !s.isEmpty(); }

    /** Build a ClearKey JSON URI from "KEY:IV" or "KEY:IV,K2:V2" */
    private static Uri buildClearKeyUri(String keys) {
        StringBuilder sb = new StringBuilder("{\"keys\":[");
        String[] pairs = keys.split(",");
        for (int i = 0; i < pairs.length; i++) {
            String[] kv = pairs[i].trim().split(":");
            if (kv.length == 2) {
                if (i > 0) sb.append(",");
                sb.append("{\"kty\":\"oct\",\"k\":\"")
                  .append(kv[1].trim())
                  .append("\",\"kid\":\"")
                  .append(kv[0].trim())
                  .append("\"}");
            }
        }
        sb.append("],\"type\":\"temporary\"}");
        return Uri.parse("data:application/json;base64,"
                + android.util.Base64.encodeToString(
                        sb.toString().getBytes(), android.util.Base64.NO_WRAP));
    }

    private void retryPlay() {
        errorOverlay.setVisibility(View.GONE);
        if (player != null) { player.prepare(); player.play(); }
    }

    // ── Controls ────────────────────────────────────────────────────────────

    private void toggleControls() {
        if (controlsVisible) hideControlsOverlay(); else showControlsOverlay();
    }

    private void showControlsOverlay() {
        controlsVisible = true;
        controlsOverlay.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideControls);
        handler.postDelayed(hideControls, 4_000);
    }

    private void hideControlsOverlay() {
        controlsVisible = false;
        controlsOverlay.setVisibility(View.GONE);
    }

    private void updateBitrateInfo() {
        if (player == null) return;
        long bps = player.getVideoFormat() != null ? player.getVideoFormat().bitrate : -1;
        if (bps > 0) {
            String br = bps >= 1_000_000
                    ? String.format(Locale.US, "%.1f Mbps", bps / 1_000_000.0)
                    : String.format(Locale.US, "%d Kbps", bps / 1000);
            tvBitrate.setText(br);
            tvBitrateEpg.setText(br);
        }
    }

    private void updateCurrentTime() {
        tvCurrentTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
    }

    private void cycleAspectRatio() {
        int mode = playerView.getResizeMode();
        if (mode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT)
            playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL);
        else if (mode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL)
            playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        else
            playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(
                    new PictureInPictureParams.Builder().setAspectRatio(new Rational(16, 9)).build());
        }
    }

    private void showStreamInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("URL: ").append(channel.getUrl()).append("\n\n");
        if (notEmpty(channel.getUserAgent())) sb.append("User-Agent: ").append(channel.getUserAgent()).append("\n");
        if (notEmpty(channel.getReferer()))   sb.append("Referer: ").append(channel.getReferer()).append("\n");
        if (notEmpty(channel.getOrigin()))    sb.append("Origin: ").append(channel.getOrigin()).append("\n");
        if (notEmpty(channel.getCookie()))    sb.append("Cookie: ").append(channel.getCookie()).append("\n");
        if (notEmpty(channel.getDrmLicenseUrl())) sb.append("DRM URL: ").append(channel.getDrmLicenseUrl()).append("\n");
        if (notEmpty(channel.getDrmKeys()))   sb.append("DRM Keys: ").append(channel.getDrmKeys()).append("\n");

        new androidx.appcompat.app.AlertDialog.Builder(this, R.style.NexDialog)
                .setTitle(channel.getName())
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip) {
        super.onPictureInPictureModeChanged(inPip);
        controlsOverlay.setVisibility(inPip ? View.GONE : View.VISIBLE);
    }
}
