package com.motchill;

import com.cloudstream.core.model.EpisodeItem;
import com.cloudstream.core.model.MainPageSection;
import com.cloudstream.core.model.MovieDetail;
import com.cloudstream.core.model.MovieItem;
import com.cloudstream.core.model.VideoLink;

import java.util.List;

/**
 * MotchillLogic - Static Facade cho Kotlin CloudStream Provider.
 *
 * Cung cấp các API tĩnh chuyển tiếp trực tiếp đến {@link MotchillParser},
 * giúp mã nguồn Kotlin Adapter siêu mỏng (Thin Adapter), dễ đọc và bảo trì.
 */
public class MotchillLogic {

    // =========================================================================
    // BACKWARD COMPATIBILITY TYPE ALIASES
    // =========================================================================

    public static class MainPageSection extends com.cloudstream.core.model.MainPageSection {
        public MainPageSection(String name, String path) {
            super(name, path);
        }
    }

    public static class MovieItem extends com.cloudstream.core.model.MovieItem {
        public MovieItem(String title, String href, String posterUrl) {
            super(title, href, posterUrl);
        }

        public MovieItem(String title, String href, String posterUrl, List<String> tags) {
            super(title, href, posterUrl, tags);
        }
    }

    public static class MovieDetail extends com.cloudstream.core.model.MovieDetail {
        public MovieDetail(String title, String posterUrl, String plot,
                           Integer year, Integer duration, List<String> tags,
                           List<com.cloudstream.core.model.EpisodeItem> episodes) {
            super(title, posterUrl, plot, year, duration, tags, episodes);
        }
    }

    public static class EpisodeItem extends com.cloudstream.core.model.EpisodeItem {
        public EpisodeItem(String href, String name, int episodeNum) {
            super(href, name, episodeNum);
        }
    }

    public static class VideoLink extends com.cloudstream.core.model.VideoLink {
        public VideoLink(String type, String url, String label) {
            super(type, url, label);
        }

        public VideoLink(String type, String url, String label, String serverName, String langName) {
            super(type, url, label, serverName, langName);
        }
    }

    // =========================================================================
    // HẰNG SỐ CƠ BẢN
    // =========================================================================

    public static final String PORTAL_URL = MotchillParser.PORTAL_URL;
    public static final String DEFAULT_BASE_URL = MotchillParser.DEFAULT_BASE_URL;

    // =========================================================================
    // PHƯƠNG THỨC STATIC FACADE
    // =========================================================================

    /**
     * Bóc tách tên miền động đang hoạt động từ HTML của trang portal (motchillw.sh).
     */
    public static String parseDomain(String html) {
        return MotchillParser.getInstance().parseDomain(html);
    }

    /**
     * Lấy danh sách các mục/section trên trang chủ.
     */
    public static List<com.cloudstream.core.model.MainPageSection> parseMainPage(String content) {
        return MotchillParser.getInstance().parseMainPage(content, DEFAULT_BASE_URL);
    }

    /**
     * Lấy danh sách các mục/section trên trang chủ với baseUrl tùy biến.
     */
    public static List<com.cloudstream.core.model.MainPageSection> parseMainPage(String content, String baseUrl) {
        return MotchillParser.getInstance().parseMainPage(content, baseUrl);
    }

    /**
     * Phân tích danh sách phim từ HTML trang danh mục hoặc tìm kiếm.
     */
    public static List<com.cloudstream.core.model.MovieItem> parseMovieList(String html, String baseUrl) {
        return MotchillParser.getInstance().parseMovieList(html, baseUrl);
    }

    /**
     * Phân tích chi tiết phim, metadata và danh sách tập.
     */
    public static com.cloudstream.core.model.MovieDetail parseMovieDetail(String html, String baseUrl) {
        return MotchillParser.getInstance().parseMovieDetail(html, baseUrl);
    }

    /**
     * Trích xuất các luồng phát video HLS m3u8.
     */
    public static List<com.cloudstream.core.model.VideoLink> extractVideoLinks(String html, String slugOrData) {
        return MotchillParser.getInstance().extractVideoLinks(html, slugOrData);
    }

    /**
     * Xây dựng URL tìm kiếm tương thích với motchillw.sh.
     */
    public static String buildSearchUrl(String baseUrl, String query, int page) {
        return MotchillParser.getInstance().buildSearchUrl(baseUrl, query, page);
    }
}
