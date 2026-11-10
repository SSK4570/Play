# NEX PLAYER+

> A powerful Android IPTV player with M3U playlist support, EPG, DRM, and advanced stream controls.

**Package:** `com.nex.player.ssk`  
**Min SDK:** Android 5.0 (API 21)  
**Target SDK:** Android 14 (API 34)

---

## Features

### Playlist / Provider
- Add M3U/M3U8 playlists via **URL** (http/https)
- Add M3U/M3U8 playlists from **local file**
- Automatic channel parsing with group/category support
- EPG (Electronic Program Guide) URL support per playlist
- Channels cached locally for offline browsing

### Channel Browser
- Group/category list with channel counts
- Channel list with logos (via Glide), EPG title, and HD badge
- Search across all channels
- Recent channels history

### Player (ExoPlayer / Media3)
Supports all stream types:
| Format | Support |
|--------|---------|
| HLS (`.m3u8`) | ✅ |
| MPEG-DASH (`.mpd`) | ✅ |
| RTSP | ✅ |
| SmoothStreaming | ✅ |
| Direct MP4/TS | ✅ |

Advanced per-channel parameters parsed from M3U attributes:

| Parameter | M3U Attribute | Description |
|-----------|--------------|-------------|
| User-Agent | `user-agent="..."` | Custom HTTP User-Agent |
| Referer | `referrer="..."` | HTTP Referer header |
| Origin | `origin="..."` | HTTP Origin header |
| Cookie | `tvg-cookie="..."` | HTTP Cookie header |
| DRM License URL | `drm-license="..."` | Widevine license server |
| DRM ClearKey | set via keys `key:iv` | ClearKey DRM pairs |

Also supports `#EXTVLCOPT:http-user-agent=` and `#EXTVLCOPT:http-referrer=` VLC-style options.

### Player UI
- Black fullscreen landscape player
- Touch anywhere to show/hide controls
- Channel name + HD badge + bitrate at top
- EPG bar at bottom: channel name, current show title, time, EPG progress
- PiP (Picture-in-Picture) mode
- Aspect ratio cycling (Fit / Fill / Zoom)
- Stream info dialog
- Retry on error

### Theme
- Black & Red dark theme throughout
- Forest / cityscape background wallpapers on main screen
- Material design components

---

## Screenshots

| Main Screen | No Provider | Add Provider | Channel Groups |
|-------------|-------------|--------------|----------------|
| Live stream home | "IPTV provider required" dialog | Playlist / File options | Grouped channel list |

| Channel List | Player |
|--------------|--------|
| Channels with EPG | Fullscreen player with EPG bar |

---

## Building

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK with API 34

### Clone & Build
```bash
git clone https://github.com/YOUR_USERNAME/NexPlayerPlus.git
cd NexPlayerPlus
./gradlew assembleDebug
```

The APK will be at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Build Unsigned Release APK (no keystore needed)
```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release-unsigned.apk`

> **Note:** The release APK is intentionally built without a keystore so it can be distributed directly from GitHub. To install it, enable "Install from unknown sources" in your Android settings.

---

## GitHub Actions CI

Every push to `main`/`master` automatically builds both Debug and Release APKs. Download them from the **Actions** tab → latest workflow run → Artifacts.

To create a release with APK attached:
```bash
git tag v1.0.0
git push origin v1.0.0
```

---

## Project Structure

```
app/src/main/java/com/nex/player/ssk/
├── ui/
│   ├── SplashActivity.java          # Launch screen
│   ├── MainActivity.java            # Home: Live stream button
│   ├── ChannelListActivity.java     # Group + channel browser
│   ├── ChannelAdapter.java          # Channel RecyclerView adapter
│   ├── GroupAdapter.java            # Group RecyclerView adapter
│   ├── ProviderListAdapter.java     # Provider list adapter
│   └── SettingsActivity.java        # App settings
├── provider/
│   ├── AddProviderActivity.java     # Add M3U URL or file
│   └── ChannelCache.java            # Channel JSON cache
├── player/
│   └── PlayerActivity.java          # ExoPlayer with DRM + EPG UI
└── utils/
    ├── Channel.java                 # Channel data model
    ├── Provider.java                # Provider data model
    ├── M3UParser.java               # M3U/M3U8 parser
    └── AppPrefs.java                # SharedPreferences helper
```

---

## M3U Attribute Reference

NEX PLAYER+ reads these attributes from your M3U playlist:

```
#EXTINF:-1 tvg-id="channel.id" tvg-name="Channel Name" tvg-logo="http://logo.url/img.png" group-title="Group" user-agent="Mozilla/5.0" referrer="http://site.com" origin="http://site.com" tvg-cookie="session=abc" drm-license="https://license.server/widevine",Channel Display Name
http://stream.url/live.m3u8
```

ClearKey DRM keys are set programmatically via channel extras.

---

## EPG Support

Add an XMLTV EPG URL when adding your playlist. The app fetches and parses EPG data to show:
- Current show title below channel name in the list
- EPG progress bar in the player
- Show time range in the channel list

---

## Disclaimer

This app does not provide any streams, channels, or content. You must supply your own legal IPTV playlist. The developers are not responsible for any content accessed through this app.

---

## License

MIT License — see [LICENSE](LICENSE) file.
