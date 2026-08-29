package com.vieflix;

import com.cloudstream.core.model.EpisodeItem;
import com.cloudstream.core.model.MovieDetail;
import com.cloudstream.core.model.MovieItem;
import com.cloudstream.core.model.VideoLink;
import com.cloudstream.core.parser.MovieParser;
import com.cloudstream.core.util.HtmlHelper;
import com.cloudstream.core.util.RegexHelper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

/**
 * VieflixParser - Triển khai MovieParser bóc tách HTML chuyên biệt cho nguồn Vieflix.
 */
public class VieflixParser implements MovieParser {

    private static final VieflixParser INSTANCE = new VieflixParser();

    public static VieflixParser getInstance() {
        return INSTANCE;
    }

    // ==========================================
    // 1. PARSE DANH SÁCH PHIM
    // ==========================================

    @Override
    public List<MovieItem> parseMovieList(String html, String baseUrl) {
        Document document = Jsoup.parse(html, baseUrl);
        Elements elements = document.select("a[href^=/phim/]");

        List<MovieItem> result = new ArrayList<>();
        for (Element element : elements) {
            Element img = element.selectFirst("img");

            String title = (img != null) ? img.attr("alt") : "";
            if (title.isEmpty()) title = element.text();
            if (title.trim().isEmpty()) continue;

            String href = HtmlHelper.getAbsoluteUrl(baseUrl, element, "href");
            String poster = (img != null) ? img.attr("src") : "";

            result.add(new MovieItem(title, href, poster.isEmpty() ? null : poster));
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

        String searchSlug = "\\\"slug\\\":\\\"" + slug + "\\\"";
        String slugKey = "\\\"slug\\\":\\\"";
        String m3u8Key = "\\\"linkM3u8\\\":\\\"";
        String embedKey = "\\\"linkEmbed\\\":\\\"";
        String closingQuote = "\\\"";

        int startIndex = html.indexOf(searchSlug);

        while (startIndex != -1) {
            int endOfBlock = html.indexOf(slugKey, startIndex + searchSlug.length());
            String block = (endOfBlock != -1)
                    ? html.substring(startIndex, endOfBlock)
                    : html.substring(startIndex);

            // 1. Tìm Link M3U8
            String m3u8Url = RegexHelper.extractBetween(block, m3u8Key, closingQuote);
            if (m3u8Url != null && !m3u8Url.trim().isEmpty() && m3u8Url.contains(".m3u8")) {
                result.add(new VideoLink(VideoLink.TYPE_M3U8, m3u8Url, "Vieflix M3U8"));
            }

            // 2. Tìm Link Embed
            String embedUrl = RegexHelper.extractBetween(block, embedKey, closingQuote);
            if (embedUrl != null && !embedUrl.trim().isEmpty()) {
                if (embedUrl.contains("url=") && embedUrl.contains(".m3u8")) {
                    String m3u8 = embedUrl.substring(embedUrl.indexOf("url=") + 4);
                    int ampIdx = m3u8.indexOf('&');
                    if (ampIdx != -1) m3u8 = m3u8.substring(0, ampIdx);
                    result.add(new VideoLink(VideoLink.TYPE_M3U8, m3u8, "Vieflix Embed"));
                } else {
                    result.add(new VideoLink(VideoLink.TYPE_EMBED, embedUrl, "Vieflix Embed"));
                }
            }

            startIndex = html.indexOf(searchSlug, startIndex + searchSlug.length());
        }

        // Fallback: Tìm linkM3u8 đầu tiên trong HTML nếu không tìm thấy theo slug
        if (result.isEmpty()) {
            String fbM3u8 = RegexHelper.extractBetween(html, m3u8Key, closingQuote);
            if (fbM3u8 != null && !fbM3u8.trim().isEmpty() && fbM3u8.contains(".m3u8")) {
                result.add(new VideoLink(VideoLink.TYPE_M3U8, fbM3u8, "Vieflix Auto"));
            }
        }

        return result;
    }
}
