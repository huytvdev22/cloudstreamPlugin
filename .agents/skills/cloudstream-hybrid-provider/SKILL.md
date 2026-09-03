---
name: cloudstream-hybrid-provider
description: >-
  Hướng dẫn quy trình chuẩn hóa để phân tích, bóc tách và triển khai một CloudStream Provider mới theo kiến trúc Hybrid Pattern (Java Core + Kotlin Adapter) trong repository này. Kích hoạt kỹ năng này khi người dùng yêu cầu tạo mới, tích hợp thêm website phim/anime bất kỳ (như XemPlay, VieFlix, Chợ Phim, AnimeVietsub...) hoặc bảo trì/sửa lỗi provider hiện có.
---

# Quy Trình Triển Khai CloudStream Provider Chuẩn (Hybrid Pattern)

Quy trình chuẩn hóa phát triển CloudStream Provider theo mô hình kiến trúc **Hybrid Pattern**:
- **Tầng 1 (Java Core & Test):** Nằm trong `JavaRunTest/`, viết bằng **Java 8 thuần túy**, kiểm thử tự động bằng Maven và JUnit 5 trong vài giây độc lập với Android SDK.
- **Tầng 2 (Kotlin CloudStream Plugin):** Nằm trong `[Name]Provider/`, là một **Thin Adapter** siêu mỏng chỉ làm nhiệm vụ kết nối CloudStream framework (Metadata, Network, UI Mapping). Mã nguồn Java được tự động đồng bộ từ `JavaRunTest` qua Gradle task `syncJavaFromTest`.

---

## Sơ Đồ Cấu Trúc Thư Mục

```text
cloudstreamPlugin/
├── JavaRunTest/
│   ├── src/main/java/com/[provider]/
│   │   ├── [Name]Parser.java          # Triển khai MovieParser (bóc tách DOM, JSON, Regex, Stream M3U8)
│   │   └── [Name]Logic.java           # Static Facade & Type Aliases cho Kotlin Adapter
│   └── src/test/java/com/[provider]/
│       └── [Name]LogicTest.java       # Bộ 6 Test Cases JUnit 5 tự động (Mock & Live Network)
│
└── [Name]Provider/
    ├── build.gradle.kts               # Plugin Metadata + task syncJavaFromTest
    └── src/main/kotlin/com/[provider]/
        ├── [Name]Plugin.kt            # @CloudstreamPlugin entrypoint
        └── [Name]Provider.kt          # Kế thừa MainAPI(), tự động update domain động, loadLinks kèm Referer
```

---

## Quy Trình Triển Khai 8 Bước

### Bước 1: Khảo sát Kỹ thuật Nguồn Phim (Discovery)
1. **Kiểm tra Tên miền Portal & Redirect:**
   - Kiểm tra xem nguồn có tên miền chuyển tiếp (portal) như `.com` trỏ sang `.uk`/`.top` hay không (ví dụ: `xemplay.com` -> `xemplay.uk`, `vieflix.com` -> `vieflix.top`).
   - Xác định thẻ chứa tên miền động: `<link rel="alternate" href="...">`, `<a class="top-cta" href="...">`, hoặc JSON-LD schema `sameAs`.
2. **Kiểm tra Danh mục & Trang chủ:**
   - Lấy URL duyệt phim theo thể loại, phim bộ, phim lẻ, hoạt hình: `/browse?type=...`, `/browse?category=...`.
   - Xác định cấu trúc thẻ phim HTML: selector `a[href*='/phim/']`, thẻ ảnh `img[src]` / `data-src`, tiêu đề `h3`/`alt`, và các badge (`HD`, `FHD`, `Vietsub`, `Thuyết minh`).
3. **Kiểm tra Chi tiết Phim & Tập phim:**
   - Trang chi tiết `/phim/{slug}`: Kiểm tra JSON-LD schema (`@type: "Movie"` / `"TVSeries"`), các thẻ meta OG.
   - Danh sách tập: Kiểm tra thẻ HTML `<a href*='/tap-'>` hoặc khối Next.js React Server Components (RSC) trong thẻ `<script>`.
4. **Kiểm tra Link Video Streaming (`loadLinks`):**
   - Mở trang xem tập: `/phim/{slug}/tap-{xx}` hoặc `/phim/{slug}/full`.
   - Bắt gói tin mạng hoặc quét HTML tìm link HLS master playlist `.m3u8` (thường nằm ở endpoint API như `/api/stream?t=...`).
   - Kiểm tra xem stream có yêu cầu header `Referer` hay Cookie không.

---

### Bước 2: Tạo Package Java Core trong `JavaRunTest`
Tạo package mới tại `JavaRunTest/src/main/java/com/[provider]/`.

---

### Bước 3: Viết `[Name]Parser.java` (Triển khai `MovieParser`)
Tạo class singleton kế thừa `com.cloudstream.core.parser.MovieParser`:

```java
package com.[provider];

import com.cloudstream.core.model.*;
import com.cloudstream.core.parser.MovieParser;
import com.cloudstream.core.util.HtmlHelper;
import com.cloudstream.core.util.RegexHelper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.*;
import java.util.regex.*;

public class [Name]Parser implements MovieParser {

    public static final String PORTAL_URL = "https://[provider].com";
    public static final String DEFAULT_BASE_URL = "https://[provider].uk";
    private static final [Name]Parser INSTANCE = new [Name]Parser();

    public static [Name]Parser getInstance() {
        return INSTANCE;
    }

    // 0. Bóc tách tên miền động từ Portal
    public String parseDomain(String html) {
        if (html == null || html.trim().isEmpty()) return DEFAULT_BASE_URL;
        Document doc = Jsoup.parse(html);
        Element altLink = doc.selectFirst("link[rel='alternate'][href]");
        if (altLink != null && isValidDomain(altLink.attr("href"))) {
            return cleanDomain(altLink.attr("href"));
        }
        return DEFAULT_BASE_URL;
    }

    // 1. Danh mục trang chủ (Sections)
    @Override
    public List<MainPageSection> parseMainPage(String content, String baseUrl) {
        List<MainPageSection> sections = new ArrayList<>();
        sections.add(new MainPageSection("🔥 Phim Mới Cập Nhật", "/browse?type=phim-moi-cap-nhat"));
        sections.add(new MainPageSection("📺 Phim Bộ Mới Nhất", "/browse?type=phim-bo"));
        sections.add(new MainPageSection("🎬 Phim Lẻ Mới Nhất", "/browse?type=phim-le"));
        return sections;
    }

    // 2. Danh sách phim (Listing)
    @Override
    public List<MovieItem> parseMovieList(String html, String baseUrl) {
        List<MovieItem> list = new ArrayList<>();
        if (html == null || html.isEmpty()) return list;
        Document doc = Jsoup.parse(html, baseUrl);
        Set<String> seen = new HashSet<>();

        Elements cards = doc.select("a[href*='/phim/']");
        for (Element card : cards) {
            String href = HtmlHelper.getAbsoluteUrl(baseUrl, card, "href");
            href = href.replaceAll("(/tap-[^/?#]+|/full)(\\?.*)?$", "");
            if (seen.contains(href)) continue;

            Element img = card.selectFirst("img");
            if (img == null) continue;

            String title = HtmlHelper.selectFirstText(card, "h3");
            if (title.isEmpty()) title = img.attr("alt").trim();
            if (title.isEmpty()) continue;

            String poster = img.attr("src");
            List<String> tags = new ArrayList<>(); // HD, Vietsub...

            seen.add(href);
            list.add(new MovieItem(title, href, poster, tags));
        }
        return list;
    }

    // 3. Chi tiết phim & danh sách tập (Detail & Episodes)
    @Override
    public MovieDetail parseMovieDetail(String html, String baseUrl) {
        // Trích xuất metadata từ JSON-LD schema (@type: Movie / TVSeries)
        // Trích xuất danh sách tập từ a[href*='/tap-'] hoặc Next.js RSC
        // Fallback 1 tập Full cho phim lẻ
        return new MovieDetail(title, posterUrl, plot, year, duration, tags, episodes);
    }

    // 4. Trích xuất link video stream (Extract Video Links)
    @Override
    public List<VideoLink> extractVideoLinks(String html, String slugOrData) {
        List<VideoLink> links = new ArrayList<>();
        // Trích xuất URL /api/stream?t=... hoặc regex m3u8
        Matcher m = Pattern.compile("(/api/stream\\?t=[^\"'\\s&\\\\]+)").matcher(html);
        if (m.find()) {
            String fullUrl = HtmlHelper.getAbsoluteUrl(DEFAULT_BASE_URL, doc.createElement("a").attr("href", m.group(1)), "href");
            links.add(new VideoLink(VideoLink.TYPE_M3U8, fullUrl, "HLS VIP", "Máy chủ 1", "Vietsub"));
        }
        return links;
    }

    // 5. Smart Search URL
    public String buildSearchUrl(String baseUrl, String query, int page) {
        // Hỗ trợ hashtag thông minh: #phimbo, #phimle, #hanquoc, nam:YYYY
        return baseUrl + "/browse?q=" + urlEncode(query) + "&page=" + page;
    }
}
```

---

### Bước 4: Viết `[Name]Logic.java` (Static Facade)
Cung cấp các hàm tĩnh ủy quyền sang `[Name]Parser.getInstance()` và Type Aliases cho Kotlin:

```java
package com.[provider];

import com.cloudstream.core.model.*;
import java.util.List;

public class [Name]Logic {
    public static final String PORTAL_URL = [Name]Parser.PORTAL_URL;
    public static final String DEFAULT_BASE_URL = [Name]Parser.DEFAULT_BASE_URL;

    public static String parseDomain(String html) {
        return [Name]Parser.getInstance().parseDomain(html);
    }

    public static List<MainPageSection> parseMainPage(String content) {
        return [Name]Parser.getInstance().parseMainPage(content, DEFAULT_BASE_URL);
    }

    public static List<MovieItem> parseMovieList(String html, String baseUrl) {
        return [Name]Parser.getInstance().parseMovieList(html, baseUrl);
    }

    public static MovieDetail parseMovieDetail(String html, String baseUrl) {
        return [Name]Parser.getInstance().parseMovieDetail(html, baseUrl);
    }

    public static List<VideoLink> extractVideoLinks(String html, String slugOrData) {
        return [Name]Parser.getInstance().extractVideoLinks(html, slugOrData);
    }

    public static String buildSearchUrl(String baseUrl, String query, int page) {
        return [Name]Parser.getInstance().buildSearchUrl(baseUrl, query, page);
    }
}
```

---

### Bước 5: Viết `[Name]LogicTest.java` (Bộ 6 Test Cases)
Viết Unit & Integration Test trong `JavaRunTest/src/test/java/com/[provider]/`:
1. `test00_ParseDomain`: Kiểm tra lấy tên miền động từ portal.
2. `test01_ParseMainPageSections`: Kiểm tra danh mục trang chủ.
3. `test02_ParseMovieList`: Kiểm tra cào danh sách phim (Mock HTML & Live Network).
4. `test03_BuildSearchUrlAndSearch`: Kiểm tra Smart Search & Live Network Search.
5. `test04_ParseMovieDetail_MovieAndSeries`: Kiểm tra chi tiết phim lẻ (1 tập Full) và phim bộ (nhiều tập).
6. `test05_ExtractVideoLinks`: Kiểm tra trích xuất M3U8 và xác minh header `#EXTM3U` phản hồi từ stream URL.

---

### Bước 6: Chạy Kiểm Thử Tự Động trong WSL
Thực thi lệnh Maven qua WSL:
```bash
wsl bash -ic "cd /mnt/d/2.projects/fl/cloudstreamPlugin/cloudstreamPlugin/JavaRunTest && mvn test -Dtest=[Name]LogicTest"
```
> **Yêu cầu:** Toàn bộ 6/6 test cases phải **BUILD SUCCESS** trước khi chuyển sang bước viết tầng Kotlin.

---

### Bước 7: Khởi Tạo Module Kotlin Plugin `[Name]Provider`
1. **Tạo `[Name]Provider/build.gradle.kts`:**
   ```kotlin
   import java.text.SimpleDateFormat
   import java.util.Date
   import java.util.TimeZone

   version = 1

   cloudstream {
       description = "Nguồn xem phim [Name]"
       authors = listOf("HuyTV")
       status = 1 // 1: Ok, 2: Slow, 3: Beta
       tvTypes = listOf("Movie", "TvSeries", "Anime")
       requiresResources = false
       language = "vi"
       iconUrl = "https://[provider].uk/logo.png"
   }

   tasks.register<Copy>("syncJavaFromTest") {
       val sourceDir = file("${rootProject.projectDir}/JavaRunTest/src/main/java")
       if (sourceDir.exists()) {
           from(sourceDir)
           into(file("src/main/java"))
       }
   }

   tasks.matching { it.name.startsWith("compile") || it.name == "preBuild" }.configureEach {
       dependsOn("syncJavaFromTest")
   }
   ```

2. **Tạo `[Name]Provider/src/main/kotlin/com/[provider]/[Name]Plugin.kt`:**
   ```kotlin
   package com.[provider]

   import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
   import com.lagradost.cloudstream3.plugins.Plugin
   import android.content.Context

   @CloudstreamPlugin
   class [Name]Plugin : Plugin() {
       override fun load(context: Context) {
           registerMainAPI([Name]Provider())
       }
   }
   ```

3. **Tạo `[Name]Provider/src/main/kotlin/com/[provider]/[Name]Provider.kt`:**
   - Kế thừa `MainAPI()`.
   - Có hàm `getDomain()` tự động lấy và cache domain đang hoạt động từ `PORTAL_URL`.
   - Triển khai `getMainPage`, `search`, `load`, `loadLinks`.
   - Trong `loadLinks`, truyền `referer = "$currentBase/"` vào `ExtractorLink` để bảo đảm video stream không bị chặn.

---

### Bước 8: Đồng Bộ Mã Nguồn & Xác Minh Hoàn Tất
1. Chạy task đồng bộ mã nguồn Java từ Gradle:
   ```powershell
   .\gradlew.bat :[Name]Provider:syncJavaFromTest
   ```
2. Kiểm tra `git status` đảm bảo:
   - Các file Java trong `JavaRunTest/` được tạo.
   - Thư mục `[Name]Provider/` chỉ theo dõi các file Kotlin và `build.gradle.kts` (thư mục `src/main/java` tự động được bỏ qua bởi `.gitignore`).
3. Commit và push:
   ```bash
   git add JavaRunTest/src/main/java/com/[provider]/ JavaRunTest/src/test/java/com/[provider]/ [Name]Provider/
   git commit -m "feat([provider]): trien khai [Name]Provider theo chuan kien truc Hybrid Pattern"
   git push origin master
   ```
