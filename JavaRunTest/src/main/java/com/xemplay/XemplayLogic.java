package com.xemplay;

import com.cloudstream.core.model.EpisodeItem;
import com.cloudstream.core.model.MainPageSection;
import com.cloudstream.core.model.MovieDetail;
import com.cloudstream.core.model.MovieItem;
import com.cloudstream.core.model.VideoLink;

import java.util.List;

/**
 * XemplayLogic - Static Facade cho Kotlin CloudStream Provider.
 *
 * Cung cấp các API tĩnh chuyển tiếp trực tiếp đến {@link XemplayParser},
 * giúp mã nguồn Kotlin Adapter siêu mỏng (Thin Adapter), dễ đọc và bảo trì.
 */
public class XemplayLogic {

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

    public static final String PORTAL_URL = XemplayParser.PORTAL_URL;
    public static final String DEFAULT_BASE_URL = XemplayParser.DEFAULT_BASE_URL;

    // =========================================================================
    // PHƯƠNG THỨC STATIC FACADE
    // =========================================================================

    /**
     * Bóc tách tên miền động đang hoạt động từ HTML của trang portal (xemplay.com).
     */
    public static String parseDomain(String html) {
        return XemplayParser.getInstance().parseDomain(html);
    }

    /**
     * Lấy danh sách các mục/section trên trang chủ.
     */
    public static List<com.cloudstream.core.model.MainPageSection> parseMainPage(String content) {
        return XemplayParser.getInstance().parseMainPage(content, DEFAULT_BASE_URL);
    }

    /**
     * Lấy danh sách các mục/section trên trang chủ với baseUrl tùy biến.
     */
    public static List<com.cloudstream.core.model.MainPageSection> parseMainPage(String content, String baseUrl) {
        return XemplayParser.getInstance().parseMainPage(content, baseUrl);
    }

    /**
     * Phân tích danh sách phim từ HTML trang danh mục hoặc tìm kiếm.
     */
    public static List<com.cloudstream.core.model.MovieItem> parseMovieList(String html, String baseUrl) {
        return XemplayParser.getInstance().parseMovieList(html, baseUrl);
    }

    /**
     * Phân tích chi tiết phim và danh sách tập từ HTML trang chi tiết phim.
     */
    public static com.cloudstream.core.model.MovieDetail parseMovieDetail(String html, String baseUrl) {
        return XemplayParser.getInstance().parseMovieDetail(html, baseUrl);
    }

    /**
     * Trích xuất link phát trực tiếp (.m3u8 hoặc embed) từ trang xem phim.
     */
    public static List<com.cloudstream.core.model.VideoLink> extractVideoLinks(String html, String slugOrData) {
        return XemplayParser.getInstance().extractVideoLinks(html, slugOrData);
    }

    /**
     * Xây dựng URL tìm kiếm hỗ trợ hashtag thông minh.
     */
    public static String buildSearchUrl(String baseUrl, String query, int page) {
        return XemplayParser.getInstance().buildSearchUrl(baseUrl, query, page);
    }
}
