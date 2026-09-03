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

        // Delegate logic parse sang TramphimLogic
        val detail = TramphimLogic.parseMovieDetail(html, domain)

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
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "$name VIP",
                    url = data,
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

        // 3. Trường hợp data là URL trang xem phim của Trạm Phim
        val targetMovieUrl = if (data.startsWith("http")) data else "$domain$data"
        val html = app.get(targetMovieUrl).text

        val videoLinks = TramphimLogic.extractVideoLinks(html, data)
        var foundAny = false

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
                loadExtractor(link.url, domain, subtitleCallback, callback)
                foundAny = true
            }
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

            for (link in links) {
                val streamUrl = link.url.substringBefore("#")
                val hashFromUrl = if (link.url.contains("#hash=")) link.url.substringAfter("#hash=") else ""

                // Gửi stream URL với header Referer tới embed page
                val embedOrigin = if (embedUrl.startsWith("http")) {
                    val uri = java.net.URI(embedUrl)
                    "${uri.scheme}://${uri.host}"
                } else "https://embed14.streamc.xyz"

                val embedReferer = "$embedOrigin/"

                // Thử giải mã nội dung M3U8 sang dạng Data URI nếu có hash
                if (hashFromUrl.isNotEmpty()) {
                    try {
                        val encryptedM3u8 = app.get(streamUrl, referer = embedUrl).text
                        val decryptedM3u8 = TramphimLogic.decryptStreamcM3u8(encryptedM3u8, hashFromUrl)

                        if (decryptedM3u8.contains("#EXTM3U")) {
                            // Tạo Data URI cho M3U8 đã giải mã
                            val base64M3u8 = Base64.encodeToString(
                                decryptedM3u8.toByteArray(Charsets.UTF_8),
                                Base64.NO_WRAP
                            )
                            val dataUri = "data:application/vnd.apple.mpegurl;base64,$base64M3u8"

                            callback.invoke(
                                ExtractorLink(
                                    source = "StreamC (Decrypted)",
                                    name = "StreamC VIP 1080p (M3U8 Đã Giải Mã)",
                                    url = dataUri,
                                    referer = embedReferer,
                                    quality = Qualities.P1080.value,
                                    type = ExtractorLinkType.M3U8
                                )
                            )
                        }
                    } catch (ignored: Exception) {
                    }
                }

                // Luôn cung cấp luồng HLS trực tiếp kèm Referer
                callback.invoke(
                    ExtractorLink(
                        source = "StreamC HLS",
                        name = "StreamC Server VIP (HLS)",
                        url = streamUrl,
                        referer = embedReferer,
                        quality = Qualities.P1080.value,
                        type = ExtractorLinkType.M3U8
                    )
                )
            }
            return links.isNotEmpty()
        } catch (e: Exception) {
            return false
        }
    }
}
