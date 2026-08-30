package com.vieflix;

import com.cloudstream.core.model.EpisodeItem;
import com.cloudstream.core.model.MainPageSection;
import com.cloudstream.core.model.MovieDetail;
import com.cloudstream.core.model.MovieItem;
import com.cloudstream.core.model.VideoLink;
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
    @DisplayName("Test 0: Trích xuất tên miền động từ vieflix.com / constan.js")
    public void testParseDomain() {
        // 1. Test với HTML thật từ portal vieflix.com
        try {
            Document doc = Jsoup.connect(VieflixLogic.PORTAL_URL)
                    .userAgent(USER_AGENT)
                    .timeout(10000)
                    .get();
            String domainFromHtml = VieflixLogic.parseDomain(doc.html());
            System.out.println("✅ Domain lấy từ vieflix.com: " + domainFromHtml);
            assertNotNull(domainFromHtml);
            assertTrue(domainFromHtml.startsWith("http"), "Domain phải bắt đầu bằng http/https");
        } catch (Exception e) {
            System.out.println("⚠️ Không kết nối được vieflix.com (có thể do mạng): " + e.getMessage());
        }

        // 2. Test với mock HTML (giả lập trường hợp link đổi sang domain mới)
        String mockHtml = "<div class=\"input-group\">"
                + "<a href=\"https://vieflix.top\" id=\"accessBtn\" class=\"btn-access\">Truy cập →</a>"
                + "</div>";
        String domainMock = VieflixLogic.parseDomain(mockHtml);
        assertEquals("https://vieflix.top", domainMock);

        // 3. Test với mock JS config
        String mockJs = "var SITE_CONFIG = { TARGET_DOMAIN: 'https://vieflix.top' };";
        String domainJs = VieflixLogic.parseDomain(mockJs);
        assertEquals("https://vieflix.top", domainJs);
    }

    @Test
    @DisplayName("Test 0.1: Bóc tách danh mục trang chủ động (MainPage Sections)")
    public void testParseMainPage() throws IOException {
        // 1. Test với HTML mock (đoạn HTML mẫu từ người dùng)
        String mockHtml = "<div class=\"flex items-center justify-between\">"
                + "<h2 class=\"text-xl font-extrabold\">Phim Mới Cập Nhật</h2>"
                + "<a href=\"/duyet-tim\"><span>Xem toàn bộ</span></a>"
                + "</div>"
                + "<div class=\"flex items-center justify-between\">"
                + "<h2 class=\"text-xl font-extrabold\">Phim Chiếu Rạp Mới Nhất</h2>"
                + "<a href=\"/duyet-tim?isChieuRap=true&amp;sortField=year\"><span>Xem toàn bộ</span></a>"
                + "</div>";

        List<MainPageSection> mockSections = VieflixLogic.parseMainPage(mockHtml);
        assertNotNull(mockSections);
        assertEquals(2, mockSections.size());
        assertEquals("Phim Mới Cập Nhật", mockSections.get(0).name);
        assertEquals("/duyet-tim", mockSections.get(0).path);
        assertEquals("Phim Chiếu Rạp Mới Nhất", mockSections.get(1).name);
        assertEquals("/duyet-tim?isChieuRap=true&sortField=year", mockSections.get(1).path);
        System.out.println("✅ Mock HTML parse thành công: " + mockSections.size() + " mục.");

        // 2. Test với trang chủ live
        try {
            Document doc = Jsoup.connect(BASE_URL).userAgent(USER_AGENT).timeout(15000).get();
            List<MainPageSection> liveSections = VieflixLogic.parseMainPage(doc.html());
            assertNotNull(liveSections);
            assertFalse(liveSections.isEmpty(), "Phải bóc tách được ít nhất 1 mục trang chủ");

            System.out.println("✅ Bóc tách " + liveSections.size() + " mục trang chủ live:");
            for (MainPageSection s : liveSections) {
                System.out.println("   [" + s.name + "] -> " + s.path);
            }
        } catch (Exception e) {
            System.out.println("⚠️ Không thể kết nối trang chủ live: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test 1: Lấy danh sách phim từ trang duyệt tìm / trang chủ")
    public void testParseMovieList() {
        // 1. Mock HTML test
        String mockHtml = "<div class=\"movie-item\">"
                + "<a href=\"/phim/test-movie\">"
                + "<img src=\"https://vieflix.top/poster.jpg\" />"
                + "<h3>Phim Test Mẫu</h3>"
                + "<span class=\"uppercase\">LT</span><span class=\"uppercase\">VS</span>"
                + "</a></div>";
        List<MovieItem> mockMovies = VieflixLogic.parseMovieList(mockHtml, BASE_URL);
        assertNotNull(mockMovies);
        assertEquals(1, mockMovies.size());
        assertEquals("Phim Test Mẫu", mockMovies.get(0).title);
        assertEquals(BASE_URL + "/phim/test-movie", mockMovies.get(0).href);
        assertTrue(mockMovies.get(0).tags.contains("LT"));
        assertTrue(mockMovies.get(0).tags.contains("VS"));

        // 2. Live network test
        try {
            String url = BASE_URL + "/duyet-tim?sortField=year&page=1";
            System.out.println("GET: " + url);

            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(20000)
                    .get();

            List<MovieItem> movies = VieflixLogic.parseMovieList(doc.html(), BASE_URL);
            assertNotNull(movies);
            assertFalse(movies.isEmpty());

            MovieItem firstMovie = movies.get(0);
            System.out.println("✅ Phim đầu tiên: " + firstMovie.title + " | Link: " + firstMovie.href);
            assertNotNull(firstMovie.title);
            assertFalse(firstMovie.title.trim().isEmpty());
            assertNotNull(firstMovie.href);
            assertTrue(firstMovie.href.startsWith("http"));
        } catch (Exception e) {
            System.out.println("⚠️ Bỏ qua live network test do lỗi mạng/timeout: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test 2: Tìm kiếm phim theo từ khóa và bộ lọc thông minh")
    public void testSearchMovie() {
        // 1. Test buildSearchUrl (Smart Filter Query Parser)
        String url1 = VieflixLogic.buildSearchUrl(BASE_URL, "conan", 1);
        assertEquals(BASE_URL + "/duyet-tim?search=conan&page=1", url1);

        String url2 = VieflixLogic.buildSearchUrl(BASE_URL, "conan #thuyetminh", 1);
        assertTrue(url2.contains("search=conan"));
        assertTrue(url2.contains("lang=thuyet-minh"));
        assertTrue(url2.contains("page=1"));

        String url3 = VieflixLogic.buildSearchUrl(BASE_URL, "#chieurap #hanquoc nam:2024", 2);
        assertTrue(url3.contains("isChieuRap=true"));
        assertTrue(url3.contains("country=han-quoc"));
        assertTrue(url3.contains("year=2024"));
        assertTrue(url3.contains("page=2"));

        String url4 = VieflixLogic.buildSearchUrl(BASE_URL, "#cotrang #trungquoc #phimbo", 1);
        assertTrue(url4.contains("category=co-trang"));
        assertTrue(url4.contains("country=trung-quoc"));
        assertTrue(url4.contains("typeList=phim-bo"));

        String url5 = VieflixLogic.buildSearchUrl(BASE_URL, "/duyet-tim?category=kinh-di", 3);
        assertEquals(BASE_URL + "/duyet-tim?category=kinh-di&page=3", url5);

        System.out.println("✅ buildSearchUrl test thành công:");
        System.out.println("   [1] conan -> " + url1);
        System.out.println("   [2] conan #thuyetminh -> " + url2);
        System.out.println("   [3] #chieurap #hanquoc nam:2024 -> " + url3);

        // 2. Live network test
        try {
            String query = "conan";
            String searchUrl = VieflixLogic.buildSearchUrl(BASE_URL, query, 1);
            System.out.println("GET Search: " + searchUrl);

            Document doc = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .timeout(20000)
                    .get();

            List<MovieItem> searchResults = VieflixLogic.parseMovieList(doc.html(), BASE_URL);
            assertNotNull(searchResults);
            assertFalse(searchResults.isEmpty());

            System.out.println("✅ Tìm thấy " + searchResults.size() + " kết quả cho từ khóa '" + query + "':");
            for (int i = 0; i < Math.min(3, searchResults.size()); i++) {
                MovieItem item = searchResults.get(i);
                System.out.println("   [" + (i + 1) + "] " + item.title + " -> " + item.href);
            }
        } catch (Exception e) {
            System.out.println("⚠️ Bỏ qua live search test do lỗi mạng/timeout: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test 3: Lấy chi tiết thông tin phim và danh sách tập")
    public void testParseMovieDetail() {
        // 1. Mock HTML test
        String mockDetailHtml = "<html><body>"
                + "<h1 class=\"text-2xl\">Thám Tử Lừng Danh Conan</h1>"
                + "<span>Năm phát hành: 1996</span>"
                + "<span>Thời lượng: 25 phút</span>"
                + "<div class=\"tag\">Anime</div><div class=\"tag\">Trinh Thám</div>"
                + "<p id=\"synopsis\">Nội dung phim Conan...</p>"
                + "<a href=\"/phim/tham-tu-lung-danh-conan/tap-1?sv=0&lang=0\">Tập 1</a>"
                + "<a href=\"/phim/tham-tu-lung-danh-conan/tap-2?sv=0&lang=0\">Tập 2</a>"
                + "</body></html>";
        MovieDetail mockDetail = VieflixLogic.parseMovieDetail(mockDetailHtml, BASE_URL);
        assertNotNull(mockDetail);
        assertEquals("Thám Tử Lừng Danh Conan", mockDetail.title);
        assertEquals(1996, mockDetail.year);
        assertEquals(25, mockDetail.duration);
        assertFalse(mockDetail.episodes.isEmpty());
        assertEquals(2, mockDetail.episodes.size());

        // 2. Live network test
        try {
            String listUrl = BASE_URL + "/duyet-tim?sortField=year&page=1";
            Document listDoc = Jsoup.connect(listUrl).userAgent(USER_AGENT).timeout(20000).get();
            List<MovieItem> movies = VieflixLogic.parseMovieList(listDoc.html(), BASE_URL);
            if (!movies.isEmpty()) {
                String movieUrl = movies.get(0).href;
                System.out.println("GET Detail: " + movieUrl);

                Document detailDoc = Jsoup.connect(movieUrl).userAgent(USER_AGENT).timeout(20000).get();
                MovieDetail detail = VieflixLogic.parseMovieDetail(detailDoc.html(), BASE_URL);

                assertNotNull(detail);
                assertNotNull(detail.title);
                assertFalse(detail.title.trim().isEmpty());
                assertNotNull(detail.episodes);
                assertFalse(detail.episodes.isEmpty());

                System.out.println("✅ Tên phim: " + detail.title);
                System.out.println("✅ Năm: " + detail.year + " | Thời lượng: " + detail.duration + " phút");
                System.out.println("✅ Thể loại: " + detail.tags);
                System.out.println("✅ Số tập: " + detail.episodes.size());
            }
        } catch (Exception e) {
            System.out.println("⚠️ Bỏ qua live movie detail test do lỗi mạng/timeout: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test 4: Trích xuất Link Video phát (M3U8 / Embed) cho tập phim")
    public void testExtractVideoLinks() {
        // 1. Mock HTML test
        String mockPlayerHtml = "<div id=\"player-holder\">"
                + "<iframe src=\"https://v7.kkphimplayer7.com/embed/test.m3u8\"></iframe>"
                + "</div>";
        List<VideoLink> mockLinks = VieflixLogic.extractVideoLinks(mockPlayerHtml, "tap-1");
        assertNotNull(mockLinks);

        // 2. Live network test
        try {
            String listUrl = BASE_URL + "/duyet-tim?sortField=year&page=1";
            Document listDoc = Jsoup.connect(listUrl).userAgent(USER_AGENT).timeout(20000).get();
            List<MovieItem> movies = VieflixLogic.parseMovieList(listDoc.html(), BASE_URL);
            if (!movies.isEmpty()) {
                String movieUrl = movies.get(0).href;
                Document detailDoc = Jsoup.connect(movieUrl).userAgent(USER_AGENT).timeout(20000).get();
                MovieDetail detail = VieflixLogic.parseMovieDetail(detailDoc.html(), BASE_URL);
                if (!detail.episodes.isEmpty()) {
                    EpisodeItem firstEp = detail.episodes.get(0);
                    String href = firstEp.href;
                    int tapIdx = href.lastIndexOf("/tap-");
                    String slug = (tapIdx >= 0) ? href.substring(tapIdx + 1) : "";
                    if (slug.contains("?")) slug = slug.substring(0, slug.indexOf("?"));

                    System.out.println("Trích xuất link cho slug: '" + slug + "' từ: " + movieUrl);
                    List<VideoLink> links = VieflixLogic.extractVideoLinks(detailDoc.html(), slug);
                    assertNotNull(links);

                    for (VideoLink link : links) {
                        System.out.println("🎬 [STREAM LINK] " + link.label + " (" + link.type + "): " + link.url);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ Bỏ qua live extract video links test do lỗi mạng/timeout: " + e.getMessage());
        }
    }
}
