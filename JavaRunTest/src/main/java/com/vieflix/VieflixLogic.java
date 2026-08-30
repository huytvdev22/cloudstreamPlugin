package com.vieflix;

import com.cloudstream.core.model.EpisodeItem;
import com.cloudstream.core.model.MainPageSection;
import com.cloudstream.core.model.MovieDetail;
import com.cloudstream.core.model.MovieItem;
import com.cloudstream.core.model.VideoLink;

import java.util.List;

/**
 * VieflixLogic - Static Facade cho Kotlin CloudStream Provider.
 * Cung cấp API tĩnh gọi đến VieflixParser để giữ code Kotlin ngắn gọn và dễ sử dụng.
 */
public class VieflixLogic {

    // ==========================================
    // BACKWARD COMPATIBILITY TYPE ALIASES
    // ==========================================
    public static class MainPageSection extends com.cloudstream.core.model.MainPageSection {
        public MainPageSection(String name, String path) {
            super(name, path);
        }
    }

    public static class MovieItem extends com.cloudstream.core.model.MovieItem {
        public MovieItem(String title, String href, String posterUrl) {
            super(title, href, posterUrl);
        }
    }

    public static class MovieDetail extends com.cloudstream.core.model.MovieDetail {
        public MovieDetail(String title, String posterUrl, String plot,
                           Integer year, Integer duration, List<String> tags, List<com.cloudstream.core.model.EpisodeItem> episodes) {
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
    }

    public static final String PORTAL_URL = VieflixParser.PORTAL_URL;
    public static final String DEFAULT_BASE_URL = VieflixParser.DEFAULT_BASE_URL;

    // ==========================================
    // STATIC DELEGATION METHODS
    // ==========================================

    public static String parseDomain(String html) {
        return VieflixParser.getInstance().parseDomain(html);
    }

    public static List<com.cloudstream.core.model.MainPageSection> parseMainPage(String html) {
        return VieflixParser.getInstance().parseMainPage(html);
    }

    public static List<com.cloudstream.core.model.MainPageSection> parseMainPage(String html, String baseUrl) {
        return VieflixParser.getInstance().parseMainPage(html, baseUrl);
    }

    public static List<com.cloudstream.core.model.MovieItem> parseMovieList(String html, String baseUrl) {
        return VieflixParser.getInstance().parseMovieList(html, baseUrl);
    }

    public static com.cloudstream.core.model.MovieDetail parseMovieDetail(String html, String baseUrl) {
        return VieflixParser.getInstance().parseMovieDetail(html, baseUrl);
    }

    public static List<com.cloudstream.core.model.VideoLink> extractVideoLinks(String html, String slug) {
        return VieflixParser.getInstance().extractVideoLinks(html, slug);
    }

    public static String buildSearchUrl(String baseUrl, String query, int page) {
        return VieflixParser.getInstance().buildSearchUrl(baseUrl, query, page);
    }
}
