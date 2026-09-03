package com.chophim;

import com.cloudstream.core.model.EpisodeItem;
import com.cloudstream.core.model.MainPageSection;
import com.cloudstream.core.model.MovieDetail;
import com.cloudstream.core.model.MovieItem;
import com.cloudstream.core.model.VideoLink;

import java.util.List;

/**
 * ChophimLogic - Static Facade đóng vai trò cầu nối tiện dụng giữa Kotlin Adapter và Java Parser.
 * Tuân thủ mô hình Facade Pattern để đơn giản hóa giao diện lập trình.
 */
public class ChophimLogic {

    public static final String DEFAULT_BASE_URL = ChophimParser.DEFAULT_BASE_URL;
    public static final String API_BASE_URL = ChophimParser.API_BASE_URL;

    // Type Aliases kế thừa từ com.cloudstream.core.model để tương thích mã nguồn
    public static class MovieItem extends com.cloudstream.core.model.MovieItem {
        public MovieItem(String title, String href, String posterUrl, List<String> tags) {
            super(title, href, posterUrl, tags);
        }
    }

    public static class MovieDetail extends com.cloudstream.core.model.MovieDetail {
        public MovieDetail(String title, String posterUrl, String plot, Integer year, Integer duration, List<String> tags, List<com.cloudstream.core.model.EpisodeItem> episodes) {
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

    public static class MainPageSection extends com.cloudstream.core.model.MainPageSection {
        public MainPageSection(String name, String path) {
            super(name, path);
        }
    }

    // ==========================================
    // STATIC FACADE METHODS
    // ==========================================

    public static List<com.cloudstream.core.model.MainPageSection> parseMainPage(String content, String baseUrl) {
        return ChophimParser.getInstance().parseMainPage(content, baseUrl);
    }

    public static List<com.cloudstream.core.model.MovieItem> parseMovieList(String content, String baseUrl) {
        return ChophimParser.getInstance().parseMovieList(content, baseUrl);
    }

    public static com.cloudstream.core.model.MovieDetail parseMovieDetail(String content, String baseUrl) {
        return ChophimParser.getInstance().parseMovieDetail(content, baseUrl);
    }

    public static List<com.cloudstream.core.model.VideoLink> extractVideoLinks(String content, String slugOrEp) {
        return ChophimParser.getInstance().extractVideoLinks(content, slugOrEp);
    }

    public static String buildSearchUrl(String baseUrl, String query, int page) {
        return ChophimParser.getInstance().buildSearchUrl(baseUrl, query, page);
    }
}
