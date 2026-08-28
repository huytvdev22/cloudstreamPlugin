package com.vieflix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor

/**
 * VieflixProvider - Provider CloudStream cho trang Vieflix.
 *
 * Class nay chi chiu trach nhiem:
 *   1. Khai bao metadata CloudStream (mainUrl, name, supportedTypes, ...)
 *   2. Goi HTTP de lay HTML
 *   3. Delegate toan bo logic parse sang [VieflixLogic] (Java)
 *   4. Chuyen ket qua tu Java bean -> kieu du lieu CloudStream
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

    /**
     * Lay danh sach phim cho trang chu theo tung muc.
     * HTML duoc fetch roi delegate sang VieflixLogic.parseMovieList().
     */
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}$page"
        val html = app.get(url).text

        // Delegate: giao toan bo logic parse cho VieflixLogic (Java)
        val items = VieflixLogic.parseMovieList(html, mainUrl).mapNotNull { item ->
            newMovieSearchResponse(item.title, item.href, TvType.Movie) {
                this.posterUrl = item.posterUrl
            }
        }

        return newHomePageResponse(request.name, items)
    }

    // ==========================================
    // 2. TIM KIEM
    // ==========================================

    /**
     * Tim kiem phim theo tu khoa.
     * Delegate parse HTML sang VieflixLogic.parseMovieList().
     */
    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/duyet-tim?search=$query"
        val html = app.get(searchUrl).text

        return VieflixLogic.parseMovieList(html, mainUrl).mapNotNull { item ->
            newMovieSearchResponse(item.title, item.href, TvType.Movie) {
                this.posterUrl = item.posterUrl
            }
        }
    }

    // ==========================================
    // 3. CHI TIET PHIM & DANH SACH TAP
    // ==========================================

    /**
     * Lay chi tiet phim va danh sach tap.
     * Delegate parse sang VieflixLogic.parseMovieDetail().
     */
    override suspend fun load(url: String): LoadResponse {
        val html = app.get(url).text

        // Delegate: giao toan bo logic parse cho VieflixLogic (Java)
        val detail = VieflixLogic.parseMovieDetail(html, mainUrl)

        // Chuyen EpisodeItem (Java) -> Episode (CloudStream)
        val episodesList = detail.episodes.map { ep ->
            newEpisode(ep.href) {
                this.name = ep.name
                this.episode = ep.episodeNum
            }
        }

        return if (episodesList.size > 1) {
            newTvSeriesLoadResponse(detail.title, url, TvType.TvSeries, episodesList) {
                this.posterUrl = detail.posterUrl
                this.plot = detail.plot
                this.year = detail.year
                this.duration = detail.duration
                this.tags = detail.tags
            }
        } else {
            newMovieLoadResponse(
                detail.title,
                url,
                TvType.Movie,
                episodesList.firstOrNull()?.data ?: url
            ) {
                this.posterUrl = detail.posterUrl
                this.plot = detail.plot
                this.year = detail.year
                this.duration = detail.duration
                this.tags = detail.tags
            }
        }
    }

    // ==========================================
    // 4. TRICH XUAT LINK PHAT VIDEO (.m3u8 / .mp4)
    // ==========================================

    /**
     * Trich xuat link video de phat.
     *
     * Lay HTML trang phim GOC (khong phai trang tap) vi trang goc
     * chua toan bo JSON data cho tat ca tap, dam bao luon tim duoc link.
     *
     * Delegate logic trich xuat sang VieflixLogic.extractVideoLinks().
     */
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Lay slug cua tap (tap-full, tap-1, tap-2, ...)
        val slug = data.substringAfterLast("/").substringBefore("?")

        // Luon GET trang phim GOC (khong phai trang tap).
        // Vi du: data = "https://vieflix.top/phim/ten-cau-la-gi/tap-full?sv=0&lang=0"
        //    -> movieUrl = "https://vieflix.top/phim/ten-cau-la-gi"
        val movieUrl = if (data.contains("/tap-")) {
            data.substringBefore("/tap-")
        } else {
            data.substringBefore("?")
        }

        val html = app.get(movieUrl).text

        // Delegate: giao toan bo logic trich xuat link cho VieflixLogic (Java)
        val videoLinks = VieflixLogic.extractVideoLinks(html, slug)

        // Chuyen VideoLink (Java) -> ExtractorLink (CloudStream) hoac goi loadExtractor
        for (link in videoLinks) {
            when (link.type) {
                VieflixLogic.VideoLink.TYPE_M3U8 -> {
                    callback.invoke(
                        ExtractorLink(
                            source = name,
                            name = link.label,
                            url = link.url,
                            referer = mainUrl,
                            quality = Qualities.P1080.value,
                            type = ExtractorLinkType.M3U8
                        )
                    )
                }
                VieflixLogic.VideoLink.TYPE_EMBED -> {
                    // Giao cho CloudStream tu xu ly embed (Doodstream, Streamtape, ...)
                    loadExtractor(link.url, mainUrl, subtitleCallback, callback)
                }
            }
        }

        return true
    }
}
