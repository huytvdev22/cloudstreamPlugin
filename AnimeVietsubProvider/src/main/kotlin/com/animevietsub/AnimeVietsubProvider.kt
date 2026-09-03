package com.animevietsub

import com.cloudstream.core.model.MovieItem
import com.cloudstream.core.model.VideoLink
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.EnumSet

/**
 * AnimeVietsubProvider - Provider CloudStream cho kênh AnimeVietsub.
 *
 * Triển khai theo mô hình Hybrid Architecture:
 * 1. Khai báo metadata CloudStream (mainUrl, name, supportedTypes, ...)
 * 2. Tự động khởi tạo và duy trì Cookie Session chống 403 trên Dispatchers.IO
 * 3. Gửi HTTP request lấy HTML
 * 4. Delegate toàn bộ logic parse sang [AnimeVietsubLogic] (Java)
 * 5. Chuyển kết quả từ Java bean sang kiểu dữ liệu CloudStream
 */
class AnimeVietsubProvider : MainAPI() {
    override var mainUrl = AnimeVietsubLogic.DEFAULT_BASE_URL
    override var name = "AnimeVietsub"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA, TvType.Movie, TvType.TvSeries)
    override var lang = "vi"
    override val hasMainPage = true

    companion object {
        const val DEFAULT_DOMAIN = AnimeVietsubLogic.DEFAULT_BASE_URL
        private var cachedDomain: String? = null
        private val cookieMap = java.util.concurrent.ConcurrentHashMap<String, String>()
    }

    /**
     * Lấy domain đang hoạt động.
     */
    private fun getDomain(): String {
        return cachedDomain ?: DEFAULT_DOMAIN
    }

    private fun extractCookies(headers: okhttp3.Headers?) {
        if (headers == null) return
        for (i in 0 until headers.size) {
            if (headers.name(i).equals("set-cookie", ignoreCase = true)) {
                val sc = headers.value(i)
                val parts = sc.split(";")
                if (parts.isNotEmpty()) {
                    val kv = parts[0].split("=", limit = 2)
                    if (kv.size == 2) {
                        val k = kv[0].trim()
                        val v = kv[1].trim()
                        if (k.isNotEmpty() && v.isNotEmpty()) {
                            cookieMap[k] = v
                        }
                    }
                }
            }
        }
    }

    private fun getCookieHeader(): String {
        return cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /**
     * Gửi request HTTP an toàn với cơ chế bắt tay khởi tạo Cookie bằng OkHttp gốc chống 403.
     */
    private suspend fun fetchHtml(url: String, domain: String): String {
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        val accept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"

        // 1. Bắt tay lấy cookie nếu cookieMap đang rỗng
        if (cookieMap.isEmpty()) {
            try {
                val initReq = okhttp3.Request.Builder()
                    .url("$domain/")
                    .header("User-Agent", userAgent)
                    .header("Accept", accept)
                    .build()
                val initResp = app.baseClient.newCall(initReq).execute()
                extractCookies(initResp.headers)
                initResp.close()
            } catch (_: Exception) {
            }
        }

        // 2. Gửi request lấy HTML bằng OkHttp với đầy đủ Cookie
        return try {
            val reqBuilder = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", accept)
                .header("Accept-Language", "vi,en-US;q=0.9,en;q=0.8")
                .header("Referer", "$domain/")

            val cHeader = getCookieHeader()
            if (cHeader.isNotEmpty()) {
                reqBuilder.header("Cookie", cHeader)
            }

            val resp = app.baseClient.newCall(reqBuilder.build()).execute()
            extractCookies(resp.headers)

            // Nếu gặp 403 (cookie được cấp mới tại response này), tự động retry lần 2 với cookie mới
            if (resp.code == 403) {
                resp.close()
                val retryCookie = getCookieHeader()
                val retryReq = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Accept", accept)
                    .header("Referer", "$domain/")
                    .apply {
                        if (retryCookie.isNotEmpty()) header("Cookie", retryCookie)
                    }
                    .build()
                val retryResp = app.baseClient.newCall(retryReq).execute()
                extractCookies(retryResp.headers)
                val body = retryResp.body?.string() ?: ""
                retryResp.close()
                body
            } else {
                val body = resp.body?.string() ?: ""
                resp.close()
                body
            }
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Gửi request POST tới AJAX endpoint kèm Cookie session để chống Cloudflare 403.
     */
    private suspend fun postAjaxWithCookie(
        url: String,
        params: Map<String, String>,
        referer: String
    ): String {
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        val formBodyBuilder = okhttp3.FormBody.Builder()
        for ((k, v) in params) {
            formBodyBuilder.add(k, v)
        }
        val formBody = formBodyBuilder.build()

        val reqBuilder = okhttp3.Request.Builder()
            .url(url)
            .post(formBody)
            .header("User-Agent", userAgent)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", referer)

        val cHeader = getCookieHeader()
        if (cHeader.isNotEmpty()) {
            reqBuilder.header("Cookie", cHeader)
        }

        return try {
            val resp = app.baseClient.newCall(reqBuilder.build()).execute()
            extractCookies(resp.headers)

            if (resp.code == 403) {
                resp.close()
                val retryCookie = getCookieHeader()
                val retryReq = okhttp3.Request.Builder()
                    .url(url)
                    .post(formBody)
                    .header("User-Agent", userAgent)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", referer)
                    .apply {
                        if (retryCookie.isNotEmpty()) header("Cookie", retryCookie)
                    }
                    .build()
                val retryResp = app.baseClient.newCall(retryReq).execute()
                extractCookies(retryResp.headers)
                val body = retryResp.body?.string() ?: ""
                retryResp.close()
                body
            } else {
                val body = resp.body?.string() ?: ""
                resp.close()
                body
            }
        } catch (_: Exception) {
            ""
        }
    }

    // ==========================================
    // 1. CẤU HÌNH MỤC TRANG CHỦ
    // ==========================================

    override val mainPage: List<MainPageData> = mainPageOf(
        "" to "🔥 Anime Mới Cập Nhật",
        "/danh-sach/list-dang-chieu/" to "📺 Anime Đang Chiếu",
        "/anime-bo/" to "⛩️ Anime Bộ (TV Series)",
        "/anime-le/" to "🎬 Anime Lẻ (Movie/OVA)",
        "/danh-sach/list-tron-bo/" to "📦 Anime Trọn Bộ",
        "/hoat-hinh-trung-quoc/" to "🐉 Hoạt Hình Trung Quốc",
        "/bang-xep-hang/day.html" to "⭐ Top Anime Hôm Nay",
        "/bang-xep-hang/season.html" to "🌸 Top Anime Mùa Này",
        "/the-loai/hanh-dong/" to "💥 Hành Động & Phiêu Lưu",
        "/the-loai/tinh-cam/" to "💖 Tình Cảm & Lãng Mạn",
        "/the-loai/hai-huoc/" to "🤣 Hài Hước & Đời Thường",
        "/the-loai/phep-thuat/" to "✨ Phép Thuật & Fantasy"
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

        // Xây dựng URL phân trang theo cấu trúc AnimeVietsub (/trang-2.html)
        val url = if (page > 1) {
            if (cleanPath.endsWith("/")) {
                "${cleanPath}trang-$page.html"
            } else if (cleanPath.endsWith(".html")) {
                cleanPath.replace(".html", "/trang-$page.html")
            } else {
                "$cleanPath/trang-$page.html"
            }
        } else {
            cleanPath
        }

        val html = fetchHtml(url, domain)
        val items = AnimeVietsubLogic.parseMovieList(html, domain).mapNotNull { item ->
            toSearchResponse(item)
        }

        return newHomePageResponse(request, items, hasNext = items.isNotEmpty())
    }

    // ==========================================
    // 2. TÌM KIẾM ANIME (SEARCH)
    // ==========================================

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val domain = getDomain()
        val searchUrl = AnimeVietsubLogic.buildSearchUrl(domain, query, page)
        val html = fetchHtml(searchUrl, domain)

        val items = AnimeVietsubLogic.parseMovieList(html, domain).mapNotNull { item ->
            toSearchResponse(item)
        }

        return newSearchResponseList(items, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return search(query, 1).items
    }

    private fun toSearchResponse(item: MovieItem): SearchResponse? {
        if (item.title.isNullOrBlank() || item.href.isNullOrBlank() || item.posterUrl.isNullOrBlank()) {
            return null
        }

        val hasDub = item.tags.any { it.contains("Lồng", ignoreCase = true) || it.contains("Thuyết", ignoreCase = true) }
        val hasSub = item.tags.any { it.contains("Sub", ignoreCase = true) || it.contains("Viet", ignoreCase = true) }

        val badgeText = if (item.tags.isNotEmpty()) item.tags.joinToString(" • ") else null

        return newAnimeSearchResponse(item.title, item.href, TvType.Anime) {
            this.posterUrl = item.posterUrl
            if (badgeText != null) {
                this.otherName = badgeText
            }
            if (hasDub && hasSub) {
                this.dubStatus = EnumSet.of(DubStatus.Dubbed, DubStatus.Subbed)
            } else if (hasDub) {
                this.dubStatus = EnumSet.of(DubStatus.Dubbed)
            } else if (hasSub) {
                this.dubStatus = EnumSet.of(DubStatus.Subbed)
            }
        }
    }

    // ==========================================
    // 3. CHI TIẾT ANIME & DANH SÁCH TẬP
    // ==========================================

    override suspend fun load(url: String): LoadResponse {
        val domain = getDomain()
        val targetUrl = AnimeVietsubLogic.normalizeUrl(url, domain)
        val html = fetchHtml(targetUrl, domain)

        val detail = AnimeVietsubLogic.parseMovieDetail(html, domain)

        // Tự động kiểm tra và lấy danh sách tập đầy đủ từ trang xem-phim.html nếu số tập bị thiếu (<= 3)
        var fullEpisodes = detail.episodes
        if (fullEpisodes.size <= 3 || !targetUrl.contains("xem-phim.html")) {
            val watchUrl = if (targetUrl.endsWith("/xem-phim.html")) {
                targetUrl
            } else {
                "${targetUrl.trimEnd('/')}/xem-phim.html"
            }
            try {
                val watchHtml = fetchHtml(watchUrl, domain)
                val episodesFromWatch = AnimeVietsubLogic.parseEpisodes(watchHtml, domain)
                if (episodesFromWatch.isNotEmpty() && episodesFromWatch.size > fullEpisodes.size) {
                    fullEpisodes = episodesFromWatch
                }
            } catch (_: Exception) {
            }
        }

        val episodesList = fullEpisodes.map { ep ->
            newEpisode(ep.href) {
                this.name = ep.name
                this.episode = ep.episodeNum
            }
        }

        return if (episodesList.size > 1) {
            newTvSeriesLoadResponse(detail.title, url, TvType.Anime, episodesList) {
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
                TvType.AnimeMovie,
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
    // 4. TRÍCH XUẤT LINK PHÁT VIDEO (.m3u8 / embed)
    // ==========================================

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val domain = getDomain()
        val watchUrl = AnimeVietsubLogic.normalizeUrl(data, domain)
        val html = fetchHtml(watchUrl, domain)

        var hasValidLink = false

        // 1. Trích xuất server chính từ window.PLAYER_DATA
        var episodeIdFromData: String? = null
        val pDataJson = Regex("""window\.PLAYER_DATA\s*=\s*(\{[^;]+?\});""").find(html)?.groupValues?.get(1)
        if (pDataJson != null) {
            try {
                val pObj = JSONObject(pDataJson)
                val playerUrl = pObj.optString("link", "")
                episodeIdFromData = pObj.optString("episode_id", "").ifEmpty { null }

                if (playerUrl.isNotEmpty()) {
                    if (playerUrl.contains(".m3u8")) {
                        callback.invoke(
                            ExtractorLink(
                                source = name,
                                name = "AnimeVietsub Direct (M3U8)",
                                url = playerUrl,
                                referer = domain,
                                quality = Qualities.P1080.value,
                                type = ExtractorLinkType.M3U8
                            )
                        )
                        hasValidLink = true
                    } else if (playerUrl.contains("storage.googleapiscdn.com")) {
                        // Trích xuất playlist m3u8 từ player embed của AnimeVietsub
                        try {
                            val embedHtml = fetchHtml(playerUrl, domain)
                            val idMatch = Regex("""const\s+id\s*=\s*"([^"]+)";""").find(embedHtml)
                            val tokenMatch = Regex("""const\s+avsToken\s*=\s*"([^"]+)";""").find(embedHtml)
                            if (idMatch != null && tokenMatch != null) {
                                val streamId = idMatch.groupValues[1]
                                val streamToken = tokenMatch.groupValues[1]
                                val m3u8Url = "https://storage.googleapiscdn.com/playlist/$streamId/playlist.m3u8?token=$streamToken"
                                callback.invoke(
                                    ExtractorLink(
                                        source = "Server DU (Chính)",
                                        name = "DU Fast (1080p)",
                                        url = m3u8Url,
                                        referer = "https://storage.googleapiscdn.com/",
                                        quality = Qualities.P1080.value,
                                        type = ExtractorLinkType.M3U8
                                    )
                                )
                                hasValidLink = true
                            }
                        } catch (_: Exception) {
                        }
                    } else {
                        val loaded = loadExtractor(playerUrl, domain, subtitleCallback, callback)
                        if (loaded) hasValidLink = true
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 2. Trích xuất server dự phòng (HDX / Abyss / FB / VIP...) qua AJAX API CÓ KÈM COOKIE SESSION
        try {
            val doc = Jsoup.parse(html)
            val episodeId = episodeIdFromData
                ?: doc.selectFirst("input#error-episode-id")?.attr("value")
                ?: Regex("""filmInfo\.episodeID\s*=\s*parseInt\(['"](\d+)['"]\)""").find(html)?.groupValues?.get(1)
                ?: Regex("""data-id="(\d+)"""").find(html)?.groupValues?.get(1)

            if (!episodeId.isNullOrBlank()) {
                val ajaxUrl = "$domain/ajax/player"
                val backupRes = postAjaxWithCookie(
                    ajaxUrl,
                    mapOf("episodeId" to episodeId, "backup" to "1"),
                    watchUrl
                )

                if (backupRes.isNotEmpty()) {
                    val backupJson = JSONObject(backupRes)
                    if (backupJson.optInt("success", 0) == 1) {
                        val backupHtml = backupJson.optString("html", "")
                        val backupDoc = Jsoup.parse(backupHtml)
                        val serverButtons = backupDoc.select("a[data-href]")

                        for (btn in serverButtons) {
                            val dataHref = btn.attr("data-href")
                            val dataPlay = btn.attr("data-play")
                            val dataId = btn.attr("data-id")
                            val serverTitle = btn.text().trim().ifEmpty { "Server $dataId" }

                            if (dataHref.isNotEmpty() && dataId != "0") {
                                try {
                                    val sRes = postAjaxWithCookie(
                                        ajaxUrl,
                                        mapOf(
                                            "link" to dataHref,
                                            "play" to dataPlay,
                                            "id" to dataId,
                                            "backuplinks" to "1"
                                        ),
                                        watchUrl
                                    )

                                    if (sRes.isNotEmpty()) {
                                        val sJson = JSONObject(sRes)
                                        if (sJson.optInt("success", 0) == 1) {
                                            val streamUrl = sJson.optString("link", "")
                                            if (streamUrl.isNotEmpty() && streamUrl != "null") {
                                                if (streamUrl.contains(".m3u8")) {
                                                    callback.invoke(
                                                        ExtractorLink(
                                                            source = name,
                                                            name = "$serverTitle (M3U8)",
                                                            url = streamUrl,
                                                            referer = domain,
                                                            quality = Qualities.P1080.value,
                                                            type = ExtractorLinkType.M3U8
                                                        )
                                                    )
                                                    hasValidLink = true
                                                } else {
                                                    val loaded = loadExtractor(streamUrl, domain, subtitleCallback, callback)
                                                    if (loaded) hasValidLink = true
                                                }
                                            }
                                        }
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        return hasValidLink
    }
}
