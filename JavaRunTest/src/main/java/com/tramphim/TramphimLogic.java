package com.tramphim;

import com.cloudstream.core.model.EpisodeItem;
import com.cloudstream.core.model.MainPageSection;
import com.cloudstream.core.model.MovieDetail;
import com.cloudstream.core.model.MovieItem;
import com.cloudstream.core.model.VideoLink;

import java.util.List;

/**
 * TramphimLogic - Static Facade cho Kotlin CloudStream Provider.
 * Cung cấp API tĩnh gọi đến TramphimParser để giữ code Kotlin Adapter mỏng và sạch sẽ.
 */
public class TramphimLogic {

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

        public MovieItem(String title, String href, String posterUrl, List<String> tags) {
            super(title, href, posterUrl, tags);
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

        public VideoLink(String type, String url, String label, String serverName, String langName) {
            super(type, url, label, serverName, langName);
        }
    }

    public static final String PORTAL_URL = TramphimParser.PORTAL_URL;
    public static final String DEFAULT_BASE_URL = TramphimParser.DEFAULT_BASE_URL;

    // ==========================================
    // STATIC DELEGATION METHODS
    // ==========================================

    public static String parseDomain(String html) {
        return TramphimParser.getInstance().parseDomain(html);
    }

    public static List<com.cloudstream.core.model.MainPageSection> parseMainPage(String html) {
        return TramphimParser.getInstance().parseMainPage(html, DEFAULT_BASE_URL);
    }

    public static List<com.cloudstream.core.model.MainPageSection> parseMainPage(String html, String baseUrl) {
        return TramphimParser.getInstance().parseMainPage(html, baseUrl);
    }

    public static List<com.cloudstream.core.model.MovieItem> parseMovieList(String html, String baseUrl) {
        return TramphimParser.getInstance().parseMovieList(html, baseUrl);
    }

    public static com.cloudstream.core.model.MovieDetail parseMovieDetail(String html, String baseUrl) {
        return TramphimParser.getInstance().parseMovieDetail(html, baseUrl);
    }

    public static List<com.cloudstream.core.model.VideoLink> extractVideoLinks(String html, String slugOrData) {
        return TramphimParser.getInstance().extractVideoLinks(html, slugOrData);
    }

    public static String decryptStreamcM3u8(String encryptedM3u8, String videoHash) throws Exception {
        return TramphimParser.getInstance().decryptStreamcM3u8(encryptedM3u8, videoHash);
    }

    public static String buildSearchUrl(String baseUrl, String query, int page) {
        return TramphimParser.getInstance().buildSearchUrl(baseUrl, query, page);
    }
}
