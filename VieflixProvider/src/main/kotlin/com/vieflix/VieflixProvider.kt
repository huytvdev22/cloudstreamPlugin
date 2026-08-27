package com.vieflix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup

/**
 * Provider khai thác dữ liệu từ web Vieflix (Mẫu cào HTML DOM bằng Jsoup)
 */
class VieflixProvider : MainAPI() {
    override var mainUrl = "https://vieflix.top"
    override var name = "Vieflix"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    override var lang = "vi"
    override val hasMainPage = true

    // ==========================================
    // 1. CẤU HÌNH MỤC TRANG CHỦ
    // ==========================================
    override val mainPage = mainPageOf(
        "$mainUrl/duyet-tim?sortField=year&page=" to "Phim Mới",
        "$mainUrl/loai-phim/phim-bo?page=" to "Phim Bộ",
        "$mainUrl/loai-phim/phim-le?page=" to "Phim Lẻ",
        "$mainUrl/loai-phim/tv-shows?page=" to "TV Shows",
        "$mainUrl/chu-de/hoat-hinh?page=" to "Hoạt Hình"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}$page"
        val document = app.get(url).document

        val homeItems = document.select("a[href^=/phim/]").mapNotNull { element ->
            val img = element.selectFirst("img")
            val title = img?.attr("alt")?.ifEmpty { element.text() } ?: element.text()
            val href = fixUrl(element.attr("href"))
            val poster = img?.attr("src")

            if (title.isBlank()) return@mapNotNull null

            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }

        return newHomePageResponse(request.name, homeItems)
    }

    // ==========================================
    // 2. TÌM KIẾM
    // ==========================================
    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/duyet-tim?search=$query"
        val document = app.get(searchUrl).document

        return document.select("a[href^=/phim/]").mapNotNull { element ->
            val img = element.selectFirst("img")
            val title = img?.attr("alt")?.ifEmpty { element.text() } ?: element.text()
            val href = fixUrl(element.attr("href"))
            val poster = img?.attr("src")
            
            if (title.isBlank()) return@mapNotNull null

            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }

    // ==========================================
    // 3. CHI TIẾT PHIM & DANH SÁCH TẬP
    // ==========================================
    override suspend fun load(url: String): LoadResponse {
        val html = app.get(url).text
        val document = Jsoup.parse(html)
        
        val title = document.selectFirst("h1")?.text() ?: "Không có tên"
        val poster = document.selectFirst("img[src*=/movies/]")?.attr("src") 
            ?: document.selectFirst("img")?.attr("src")
            
        // Trích xuất metadata từ Regex và HTML DOM
        val plotRegex = Regex("""Giới thiệu:.*?<p[^>]*>(.*?)</p>""")
        val plot = plotRegex.find(html)?.groupValues?.get(1)?.replace(Regex("""<[^>]*>"""), "")?.trim() 
            ?: document.select("p").map { it.text() }.firstOrNull { it.length > 50 }?.substringAfter("Giới thiệu:")?.trim()
            
        val durationRegex = Regex("""Thời lượng:[^0-9]*([0-9]+)""")
        val duration = durationRegex.find(html)?.groupValues?.get(1)?.toIntOrNull()

        val yearRegex = Regex("""\b(19\d{2}|20\d{2})\b""")
        val year = yearRegex.find(html)?.groupValues?.get(1)?.toIntOrNull()
        
        val tags = document.select("a[href*=/the-loai/], a[href*=/quoc-gia/]").map { it.text() }.filter { it.isNotBlank() }

        val episodeElements = document.select("a[href*=/tap-]")
        val episodesList = episodeElements.mapIndexedNotNull { index, ep ->
            val epHref = fixUrl(ep.attr("href"))
            val epNumStr = epHref.substringAfter("/tap-").substringBefore("?").toIntOrNull()
            val epNum = epNumStr ?: (index + 1)
            val epName = ep.text().ifEmpty { "Tập $epNum" }
            
            newEpisode(epHref) {
                this.name = epName
                this.episode = epNum
            }
        }.distinctBy { it.data }

        return if (episodesList.size > 1) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesList) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.duration = duration
                this.tags = tags
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, episodesList.firstOrNull()?.data ?: url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.duration = duration
                this.tags = tags
            }
        }
    }

    // ==========================================
    // 4. TRÍCH XUẤT LINK PHÁT VIDEO (.m3u8 / .mp4)
    // ==========================================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Lấy slug của tập (tap-full, tap-1, tap-2, ...)
        val slug = data.substringAfterLast("/").substringBefore("?")

        // Luôn GET trang phim GỐC (không phải trang tập) vì trang gốc
        // chứa toàn bộ JSON data cho tất cả tập, đảm bảo luôn tìm được link.
        // Ví dụ: data = "https://vieflix.top/phim/ten-cau-la-gi/tap-full?sv=0&lang=0"
        //   -> movieUrl = "https://vieflix.top/phim/ten-cau-la-gi"
        // Nếu data là URL phim gốc (không có /tap-), dùng thẳng.
        val movieUrl = if (data.contains("/tap-")) {
            data.substringBefore("/tap-")
        } else {
            data.substringBefore("?")
        }

        val html = app.get(movieUrl).text

        // Dữ liệu trong HTML (Next.js) được escape dạng JSON-in-string:
        // Ký tự THỰC trong HTML: \"slug\":\"tap-full\"  (backslash=92, quote=34)
        // Kotlin: \\\" = backslash+quote trong JVM string (ĐÚNG)
        //         \"   = chỉ quote trong JVM string (SAI - không khớp HTML)
        val searchSlug = "\\\"slug\\\":\\\"$slug\\\""
        var startIndex = html.indexOf(searchSlug)
        var foundAny = false

        while (startIndex != -1) {
            val endOfBlock = html.indexOf("\\\"slug\\\":\\\"", startIndex + searchSlug.length)
            val block = if (endOfBlock != -1) {
                html.substring(startIndex, endOfBlock)
            } else {
                html.substring(startIndex)
            }

            // Tìm linkM3u8: \"linkM3u8\":\"<url>\"
            val m3u8Key = "\\\"linkM3u8\\\":\\\""
            val m3Idx = block.indexOf(m3u8Key)
            if (m3Idx != -1) {
                val valueStart = m3Idx + m3u8Key.length
                val endM3Idx = block.indexOf("\\\"", valueStart)
                if (endM3Idx != -1) {
                    val m3u8Url = block.substring(valueStart, endM3Idx)
                    if (m3u8Url.isNotBlank() && m3u8Url.contains(".m3u8")) {
                        foundAny = true
                        callback.invoke(
                            ExtractorLink(
                                source = name,
                                name = "Vieflix M3U8",
                                url = m3u8Url,
                                referer = mainUrl,
                                quality = Qualities.P1080.value,
                                type = ExtractorLinkType.M3U8
                            )
                        )
                    }
                }
            }

            // Tìm linkEmbed: \"linkEmbed\":\"<url>\"
            val embedKey = "\\\"linkEmbed\\\":\\\""
            val emIdx = block.indexOf(embedKey)
            if (emIdx != -1) {
                val embedValueStart = emIdx + embedKey.length
                val endEmIdx = block.indexOf("\\\"", embedValueStart)
                if (endEmIdx != -1) {
                    val embedUrl = block.substring(embedValueStart, endEmIdx)
                    if (embedUrl.isNotBlank()) {
                        foundAny = true
                        // Nếu embed chứa tham số url=*.m3u8 thì trích xuất trực tiếp
                        if (embedUrl.contains("url=") && embedUrl.contains(".m3u8")) {
                            val m3u8 = embedUrl.substringAfter("url=").substringBefore("&")
                            callback.invoke(
                                ExtractorLink(
                                    source = name,
                                    name = "Vieflix Embed",
                                    url = m3u8,
                                    referer = mainUrl,
                                    quality = Qualities.P1080.value,
                                    type = ExtractorLinkType.M3U8
                                )
                            )
                        } else {
                            loadExtractor(embedUrl, mainUrl, subtitleCallback, callback)
                        }
                    }
                }
            }

            startIndex = html.indexOf(searchSlug, startIndex + searchSlug.length)
        }

        // Fallback: Nếu không tìm thấy theo slug, lấy linkM3u8 đầu tiên trong toàn bộ HTML
        if (!foundAny) {
            val fallbackKey = "\\\"linkM3u8\\\":\\\""
            val fallbackIdx = html.indexOf(fallbackKey)
            if (fallbackIdx != -1) {
                val fbValueStart = fallbackIdx + fallbackKey.length
                val endFbIdx = html.indexOf("\\\"", fbValueStart)
                if (endFbIdx != -1) {
                    val fbM3u8 = html.substring(fbValueStart, endFbIdx)
                    if (fbM3u8.isNotBlank() && fbM3u8.contains(".m3u8")) {
                        callback.invoke(
                            ExtractorLink(
                                source = name,
                                name = "Vieflix Auto",
                                url = fbM3u8,
                                referer = mainUrl,
                                quality = Qualities.P1080.value,
                                type = ExtractorLinkType.M3U8
                            )
                        )
                    }
                }
            }
        }

        return true
    }
}
