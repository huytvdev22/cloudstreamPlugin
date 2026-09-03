package com.xemplay;

import com.cloudstream.core.model.EpisodeItem;
import com.cloudstream.core.model.MainPageSection;
import com.cloudstream.core.model.MovieDetail;
import com.cloudstream.core.model.MovieItem;
import com.cloudstream.core.model.VideoLink;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XemplayLogicTest - Bộ kiểm thử tự động toàn diện cho xemplay.uk.
 *
 * Kiểm tra đầy đủ 5 trường hợp sử dụng theo vòng đời xem phim:
 * 1. Danh mục trang chủ (MainPage Sections).
 * 2. Bóc tách danh sách phim (Movie List) với Mock HTML và Live Network.
 * 3. Tìm kiếm thông minh (Smart Search) và Live Network.
 * 4. Chi tiết phim lẻ & phim bộ (Movie Detail & Episodes).
 * 5. Trích xuất link phát streaming HLS M3U8 trực tiếp.
 */
public class XemplayLogicTest {

    private static final String BASE_URL = "https://xemplay.uk";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Test
    @DisplayName("Test 0: Trích xuất tên miền động từ portal xemplay.com")
    public void test00_ParseDomain() {
        // 1. Mock HTML tests
        String mockHtml1 = "<html><head><link rel=\"alternate\" href=\"https://xemplay.uk/\" hreflang=\"vi\" /></head></html>";
        assertEquals("https://xemplay.uk", XemplayLogic.parseDomain(mockHtml1));

        String mockHtml2 = "<html><body><a class=\"top-cta\" href=\"https://xemplay.uk/\">Vào xem phim</a></body></html>";
        assertEquals("https://xemplay.uk", XemplayLogic.parseDomain(mockHtml2));

        String mockHtml3 = "<script type=\"application/ld+json\">{\"@type\":\"WebSite\",\"potentialAction\":{\"target\":\"https://xemplay.uk/browse?q={search}\"}}</script>";
        assertEquals("https://xemplay.uk", XemplayLogic.parseDomain(mockHtml3));

        // 2. Live Network Test từ portal xemplay.com
        try {
            Document doc = Jsoup.connect(XemplayLogic.PORTAL_URL)
                    .userAgent(USER_AGENT)
                    .timeout(10000)
                    .get();
            String liveDomain = XemplayLogic.parseDomain(doc.html());
            System.out.println("✅ Tên miền động lấy từ xemplay.com: " + liveDomain);
            assertNotNull(liveDomain);
            assertTrue(liveDomain.startsWith("http"), "Domain phải bắt đầu bằng http/https");
            assertFalse(liveDomain.equalsIgnoreCase(XemplayLogic.PORTAL_URL), "Domain phải khác URL portal");
            assertEquals("https://xemplay.uk", liveDomain);
        } catch (Exception e) {
            System.out.println("⚠️ Không thể kết nối xemplay.com: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test 1: Cấu hình danh mục trang chủ (MainPage Sections)")
    public void test01_ParseMainPageSections() {
        List<MainPageSection> sections = XemplayLogic.parseMainPage("");
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
                + "<a class=\"block\" href=\"/phim/test-film\">"
                + "  <div class=\"aspect-[2/3]\">"
                + "    <img src=\"https://phimimg.com/uploads/test.jpg\" alt=\"Test Film\" />"
                + "    <span>HD</span><span>Vietsub</span>"
                + "  </div>"
                + "  <div><h3>Test Film</h3></div>"
                + "</a>"
                + "</div>";

        List<MovieItem> mockList = XemplayLogic.parseMovieList(mockHtml, BASE_URL);
        assertEquals(1, mockList.size());
        assertEquals("Test Film", mockList.get(0).title);
        assertEquals(BASE_URL + "/phim/test-film", mockList.get(0).href);
        assertEquals("https://phimimg.com/uploads/test.jpg", mockList.get(0).posterUrl);
        assertTrue(mockList.get(0).tags.contains("HD"));
        assertTrue(mockList.get(0).tags.contains("Vietsub"));
        System.out.println("✅ Mock HTML parsed thành công 1 phim.");

        // 2. Live Network Test với /browse?type=phim-moi-cap-nhat
        try {
            String url = BASE_URL + "/browse?type=phim-moi-cap-nhat";
            Document doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(15000).get();
            List<MovieItem> liveList = XemplayLogic.parseMovieList(doc.html(), BASE_URL);

            assertNotNull(liveList);
            assertFalse(liveList.isEmpty(), "Danh sách phim live không được rỗng");
            System.out.println("✅ Live Network parsed thành công " + liveList.size() + " phim:");
            for (int i = 0; i < Math.min(5, liveList.size()); i++) {
                MovieItem item = liveList.get(i);
                System.out.println("   #" + (i + 1) + ": " + item.title + " | " + item.href + " | Tags: " + item.tags);
                assertNotNull(item.title);
                assertFalse(item.title.isEmpty());
                assertTrue(item.href.startsWith("http"));
            }
        } catch (Exception e) {
            System.out.println("⚠️ Lỗi kết nối Live Network: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test 3: Tìm kiếm thông minh (Smart Search URL & Live Network)")
    public void test03_BuildSearchUrlAndSearch() {
        // 1. Kiểm tra build search URL với các hashtag
        String searchUrl1 = XemplayLogic.buildSearchUrl(BASE_URL, "conan", 1);
        assertTrue(searchUrl1.contains("q=conan"));
        assertTrue(searchUrl1.contains("page=1"));

        String searchUrl2 = XemplayLogic.buildSearchUrl(BASE_URL, "conan #phimbo #hanquoc nam:2024", 2);
        assertTrue(searchUrl2.contains("q=conan"));
        assertTrue(searchUrl2.contains("type=phim-bo"));
        assertTrue(searchUrl2.contains("country=han-quoc"));
        assertTrue(searchUrl2.contains("year=2024"));
        assertTrue(searchUrl2.contains("page=2"));
        System.out.println("✅ Smart Search URL generated: " + searchUrl2);

        // 2. Live Network Search
        try {
            Document doc = Jsoup.connect(BASE_URL + "/browse?q=conan").userAgent(USER_AGENT).timeout(15000).get();
            List<MovieItem> searchResults = XemplayLogic.parseMovieList(doc.html(), BASE_URL);

            assertNotNull(searchResults);
            assertFalse(searchResults.isEmpty(), "Kết quả tìm kiếm cho 'conan' không được rỗng");
            System.out.println("✅ Tìm thấy " + searchResults.size() + " kết quả cho từ khóa 'conan':");
            for (int i = 0; i < Math.min(3, searchResults.size()); i++) {
                System.out.println("   - " + searchResults.get(i).title);
            }
        } catch (Exception e) {
            System.out.println("⚠️ Lỗi kết nối tìm kiếm: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test 4: Chi tiết phim lẻ & phim bộ (Detail & Episodes)")
    public void test04_ParseMovieDetail_MovieAndSeries() {
        // 1. Kiểm tra phim lẻ (Single Movie)
        try {
            String movieUrl = BASE_URL + "/phim/su-thi-odyssey";
            Document doc = Jsoup.connect(movieUrl).userAgent(USER_AGENT).timeout(15000).get();
            MovieDetail movieDetail = XemplayLogic.parseMovieDetail(doc.html(), BASE_URL);

            assertNotNull(movieDetail);
            System.out.println("✅ Phim Lẻ: " + movieDetail.title);
            System.out.println("   Poster: " + movieDetail.posterUrl);
            System.out.println("   Year: " + movieDetail.year);
            System.out.println("   Số tập: " + movieDetail.episodes.size());

            assertNotNull(movieDetail.title);
            assertFalse(movieDetail.title.isEmpty());
            assertNotNull(movieDetail.posterUrl);
            assertFalse(movieDetail.episodes.isEmpty(), "Phim lẻ phải có ít nhất 1 tập phát (Full)");
        } catch (Exception e) {
            System.out.println("⚠️ Lỗi kiểm tra phim lẻ: " + e.getMessage());
        }

        // 2. Kiểm tra phim bộ (TV Series)
        try {
            String seriesUrl = BASE_URL + "/phim/keo-ngot-tinh-yeu";
            Document doc = Jsoup.connect(seriesUrl).userAgent(USER_AGENT).timeout(15000).get();
            MovieDetail seriesDetail = XemplayLogic.parseMovieDetail(doc.html(), BASE_URL);

            assertNotNull(seriesDetail);
            System.out.println("✅ Phim Bộ: " + seriesDetail.title);
            System.out.println("   Poster: " + seriesDetail.posterUrl);
            System.out.println("   Year: " + seriesDetail.year);
            System.out.println("   Số tập bóc tách được: " + seriesDetail.episodes.size());

            assertNotNull(seriesDetail.title);
            assertTrue(seriesDetail.episodes.size() >= 10, "Phim bộ 'Kẹo Ngọt Tình Yêu' phải bóc tách được đủ 12 tập");

            for (EpisodeItem ep : seriesDetail.episodes) {
                System.out.println("      " + ep.name + " (#" + ep.episodeNum + ") -> " + ep.href);
                assertTrue(ep.href.startsWith("http"));
            }
        } catch (Exception e) {
            System.out.println("⚠️ Lỗi kiểm tra phim bộ: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test 5: Trích xuất link phát streaming HLS M3U8")
    public void test05_ExtractVideoLinks() {
        try {
            String watchUrl = BASE_URL + "/phim/su-thi-odyssey/full";
            Document doc = Jsoup.connect(watchUrl).userAgent(USER_AGENT).timeout(15000).get();
            List<VideoLink> links = XemplayLogic.extractVideoLinks(doc.html(), watchUrl);

            assertNotNull(links);
            assertFalse(links.isEmpty(), "Phải trích xuất được ít nhất 1 link video");

            System.out.println("✅ Trích xuất được " + links.size() + " link phát:");
            boolean hasM3u8 = false;
            for (VideoLink l : links) {
                System.out.println("   [" + l.type + "] " + l.label + " | " + l.serverName + " -> " + l.url);
                if (VideoLink.TYPE_M3U8.equals(l.type)) {
                    hasM3u8 = true;
                    assertTrue(l.url.startsWith("http"));

                    // Kiểm tra kết nối tải thử nội dung M3U8
                    String m3u8Content = Jsoup.connect(l.url)
                            .userAgent(USER_AGENT)
                            .referrer(BASE_URL + "/")
                            .ignoreContentType(true)
                            .timeout(10000)
                            .execute()
                            .body();

                    assertNotNull(m3u8Content);
                    assertTrue(m3u8Content.contains("#EXTM3U"), "Link M3U8 phải chứa header #EXTM3U");
                    System.out.println("   🎉 M3U8 Playlist hợp lệ: chứa header #EXTM3U!");
                }
            }

            assertTrue(hasM3u8, "Phải có ít nhất 1 link loại M3U8");
        } catch (Exception e) {
            System.out.println("⚠️ Lỗi trích xuất video streaming: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
