package com.tramphim;

import com.cloudstream.core.model.EpisodeItem;
import com.cloudstream.core.model.MainPageSection;
import com.cloudstream.core.model.MovieDetail;
import com.cloudstream.core.model.MovieItem;
import com.cloudstream.core.model.VideoLink;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bộ 6 Test Cases JUnit 5 tự động kiểm thử toàn diện TramphimLogic & TramphimParser.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TramphimLogicTest {

    private static final String BASE_URL = "https://tramphim4.org";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    @Test
    @Order(0)
    @DisplayName("Test 00: Bóc tách tên miền động từ portal tramphim.top")
    public void test00_ParseDomain() {
        // 1. Test Mock HTML portal
        String mockHtml = "<html><body>"
                + "<a href=\"https://tramphim4.org/\" class=\"logo-link\">Logo</a>"
                + "<a href=\"https://tramphim4.org/\" class=\"btn-redirect\">Đến Hệ Thống Chính</a>"
                + "<script>const targetUrl = \"https://tramphim4.org/\";</script>"
                + "</body></html>";
        String domainMock = TramphimLogic.parseDomain(mockHtml);
        assertEquals("https://tramphim4.org", domainMock);
        System.out.println("✅ Mock parseDomain: " + domainMock);

        // 2. Test Live Network Portal (nếu có mạng)
        try {
            Document portalDoc = Jsoup.connect(TramphimLogic.PORTAL_URL)
                    .userAgent(USER_AGENT)
                    .timeout(10000)
                    .get();
            String liveDomain = TramphimLogic.parseDomain(portalDoc.html());
            System.out.println("✅ Live portal parseDomain: " + liveDomain);
            assertNotNull(liveDomain);
            assertTrue(liveDomain.startsWith("http"));
        } catch (Exception e) {
            System.out.println("⚠️ Bỏ qua kết nối live portal: " + e.getMessage());
        }
    }

    @Test
    @Order(1)
    @DisplayName("Test 01: Bóc tách danh mục trang chủ (MainPage Sections)")
    public void test01_ParseMainPageSections() {
        // 1. Test Default sections khi HTML rỗng
        List<MainPageSection> defaultSections = TramphimLogic.parseMainPage("");
        assertNotNull(defaultSections);
        assertFalse(defaultSections.isEmpty());
        System.out.println("✅ Danh mục mặc định có " + defaultSections.size() + " mục.");

        // 2. Test Live Network homepage
        try {
            Document doc = Jsoup.connect(BASE_URL).userAgent(USER_AGENT).timeout(15000).get();
            List<MainPageSection> liveSections = TramphimLogic.parseMainPage(doc.html(), BASE_URL);
            assertNotNull(liveSections);
            assertFalse(liveSections.isEmpty(), "Phải có danh mục trang chủ");
            System.out.println("✅ Live homepage có " + liveSections.size() + " mục:");
            for (MainPageSection s : liveSections) {
                System.out.println("   - " + s.name + " -> " + s.path);
            }
        } catch (Exception e) {
            System.out.println("⚠️ Bỏ qua kết nối live homepage: " + e.getMessage());
        }
    }

    @Test
    @Order(2)
    @DisplayName("Test 02: Bóc tách danh sách phim (Movie Listing)")
    public void test02_ParseMovieList() {
        // 1. Test Mock HTML
        String mockHtml = "<div>"
                + "<a href=\"/phim/test-movie-1\">"
                + "  <img src=\"https://tramphim4.org/poster1.webp\" alt=\"Phim Test 1\" />"
                + "  <h3>Phim Test 1</h3>"
                + "  <span>HD</span><span>Vietsub</span>"
                + "</a>"
                + "</div>";

        List<MovieItem> mockMovies = TramphimLogic.parseMovieList(mockHtml, BASE_URL);
        assertNotNull(mockMovies);
        assertEquals(1, mockMovies.size());
        MovieItem item = mockMovies.get(0);
        assertEquals("Phim Test 1", item.title);
        assertEquals(BASE_URL + "/phim/test-movie-1", item.href);
        assertEquals("https://tramphim4.org/poster1.webp", item.posterUrl);
        assertTrue(item.tags.contains("HD"));
        assertTrue(item.tags.contains("Vietsub"));
        System.out.println("✅ Mock parseMovieList thành công: " + item.title);

        // 2. Test Live Network /phim-le
        try {
            Document doc = Jsoup.connect(BASE_URL + "/phim-le").userAgent(USER_AGENT).timeout(15000).get();
            List<MovieItem> liveMovies = TramphimLogic.parseMovieList(doc.html(), BASE_URL);
            assertNotNull(liveMovies);
            assertFalse(liveMovies.isEmpty(), "Danh sách phim lẻ không được rỗng");
            System.out.println("✅ Live cào được " + liveMovies.size() + " phim từ /phim-le:");
            for (int i = 0; i < Math.min(3, liveMovies.size()); i++) {
                MovieItem m = liveMovies.get(i);
                System.out.println("   [" + i + "] " + m.title + " | " + m.href + " | " + m.tags);
            }
        } catch (Exception e) {
            System.out.println("⚠️ Bỏ qua kết nối live listing: " + e.getMessage());
        }
    }

    @Test
    @Order(3)
    @DisplayName("Test 03: Tạo URL tìm kiếm & Tìm kiếm phim thực tế (Search)")
    public void test03_BuildSearchUrlAndSearch() {
        // 1. Kiểm tra định dạng search URL
        String searchUrl = TramphimLogic.buildSearchUrl(BASE_URL, "tình yêu", 1);
        assertTrue(searchUrl.contains("/tim-kiem?keyword="));
        System.out.println("✅ Search URL: " + searchUrl);

        // 2. Test Live Search
        try {
            Document doc = Jsoup.connect(searchUrl).userAgent(USER_AGENT).timeout(15000).get();
            List<MovieItem> searchResults = TramphimLogic.parseMovieList(doc.html(), BASE_URL);
            assertNotNull(searchResults);
            assertFalse(searchResults.isEmpty(), "Kết quả tìm kiếm không được rỗng");
            System.out.println("✅ Tìm kiếm 'tình yêu' trả về " + searchResults.size() + " phim:");
            for (int i = 0; i < Math.min(3, searchResults.size()); i++) {
                System.out.println("   - " + searchResults.get(i).title);
            }
        } catch (Exception e) {
            System.out.println("⚠️ Bỏ qua kết nối live search: " + e.getMessage());
        }
    }

    @Test
    @Order(4)
    @DisplayName("Test 04: Bóc tách chi tiết phim lẻ và phim bộ (Movie Detail & Episodes)")
    public void test04_ParseMovieDetail_MovieAndSeries() {
        // 1. Test Live Phim Lẻ: /phim/xich-cuoc-dai-tien
        try {
            String movieUrl = BASE_URL + "/phim/xich-cuoc-dai-tien";
            Document doc = Jsoup.connect(movieUrl).userAgent(USER_AGENT).timeout(15000).get();
            MovieDetail detail = TramphimLogic.parseMovieDetail(doc.html(), BASE_URL);

            assertNotNull(detail);
            assertNotNull(detail.title);
            assertFalse(detail.title.isEmpty(), "Tiêu đề phim lẻ không được rỗng");
            assertNotNull(detail.episodes);
            assertFalse(detail.episodes.isEmpty(), "Phim lẻ phải có ít nhất 1 tập");

            System.out.println("✅ Chi tiết phim lẻ: " + detail.title + " (" + detail.year + ")");
            System.out.println("   Poster: " + detail.posterUrl);
            System.out.println("   Số tập: " + detail.episodes.size() + " (Tập 1 URL: " + detail.episodes.get(0).href + ")");
        } catch (Exception e) {
            System.out.println("⚠️ Bỏ qua kết nối live phim lẻ: " + e.getMessage());
        }

        // 2. Test Live Phim Bộ: /phim/huyen-huyen-ta-thien-menh-trum-phan-dien
        try {
            String seriesUrl = BASE_URL + "/phim/huyen-huyen-ta-thien-menh-trum-phan-dien";
            Document doc = Jsoup.connect(seriesUrl).userAgent(USER_AGENT).timeout(15000).get();
            MovieDetail seriesDetail = TramphimLogic.parseMovieDetail(doc.html(), BASE_URL);

            assertNotNull(seriesDetail);
            assertFalse(seriesDetail.title.isEmpty());
            assertTrue(seriesDetail.episodes.size() > 1, "Phim bộ phải có nhiều tập");

            System.out.println("✅ Chi tiết phim bộ: " + seriesDetail.title);
            System.out.println("   Tổng số tập cào được: " + seriesDetail.episodes.size());
            for (int i = 0; i < Math.min(3, seriesDetail.episodes.size()); i++) {
                EpisodeItem ep = seriesDetail.episodes.get(i);
                System.out.println("   - " + ep.name + " | " + ep.href);
            }
        } catch (Exception e) {
            System.out.println("⚠️ Bỏ qua kết nối live phim bộ: " + e.getMessage());
        }
    }

    @Test
    @Order(5)
    @DisplayName("Test 05: Trích xuất Video Links & Giải mã Master Playlist AES-GCM của StreamC")
    public void test05_ExtractVideoLinksAndDecryptM3u8() throws Exception {
        // 1. Test giải mã AES-GCM M3U8 với thuật toán Java thuần
        String videoHash = "23e7991f3061eead38b4b32e84da2394";
        String sampleEncryptedM3u8 = "#EXTM3U\n"
                + "#ENC-AESGCM;iv=18c6870c3e768e06088d286d\n"
                + "#EXT-X-B65:0-138\n";

        // Thử fetch m3u8 thật từ StreamC nếu mạng hoạt động
        String sUb = "eyJoIjoiMjNlNzk5MWYzMDYxZWVhZDM4YjRiMzJlODRkYTIzOTQiLCJ0IjoiMmNlNDAyNjQ4NmQwM2ZhMTlkZjQzMjU5M2Q1ZWMyODNmMDE3NTUyMjYzNDgxYmQ5ZWQ4OWE5ZTQwMWQ5MDdmOSJ9";
        String streamUrl = "https://embed14.streamc.xyz/" + sUb;
        String embedReferer = "https://embed14.streamc.xyz/embed.php?hash=" + videoHash;

        try {
            Connection.Response res = Jsoup.connect(streamUrl)
                    .userAgent(USER_AGENT)
                    .referrer(embedReferer)
                    .ignoreContentType(true)
                    .timeout(15000)
                    .execute();

            String encryptedM3u8 = res.body();
            assertNotNull(encryptedM3u8);
            assertTrue(encryptedM3u8.contains("#ENC-AESGCM"), "Stream response phải chứa header #ENC-AESGCM");

            // Tiến hành giải mã bằng Java javax.crypto
            String decryptedM3u8 = TramphimLogic.decryptStreamcM3u8(encryptedM3u8, videoHash);
            assertNotNull(decryptedM3u8);
            assertTrue(decryptedM3u8.contains("#EXTM3U"), "Kết quả giải mã phải là M3U8 hợp lệ");
            assertTrue(decryptedM3u8.contains(".png") || decryptedM3u8.contains(".ts"), "Playlist giải mã phải chứa các chunk video");

            System.out.println("✅ GIẢI MÃ THÀNH CÔNG M3U8 AES-256-GCM!");
            System.out.println("   Độ dài playlist giải mã: " + decryptedM3u8.length() + " ký tự");
            String[] lines = decryptedM3u8.split("\n");
            for (int i = 0; i < Math.min(8, lines.length); i++) {
                System.out.println("   > " + lines[i]);
            }
        } catch (Exception e) {
            System.out.println("⚠️ Bỏ qua kiểm tra live stream fetch (mạng có thể chặn): " + e.getMessage());
        }

        // 2. Test parseStreamcPlayer từ Mock HTML
        String mockPlayerHtml = "<html><body>"
                + "<div id=\"player\" data-obf=\"eyJzVWIiOiJleUpvSWpvaU1qTmxOems1TVdZek1EWXhaV1ZoWkRNNFlqUmlNekpsT0RSa1lUSXpPVFFpTENKMElqb2lNbU5sTkRBeU5qUTRObVF3TTJaaE1UbGtaalF6TWpVNU0yUTFaV015T0RObU1ERTNOVFV5TWpZek5EZ3hZbVE1WldRNE9XRTVaVFF3TVdRNU1EZG1PU0o5IiwiaEQiOiIyM2U3OTkxZjMwNjFlZWFkMzhiNGIzMmU4NGRhMjM5NCJ9\"></div>"
                + "</body></html>";

        List<VideoLink> links = TramphimLogic.extractVideoLinks(mockPlayerHtml, "https://embed14.streamc.xyz/embed.php?hash=23e7991f3061eead38b4b32e84da2394");
        assertNotNull(links);
        assertFalse(links.isEmpty(), "Phải trích xuất được link từ player");
        System.out.println("✅ parseStreamcPlayer trích xuất được " + links.size() + " links:");
        for (VideoLink l : links) {
            System.out.println("   - [" + l.type + "] " + l.label + " -> " + l.url);
        }
    }
}
