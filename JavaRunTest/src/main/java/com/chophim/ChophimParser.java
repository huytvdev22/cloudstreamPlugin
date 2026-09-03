package com.chophim;

import com.cloudstream.core.model.EpisodeItem;
import com.cloudstream.core.model.MainPageSection;
import com.cloudstream.core.model.MovieDetail;
import com.cloudstream.core.model.MovieItem;
import com.cloudstream.core.model.VideoLink;
import com.cloudstream.core.parser.MovieParser;
import com.cloudstream.core.util.HtmlHelper;
import com.cloudstream.core.util.RegexHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;

/**
 * ChophimParser - Triển khai MovieParser bóc tách dữ liệu cho nguồn ChoPhim.app.
 *
 * Hỗ trợ linh hoạt 2 cơ chế:
 * 1. RESTful JSON API (/api/film/...) với hiệu năng cao, dữ liệu chuẩn hóa và ổn định.
 * 2. Fallback HTML Scraping (DOM + Next.js RSC Payload) khi dữ liệu trả về ở dạng trang web.
 */
public class ChophimParser implements MovieParser {

    public static final String DEFAULT_BASE_URL = "https://chophim.app";
    public static final String API_BASE_URL = "https://chophim.app/api/film";

    private static final ChophimParser INSTANCE = new ChophimParser();

    public static ChophimParser getInstance() {
        return INSTANCE;
    }

    // ==========================================
    // 0. DANH MỤC TRANG CHỦ (MAIN PAGE SECTIONS)
    // ==========================================

    @Override
    public List<MainPageSection> parseMainPage(String content, String baseUrl) {
        List<MainPageSection> sections = new ArrayList<>();
        // Mặc định cung cấp danh sách các mục hot nhất từ API chophim.app
        sections.add(new MainPageSection("🔥 Phim Mới Cập Nhật", "/api/film/danh-sach/phim-moi"));
        sections.add(new MainPageSection("📺 Phim Bộ Mới Nhất", "/api/film/danh-sach/phim-bo"));
        sections.add(new MainPageSection("🎬 Phim Lẻ Mới Nhất", "/api/film/danh-sach/phim-le"));
        sections.add(new MainPageSection("⛩️ Hoạt Hình / Anime", "/api/film/danh-sach/hoat-hinh"));
        sections.add(new MainPageSection("🎤 TV Shows Truyền Hình", "/api/film/danh-sach/tv-shows"));
        sections.add(new MainPageSection("🎎 Phim Cổ Trang", "/api/film/the-loai/co-trang"));
        sections.add(new MainPageSection("💥 Phim Hành Động", "/api/film/the-loai/hanh-dong"));
        sections.add(new MainPageSection("💖 Phim Tình Cảm", "/api/film/the-loai/tinh-cam"));
        sections.add(new MainPageSection("👻 Phim Kinh Dị", "/api/film/the-loai/kinh-di"));
        sections.add(new MainPageSection("🌸 Phim Hàn Quốc", "/api/film/quoc-gia/han-quoc"));
        sections.add(new MainPageSection("🐉 Phim Trung Quốc", "/api/film/quoc-gia/trung-quoc"));
        sections.add(new MainPageSection("🗽 Phim Âu Mỹ", "/api/film/quoc-gia/au-my"));
        sections.add(new MainPageSection("🌸 Phim Nhật Bản", "/api/film/quoc-gia/nhat-ban"));
        return sections;
    }

    // ==========================================
    // 1. PARSE DANH SÁCH PHIM (MOVIE LIST)
    // ==========================================

    @Override
    public List<MovieItem> parseMovieList(String content, String baseUrl) {
        List<MovieItem> result = new ArrayList<>();
        if (content == null || content.trim().isEmpty()) {
            return result;
        }

        String trimmed = content.trim();

        // Trường hợp 1: Dữ liệu JSON từ API (/api/film/...)
        if (trimmed.startsWith("{") || trimmed.contains("\"items\":")) {
            return parseMovieListFromJson(trimmed, baseUrl);
        }

        // Trường hợp 2: Dữ liệu HTML của trang web (SSR / Next.js)
        return parseMovieListFromHtml(trimmed, baseUrl);
    }

    /**
     * Bóc tách danh sách phim từ chuỗi JSON API của chophim.app
     */
    public List<MovieItem> parseMovieListFromJson(String jsonStr, String baseUrl) {
        List<MovieItem> result = new ArrayList<>();
        Set<String> seenSlugs = new HashSet<>();

        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray items = null;

            if (root.has("data")) {
                Object dataObj = root.get("data");
                if (dataObj instanceof JSONObject) {
                    items = ((JSONObject) dataObj).optJSONArray("items");
                } else if (dataObj instanceof JSONArray) {
                    items = (JSONArray) dataObj;
                }
            } else if (root.has("items")) {
                items = root.optJSONArray("items");
            }

            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) continue;

                    String name = item.optString("name", "").trim();
                    String slug = item.optString("slug", "").trim();
                    if (name.isEmpty() || slug.isEmpty()) continue;

                    if (seenSlugs.contains(slug)) continue;
                    seenSlugs.add(slug);

                    // Đường dẫn phim chuẩn
                    String href = cleanUrl(baseUrl) + "/phim/" + slug;

                    // Poster & Thumbnail
                    String poster = item.optString("poster_url", "").trim();
                    if (poster.isEmpty()) {
                        poster = item.optString("thumb_url", "").trim();
                    }
                    poster = normalizeImageUrl(poster);

                    // Badges nhãn: Vietsub (VS), Thuyết Minh (TM), Lồng Tiếng (LT), Chất lượng (FHD, HD), Số tập
                    List<String> tags = new ArrayList<>();

                    // Parse lang_key (["vs"], ["tm"], ["lt"])
                    JSONArray langKeys = item.optJSONArray("lang_key");
                    if (langKeys != null) {
                        for (int k = 0; k < langKeys.length(); k++) {
                            String key = langKeys.optString(k, "").toUpperCase().trim();
                            if (!key.isEmpty() && !tags.contains(key)) {
                                tags.add(key);
                            }
                        }
                    }

                    // Parse trường lang ("Vietsub", "Thuyết Minh", ...)
                    String langText = item.optString("lang", "").trim();
                    if (!langText.isEmpty()) {
                        String shortLang = toShortLang(langText);
                        if (!shortLang.isEmpty() && !tags.contains(shortLang)) {
                            tags.add(shortLang);
                        }
                    }

                    // Chất lượng video (FHD, HD)
                    String quality = item.optString("quality", "").trim().toUpperCase();
                    if (!quality.isEmpty() && !tags.contains(quality)) {
                        tags.add(quality);
                    }

                    // Số tập hiện tại (Tập 1, Hoàn Tất (7/7))
                    String epCurrent = item.optString("episode_current", "").trim();
                    if (!epCurrent.isEmpty() && !tags.contains(epCurrent)) {
                        tags.add(epCurrent);
                    }

                    result.add(new MovieItem(name, href, poster.isEmpty() ? null : poster, tags));
                }
            }
        } catch (Exception e) {
            // Không ngắt luồng nếu parse json lỗi, fallback sang HTML
        }

        return result;
    }

    /**
     * Bóc tách danh sách phim từ HTML (DOM hoặc Next.js RSC Chunks)
     */
    public List<MovieItem> parseMovieListFromHtml(String html, String baseUrl) {
        List<MovieItem> result = new ArrayList<>();
        Set<String> seenHrefs = new HashSet<>();

        // Kiểm tra xem HTML có chứa khối JSON trong Next.js RSC chunks không
        if (html.contains("self.__next_f")) {
            String extractedJson = extractJsonArrayFromNextChunks(html, "\"items\":[");
            if (extractedJson != null) {
                List<MovieItem> fromJson = parseMovieListFromJson("{\"items\":" + extractedJson + "}", baseUrl);
                if (!fromJson.isEmpty()) {
                    return fromJson;
                }
            }
        }

        // Bóc tách DOM thẻ card chuẩn
        Document document = Jsoup.parse(html, baseUrl);
        Elements cards = document.select("a[href*=/phim/]");

        for (Element card : cards) {
            String href = HtmlHelper.getAbsoluteUrl(baseUrl, card, "href");
            if (href == null || href.isEmpty() || seenHrefs.contains(href)) continue;

            Element img = card.selectFirst("img");
            String title = (img != null && !img.attr("alt").trim().isEmpty())
                    ? img.attr("alt").trim()
                    : HtmlHelper.selectFirstText(card, "h2, h3, h4, .title");

            if (title.isEmpty()) continue;
            seenHrefs.add(href);

            String poster = (img != null) ? img.attr("src") : null;
            poster = normalizeImageUrl(poster);

            List<String> tags = new ArrayList<>();
            Elements badges = card.select("span, div[class*=badge]");
            for (Element b : badges) {
                String t = b.text().trim();
                if (!t.isEmpty() && t.length() < 20 && !tags.contains(t)) {
                    tags.add(t);
                }
            }

            result.add(new MovieItem(title, href, poster, tags));
        }

        return result;
    }

    // ==========================================
    // 2. PARSE CHI TIẾT BỘ PHIM (MOVIE DETAIL)
    // ==========================================

    @Override
    public MovieDetail parseMovieDetail(String content, String baseUrl) {
        if (content == null || content.trim().isEmpty()) {
            return new MovieDetail("Không có tên", null, null, null, null, Collections.emptyList(), Collections.emptyList());
        }

        String trimmed = content.trim();

        // Trường hợp 1: Dữ liệu JSON API (/api/film/phim/{slug})
        if (trimmed.startsWith("{") && (trimmed.contains("\"item\":") || trimmed.contains("\"episodes\":"))) {
            return parseMovieDetailFromJson(trimmed, baseUrl);
        }

        // Trường hợp 2: Dữ liệu HTML của trang chi tiết
        return parseMovieDetailFromHtml(trimmed, baseUrl);
    }

    /**
     * Bóc tách chi tiết phim từ JSON API của chophim.app
     */
    public MovieDetail parseMovieDetailFromJson(String jsonStr, String baseUrl) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject item = root.optJSONObject("data");
            if (item != null && item.has("item")) {
                item = item.optJSONObject("item");
            } else if (item == null) {
                item = root.optJSONObject("item");
            }
            if (item == null) {
                item = root;
            }

            String title = item.optString("name", "").trim();
            if (title.isEmpty()) {
                title = item.optString("origin_name", "Không có tên").trim();
            }

            String posterUrl = item.optString("poster_url", "").trim();
            if (posterUrl.isEmpty()) {
                posterUrl = item.optString("thumb_url", "").trim();
            }
            posterUrl = normalizeImageUrl(posterUrl);

            // Mô tả tóm tắt
            String plot = item.optString("content", "").trim();
            if (!plot.isEmpty()) {
                plot = plot.replaceAll("<[^>]*>", "").trim();
            }

            // Năm sản xuất & Thời lượng
            Integer year = item.has("year") ? item.optInt("year") : null;
            Integer duration = RegexHelper.parseInt(item.optString("time", ""), "(\\d+)");

            // Thể loại (Tags)
            List<String> tags = new ArrayList<>();
            JSONArray categories = item.optJSONArray("category");
            if (categories != null) {
                for (int i = 0; i < categories.length(); i++) {
                    JSONObject cat = categories.optJSONObject(i);
                    if (cat != null) {
                        String catName = cat.optString("name", "").trim();
                        if (!catName.isEmpty() && !tags.contains(catName)) {
                            tags.add(catName);
                        }
                    }
                }
            }

            // Danh sách tập phim (gom nhóm các máy chủ theo tập)
            String movieSlug = item.optString("slug", "").trim();
            JSONArray episodesArray = item.optJSONArray("episodes");
            List<EpisodeItem> episodes = parseEpisodesFromJsonArray(episodesArray, movieSlug, baseUrl);

            return new MovieDetail(title, posterUrl, plot, year, duration, tags, episodes);
        } catch (Exception e) {
            return new MovieDetail("Không có tên", null, null, null, null, Collections.emptyList(), Collections.emptyList());
        }
    }

    /**
     * Bóc tách chi tiết phim từ HTML (DOM hoặc Next.js RSC chunks)
     */
    public MovieDetail parseMovieDetailFromHtml(String html, String baseUrl) {
        // Kiểm tra xem có dữ liệu episodes trong chunk Next.js không
        if (html.contains("self.__next_f")) {
            String extractedEpisodes = extractJsonArrayFromNextChunks(html, "\"episodes\":[");
            if (extractedEpisodes != null) {
                // Tạo một object JSON giả lập để parse
                String movieSlug = RegexHelper.extractGroup(baseUrl, "/(?:phim|xem)/([a-zA-Z0-9_-]+)", 1);
                if (movieSlug == null) movieSlug = "";

                Document doc = Jsoup.parse(html, baseUrl);
                String title = HtmlHelper.selectFirstText(doc, "h1");
                Element img = doc.selectFirst("img[src*=/upload/], img[src*=tmdb], img[src*=/movies/]");
                String poster = img != null ? img.attr("src") : null;
                String plot = HtmlHelper.selectFirstText(doc, "p.content, .description, p:has(> span)");
                Integer year = RegexHelper.parseInt(html, "\\b(19\\d{2}|20\\d{2})\\b");
                Integer duration = RegexHelper.parseInt(html, "(\\d+)\\s*(?:phút|mins|Phút)");

                try {
                    JSONArray epJson = new JSONArray(extractedEpisodes);
                    List<EpisodeItem> eps = parseEpisodesFromJsonArray(epJson, movieSlug, baseUrl);
                    return new MovieDetail(title.isEmpty() ? "Không có tên" : title, normalizeImageUrl(poster), plot, year, duration, Collections.emptyList(), eps);
                } catch (Exception ignored) {
                }
            }
        }

        // Fallback thuần DOM Jsoup
        Document doc = Jsoup.parse(html, baseUrl);
        String title = HtmlHelper.selectFirstText(doc, "h1");
        if (title.isEmpty()) title = "Không có tên";

        Element img = doc.selectFirst("img[src*=/upload/], img[src*=tmdb], img[src*=/movies/]");
        String poster = img != null ? img.attr("src") : null;
        String plot = HtmlHelper.selectFirstText(doc, "p.content, .description");
        Integer year = RegexHelper.parseInt(html, "\\b(19\\d{2}|20\\d{2})\\b");
        Integer duration = RegexHelper.parseInt(html, "(\\d+)\\s*(?:phút|mins|Phút)");

        Elements epLinks = doc.select("a[href*=/xem/]");
        List<EpisodeItem> episodes = new ArrayList<>();
        int count = 1;
        Set<String> seen = new HashSet<>();

        for (Element a : epLinks) {
            String href = HtmlHelper.getAbsoluteUrl(baseUrl, a, "href");
            if (href == null || seen.contains(href)) continue;
            seen.add(href);

            String name = a.text().trim();
            if (name.isEmpty()) name = "Tập " + count;

            Integer epNum = RegexHelper.parseInt(name, "(\\d+)");
            int finalNum = (epNum != null) ? epNum : count;

            episodes.add(new EpisodeItem(href, name, finalNum));
            count++;
        }

        return new MovieDetail(title, normalizeImageUrl(poster), plot, year, duration, Collections.emptyList(), episodes);
    }

    /**
     * Bóc tách danh sách tập phim duy nhất từ mảng JSON các máy chủ của ChoPhim.app
     */
    private List<EpisodeItem> parseEpisodesFromJsonArray(JSONArray episodesArray, String movieSlug, String baseUrl) {
        List<EpisodeItem> episodes = new ArrayList<>();
        if (episodesArray == null || episodesArray.length() == 0) {
            return episodes;
        }

        String base = cleanUrl(baseUrl);
        Map<String, EpisodeItem> uniqueEpisodes = new LinkedHashMap<>();
        int fallbackNum = 1;

        for (int i = 0; i < episodesArray.length(); i++) {
            JSONObject server = episodesArray.optJSONObject(i);
            if (server == null) continue;

            JSONArray serverData = server.optJSONArray("server_data");
            if (serverData == null) continue;

            for (int j = 0; j < serverData.length(); j++) {
                JSONObject ep = serverData.optJSONObject(j);
                if (ep == null) continue;

                String name = ep.optString("name", "").trim();
                String epSlug = ep.optString("slug", "").trim();
                if (epSlug.isEmpty()) {
                    epSlug = "tap-" + (j + 1);
                }
                if (name.isEmpty()) {
                    name = "Tập " + (j + 1);
                }

                // Khóa duy nhất theo slug tập
                if (!uniqueEpisodes.containsKey(epSlug)) {
                    Integer epNum = RegexHelper.parseInt(name, "(\\d+)");
                    if (epNum == null) {
                        epNum = RegexHelper.parseInt(epSlug, "(\\d+)");
                    }
                    int finalNum = (epNum != null) ? epNum : fallbackNum;

                    // Đường dẫn tập chuẩn: chứa slug phim và slug tập (?ep=...)
                    String href = base + "/phim/" + movieSlug + "?ep=" + epSlug;
                    uniqueEpisodes.put(epSlug, new EpisodeItem(href, name, finalNum));
                    fallbackNum++;
                }
            }
        }

        episodes.addAll(uniqueEpisodes.values());
        return episodes;
    }

    // ==========================================
    // 3. TRÍCH XUẤT LINK VIDEO STREAM (M3U8 / Embed)
    // ==========================================

    @Override
    public List<VideoLink> extractVideoLinks(String content, String slugOrEp) {
        List<VideoLink> result = new ArrayList<>();
        if (content == null || content.trim().isEmpty()) {
            return result;
        }

        Set<String> seenUrls = new HashSet<>();

        // Chuẩn hóa slug cần tìm (vd: "tap-01", "tap-1", "full")
        String targetSlug = slugOrEp != null ? slugOrEp.trim() : "";
        if (targetSlug.contains("?ep=")) {
            targetSlug = targetSlug.substring(targetSlug.indexOf("?ep=") + 4);
        }
        if (targetSlug.contains("&")) {
            targetSlug = targetSlug.substring(0, targetSlug.indexOf('&'));
        }
        if (targetSlug.contains("/")) {
            targetSlug = targetSlug.substring(targetSlug.lastIndexOf('/') + 1);
        }
        targetSlug = targetSlug.toLowerCase();
        Integer targetEpNum = RegexHelper.parseInt(targetSlug, "(\\d+)");

        // 1. Nếu content là JSON API (/api/film/phim/{slug})
        if (content.trim().startsWith("{") && content.contains("\"episodes\":")) {
            try {
                JSONObject root = new JSONObject(content);
                JSONObject item = root.optJSONObject("data");
                if (item != null && item.has("item")) {
                    item = item.optJSONObject("item");
                } else if (item == null) {
                    item = root.optJSONObject("item");
                }
                if (item == null) item = root;

                JSONArray episodes = item.optJSONArray("episodes");
                if (episodes != null) {
                    extractLinksFromEpisodesJson(episodes, targetSlug, targetEpNum, result, seenUrls);
                }
            } catch (Exception ignored) {
            }
        }

        // 2. Nếu content là HTML chứa Next.js RSC Chunks
        if (result.isEmpty() && content.contains("self.__next_f")) {
            String epJsonStr = extractJsonArrayFromNextChunks(content, "\"episodes\":[");
            if (epJsonStr != null) {
                try {
                    JSONArray episodes = new JSONArray(epJsonStr);
                    extractLinksFromEpisodesJson(episodes, targetSlug, targetEpNum, result, seenUrls);
                } catch (Exception ignored) {
                }
            }
        }

        // 3. Fallback Regex: Quét link m3u8 hoặc embed trực tiếp trong nội dung
        if (result.isEmpty()) {
            String m3u8 = RegexHelper.extractGroup(content, "(https?://[^\"'\\s\\\\]+\\.m3u8[^\"'\\s\\\\]*)", 1);
            if (m3u8 != null && !seenUrls.contains(m3u8)) {
                seenUrls.add(m3u8);
                result.add(new VideoLink(VideoLink.TYPE_M3U8, m3u8, "ChoPhim M3U8 Fast", "Máy chủ ChoPhim", "Vietsub"));
            }

            String embed = RegexHelper.extractGroup(content, "<iframe[^>]+src=[\"']([^\"']+)[\"']", 1);
            if (embed != null && !seenUrls.contains(embed)) {
                seenUrls.add(embed);
                result.add(new VideoLink(VideoLink.TYPE_EMBED, embed, "ChoPhim Embed", "Máy chủ Embed", "Vietsub"));
            }
        }

        return result;
    }

    /**
     * Duyệt qua danh sách các máy chủ (Vietsub, Thuyết Minh, Song Ngữ...) để lấy link tương ứng với tập cần xem.
     */
    private void extractLinksFromEpisodesJson(JSONArray episodes, String targetSlug, Integer targetEpNum,
                                             List<VideoLink> result, Set<String> seenUrls) {
        for (int i = 0; i < episodes.length(); i++) {
            JSONObject server = episodes.optJSONObject(i);
            if (server == null) continue;

            String serverName = server.optString("server_name", "Máy chủ " + (i + 1)).trim();
            String langName = getLangName(serverName);

            JSONArray serverData = server.optJSONArray("server_data");
            if (serverData == null) continue;

            for (int j = 0; j < serverData.length(); j++) {
                JSONObject ep = serverData.optJSONObject(j);
                if (ep == null) continue;

                String epSlug = ep.optString("slug", "").trim().toLowerCase();
                String epName = ep.optString("name", "").trim();
                Integer currentEpNum = RegexHelper.parseInt(epName, "(\\d+)");
                if (currentEpNum == null) {
                    currentEpNum = RegexHelper.parseInt(epSlug, "(\\d+)");
                }

                // Kiểm tra khớp tập: Trùng slug, hoặc cùng số tập, hoặc phim lẻ chỉ có 1 tập
                boolean isMatch = false;
                if (targetSlug.isEmpty() || epSlug.equalsIgnoreCase(targetSlug)) {
                    isMatch = true;
                } else if (targetEpNum != null && currentEpNum != null && targetEpNum.equals(currentEpNum)) {
                    isMatch = true;
                } else if (serverData.length() == 1 && (targetSlug.contains("full") || targetSlug.contains("tap-1") || targetSlug.contains("tap-01"))) {
                    isMatch = true;
                }

                if (isMatch) {
                    String m3u8 = ep.optString("link_m3u8", "").trim();
                    String embed = ep.optString("link_embed", "").trim();

                    // 1. Link M3U8 trực tiếp
                    if (!m3u8.isEmpty() && m3u8.startsWith("http") && !seenUrls.contains(m3u8)) {
                        seenUrls.add(m3u8);
                        String label = serverName + " (HLS Fast)";
                        result.add(new VideoLink(VideoLink.TYPE_M3U8, m3u8, label, serverName, langName));
                    }

                    // 2. Link Embed / Player
                    if (!embed.isEmpty() && embed.startsWith("http")) {
                        // Nếu embed chứa tham số url=...m3u8 thì trích xuất ra m3u8 trực tiếp
                        if (embed.contains("url=") && embed.contains(".m3u8")) {
                            String directM3u8 = embed.substring(embed.indexOf("url=") + 4);
                            int ampIdx = directM3u8.indexOf('&');
                            if (ampIdx != -1) directM3u8 = directM3u8.substring(0, ampIdx);
                            if (!seenUrls.contains(directM3u8)) {
                                seenUrls.add(directM3u8);
                                String label = serverName + " (Player HLS)";
                                result.add(new VideoLink(VideoLink.TYPE_M3U8, directM3u8, label, serverName, langName));
                            }
                        } else if (!seenUrls.contains(embed)) {
                            seenUrls.add(embed);
                            String label = serverName + " (Embed)";
                            result.add(new VideoLink(VideoLink.TYPE_EMBED, embed, label, serverName, langName));
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // 4. TIỆN ÍCH TÌM KIẾM THÔNG MINH (SMART SEARCH)
    // ==========================================

    /**
     * Xây dựng URL tìm kiếm từ từ khóa hoặc bộ lọc thông minh.
     */
    public String buildSearchUrl(String baseUrl, String query, int page) {
        String base = cleanUrl(baseUrl);
        if (query == null || query.trim().isEmpty()) {
            return base + "/api/film/danh-sach/phim-moi?page=" + page;
        }

        String cleanedQuery = query.trim();

        // 1. Phân tích các bộ lọc dạng tag (#phimbo, #phimle, #hanquoc, #trungquoc)
        if (cleanedQuery.contains("#phimbo") || cleanedQuery.contains("#bo")) {
            return base + "/api/film/danh-sach/phim-bo?page=" + page;
        }
        if (cleanedQuery.contains("#phimle") || cleanedQuery.contains("#le")) {
            return base + "/api/film/danh-sach/phim-le?page=" + page;
        }
        if (cleanedQuery.contains("#hoathinh") || cleanedQuery.contains("#anime")) {
            return base + "/api/film/danh-sach/hoat-hinh?page=" + page;
        }

        // Encode query
        try {
            String encoded = java.net.URLEncoder.encode(cleanedQuery, "UTF-8").replace("+", "%20");
            return base + "/api/film/tim-kiem?keyword=" + encoded + "&page=" + page;
        } catch (Exception e) {
            return base + "/api/film/tim-kiem?keyword=" + cleanedQuery + "&page=" + page;
        }
    }

    // ==========================================
    // 5. CÁC HÀM HELPER BỔ TRỢ
    // ==========================================

    private String cleanUrl(String url) {
        if (url == null || url.isEmpty()) return DEFAULT_BASE_URL;
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private String normalizeImageUrl(String imgUrl) {
        if (imgUrl == null || imgUrl.trim().isEmpty()) return null;
        String u = imgUrl.trim();
        if (u.startsWith("//")) {
            return "https:" + u;
        }
        if (!u.startsWith("http")) {
            return "https://img.ophim.live/uploads/movies/" + (u.startsWith("/") ? u.substring(1) : u);
        }
        return u;
    }

    private String toShortLang(String lang) {
        String l = lang.toLowerCase();
        if (l.contains("lồng tiếng") || l.contains("long tieng") || l.contains("lt")) return "LT";
        if (l.contains("thuyết minh") || l.contains("thuyet minh") || l.contains("tm")) return "TM";
        if (l.contains("song ngữ") || l.contains("song ngu") || l.contains("sn")) return "SN";
        if (l.contains("vietsub") || l.contains("vs")) return "VS";
        return "";
    }

    private String getLangName(String serverName) {
        String s = serverName.toLowerCase();
        if (s.contains("thuyết minh") || s.contains("tm")) return "Thuyết Minh";
        if (s.contains("lồng tiếng") || s.contains("lt")) return "Lồng Tiếng";
        if (s.contains("song ngữ") || s.contains("sn")) return "Song Ngữ";
        return "Vietsub";
    }

    /**
     * Trích xuất mảng JSON từ Next.js RSC chunks dạng `self.__next_f.push([1, "..."])`
     */
    private String extractJsonArrayFromNextChunks(String html, String marker) {
        int idx = html.indexOf(marker);
        if (idx == -1) return null;

        int startPos = html.indexOf('[', idx);
        if (startPos == -1) return null;

        int depth = 0;
        int endIdx = -1;
        for (int i = startPos; i < html.length(); i++) {
            char c = html.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) {
                    endIdx = i;
                    break;
                }
            }
        }

        if (endIdx == -1) return null;

        return html.substring(startPos, endIdx + 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace(":\"$undefined\"", ":null");
    }
}
