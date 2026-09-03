package com.motchill;

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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MotchillParser - Trình phân tích cú pháp & bóc tách dữ liệu cho motchillw.sh.
 *
 * Triển khai interface {@link MovieParser} theo chuẩn kiến trúc Hybrid Pattern:
 * - 100% Java 8 thuần túy, độc lập hoàn toàn với Android framework.
 * - Xử lý bóc tách Jsoup DOM, Schema.org JSON-LD và Next.js React Server Components (RSC).
 * - Hỗ trợ trích xuất luồng video HLS (.m3u8) trực tiếp từ RSC payload và KKPhim/Vsmov CDN.
 */
public class MotchillParser implements MovieParser {

    public static final String PORTAL_URL = "https://motchillw.sh";
    public static final String DEFAULT_BASE_URL = "https://motchillw.sh";
    private static final MotchillParser INSTANCE = new MotchillParser();

    public static MotchillParser getInstance() {
        return INSTANCE;
    }

    // =========================================================================
    // 0. CHECK & TRÍCH XUẤT TÊN MIỀN ĐỘNG (DYNAMIC DOMAIN PARSER)
    // =========================================================================

    /**
     * Bóc tách tên miền mới nhất từ HTML của trang portal/chính.
     * Tự động thích ứng khi website đổi domain mà không cần cập nhật plugin.
     */
    public String parseDomain(String html) {
        if (html == null || html.trim().isEmpty()) {
            return DEFAULT_BASE_URL;
        }

        try {
            Document doc = Jsoup.parse(html);

            // 1. Thẻ link rel="canonical"
            Element canonical = doc.selectFirst("link[rel='canonical'][href]");
            if (canonical != null) {
                String href = canonical.attr("href").trim();
                if (isValidDomain(href)) {
                    return cleanDomain(href);
                }
            }

            // 2. Thẻ link rel="alternate"
            Element altLink = doc.selectFirst("link[rel='alternate'][href]");
            if (altLink != null) {
                String href = altLink.attr("href").trim();
                if (isValidDomain(href)) {
                    return cleanDomain(href);
                }
            }

            // 3. Schema JSON-LD WebSite url
            Elements jsonLds = doc.select("script[type='application/ld+json']");
            for (Element s : jsonLds) {
                String data = s.data();
                Matcher m = Pattern.compile("\"url\"\\s*:\\s*\"(https?://[^\"/?#]+)").matcher(data);
                if (m.find()) {
                    String domain = m.group(1);
                    if (isValidDomain(domain)) {
                        return cleanDomain(domain);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return DEFAULT_BASE_URL;
    }

    private boolean isValidDomain(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"))
                && !url.contains("facebook.com") && !url.contains("schema.org")
                && !url.contains("twitter.com") && !url.contains("google.com");
    }

    private String cleanDomain(String url) {
        try {
            int schemeEnd = url.indexOf("://");
            if (schemeEnd == -1) return DEFAULT_BASE_URL;
            int hostEnd = url.indexOf("/", schemeEnd + 3);
            if (hostEnd == -1) return url;
            return url.substring(0, hostEnd);
        } catch (Exception e) {
            return DEFAULT_BASE_URL;
        }
    }

    // =========================================================================
    // 1. DANH MỤC TRANG CHỦ (SECTIONS)
    // =========================================================================

    @Override
    public List<MainPageSection> parseMainPage(String content, String baseUrl) {
        List<MainPageSection> sections = new ArrayList<>();
        sections.add(new MainPageSection("🔥 Phim Mới Cập Nhật", "/"));
        sections.add(new MainPageSection("📺 Phim Bộ Mới Nhất", "/danh-sach/phim-bo"));
        sections.add(new MainPageSection("🎬 Phim Lẻ Mới Nhất", "/danh-sach/phim-le"));
        sections.add(new MainPageSection("⛩️ Phim Hoạt Hình", "/the-loai/hoat-hinh"));
        sections.add(new MainPageSection("💥 Phim Hành Động", "/the-loai/hanh-dong"));
        sections.add(new MainPageSection("💖 Phim Tình Cảm", "/the-loai/tinh-cam"));
        sections.add(new MainPageSection("🎎 Phim Cổ Trang", "/the-loai/co-trang"));
        sections.add(new MainPageSection("👻 Phim Kinh Dị", "/the-loai/kinh-di"));
        sections.add(new MainPageSection("🐉 Phim Trung Quốc", "/quoc-gia/trung-quoc"));
        sections.add(new MainPageSection("🌸 Phim Hàn Quốc", "/quoc-gia/han-quoc"));
        sections.add(new MainPageSection("🗽 Phim Âu Mỹ", "/quoc-gia/au-my"));
        return sections;
    }

    // =========================================================================
    // 2. DANH SÁCH PHIM (MOVIE LISTING)
    // =========================================================================

    @Override
    public List<MovieItem> parseMovieList(String html, String baseUrl) {
        List<MovieItem> list = new ArrayList<>();
        if (html == null || html.trim().isEmpty()) {
            return list;
        }

        Document doc = Jsoup.parse(html, baseUrl);
        Set<String> seen = new HashSet<>();

        // Tìm tất cả các thẻ a trỏ đến link phim
        Elements cards = doc.select("a[href*='/phim/']");
        for (Element card : cards) {
            String href = HtmlHelper.getAbsoluteUrl(baseUrl, card, "href");
            if (href.isEmpty()) continue;

            // Chuẩn hóa href: loại bỏ các hậu tố tập phim như /k-tap-01, /tap-1, /full
            String cleanUrl = href.replaceAll("(/k-tap-[^/?#]+|/tap-[^/?#]+|/full)(\\?.*)?$", "");
            cleanUrl = cleanUrl.replaceAll("\\?.*$", "");
            if (!cleanUrl.matches(".*/phim/[^/]+$")) {
                continue;
            }

            if (seen.contains(cleanUrl)) {
                continue;
            }

            // Bóc tách tiêu đề
            String title = card.attr("title").trim();
            if (title.isEmpty()) {
                Element img = card.selectFirst("img");
                if (img != null) {
                    title = img.attr("alt").trim();
                }
            }
            if (title.isEmpty()) {
                title = HtmlHelper.selectFirstText(card, "h2, h3, .title");
            }
            if (title.isEmpty()) {
                continue; // Bỏ qua nếu không có tiêu đề
            }

            // Bóc tách poster
            Element img = card.selectFirst("img");
            String poster = "";
            if (img != null) {
                poster = img.attr("src");
                if (poster.isEmpty() || poster.startsWith("data:")) {
                    poster = img.attr("data-src");
                }
            }
            if (!poster.isEmpty()) {
                if (poster.startsWith("/")) {
                    poster = baseUrl + poster;
                }
            }

            // Bóc tách badges (Vietsub, Thuyết Minh, FHD, HD, Số tập)
            List<String> tags = new ArrayList<>();
            Elements badges = card.select("span, div");
            for (Element badge : badges) {
                String text = badge.ownText().trim();
                if (text.isEmpty()) continue;
                if (text.equalsIgnoreCase("FHD") || text.equalsIgnoreCase("HD")
                        || text.equalsIgnoreCase("Vietsub") || text.equalsIgnoreCase("Thuyết Minh")
                        || text.contains("Tập") || text.contains("Trọn bộ")) {
                    if (!tags.contains(text)) {
                        tags.add(text);
                    }
                }
            }

            seen.add(cleanUrl);
            list.add(new MovieItem(title, cleanUrl, poster, tags));
        }

        return list;
    }

    // =========================================================================
    // 3. CHI TIẾT PHIM & TẬP PHIM (MOVIE DETAIL & EPISODES)
    // =========================================================================

    @Override
    public MovieDetail parseMovieDetail(String html, String baseUrl) {
        if (html == null || html.trim().isEmpty()) {
            return new MovieDetail("", "", "", null, null, new ArrayList<String>(), new ArrayList<EpisodeItem>());
        }

        Document doc = Jsoup.parse(html, baseUrl);

        String title = "";
        String posterUrl = "";
        String plot = "";
        Integer year = null;
        Integer duration = null;
        List<String> tags = new ArrayList<>();
        List<EpisodeItem> episodes = new ArrayList<>();

        // 1. Trích xuất metadata từ Schema.org JSON-LD
        Elements jsonLds = doc.select("script[type='application/ld+json']");
        for (Element s : jsonLds) {
            String data = s.data().trim();
            if (data.contains("\"TVSeries\"") || data.contains("\"Movie\"")) {
                try {
                    JSONObject obj = new JSONObject(data);
                    if (obj.has("name")) {
                        title = obj.getString("name").trim();
                    }
                    if (obj.has("description")) {
                        plot = obj.getString("description").trim();
                    }
                    if (obj.has("thumbnailUrl")) {
                        posterUrl = obj.getString("thumbnailUrl").trim();
                    } else if (obj.has("image")) {
                        posterUrl = obj.getString("image").trim();
                    }
                    if (obj.has("datePublished")) {
                        String dateStr = obj.getString("datePublished");
                        year = RegexHelper.parseInt(dateStr, "(\\d{4})");
                    }
                    if (obj.has("genre")) {
                        JSONArray genreArr = obj.getJSONArray("genre");
                        for (int i = 0; i < genreArr.length(); i++) {
                            String g = genreArr.getString(i).trim();
                            if (!g.isEmpty() && !tags.contains(g)) {
                                tags.add(g);
                            }
                        }
                    }
                    break;
                } catch (Exception ignored) {
                }
            }
        }

        // 2. Fallback tiêu đề, poster, plot từ HTML nếu JSON-LD còn thiếu
        if (title.isEmpty()) {
            title = HtmlHelper.selectFirstText(doc, "h1");
            if (title.isEmpty()) {
                Element ogTitle = doc.selectFirst("meta[property='og:title']");
                if (ogTitle != null) {
                    title = ogTitle.attr("content").trim();
                }
            }
        }

        if (posterUrl.isEmpty()) {
            Element ogImage = doc.selectFirst("meta[property='og:image']");
            if (ogImage != null) {
                posterUrl = ogImage.attr("content").trim();
            }
        }
        if (!posterUrl.isEmpty() && posterUrl.startsWith("/")) {
            posterUrl = baseUrl + posterUrl;
        }

        if (plot.isEmpty()) {
            Element ogDesc = doc.selectFirst("meta[property='og:description']");
            if (ogDesc != null) {
                plot = ogDesc.attr("content").trim();
            }
        }

        // 3. Trích xuất danh sách tập từ React Server Components (RSC) payload
        String episodesJson = extractJsonArray(html, "episodes");

        if (episodesJson != null) {
            try {
                String cleanJson = episodesJson;
                if (cleanJson.contains("\\\"")) {
                    cleanJson = cleanJson.replace("\\\"", "\"").replace("\\\\", "\\");
                }
                JSONArray arr = new JSONArray(cleanJson);

                // Gom nhóm các server theo tên tập (name)
                Map<String, List<JSONObject>> epMap = new LinkedHashMap<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject item = arr.getJSONObject(i);
                    String epName = item.optString("name", "Tập " + (i + 1)).trim();
                    if (!epMap.containsKey(epName)) {
                        epMap.put(epName, new ArrayList<JSONObject>());
                    }
                    epMap.get(epName).add(item);
                }

                String movieSlug = extractMovieSlug(doc, baseUrl);
                int episodeIndex = 1;
                for (Map.Entry<String, List<JSONObject>> entry : epMap.entrySet()) {
                    String epName = entry.getKey();
                    List<JSONObject> servers = entry.getValue();

                    // Chuẩn hóa tên tập (nếu chỉ là số "1" thì đổi thành "Tập 01" cho đẹp)
                    String displayName = epName;
                    if (displayName.matches("^\\d+$")) {
                        int num = Integer.parseInt(displayName);
                        displayName = String.format("Tập %02d", num);
                    } else if (!displayName.toLowerCase().contains("tập") && !displayName.equalsIgnoreCase("full")) {
                        displayName = "Tập " + displayName;
                    }

                    Integer epNumber = RegexHelper.parseInt(displayName, "(\\d+)");
                    if (epNumber == null) {
                        epNumber = episodeIndex;
                    }

                    // Lấy slug từ server đầu tiên
                    JSONObject firstServer = servers.get(0);
                    String slug = firstServer.optString("slug", "");
                    String epUrl = baseUrl;
                    if (!slug.isEmpty() && !movieSlug.isEmpty()) {
                        epUrl = baseUrl + "/phim/" + movieSlug + "/" + slug;
                    } else if (!movieSlug.isEmpty()) {
                        epUrl = baseUrl + "/phim/" + movieSlug;
                    }

                    // Đóng gói servers payload vào URL fragment #servers= để loadLinks xử lý tức thì
                    JSONArray serversPayload = new JSONArray();
                    for (JSONObject sObj : servers) {
                        JSONObject sData = new JSONObject();
                        sData.put("server", sObj.optString("server", "Motchill VIP"));
                        sData.put("link", sObj.optString("link", ""));
                        sData.put("type", sObj.optString("type", "m3u8"));
                        serversPayload.put(sData);
                    }

                    try {
                        epUrl += "#servers=" + URLEncoder.encode(serversPayload.toString(), StandardCharsets.UTF_8.name());
                    } catch (Exception ignored) {
                    }

                    episodes.add(new EpisodeItem(epUrl, displayName, epNumber));
                    episodeIndex++;
                }
            } catch (Exception e) {
                // Fallback nếu có lỗi parse JSON
            }
        }

        // 4. Fallback nếu không bóc tách được từ RSC: tìm thẻ HTML nút Xem Phim hoặc tập
        if (episodes.isEmpty()) {
            Elements watchBtns = doc.select("a[href*='/k-tap-'], a[href*='/tap-'], a[href*='/full']");
            if (!watchBtns.isEmpty()) {
                int epIdx = 1;
                for (Element btn : watchBtns) {
                    String href = HtmlHelper.getAbsoluteUrl(baseUrl, btn, "href");
                    String epText = btn.text().trim();
                    if (epText.isEmpty()) epText = "Tập " + epIdx;
                    Integer num = RegexHelper.parseInt(epText, "(\\d+)");
                    if (num == null) num = epIdx;
                    episodes.add(new EpisodeItem(href, epText, num));
                    epIdx++;
                }
            } else {
                // Fallback 1 tập Full trỏ đến trang hiện tại
                episodes.add(new EpisodeItem(baseUrl, "Full", 1));
            }
        }

        return new MovieDetail(title, posterUrl, plot, year, duration, tags, episodes);
    }

    private String extractMovieSlug(Document doc, String baseUrl) {
        Element canonical = doc.selectFirst("link[rel='canonical']");
        if (canonical != null) {
            String href = canonical.attr("href");
            Matcher m = Pattern.compile("/phim/([^/?#]+)").matcher(href);
            if (m.find()) {
                return m.group(1);
            }
        }
        Element ogUrl = doc.selectFirst("meta[property='og:url']");
        if (ogUrl != null) {
            String href = ogUrl.attr("content");
            Matcher m = Pattern.compile("/phim/([^/?#]+)").matcher(href);
            if (m.find()) {
                return m.group(1);
            }
        }
        return "";
    }

    // =========================================================================
    // 4. TRÍCH XUẤT LINK VIDEO STREAM (EXTRACT VIDEO LINKS)
    // =========================================================================

    @Override
    public List<VideoLink> extractVideoLinks(String html, String slugOrData) {
        List<VideoLink> links = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        String serversJson = null;

        // 1. Kiểm tra nếu slugOrData có fragment #servers= hoặc là JSON array
        if (slugOrData != null) {
            if (slugOrData.contains("#servers=")) {
                int idx = slugOrData.indexOf("#servers=");
                String encoded = slugOrData.substring(idx + "#servers=".length());
                try {
                    serversJson = URLDecoder.decode(encoded, StandardCharsets.UTF_8.name());
                } catch (Exception e) {
                    serversJson = encoded;
                }
            } else if (slugOrData.trim().startsWith("[")) {
                serversJson = slugOrData.trim();
            }
        }

        // 2. Parse từ serversJson nếu có
        if (serversJson != null) {
            try {
                JSONArray arr = new JSONArray(serversJson);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String rawLink = obj.optString("link", "").trim();
                    String serverName = obj.optString("server", "Motchill VIP").trim();

                    String directUrl = extractRealStreamUrl(rawLink);
                    if (!directUrl.isEmpty() && !seen.contains(directUrl)) {
                        seen.add(directUrl);
                        links.add(new VideoLink(VideoLink.TYPE_M3U8, directUrl, serverName, serverName, "FHD"));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 3. Nếu slugOrData là link video trực tiếp (.m3u8)
        if (links.isEmpty() && slugOrData != null && slugOrData.contains(".m3u8")) {
            String directUrl = extractRealStreamUrl(slugOrData);
            if (!directUrl.isEmpty() && !seen.contains(directUrl)) {
                seen.add(directUrl);
                links.add(new VideoLink(VideoLink.TYPE_M3U8, directUrl, "Motchill VIP", "HLS VIP", "FHD"));
            }
        }

        // 4. Bóc tách từ HTML nếu chưa có links hoặc cần bổ sung
        if (html != null && !html.trim().isEmpty()) {
            // Thử bóc tách JSON episodes từ RSC trong HTML
            String episodesJson = extractJsonArray(html, "episodes");
            if (episodesJson != null) {
                try {
                    String cleanJson = episodesJson;
                    if (cleanJson.contains("\\\"")) {
                        cleanJson = cleanJson.replace("\\\"", "\"").replace("\\\\", "\\");
                    }
                    JSONArray arr = new JSONArray(cleanJson);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String rawLink = obj.optString("link", "").trim();
                        String serverName = obj.optString("server", "Motchill VIP").trim();
                        String directUrl = extractRealStreamUrl(rawLink);
                        if (!directUrl.isEmpty() && !seen.contains(directUrl)) {
                            seen.add(directUrl);
                            links.add(new VideoLink(VideoLink.TYPE_M3U8, directUrl, serverName, serverName, "FHD"));
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            // Fallback Regex quét mọi link m3u8 trong HTML
            Pattern m3u8Pattern = Pattern.compile("(https?://[^\"'\\s<>\\]+\\.m3u8[^\"'\\s<>\\]*)");
            Matcher m = m3u8Pattern.matcher(html);
            int count = 1;
            while (m.find()) {
                String rawUrl = m.group(1);
                String directUrl = extractRealStreamUrl(rawUrl);
                if (!directUrl.isEmpty() && !seen.contains(directUrl)) {
                    seen.add(directUrl);
                    links.add(new VideoLink(VideoLink.TYPE_M3U8, directUrl, "HLS Server " + count, "Máy chủ " + count, "FHD"));
                    count++;
                }
            }
        }

        return links;
    }

    /**
     * Trích xuất mảng JSON gắn với key từ text/HTML (hỗ trợ cả escaped RSC text).
     */
    private String extractJsonArray(String text, String key) {
        if (text == null || key == null) return null;

        int keyIdx = text.indexOf(key);
        while (keyIdx != -1) {
            // Tìm dấu '[' đầu tiên sau key
            int bracketStart = text.indexOf('[', keyIdx + key.length());
            if (bracketStart != -1 && bracketStart - (keyIdx + key.length()) < 30) {
                int balance = 0;
                boolean inString = false;
                boolean isEscaped = false;

                for (int i = bracketStart; i < text.length(); i++) {
                    char c = text.charAt(i);

                    if (isEscaped) {
                        isEscaped = false;
                        continue;
                    }

                    if (c == '\\') {
                        isEscaped = true;
                        continue;
                    }

                    if (c == '"') {
                        inString = !inString;
                        continue;
                    }

                    if (!inString) {
                        if (c == '[') {
                            balance++;
                        } else if (c == ']') {
                            balance--;
                            if (balance == 0) {
                                return text.substring(bracketStart, i + 1);
                            }
                        }
                    }
                }
            }

            keyIdx = text.indexOf(key, keyIdx + key.length());
        }

        return null;
    }

    private String extractRealStreamUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) return "";
        String trimmed = rawUrl.trim();

        // Xóa backslash escape nếu có
        trimmed = trimmed.replace("\\/", "/");

        // Kiểm tra nếu là player.phimapi.com/player/?url=https://...
        if (trimmed.contains("player/?url=")) {
            int idx = trimmed.indexOf("player/?url=");
            String encoded = trimmed.substring(idx + "player/?url=".length());
            try {
                return URLDecoder.decode(encoded, StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                return encoded;
            }
        }

        return trimmed;
    }

    // =========================================================================
    // 5. SMART SEARCH URL
    // =========================================================================

    public String buildSearchUrl(String baseUrl, String query, int page) {
        if (query == null) query = "";
        String encodedQuery = "";
        try {
            encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            encodedQuery = query.trim().replace(" ", "+");
        }

        String url = baseUrl + "/search?q=" + encodedQuery;
        if (page > 1) {
            url += "&page=" + page;
        }
        return url;
    }
}
