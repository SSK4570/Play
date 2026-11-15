package com.nex.player.ssk.provider;

import android.util.Log;
import android.util.Xml;

import com.nex.player.ssk.utils.Channel;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

/**
 * Parses XMLTV EPG data and injects now/next programme info into channels.
 *
 * Supports:
 *   - Plain .xml URLs
 *   - Gzipped .xml.gz URLs (auto-detected by Content-Encoding or file extension)
 *   - x-tvg-url attribute extracted from M3U header by M3UParser
 */
public class EpgParser {

    private static final String TAG = "EpgParser";
    // XMLTV date format: 20240115093000 +0530
    private static final SimpleDateFormat SDF_XMLTV =
            new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
    private static final SimpleDateFormat SDF_DISPLAY =
            new SimpleDateFormat("h:mm a", Locale.US);

    static {
        SDF_XMLTV.setTimeZone(TimeZone.getDefault());
    }

    /**
     * Fetch EPG from URL, parse, and apply to channels list in-place.
     * Call this from a background thread.
     */
    public static void applyEpg(String epgUrl, List<Channel> channels) {
        if (epgUrl == null || epgUrl.isEmpty() || channels == null || channels.isEmpty()) return;
        try {
            // Build tvg-id → channel lookup for fast matching
            Map<String, Channel> byTvgId   = new HashMap<>();
            Map<String, Channel> byTvgName = new HashMap<>();
            Map<String, Channel> byName    = new HashMap<>();
            for (Channel ch : channels) {
                if (ch.getTvgId() != null && !ch.getTvgId().isEmpty())
                    byTvgId.put(ch.getTvgId().toLowerCase(), ch);
                if (ch.getTvgName() != null && !ch.getTvgName().isEmpty())
                    byTvgName.put(norm(ch.getTvgName()), ch);
                if (ch.getName() != null)
                    byName.put(norm(ch.getName()), ch);
            }

            InputStream raw = openStream(epgUrl);
            if (raw == null) { Log.w(TAG, "Cannot open EPG stream: " + epgUrl); return; }

            long now = System.currentTimeMillis();
            parseXmlTv(raw, byTvgId, byTvgName, byName, now);
            raw.close();
            Log.d(TAG, "EPG applied to " + channels.size() + " channels");
        } catch (Exception e) {
            Log.e(TAG, "EPG parse error: " + e.getMessage());
        }
    }

    private static InputStream openStream(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            conn.setRequestProperty("Accept-Encoding", "gzip");
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            String enc = conn.getHeaderField("Content-Encoding");
            InputStream is = conn.getInputStream();
            // Auto-detect gzip: by header or by URL suffix
            if ("gzip".equalsIgnoreCase(enc) || urlStr.endsWith(".gz")) {
                return new GZIPInputStream(is);
            }
            return is;
        } catch (Exception e) {
            Log.e(TAG, "openStream: " + e.getMessage());
            return null;
        }
    }

    private static void parseXmlTv(InputStream is,
            Map<String, Channel> byTvgId,
            Map<String, Channel> byTvgName,
            Map<String, Channel> byName,
            long now) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(is, "UTF-8");

        // We only need <programme> elements that are current or next
        // Track per-channel: current (now) and next programme
        // Store as: channelId → { "nowStart","nowEnd","nowTitle","nextTime","nextTitle" }
        Map<String, String[]> progMap = new HashMap<>(); // id → [nowTitle,nowS,nowE,nextTitle,nextS]

        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event != XmlPullParser.START_TAG) continue;
            if (!"programme".equals(parser.getName())) continue;

            String chanId   = parser.getAttributeValue(null, "channel");
            String startStr = parser.getAttributeValue(null, "start");
            String stopStr  = parser.getAttributeValue(null, "stop");
            if (chanId == null || startStr == null) continue;

            long startMs = parseXmltvDate(startStr);
            long stopMs  = stopStr != null ? parseXmltvDate(stopStr) : startMs + 3_600_000;
            if (startMs <= 0) continue;

            // Read the title
            String title = null;
            int depth = 1;
            while (depth > 0) {
                int e2 = parser.next();
                if (e2 == XmlPullParser.START_TAG) {
                    depth++;
                    if ("title".equals(parser.getName()) && title == null) {
                        try { title = parser.nextText(); depth--; } catch (Exception ignored) {}
                    }
                } else if (e2 == XmlPullParser.END_TAG) {
                    depth--;
                }
            }
            if (title == null || title.isEmpty()) continue;

            String id = chanId.toLowerCase();
            String[] cur = progMap.get(id);

            if (startMs <= now && stopMs > now) {
                // This is the CURRENT programme
                if (cur == null) cur = new String[5];
                cur[0] = title;
                cur[1] = SDF_DISPLAY.format(new Date(startMs));
                cur[2] = SDF_DISPLAY.format(new Date(stopMs));
                cur[3] = cur != null ? cur[3] : null; // keep next if already set
                cur[4] = cur != null ? cur[4] : null;
                progMap.put(id, cur);
            } else if (startMs > now) {
                // This could be NEXT programme
                if (cur == null) { cur = new String[5]; progMap.put(id, cur); }
                // Only keep the soonest next
                if (cur[4] == null) {
                    cur[3] = title;
                    cur[4] = SDF_DISPLAY.format(new Date(startMs));
                    progMap.put(id, cur);
                }
            }
        }

        // Apply to channels
        for (Map.Entry<String, String[]> entry : progMap.entrySet()) {
            String id    = entry.getKey();
            String[] info = entry.getValue();
            if (info == null) continue;

            Channel ch = byTvgId.get(id);
            if (ch == null) ch = byTvgName.get(id);
            if (ch == null) ch = byName.get(id);
            if (ch == null) continue;

            if (info[0] != null) {
                ch.setEpgTitle(info[0]);
                ch.setEpgStart(info[1]);
                ch.setEpgEnd(info[2]);
                // Store ms for progress bar
                try {
                    if (info[1] != null) ch.setEpgStartMs(SDF_DISPLAY.parse(info[1]).getTime());
                    if (info[2] != null) ch.setEpgEndMs(SDF_DISPLAY.parse(info[2]).getTime());
                } catch (Exception ignored) {}
            }
            if (info[3] != null) {
                ch.setEpgNextTitle(info[3]);
                ch.setEpgNextTime(info[4]);
            }
        }
    }

    private static long parseXmltvDate(String s) {
        if (s == null || s.length() < 14) return 0;
        try {
            // Format: "20240115093000 +0530" or "20240115093000" (no TZ)
            String trimmed = s.trim();
            if (trimmed.length() == 14) trimmed += " +0000";
            return SDF_XMLTV.parse(trimmed).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    private static String norm(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
