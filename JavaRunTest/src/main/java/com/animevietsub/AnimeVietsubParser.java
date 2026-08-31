package com.animevietsub;

import com.cloudstream.core.model.EpisodeItem;
import com.cloudstream.core.model.MainPageSection;
import com.cloudstream.core.model.MovieDetail;
import com.cloudstream.core.model.MovieItem;
import com.cloudstream.core.model.VideoLink;
import com.cloudstream.core.parser.MovieParser;
import com.cloudstream.core.util.HtmlHelper;
import com.cloudstream.core.util.RegexHelper;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AnimeVietsubParser - Triển khai MovieParser bóc tách HTML/JSON cho nguồn AnimeVietsub.
 */
public class AnimeVietsubParser implements MovieParser {

    public static final String PORTAL_URL = "https://bit.ly/animevietsubtv";
    public static final String DEFAULT_BASE_URL = "https://animevietsub.li";

    private static final AnimeVietsubParser INSTANCE = new AnimeVietsubParser();

    public static AnimeVietsubParser getInstance() {
        return INSTANCE;
    }

    // ==========================================
    // 0. DOMAIN RESOLVER & URL NORMALIZER
    // ==========================================

    /**
     * Chuẩn hóa URL sang domain đang hoạt động (loại bỏ các domain cũ như animevietsub.work, animevietsub.tv).
     */
    public String normalizeUrl(String url, String currentBaseUrl) {
        if (url == null || url.trim().isEmpty()) return "";
        String clean = url.trim();
        if (clean.startsWith("/")) {
            return currentBaseUrl + clean;
        }
        if (clean.startsWith("http://") || clean.startsWith("https://")) {
            try {
                java.net.URI uri = new java.net.URI(clean);
                String path = uri.getRawPath();
                String query = uri.getRawQuery();
                return currentBaseUrl + (path != null ? path : "") + (query != null ? "?" + query : "");
            } catch (Exception ignored) {
            }
        }
        return currentBaseUrl + "/" + clean;
    }

    // ==========================================
    // 1. PARSE DANH MỤC TRANG CHỦ (MAIN PAGE SECTIONS)
    // ==========================================

    @Override
    public List<MainPageSection> parseMainPage(String html, String baseUrl) {
        List<MainPageSection> list = new ArrayList<>();
        if (html == null || html.trim().isEmpty()) return list;

        Document doc = Jsoup.parse(html, baseUrl);
        Elements menuLinks = doc.select(".nav a, .menu a, .dropdown-menu a");
        List<String> seenPaths = new ArrayList<>();

        for (Element a : menuLinks) {
            String href = a.attr("href").trim();
            String title = a.text().trim();

            if (href.isEmpty() || title.isEmpty() || href.equals("#") || href.contains("javascript:")) continue;
            if (href.contains("/phim/") || href.contains("/account/")) continue;

            String path;
            if (href.startsWith("http://") || href.startsWith("https://")) {
                try {
                    java.net.URI uri = new java.net.URI(href);
                    path = uri.getRawPath();
                } catch (Exception e) {
                    continue;
                }
            } else {
                path = href.startsWith("/") ? href : "/" + href;
            }

            if (path != null && !seenPaths.contains(path) && (path.contains("danh-sach") || path.contains("the-loai") || path.contains("bang-xep-hang"))) {
                seenPaths.add(path);
                list.add(new MainPageSection(title, path));
            }
        }
        return list;
    }

    // ==========================================
    // 2. PARSE DANH SÁCH ANIME (CARD LIST)
    // ==========================================

    @Override
    public List<MovieItem> parseMovieList(String html, String baseUrl) {
        List<MovieItem> result = new ArrayList<>();
        if (html == null || html.trim().isEmpty()) return result;

        Document doc = Jsoup.parse(html, baseUrl);
        Elements items = doc.select(".TPostMv, .item, .film-item, li:has(a[href*=/phim/])");
        List<String> seenHrefs = new ArrayList<>();

        for (Element el : items) {
            Element linkEl = el.selectFirst("a[href*=/phim/]");
            if (linkEl == null) continue;

            String rawHref = linkEl.attr("href").trim();
            if (rawHref.isEmpty() || rawHref.contains("xem-phim") || rawHref.contains("/tap-")) continue;

            String href = normalizeUrl(rawHref, baseUrl);
            if (seenHrefs.contains(href)) continue;
            seenHrefs.add(href);

            // Tiêu đề
            String title = "";
            Element titleEl = el.selectFirst(".Title, .title, .name, h2, h3");
            if (titleEl != null && !titleEl.text().trim().isEmpty()) {
                title = titleEl.text().trim();
            } else if (!linkEl.attr("title").trim().isEmpty()) {
                title = linkEl.attr("title").trim();
            }
            if (title.isEmpty()) continue;

            // Poster image
            Element imgEl = el.selectFirst("img");
            String posterUrl = null;
            if (imgEl != null) {
                if (imgEl.hasAttr("data-src") && !imgEl.attr("data-src").trim().isEmpty()) {
                    posterUrl = imgEl.attr("data-src").trim();
                } else if (imgEl.hasAttr("src") && !imgEl.attr("src").trim().isEmpty()) {
                    posterUrl = imgEl.attr("src").trim();
                }
            }

            // Badges / Tags (Tập số, Vietsub, Thuyết minh, Season...)
            List<String> badges = new ArrayList<>();
            Elements badgeEls = el.select(".mli-eps, .label, .status, .episode, .year, .ribbon, span[class*=badge]");
            for (Element b : badgeEls) {
                String bText = b.text().trim();
                if (!bText.isEmpty() && !badges.contains(bText)) {
                    badges.add(bText);
                }
            }

            result.add(new MovieItem(title, href, posterUrl, badges));
        }

        return result;
    }

    // ==========================================
    // 3. PARSE CHI TIẾT ANIME & DANH SÁCH TẬP
    // ==========================================

    @Override
    public MovieDetail parseMovieDetail(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);

        // 1. Tiêu đề
        String title = HtmlHelper.selectFirstText(doc, "h1.Title, h1.title, h1");
        if (title.isEmpty()) title = "Anime";

        // 2. Poster
        Element posterEl = doc.selectFirst(".Image img, .poster img, .thumb img, meta[property=og:image]");
        String posterUrl = null;
        if (posterEl != null) {
            if (posterEl.tagName().equalsIgnoreCase("meta")) {
                posterUrl = posterEl.attr("content");
            } else if (posterEl.hasAttr("data-src") && !posterEl.attr("data-src").trim().isEmpty()) {
                posterUrl = posterEl.attr("data-src");
            } else {
                posterUrl = posterEl.attr("src");
            }
        }

        // 3. Mô tả (Plot)
        String plot = null;
        Element descEl = doc.selectFirst(".Description, .content, .film-info-description, #synopsis");
        if (descEl != null) {
            plot = descEl.text().trim();
        }
        if (plot == null || plot.isEmpty()) {
            Element metaDesc = doc.selectFirst("meta[property=og:description], meta[name=description]");
            if (metaDesc != null) {
                plot = metaDesc.attr("content").trim();
            }
        }

        // 4. Năm & Thời lượng
        Integer year = RegexHelper.parseInt(html, "\\b(19\\d{2}|20\\d{2})\\b");
        Integer duration = RegexHelper.parseInt(html, "([0-9]+)\\s*(?:phút|mins|m)");

        // 5. Thể loại (Tags)
        List<String> tags = new ArrayList<>();
        Elements tagEls = doc.select("a[href*=/the-loai/], a[href*=/quoc-gia/]");
        for (Element t : tagEls) {
            String tText = t.text().trim();
            if (!tText.isEmpty() && !tags.contains(tText)) {
                tags.add(tText);
            }
        }

        // 6. Danh sách tập phim (Parse từ thẻ a[href*=/tap-] hoặc link xem phim)
        List<EpisodeItem> episodes = parseEpisodes(doc, html, baseUrl);

        return new MovieDetail(title, posterUrl, plot, year, duration, tags, episodes);
    }

    private List<EpisodeItem> parseEpisodes(Document doc, String rawHtml, String baseUrl) {
        List<EpisodeItem> episodes = new ArrayList<>();
        List<String> seenHrefs = new ArrayList<>();

        Elements epElements = doc.select("a[href*=/tap-], a.btn-episode, .list-episode a");
        int index = 1;
        for (Element ep : epElements) {
            String rawHref = ep.attr("href").trim();
            if (rawHref.isEmpty() || rawHref.contains("account/login")) continue;

            String href = normalizeUrl(rawHref, baseUrl);
            if (seenHrefs.contains(href)) continue;
            seenHrefs.add(href);

            Integer epNum = RegexHelper.parseInt(href, "/tap-([0-9]+)");
            if (epNum == null) {
                epNum = RegexHelper.parseInt(ep.text(), "([0-9]+)");
            }
            int finalEpNum = (epNum != null) ? epNum : index;

            String name = ep.text().trim();
            if (name.isEmpty() || name.matches("\\d+")) {
                name = "Tập " + finalEpNum;
            }

            episodes.add(new EpisodeItem(href, name, finalEpNum));
            index++;
        }

        // Fallback: nếu trang chi tiết chưa có danh sách tập nhưng có nút "Xem Phim"
        if (episodes.isEmpty()) {
            Element watchBtn = doc.selectFirst("a[href*=/xem-phim], a[href*=/tap-]");
            if (watchBtn != null) {
                String href = normalizeUrl(watchBtn.attr("href").trim(), baseUrl);
                episodes.add(new EpisodeItem(href, "Tập 1", 1));
            }
        }

        return episodes;
    }

    // ==========================================
    // 4. TRÍCH XUẤT LINK STREAM (VIDEO LINKS)
    // ==========================================

    @Override
    public List<VideoLink> extractVideoLinks(String html, String slugOrData) {
        List<VideoLink> list = new ArrayList<>();
        if (html == null || html.trim().isEmpty()) return list;

        // 1. Trích xuất từ window.PLAYER_DATA trong script
        String pDataJson = RegexHelper.extractGroup(html, "window\\.PLAYER_DATA\\s*=\\s*(\\{[\\s\\S]*?\\});", 1);
        if (pDataJson != null) {
            try {
                JSONObject obj = new JSONObject(pDataJson);
                String link = obj.optString("link", "");
                String playTech = obj.optString("playTech", "iframe");
                if (!link.isEmpty()) {
                    if (link.contains(".m3u8")) {
                        list.add(new VideoLink(VideoLink.TYPE_M3U8, link, "AnimeVietsub Direct (M3U8)", "Server DU", "Vietsub"));
                    } else {
                        list.add(new VideoLink(VideoLink.TYPE_EMBED, link, "AnimeVietsub Player", "Server DU", "Vietsub"));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 2. Tìm trực tiếp URL m3u8 trong HTML/JS
        String m3u8Url = RegexHelper.extractGroup(html, "[\"'](https?://[^\"']+\\.m3u8[^\"']*)[\"']", 1);
        if (m3u8Url != null && !containsLink(list, m3u8Url)) {
            list.add(new VideoLink(VideoLink.TYPE_M3U8, m3u8Url, "AnimeVietsub Auto (M3U8)", "Server Direct", "Vietsub"));
        }

        // 3. Tìm iframe stream player
        String iframeSrc = RegexHelper.extractGroup(html, "<iframe[^>]+src=[\"']([^\"']+)[\"']", 1);
        if (iframeSrc != null && !containsLink(list, iframeSrc)) {
            list.add(new VideoLink(VideoLink.TYPE_EMBED, iframeSrc, "AnimeVietsub Embed", "Server Embed", "Vietsub"));
        }

        return list;
    }

    private boolean containsLink(List<VideoLink> list, String url) {
        for (VideoLink l : list) {
            if (l.url.equalsIgnoreCase(url)) return true;
        }
        return false;
    }

    // ==========================================
    // 5. BUILD SEARCH URL
    // ==========================================

    /**
     * Xây dựng URL tìm kiếm phân trang cho AnimeVietsub.
     */
    public String buildSearchUrl(String baseUrl, String query, int page) {
        if (query == null) query = "";
        String cleanQuery = query.trim();

        String encodedQuery;
        try {
            encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            encodedQuery = cleanQuery;
        }

        if (page > 1) {
            return baseUrl + "/tim-kiem/" + encodedQuery + "/trang-" + page + ".html";
        } else {
            return baseUrl + "/tim-kiem/" + encodedQuery + "/";
        }
    }
}
