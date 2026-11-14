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

    // EPG current
    private String epgTitle;
    private String epgStart;
    private String epgEnd;
    private long epgStartMs;
    private long epgEndMs;

    // EPG next
    private String epgNextTitle;
    private String epgNextTime;

    public Channel() {}

    public Channel(String name, String url, String group, String logoUrl) {
        this.name    = name;
        this.url     = url;
        this.group   = group;
        this.logoUrl = logoUrl;
        this.isHD    = name != null &&
                (name.toUpperCase().contains("HD") ||
                 name.toUpperCase().contains("FHD") ||
                 name.toUpperCase().contains("4K"));
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public String getName()           { return name; }
    public void   setName(String v)   { name = v; }

    public String getUrl()            { return url; }
    public void   setUrl(String v)    { url = v; }

    public String getLogoUrl()        { return logoUrl; }
    public void   setLogoUrl(String v){ logoUrl = v; }

    public String getGroup()          { return group; }
    public void   setGroup(String v)  { group = v; }

    public String getTvgId()          { return tvgId; }
    public void   setTvgId(String v)  { tvgId = v; }

    public String getTvgName()        { return tvgName; }
    public void   setTvgName(String v){ tvgName = v; }

    public boolean isHD()             { return isHD; }
    public void    setHD(boolean v)   { isHD = v; }

    public String getUserAgent()          { return userAgent; }
    public void   setUserAgent(String v)  { userAgent = v; }

    public String getReferer()            { return referer; }
    public void   setReferer(String v)    { referer = v; }

    public String getOrigin()             { return origin; }
    public void   setOrigin(String v)     { origin = v; }

    public String getCookie()             { return cookie; }
    public void   setCookie(String v)     { cookie = v; }

    public String getDrmLicenseUrl()          { return drmLicenseUrl; }
    public void   setDrmLicenseUrl(String v)  { drmLicenseUrl = v; }

    public String getDrmKeys()            { return drmKeys; }
    public void   setDrmKeys(String v)    { drmKeys = v; }

    // EPG current
    public String getEpgTitle()       { return epgTitle != null ? epgTitle : ""; }
    public void   setEpgTitle(String v){ epgTitle = v; }

    public String getEpgStart()       { return epgStart; }
    public void   setEpgStart(String v){ epgStart = v; }

    public String getEpgEnd()         { return epgEnd; }
    public void   setEpgEnd(String v) { epgEnd = v; }

    public long getEpgStartMs()           { return epgStartMs; }
    public void setEpgStartMs(long v)     { epgStartMs = v; }

    public long getEpgEndMs()             { return epgEndMs; }
    public void setEpgEndMs(long v)       { epgEndMs = v; }

    // EPG next
    public String getEpgNextTitle()       { return epgNextTitle; }
    public void   setEpgNextTitle(String v){ epgNextTitle = v; }

    public String getEpgNextTime()        { return epgNextTime; }
    public void   setEpgNextTime(String v){ epgNextTime = v; }

    // ── Computed helpers ──────────────────────────────────────────────────

    public boolean hasEpg() {
        return epgTitle != null && !epgTitle.isEmpty();
    }

    /** 0-100 progress of current show, -1 if unknown */
    public int getEpgProgress() {
        if (epgStartMs <= 0 || epgEndMs <= 0) return -1;
        long now      = System.currentTimeMillis();
        long duration = epgEndMs - epgStartMs;
        if (duration <= 0) return -1;
        int prog = (int) (((now - epgStartMs) * 100L) / duration);
        return Math.max(0, Math.min(100, prog));
    }

    public String getDisplayEpg() {
        if (!hasEpg()) return "No EPG teleguide";
        if (epgStart != null && epgEnd != null)
            return epgStart + " - " + epgEnd + "  " + epgTitle;
        return epgTitle;
    }
}
