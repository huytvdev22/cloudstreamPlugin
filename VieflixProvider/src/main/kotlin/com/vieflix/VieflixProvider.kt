package com.vieflix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities

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
        val document = app.get(url).document
        
        val title = document.selectFirst("h1")?.text() ?: "Không có tên"
        val poster = document.selectFirst("img[src*=/movies/]")?.attr("src") 
            ?: document.selectFirst("img")?.attr("src")
            
        val plot = document.select("p").map { it.text() }.firstOrNull { it.length > 50 }
            ?.substringAfter("Giới thiệu:")?.trim()

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
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, episodesList.firstOrNull()?.data ?: url) {
                this.posterUrl = poster
                this.plot = plot
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
        val html = app.get(data).text
        val slug = data.substringAfterLast("/").substringBefore("?")
        
        // Cố gắng tìm link m3u8 trực tiếp từ JSON state (thường cho server VIP)
        val m3u8Regex = Regex("""\\"slug\\":\\"$slug\\".*?\\"linkM3u8\\":\\"([^\\"]+)\\"""")
        m3u8Regex.findAll(html).forEach { match ->
            val m3u8Url = match.groupValues[1]
            if (m3u8Url.isNotBlank() && m3u8Url.contains(".m3u8")) {
                callback.invoke(
                    ExtractorLink(
                        source = name,
                        name = "Vieflix VIP",
                        url = m3u8Url,
                        referer = mainUrl,
                        quality = Qualities.P1080.value,
                        type = ExtractorLinkType.M3U8
                    )
                )
            }
        }
        
        // Tìm các link embed (phụ) để dự phòng
        val embedRegex = Regex("""\\"slug\\":\\"$slug\\".*?\\"linkEmbed\\":\\"([^\\"]+)\\"""")
        embedRegex.findAll(html).forEach { match ->
            val embedUrl = match.groupValues[1]
            if (embedUrl.isNotBlank()) {
                loadExtractor(embedUrl, mainUrl, subtitleCallback, callback)
            }
        }

        return true
    }
}
