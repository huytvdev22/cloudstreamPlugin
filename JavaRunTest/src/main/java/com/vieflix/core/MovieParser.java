package com.vieflix.core;

import com.vieflix.model.MovieDetail;
import com.vieflix.model.MovieItem;
import com.vieflix.model.VideoLink;

import java.util.List;

/**
 * Interface chuẩn cho mọi Movie Provider Logic.
 * Bất kỳ nguồn phim nào (Vieflix, OPhim, MotChill, PhimMoi,...)
 * đều triển khai interface này để thống nhất luồng xử lý.
 */
public interface MovieParser {

    /**
     * Bóc tách danh sách phim từ HTML (Trang chủ, Tìm kiếm, Thể loại).
     *
     * @param html    Nội dung HTML của trang
     * @param baseUrl URL gốc để chuẩn hóa đường dẫn tương đối
     * @return Danh sách MovieItem
     */
    List<MovieItem> parseMovieList(String html, String baseUrl);

    /**
     * Bóc tách thông tin chi tiết một bộ phim (tiêu đề, poster, mô tả, tập phim).
     *
     * @param html    Nội dung HTML trang chi tiết
     * @param baseUrl URL gốc
     * @return MovieDetail
     */
    MovieDetail parseMovieDetail(String html, String baseUrl);

    /**
     * Trích xuất link phát video (M3U8 / Embed) cho một tập phim.
     *
     * @param html       Nội dung HTML chứa dữ liệu video
     * @param slugOrData Định danh tập phim hoặc dữ liệu kèm theo
     * @return Danh sách VideoLink
     */
    List<VideoLink> extractVideoLinks(String html, String slugOrData);
}
