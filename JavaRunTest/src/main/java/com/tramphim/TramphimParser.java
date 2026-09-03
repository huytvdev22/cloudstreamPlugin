package com.tramphim;

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

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TramphimParser - Phân tích cú pháp & bóc tách dữ liệu cho nguồn Trạm Phim (tramphim.top / tramphim4.org).
 *
 * Triển khai theo kiến trúc Hybrid Pattern:
 * - 100% Java 8 thuần túy, độc lập với Android SDK.
 * - Hỗ trợ bóc tách Domain động từ Portal https://tramphim.top.
 * - Hỗ trợ phân giải Next.js RSC payload, JSON-LD Schema.
 * - Giải mã bảo vệ M3U8 Master Playlist StreamC bằng AES-256-GCM + HMAC-SHA256 key derivation.
 */
public class TramphimParser implements MovieParser {

    public static final String PORTAL_URL = "https://tramphim.top";
    public static final String DEFAULT_BASE_URL = "https://tramphim4.org";
    private static final String STREAMC_SECRET_KEY = "stream-derive-v1";

    private static final TramphimParser INSTANCE = new TramphimParser();

    public static TramphimParser getInstance() {
        return INSTANCE;
    }

    // =========================================================================
    // 0. CHECK & TRÍCH XUẤT TÊN MIỀN ĐỘNG (DYNAMIC DOMAIN PARSER)
    // =========================================================================

    /**
     * Bóc tách tên miền mới nhất từ HTML của trang portal (tramphim.top).
     *
     * @param html Nội dung HTML của trang portal
     * @return Tên miền chính xác đang hoạt động (ví dụ: https://tramphim4.org)
     */
    public String parseDomain(String html) {
        if (html == null || html.trim().isEmpty()) {
            return DEFAULT_BASE_URL;
        }

        Document doc = Jsoup.parse(html);

        // 1. Thẻ nút chuyển hướng chính: a.btn-redirect[href]
        Element btnRedirect = doc.selectFirst("a.btn-redirect[href]");
        if (btnRedirect != null) {
            String href = btnRedirect.attr("href").trim();
            if (isValidDomain(href) && !isPortalUrl(href)) {
                return cleanDomain(href);
            }
        }

        // 2. Thẻ logo link: a.logo-link[href]
        Element logoLink = doc.selectFirst("a.logo-link[href]");
        if (logoLink != null) {
            String href = logoLink.attr("href").trim();
            if (isValidDomain(href) && !isPortalUrl(href)) {
                return cleanDomain(href);
            }
        }

        // 3. Script đếm ngược chuyển hướng: const targetUrl = "https://...";
        Matcher targetMatcher = Pattern.compile("targetUrl\\s*=\\s*[\"'](https?://[^\"']+)[\"']").matcher(html);
        if (targetMatcher.find()) {
            String target = targetMatcher.group(1).trim();
            if (isValidDomain(target) && !isPortalUrl(target)) {
                return cleanDomain(target);
            }
        }

        // 4. Regex tìm URL dạng https://tramphim[0-9]*.(org|net|com|tv|uk|vip)
        Matcher regexMatcher = Pattern.compile("(https?://(?:www\\.)?tramphim[0-9]*\\.[a-z]{2,6})", Pattern.CASE_INSENSITIVE).matcher(html);
        while (regexMatcher.find()) {
            String candidate = regexMatcher.group(1);
            if (isValidDomain(candidate) && !isPortalUrl(candidate)) {
                return cleanDomain(candidate);
            }
        }

        return DEFAULT_BASE_URL;
    }

    private boolean isPortalUrl(String url) {
        if (url == null) return false;
        String clean = cleanDomain(url);
        return clean.equalsIgnoreCase(PORTAL_URL) || clean.equalsIgnoreCase("https://tramphim.top") || clean.equalsIgnoreCase("http://tramphim.top");
    }

    private boolean isValidDomain(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private String cleanDomain(String url) {
        String cleaned = url.trim();
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    // =========================================================================
    // 1. PARSE DANH MỤC TRANG CHỦ (MAIN PAGE SECTIONS)
    // =========================================================================

    @Override
    public List<MainPageSection> parseMainPage(String html, String baseUrl) {
        List<MainPageSection> sections = new ArrayList<>();
        String base = (baseUrl != null && !baseUrl.isEmpty()) ? cleanDomain(baseUrl) : DEFAULT_BASE_URL;

        // Nếu có HTML, thử trích xuất các section từ DOM
        if (html != null && !html.trim().isEmpty()) {
            Document doc = Jsoup.parse(html, base);
            Elements h2Elements = doc.select("h2");
            Set<String> seenPaths = new HashSet<>();

            for (Element h2 : h2Elements) {
                String title = h2.text().trim();
                if (title.isEmpty()) continue;

                Element parent = h2.parent();
                if (parent == null) continue;

                Element linkEl = parent.selectFirst("a[href]");
                if (linkEl == null && parent.parent() != null) {
                    linkEl = parent.parent().selectFirst("a[href]");
                }

                if (linkEl != null) {
                    String href = linkEl.attr("href").trim();
                    if (!href.isEmpty() && !href.startsWith("/phim/") && !href.contains("javascript:")) {
                        if (href.startsWith("http")) {
                            try {
                                java.net.URI uri = new java.net.URI(href);
                                href = (uri.getRawPath() != null ? uri.getRawPath() : "") +
                                       (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
                            } catch (Exception ignored) {}
                        }
                        if (!href.startsWith("/")) href = "/" + href;
                        if (!seenPaths.contains(href)) {
                            seenPaths.add(href);
                            sections.add(new MainPageSection(title, href));
                        }
                    }
                }
            }
        }

        // Nếu danh sách rỗng, sử dụng danh mục tiêu chuẩn của Trạm Phim
        if (sections.isEmpty()) {
            sections.add(new MainPageSection("🔥 Phim Lẻ Mới Cập Nhật", "/phim-le"));
            sections.add(new MainPageSection("📺 Phim Bộ Mới Nhất", "/phim-bo"));
            sections.add(new MainPageSection("🍿 Phim Chiếu Rạp", "/phim-chieu-rap"));
            sections.add(new MainPageSection("🎌 Phim Hoạt Hình & Anime", "/hoat-hinh"));
            sections.add(new MainPageSection("💎 Phim 4K Siêu Nét", "/phim-4k"));
            sections.add(new MainPageSection("🇰🇷 Phim Hàn Quốc", "/quoc-gia/han-quoc"));
            sections.add(new MainPageSection("🇨🇳 Phim Trung Quốc", "/quoc-gia/trung-quoc"));
            sections.add(new MainPageSection("⚔️ Thể Loại Hành Động", "/the-loai/hanh-dong"));
        }

        return sections;
    }

    // =========================================================================
    // 2. PARSE DANH SÁCH PHIM (LISTING & SEARCH RESULTS)
    // =========================================================================

    @Override
    public List<MovieItem> parseMovieList(String html, String baseUrl) {
        List<MovieItem> movies = new ArrayList<>();
        if (html == null || html.trim().isEmpty()) {
            return movies;
        }

        String base = (baseUrl != null && !baseUrl.isEmpty()) ? cleanDomain(baseUrl) : DEFAULT_BASE_URL;
        Document doc = Jsoup.parse(html, base);
        Set<String> seenUrls = new HashSet<>();

        // Tìm tất cả các thẻ liên kết dẫn tới /phim/
        Elements links = doc.select("a[href*='/phim/']");
        for (Element link : links) {
            String href = link.attr("href").trim();
            if (href.isEmpty()) continue;

            // Chuẩn hóa link phim: loại bỏ tham số ?tap= hoặc hash
            href = href.replaceAll("(\\?.*|#.*)$", "");
            if (href.contains("/tap-")) {
                href = href.replaceAll("/tap-[^/?#]+", "");
            }

            String fullUrl = HtmlHelper.getAbsoluteUrl(base, doc.createElement("a").attr("href", href), "href");
            if (seenUrls.contains(fullUrl)) continue;

            // Tìm ảnh poster
            Element img = link.selectFirst("img");
            String posterUrl = "";
            if (img != null) {
                posterUrl = extractPosterUrl(img, base);
            }

            // Tìm tiêu đề phim
            String title = "";
            Element titleEl = link.selectFirst("h2, h3, p.font-bold, p.font-semibold, p.line-clamp-1, span.line-clamp-1");
            if (titleEl != null) {
                title = titleEl.text().trim();
            }
            if (title.isEmpty() && img != null) {
                title = img.attr("alt").trim();
            }

            if (title.isEmpty()) continue;

            // Bóc tách tags/badges (HD, Vietsub, Thuyết minh, Tập xx...)
            List<String> tags = new ArrayList<>();
            Elements badges = link.select("span, div");
            for (Element b : badges) {
                String bText = b.ownText().trim();
                if (isBadgeText(bText) && !tags.contains(bText)) {
                    tags.add(bText);
                }
            }

            seenUrls.add(fullUrl);
            movies.add(new MovieItem(title, fullUrl, posterUrl, tags));
        }

        return movies;
    }

    private boolean isBadgeText(String text) {
        if (text == null || text.length() > 25 || text.isEmpty()) return false;
        String lower = text.toLowerCase();
        return lower.contains("hd") || lower.contains("fhd") || lower.contains("4k")
                || lower.contains("vietsub") || lower.contains("thuyết minh") || lower.contains("lồng tiếng")
                || lower.contains("tập") || lower.contains("full") || lower.contains("cam");
    }

    private String extractPosterUrl(Element img, String base) {
        String src = img.attr("src").trim();
        if (src.isEmpty() || src.startsWith("data:")) {
            src = img.attr("data-src").trim();
        }
        if (src.isEmpty() || src.startsWith("data:")) {
            src = img.attr("srcset").trim();
            if (!src.isEmpty()) {
                String[] parts = src.split(",");
                if (parts.length > 0) {
                    src = parts[parts.length - 1].trim().split(" ")[0];
                }
            }
        }
        if (!src.isEmpty() && !src.startsWith("http")) {
            src = HtmlHelper.getAbsoluteUrl(base, img.clone().attr("href", src), "href");
        }
        return src;
    }

    // =========================================================================
    // 3. PARSE CHI TIẾT PHIM & DANH SÁCH TẬP (MOVIE DETAIL & EPISODES)
    // =========================================================================

    @Override
    public MovieDetail parseMovieDetail(String html, String baseUrl) {
        if (html == null || html.trim().isEmpty()) {
            return new MovieDetail("", "", "", null, null, new ArrayList<>(), new ArrayList<>());
        }

        String base = (baseUrl != null && !baseUrl.isEmpty()) ? cleanDomain(baseUrl) : DEFAULT_BASE_URL;
        Document doc = Jsoup.parse(html, base);

        String title = "";
        String posterUrl = "";
        String plot = "";
        Integer year = null;
        Integer duration = null;
        List<String> tags = new ArrayList<>();

        // A. Trích xuất Schema JSON-LD (Movie hoặc TVSeries)
        Elements scripts = doc.select("script[type='application/ld+json']");
        for (Element script : scripts) {
            String jsonStr = script.data().trim();
            try {
                if (jsonStr.startsWith("{")) {
                    JSONObject json = new JSONObject(jsonStr);
                    String type = json.optString("@type");
                    if ("Movie".equalsIgnoreCase(type) || "TVSeries".equalsIgnoreCase(type)) {
                        title = json.optString("name", title);
                        posterUrl = json.optString("image", posterUrl);
                        plot = json.optString("description", plot);

                        String datePub = json.optString("datePublished", json.optString("dateCreated"));
                        if (!datePub.isEmpty()) {
                            year = RegexHelper.parseInt(datePub, "(\\d{4})");
                        }

                        JSONArray genreArr = json.optJSONArray("genre");
                        if (genreArr != null) {
                            for (int i = 0; i < genreArr.length(); i++) {
                                String g = genreArr.getString(i).trim();
                                if (!g.isEmpty() && !tags.contains(g)) tags.add(g);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // B. Bổ sung từ HTML DOM nếu thiếu
        if (title.isEmpty()) {
            title = HtmlHelper.selectFirstText(doc, "h1");
        }
        if (plot.isEmpty()) {
            Element metaDesc = doc.selectFirst("meta[name='description'], meta[property='og:description']");
            if (metaDesc != null) plot = metaDesc.attr("content").trim();
        }
        if (posterUrl.isEmpty()) {
            Element metaImg = doc.selectFirst("meta[property='og:image']");
            if (metaImg != null) posterUrl = metaImg.attr("content").trim();
        }
        if (year == null) {
            year = RegexHelper.parseInt(html, "\\b(19\\d{2}|20\\d{2})\\b");
        }
        if (duration == null) {
            duration = RegexHelper.parseInt(html, "(\\d+)\\s*(?:phút|mins|min)");
        }

        // C. Trích xuất danh sách tập (Episodes)
        List<EpisodeItem> episodes = new ArrayList<>();
        Map<String, EpisodeItem> epMap = new LinkedHashMap<>();

        // C1. Bóc tách từ Next.js RSC payload / scripts có chứa "episodes" & "server_data"
        parseEpisodesFromNextRsc(html, base, epMap);

        // C2. Bóc tách từ HTML DOM: các thẻ a hoặc button tập phim
        if (epMap.isEmpty()) {
            Elements epElements = doc.select("a[href*='tap='], a[href*='/tap-'], button[data-slug]");
            int idx = 1;
            for (Element epEl : epElements) {
                String epHref = epEl.attr("href").trim();
                String epName = epEl.text().trim();
                if (epName.isEmpty()) epName = "Tập " + idx;
                Integer epNum = RegexHelper.parseInt(epName, "(\\d+)");
                if (epNum == null) epNum = idx;

                String absUrl = HtmlHelper.getAbsoluteUrl(base, doc.createElement("a").attr("href", epHref), "href");
                if (!epMap.containsKey(absUrl)) {
                    epMap.put(absUrl, new EpisodeItem(absUrl, epName, epNum));
                    idx++;
                }
            }
        }

        // Fallback: nếu vẫn không có tập (ví dụ phim lẻ 1 tập Full)
        if (epMap.isEmpty()) {
            epMap.put(base, new EpisodeItem(base, "Full", 1));
        }

        episodes.addAll(epMap.values());
        return new MovieDetail(title, posterUrl, plot, year, duration, tags, episodes);
    }

    /**
     * Phân giải danh sách tập từ Next.js React Server Components (RSC) payload nhúng trong script.
     */
    private void parseEpisodesFromNextRsc(String html, String base, Map<String, EpisodeItem> epMap) {
        if (html == null || html.isEmpty()) return;
        try {
            String jsonArrayStr = extractJsonArrayByKey(html, "episodes");
            if (jsonArrayStr == null || jsonArrayStr.isEmpty()) return;

            String unescaped = jsonArrayStr.replace("\\\"", "\"").replace("\\\\", "\\");
            JSONArray epServers = new JSONArray(unescaped);
            int epIndex = 1;

            for (int i = 0; i < epServers.length(); i++) {
                JSONObject server = epServers.optJSONObject(i);
                if (server == null) continue;
                String serverName = server.optString("server_name", "Vietsub");
                JSONArray serverData = server.optJSONArray("server_data");
                if (serverData == null) continue;

                for (int j = 0; j < serverData.length(); j++) {
                    JSONObject epObj = serverData.optJSONObject(j);
                    if (epObj == null) continue;

                    String name = epObj.optString("name", "").trim();
                    String slug = epObj.optString("slug", "").trim();
                    String linkEmbed = epObj.optString("link_embed", "").trim();
                    String linkM3u8 = epObj.optString("link_m3u8", "").trim();

                    if (name.isEmpty()) name = "Tập " + epIndex;
                    else if (!name.toLowerCase().startsWith("tập") && !name.equalsIgnoreCase("full")) {
                        name = "Tập " + name;
                    }

                    Integer epNum = RegexHelper.parseInt(name, "(\\d+)");
                    if (epNum == null) epNum = epIndex;

                    // URL tập phim: ưu tiên link embed nếu có để truyền thẳng sang extractVideoLinks
                    String epUrl = !linkEmbed.isEmpty() ? linkEmbed : (!linkM3u8.isEmpty() ? linkM3u8 : slug);

                    String displayName = name;
                    if (!serverName.isEmpty() && !serverName.equalsIgnoreCase("Vietsub #1") && !serverName.equalsIgnoreCase("Vietsub")) {
                        displayName += " (" + serverName + ")";
                    }

                    if (!epMap.containsKey(epUrl)) {
                        epMap.put(epUrl, new EpisodeItem(epUrl, displayName, epNum));
                        epIndex++;
                    }
                }
            }
        } catch (Exception e) {
            // Lỗi parse Next RSC được bỏ qua an toàn
        }
    }

    /**
     * Bóc tách một JSON Array [...] bắt đầu sau key cho trước, sử dụng bộ đếm ngoặc (bracket counter).
     */
    private String extractJsonArrayByKey(String content, String key) {
        String[] prefixes = new String[]{
                "\"" + key + "\":[",
                "\\\"" + key + "\\\":[",
                "\"" + key + "\": [",
                "\\\"" + key + "\\\": ["
        };

        int start = -1;
        for (String p : prefixes) {
            int idx = content.indexOf(p);
            if (idx != -1) {
                start = content.indexOf('[', idx);
                break;
            }
        }

        if (start == -1) return null;

        int depth = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = start; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (ch == '\\') {
                escape = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (ch == '[') depth++;
                else if (ch == ']') {
                    depth--;
                    if (depth == 0) {
                        return content.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }

    // =========================================================================
    // 4. TRÍCH XUẤT LINK VIDEO STREAMING (EXTRACT VIDEO LINKS & DECRYPT STREAMC)
    // =========================================================================

    @Override
    public List<VideoLink> extractVideoLinks(String html, String slugOrData) {
        List<VideoLink> links = new ArrayList<>();
        if (html == null) html = "";

        Set<String> seenUrls = new HashSet<>();

        // 1. Kiểm tra nếu input là trang Player StreamC (có chứa id="player" và data-obf)
        if (html.contains("id=\"player\"") && html.contains("data-obf=")) {
            List<VideoLink> streamcLinks = parseStreamcPlayer(html, slugOrData);
            for (VideoLink l : streamcLinks) {
                if (!seenUrls.contains(l.url)) {
                    seenUrls.add(l.url);
                    links.add(l);
                }
            }
        }

        // 2. Tìm link embed StreamC từ HTML trang phim (Next RSC hoặc iframe)
        Matcher embedMatcher = Pattern.compile("(https?://embed[0-9]*\\.streamc\\.xyz/embed\\.php\\?hash=[a-zA-Z0-9]+)").matcher(html);
        while (embedMatcher.find()) {
            String embedUrl = embedMatcher.group(1);
            if (!seenUrls.contains(embedUrl)) {
                seenUrls.add(embedUrl);
                links.add(new VideoLink(VideoLink.TYPE_EMBED, embedUrl, "StreamC Embed", "StreamC", "Vietsub"));
            }
        }

        // 3. Tìm link M3U8 trực tiếp từ HTML nếu có
        Matcher m3u8Matcher = Pattern.compile("(https?://[^\"'\\s&<>]+\\.m3u8(?:\\?[^\"'\\s&<>]*)?)").matcher(html);
        while (m3u8Matcher.find()) {
            String m3u8Url = m3u8Matcher.group(1);
            if (!seenUrls.contains(m3u8Url)) {
                seenUrls.add(m3u8Url);
                links.add(new VideoLink(VideoLink.TYPE_M3U8, m3u8Url, "Trạm Phim M3U8 Direct", "VIP", "Vietsub"));
            }
        }

        return links;
    }

    /**
     * Bóc tách thông tin từ trang StreamC Embed và tạo URL stream M3U8.
     */
    public List<VideoLink> parseStreamcPlayer(String html, String embedPageUrl) {
        List<VideoLink> links = new ArrayList<>();
        try {
            Document doc = Jsoup.parse(html);
            Element playerEl = doc.selectFirst("#player[data-obf]");
            if (playerEl == null) return links;

            String dataObf = playerEl.attr("data-obf").trim();
            if (dataObf.isEmpty()) return links;

            // Decode base64
            byte[] decoded = Base64.getDecoder().decode(dataObf);
            String jsonStr = new String(decoded, StandardCharsets.UTF_8);
            JSONObject obfJson = new JSONObject(jsonStr);

            String sUb = obfJson.optString("sUb");
            String videoHash = obfJson.optString("hD");

            if (!sUb.isEmpty()) {
                String origin = "https://embed14.streamc.xyz";
                if (embedPageUrl != null && embedPageUrl.startsWith("http")) {
                    try {
                        java.net.URI uri = new java.net.URI(embedPageUrl);
                        origin = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
                    } catch (Exception ignored) {}
                }

                String streamUrl = origin + "/" + sUb;
                links.add(new VideoLink(VideoLink.TYPE_M3U8, streamUrl, "StreamC HLS (AES-GCM)", "StreamC VIP", "Vietsub"));

                // Lưu thêm videoHash trong label hoặc server name để adapter có thể dùng
                String streamUrlWithHash = streamUrl + "#hash=" + videoHash;
                links.add(new VideoLink(VideoLink.TYPE_M3U8, streamUrlWithHash, "StreamC HLS Playlist", "StreamC", videoHash));
            }
        } catch (Exception ignored) {}
        return links;
    }

    /**
     * Giải mã nội dung M3U8 Master Playlist của StreamC được mã hóa bằng AES-256-GCM.
     *
     * @param encryptedM3u8 Nội dung playlist thô nhận từ máy chủ (chứa #ENC-AESGCM;iv=... và Base64 ciphertext)
     * @param videoHash     Chuỗi hash của video (trích xuất từ hD trong data-obf)
     * @return Nội dung M3U8 đã được giải mã hoàn chỉnh (chứa các chunk .png / .ts)
     * @throws Exception khi lỗi giải mã hoặc sai khóa
     */
    public String decryptStreamcM3u8(String encryptedM3u8, String videoHash) throws Exception {
        if (encryptedM3u8 == null || encryptedM3u8.isEmpty()) {
            throw new IllegalArgumentException("encryptedM3u8 cannot be empty");
        }
        if (videoHash == null || videoHash.isEmpty()) {
            throw new IllegalArgumentException("videoHash cannot be empty");
        }

        String[] lines = encryptedM3u8.split("\n");
        String ivHex = null;
        String b64Ciphertext = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#ENC-AESGCM")) {
                Matcher m = Pattern.compile("iv=([0-9a-fA-F]+)").matcher(trimmed);
                if (m.find()) {
                    ivHex = m.group(1);
                }
            } else if (!trimmed.startsWith("#") && !trimmed.isEmpty()) {
                b64Ciphertext = trimmed;
            }
        }

        if (ivHex == null || b64Ciphertext == null) {
            throw new IllegalStateException("Could not find IV or ciphertext in M3U8");
        }

        // 1. Key Derivation: HMAC-SHA256 với secret "stream-derive-v1" và data là videoHash
        Mac hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(STREAMC_SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        hmac.init(secretKeySpec);
        byte[] hmacResult = hmac.doFinal(videoHash.getBytes(StandardCharsets.UTF_8));
        byte[] aesKey = Arrays.copyOfRange(hmacResult, 0, 32); // 256-bit AES Key

        // 2. Chuyển đổi IV từ Hex sang bytes (12 bytes)
        byte[] iv = hexToBytes(ivHex);

        // 3. Giải mã Base64 của ciphertext (bao gồm cả 16 bytes GCM Auth Tag ở cuối)
        byte[] cipherBytes = Base64.getDecoder().decode(b64Ciphertext);

        // 4. Giải mã AES-256-GCM
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

        byte[] decryptedBytes = cipher.doFinal(cipherBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    // =========================================================================
    // 5. SMART SEARCH URL BUILDER
    // =========================================================================

    /**
     * Tạo URL tìm kiếm phim chuẩn hóa trên Trạm Phim.
     *
     * @param baseUrl URL gốc (ví dụ: https://tramphim4.org)
     * @param query   Từ khóa tìm kiếm
     * @param page    Số trang (bắt đầu từ 1)
     * @return URL tìm kiếm hoàn chỉnh
     */
    public String buildSearchUrl(String baseUrl, String query, int page) {
        String base = (baseUrl != null && !baseUrl.isEmpty()) ? cleanDomain(baseUrl) : DEFAULT_BASE_URL;
        String encodedQuery = "";
        try {
            encodedQuery = URLEncoder.encode(query.trim(), "UTF-8");
        } catch (Exception e) {
            encodedQuery = query.trim();
        }

        String searchUrl = base + "/tim-kiem?keyword=" + encodedQuery;
        if (page > 1) {
            searchUrl += "&page=" + page;
        }
        return searchUrl;
    }
}
