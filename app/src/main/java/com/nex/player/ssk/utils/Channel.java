package com.nex.player.ssk.utils;

import java.io.Serializable;

public class Channel implements Serializable {
    private String name;
    private String url;
    private String logoUrl;
    private String group;
    private String tvgId;
    private String tvgName;
    private boolean isHD;

    // Advanced playback params
    private String userAgent;
    private String referer;
    private String origin;
    private String cookie;
    private String drmLicenseUrl;
    private String drmKeys;

    // EPG
    private String epgTitle;
    private String epgStart;
    private String epgEnd;

    public Channel() {}

    public Channel(String name, String url, String group, String logoUrl) {
        this.name = name;
        this.url = url;
        this.group = group;
        this.logoUrl = logoUrl;
        this.isHD = name != null && (name.toUpperCase().contains("HD") || name.toUpperCase().contains("FHD") || name.toUpperCase().contains("4K"));
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public String getTvgId() { return tvgId; }
    public void setTvgId(String tvgId) { this.tvgId = tvgId; }

    public String getTvgName() { return tvgName; }
    public void setTvgName(String tvgName) { this.tvgName = tvgName; }

    public boolean isHD() { return isHD; }
    public void setHD(boolean HD) { isHD = HD; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getReferer() { return referer; }
    public void setReferer(String referer) { this.referer = referer; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getCookie() { return cookie; }
    public void setCookie(String cookie) { this.cookie = cookie; }

    public String getDrmLicenseUrl() { return drmLicenseUrl; }
    public void setDrmLicenseUrl(String drmLicenseUrl) { this.drmLicenseUrl = drmLicenseUrl; }

    public String getDrmKeys() { return drmKeys; }
    public void setDrmKeys(String drmKeys) { this.drmKeys = drmKeys; }

    public String getEpgTitle() { return epgTitle != null ? epgTitle : ""; }
    public void setEpgTitle(String epgTitle) { this.epgTitle = epgTitle; }

    public String getEpgStart() { return epgStart; }
    public void setEpgStart(String epgStart) { this.epgStart = epgStart; }

    public String getEpgEnd() { return epgEnd; }
    public void setEpgEnd(String epgEnd) { this.epgEnd = epgEnd; }

    public boolean hasEpg() {
        return epgTitle != null && !epgTitle.isEmpty();
    }

    public String getDisplayEpg() {
        if (!hasEpg()) return "No EPG teleguide";
        if (epgStart != null && epgEnd != null) {
            return epgStart + "-" + epgEnd + " " + epgTitle;
        }
        return epgTitle;
    }
}
