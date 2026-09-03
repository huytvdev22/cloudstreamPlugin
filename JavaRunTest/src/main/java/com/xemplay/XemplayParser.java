package com.xemplay;

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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XemplayParser - Trình phân tích cú pháp & bóc tách dữ liệu cho xemplay.uk.
 *
 * Triển khai interface {@link MovieParser} theo chuẩn kiến trúc Hybrid Pattern:
 * - 100% Java 8 thuần túy, độc lập hoàn toàn với Android framework.
 * - Xử lý bóc tách Jsoup DOM, Schema.org JSON-LD và dữ liệu Next.js React Server Components (RSC).
 * - Hỗ trợ phân giải link video trực tiếp HLS (.m3u8) từ /api/stream.
 */
public class XemplayParser implements MovieParser {

    public static final String PORTAL_URL = "https://xemplay.com";
    public static final String DEFAULT_BASE_URL = "https://xemplay.uk";
    private static final XemplayParser INSTANCE = new XemplayParser();

    public static XemplayParser getInstance() {
        return INSTANCE;
    }

    // =========================================================================
    // 0. CHECK & TRÍCH XUẤT TÊN MIỀN ĐỘNG (DYNAMIC DOMAIN PARSER)
    // =========================================================================

    /**
     * Bóc tách tên miền mới nhất từ HTML của trang portal (xemplay.com).
     * Tương tự cơ chế của VieflixParser, giúp ứng dụng tự động thích ứng
     * khi website đổi domain mà không cần cập nhật code plugin.
     *
     * @param html Nội dung HTML của trang portal
     * @return Tên miền chính xác đang hoạt động (ví dụ: https://xemplay.uk)
     */
    public String parseDomain(String html) {
        if (html == null || html.trim().isEmpty()) {
            return DEFAULT_BASE_URL;
        }

        Document doc = Jsoup.parse(html);

        // 1. Parse từ thẻ link rel="alternate"
        Element altLink = doc.selectFirst("link[rel='alternate'][href]");
        if (altLink != null) {
            String href = altLink.attr("href").trim();
            if (isValidDomain(href) && !isPortalUrl(href)) {
                return cleanDomain(href);
            }
        }

        // 2. Parse từ các nút CTA / Brand (a.top-cta, a.cta, a.brand)
        Elements ctaLinks = doc.select("a.top-cta, a.cta, a.brand, a.cta-ghost");
        for (Element cta : ctaLinks) {
            String href = cta.attr("href").trim();
            if (isValidDomain(href) && !isPortalUrl(href)) {
                return cleanDomain(href);
            }
        }

        // 3. Parse từ JSON-LD schema (SearchAction target hoặc sameAs)
        Elements jsonLds = doc.select("script[type='application/ld+json']");
        for (Element s : jsonLds) {
            String data = s.data();
            Matcher targetMatcher = Pattern.compile("\"target\"\\s*:\\s*\"(https?://[^\"/?#]+)").matcher(data);
            if (targetMatcher.find()) {
                String domain = targetMatcher.group(1);
                if (isValidDomain(domain) && !isPortalUrl(domain)) {
                    return cleanDomain(domain);
                }
            }

            Matcher sameAsMatcher = Pattern.compile("\"sameAs\"\\s*:\\s*\\[?\\s*\"(https?://[^\"/?#]+)").matcher(data);
            if (sameAsMatcher.find()) {
                String domain = sameAsMatcher.group(1);
                if (isValidDomain(domain) && !isPortalUrl(domain)) {
                    return cleanDomain(domain);
                }
            }
        }

        // 4. Fallback tìm URL dạng https://xemplay.* bất kỳ khác xemplay.com
        Matcher regexMatcher = Pattern.compile("(https?://(?:www\\.)?xemplay\\.[a-z]{2,6})", Pattern.CASE_INSENSITIVE).matcher(html);
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
        return clean.equalsIgnoreCase(PORTAL_URL) || clean.equalsIgnoreCase("https://xemplay.com") || clean.equalsIgnoreCase("http://xemplay.com");
    }

    private boolean isValidDomain(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private String cleanDomain(String url) {
        if (url == null) return "";
        String cleaned = url.trim();
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    // =========================================================================
    // 1. DANH MỤC TRANG CHỦ (MAIN PAGE SECTIONS)
    // =========================================================================

    @Override
    public List<MainPageSection> parseMainPage(String content, String baseUrl) {
        List<MainPageSection> sections = new ArrayList<>();
        sections.add(new MainPageSection("🔥 Phim Mới Cập Nhật", "/browse?type=phim-moi-cap-nhat"));
        sections.add(new MainPageSection("📺 Phim Bộ Mới Nhất", "/browse?type=phim-bo"));
        sections.add(new MainPageSection("🎬 Phim Lẻ Mới Nhất", "/browse?type=phim-le"));
        sections.add(new MainPageSection("⛩️ Hoạt Hình / Anime", "/browse?type=hoat-hinh"));
        sections.add(new MainPageSection("🎤 TV Shows Truyền Hình", "/browse?type=tv-shows"));
        sections.add(new MainPageSection("📱 Short Drama Hot", "/short-drama"));
        sections.add(new MainPageSection("💥 Phim Hành Động", "/browse?category=hanh-dong"));
        sections.add(new MainPageSection("💖 Phim Tình Cảm", "/browse?category=tinh-cam"));
        sections.add(new MainPageSection("👻 Phim Kinh Dị", "/browse?category=kinh-di"));
        sections.add(new MainPageSection("🤣 Phim Hài Hước", "/browse?category=hai-huoc"));
        sections.add(new MainPageSection("🎎 Phim Cổ Trang", "/browse?category=co-trang"));
        sections.add(new MainPageSection("🚀 Phim Viễn Tưởng", "/browse?category=vien-tuong"));
        sections.add(new MainPageSection("🌸 Phim Hàn Quốc", "/browse?country=han-quoc"));
        sections.add(new MainPageSection("🐉 Phim Trung Quốc", "/browse?country=trung-quoc"));
        sections.add(new MainPageSection("🗽 Phim Âu Mỹ", "/browse?country=au-my"));
        return sections;
    }

    // =========================================================================
    // 2. DANH SÁCH PHIM (MOVIE LIST PARSER)
    // =========================================================================

    @Override
    public List<MovieItem> parseMovieList(String html, String baseUrl) {
        List<MovieItem> list = new ArrayList<>();
        if (html == null || html.trim().isEmpty()) return list;

        String base = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : DEFAULT_BASE_URL;
        Document doc = Jsoup.parse(html, base);
        Set<String> seenUrls = new HashSet<>();

        // Tìm tất cả các liên kết dẫn đến phim (/phim/{slug})
        Elements links = doc.select("a[href*='/phim/']");

        for (Element link : links) {
            String rawHref = link.attr("href");
            if (rawHref == null || rawHref.trim().isEmpty()) continue;

            // Bỏ qua các liên kết không phải thẻ phim (như diễn viên, menu, v.v.)
            if (!rawHref.contains("/phim/")) continue;

            // Chuẩn hóa link về dạng gốc: /phim/{slug} (loại bỏ /full, /tap-xx, query string)
            String normalizedPath = rawHref.replaceAll("(/tap-[^/?#]+|/full)(\\?.*)?$", "");
            normalizedPath = normalizedPath.replaceAll("\\?.*$", "");
            if (!normalizedPath.matches(".*/phim/[a-zA-Z0-9_-]+.*")) continue;

            String absUrl = HtmlHelper.getAbsoluteUrl(base, link, "href");
            absUrl = absUrl.replaceAll("(/tap-[^/?#]+|/full)(\\?.*)?$", "");
            absUrl = absUrl.replaceAll("\\?.*$", "");

            if (seenUrls.contains(absUrl)) continue;

            // Kiểm tra thẻ chứa hình ảnh (ảnh poster)
            Element img = link.selectFirst("img");
            if (img == null) {
                // Kiểm tra xem có nằm trong cấu trúc item danh sách mobile hay không
                Element parentItem = link.closest("li");
                if (parentItem != null) {
                    img = parentItem.selectFirst("img");
                }
            }

            // Nếu hoàn toàn không có ảnh poster, nhiều khả năng là text link trong breadcrumb hoặc footer
            if (img == null) continue;

            // Trích xuất poster URL
            String posterUrl = extractPosterUrl(img, base);
            if (posterUrl == null || posterUrl.trim().isEmpty()) continue;

            // Trích xuất tiêu đề phim
            String title = extractTitle(link, img);
            if (title.isEmpty()) continue;

            // Trích xuất các badges / nhãn (HD, FHD, CAM, Vietsub, Thuyết minh, v.v.)
            List<String> tags = extractBadges(link);

            seenUrls.add(absUrl);
            list.add(new MovieItem(title, absUrl, posterUrl, tags));
        }

        return list;
    }

    // =========================================================================
    // 3. CHI TIẾT PHIM & TẬP PHIM (MOVIE DETAIL PARSER)
    // =========================================================================

    @Override
    public MovieDetail parseMovieDetail(String html, String baseUrl) {
        if (html == null || html.trim().isEmpty()) {
            return new MovieDetail("", "", "", null, null, Collections.emptyList(), Collections.emptyList());
        }

        String base = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : DEFAULT_BASE_URL;
        Document doc = Jsoup.parse(html, base);

        String title = "";
        String posterUrl = "";
        String plot = "";
        Integer year = null;
        Integer duration = null;
        List<String> tags = new ArrayList<>();
        List<EpisodeItem> episodes = new ArrayList<>();
        String potentialWatchTarget = null;

        // ---------------------------------------------------------------------
        // A. Ưu tiên trích xuất từ Schema.org JSON-LD (<script type="application/ld+json">)
        // ---------------------------------------------------------------------
        Elements jsonLdScripts = doc.select("script[type='application/ld+json']");
        for (Element script : jsonLdScripts) {
            String jsonStr = script.data().trim();
            if (jsonStr.isEmpty()) continue;

            try {
                if (jsonStr.startsWith("{")) {
                    JSONObject json = new JSONObject(jsonStr);
                    String type = json.optString("@type");
                    if ("Movie".equalsIgnoreCase(type) || "TVSeries".equalsIgnoreCase(type)) {
                        title = json.optString("name", title);
                        posterUrl = json.optString("image", posterUrl);
                        plot = json.optString("description", plot);

                        String datePub = json.optString("datePublished");
                        if (!datePub.isEmpty()) {
                            year = RegexHelper.parseInt(datePub, "(\\d{4})");
                        }

                        JSONArray genreArr = json.optJSONArray("genre");
                        if (genreArr != null) {
                            for (int i = 0; i < genreArr.length(); i++) {
                                String g = genreArr.getString(i).trim();
                                if (!g.isEmpty() && !tags.contains(g)) {
                                    tags.add(g);
                                }
                            }
                        }

                        // Lưu target phát phim làm fallback
                        JSONObject action = json.optJSONObject("potentialAction");
                        if (action != null) {
                            String target = action.optString("target");
                            if (!target.isEmpty()) {
                                potentialWatchTarget = HtmlHelper.getAbsoluteUrl(base, doc.createElement("a").attr("href", target), "href");
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // ---------------------------------------------------------------------
        // B. Bổ sung từ HTML DOM nếu còn thiếu
        // ---------------------------------------------------------------------
        if (title.isEmpty()) {
            title = HtmlHelper.selectFirstText(doc, "h1");
        }
        // Xóa các tiền tố như "[vietsub]" nếu có trong thẻ title
        title = title.replaceAll("^\\[.*?\\]\\s*", "").trim();

        if (posterUrl.isEmpty()) {
            Element posterImg = doc.selectFirst("img[src*='phimimg.com'], img[src*='poster'], img[alt*='" + title + "']");
            if (posterImg != null) {
                posterUrl = extractPosterUrl(posterImg, base);
            }
        }

        if (plot.isEmpty()) {
            plot = HtmlHelper.selectFirstText(doc, "div[data-movie-detail] p.line-clamp-5, p.line-clamp-4, p.line-clamp-3, .description");
        }

        if (year == null) {
            year = RegexHelper.parseInt(html, "\\b(19\\d{2}|20\\d{2})\\b");
        }

        if (duration == null) {
            duration = RegexHelper.parseInt(html, "(\\d+)\\s*(?:phút|mins|min)");
        }

        // ---------------------------------------------------------------------
        // C. Trích xuất danh sách tập phim (Episodes)
        // ---------------------------------------------------------------------
        Map<Integer, EpisodeItem> epMap = new TreeMap<>();

        // C1. Bóc tách các thẻ tập từ HTML DOM (a[href*='/tap-'])
        Elements epLinks = doc.select("a[href*='/tap-']");
        for (Element epLink : epLinks) {
            String href = epLink.attr("href");
            if (href.isEmpty() || !href.contains("/tap-")) continue;

            // Bóc tách số tập từ link /tap-(\d+)
            Matcher numMatcher = Pattern.compile("/tap-(\\d+)").matcher(href);
            if (numMatcher.find()) {
                int epNum = Integer.parseInt(numMatcher.group(1));
                if (!epMap.containsKey(epNum)) {
                    String absHref = HtmlHelper.getAbsoluteUrl(base, epLink, "href");
                    String epName = "Tập " + epNum;

                    // Nếu thẻ có text con chứa tên tập cụ thể
                    Element nameSpan = epLink.selectFirst("span.font-semibold, span.line-clamp-1");
                    if (nameSpan != null && !nameSpan.text().trim().isEmpty() && nameSpan.text().trim().length() < 30) {
                        epName = nameSpan.text().trim();
                    }

                    epMap.put(epNum, new EpisodeItem(absHref, epName, epNum));
                }
            }
        }

        // C2. Bóc tách bổ sung từ RSC payload nếu DOM không có
        if (epMap.isEmpty()) {
            Pattern epPattern = Pattern.compile("(/phim/[^\"'/]+/tap-(\\d+)[^\"'\\\\]*)");
            Matcher epMatcher = epPattern.matcher(html);
            while (epMatcher.find()) {
                String rawPath = epMatcher.group(1).replace("\\u0026", "&");
                int epNum = Integer.parseInt(epMatcher.group(2));
                if (!epMap.containsKey(epNum)) {
                    String fullEpHref = HtmlHelper.getAbsoluteUrl(base, doc.createElement("a").attr("href", rawPath), "href");
                    epMap.put(epNum, new EpisodeItem(fullEpHref, "Tập " + epNum, epNum));
                }
            }
        }

        // Chuyển epMap sang danh sách episodes
        if (!epMap.isEmpty()) {
            episodes.addAll(epMap.values());
        }

        // C3. Fallback cho phim lẻ (Single Movie) nếu không có tập nào
        if (episodes.isEmpty()) {
            String singleWatchUrl = potentialWatchTarget;
            if (singleWatchUrl == null || singleWatchUrl.isEmpty()) {
                Element fullLink = doc.selectFirst("a[href*='/full']");
                if (fullLink != null) {
                    singleWatchUrl = HtmlHelper.getAbsoluteUrl(base, fullLink, "href");
                }
            }
            if (singleWatchUrl == null || singleWatchUrl.isEmpty()) {
                String currentUrl = doc.location();
                if (currentUrl == null || currentUrl.isEmpty()) {
                    currentUrl = base;
                }
                singleWatchUrl = currentUrl.endsWith("/") ? currentUrl + "full" : currentUrl + "/full";
            }
            episodes.add(new EpisodeItem(singleWatchUrl, "Full", 1));
        }

        return new MovieDetail(title, posterUrl, plot, year, duration, tags, episodes);
    }

    // =========================================================================
    // 4. TRÍCH XUẤT LINK PHÁT STREAMING (EXTRACT VIDEO LINKS)
    // =========================================================================

    @Override
    public List<VideoLink> extractVideoLinks(String html, String slugOrData) {
        List<VideoLink> links = new ArrayList<>();
        if (html == null || html.trim().isEmpty()) return links;

        String base = DEFAULT_BASE_URL;

        // 1. Trích xuất direct stream URL từ endpoint /api/stream?t=...
        Matcher streamMatcher = Pattern.compile("(/api/stream\\?t=[^\"'\\s&\\\\]+)").matcher(html);
        if (streamMatcher.find()) {
            String streamPath = streamMatcher.group(1);
            String fullStreamUrl = streamPath.startsWith("http")
                    ? streamPath
                    : (base + (streamPath.startsWith("/") ? streamPath : "/" + streamPath));

            links.add(new VideoLink(
                    VideoLink.TYPE_M3U8,
                    fullStreamUrl,
                    "XemPlay HLS (VIP)",
                    "Máy chủ VIP 1",
                    "Vietsub"
            ));
        }

        // 2. Trích xuất fallback embed link: "fallbackEmbedHref":"/phim/.../full?sv=vip1&lang=vietsub&play=embed&iframe=1"
        Matcher embedMatcher = Pattern.compile("fallbackEmbedHref[\"']?\\s*:\\s*[\"']([^\"'\\s]+)[\"']").matcher(html);
        if (embedMatcher.find()) {
            String embedPath = embedMatcher.group(1).replace("\\u0026", "&");
            String fullEmbedUrl = embedPath.startsWith("http")
                    ? embedPath
                    : (base + (embedPath.startsWith("/") ? embedPath : "/" + embedPath));

            links.add(new VideoLink(
                    VideoLink.TYPE_EMBED,
                    fullEmbedUrl,
                    "XemPlay Embed Dự Phòng",
                    "Máy chủ Embed",
                    "Vietsub"
            ));
        }

        // 3. Quét link M3U8 độc lập trong HTML nếu các cách trên chưa có
        if (links.isEmpty()) {
            String m3u8Url = RegexHelper.extractGroup(html, "(https?://[^\"'\\s]+\\.m3u8[^\"'\\s]*)", 1);
            if (m3u8Url != null) {
                links.add(new VideoLink(
                        VideoLink.TYPE_M3U8,
                        m3u8Url,
                        "HLS Stream Direct",
                        "Máy chủ phụ",
                        "Vietsub"
                ));
            }
        }

        // 4. Quét thẻ <iframe> trong DOM nếu có
        Document doc = Jsoup.parse(html, base);
        Elements iframes = doc.select("iframe[src]");
        for (Element iframe : iframes) {
            String src = iframe.attr("abs:src");
            if (src.startsWith("http")) {
                links.add(new VideoLink(
                        VideoLink.TYPE_EMBED,
                        src,
                        "Embed Player",
                        "Máy chủ phụ",
                        "Vietsub"
                ));
            }
        }

        return links;
    }

    // =========================================================================
    // 5. TÌM KIẾM THÔNG MINH (SMART SEARCH URL BUILDER)
    // =========================================================================

    /**
     * Xây dựng URL tìm kiếm với hỗ trợ hashtag thông minh:
     * - #phimbo, #phimle, #hoathinh, #anime, #tvshows
     * - #hanquoc, #trungquoc, #aumy, #nhatban, #thailan, #vietnam
     * - #hanhdong, #tinhcam, #kinhdi, #haihuoc, #cotrang, #vientuong
     * - nam:YYYY
     */
    public String buildSearchUrl(String baseUrl, String query, int page) {
        String base = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : DEFAULT_BASE_URL;
        if (query == null) query = "";

        String q = query.trim();
        Map<String, String> params = new LinkedHashMap<>();

        // 1. Phân tích loại phim (Type)
        if (q.contains("#phimbo")) {
            params.put("type", "phim-bo");
            q = q.replace("#phimbo", "");
        } else if (q.contains("#phimle")) {
            params.put("type", "phim-le");
            q = q.replace("#phimle", "");
        } else if (q.contains("#hoathinh") || q.contains("#anime")) {
            params.put("type", "hoat-hinh");
            q = q.replace("#hoathinh", "").replace("#anime", "");
        } else if (q.contains("#tvshows")) {
            params.put("type", "tv-shows");
            q = q.replace("#tvshows", "");
        }

        // 2. Phân tích quốc gia (Country)
        if (q.contains("#hanquoc")) {
            params.put("country", "han-quoc");
            q = q.replace("#hanquoc", "");
        } else if (q.contains("#trungquoc")) {
            params.put("country", "trung-quoc");
            q = q.replace("#trungquoc", "");
        } else if (q.contains("#aumy")) {
            params.put("country", "au-my");
            q = q.replace("#aumy", "");
        } else if (q.contains("#nhatban")) {
            params.put("country", "nhat-ban");
            q = q.replace("#nhatban", "");
        } else if (q.contains("#thailan")) {
            params.put("country", "thai-lan");
            q = q.replace("#thailan", "");
        } else if (q.contains("#vietnam")) {
            params.put("country", "viet-nam");
            q = q.replace("#vietnam", "");
        }

        // 3. Phân tích thể loại (Category)
        if (q.contains("#hanhdong")) {
            params.put("category", "hanh-dong");
            q = q.replace("#hanhdong", "");
        } else if (q.contains("#tinhcam")) {
            params.put("category", "tinh-cam");
            q = q.replace("#tinhcam", "");
        } else if (q.contains("#kinhdi")) {
            params.put("category", "kinh-di");
            q = q.replace("#kinhdi", "");
        } else if (q.contains("#haihuoc")) {
            params.put("category", "hai-huoc");
            q = q.replace("#haihuoc", "");
        } else if (q.contains("#cotrang")) {
            params.put("category", "co-trang");
            q = q.replace("#cotrang", "");
        } else if (q.contains("#vientuong")) {
            params.put("category", "vien-tuong");
            q = q.replace("#vientuong", "");
        }

        // 4. Phân tích năm (Year)
        Matcher yearMatcher = Pattern.compile("nam:(\\d{4})").matcher(q);
        if (yearMatcher.find()) {
            params.put("year", yearMatcher.group(1));
            q = yearMatcher.replaceAll("");
        }

        q = q.trim();

        // Ghép URL
        StringBuilder url = new StringBuilder(base).append("/browse?");
        if (!q.isEmpty()) {
            url.append("q=").append(urlEncode(q)).append("&");
        }
        for (Map.Entry<String, String> entry : params.entrySet()) {
            url.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }
        url.append("page=").append(page);

        return url.toString();
    }

    // =========================================================================
    // HÀM TIỆN ÍCH RIÊNG (PRIVATE HELPERS)
    // =========================================================================

    private String extractTitle(Element card, Element img) {
        String title = HtmlHelper.selectFirstText(card, "h3");
        if (title.isEmpty()) {
            title = HtmlHelper.selectFirstText(card, "h2");
        }
        if (title.isEmpty() && img != null) {
            title = img.attr("alt").trim();
        }
        if (title.isEmpty()) {
            title = card.attr("title").trim();
        }
        if (title.isEmpty()) {
            title = HtmlHelper.selectFirstText(card, "span.line-clamp-1, span.text-white");
        }
        return title;
    }

    private String extractPosterUrl(Element img, String base) {
        if (img == null) return "";
        String src = "";
        if (img.hasAttr("data-src") && !img.attr("data-src").trim().isEmpty()) {
            src = img.attr("data-src").trim();
        } else if (img.hasAttr("data-original") && !img.attr("data-original").trim().isEmpty()) {
            src = img.attr("data-original").trim();
        } else if (img.hasAttr("src") && !img.attr("src").trim().isEmpty()) {
            src = img.attr("src").trim();
        }

        // Bỏ qua nếu là placeholder data URI
        if (src.startsWith("data:image")) {
            if (img.hasAttr("data-src") && !img.attr("data-src").trim().isEmpty()) {
                src = img.attr("data-src").trim();
            } else if (img.hasAttr("data-original") && !img.attr("data-original").trim().isEmpty()) {
                src = img.attr("data-original").trim();
            } else {
                return "";
            }
        }

        // Xử lý link ảnh Next.js tối ưu /_next/image?url=...
        if (src.contains("/_next/image?url=")) {
            String extracted = RegexHelper.extractGroup(src, "url=([^&]+)", 1);
            if (extracted != null) {
                try {
                    src = URLDecoder.decode(extracted, "UTF-8");
                } catch (Exception ignored) {
                }
            }
        }

        if (src.startsWith("//")) {
            return "https:" + src;
        }

        if (src.startsWith("http://") || src.startsWith("https://")) {
            return src;
        }

        // Các ảnh vod upload/vod/... hoặc uploads/movies/... được lưu trữ thực tế trên CDN phimimg.com
        if (src.startsWith("upload/") || src.startsWith("/upload/")
                || src.startsWith("uploads/") || src.startsWith("/uploads/")) {
            String path = src.startsWith("/") ? src.substring(1) : src;
            return "https://phimimg.com/" + path;
        }

        if (src.startsWith("/")) {
            return base + src;
        }
        return base + "/" + src;
    }

    private List<String> extractBadges(Element card) {
        List<String> tags = new ArrayList<>();
        Elements badgeElements = card.select("span");
        for (Element span : badgeElements) {
            String text = span.text().trim();
            if (text.isEmpty()) continue;
            if (text.matches("^(HD|FHD|CAM|4K|Vietsub|Thuyết minh|Lồng tiếng|Song ngữ|Full|Hoàn Tất.*|Tập.*)$")) {
                if (!tags.contains(text)) {
                    tags.add(text);
                }
            }
        }
        return tags;
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value;
        }
    }
}
