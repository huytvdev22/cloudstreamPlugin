package org.example;

import com.vieflix.VieflixLogic;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.List;

public class Main {

    public static void main(String[] args) {
        String baseUrl = "https://vieflix.top";

        try {
            System.out.println("==================================================");
            System.out.println("  1. TEST PARSE MOVIE LIST (DANH SÁCH PHIM)");
            System.out.println("==================================================");
            String url = baseUrl + "/duyet-tim?sortField=year&page=1";
            System.out.println("Đang fetch URL: " + url);
            
            Document listDoc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .get();
                    
            List<VieflixLogic.MovieItem> movies = VieflixLogic.parseMovieList(listDoc.html(), baseUrl);

            if (movies.isEmpty()) {
                System.out.println("❌ LỖI: Không tìm thấy phim nào!");
                return;
            }

            System.out.println("✅ Tìm thấy " + movies.size() + " phim.");
            VieflixLogic.MovieItem firstMovie = movies.get(0);
            System.out.println("-> Phim đầu tiên: " + firstMovie.title);
            System.out.println("-> URL: " + firstMovie.href);
            System.out.println("-> Poster: " + firstMovie.posterUrl);

            System.out.println("\n==================================================");
            System.out.println("  2. TEST PARSE MOVIE DETAIL (CHI TIẾT PHIM)");
            System.out.println("==================================================");
            System.out.println("Đang fetch chi tiết phim: " + firstMovie.href);
            
            Document detailDoc = Jsoup.connect(firstMovie.href)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .get();
                    
            VieflixLogic.MovieDetail detail = VieflixLogic.parseMovieDetail(detailDoc.html(), baseUrl);
            System.out.println("-> Tiêu đề: " + detail.title);
            System.out.println("-> Năm phát hành: " + detail.year);
            System.out.println("-> Thời lượng: " + detail.duration + " phút");
            System.out.println("-> Thể loại: " + detail.tags);
            System.out.println("-> Mô tả: " + (detail.plot != null ? (detail.plot.substring(0, Math.min(detail.plot.length(), 100)) + "...") : "null"));
            System.out.println("-> Tổng số tập: " + detail.episodes.size());

            if (detail.episodes.isEmpty()) {
                System.out.println("❌ LỖI: Không tìm thấy danh sách tập phim!");
                return;
            }

            System.out.println("\n==================================================");
            System.out.println("  3. TEST EXTRACT VIDEO LINKS (LẤY LINK PHÁT)");
            System.out.println("==================================================");
            VieflixLogic.EpisodeItem firstEp = detail.episodes.get(0);
            System.out.println("-> Đang thử tập: " + firstEp.name + " (" + firstEp.href + ")");

            String href = firstEp.href;
            int tapIdx = href.lastIndexOf("/tap-");
            String slug = "";
            if (tapIdx >= 0) {
                slug = href.substring(tapIdx + 1);
            }
            if (slug.contains("?")) slug = slug.substring(0, slug.indexOf("?"));

            System.out.println("-> Slug trích xuất: " + slug);
            List<VieflixLogic.VideoLink> links = VieflixLogic.extractVideoLinks(detailDoc.html(), slug);

            if (links.isEmpty()) {
                System.out.println("❌ LỖI: Không tìm thấy link video nào!");
            } else {
                for (VieflixLogic.VideoLink link : links) {
                    System.out.println("🎬 [Tìm thấy Link] Loại: " + link.type + " | Label: " + link.label + " | URL: " + link.url);
                }
            }

            System.out.println("\n==================================================");
            System.out.println("✅ TẤT CẢ CÁC BƯỚC TEST ĐỀU CHẠY XONG!");
            System.out.println("==================================================");

        } catch (Exception e) {
            System.err.println("Lỗi trong quá trình chạy test: " + e.getMessage());
            e.printStackTrace();
        }
    }
}