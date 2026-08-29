package com.cloudstream.core.parser;

import com.cloudstream.core.model.MovieDetail;
import com.cloudstream.core.model.MovieItem;
import com.cloudstream.core.model.VideoLink;

import java.util.List;

/**
 * Interface chuẩn cho mọi Movie Provider Logic.
 * Bất kỳ nguồn phim nào (Vieflix, OPhim, MotChill, PhimMoi,...)
 * đều triển khai interface này để thống nhất luồng xử lý.
 */
public interface MovieParser {

    /**
     * Bóc tách danh sách phim từ HTML/JSON (Trang chủ, Tìm kiếm, Thể loại).
     *
     * @param content Nội dung HTML hoặc JSON của trang
     * @param baseUrl URL gốc để chuẩn hóa đường dẫn tương đối
     * @return Danh sách MovieItem
     */
    List<MovieItem> parseMovieList(String content, String baseUrl);

    /**
     * Bóc tách thông tin chi tiết một bộ phim (tiêu đề, poster, mô tả, tập phim).
     *
     * @param content Nội dung HTML hoặc JSON trang chi tiết
     * @param baseUrl URL gốc
     * @return MovieDetail
     */
    MovieDetail parseMovieDetail(String content, String baseUrl);

    /**
     * Trích xuất link phát video (M3U8 / Embed) cho một tập phim.
     *
     * @param content    Nội dung HTML/JSON chứa dữ liệu video
     * @param slugOrData Định danh tập phim hoặc dữ liệu kèm theo
     * @return Danh sách VideoLink
     */
    List<VideoLink> extractVideoLinks(String content, String slugOrData);
}
