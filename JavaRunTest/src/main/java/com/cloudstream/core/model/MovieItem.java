package com.cloudstream.core.model;

import java.util.Collections;
import java.util.List;

/**
 * Đại diện cho một item phim trong danh sách (Trang chủ, Tìm kiếm, Danh mục).
 * Dùng chung cho tất cả các Provider.
 */
public class MovieItem {
    public final String title;
    public final String href;
    public final String posterUrl;
    public final List<String> tags;

    public MovieItem(String title, String href, String posterUrl) {
        this(title, href, posterUrl, Collections.emptyList());
    }

    public MovieItem(String title, String href, String posterUrl, List<String> tags) {
        this.title = title;
        this.href = href;
        this.posterUrl = posterUrl;
        this.tags = (tags != null) ? tags : Collections.emptyList();
    }

    @Override
    public String toString() {
        return "MovieItem{" +
                "title='" + title + '\'' +
                ", href='" + href + '\'' +
                ", posterUrl='" + posterUrl + '\'' +
                ", tags=" + tags +
                '}';
    }
}
