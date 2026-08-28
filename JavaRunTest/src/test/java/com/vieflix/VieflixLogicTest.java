package com.vieflix;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test cho VieflixLogic chạy trực tiếp trên module JavaRunTest.
 */
public class VieflixLogicTest {

    private final String BASE_URL = "https://vieflix.top";
    private final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Test
    @DisplayName("Test 1: Lấy danh sách phim từ trang duyệt tìm / trang chủ")
    public void testParseMovieList() throws IOException {
        String url = BASE_URL + "/duyet-tim?sortField=year&page=1";
        System.out.println("GET: " + url);

        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(15000)
                .get();

        List<VieflixLogic.MovieItem> movies = VieflixLogic.parseMovieList(doc.html(), BASE_URL);

        // Assertions
        assertNotNull(movies, "Danh sách phim không được null");
        assertFalse(movies.isEmpty(), "Danh sách phim không được rỗng");

        VieflixLogic.MovieItem firstMovie = movies.get(0);
        System.out.println("✅ Phim đầu tiên: " + firstMovie.title + " | Link: " + firstMovie.href);

        assertNotNull(firstMovie.title, "Tên phim không được null");
        assertFalse(firstMovie.title.trim().isEmpty(), "Tên phim không được rỗng");
        assertNotNull(firstMovie.href, "Href không được null");
        assertTrue(firstMovie.href.startsWith("http"), "Href phải là URL tuyệt đối");
    }

    @Test
    @DisplayName("Test 2: Tìm kiếm phim theo từ khóa")
    public void testSearchMovie() throws IOException {
        String query = "conan";
        String searchUrl = BASE_URL + "/duyet-tim?search=" + query;
        System.out.println("GET Search: " + searchUrl);

        Document doc = Jsoup.connect(searchUrl)
                .userAgent(USER_AGENT)
                .timeout(15000)
                .get();

        List<VieflixLogic.MovieItem> searchResults = VieflixLogic.parseMovieList(doc.html(), BASE_URL);

        assertNotNull(searchResults, "Kết quả tìm kiếm không được null");
        assertFalse(searchResults.isEmpty(), "Phải tìm thấy ít nhất 1 kết quả cho từ khóa: " + query);

        System.out.println("✅ Tìm thấy " + searchResults.size() + " kết quả cho từ khóa '" + query + "':");
        for (int i = 0; i < Math.min(3, searchResults.size()); i++) {
            VieflixLogic.MovieItem item = searchResults.get(i);
            System.out.println("   [" + (i + 1) + "] " + item.title + " -> " + item.href);
        }
    }

    @Test
    @DisplayName("Test 3: Lấy chi tiết thông tin phim và danh sách tập")
    public void testParseMovieDetail() throws IOException {
        // Lấy 1 phim từ danh sách để test chi tiết
        String listUrl = BASE_URL + "/duyet-tim?sortField=year&page=1";
        Document listDoc = Jsoup.connect(listUrl).userAgent(USER_AGENT).timeout(15000).get();
        List<VieflixLogic.MovieItem> movies = VieflixLogic.parseMovieList(listDoc.html(), BASE_URL);
        assertFalse(movies.isEmpty(), "Không lấy được danh sách phim mẫu để test detail");

        String movieUrl = movies.get(0).href;
        System.out.println("GET Detail: " + movieUrl);

        Document detailDoc = Jsoup.connect(movieUrl).userAgent(USER_AGENT).timeout(15000).get();
        VieflixLogic.MovieDetail detail = VieflixLogic.parseMovieDetail(detailDoc.html(), BASE_URL);

        // Assertions
        assertNotNull(detail, "MovieDetail không được null");
        assertNotNull(detail.title, "Tên phim không được null");
        assertFalse(detail.title.trim().isEmpty(), "Tên phim không được rỗng");
        assertNotNull(detail.episodes, "Danh sách tập phim không được null");
        assertFalse(detail.episodes.isEmpty(), "Phim phải có ít nhất 1 tập");

        System.out.println("✅ Tên phim: " + detail.title);
        System.out.println("✅ Năm: " + detail.year + " | Thời lượng: " + detail.duration + " phút");
        System.out.println("✅ Thể loại: " + detail.tags);
        System.out.println("✅ Số tập: " + detail.episodes.size());
        System.out.println("   Tập 1: " + detail.episodes.get(0).name + " -> " + detail.episodes.get(0).href);
    }

    @Test
    @DisplayName("Test 4: Trích xuất Link Video phát (M3U8 / Embed) cho tập phim")
    public void testExtractVideoLinks() throws IOException {
        // Lấy trang phim
        String listUrl = BASE_URL + "/duyet-tim?sortField=year&page=1";
        Document listDoc = Jsoup.connect(listUrl).userAgent(USER_AGENT).timeout(15000).get();
        List<VieflixLogic.MovieItem> movies = VieflixLogic.parseMovieList(listDoc.html(), BASE_URL);
        assertFalse(movies.isEmpty());

        String movieUrl = movies.get(0).href;
        Document detailDoc = Jsoup.connect(movieUrl).userAgent(USER_AGENT).timeout(15000).get();
        VieflixLogic.MovieDetail detail = VieflixLogic.parseMovieDetail(detailDoc.html(), BASE_URL);
        assertFalse(detail.episodes.isEmpty(), "Không có tập phim nào để test link");

        // Lấy tập đầu tiên và trích xuất slug
        VieflixLogic.EpisodeItem firstEp = detail.episodes.get(0);
        String href = firstEp.href;
        int tapIdx = href.lastIndexOf("/tap-");
        String slug = (tapIdx >= 0) ? href.substring(tapIdx + 1) : "";
        if (slug.contains("?")) slug = slug.substring(0, slug.indexOf("?"));

        System.out.println("Trích xuất link cho slug: '" + slug + "' từ: " + movieUrl);

        List<VieflixLogic.VideoLink> links = VieflixLogic.extractVideoLinks(detailDoc.html(), slug);

        // Assertions
        assertNotNull(links, "Danh sách video link không được null");
        assertFalse(links.isEmpty(), "Phải trích xuất được ít nhất 1 link video");

        for (VieflixLogic.VideoLink link : links) {
            System.out.println("🎬 [STREAM LINK] " + link.label + " (" + link.type + "): " + link.url);
            assertNotNull(link.url, "URL link video không được null");
            assertNotNull(link.type, "Loại link không được null");
        }
    }
}
