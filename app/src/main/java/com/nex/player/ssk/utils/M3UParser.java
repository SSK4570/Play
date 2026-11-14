package com.nex.player.ssk.utils;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class M3UParser {

    private static final String TAG = "M3UParser";

    // EXTINF attribute patterns
    private static final Pattern P_TVG_ID      = Pattern.compile("tvg-id=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TVG_NAME    = Pattern.compile("tvg-name=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TVG_LOGO    = Pattern.compile("tvg-logo=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_GROUP       = Pattern.compile("group-title=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_UA_ATTR     = Pattern.compile("user-agent=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_REF_ATTR    = Pattern.compile("referrer=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_ORIGIN_ATTR = Pattern.compile("origin=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_COOKIE_ATTR = Pattern.compile("(?:tvg-cookie|cookie)=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_DRM_ATTR    = Pattern.compile("(?:drm-license|license-url)=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_CHANNEL_NAME= Pattern.compile(",(.+)$");

    // VLC options
    private static final Pattern P_VLC_UA      = Pattern.compile("#EXTVLCOPT:http-user-agent=(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_VLC_REF     = Pattern.compile("#EXTVLCOPT:http-referrer=(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_VLC_COOKIE  = Pattern.compile("#EXTVLCOPT:http-cookie=(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_VLC_ORIGIN  = Pattern.compile("#EXTVLCOPT:http-origin=(.+)", Pattern.CASE_INSENSITIVE);

    // EXTHTTP JSON cookie: #EXTHTTP:{"cookie":"VALUE"}
    private static final Pattern P_EXTHTTP_COOKIE = Pattern.compile("#EXTHTTP:\\{\"cookie\":\"([^\"]+)\"\\}", Pattern.CASE_INSENSITIVE);

    // KODIPROP license key — two forms:
    //   #KODIPROP:inputstream.adaptive.license_key=KEY:IV   (clearkey hex pairs)
    //   #KODIPROP:inputstream.adaptive.license_key=https://...  (Widevine URL)
    private static final Pattern P_KODIPROP_LICENSE = Pattern.compile(
            "#KODIPROP:inputstream\\.adaptive\\.license_key=(.+)", Pattern.CASE_INSENSITIVE);
    // KODIPROP UA / cookies
    private static final Pattern P_KODIPROP_UA     = Pattern.compile(
            "#KODIPROP:inputstream\\.adaptive\\.user_agent=(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_KODIPROP_COOKIE = Pattern.compile(
            "#KODIPROP:inputstream\\.adaptive\\.stream_headers=.*[Cc]ookie=([^&\\s]+)", Pattern.CASE_INSENSITIVE);

    // URL trailing param detection (e.g. .m3u8?cookie=VALUE or ?hdnea=…)
    private static final Pattern P_URL_COOKIE_PARAM = Pattern.compile(
            "[?&]((?:cookie|hdnea|hdntl|__hdnea__|__hdntl__|token)[^\\s#&]*)", Pattern.CASE_INSENSITIVE);

    public static List<Channel> parse(InputStream inputStream) throws IOException {
        List<Channel> channels = new ArrayList<>();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, "UTF-8"), 65536);

        String line;
        Channel pending = null;
        // Pending per-channel values gathered from #EXT lines before the URL
        String pendingUA      = null;
        String pendingRef     = null;
        String pendingCookie  = null;
        String pendingOrigin  = null;
        String pendingDrmUrl  = null;
        String pendingDrmKeys = null;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("#EXTINF:")) {
                pending       = parseExtInf(line);
                pendingUA     = null;
                pendingRef    = null;
                pendingCookie = null;
                pendingOrigin = null;
                pendingDrmUrl = null;
                pendingDrmKeys= null;

            } else if (line.startsWith("#EXTVLCOPT:http-user-agent=")) {
                Matcher m = P_VLC_UA.matcher(line);
                if (m.find()) pendingUA = m.group(1).trim();

            } else if (line.startsWith("#EXTVLCOPT:http-referrer=")) {
                Matcher m = P_VLC_REF.matcher(line);
                if (m.find()) pendingRef = m.group(1).trim();

            } else if (line.startsWith("#EXTVLCOPT:http-cookie=")) {
                Matcher m = P_VLC_COOKIE.matcher(line);
                if (m.find()) pendingCookie = m.group(1).trim();

            } else if (line.startsWith("#EXTVLCOPT:http-origin=")) {
                Matcher m = P_VLC_ORIGIN.matcher(line);
                if (m.find()) pendingOrigin = m.group(1).trim();

            } else if (line.startsWith("#EXTHTTP:")) {
                // #EXTHTTP:{"cookie":"VALUE"}
                Matcher m = P_EXTHTTP_COOKIE.matcher(line);
                if (m.find()) pendingCookie = m.group(1).trim();

            } else if (line.startsWith("#KODIPROP:")) {
                Matcher m;
                // License key
                m = P_KODIPROP_LICENSE.matcher(line);
                if (m.find()) {
                    String val = m.group(1).trim();
                    if (val.startsWith("http://") || val.startsWith("https://")) {
                        pendingDrmUrl = val;
                    } else {
                        // Could be "KEY:IV" clearkey hex pair
                        pendingDrmKeys = val;
                    }
                }
                // UA
                m = P_KODIPROP_UA.matcher(line);
                if (m.find()) pendingUA = m.group(1).trim();
                // Cookie via stream_headers
                m = P_KODIPROP_COOKIE.matcher(line);
                if (m.find()) pendingCookie = m.group(1).trim();

            } else if (!line.startsWith("#")) {
                // This is the stream URL
                if (pending != null) {
                    String url = line;

                    // Extract cookie appended to URL: .m3u8?hdnea=... or ?cookie=...
                    String urlCookie = extractCookieFromUrl(url);
                    // Strip cookie from URL if needed (keep URL clean for ExoPlayer)
                    // Actually leave URL intact — ExoPlayer handles query params fine.

                    pending.setUrl(url);

                    // Apply inline EXTINF attrs first, then override with explicit EXT lines
                    if (pendingUA != null)      pending.setUserAgent(pendingUA);
                    if (pendingRef != null)      pending.setReferer(pendingRef);
                    if (pendingOrigin != null)   pending.setOrigin(pendingOrigin);
                    if (pendingCookie != null)   pending.setCookie(pendingCookie);
                    else if (urlCookie != null)  pending.setCookie(urlCookie);
                    if (pendingDrmUrl != null)   pending.setDrmLicenseUrl(pendingDrmUrl);
                    if (pendingDrmKeys != null)  pending.setDrmKeys(pendingDrmKeys);

                    // Also check URL for user-agent param (some providers embed it)
                    if (pending.getUserAgent() == null) {
                        String uaFromUrl = extractParamFromUrl(url, "user-agent");
                        if (uaFromUrl == null) uaFromUrl = extractParamFromUrl(url, "useragent");
                        if (uaFromUrl != null) pending.setUserAgent(uaFromUrl);
                    }
                    if (pending.getReferer() == null) {
                        String refFromUrl = extractParamFromUrl(url, "referer");
                        if (refFromUrl == null) refFromUrl = extractParamFromUrl(url, "referrer");
                        if (refFromUrl != null) pending.setReferer(refFromUrl);
                    }
                    if (pending.getOrigin() == null) {
                        String originFromUrl = extractParamFromUrl(url, "origin");
                        if (originFromUrl != null) pending.setOrigin(originFromUrl);
                    }

                    channels.add(pending);
                    pending = null;
                }
            }
        }

        reader.close();
        return channels;
    }

    private static Channel parseExtInf(String line) {
        Channel ch = new Channel();

        applyAttr(ch, P_TVG_ID,       line, "tvg-id");
        applyAttr(ch, P_TVG_NAME,     line, "tvg-name");
        applyAttr(ch, P_TVG_LOGO,     line, "tvg-logo");
        applyAttr(ch, P_GROUP,        line, "group");
        applyAttr(ch, P_UA_ATTR,      line, "ua");
        applyAttr(ch, P_REF_ATTR,     line, "ref");
        applyAttr(ch, P_ORIGIN_ATTR,  line, "origin");
        applyAttr(ch, P_COOKIE_ATTR,  line, "cookie");
        applyAttr(ch, P_DRM_ATTR,     line, "drm");

        // Channel name: everything after the last comma
        Matcher m = P_CHANNEL_NAME.matcher(line);
        if (m.find()) ch.setName(m.group(1).trim());

        if (ch.getName() != null) {
            String n = ch.getName().toUpperCase();
            ch.setHD(n.contains("HD") || n.contains("FHD") || n.contains("4K") || n.contains("UHD"));
        }

        return ch;
    }

    private static void applyAttr(Channel ch, Pattern p, String line, String field) {
        Matcher m = p.matcher(line);
        if (!m.find()) return;
        String val = m.group(1).trim();
        switch (field) {
            case "tvg-id":  ch.setTvgId(val); break;
            case "tvg-name":ch.setTvgName(val); break;
            case "tvg-logo":ch.setLogoUrl(val); break;
            case "group":   ch.setGroup(val); break;
            case "ua":      ch.setUserAgent(val); break;
            case "ref":     ch.setReferer(val); break;
            case "origin":  ch.setOrigin(val); break;
            case "cookie":  ch.setCookie(val); break;
            case "drm":
                if (val.startsWith("http://") || val.startsWith("https://"))
                    ch.setDrmLicenseUrl(val);
                else
                    ch.setDrmKeys(val);
                break;
        }
    }

    /** Extract a cookie-like param from the URL query string */
    private static String extractCookieFromUrl(String url) {
        if (url == null || !url.contains("?")) return null;
        Matcher m = P_URL_COOKIE_PARAM.matcher(url);
        if (m.find()) return m.group(1);
        return null;
    }

    /** Extract named query param from URL */
    private static String extractParamFromUrl(String url, String param) {
        if (url == null || !url.contains("?")) return null;
        Pattern p = Pattern.compile("[?&]" + Pattern.quote(param) + "=([^&\\s#]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(url);
        if (m.find()) return m.group(1);
        return null;
    }

    public static Map<String, List<Channel>> groupChannels(List<Channel> channels) {
        LinkedHashMap<String, List<Channel>> groups = new LinkedHashMap<>();
        for (Channel ch : channels) {
            String group = ch.getGroup();
            if (group == null || group.isEmpty()) group = "Uncategorized";
            if (!groups.containsKey(group)) groups.put(group, new ArrayList<>());
            groups.get(group).add(ch);
        }
        return groups;
    }
}
