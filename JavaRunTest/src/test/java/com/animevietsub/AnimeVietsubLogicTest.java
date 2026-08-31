package com.animevietsub;

import com.cloudstream.core.model.EpisodeItem;
import com.cloudstream.core.model.MainPageSection;
import com.cloudstream.core.model.MovieDetail;
import com.cloudstream.core.model.MovieItem;
import com.cloudstream.core.model.VideoLink;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test cho AnimeVietsubLogic chạy trực tiếp trên module JavaRunTest.
 * Bao gồm cả Test Mock HTML và Test Live Network kết nối thật.
 */
public class AnimeVietsubLogicTest {

    private final String BASE_URL = AnimeVietsubLogic.DEFAULT_BASE_URL;
    private final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private static final Map<String, String> COOKIES = new ConcurrentHashMap<>();

    /**
     * Helper tải HTML từ URL thật kèm cơ chế bắt tay Cookie chống 403.
     */
    private String fetchLiveHtml(String url) {
        try {
            if (COOKIES.isEmpty()) {
                Connection.Response handshake = Jsoup.connect(BASE_URL)
                        .userAgent(USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .ignoreHttpErrors(true)
                        .followRedirects(true)
                        .timeout(10000)
                        .execute();
                COOKIES.putAll(handshake.cookies());
            }

            Connection.Response res = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Referer", BASE_URL + "/")
                    .cookies(COOKIES)
                    .ignoreHttpErrors(true)
                    .followRedirects(true)
                    .timeout(10000)
                    .execute();

            COOKIES.putAll(res.cookies());
            return res.body();
        } catch (Exception e) {
            System.out.println("⚠️ Lỗi kết nối live (" + url + "): " + e.getMessage());
            return null;
        }
    }

    @Test
    @DisplayName("Test 0: Kiểm tra buildSearchUrl và normalizeUrl")
    public void testUrlHelpers() {
        String searchUrl1 = AnimeVietsubLogic.buildSearchUrl(BASE_URL, "conan", 1);
        assertEquals(BASE_URL + "/tim-kiem/conan/", searchUrl1);

        String searchUrl2 = AnimeVietsubLogic.buildSearchUrl(BASE_URL, "one piece", 2);
        assertEquals(BASE_URL + "/tim-kiem/one%20piece/trang-2.html", searchUrl2);

        String normalized1 = AnimeVietsubLogic.normalizeUrl("/phim/conan-a1/", BASE_URL);
        assertEquals(BASE_URL + "/phim/conan-a1/", normalized1);

        String normalized2 = AnimeVietsubLogic.normalizeUrl("https://animevietsub.work/phim/conan-a1/", BASE_URL);
        assertEquals(BASE_URL + "/phim/conan-a1/", normalized2);

        System.out.println("✅ Helper URLs verified: " + searchUrl1 + " | " + searchUrl2);
    }

    @Test
    @DisplayName("Test 1: Lấy danh sách Anime từ trang chủ / danh mục (Mock & Live)")
    public void testParseMovieList() {
        // 1. Mock HTML test
        String mockHtml = "<div class=\"TPostMv item\">"
                + "<a href=\"/phim/one-piece-a1/\" title=\"One Piece - Đảo Hải Tặc\">"
                + "<img src=\"https://animevietsub.li/poster.jpg\" />"
                + "<h2 class=\"Title\">One Piece - Đảo Hải Tặc</h2>"
                + "<span class=\"mli-eps\">Tập 1176</span>"
                + "</a></div>";

        List<MovieItem> mockList = AnimeVietsubLogic.parseMovieList(mockHtml, BASE_URL);
        assertNotNull(mockList);
        assertEquals(1, mockList.size());
        assertEquals("One Piece - Đảo Hải Tặc", mockList.get(0).title);
        assertEquals(BASE_URL + "/phim/one-piece-a1/", mockList.get(0).href);
        assertTrue(mockList.get(0).tags.contains("Tập 1176"));

        // 2. Live network test
        String liveHtml = fetchLiveHtml(BASE_URL + "/danh-sach/list-dang-chieu/");
        if (liveHtml != null && !liveHtml.isEmpty()) {
            List<MovieItem> liveList = AnimeVietsubLogic.parseMovieList(liveHtml, BASE_URL);
            assertNotNull(liveList);
            if (!liveList.isEmpty()) {
                System.out.println("✅ [LIVE] Parse thành công " + liveList.size() + " anime từ danh mục Đang Chiếu:");
                for (int i = 0; i < Math.min(3, liveList.size()); i++) {
                    MovieItem it = liveList.get(i);
                    System.out.println("   [" + (i + 1) + "] " + it.title + " -> " + it.href + " | Poster: " + it.posterUrl + " | Tags: " + it.tags);
                }
            } else {
                System.out.println("⚠️ [LIVE] Không tìm thấy anime nào (có thể do IP Datacenter bị Cloudflare chặn trên CI).");
            }
        }
    }

    @Test
    @DisplayName("Test 2: Tìm kiếm Anime theo từ khóa (Mock & Live)")
    public void testSearchAnime() {
        // 1. Mock HTML test
        String mockSearchHtml = "<ul>"
                + "<li class=\"item\">"
                + "<a href=\"https://animevietsub.li/phim/tham-tu-conan-a2/\" title=\"Thám Tử Lừng Danh Conan\">"
                + "<img src=\"https://animevietsub.li/conan.jpg\" />"
                + "<h3 class=\"title\">Thám Tử Lừng Danh Conan</h3>"
                + "<span class=\"status\">Full</span>"
                + "</a></li></ul>";

        List<MovieItem> searchResults = AnimeVietsubLogic.parseMovieList(mockSearchHtml, BASE_URL);
        assertNotNull(searchResults);
        assertEquals(1, searchResults.size());
        assertEquals("Thám Tử Lừng Danh Conan", searchResults.get(0).title);
        assertEquals("https://animevietsub.li/phim/tham-tu-conan-a2/", searchResults.get(0).href);

        // 2. Live search test
        String searchUrl = AnimeVietsubLogic.buildSearchUrl(BASE_URL, "conan", 1);
        String liveSearchHtml = fetchLiveHtml(searchUrl);
        if (liveSearchHtml != null && !liveSearchHtml.isEmpty()) {
            List<MovieItem> liveResults = AnimeVietsubLogic.parseMovieList(liveSearchHtml, BASE_URL);
            assertNotNull(liveResults);
            if (!liveResults.isEmpty()) {
                System.out.println("✅ [LIVE] Tìm kiếm 'conan' trả về: " + liveResults.size() + " kết quả.");
                for (int i = 0; i < Math.min(3, liveResults.size()); i++) {
                    MovieItem it = liveResults.get(i);
                    System.out.println("   [" + (i + 1) + "] " + it.title + " -> " + it.href);
                }
            } else {
                System.out.println("⚠️ [LIVE] Tìm kiếm live trả về rỗng (có thể do IP Datacenter bị Cloudflare chặn trên CI).");
            }
        }
    }

    @Test
    @DisplayName("Test 3: Parse chi tiết Anime và danh sách tập (Mock & Live)")
    public void testParseMovieDetail() {
        // 1. Mock Detail HTML
        String mockDetail = "<html><body>"
                + "<h1 class=\"Title\">Bleach: Huyết Chiến Ngàn Năm</h1>"
                + "<div class=\"Image\"><img src=\"https://animevietsub.li/bleach.jpg\" /></div>"
                + "<div class=\"Description\">Cuộc chiến cuối cùng của Shinigami...</div>"
                + "<span>Năm: 2024</span>"
                + "<div class=\"list-episode\">"
                + "<a class=\"btn-episode\" href=\"/phim/bleach-a5985/tap-01-115000.html\">01</a>"
                + "<a class=\"btn-episode\" href=\"/phim/bleach-a5985/tap-02-115001.html\">02</a>"
                + "</div>"
                + "</body></html>";

        MovieDetail detail = AnimeVietsubLogic.parseMovieDetail(mockDetail, BASE_URL);
        assertNotNull(detail);
        assertEquals("Bleach: Huyết Chiến Ngàn Năm", detail.title);
        assertEquals("https://animevietsub.li/bleach.jpg", detail.posterUrl);
        assertEquals("Cuộc chiến cuối cùng của Shinigami...", detail.plot);
        assertEquals(2024, detail.year);
        assertEquals(2, detail.episodes.size());
        assertEquals("Tập 1", detail.episodes.get(0).name);
        assertEquals(BASE_URL + "/phim/bleach-a5985/tap-01-115000.html", detail.episodes.get(0).href);
        System.out.println("✅ [MOCK] Detail parse thành công: " + detail.title + " | " + detail.episodes.size() + " tập.");

        // 2. Live Detail test (One Piece)
        String liveDetailHtml = fetchLiveHtml(BASE_URL + "/phim/one-piece-dao-hai-tac-a1/");
        if (liveDetailHtml != null && !liveDetailHtml.isEmpty()) {
            MovieDetail liveDetail = AnimeVietsubLogic.parseMovieDetail(liveDetailHtml, BASE_URL);
            assertNotNull(liveDetail);
            if (!liveDetail.title.isEmpty()) {
                System.out.println("✅ [LIVE] Parse chi tiết: " + liveDetail.title + " | Năm: " + liveDetail.year + " | Thể loại: " + liveDetail.tags + " | Số tập: " + liveDetail.episodes.size());
                if (!liveDetail.episodes.isEmpty()) {
                    EpisodeItem ep1 = liveDetail.episodes.get(0);
                    System.out.println("   Tập đầu tiên: " + ep1.name + " -> " + ep1.href);
                }
            } else {
                System.out.println("⚠️ [LIVE] Parse chi tiết rỗng (có thể do IP Datacenter bị Cloudflare chặn trên CI).");
            }
        }
    }

    @Test
    @DisplayName("Test 4: Trích xuất Link Video phát (window.PLAYER_DATA / M3U8)")
    public void testExtractVideoLinks() {
        // 1. Mock Watch HTML với window.PLAYER_DATA
        String mockWatch = "<script>"
                + "window.PLAYER_DATA = {\"_fxStatus\":1,\"success\":1,\"title\":\"AnimeVsub\",\"link\":\"https://storage.googleapiscdn.com/player/abcxyz123\",\"playTech\":\"iframe\",\"episode_id\":\"115468\"};"
                + "</script>";

        List<VideoLink> links = AnimeVietsubLogic.extractVideoLinks(mockWatch, "tap-1176");
        assertNotNull(links);
        assertFalse(links.isEmpty(), "Phải trích xuất được ít nhất 1 link từ PLAYER_DATA");
        assertEquals("https://storage.googleapiscdn.com/player/abcxyz123", links.get(0).url);
        assertEquals("Server DU", links.get(0).serverName);
        System.out.println("✅ [MOCK] Trích xuất VideoLink thành công: " + links.get(0).url);

        // 2. Live Watch HTML test (Lấy trang tập phim live và extract link)
        String liveDetailHtml = fetchLiveHtml(BASE_URL + "/phim/one-piece-dao-hai-tac-a1/");
        if (liveDetailHtml != null && !liveDetailHtml.isEmpty()) {
            MovieDetail liveDetail = AnimeVietsubLogic.parseMovieDetail(liveDetailHtml, BASE_URL);
            if (!liveDetail.episodes.isEmpty()) {
                String firstEpUrl = liveDetail.episodes.get(0).href;
                String epWatchHtml = fetchLiveHtml(firstEpUrl);
                if (epWatchHtml != null && !epWatchHtml.isEmpty()) {
                    List<VideoLink> liveLinks = AnimeVietsubLogic.extractVideoLinks(epWatchHtml, firstEpUrl);
                    if (!liveLinks.isEmpty()) {
                        System.out.println("✅ [LIVE] Trích xuất link từ tập (" + firstEpUrl + "): " + liveLinks.size() + " link tìm thấy");
                        for (VideoLink vl : liveLinks) {
                            System.out.println("   - [" + vl.serverName + "] " + vl.label + " (" + vl.type + "): " + vl.url);
                        }
                    } else {
                        System.out.println("⚠️ [LIVE] Trích xuất link rỗng (có thể do IP Datacenter bị Cloudflare chặn trên CI).");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Test 5: Parse danh mục trang chủ (MainPage Sections) (Mock & Live)")
    public void testParseMainPage() {
        // 1. Mock HTML test
        String mockNav = "<ul class=\"nav\">"
                + "<li><a href=\"/danh-sach/list-dang-chieu/\">Anime Đang Chiếu</a></li>"
                + "<li><a href=\"/danh-sach/list-tron-bo/\">Anime Trọn Bộ</a></li>"
                + "<li><a href=\"/bang-xep-hang.html\">Top Anime</a></li>"
                + "<li><a href=\"/the-loai/hanh-dong/\">Hành Động</a></li>"
                + "</ul>";

        List<MainPageSection> sections = AnimeVietsubLogic.parseMainPage(mockNav, BASE_URL);
        assertNotNull(sections);
        assertEquals(4, sections.size());
        assertEquals("Anime Đang Chiếu", sections.get(0).name);
        assertEquals("/danh-sach/list-dang-chieu/", sections.get(0).path);
        System.out.println("✅ [MOCK] Parse MainPage Sections thành công: " + sections.size() + " mục.");

        // 2. Live Homepage Navbar test
        String liveHomeHtml = fetchLiveHtml(BASE_URL);
        if (liveHomeHtml != null && !liveHomeHtml.isEmpty()) {
            List<MainPageSection> liveSections = AnimeVietsubLogic.parseMainPage(liveHomeHtml, BASE_URL);
            assertNotNull(liveSections);
            if (!liveSections.isEmpty()) {
                System.out.println("✅ [LIVE] Bóc tách " + liveSections.size() + " mục danh mục từ trang chủ live:");
                for (int i = 0; i < Math.min(5, liveSections.size()); i++) {
                    MainPageSection s = liveSections.get(i);
                    System.out.println("   [" + (i + 1) + "] " + s.name + " -> " + s.path);
                }
            } else {
                System.out.println("⚠️ [LIVE] Parse danh mục trang chủ rỗng (có thể do IP Datacenter bị Cloudflare chặn trên CI).");
            }
        }
    }
}
