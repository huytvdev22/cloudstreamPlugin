package com.cloudstream.core.model;

/**
 * Đại diện cho một item phim trong danh sách (Trang chủ, Tìm kiếm, Danh mục).
 * Dùng chung cho tất cả các Provider.
 */
public class MovieItem {
    public final String title;
    public final String href;
    public final String posterUrl;

    public MovieItem(String title, String href, String posterUrl) {
        this.title = title;
        this.href = href;
        this.posterUrl = posterUrl;
    }

    @Override
    public String toString() {
        return "MovieItem{" +
                "title='" + title + '\'' +
                ", href='" + href + '\'' +
                ", posterUrl='" + posterUrl + '\'' +
                '}';
    }
}
