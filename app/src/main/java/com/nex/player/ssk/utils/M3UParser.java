package com.nex.player.ssk.utils;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class M3UParser {

    private static final String TAG = "M3UParser";

    private static final Pattern EXTINF_PATTERN = Pattern.compile("#EXTINF:(-?\\d+)(.*)");
    private static final Pattern ATTR_TVG_ID = Pattern.compile("tvg-id=\"([^\"]*)\"");
    private static final Pattern ATTR_TVG_NAME = Pattern.compile("tvg-name=\"([^\"]*)\"");
    private static final Pattern ATTR_TVG_LOGO = Pattern.compile("tvg-logo=\"([^\"]*)\"");
    private static final Pattern ATTR_GROUP = Pattern.compile("group-title=\"([^\"]*)\"");
    private static final Pattern ATTR_USER_AGENT = Pattern.compile("(?i)user-agent=\"([^\"]*)\"");
    private static final Pattern ATTR_REFERRER = Pattern.compile("(?i)referrer=\"([^\"]*)\"");
    private static final Pattern ATTR_ORIGIN = Pattern.compile("(?i)origin=\"([^\"]*)\"");
    private static final Pattern ATTR_COOKIE = Pattern.compile("(?i)tvg-cookie=\"([^\"]*)\"");
    private static final Pattern ATTR_DRM_URL = Pattern.compile("(?i)drm-license=\"([^\"]*)\"");
    private static final Pattern CHANNEL_NAME_PATTERN = Pattern.compile(",(.+)$");
    private static final Pattern VLC_OPT_UA = Pattern.compile("#EXTVLCOPT:http-user-agent=(.+)");
    private static final Pattern VLC_OPT_REF = Pattern.compile("#EXTVLCOPT:http-referrer=(.+)");

    public interface ParseCallback {
        void onChannelParsed(Channel channel);
        void onComplete(int total);
        void onError(String error);
    }

    public static List<Channel> parse(InputStream inputStream) throws IOException {
        List<Channel> channels = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

        String line;
        Channel pendingChannel = null;
        String pendingUserAgent = null;
        String pendingReferer = null;

        while ((line = reader.readLine()) != null) {
            line = line.trim();

            if (line.isEmpty()) continue;

            if (line.startsWith("#EXTINF:")) {
                pendingChannel = parseExtInf(line);
                pendingUserAgent = null;
                pendingReferer = null;
            } else if (line.startsWith("#EXTVLCOPT:http-user-agent=")) {
                Matcher m = VLC_OPT_UA.matcher(line);
                if (m.find()) pendingUserAgent = m.group(1).trim();
            } else if (line.startsWith("#EXTVLCOPT:http-referrer=")) {
                Matcher m = VLC_OPT_REF.matcher(line);
                if (m.find()) pendingReferer = m.group(1).trim();
            } else if (!line.startsWith("#")) {
                // This is a URL
                if (pendingChannel != null) {
                    pendingChannel.setUrl(line);
                    if (pendingUserAgent != null) pendingChannel.setUserAgent(pendingUserAgent);
                    if (pendingReferer != null) pendingChannel.setReferer(pendingReferer);
                    channels.add(pendingChannel);
                    pendingChannel = null;
                }
            }
        }

        reader.close();
        return channels;
    }

    private static Channel parseExtInf(String line) {
        Channel channel = new Channel();

        // Extract tvg-id
        Matcher m = ATTR_TVG_ID.matcher(line);
        if (m.find()) channel.setTvgId(m.group(1));

        // Extract tvg-name
        m = ATTR_TVG_NAME.matcher(line);
        if (m.find()) channel.setTvgName(m.group(1));

        // Extract tvg-logo
        m = ATTR_TVG_LOGO.matcher(line);
        if (m.find()) channel.setLogoUrl(m.group(1));

        // Extract group-title
        m = ATTR_GROUP.matcher(line);
        if (m.find()) channel.setGroup(m.group(1));

        // Extract user-agent
        m = ATTR_USER_AGENT.matcher(line);
        if (m.find()) channel.setUserAgent(m.group(1));

        // Extract referrer
        m = ATTR_REFERRER.matcher(line);
        if (m.find()) channel.setReferer(m.group(1));

        // Extract origin
        m = ATTR_ORIGIN.matcher(line);
        if (m.find()) channel.setOrigin(m.group(1));

        // Extract cookie
        m = ATTR_COOKIE.matcher(line);
        if (m.find()) channel.setCookie(m.group(1));

        // Extract DRM URL
        m = ATTR_DRM_URL.matcher(line);
        if (m.find()) channel.setDrmLicenseUrl(m.group(1));

        // Extract channel name (after the last comma)
        m = CHANNEL_NAME_PATTERN.matcher(line);
        if (m.find()) {
            channel.setName(m.group(1).trim());
        }

        // Determine HD
        String name = channel.getName();
        if (name != null) {
            channel.setHD(name.contains("HD") || name.contains("FHD") || name.contains("4K") || name.contains("UHD"));
        }

        return channel;
    }

    /**
     * Group channels by their group-title
     */
    public static java.util.Map<String, List<Channel>> groupChannels(List<Channel> channels) {
        java.util.LinkedHashMap<String, List<Channel>> groups = new java.util.LinkedHashMap<>();
        for (Channel ch : channels) {
            String group = ch.getGroup();
            if (group == null || group.isEmpty()) group = "Uncategorized";
            if (!groups.containsKey(group)) {
                groups.put(group, new ArrayList<>());
            }
            groups.get(group).add(ch);
        }
        return groups;
    }
}
