package com.chophim;

import com.cloudstream.core.model.EpisodeItem;
import com.cloudstream.core.model.MainPageSection;
import com.cloudstream.core.model.MovieDetail;
import com.cloudstream.core.model.MovieItem;
import com.cloudstream.core.model.VideoLink;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChophimLogicTest - Test suite kiểm thử độc lập cho logic bóc tách ChoPhim.app.
 * Chạy siêu tốc trên module JavaRunTest bằng JUnit 5.
 */
public class ChophimLogicTest {

    private final String BASE_URL = ChophimLogic.DEFAULT_BASE_URL;
    private final String API_BASE_URL = ChophimLogic.API_BASE_URL;
    private final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Test
    @DisplayName("Test 1: Bóc tách danh mục trang chủ (MainPage Sections)")
    public void testParseMainPage() {
        List<MainPageSection> sections = ChophimLogic.parseMainPage("", BASE_URL);
        assertNotNull(sections);
        assertFalse(sections.isEmpty(), "Danh mục trang chủ không được rỗng");
        assertTrue(sections.stream().anyMatch(s -> s.path.contains("phim-moi")));
        assertTrue(sections.stream().anyMatch(s -> s.path.contains("phim-bo")));
        assertTrue(sections.stream().anyMatch(s -> s.path.contains("phim-le")));

        System.out.println("✅ Đã tạo " + sections.size() + " danh mục trang chủ:");
        for (MainPageSection s : sections) {
            System.out.println("   [" + s.name + "] -> " + s.path);
        }
    }

    @Test
    @DisplayName("Test 2: Lấy danh sách phim từ JSON API và HTML")
    public void testParseMovieList() {
        // 1. Mock JSON test
        String mockJson = "{\n" +
                "  \"status\": \"success\",\n" +
                "  \"data\": {\n" +
                "    \"items\": [\n" +
                "      {\n" +
                "        \"name\": \"Doraemon 2026\",\n" +
                "        \"slug\": \"doraemon-2026\",\n" +
                "        \"poster_url\": \"https://phimimg.com/poster.jpg\",\n" +
                "        \"quality\": \"FHD\",\n" +
                "        \"lang\": \"Vietsub\",\n" +
                "        \"lang_key\": [\"vs\"],\n" +
                "        \"episode_current\": \"Tập 1\"\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}";

        List<MovieItem> mockItems = ChophimLogic.parseMovieList(mockJson, BASE_URL);
        assertNotNull(mockItems);
        assertEquals(1, mockItems.size());
        MovieItem item = mockItems.get(0);
        assertEquals("Doraemon 2026", item.title);
        assertEquals("https://chophim.app/phim/doraemon-2026", item.href);
        assertEquals("https://phimimg.com/poster.jpg", item.posterUrl);
        assertTrue(item.tags.contains("VS"));
        assertTrue(item.tags.contains("FHD"));
        System.out.println("✅ Mock JSON test passed: " + item.title + " | Tags: " + item.tags);

        // 2. Live API test
        try {
            String apiUrl = API_BASE_URL + "/danh-sach/phim-moi?page=1";
            String json = Jsoup.connect(apiUrl)
                    .userAgent(USER_AGENT)
                    .ignoreContentType(true)
                    .timeout(10000)
                    .execute()
                    .body();

            List<MovieItem> liveItems = ChophimLogic.parseMovieList(json, BASE_URL);
            assertNotNull(liveItems);
            assertFalse(liveItems.isEmpty(), "Live API phải trả về danh sách phim");
            System.out.println("✅ Live API test passed: Bóc tách thành công " + liveItems.size() + " phim mới cập nhật.");
            System.out.println("   Phim mẫu: " + liveItems.get(0).title + " (" + liveItems.get(0).href + ")");
        } catch (Exception e) {
            System.out.println("⚠️ Không kết nối được Live API (có thể do mạng): " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test 3: Tìm kiếm phim theo từ khóa (Search)")
    public void testSearch() {
        // 1. Kiểm tra buildSearchUrl
        String searchUrl = ChophimLogic.buildSearchUrl(BASE_URL, "doraemon", 1);
        assertTrue(searchUrl.contains("tim-kiem?keyword=doraemon"));

        String tagUrl = ChophimLogic.buildSearchUrl(BASE_URL, "naruto #phimbo", 1);
        assertTrue(tagUrl.contains("danh-sach/phim-bo"));

        // 2. Live search test
        try {
            String apiUrl = API_BASE_URL + "/tim-kiem?keyword=doraemon&page=1";
            String json = Jsoup.connect(apiUrl)
                    .userAgent(USER_AGENT)
                    .ignoreContentType(true)
                    .timeout(10000)
                    .execute()
                    .body();

            List<MovieItem> searchResults = ChophimLogic.parseMovieList(json, BASE_URL);
            assertNotNull(searchResults);
            assertFalse(searchResults.isEmpty(), "Tìm kiếm 'doraemon' phải trả về kết quả");
            System.out.println("✅ Live Search test passed: Tìm thấy " + searchResults.size() + " phim với từ khóa 'doraemon'.");
            System.out.println("   Kết quả đầu: " + searchResults.get(0).title);
        } catch (Exception e) {
            System.out.println("⚠️ Live Search test bị gián đoạn mạng: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test 4: Bóc tách chi tiết phim và danh sách tập (Movie Detail & Episodes)")
    public void testParseMovieDetail() {
        // 1. Mock JSON test
        String mockDetailJson = "{\n" +
                "  \"status\": \"success\",\n" +
                "  \"data\": {\n" +
                "    \"item\": {\n" +
                "      \"name\": \"Bay Vào Tim Anh\",\n" +
                "      \"slug\": \"bay-vao-trai-tim-anh\",\n" +
                "      \"content\": \"<p>Phim tình cảm lãng mạn hàng không...</p>\",\n" +
                "      \"poster_url\": \"https://image.tmdb.org/poster.jpg\",\n" +
                "      \"year\": 2026,\n" +
                "      \"time\": \"45 phút/tập\",\n" +
                "      \"category\": [{\"name\": \"Chính Kịch\"}, {\"name\": \"Tâm Lý\"}],\n" +
                "      \"episodes\": [\n" +
                "        {\n" +
                "          \"server_name\": \"Vietsub (KK)\",\n" +
                "          \"server_data\": [\n" +
                "            {\"name\": \"Tập 01\", \"slug\": \"tap-01\", \"link_m3u8\": \"https://v7.kkphim.com/tap1.m3u8\"},\n" +
                "            {\"name\": \"Tập 02\", \"slug\": \"tap-02\", \"link_m3u8\": \"https://v7.kkphim.com/tap2.m3u8\"}\n" +
                "          ]\n" +
                "        },\n" +
                "        {\n" +
                "          \"server_name\": \"Thuyết Minh (KK)\",\n" +
                "          \"server_data\": [\n" +
                "            {\"name\": \"Tập 01\", \"slug\": \"tap-01\", \"link_m3u8\": \"https://v7.kkphim.com/tap1_tm.m3u8\"}\n" +
                "          ]\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  }\n" +
                "}";

        MovieDetail detail = ChophimLogic.parseMovieDetail(mockDetailJson, BASE_URL);
        assertNotNull(detail);
        assertEquals("Bay Vào Tim Anh", detail.title);
        assertEquals(2026, detail.year);
        assertEquals(45, detail.duration);
        assertEquals("Phim tình cảm lãng mạn hàng không...", detail.plot);
        assertEquals(2, detail.tags.size());
        assertEquals(2, detail.episodes.size());
        assertEquals("Tập 01", detail.episodes.get(0).name);
        assertEquals("Tập 02", detail.episodes.get(1).name);
        System.out.println("✅ Mock Detail test passed: " + detail.title + " | " + detail.episodes.size() + " tập.");

        // 2. Live detail test
        try {
            String apiUrl = API_BASE_URL + "/phim/bay-vao-trai-tim-anh";
            String json = Jsoup.connect(apiUrl)
                    .userAgent(USER_AGENT)
                    .ignoreContentType(true)
                    .timeout(10000)
                    .execute()
                    .body();

            MovieDetail liveDetail = ChophimLogic.parseMovieDetail(json, BASE_URL);
            assertNotNull(liveDetail);
            assertEquals("Bay Vào Tim Anh", liveDetail.title);
            assertFalse(liveDetail.episodes.isEmpty(), "Phim bộ phải có danh sách tập");
            System.out.println("✅ Live Detail test passed: " + liveDetail.title + " | " + liveDetail.episodes.size() + " tập.");
        } catch (Exception e) {
            System.out.println("⚠️ Live Detail test bị gián đoạn mạng: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test 5: Trích xuất link phát video (Multi-Server M3U8 & Embed)")
    public void testExtractVideoLinks() {
        String mockDetailJson = "{\n" +
                "  \"data\": {\n" +
                "    \"item\": {\n" +
                "      \"episodes\": [\n" +
                "        {\n" +
                "          \"server_name\": \"Vietsub (KK)\",\n" +
                "          \"server_data\": [\n" +
                "            {\"name\": \"Tập 01\", \"slug\": \"tap-01\", \"link_m3u8\": \"https://v7.kkphim.com/tap1.m3u8\", \"link_embed\": \"https://player.phimapi.com/player/?url=https://v7.kkphim.com/tap1.m3u8\"}\n" +
                "          ]\n" +
                "        },\n" +
                "        {\n" +
                "          \"server_name\": \"Thuyết Minh (KK)\",\n" +
                "          \"server_data\": [\n" +
                "            {\"name\": \"Tập 01\", \"slug\": \"tap-01\", \"link_m3u8\": \"https://v7.kkphim.com/tap1_tm.m3u8\", \"link_embed\": \"\"}\n" +
                "          ]\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  }\n" +
                "}";

        // Trích xuất link cho Tập 1 ("tap-01")
        List<VideoLink> links = ChophimLogic.extractVideoLinks(mockDetailJson, "tap-01");
        assertNotNull(links);
        assertFalse(links.isEmpty(), "Phải trích xuất được link phát");
        assertEquals(2, links.size(), "Phải trích xuất đủ link của 2 server (Vietsub & Thuyết Minh)");

        VideoLink link1 = links.get(0);
        assertEquals(VideoLink.TYPE_M3U8, link1.type);
        assertEquals("https://v7.kkphim.com/tap1.m3u8", link1.url);
        assertEquals("Vietsub (KK)", link1.serverName);
        assertEquals("Vietsub", link1.langName);

        VideoLink link2 = links.get(1);
        assertEquals(VideoLink.TYPE_M3U8, link2.type);
        assertEquals("https://v7.kkphim.com/tap1_tm.m3u8", link2.url);
        assertEquals("Thuyết Minh (KK)", link2.serverName);
        assertEquals("Thuyết Minh", link2.langName);

        System.out.println("✅ Mock ExtractVideoLinks test passed: " + links.size() + " links:");
        for (VideoLink l : links) {
            System.out.println("   [" + l.serverName + " - " + l.langName + "] -> " + l.url);
        }

        // Live extract test
        try {
            String apiUrl = API_BASE_URL + "/phim/bay-vao-trai-tim-anh";
            String json = Jsoup.connect(apiUrl)
                    .userAgent(USER_AGENT)
                    .ignoreContentType(true)
                    .timeout(10000)
                    .execute()
                    .body();

            List<VideoLink> liveLinks = ChophimLogic.extractVideoLinks(json, "tap-01");
            assertNotNull(liveLinks);
            assertFalse(liveLinks.isEmpty(), "Live video links không được rỗng");
            System.out.println("✅ Live ExtractVideoLinks test passed: " + liveLinks.size() + " links.");
            for (VideoLink l : liveLinks) {
                System.out.println("   Live link: [" + l.serverName + "] " + l.url);
            }
        } catch (Exception e) {
            System.out.println("⚠️ Live ExtractVideoLinks test bị gián đoạn: " + e.getMessage());
        }
    }
}
