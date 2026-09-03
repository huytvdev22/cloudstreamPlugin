package com.motchill;

import com.cloudstream.core.model.EpisodeItem;
import com.cloudstream.core.model.MainPageSection;
import com.cloudstream.core.model.MovieDetail;
import com.cloudstream.core.model.MovieItem;
import com.cloudstream.core.model.VideoLink;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MotchillLogicTest - Bộ kiểm thử tự động toàn diện cho motchillw.sh.
 *
 * Kiểm tra đầy đủ 6 test cases chuẩn kiến trúc Hybrid Pattern:
 * 1. Test 0: Trích xuất tên miền động (ParseDomain).
 * 2. Test 1: Danh mục trang chủ (MainPage Sections).
 * 3. Test 2: Bóc tách danh sách phim (Movie List) với Mock HTML và Live Network.
 * 4. Test 3: Tìm kiếm thông minh (Smart Search) và Live Network.
 * 5. Test 4: Chi tiết phim & danh sách tập (Movie Detail & Episodes).
 * 6. Test 5: Trích xuất link phát streaming HLS M3U8 trực tiếp và xác minh #EXTM3U.
 */
public class MotchillLogicTest {

    private static final String BASE_URL = MotchillLogic.DEFAULT_BASE_URL;
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Test
    @DisplayName("Test 0: Trích xuất tên miền động từ canonical / alternate")
    public void test00_ParseDomain() {
        // 1. Mock HTML test
        String mockHtml1 = "<html><head><link rel=\"canonical\" href=\"https://motchillw.sh/\" /></head></html>";
        assertEquals("https://motchillw.sh", MotchillLogic.parseDomain(mockHtml1));

        String mockHtml2 = "<html><head><link rel=\"alternate\" href=\"https://motchillw.sh/\" /></head></html>";
        assertEquals("https://motchillw.sh", MotchillLogic.parseDomain(mockHtml2));

        // 2. Live Network Test
        try {
            Document doc = Jsoup.connect(BASE_URL)
                    .userAgent(USER_AGENT)
                    .timeout(10000)
                    .get();
            String liveDomain = MotchillLogic.parseDomain(doc.html());
            System.out.println("✅ Tên miền động lấy từ live site: " + liveDomain);
            assertNotNull(liveDomain);
            assertTrue(liveDomain.startsWith("http"), "Domain phải bắt đầu bằng http/https");
            assertEquals("https://motchillw.sh", liveDomain);
        } catch (Exception e) {
            System.out.println("⚠️ Không thể kết nối motchillw.sh: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test 1: Cấu hình danh mục trang chủ (MainPage Sections)")
    public void test01_ParseMainPageSections() {
        List<MainPageSection> sections = MotchillLogic.parseMainPage("");
        assertNotNull(sections);
        assertTrue(sections.size() >= 10, "Phải có ít nhất 10 danh mục trên trang chủ");

        System.out.println("✅ Danh mục trang chủ (" + sections.size() + " mục):");
        for (MainPageSection s : sections) {
            System.out.println("   [" + s.name + "] -> " + s.path);
        }

        assertTrue(sections.stream().anyMatch(s -> s.name.contains("Mới Cập Nhật")));
        assertTrue(sections.stream().anyMatch(s -> s.name.contains("Phim Bộ")));
        assertTrue(sections.stream().anyMatch(s -> s.name.contains("Phim Lẻ")));
    }

    @Test
    @DisplayName("Test 2: Bóc tách danh sách phim (Mock HTML & Live Network)")
    public void test02_ParseMovieList() {
        // 1. Mock HTML Test
        String mockHtml = "<div class=\"grid\">"
                + "<div class=\"group relative\">"
                + "  <a title=\"Đầu Xuân Tươi Sáng\" href=\"/phim/dau-xuan-tuoi-sang-1787738435/k-tap-01\">"
                + "    <img alt=\"Đầu Xuân Tươi Sáng\" src=\"/storage/images/poster.webp\" />"
                + "    <span>Vietsub</span><span>FHD</span>"
                + "  </a>"
                + "</div>"
                + "</div>";

        List<MovieItem> mockList = MotchillLogic.parseMovieList(mockHtml, BASE_URL);
        assertEquals(1, mockList.size());
        assertEquals("Đầu Xuân Tươi Sáng", mockList.get(0).title);
        assertEquals(BASE_URL + "/phim/dau-xuan-tuoi-sang-1787738435", mockList.get(0).href);
        assertEquals(BASE_URL + "/storage/images/poster.webp", mockList.get(0).posterUrl);
        assertTrue(mockList.get(0).tags.contains("Vietsub"));
        assertTrue(mockList.get(0).tags.contains("FHD"));
        System.out.println("✅ Mock HTML parsed thành công 1 phim.");

        // 2. Live Network Test
        try {
            String liveUrl = BASE_URL + "/danh-sach/phim-bo";
            Document doc = Jsoup.connect(liveUrl)
                    .userAgent(USER_AGENT)
                    .timeout(10000)
                    .get();

            List<MovieItem> liveList = MotchillLogic.parseMovieList(doc.html(), BASE_URL);
            System.out.println("✅ Live Network cào thành công " + liveList.size() + " phim từ: " + liveUrl);
            assertTrue(liveList.size() >= 10, "Phải cào được ít nhất 10 phim từ trang phim bộ");

            for (int i = 0; i < Math.min(3, liveList.size()); i++) {
                MovieItem item = liveList.get(i);
                System.out.println("   [" + (i + 1) + "] " + item.title + " | Link: " + item.href + " | Tags: " + item.tags);
                assertNotNull(item.title);
                assertFalse(item.title.isEmpty());
                assertTrue(item.href.startsWith(BASE_URL + "/phim/"));
            }
        } catch (Exception e) {
            System.out.println("⚠️ Bỏ qua Live Network test do lỗi mạng: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test 3: Tìm kiếm thông minh (Smart Search) và Live Network")
    public void test03_BuildSearchUrlAndSearch() {
        // 1. Kiểm tra build search url
        String searchUrl = MotchillLogic.buildSearchUrl(BASE_URL, "hoa khai", 1);
        assertEquals(BASE_URL + "/search?q=hoa+khai", searchUrl);

        String page2Url = MotchillLogic.buildSearchUrl(BASE_URL, "hoa khai", 2);
        assertEquals(BASE_URL + "/search?q=hoa+khai&page=2", page2Url);
        System.out.println("✅ Search URL hợp lệ: " + searchUrl);

        // 2. Live Network Search Test
        try {
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .timeout(10000)
                    .get();

            List<MovieItem> results = MotchillLogic.parseMovieList(doc.html(), BASE_URL);
            System.out.println("✅ Live Search 'hoa khai' trả về " + results.size() + " phim.");
            assertTrue(results.size() >= 1, "Phải tìm thấy ít nhất 1 kết quả cho 'hoa khai'");

            boolean matched = false;
            for (MovieItem item : results) {
                if (item.title.toLowerCase().contains("hoa") || item.title.toLowerCase().contains("khai")) {
                    matched = true;
                    System.out.println("   -> Khớp từ khóa: " + item.title + " (" + item.href + ")");
                    break;
                }
            }
            assertTrue(matched, "Kết quả tìm kiếm phải khớp từ khóa 'hoa' hoặc 'khai'");
        } catch (Exception e) {
            System.out.println("⚠️ Bỏ qua Live Network Search do lỗi mạng: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test 4: Chi tiết phim lẻ & phim bộ (Movie Detail & Episodes)")
    public void test04_ParseMovieDetail_MovieAndSeries() {
        // 1. Mock HTML Detail Test với JSON-LD và RSC Episodes
        String mockDetailHtml = "<html><head>"
                + "<script type=\"application/ld+json\">"
                + "{\"@type\":\"TVSeries\",\"name\":\"Phim Thử Nghiệm\",\"description\":\"Nội dung thử nghiệm\",\"thumbnailUrl\":\"https://img.com/p.jpg\",\"datePublished\":\"2026-01-01\",\"genre\":[\"Hành Động\",\"Cổ Trang\"]}"
                + "</script>"
                + "</head><body>"
                + "<script>self.__next_f.push([1,\"\\\"episodes\\\":[{\\\"id\\\":\\\"1\\\",\\\"name\\\":\\\"Tập 01\\\",\\\"server\\\":\\\"K - Vietsub\\\",\\\"slug\\\":\\\"k-tap-01\\\",\\\"link\\\":\\\"https://cdn.com/1.m3u8\\\"},{\\\"id\\\":\\\"2\\\",\\\"name\\\":\\\"Tập 01\\\",\\\"server\\\":\\\"K - Thuyết Minh\\\",\\\"slug\\\":\\\"k-tap-01\\\",\\\"link\\\":\\\"https://cdn.com/1-tm.m3u8\\\"},{\\\"id\\\":\\\"3\\\",\\\"name\\\":\\\"Tập 02\\\",\\\"server\\\":\\\"K - Vietsub\\\",\\\"slug\\\":\\\"k-tap-02\\\",\\\"link\\\":\\\"https://cdn.com/2.m3u8\\\"}]\"])</script>"
                + "</body></html>";

        MovieDetail detail = MotchillLogic.parseMovieDetail(mockDetailHtml, BASE_URL);
        assertNotNull(detail);
        assertEquals("Phim Thử Nghiệm", detail.title);
        assertEquals("Nội dung thử nghiệm", detail.plot);
        assertEquals("https://img.com/p.jpg", detail.posterUrl);
        assertEquals(2026, detail.year);
        assertTrue(detail.tags.contains("Hành Động"));
        assertTrue(detail.tags.contains("Cổ Trang"));
        assertEquals(2, detail.episodes.size(), "Phải gom nhóm 3 server thành 2 tập (Tập 01 và Tập 02)");
        assertEquals("Tập 01", detail.episodes.get(0).name);
        assertEquals("Tập 02", detail.episodes.get(1).name);
        System.out.println("✅ Mock Movie Detail & Episodes parsed thành công.");

        // 2. Live Network Test trang chi tiết phim
        try {
            String movieUrl = BASE_URL + "/phim/dau-xuan-tuoi-sang-1787738435";
            Document doc = Jsoup.connect(movieUrl)
                    .userAgent(USER_AGENT)
                    .timeout(10000)
                    .get();

            MovieDetail liveDetail = MotchillLogic.parseMovieDetail(doc.html(), BASE_URL);
            System.out.println("✅ Live Movie Detail: " + liveDetail.title + " | Năm: " + liveDetail.year + " | Tập: " + liveDetail.episodes.size());
            assertNotNull(liveDetail.title);
            assertFalse(liveDetail.title.isEmpty());
            assertTrue(liveDetail.episodes.size() >= 1, "Phim phải có ít nhất 1 tập");

            for (EpisodeItem ep : liveDetail.episodes) {
                System.out.println("   -> Tập: " + ep.name + " | Link: " + ep.href);
            }
        } catch (Exception e) {
            System.out.println("⚠️ Bỏ qua Live Network Detail do lỗi mạng: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test 5: Trích xuất link phát streaming HLS M3U8 trực tiếp và xác minh #EXTM3U")
    public void test05_ExtractVideoLinks() {
        // 1. Mock JSON servers data trích xuất từ EpisodeItem
        String mockServersJson = "[{\"server\":\"K - Vietsub\",\"link\":\"https://v7.kkphimplayer7.com/20260826/3hQ5mFzr/index.m3u8\"},"
                + "{\"server\":\"Player PhimAPI\",\"link\":\"https://player.phimapi.com/player/?url=https://v7.kkphimplayer7.com/20260826/3hQ5mFzr/index.m3u8\"}]";

        List<VideoLink> links = MotchillLogic.extractVideoLinks("", mockServersJson);
        assertNotNull(links);
        assertEquals(1, links.size(), "Link trùng nhau sau khi unwrap phải được khử trùng lặp");
        assertEquals(VideoLink.TYPE_M3U8, links.get(0).type);
        assertEquals("https://v7.kkphimplayer7.com/20260826/3hQ5mFzr/index.m3u8", links.get(0).url);
        System.out.println("✅ Mock ExtractVideoLinks thành công: " + links.get(0).url);

        // 2. Live Network Test kiểm tra stream thực tế và header #EXTM3U
        try {
            String testStreamUrl = "https://v7.kkphimplayer7.com/20260826/3hQ5mFzr/index.m3u8";
            Connection.Response res = Jsoup.connect(testStreamUrl)
                    .userAgent(USER_AGENT)
                    .referrer(BASE_URL + "/")
                    .ignoreContentType(true)
                    .timeout(10000)
                    .execute();

            assertEquals(200, res.statusCode());
            String body = res.body();
            assertTrue(body.startsWith("#EXTM3U"), "Phản hồi stream HLS m3u8 phải bắt đầu bằng #EXTM3U");
            System.out.println("✅ Live Stream M3U8 hợp lệ, phản hồi mã 200 và header #EXTM3U:");
            System.out.println("   " + body.substring(0, Math.min(120, body.length())).replace("\n", " "));
        } catch (Exception e) {
            System.out.println("⚠️ Bỏ qua Live Stream M3U8 do lỗi mạng: " + e.getMessage());
        }
    }
}
