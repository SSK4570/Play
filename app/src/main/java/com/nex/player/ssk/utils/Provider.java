package com.nex.player.ssk.utils;

import java.io.Serializable;

public class Provider implements Serializable {
    public static final int TYPE_M3U_URL = 0;
    public static final int TYPE_M3U_FILE = 1;

    private long id;
    private String name;
    private String url;        // M3U URL
    private String filePath;   // Local file path
    private String epgUrl;     // XMLTV EPG URL
    private int type;
    private long lastUpdated;
    private int channelCount;

    public Provider() {}

    public Provider(String name, String url, String epgUrl, int type) {
        this.name = name;
        this.url = url;
        this.epgUrl = epgUrl;
        this.type = type;
        this.lastUpdated = System.currentTimeMillis();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name != null ? name : ""; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getEpgUrl() { return epgUrl; }
    public void setEpgUrl(String epgUrl) { this.epgUrl = epgUrl; }

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }

    public int getChannelCount() { return channelCount; }
    public void setChannelCount(int channelCount) { this.channelCount = channelCount; }

    public String getDisplayUrl() {
        if (type == TYPE_M3U_FILE) return filePath != null ? filePath : "";
        return url != null ? url : "";
    }
}
