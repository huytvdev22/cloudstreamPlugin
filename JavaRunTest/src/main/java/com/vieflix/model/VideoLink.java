package com.vieflix.model;

/**
 * Đại diện cho một link video stream (M3U8 hoặc Embed).
 */
public class VideoLink {
    public static final String TYPE_M3U8 = "M3U8";
    public static final String TYPE_EMBED = "EMBED";

    public final String type;
    public final String url;
    public final String label;

    public VideoLink(String type, String url, String label) {
        this.type = type;
        this.url = url;
        this.label = label;
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
