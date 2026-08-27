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
        val searchUrl = "$mainUrl/search?keyword=$query"
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
        val title = document.selectFirst("h1.title, .film-info h1")?.text() ?: "Không có tên"
        val poster = document.selectFirst(".poster img, .film-poster img")?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        }
        val plot = document.selectFirst(".description, .synopsis, .film-content")?.text()

        val episodeElements = document.select(".episodes a, ul.list-episode a")
        val episodesList = episodeElements.mapIndexed { index, ep ->
            val epHref = fixUrl(ep.attr("href"))
            val epName = ep.text().ifEmpty { "Tập ${index + 1}" }
            newEpisode(epHref) {
                this.name = epName
                this.episode = index + 1
            }
        }

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
        val document = app.get(data).document
        val playerIframe = document.selectFirst("iframe#player, .player-container iframe")?.attr("src")

        // Nếu tìm thấy player iframe hoặc stream link
        playerIframe?.let { iframeUrl ->
            // Ví dụ trích xuất luồng trực tiếp nếu có:
            if (iframeUrl.contains(".m3u8")) {
                callback.invoke(
                    ExtractorLink(
                        source = name,
                        name = "Vieflix Stream",
                        url = iframeUrl,
                        referer = mainUrl,
                        quality = Qualities.P1080.value,
                        type = ExtractorLinkType.M3U8
                    )
                )
            }
        }

        return true
    }
}
