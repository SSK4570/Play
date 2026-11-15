package com.nex.player.ssk.player;

import android.app.PictureInPictureParams;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
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
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.nex.player.ssk.R;
import com.nex.player.ssk.utils.Channel;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {

    private static final String TAG = "PlayerActivity";
    public static final String EXTRA_CHANNEL = "channel";

    private ExoPlayer   player;
    private PlayerView  playerView;
    private ProgressBar pbBuffering, epgProgress;
    private View        controlsOverlay, errorOverlay;
    private TextView    tvError, tvChannelNamePlayer, tvBitrate, tvHdBadge;
    private TextView    tvChannelNameEpg, tvHdBadgeEpg, tvBitrateEpg;
    private TextView    tvEpgTitlePlayer, tvCurrentTime;

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

    // ── Views ───────────────────────────────────────────────────────────────

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

    // ── Player init ─────────────────────────────────────────────────────────

    private void initPlayer() {
        String url     = channel.getUrl();
        String ua      = channel.getUserAgent();
        String referer = channel.getReferer();
        String origin  = channel.getOrigin();
        String cookie  = channel.getCookie();
        String drmUrl  = channel.getDrmLicenseUrl();
        String drmKeys = channel.getDrmKeys();

        // If cookie is embedded in URL as a query param, strip it and move to header
        // e.g. https://host/live/index.mpd?__hdnea__=st=...~exp=...~acl=...
        // We keep the full URL intact (CDN auth token is part of the URL path/query)
        // and ALSO set it as Cookie header
        if (cookie == null || cookie.isEmpty()) {
            String extracted = extractTokenFromUrl(url);
            if (extracted != null) cookie = extracted;
        }

        // Build the HTTP headers map
        Map<String, String> headers = new HashMap<>();
        // User-Agent: set default if none provided
        String userAgent = (ua != null && !ua.isEmpty()) ? ua
                : "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
        headers.put("User-Agent", userAgent);
        if (notEmpty(referer)) headers.put("Referer", referer);
        if (notEmpty(origin))  headers.put("Origin",  origin);
        if (notEmpty(cookie))  headers.put("Cookie",  cookie);

        Log.d(TAG, "Playing URL: " + url);
        Log.d(TAG, "Headers: " + headers);
        if (notEmpty(drmKeys)) Log.d(TAG, "DRM Keys: " + drmKeys);
        if (notEmpty(drmUrl))  Log.d(TAG, "DRM URL: " + drmUrl);

        // ── DataSource factory with all headers ──────────────────────────────
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setDefaultRequestProperties(headers)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)
                .setAllowCrossProtocolRedirects(true);

        // Same factory for DRM license requests (needs same UA / Cookie)
        DefaultHttpDataSource.Factory drmHttpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setDefaultRequestProperties(headers)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)
                .setAllowCrossProtocolRedirects(true);

        // ── DRM detection ────────────────────────────────────────────────────
        boolean hasClearKey = notEmpty(drmKeys) && !drmKeys.startsWith("http");
        boolean hasWidevine = notEmpty(drmUrl)  || (notEmpty(drmKeys) && drmKeys.startsWith("http"));

        // If drmKeys is a URL (some playlists put Widevine URL in license_key)
        if (notEmpty(drmKeys) && drmKeys.startsWith("http")) {
            drmUrl  = drmKeys;
            drmKeys = null;
            hasClearKey = false;
            hasWidevine = true;
        }

        // ── Load control: fast start for live TV ─────────────────────────────
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(2_000, 15_000, 1_000, 2_000)
                .build();

        DefaultTrackSelector trackSelector =
                new DefaultTrackSelector(this, new AdaptiveTrackSelection.Factory());
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setMaxVideoSizeSd()   // start with SD, upgrade if bandwidth allows
                .build());

        ExoPlayer.Builder builder = new ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl);

        // ── Configure DRM session manager on builder ──────────────────────────
        if (hasClearKey) {
            // ClearKey: build inline JSON license as data: URI
            Uri ckLicenseUri = buildClearKeyDataUri(drmKeys);
            Log.d(TAG, "ClearKey URI: " + ckLicenseUri);
            // ExoPlayer ClearKey uses a local MediaDrm — no HTTP needed for license
            // But we still need a DrmSessionManager so the MediaSource handles encryption
            DefaultDrmSessionManager ckManager = new DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID,
                            FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .setMultiSession(false)
                    .build(new LocalClearKeyCallback(ckLicenseUri.toString()));
            builder.setMediaSourceFactory(new DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(httpFactory)
                    .setDrmSessionManagerProvider(unused -> ckManager));

        } else if (hasWidevine) {
            final String finalDrmUrl = drmUrl;
            HttpMediaDrmCallback wvCallback =
                    new HttpMediaDrmCallback(finalDrmUrl, drmHttpFactory);
            DefaultDrmSessionManager wvManager = new DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID,
                            FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .setMultiSession(false)
                    .build(wvCallback);
            builder.setMediaSourceFactory(new DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(httpFactory)
                    .setDrmSessionManagerProvider(unused -> wvManager));

        } else {
            builder.setMediaSourceFactory(new DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(httpFactory));
        }

        player = builder.build();
        playerView.setPlayer(player);
        playerView.setUseController(false);

        // ── MediaItem with DRM config ─────────────────────────────────────────
        MediaItem.Builder mi = new MediaItem.Builder().setUri(Uri.parse(url));

        if (hasClearKey) {
            mi.setDrmConfiguration(new MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                    .setLicenseUri(buildClearKeyDataUri(drmKeys))
                    .setMultiSession(false)
                    .build());
        } else if (hasWidevine) {
            mi.setDrmConfiguration(new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(drmUrl)
                    .build());
        }

        player.setMediaItem(mi.build());
        player.prepare();
        player.setPlayWhenReady(true);

        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                pbBuffering.setVisibility(state == Player.STATE_BUFFERING
                        ? View.VISIBLE : View.GONE);
                if (state == Player.STATE_READY) {
                    errorOverlay.setVisibility(View.GONE);
                    updateBitrateInfo();
                }
            }
            @Override public void onPlayerError(PlaybackException error) {
                pbBuffering.setVisibility(View.GONE);
                Log.e(TAG, "Player error: " + error.getMessage(), error);
                String msg = friendlyError(error);
                tvError.setText(msg);
                errorOverlay.setVisibility(View.VISIBLE);
            }
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Extract an auth token from the URL query string (hdnea, hdntl, token, etc.)
     * and return it as a cookie string "name=value".
     */
    private static String extractTokenFromUrl(String url) {
        if (url == null || !url.contains("?")) return null;
        String query = url.substring(url.indexOf('?') + 1);
        // Look for known CDN token param names
        for (String param : new String[]{"__hdnea__", "__hdntl__", "hdnea", "hdntl", "token", "auth"}) {
            int idx = query.indexOf(param + "=");
            if (idx >= 0) {
                String val = query.substring(idx);
                // Cut at next & if present
                int end = val.indexOf('&');
                return end >= 0 ? val.substring(0, end) : val;
            }
        }
        return null;
    }

    private static String friendlyError(PlaybackException e) {
        int code = e.errorCode;
        // Use numeric constants — compatible with all media3 versions
        // 2001 = ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        // 2002 = ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        // 2003 = ERROR_CODE_IO_BAD_HTTP_STATUS
        // 6001 = ERROR_CODE_DRM_SCHEME_UNSUPPORTED
        // 6002 = ERROR_CODE_DRM_PROVISIONING_FAILED
        // 6004 = ERROR_CODE_DRM_CONTENT_ERROR
        // 6005 = ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED
        // 6006 = ERROR_CODE_DRM_DISALLOWED_OPERATION
        // 6007 = ERROR_CODE_DRM_SYSTEM_ERROR
        // 6008 = ERROR_CODE_DRM_DEVICE_REVOKED
        // 6009 = ERROR_CODE_DRM_LICENSE_EXPIRED
        // 4001 = ERROR_CODE_PARSING_MANIFEST_MALFORMED
        if (code == 2001) return "Network connection failed.\nCheck your internet.";
        if (code == 2002) return "Connection timed out.\nServer may be offline.";
        if (code == 2003) return "Server error (HTTP). Stream may be expired.";
        if (code >= 6001 && code <= 6009) return "DRM error (code " + code + ").\nCheck keys or license URL.";
        if (code == 4001) return "Playlist/manifest is malformed or unsupported.";
        return "Playback error.\n" + e.getMessage();
    }

    private static boolean notEmpty(String s) { return s != null && !s.isEmpty(); }

    private Map<String, String> buildHeaders() {
        Map<String, String> h = new HashMap<>();
        if (notEmpty(channel.getUserAgent())) h.put("User-Agent", channel.getUserAgent());
        if (notEmpty(channel.getReferer()))   h.put("Referer",    channel.getReferer());
        if (notEmpty(channel.getOrigin()))    h.put("Origin",     channel.getOrigin());
        if (notEmpty(channel.getCookie()))    h.put("Cookie",     channel.getCookie());
        return h;
    }

    // ── ClearKey helpers ──────────────────────────────────────────────────────

    /**
     * Build ClearKey JSON as a data: URI for ExoPlayer.
     *
     * Input: "KID_hex:KEY_hex" or "KID1:KEY1,KID2:KEY2,..."
     * Output: data:application/json;base64,<ClearKey JSON base64>
     *
     * The JSON format (W3C ClearKey):
     *   {"keys":[{"kty":"oct","k":"<KEY_base64url>","kid":"<KID_base64url>"}],"type":"temporary"}
     */
    private static Uri buildClearKeyDataUri(String keys) {
        if (keys == null || keys.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("{\"keys\":[");
        boolean first = true;
        for (String pair : keys.split(",")) {
            pair = pair.trim();
            if (pair.isEmpty()) continue;
            int colon = pair.indexOf(':');
            if (colon < 1) continue;
            String kidHex = pair.substring(0, colon).trim();
            String keyHex = pair.substring(colon + 1).trim();
            String kidB64 = hexToBase64Url(kidHex);
            String keyB64 = hexToBase64Url(keyHex);
            if (kidB64 == null || keyB64 == null) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"kty\":\"oct\",\"k\":\"").append(keyB64)
              .append("\",\"kid\":\"").append(kidB64).append("\"}");
        }
        sb.append("],\"type\":\"temporary\"}");
        byte[] json = sb.toString().getBytes(StandardCharsets.UTF_8);
        return Uri.parse("data:application/json;base64,"
                + Base64.encodeToString(json, Base64.NO_WRAP));
    }

    private static String hexToBase64Url(String hex) {
        try {
            if (hex.length() % 2 != 0) hex = "0" + hex;
            byte[] bytes = new byte[hex.length() / 2];
            for (int i = 0; i < bytes.length; i++)
                bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        } catch (Exception e) {
            Log.e(TAG, "hexToBase64Url failed: " + hex);
            return null;
        }
    }

    /**
     * A MediaDrmCallback that serves a pre-built ClearKey JSON response locally,
     * without any network request. This avoids ExoPlayer trying to POST to a
     * data: URI and getting a network error.
     */
    private static class LocalClearKeyCallback implements androidx.media3.exoplayer.drm.MediaDrmCallback {
        private final byte[] licenseResponse;

        LocalClearKeyCallback(String dataUri) {
            // dataUri is "data:application/json;base64,<b64>" — decode the JSON bytes
            byte[] decoded;
            try {
                String b64 = dataUri.substring(dataUri.indexOf(',') + 1);
                decoded = Base64.decode(b64, Base64.NO_WRAP);
            } catch (Exception e) {
                decoded = "{}".getBytes(StandardCharsets.UTF_8);
            }
            licenseResponse = decoded;
        }

        @Override
        public byte[] executeProvisionRequest(java.util.UUID uuid,
                androidx.media3.exoplayer.drm.ExoMediaDrm.ProvisionRequest request) {
            return new byte[0];
        }

        @Override
        public byte[] executeKeyRequest(java.util.UUID uuid,
                androidx.media3.exoplayer.drm.ExoMediaDrm.KeyRequest request) {
            // Return the ClearKey JSON directly — no network needed
            return licenseResponse;
        }
    }

    // ── Controls ─────────────────────────────────────────────────────────────

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

    private void retryPlay() {
        errorOverlay.setVisibility(View.GONE);
        if (player != null) { player.prepare(); player.play(); }
    }

    private void updateBitrateInfo() {
        if (player == null) return;
        long bps = player.getVideoFormat() != null ? player.getVideoFormat().bitrate : -1;
        if (bps > 0) {
            String br = bps >= 1_000_000
                    ? String.format(Locale.US, "%.1f Mbps", bps / 1_000_000.0)
                    : String.format(Locale.US, "%d Kbps", bps / 1_000);
            tvBitrate.setText(br);
            if (tvBitrateEpg != null) tvBitrateEpg.setText(br);
        }
    }

    private void updateCurrentTime() {
        if (tvCurrentTime != null)
            tvCurrentTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
    }

    private void cycleAspectRatio() {
        int mode = playerView.getResizeMode();
        if      (mode == AspectRatioFrameLayout.RESIZE_MODE_FIT)  playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
        else if (mode == AspectRatioFrameLayout.RESIZE_MODE_FILL) playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        else                                                       playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            enterPictureInPictureMode(
                    new PictureInPictureParams.Builder().setAspectRatio(new Rational(16, 9)).build());
    }

    private void showStreamInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("URL:\n").append(channel.getUrl()).append("\n\n");
        if (notEmpty(channel.getUserAgent())) sb.append("User-Agent:\n").append(channel.getUserAgent()).append("\n\n");
        if (notEmpty(channel.getReferer()))   sb.append("Referer:\n").append(channel.getReferer()).append("\n\n");
        if (notEmpty(channel.getOrigin()))    sb.append("Origin:\n").append(channel.getOrigin()).append("\n\n");
        if (notEmpty(channel.getCookie()))    sb.append("Cookie:\n").append(channel.getCookie()).append("\n\n");
        if (notEmpty(channel.getDrmLicenseUrl())) sb.append("DRM License URL:\n").append(channel.getDrmLicenseUrl()).append("\n\n");
        if (notEmpty(channel.getDrmKeys()))   sb.append("DRM Keys:\n").append(channel.getDrmKeys());

        new androidx.appcompat.app.AlertDialog.Builder(this, R.style.NexDialog)
                .setTitle(channel.getName())
                .setMessage(sb.toString().trim())
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip) {
        super.onPictureInPictureModeChanged(inPip);
        if (controlsOverlay != null)
            controlsOverlay.setVisibility(inPip ? View.GONE : View.VISIBLE);
    }
}
