package com.cloudstream.core.model;

/**
 * Đại diện cho một link video stream (M3U8 hoặc Embed).
 * Dùng chung cho tất cả các Provider.
 */
public class VideoLink {
    public static final String TYPE_M3U8 = "M3U8";
    public static final String TYPE_EMBED = "EMBED";

    public final String type;
    public final String url;
    public final String label;
    public final String serverName;
    public final String langName;

    public VideoLink(String type, String url, String label) {
        this(type, url, label, "Vieflix", label);
    }

    public VideoLink(String type, String url, String label, String serverName, String langName) {
        this.type = type;
        this.url = url;
        this.label = label;
        this.serverName = serverName;
        this.langName = langName;
    }

    @Override
    public String toString() {
        return "VideoLink{" +
                "type='" + type + '\'' +
                ", label='" + label + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
