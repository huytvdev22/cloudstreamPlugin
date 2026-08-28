package com.vieflix;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VieflixLogic - Class Java chua toan bo logic nghiep vu cua Vieflix.
 */
public class VieflixLogic {

    // ==========================================
    // INNER DATA CLASSES (Plain Java Beans)
    // ==========================================

    /** Ket qua parse mot item phim tu danh sach / trang chu / tim kiem. */
    public static class MovieItem {
        public final String title;
        public final String href;
        public final String posterUrl;

        public MovieItem(String title, String href, String posterUrl) {
            this.title = title;
            this.href = href;
            this.posterUrl = posterUrl;
        }
    }

    /** Ket qua parse chi tiet phim (load page). */
    public static class MovieDetail {
        public final String title;
        public final String posterUrl;
        public final String plot;
        public final Integer year;
        public final Integer duration;
        public final List<String> tags;
        public final List<EpisodeItem> episodes;

        public MovieDetail(String title, String posterUrl, String plot,
                Integer year, Integer duration, List<String> tags, List<EpisodeItem> episodes) {
            this.title = title;
            this.posterUrl = posterUrl;
            this.plot = plot;
            this.year = year;
            this.duration = duration;
            this.tags = tags;
            this.episodes = episodes;
        }
    }

    /** Dai dien cho mot tap phim. */
    public static class EpisodeItem {
        public final String href;
        public final String name;
        public final int episodeNum;

        public EpisodeItem(String href, String name, int episodeNum) {
            this.href = href;
            this.name = name;
            this.episodeNum = episodeNum;
        }
    }

    /** Mot link video da duoc trich xuat (M3U8 hoac Embed). */
    public static class VideoLink {
        public static final String TYPE_M3U8 = "M3U8";
        public static final String TYPE_EMBED = "EMBED";

        public final String type;
        public final String url;
        public final String label;

        public VideoLink(String type, String url, String label) {
            this.type = type;
            this.url = url;
            this.label = label;
        }
    }

    // ==========================================
    // 1. PARSE DANH SACH PHIM
    // ==========================================

    /**
     * Parse danh sach phim tu HTML cua trang danh sach hoac trang chu.
     * Selector: a[href^=/phim/] - lay tat ca link phim.
     *
     * @param html    Noi dung HTML tho cua trang can parse
     * @param baseUrl URL goc cua site (dung de fix href tuong doi -> tuyet doi)
     * @return Danh sach MovieItem, bo qua cac item khong co title
     */
    public static List<MovieItem> parseMovieList(String html, String baseUrl) {
        Document document = Jsoup.parse(html, baseUrl);
        Elements elements = document.select("a[href^=/phim/]");

        List<MovieItem> result = new ArrayList<>();
        for (Element element : elements) {
            Element img = element.selectFirst("img");

            // Uu tien lay alt cua img, fallback ve text cua the <a>
            String title = "";
            if (img != null) title = img.attr("alt");
            if (title.isEmpty()) title = element.text();
            if (title.isBlank()) continue;

            // Fix href tuong doi -> tuyet doi
            String href = element.attr("abs:href");
            if (href.isEmpty()) href = baseUrl + element.attr("href");

            String poster = (img != null) ? img.attr("src") : "";
            result.add(new MovieItem(title, href, poster.isEmpty() ? null : poster));
        }
        return result;
    }

    // ==========================================
    // 2. PARSE CHI TIET PHIM
    // ==========================================

    /**
     * Parse chi tiet mot trang phim: title, poster, plot, year, duration, tags, episodes.
     *
     * @param html    Noi dung HTML tho cua trang phim
     * @param baseUrl URL goc dung de resolve href
     * @return MovieDetail da parse
     */
    public static MovieDetail parseMovieDetail(String html, String baseUrl) {
        Document document = Jsoup.parse(html, baseUrl);

        // --- Title ---
        Element h1 = document.selectFirst("h1");
        String title = (h1 != null && !h1.text().isBlank()) ? h1.text() : "Khong co ten";

        // --- Poster ---
        Element posterEl = document.selectFirst("img[src*=/movies/]");
        if (posterEl == null) posterEl = document.selectFirst("img");
        String posterUrl = (posterEl != null) ? posterEl.attr("src") : null;

        // --- Plot ---
        String plot = parsePlot(html, document);

        // --- Duration ---
        Integer duration = parseIntByRegex(html, "Thoi luong:[^0-9]*([0-9]+)");

        // --- Year ---
        Integer year = parseIntByRegex(html, "\\b(19\\d{2}|20\\d{2})\\b");

        // --- Tags ---
        Elements tagElements = document.select("a[href*=/the-loai/], a[href*=/quoc-gia/]");
        List<String> tags = new ArrayList<>();
        for (Element tag : tagElements) {
            String text = tag.text().trim();
            if (!text.isEmpty()) tags.add(text);
        }

        // --- Episodes ---
        List<EpisodeItem> episodes = parseEpisodes(document, baseUrl);

        return new MovieDetail(title, posterUrl, plot, year, duration, tags, episodes);
    }

    /**
     * Trich xuat noi dung gioi thieu (plot) tu HTML.
     * Uu tien regex lay doan van sau "Gioi thieu:", fallback ve the p dau tien > 50 ky tu.
     */
    private static String parsePlot(String html, Document document) {
        Pattern plotPattern = Pattern.compile(
            "Gi\u1edbi thi\u1ec7u:.*?<p[^>]*>(.*?)</p>", Pattern.DOTALL);
        Matcher plotMatcher = plotPattern.matcher(html);
        if (plotMatcher.find()) {
            return plotMatcher.group(1).replaceAll("<[^>]*>", "").trim();
        }

        for (Element p : document.select("p")) {
            String text = p.text();
            if (text.length() > 50) {
                String marker = "Gi\u1edbi thi\u1ec7u:";
                int idx = text.indexOf(marker);
                return idx >= 0 ? text.substring(idx + marker.length()).trim() : text.trim();
            }
        }
        return null;
    }

    /**
     * Parse danh sach tap phim tu document.
     */
    private static List<EpisodeItem> parseEpisodes(Document document, String baseUrl) {
        Elements epElements = document.select("a[href*=/tap-]");
        List<EpisodeItem> result = new ArrayList<>();
        List<String> seenHrefs = new ArrayList<>();

        int index = 0;
        for (Element ep : epElements) {
            String href = ep.attr("abs:href");
            if (href.isEmpty()) href = baseUrl + ep.attr("href");

            if (seenHrefs.contains(href)) continue;
            seenHrefs.add(href);

            int tapIdx = href.lastIndexOf("/tap-");
            String afterTap = (tapIdx >= 0) ? href.substring(tapIdx + "/tap-".length()) : "";
            int qIdx = afterTap.indexOf('?');
            if (qIdx >= 0) afterTap = afterTap.substring(0, qIdx);

            Integer epNum = null;
            try { epNum = Integer.parseInt(afterTap); } catch (NumberFormatException ignored) {}
            int finalEpNum = (epNum != null) ? epNum : (index + 1);

            String name = ep.text().trim();
            if (name.isEmpty()) name = "Tap " + finalEpNum;

            result.add(new EpisodeItem(href, name, finalEpNum));
            index++;
        }
        return result;
    }

    // ==========================================
    // 3. TRICH XUAT LINK VIDEO (M3U8 / Embed)
    // ==========================================

    /**
     * Trich xuat tat ca link video tu HTML trang phim goc.
     */
    public static List<VideoLink> extractVideoLinks(String html, String slug) {
        List<VideoLink> result = new ArrayList<>();

        String searchSlug    = "\\\"slug\\\":\\\"" + slug + "\\\"";
        String slugKey       = "\\\"slug\\\":\\\"";
        String m3u8Key       = "\\\"linkM3u8\\\":\\\"";
        String embedKey      = "\\\"linkEmbed\\\":\\\"";
        String closingQuote  = "\\\"";

        int startIndex = html.indexOf(searchSlug);

        while (startIndex != -1) {
            int endOfBlock = html.indexOf(slugKey, startIndex + searchSlug.length());
            String block = (endOfBlock != -1)
                    ? html.substring(startIndex, endOfBlock)
                    : html.substring(startIndex);

            // --- Tim linkM3u8 ---
            int m3Idx = block.indexOf(m3u8Key);
            if (m3Idx != -1) {
                int valueStart = m3Idx + m3u8Key.length();
                int endM3Idx = block.indexOf(closingQuote, valueStart);
                if (endM3Idx != -1) {
                    String m3u8Url = block.substring(valueStart, endM3Idx);
                    if (!m3u8Url.isBlank() && m3u8Url.contains(".m3u8")) {
                        result.add(new VideoLink(VideoLink.TYPE_M3U8, m3u8Url, "Vieflix M3U8"));
                    }
                }
            }

            // --- Tim linkEmbed ---
            int emIdx = block.indexOf(embedKey);
            if (emIdx != -1) {
                int embedValueStart = emIdx + embedKey.length();
                int endEmIdx = block.indexOf(closingQuote, embedValueStart);
                if (endEmIdx != -1) {
                    String embedUrl = block.substring(embedValueStart, endEmIdx);
                    if (!embedUrl.isBlank()) {
                        if (embedUrl.contains("url=") && embedUrl.contains(".m3u8")) {
                            String m3u8 = embedUrl.substring(embedUrl.indexOf("url=") + 4);
                            int ampIdx = m3u8.indexOf('&');
                            if (ampIdx != -1) m3u8 = m3u8.substring(0, ampIdx);
                            result.add(new VideoLink(VideoLink.TYPE_M3U8, m3u8, "Vieflix Embed"));
                        } else {
                            result.add(new VideoLink(VideoLink.TYPE_EMBED, embedUrl, "Vieflix Embed"));
                        }
                    }
                }
            }

            startIndex = html.indexOf(searchSlug, startIndex + searchSlug.length());
        }

        // Fallback: khong tim duoc theo slug -> lay linkM3u8 dau tien trong toan bo HTML
        if (result.isEmpty()) {
            int fallbackIdx = html.indexOf(m3u8Key);
            if (fallbackIdx != -1) {
                int fbValueStart = fallbackIdx + m3u8Key.length();
                int endFbIdx = html.indexOf(closingQuote, fbValueStart);
                if (endFbIdx != -1) {
                    String fbM3u8 = html.substring(fbValueStart, endFbIdx);
                    if (!fbM3u8.isBlank() && fbM3u8.contains(".m3u8")) {
                        result.add(new VideoLink(VideoLink.TYPE_M3U8, fbM3u8, "Vieflix Auto"));
                    }
                }
            }
        }

        return result;
    }

    private static Integer parseIntByRegex(String html, String pattern) {
        Matcher matcher = Pattern.compile(pattern).matcher(html);
        if (matcher.find()) {
            try { return Integer.parseInt(matcher.group(1)); }
            catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
