package com.vieflix;

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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * VieflixParser - Triển khai MovieParser bóc tách HTML chuyên biệt cho nguồn Vieflix.
 */
public class VieflixParser implements MovieParser {

    public static final String PORTAL_URL = "https://vieflix.com";
    public static final String DEFAULT_BASE_URL = "https://vieflix.top";

    private static final VieflixParser INSTANCE = new VieflixParser();

    public static VieflixParser getInstance() {
        return INSTANCE;
    }

    // ==========================================
    // 0. CHECK & TRÍCH XUẤT TÊN MIỀN (DOMAIN)
    // ==========================================

    /**
     * Bóc tách tên miền mới nhất từ HTML của trang portal (vieflix.com)
     * hoặc từ script cấu hình (constan.js).
     *
     * @param html Nội dung HTML hoặc JS của trang portal
     * @return Tên miền chính xác (ví dụ: https://vieflix.top)
     */
    public String parseDomain(String html) {
        if (html == null || html.trim().isEmpty()) {
            return DEFAULT_BASE_URL;
        }

        // 1. Trích xuất TARGET_DOMAIN từ script: TARGET_DOMAIN: "https://..."
        String targetDomain = RegexHelper.extractGroup(html, "TARGET_DOMAIN\\s*:\\s*[\"']([^\"']+)[\"']", 1);
        if (targetDomain != null && isValidDomain(targetDomain)) {
            return cleanDomain(targetDomain);
        }

        // 2. Parse thẻ a#accessBtn (button "Truy cập →")
        Document document = Jsoup.parse(html);
        Element accessBtn = document.selectFirst("#accessBtn");
        if (accessBtn != null) {
            String href = accessBtn.attr("href").trim();
            if (isValidDomain(href) && !href.equalsIgnoreCase(PORTAL_URL) && !href.equalsIgnoreCase(PORTAL_URL + "/")) {
                return cleanDomain(href);
            }
        }

        // 3. Parse input#domainInput
        Element domainInput = document.selectFirst("#domainInput");
        if (domainInput != null) {
            String val = domainInput.attr("value").trim();
            if (isValidDomain(val) && !val.equalsIgnoreCase(PORTAL_URL) && !val.equalsIgnoreCase(PORTAL_URL + "/")) {
                return cleanDomain(val);
            }
        }

        return DEFAULT_BASE_URL;
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

    // ==========================================
    // 0.1. PARSE DANH MỤC TRANG CHỦ (MAIN PAGE SECTIONS)
    // ==========================================

    /**
     * Bóc tách các mục (sections/categories) động từ HTML trang chủ.
     * Tìm tất cả các tiêu đề thẻ <h2> và thẻ <a> chuyển hướng tương ứng (ví dụ: "Xem toàn bộ").
     *
     * @param html    Nội dung HTML của trang chủ
     * @param baseUrl URL gốc
     * @return Danh sách MainPageSection (tên mục và đường dẫn path)
     */
    @Override
    public List<MainPageSection> parseMainPage(String html, String baseUrl) {
        List<MainPageSection> result = new ArrayList<>();
        if (html == null || html.trim().isEmpty()) {
            return result;
        }

        Document document = (baseUrl != null && !baseUrl.isEmpty())
                ? Jsoup.parse(html, baseUrl)
                : Jsoup.parse(html);
        Elements h2Elements = document.select("h2");
        List<String> seenPaths = new ArrayList<>();

        for (Element h2 : h2Elements) {
            String title = h2.text().trim();
            if (title.isEmpty()) continue;

            Element parent = h2.parent();
            if (parent == null) continue;

            // Tìm liên kết "Xem toàn bộ" hoặc liên kết chuyển hướng của mục trong div header
            Element linkEl = parent.selectFirst("a[href]");
            if (linkEl == null && parent.parent() != null) {
                linkEl = parent.parent().selectFirst("a[href]");
            }

            if (linkEl == null) continue;

            String href = linkEl.attr("href").trim();
            if (href.isEmpty() || href.startsWith("/phim/") || href.startsWith("phim/")
                    || href.startsWith("/chu-de/") || href.startsWith("chu-de/")
                    || href.contains("javascript:")) continue;

            // Xử lý nếu href là URL tuyệt đối
            if (href.startsWith("http://") || href.startsWith("https://")) {
                try {
                    java.net.URI uri = new java.net.URI(href);
                    String path = uri.getRawPath();
                    String query = uri.getRawQuery();
                    href = (path != null ? path : "") + (query != null ? "?" + query : "");
                } catch (Exception ignored) {
                }
            }

            // Chuẩn hóa đường dẫn tương đối (đảm bảo bắt đầu bằng '/')
            if (!href.startsWith("/")) {
                href = "/" + href;
            }

            if (!seenPaths.contains(href)) {
                seenPaths.add(href);
                result.add(new MainPageSection(title, href));
            }
        }

        return result;
    }

    public List<MainPageSection> parseMainPage(String html) {
        return parseMainPage(html, DEFAULT_BASE_URL);
    }

    // ==========================================
    // 1. PARSE DANH SÁCH PHIM
    // ==========================================

    @Override
    public List<MovieItem> parseMovieList(String html, String baseUrl) {
        Document document = Jsoup.parse(html, baseUrl);
        Elements elements = document.select("a[href^=/phim/], a[href^=phim/]");

        List<MovieItem> result = new ArrayList<>();
        List<String> seenHrefs = new ArrayList<>();

        for (Element element : elements) {
            Element img = element.selectFirst("img");

            String title = (img != null && !img.attr("alt").trim().isEmpty()) ? img.attr("alt").trim() : "";
            if (title.isEmpty()) {
                Element heading = element.selectFirst("h2, h3, h4, h5");
                if (heading != null && !heading.text().trim().isEmpty()) {
                    title = heading.text().trim();
                } else {
                    Element clone = element.clone();
                    clone.select("span.uppercase, div:has(> svg) span, span[class*=uppercase]").remove();
                    title = clone.text().trim();
                }
            }
            if (title.isEmpty()) continue;

            String href = HtmlHelper.getAbsoluteUrl(baseUrl, element, "href");
            if (seenHrefs.contains(href)) continue;
            seenHrefs.add(href);

            String poster = (img != null) ? img.attr("src") : "";

            // Trích xuất các badge nhãn (LT, VS, SN, TM, HD...) từ thẻ card
            List<String> badges = new ArrayList<>();
            Elements badgeElements = element.select("span.uppercase, div:has(> svg) span, span[class*=uppercase]");
            for (Element badgeEl : badgeElements) {
                String badgeText = badgeEl.text().trim().toUpperCase();
                if (!badgeText.isEmpty() && !badges.contains(badgeText)) {
                    badges.add(badgeText);
                }
            }

            result.add(new MovieItem(title, href, poster.isEmpty() ? null : poster, badges));
        }
        return result;
    }

    // ==========================================
    // 2. PARSE CHI TIẾT BỘ PHIM
    // ==========================================

    @Override
    public MovieDetail parseMovieDetail(String html, String baseUrl) {
        Document document = Jsoup.parse(html, baseUrl);

        // 1. Tiêu đề
        String title = HtmlHelper.selectFirstText(document, "h1");
        if (title.isEmpty()) title = "Không có tên";

        // 2. Poster
        Element posterEl = document.selectFirst("img[src*=/movies/]");
        if (posterEl == null) posterEl = document.selectFirst("img");
        String posterUrl = (posterEl != null) ? posterEl.attr("src") : null;

        // 3. Mô tả (Plot)
        String plot = parsePlot(html, document);

        // 4. Thời lượng & Năm phát hành
        Integer duration = RegexHelper.parseInt(html, "Th\u1eddi l\u01b0\u1ee3ng:[^0-9]*([0-9]+)");
        Integer year = RegexHelper.parseInt(html, "\\b(19\\d{2}|20\\d{2})\\b");

        // 5. Thể loại (Tags)
        Elements tagElements = document.select("a[href*=/the-loai/], a[href*=/quoc-gia/]");
        List<String> tags = new ArrayList<>();
        for (Element tag : tagElements) {
            String text = tag.text().trim();
            if (!text.isEmpty()) tags.add(text);
        }

        // 6. Danh sách tập phim
        List<EpisodeItem> episodes = parseEpisodes(document, baseUrl);

        return new MovieDetail(title, posterUrl, plot, year, duration, tags, episodes);
    }

    private String parsePlot(String html, Document document) {
        String plot = RegexHelper.extractGroup(html, "Gi\u1edbi thi\u1ec7u:.*?<p[^>]*>(.*?)</p>", 1);
        if (plot != null) {
            return plot.replaceAll("<[^>]*>", "").trim();
        }

        for (Element p : document.select("p")) {
            String text = p.text();
            if (text.length() > 50) {
                String marker = "Gi\u1edbi thi\u1ec7u:";
                int idx = text.indexOf(marker);
                return (idx >= 0) ? text.substring(idx + marker.length()).trim() : text.trim();
            }
        }
        return null;
    }

    private List<EpisodeItem> parseEpisodes(Document document, String baseUrl) {
        Elements epElements = document.select("a[href*=/tap-]");
        List<EpisodeItem> result = new ArrayList<>();
        List<String> seenHrefs = new ArrayList<>();

        int index = 0;
        for (Element ep : epElements) {
            String href = HtmlHelper.getAbsoluteUrl(baseUrl, ep, "href");
            if (seenHrefs.contains(href)) continue;
            seenHrefs.add(href);

            Integer epNum = RegexHelper.parseInt(href, "/tap-([0-9]+)");
            int finalEpNum = (epNum != null) ? epNum : (index + 1);

            String name = ep.text().trim();
            if (name.isEmpty()) name = "Tập " + finalEpNum;

            result.add(new EpisodeItem(href, name, finalEpNum));
            index++;
        }
        return result;
    }

    // ==========================================
    // 3. TRÍCH XUẤT LINK VIDEO PHÁT (M3U8 / Embed)
    // ==========================================

    @Override
    public List<VideoLink> extractVideoLinks(String html, String slug) {
        List<VideoLink> result = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        // 1. Chuẩn hóa chuỗi JSON (xử lý escape quotes)
        String clean = html.replace("\\\"", "\"").replace("\\\\", "\\");

        String sourcesMarker = "\"sources\":[";
        int sIdx = clean.indexOf(sourcesMarker);
        if (sIdx != -1) {
            int start = sIdx + sourcesMarker.length() - 1;
            int depth = 0;
            int end = -1;
            for (int i = start; i < clean.length(); i++) {
                char c = clean.charAt(i);
                if (c == '[') depth++;
                else if (c == ']') {
                    depth--;
                    if (depth == 0) {
                        end = i;
                        break;
                    }
                }
            }

            if (end != -1) {
                String sourcesJson = clean.substring(start, end + 1);
                try {
                    JSONArray sources = new JSONArray(sourcesJson);
                    for (int i = 0; i < sources.length(); i++) {
                        JSONObject server = sources.optJSONObject(i);
                        if (server == null) continue;

                        String rawServerName = server.optString("serverName", "");
                        String serverDisplayName;
                        if (rawServerName.trim().isEmpty()) {
                            serverDisplayName = "Máy chủ " + (i + 1);
                        } else {
                            serverDisplayName = "Máy chủ " + (i + 1) + " (" + rawServerName.trim() + ")";
                        }

                        JSONArray languages = server.optJSONArray("languages");
                        if (languages != null) {
                            for (int j = 0; j < languages.length(); j++) {
                                JSONObject lang = languages.optJSONObject(j);
                                if (lang == null) continue;

                                String langName = lang.optString("name", "Vietsub");
                                if (langName.trim().isEmpty()) {
                                    langName = lang.optString("slug", "Vietsub");
                                }
                                langName = langName.replaceAll("\\s*#\\d+", "").trim();
                                if (langName.equalsIgnoreCase("vietsub")) langName = "Vietsub";
                                else if (langName.equalsIgnoreCase("thuyet-minh") || langName.equalsIgnoreCase("thuyết minh")) langName = "Thuyết Minh";
                                else if (langName.equalsIgnoreCase("long-tieng") || langName.equalsIgnoreCase("lồng tiếng")) langName = "Lồng Tiếng";
                                else if (langName.equalsIgnoreCase("song-ngu") || langName.equalsIgnoreCase("song ngữ")) langName = "Song Ngữ";

                                JSONArray episodes = lang.optJSONArray("episodes");
                                if (episodes != null) {
                                    for (int k = 0; k < episodes.length(); k++) {
                                        JSONObject ep = episodes.optJSONObject(k);
                                        if (ep == null) continue;

                                        String epSlug = ep.optString("slug", "");
                                        if (slug.equals(epSlug)) {
                                            String m3u8 = ep.optString("linkM3u8", "");
                                            String embed = ep.optString("linkEmbed", "");
                                            String direct = ep.optString("linkDirect", "");

                                            String label = serverDisplayName + " - " + langName;

                                            if (!m3u8.isEmpty() && m3u8.contains(".m3u8") && !seenUrls.contains(m3u8)) {
                                                seenUrls.add(m3u8);
                                                result.add(new VideoLink(VideoLink.TYPE_M3U8, m3u8, label, serverDisplayName, langName));
                                            }

                                            if (!embed.isEmpty()) {
                                                if (embed.contains("url=") && embed.contains(".m3u8")) {
                                                    String cleanM3u8 = embed.substring(embed.indexOf("url=") + 4);
                                                    int amp = cleanM3u8.indexOf('&');
                                                    if (amp != -1) cleanM3u8 = cleanM3u8.substring(0, amp);
                                                    if (!seenUrls.contains(cleanM3u8)) {
                                                        seenUrls.add(cleanM3u8);
                                                        result.add(new VideoLink(VideoLink.TYPE_M3U8, cleanM3u8, label, serverDisplayName, langName));
                                                    }
                                                } else if (!seenUrls.contains(embed)) {
                                                    seenUrls.add(embed);
                                                    result.add(new VideoLink(VideoLink.TYPE_EMBED, embed, label, serverDisplayName, langName));
                                                }
                                            }

                                            if (!direct.isEmpty() && !seenUrls.contains(direct)) {
                                                seenUrls.add(direct);
                                                result.add(new VideoLink(VideoLink.TYPE_M3U8, direct, label, serverDisplayName, langName));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("⚠️ Lỗi parse sources JSON: " + e.getMessage());
                }
            }
        }

        // Fallback: Tìm theo searchSlug dạng khối nếu không trích xuất được từ sources
        if (result.isEmpty()) {
            String searchSlug = "\"slug\":\"" + slug + "\"";
            String slugKey = "\"slug\":\"";
            String m3u8Key = "\"linkM3u8\":\"";
            String embedKey = "\"linkEmbed\":\"";
            String closingQuote = "\"";

            int startIndex = clean.indexOf(searchSlug);
            while (startIndex != -1) {
                int endOfBlock = clean.indexOf(slugKey, startIndex + searchSlug.length());
                String block = (endOfBlock != -1)
                        ? clean.substring(startIndex, endOfBlock)
                        : clean.substring(startIndex);

                String m3u8Url = RegexHelper.extractBetween(block, m3u8Key, closingQuote);
                if (m3u8Url != null && !m3u8Url.trim().isEmpty() && m3u8Url.contains(".m3u8")) {
                    if (!seenUrls.contains(m3u8Url)) {
                        seenUrls.add(m3u8Url);
                        result.add(new VideoLink(VideoLink.TYPE_M3U8, m3u8Url, "Vieflix M3U8"));
                    }
                }

                String embedUrl = RegexHelper.extractBetween(block, embedKey, closingQuote);
                if (embedUrl != null && !embedUrl.trim().isEmpty()) {
                    if (embedUrl.contains("url=") && embedUrl.contains(".m3u8")) {
                        String m3u8 = embedUrl.substring(embedUrl.indexOf("url=") + 4);
                        int ampIdx = m3u8.indexOf('&');
                        if (ampIdx != -1) m3u8 = m3u8.substring(0, ampIdx);
                        if (!seenUrls.contains(m3u8)) {
                            seenUrls.add(m3u8);
                            result.add(new VideoLink(VideoLink.TYPE_M3U8, m3u8, "Vieflix Embed"));
                        }
                    } else if (!seenUrls.contains(embedUrl)) {
                        seenUrls.add(embedUrl);
                        result.add(new VideoLink(VideoLink.TYPE_EMBED, embedUrl, "Vieflix Embed"));
                    }
                }

                startIndex = clean.indexOf(searchSlug, startIndex + searchSlug.length());
            }
        }

        // Fallback cuối: Tìm linkM3u8 đầu tiên trong HTML
        if (result.isEmpty()) {
            String m3u8Key = "\"linkM3u8\":\"";
            String closingQuote = "\"";
            String fbM3u8 = RegexHelper.extractBetween(clean, m3u8Key, closingQuote);
            if (fbM3u8 != null && !fbM3u8.trim().isEmpty() && fbM3u8.contains(".m3u8")) {
                result.add(new VideoLink(VideoLink.TYPE_M3U8, fbM3u8, "Vieflix Auto"));
            }
        }

        return result;
    }

    // ==========================================
    // 5. SMART FILTER SEARCH BUILDER
    // ==========================================

    /**
     * Xây dựng URL tìm kiếm thông minh từ từ khóa hoặc các tag/bộ lọc.
     * Hỗ trợ tìm kiếm từ khóa kết hợp các bộ lọc:
     * - Phim chiếu rạp: #chieurap, #chieu-rap, #rap
     * - Ngôn ngữ: #thuyetminh, #longtieng, #songngu, #vietsub
     * - Loại phim: #phimle, #phimbo, #hoathinh, #anime, #tvshows, #phimngan
     * - Quốc gia: #hanquoc, #trungquoc, #aumy, #nhatban, #thailan, #dailoan, #hongkong, #ando, #vietnam
     * - Thể loại: #cotrang, #hanhdong, #kinhdi, #tinhcam, #haihuoc, #tamly, #hinhsu, #vientuong, #vothuat, #thanthoai, #hocduong, #chientranh, #bian, #phieuluu, #tailieu, #giadinh
     * - Năm: nam:2024, year:2024, #2024
     * - Sắp xếp: #hot, #xemnhieu, #danhgia, #moinhat
     * - Bảng chữ cái: #A, alphabet:A, chu:A
     */
    public String buildSearchUrl(String baseUrl, String query, int page) {
        if (query == null) query = "";
        String cleanQuery = query.trim();

        // 0. Nếu người dùng dán trực tiếp đường dẫn hoặc path (/duyet-tim?...)
        if (cleanQuery.startsWith("http://") || cleanQuery.startsWith("https://")
                || cleanQuery.startsWith("/duyet-tim") || cleanQuery.startsWith("duyet-tim")
                || cleanQuery.startsWith("/loai-phim") || cleanQuery.startsWith("loai-phim")
                || cleanQuery.startsWith("/the-loai") || cleanQuery.startsWith("the-loai")
                || cleanQuery.startsWith("/quoc-gia") || cleanQuery.startsWith("quoc-gia")
                || cleanQuery.startsWith("/song-ngu") || cleanQuery.startsWith("song-ngu")
                || cleanQuery.startsWith("/phim-ngan") || cleanQuery.startsWith("phim-ngan")) {
            String fullUrl = cleanQuery;
            if (!fullUrl.startsWith("http")) {
                String p = cleanQuery.startsWith("/") ? cleanQuery : "/" + cleanQuery;
                fullUrl = baseUrl + p;
            }
            if (fullUrl.contains("page=")) {
                return fullUrl.replaceAll("([?&])page=[0-9]*", "$1page=" + page);
            } else {
                String connector = fullUrl.contains("?") ? "&" : "?";
                return fullUrl + connector + "page=" + page;
            }
        }

        List<String> params = new ArrayList<>();

        // 1. Phim chiếu rạp
        if (matchesTag(cleanQuery, "(#?chieu[-_ ]?rap|#rap)")) {
            params.add("isChieuRap=true");
            cleanQuery = removeTag(cleanQuery, "(#?chieu[-_ ]?rap|#rap)");
        }

        // 2. Ngôn ngữ
        if (matchesTag(cleanQuery, "(#?thuyet[-_ ]?minh|#tm)")) {
            params.add("lang=thuyet-minh");
            cleanQuery = removeTag(cleanQuery, "(#?thuyet[-_ ]?minh|#tm)");
        } else if (matchesTag(cleanQuery, "(#?long[-_ ]?tieng|#lt)")) {
            params.add("lang=long-tieng");
            cleanQuery = removeTag(cleanQuery, "(#?long[-_ ]?tieng|#lt)");
        } else if (matchesTag(cleanQuery, "(#?song[-_ ]?ngu|#sn)")) {
            params.add("lang=song-ngu");
            cleanQuery = removeTag(cleanQuery, "(#?song[-_ ]?ngu|#sn)");
        } else if (matchesTag(cleanQuery, "(#?vietsub|#sub)")) {
            params.add("lang=vietsub");
            cleanQuery = removeTag(cleanQuery, "(#?vietsub|#sub)");
        }

        // 3. Loại phim
        if (matchesTag(cleanQuery, "(#?phim[-_ ]?le|#le)")) {
            params.add("typeList=phim-le");
            cleanQuery = removeTag(cleanQuery, "(#?phim[-_ ]?le|#le)");
        } else if (matchesTag(cleanQuery, "(#?phim[-_ ]?bo|#bo)")) {
            params.add("typeList=phim-bo");
            cleanQuery = removeTag(cleanQuery, "(#?phim[-_ ]?bo|#bo)");
        } else if (matchesTag(cleanQuery, "(#?hoat[-_ ]?hinh|#anime)")) {
            params.add("typeList=hoat-hinh");
            cleanQuery = removeTag(cleanQuery, "(#?hoat[-_ ]?hinh|#anime)");
        } else if (matchesTag(cleanQuery, "(#?tv[-_ ]?shows?|#tvshow)")) {
            params.add("typeList=tv-shows");
            cleanQuery = removeTag(cleanQuery, "(#?tv[-_ ]?shows?|#tvshow)");
        } else if (matchesTag(cleanQuery, "(#?phim[-_ ]?ngan|#shorts?)")) {
            params.add("typeList=phim-ngan");
            cleanQuery = removeTag(cleanQuery, "(#?phim[-_ ]?ngan|#shorts?)");
        }

        // 4. Quốc gia
        String[][] countryMap = {
                {"han-quoc", "(#?han[-_ ]?quoc|#korea|#korean|#hq)"},
                {"trung-quoc", "(#?trung[-_ ]?quoc|#china|#chinese|#tq)"},
                {"au-my", "(#?au[-_ ]?my|#usuk|#us[-_ ]?uk|#hollywood|#my)"},
                {"nhat-ban", "(#?nhat[-_ ]?ban|#japan|#japanese|#nb)"},
                {"thai-lan", "(#?thai[-_ ]?lan|#thailand|#thai)"},
                {"dai-loan", "(#?dai[-_ ]?loan|#taiwan)"},
                {"hong-kong", "(#?hong[-_ ]?kong|#hk)"},
                {"an-do", "(#?an[-_ ]?do|#india|#indian)"},
                {"viet-nam", "(#?viet[-_ ]?nam|#vietnam|#vn)"}
        };
        for (String[] entry : countryMap) {
            if (matchesTag(cleanQuery, entry[1])) {
                params.add("country=" + entry[0]);
                cleanQuery = removeTag(cleanQuery, entry[1]);
                break;
            }
        }

        // 5. Thể loại
        String[][] catMap = {
                {"co-trang", "(#?co[-_ ]?trang)"},
                {"hanh-dong", "(#?hanh[-_ ]?dong|#action)"},
                {"kinh-di", "(#?kinh[-_ ]?di|#horror)"},
                {"tinh-cam", "(#?tinh[-_ ]?cam|#lang[-_ ]?man|#romance)"},
                {"hai-huoc", "(#?hai[-_ ]?huoc|#comedy|#hai)"},
                {"tam-ly", "(#?tam[-_ ]?ly|#drama)"},
                {"hinh-su", "(#?hinh[-_ ]?su|#crime)"},
                {"vien-tuong", "(#?vien[-_ ]?tuong|#scifi|#sci-fi)"},
                {"vo-thuat", "(#?vo[-_ ]?thuat|#martial[-_ ]?arts)"},
                {"than-thoai", "(#?than[-_ ]?thoai|#fantasy)"},
                {"hoc-duong", "(#?hoc[-_ ]?duong|#school)"},
                {"chien-tranh", "(#?chien[-_ ]?tranh|#war)"},
                {"bi-an", "(#?bi[-_ ]?an|#mystery)"},
                {"phieu-luu", "(#?phieu[-_ ]?luu|#adventure)"},
                {"tai-lieu", "(#?tai[-_ ]?lieu|#documentary)"},
                {"gia-dinh", "(#?gia[-_ ]?dinh|#family)"},
                {"kinh-dien", "(#?kinh[-_ ]?dien|#classic)"}
        };
        for (String[] entry : catMap) {
            if (matchesTag(cleanQuery, entry[1])) {
                params.add("category=" + entry[0]);
                cleanQuery = removeTag(cleanQuery, entry[1]);
                break;
            }
        }

        // 6. Năm phát hành (vd: nam:2024, year:2024, #2024)
        java.util.regex.Matcher yearMatcher = java.util.regex.Pattern.compile("(?i)\\b(?:year|nam|#)?\\s*[:=]?\\s*(20[0-2][0-9]|19[89][0-9])\\b").matcher(cleanQuery);
        if (yearMatcher.find()) {
            params.add("year=" + yearMatcher.group(1));
            cleanQuery = cleanQuery.replace(yearMatcher.group(0), " ").trim();
        }

        // 7. Sắp xếp
        if (matchesTag(cleanQuery, "(#?xem[-_ ]?nhieu|#hot|#view)")) {
            params.add("sortField=view");
            cleanQuery = removeTag(cleanQuery, "(#?xem[-_ ]?nhieu|#hot|#view)");
        } else if (matchesTag(cleanQuery, "(#?danh[-_ ]?gia|#rating)")) {
            params.add("sortField=rating");
            cleanQuery = removeTag(cleanQuery, "(#?danh[-_ ]?gia|#rating)");
        } else if (matchesTag(cleanQuery, "(#?moi[-_ ]?nhat|#cap[-_ ]?nhat)")) {
            params.add("sortField=modified");
            cleanQuery = removeTag(cleanQuery, "(#?moi[-_ ]?nhat|#cap[-_ ]?nhat)");
        }

        // 8. Bảng chữ cái (vd: alphabet:A, chu:A)
        java.util.regex.Matcher alphaMatcher = java.util.regex.Pattern.compile("(?i)\\b(?:alphabet|chu)\\s*[:=]\\s*([A-Za-z#])\\b").matcher(cleanQuery);
        if (alphaMatcher.find()) {
            params.add("alphabet=" + alphaMatcher.group(1).toUpperCase());
            cleanQuery = cleanQuery.replace(alphaMatcher.group(0), " ").trim();
        }

        // 9. Chuẩn hóa từ khóa tìm kiếm còn lại
        cleanQuery = cleanQuery.replaceAll("[#:]", " ").replaceAll("\\s+", " ").trim();
        if (!cleanQuery.isEmpty()) {
            try {
                params.add(0, "search=" + java.net.URLEncoder.encode(cleanQuery, "UTF-8"));
            } catch (Exception ignored) {
                params.add(0, "search=" + cleanQuery);
            }
        }

        // 10. Phân trang
        params.add("page=" + page);

        StringBuilder sb = new StringBuilder();
        sb.append(baseUrl).append("/duyet-tim?");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append("&");
            sb.append(params.get(i));
        }

        return sb.toString();
    }

    private boolean matchesTag(String text, String regex) {
        return java.util.regex.Pattern.compile("(?i)(^|\\s)" + regex + "($|\\s)").matcher(text).find();
    }

    private String removeTag(String text, String regex) {
        return text.replaceAll("(?i)(^|\\s)" + regex + "($|\\s)", " ").replaceAll("\\s+", " ").trim();
    }
}
