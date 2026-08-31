package com.animevietsub;

import com.cloudstream.core.model.EpisodeItem;
import com.cloudstream.core.model.MainPageSection;
import com.cloudstream.core.model.MovieDetail;
import com.cloudstream.core.model.MovieItem;
import com.cloudstream.core.model.VideoLink;

import java.util.List;

/**
 * AnimeVietsubLogic - Static Facade cho Kotlin CloudStream Provider.
 * Cung cấp API tĩnh gọi đến AnimeVietsubParser để giữ code Kotlin ngắn gọn và dễ sử dụng.
 */
public class AnimeVietsubLogic {

    public static final String PORTAL_URL = AnimeVietsubParser.PORTAL_URL;
    public static final String DEFAULT_BASE_URL = AnimeVietsubParser.DEFAULT_BASE_URL;

    // ==========================================
    // STATIC DELEGATION METHODS
    // ==========================================

    public static String normalizeUrl(String url, String currentBaseUrl) {
        return AnimeVietsubParser.getInstance().normalizeUrl(url, currentBaseUrl);
    }

    public static List<MainPageSection> parseMainPage(String html) {
        return AnimeVietsubParser.getInstance().parseMainPage(html, DEFAULT_BASE_URL);
    }

    public static List<MainPageSection> parseMainPage(String html, String baseUrl) {
        return AnimeVietsubParser.getInstance().parseMainPage(html, baseUrl);
    }

    public static List<MovieItem> parseMovieList(String html, String baseUrl) {
        return AnimeVietsubParser.getInstance().parseMovieList(html, baseUrl);
    }

    public static MovieDetail parseMovieDetail(String html, String baseUrl) {
        return AnimeVietsubParser.getInstance().parseMovieDetail(html, baseUrl);
    }

    public static List<VideoLink> extractVideoLinks(String html, String slugOrData) {
        return AnimeVietsubParser.getInstance().extractVideoLinks(html, slugOrData);
    }

    public static String buildSearchUrl(String baseUrl, String query, int page) {
        return AnimeVietsubParser.getInstance().buildSearchUrl(baseUrl, query, page);
    }
}
