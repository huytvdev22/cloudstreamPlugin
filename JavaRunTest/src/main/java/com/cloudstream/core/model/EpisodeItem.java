package com.cloudstream.core.model;

/**
 * Đại diện cho một tập phim.
 * Dùng chung cho tất cả các Provider.
 */
public class EpisodeItem {
    public final String href;
    public final String name;
    public final int episodeNum;

    public EpisodeItem(String href, String name, int episodeNum) {
        this.href = href;
        this.name = name;
        this.episodeNum = episodeNum;
    }

    @Override
    public String toString() {
        return "EpisodeItem{" +
                "name='" + name + '\'' +
                ", episodeNum=" + episodeNum +
                ", href='" + href + '\'' +
                '}';
    }
}
