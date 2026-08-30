package com.vieflix

import com.cloudstream.core.model.MovieItem
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

/**
 * VieflixProvider - Provider CloudStream cho trang Vieflix.
 *
 * Class nay chi chiu trach nhiem:
 *   1. Khai bao metadata CloudStream (mainUrl, name, supportedTypes, ...)
 *   2. Tu dong kiem tra va cap nhat domain moi nhat tu vieflix.com
 *   3. Goi HTTP de lay HTML
 *   4. Delegate toan bo logic parse sang [VieflixLogic] (Java)
 *   5. Chuyen ket qua tu Java bean -> kieu du lieu CloudStream
 */
class VieflixProvider : MainAPI() {
    override var mainUrl = VieflixLogic.DEFAULT_BASE_URL
    override var name = "Vieflix"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    override var lang = "vi"
    override val hasMainPage = true

    companion object {
        const val PORTAL_URL = VieflixLogic.PORTAL_URL
        const val CONSTAN_JS_URL = "${VieflixLogic.PORTAL_URL}/constan.js"
        private var cachedDomain: String? = null
    }

    /**
     * Tu dong kiem tra va lay domain dang hoat dong tu portal vieflix.com
     */
    private suspend fun getDomain(): String {
        cachedDomain?.let { return it }

        // 1. Uu tien lay tu constan.js (nhanh va chua TARGET_DOMAIN chinh xac nhat)
        try {
            val js = app.get(CONSTAN_JS_URL, timeout = 5).text
            val domain = VieflixLogic.parseDomain(js)
            if (domain.isNotEmpty() && !domain.equals(PORTAL_URL, ignoreCase = true)) {
                cachedDomain = domain
                mainUrl = domain
                return domain
            }
        } catch (e: Exception) {
        }

        // 2. Fallback: Lay tu HTML trang chu portal vieflix.com (doc the a#accessBtn)
        try {
            val html = app.get(PORTAL_URL, timeout = 5).text
            val domain = VieflixLogic.parseDomain(html)
            if (domain.isNotEmpty() && !domain.equals(PORTAL_URL, ignoreCase = true)) {
                cachedDomain = domain
                mainUrl = domain
                return domain
            }
        } catch (e: Exception) {
        }

        return mainUrl
    }

    // ==========================================
    // 1. CẤU HÌNH MỤC TRANG CHỦ
    // ==========================================

    override val mainPage: List<MainPageData> = mainPageOf(
        "/duyet-tim?isChieuRap=true&sortField=year" to "🍿 Phim Chiếu Rạp Mới Nhất",
        "/duyet-tim?sortField=modified" to "🔥 Phim Mới Cập Nhật",
        "/duyet-tim?lang=thuyet-minh&sortField=year" to "🎙️ Phim Thuyết Minh Mới Nhất",
        "/duyet-tim?lang=long-tieng&sortField=year" to "🗣️ Phim Lồng Tiếng Mới Nhất",
        "/song-ngu" to "🌐 Phim Song Ngữ Hot",
        "/loai-phim/phim-le?sortField=year" to "🎬 Phim Lẻ Mới Nhất",
        "/loai-phim/phim-bo?sortField=year" to "📺 Phim Bộ Mới Nhất",
        "/duyet-tim?category=co-trang&country=trung-quoc&sortField=year" to "🎎 Phim Cổ Trang Trung Quốc",
        "/quoc-gia/han-quoc?sortField=year" to "🌸 Phim Hàn Quốc Mới Nhất",
        "/duyet-tim?category=hanh-dong&country=au-my&sortField=year" to "💥 Phim Hành Động Âu Mỹ",
        "/duyet-tim?category=kinh-di&sortField=year" to "👻 Phim Kinh Dị & Giật Gân",
        "/duyet-tim?category=tinh-cam&sortField=year" to "💖 Phim Tình Cảm & Lãng Mạn",
        "/duyet-tim?category=hai-huoc&sortField=year" to "🤣 Phim Hài Hước Giải Trí",
        "/duyet-tim?typeList=hoat-hinh&country=nhat-ban&sortField=year" to "⛩️ Anime Nhật Bản Hot",
        "/duyet-tim?typeList=hoat-hinh&country=trung-quoc&sortField=year" to "🐉 Hoạt Hình 3D Trung Quốc",
        "/phim-ngan" to "📱 Shorts Drama",
        "/loai-phim/tv-shows?sortField=year" to "🎤 TV Shows & Truyền Hình"
    )

    /**
     * Lay danh sach phim cho trang chu theo tung muc.
     * HTML duoc fetch roi delegate sang VieflixLogic.parseMovieList().
     */
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val domain = getDomain()

        val rawPath = request.data
        val cleanPath = if (rawPath.startsWith("http")) {
            rawPath
        } else {
            val p = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
            "$domain$p"
        }

        // Xây dựng URL phân trang chính xác (?page= hoặc &page=, tự động thay thế nếu đã có page=)
        val url = if (cleanPath.contains("page=")) {
            cleanPath.replace(Regex("([?&])page=[0-9]*"), "$1page=$page")
        } else {
            val connector = if (cleanPath.contains("?")) "&" else "?"
            "$cleanPath${connector}page=$page"
        }

        val html = app.get(url).text

        // Delegate: giao toan bo logic parse cho VieflixLogic (Java)
        val items = VieflixLogic.parseMovieList(html, domain).mapNotNull { item ->
            toSearchResponse(item)
        }

        // Vieflix tra ve 24 phim moi trang -> hasNext = true khi so luong phim dat du 24 phim
        return newHomePageResponse(request, items, hasNext = items.size >= 24)
    }

    // ==========================================
    // 2. TIM KIEM THONG MINH (SMART FILTER SEARCH)
    // ==========================================

    /**
     * Tim kiem phim theo tu khoa hoac bo loc thong minh co ho tro phan trang.
     * Delegate parse query sang VieflixLogic.buildSearchUrl().
     */
    override suspend fun search(query: String, page: Int): SearchResponseList {
        val domain = getDomain()
        val searchUrl = VieflixLogic.buildSearchUrl(domain, query, page)
        val html = app.get(searchUrl).text

        val items = VieflixLogic.parseMovieList(html, domain).mapNotNull { item ->
            toSearchResponse(item)
        }

        return newSearchResponseList(items, hasNext = items.size >= 24)
    }

    /**
     * Tim kiem phim theo tu khoa (fallback trang 1).
     */
    override suspend fun search(query: String): List<SearchResponse> {
        return search(query, 1).items
    }

    /**
     * Chuyen doi MovieItem thanh SearchResponse co gan day du:
     * - Badges ngon ngu: Vietsub (VS), Long Tieng (LT), Thuyet Minh (TM), Song Ngu (SN)
     * - DubStatus: Tu dong bat badge DUB / SUB tren goc poster
     * - otherName: Hien thi danh sach tag ngon ngu duoi ten phim tren giao dien TV/Mobile
     */
    private fun toSearchResponse(item: MovieItem): SearchResponse {
        val hasDub = item.tags.any { it == "LT" || it == "TM" || it == "SN" }
        val hasSub = item.tags.any { it == "VS" || it == "SN" }

        val tagSuffix = if (item.tags.isNotEmpty()) {
            " [" + item.tags.joinToString(" • ") + "]"
        } else ""

        val formattedTitle = "${item.title}$tagSuffix"

        val badgeText = if (item.tags.isNotEmpty()) {
            item.tags.joinToString(" • ") { tag ->
                when (tag) {
                    "LT" -> "🎙️ Lồng Tiếng"
                    "TM" -> "🔊 Thuyết Minh"
                    "VS" -> "🔤 Vietsub"
                    "SN" -> "🌐 Song Ngữ"
                    else -> tag
                }
            }
        } else null

        return newAnimeSearchResponse(formattedTitle, item.href, TvType.Movie) {
            this.posterUrl = item.posterUrl
            if (badgeText != null) {
                this.otherName = badgeText
            }
            if (hasDub && hasSub) {
                this.dubStatus = java.util.EnumSet.of(DubStatus.Dubbed, DubStatus.Subbed)
            } else if (hasDub) {
                this.dubStatus = java.util.EnumSet.of(DubStatus.Dubbed)
            } else if (hasSub) {
                this.dubStatus = java.util.EnumSet.of(DubStatus.Subbed)
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
        val domain = getDomain()
        val targetUrl = if (url.startsWith("http")) {
            val uri = java.net.URI(url)
            val path = (uri.rawPath ?: "") + if (uri.rawQuery != null) "?${uri.rawQuery}" else ""
            "$domain$path"
        } else {
            val path = if (url.startsWith("/")) url else "/$url"
            "$domain$path"
        }

        val html = app.get(targetUrl).text

        // Delegate: giao toan bo logic parse cho VieflixLogic (Java)
        val detail = VieflixLogic.parseMovieDetail(html, domain)

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
        val domain = getDomain()
        // Lay slug cua tap (tap-full, tap-1, tap-2, ...)
        val slug = data.substringAfterLast("/").substringBefore("?")

        // Luon GET trang phim GOC (khong phai trang tap).
        val rawMovieUrl = if (data.contains("/tap-")) {
            data.substringBefore("/tap-")
        } else {
            data.substringBefore("?")
        }

        val movieUrl = if (rawMovieUrl.startsWith("http")) {
            val uri = java.net.URI(rawMovieUrl)
            val path = (uri.rawPath ?: "") + if (uri.rawQuery != null) "?${uri.rawQuery}" else ""
            "$domain$path"
        } else {
            val path = if (rawMovieUrl.startsWith("/")) rawMovieUrl else "/$rawMovieUrl"
            "$domain$path"
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
                            referer = domain,
                            quality = Qualities.P1080.value,
                            type = ExtractorLinkType.M3U8
                        )
                    )
                }
                VieflixLogic.VideoLink.TYPE_EMBED -> {
                    // Giao cho CloudStream tu xu ly embed (Doodstream, Streamtape, ...)
                    loadExtractor(link.url, domain, subtitleCallback, callback)
                }
            }
        }

        return true
    }
}
