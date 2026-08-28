package com.cloudstream.core.model;

import java.util.List;

/**
 * Đại diện cho thông tin chi tiết một bộ phim và danh sách tập của phim đó.
 * Dùng chung cho tất cả các Provider.
 */
public class MovieDetail {
    public final String title;
    public final String posterUrl;
    public final String plot;
    public final Integer year;
    public final Integer duration;
    public final List<String> tags;
    public final List<EpisodeItem> episodes;

    public MovieDetail(String title, String posterUrl, String plot,
                       Integer year, Integer duration, List<String> tags, List<EpisodeItem> episodes) {
        this.title = title;
        this.posterUrl = posterUrl;
        this.plot = plot;
        this.year = year;
        this.duration = duration;
        this.tags = tags;
        this.episodes = episodes;
    }

    @Override
    public String toString() {
        return "MovieDetail{" +
                "title='" + title + '\'' +
                ", year=" + year +
                ", duration=" + duration +
                ", episodes=" + (episodes != null ? episodes.size() : 0) +
                '}';
    }
}
