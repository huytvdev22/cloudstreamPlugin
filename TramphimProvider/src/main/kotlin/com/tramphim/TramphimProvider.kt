package com.tramphim

import android.util.Base64
import com.cloudstream.core.model.MovieItem
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor

/**
 * TramphimProvider - Provider CloudStream cho nguồn phim Trạm Phim (tramphim.top / tramphim4.org).
 *
 * Triển khai theo mô hình Hybrid Pattern (Kotlin Thin Adapter):
 * 1. Khai báo metadata CloudStream (mainUrl, name, supportedTypes, lang, ...)
 * 2. Tự động kiểm tra và cache tên miền mới nhất từ portal tramphim.top
 * 3. Gọi mạng qua `app.get()` của CloudStream framework
 * 4. Delegate toàn bộ xử lý nghiệp vụ, parsing DOM và giải mã M3U8 AES-GCM cho [TramphimLogic] (Java)
 * 5. Ánh xạ dữ liệu Java Model -> CloudStream UI Responses
 */
class TramphimProvider : MainAPI() {
    override var mainUrl = TramphimLogic.DEFAULT_BASE_URL
    override var name = "Trạm Phim"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    override var lang = "vi"
    override val hasMainPage = true

    companion object {
        const val PORTAL_URL = TramphimLogic.PORTAL_URL
        private var cachedDomain: String? = null
        private var lastDomainCheck: Long = 0
        private const val DOMAIN_CACHE_TTL = 30 * 60 * 1000L // 30 phút
    }

    /**
     * Tự động kiểm tra và lấy tên miền đang hoạt động từ portal tramphim.top.
     */
    private suspend fun getDomain(): String {
        val now = System.currentTimeMillis()
        if (cachedDomain != null && (now - lastDomainCheck) < DOMAIN_CACHE_TTL) {
            return cachedDomain!!
        }

        try {
            val html = app.get(PORTAL_URL, timeout = 6).text
            val domain = TramphimLogic.parseDomain(html)
            if (domain.isNotEmpty() && !domain.equals(PORTAL_URL, ignoreCase = true)) {
                cachedDomain = domain
                lastDomainCheck = now
                mainUrl = domain
                return domain
            }
        } catch (ignored: Exception) {
        }

        return cachedDomain ?: mainUrl
    }

    // =========================================================================
    // 1. CẤU HÌNH MỤC TRANG CHỦ (MAIN PAGE SECTIONS)
    // =========================================================================

    override val mainPage: List<MainPageData> = mainPageOf(
        "/phim-le" to "🎬 Phim Lẻ Mới Cập Nhật",
        "/phim-bo" to "📺 Phim Bộ Mới Nhất",
        "/phim-chieu-rap" to "🍿 Phim Chiếu Rạp Hot",
        "/hoat-hinh" to "🎌 Hoạt Hình & Anime",
        "/phim-4k" to "💎 Phim 4K Siêu Nét",
        "/quoc-gia/han-quoc" to "🌸 Phim Hàn Quốc Tuyển Chọn",
        "/quoc-gia/trung-quoc" to "🎎 Phim Trung Quốc Hot",
        "/the-loai/hanh-dong" to "💥 Phim Hành Động Kịch Tính",
        "/the-loai/tinh-cam" to "💖 Phim Tình Cảm Lãng Mạn",
        "/the-loai/kinh-di" to "👻 Phim Kinh Dị Rùng Rợn",
        "/the-loai/co-trang" to "⚔️ Phim Cổ Trang Đặc Sắc",
        "/the-loai/hai-huoc" to "🤣 Phim Hài Hước Giải Trí"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val domain = getDomain()
        val rawPath = request.data
        val cleanPath = if (rawPath.startsWith("http")) {
            rawPath
        } else {
            val p = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
            "$domain$p"
        }

        // Xây dựng URL phân trang chuẩn
        val url = if (cleanPath.contains("page=")) {
            cleanPath.replace(Regex("([?&])page=[0-9]*"), "$1page=$page")
        } else {
            val connector = if (cleanPath.contains("?")) "&" else "?"
            "$cleanPath${connector}page=$page"
        }

        val html = app.get(url).text

        // Delegate logic parse sang TramphimLogic
        val items = TramphimLogic.parseMovieList(html, domain).mapNotNull { item ->
            toSearchResponse(item)
        }

        return newHomePageResponse(request, items, hasNext = items.size >= 12)
    }

    // =========================================================================
    // 2. TÌM KIẾM PHIM (SEARCH)
    // =========================================================================

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val domain = getDomain()
        val searchUrl = TramphimLogic.buildSearchUrl(domain, query, page)
        val html = app.get(searchUrl).text

        val items = TramphimLogic.parseMovieList(html, domain).mapNotNull { item ->
            toSearchResponse(item)
        }

        return newSearchResponseList(items, hasNext = items.size >= 12)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return search(query, 1).items
    }

    private fun toSearchResponse(item: MovieItem): SearchResponse {
        val hasDub = item.tags.any { it.contains("Thuyết minh", ignoreCase = true) || it.contains("Lồng tiếng", ignoreCase = true) }
        val hasSub = item.tags.any { it.contains("Vietsub", ignoreCase = true) }

        val badgeText = if (item.tags.isNotEmpty()) {
            item.tags.joinToString(" • ")
        } else null

        return newAnimeSearchResponse(item.title, item.href, TvType.Movie) {
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

    // =========================================================================
    // 3. CHI TIẾT PHIM & TẬP PHIM (LOAD)
    // =========================================================================

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

        // Delegate logic parse cơ bản sang TramphimLogic
        val detail = TramphimLogic.parseMovieDetail(html, domain)

        // Trích xuất slug của bộ phim
        val slug = targetUrl.trimEnd('/').substringAfterLast("/").substringBefore("?")

        // Gọi API máy chủ dự phòng (/api/backup-servers) để lấy multi-servers (KKPhim, StreamC, VSmov, ViCDN)
        val allEpisodes = mutableListOf<com.cloudstream.core.model.EpisodeItem>()

        // 1. Lấy danh sách tập từ API backup-servers (hỗ trợ direct M3U8 từ KKPhim & StreamC)
        try {
            val backupUrl = TramphimLogic.buildBackupServersUrl(domain, slug, detail.title)
            val backupJson = app.get(backupUrl, referer = targetUrl, timeout = 6).text
            val backupEps = TramphimLogic.parseBackupServers(backupJson, domain)
            if (backupEps.isNotEmpty()) {
                allEpisodes.addAll(backupEps)
            }
        } catch (ignored: Exception) {
        }

        // 2. Bổ sung các tập từ HTML detail nếu chưa có trong danh sách
        if (detail.episodes.isNotEmpty()) {
            val existingUrls = allEpisodes.map { it.href }.toSet()
            for (ep in detail.episodes) {
                if (!existingUrls.contains(ep.href) && !ep.href.equals(domain, ignoreCase = true)) {
                    allEpisodes.add(ep)
                }
            }
        }

        val episodesList = allEpisodes.map { ep ->
            newEpisode(ep.href) {
                this.name = ep.name
                this.episode = ep.episodeNum
            }
        }

        val isSeries = episodesList.size > 1
        return if (isSeries) {
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
                episodesList.firstOrNull()?.data ?: targetUrl
            ) {
                this.posterUrl = detail.posterUrl
                this.plot = detail.plot
                this.year = detail.year
                this.duration = detail.duration
                this.tags = detail.tags
            }
        }
    }

    // =========================================================================
    // 4. TRÍCH XUẤT LINK VIDEO STREAMING (LOAD LINKS)
    // =========================================================================

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val domain = getDomain()

        // 1. Trường hợp data là direct M3U8 URL (từ KKPhim hoặc nguồn khác)
        if (data.contains(".m3u8")) {
            val cleanM3u8 = if (data.contains("url=")) {
                java.net.URLDecoder.decode(data.substringAfter("url=").substringBefore("&"), "UTF-8")
            } else data

            callback.invoke(
                ExtractorLink(
                    source = "KKPhim",
                    name = "$name (KKPhim VIP)",
                    url = cleanM3u8,
                    referer = "$domain/",
                    quality = Qualities.P1080.value,
                    type = ExtractorLinkType.M3U8
                )
            )
            return true
        }

        // 2. Trường hợp data là StreamC Embed URL (embed*.streamc.xyz)
        if (data.contains("streamc.xyz")) {
            return processStreamcEmbed(data, domain, subtitleCallback, callback)
        }

        // 3. Trường hợp player.phimapi.com có nhúng query url=.m3u8
        if (data.contains("player.phimapi.com") && data.contains("url=")) {
            try {
                val m3u8Url = java.net.URLDecoder.decode(data.substringAfter("url=").substringBefore("&"), "UTF-8")
                if (m3u8Url.contains(".m3u8")) {
                    callback.invoke(
                        ExtractorLink(
                            source = "KKPhim",
                            name = "$name VIP 1080p",
                            url = m3u8Url,
                            referer = "$domain/",
                            quality = Qualities.P1080.value,
                            type = ExtractorLinkType.M3U8
                        )
                    )
                    return true
                }
            } catch (ignored: Exception) {
            }
        }

        // 4. Trường hợp VSmov hoặc ViCDN hoặc embed extractor khác
        if (data.contains("streamvsmov.com") || data.contains("vicdn.cc")) {
            return loadExtractor(data, "$domain/", subtitleCallback, callback)
        }

        var foundAny = false

        // 5. Trường hợp data là URL trang xem hoặc slug phim (ví dụ /phim/..., /xem/...)
        val cleanSlug = data.trimEnd('/').substringAfterLast("/").substringBefore("?")
        if (cleanSlug.isNotEmpty() && !cleanSlug.startsWith("http")) {
            try {
                val backupUrl = TramphimLogic.buildBackupServersUrl(domain, cleanSlug, cleanSlug)
                val backupJson = app.get(backupUrl, referer = "$domain/", timeout = 6).text
                val backupEps = TramphimLogic.parseBackupServers(backupJson, domain)
                for (ep in backupEps) {
                    if (ep.href.contains(".m3u8")) {
                        callback.invoke(
                            ExtractorLink(
                                source = ep.name,
                                name = ep.name,
                                url = ep.href,
                                referer = "$domain/",
                                quality = Qualities.P1080.value,
                                type = ExtractorLinkType.M3U8
                            )
                        )
                        foundAny = true
                    } else if (ep.href.contains("streamc.xyz")) {
                        if (processStreamcEmbed(ep.href, domain, subtitleCallback, callback)) {
                            foundAny = true
                        }
                    } else if (loadExtractor(ep.href, "$domain/", subtitleCallback, callback)) {
                        foundAny = true
                    }
                }
            } catch (ignored: Exception) {
            }
        }

        // 6. Trường hợp cào thêm link từ HTML của trang xem
        try {
            val targetMovieUrl = if (data.startsWith("http")) data else "$domain$data"
            val html = app.get(targetMovieUrl).text
            val videoLinks = TramphimLogic.extractVideoLinks(html, data)

            for (link in videoLinks) {
                if (link.url.contains("streamc.xyz")) {
                    if (processStreamcEmbed(link.url, domain, subtitleCallback, callback)) {
                        foundAny = true
                    }
                } else if (link.type == TramphimLogic.VideoLink.TYPE_M3U8) {
                    callback.invoke(
                        ExtractorLink(
                            source = link.serverName ?: name,
                            name = link.label ?: "$name M3U8",
                            url = link.url,
                            referer = "$domain/",
                            quality = Qualities.P1080.value,
                            type = ExtractorLinkType.M3U8
                        )
                    )
                    foundAny = true
                } else if (link.type == TramphimLogic.VideoLink.TYPE_EMBED) {
                    if (loadExtractor(link.url, domain, subtitleCallback, callback)) {
                        foundAny = true
                    }
                }
            }
        } catch (ignored: Exception) {
        }

        return foundAny
    }

    /**
     * Xử lý giải mã và tạo luồng stream từ player nhúng StreamC (StreamC Embed).
     */
    private suspend fun processStreamcEmbed(
        embedUrl: String,
        domain: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            // GET trang embed kèm header Referer
            val embedHtml = app.get(embedUrl, referer = "$domain/").text
            val links = TramphimLogic.extractVideoLinks(embedHtml, embedUrl)

            val embedOrigin = if (embedUrl.startsWith("http")) {
                val uri = java.net.URI(embedUrl)
                "${uri.scheme}://${uri.host}"
            } else "https://embed14.streamc.xyz"

            val embedReferer = "$embedOrigin/"
            var found = false

            for (link in links) {
                // Đảm bảo URL stream M3U8 không chứa ?d=1 để server trả về M3U8 nguyên bản không mã hóa
                val rawUrl = link.url.substringBefore("#")
                val streamUrl = if (rawUrl.contains("?d=")) {
                    rawUrl.substringBefore("?d=")
                } else rawUrl

                // Cung cấp luồng HLS trực tiếp kèm Referer trang embed
                callback.invoke(
                    ExtractorLink(
                        source = "StreamC",
                        name = "StreamC Server VIP (HLS 1080p)",
                        url = streamUrl,
                        referer = embedReferer,
                        quality = Qualities.P1080.value,
                        type = ExtractorLinkType.M3U8,
                        headers = mapOf(
                            "Referer" to embedReferer,
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                        )
                    )
                )
                found = true
            }
            return found
        } catch (e: Exception) {
            return false
        }
    }
}
