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
 * 2. Tự động kiểm tra và chuẩn hóa domain đang hoạt động
 * 3. Gửi HTTP request lấy HTML
 * 4. Delegate toàn bộ logic parse sang [AnimeVietsubLogic] (Java)
 * 5. Chuyển kết quả từ Java bean sang kiểu dữ liệu CloudStream
 */
class AnimeVietsubProvider : MainAPI() {
    override var mainUrl = AnimeVietsubLogic.DEFAULT_BASE_URL
    override var name = "AnimeVietsub"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
    override var lang = "vi"
    override val hasMainPage = true

    companion object {
        const val PORTAL_URL = AnimeVietsubLogic.PORTAL_URL
        private var cachedDomain: String? = null
    }

    /**
     * Tự động lấy domain đang hoạt động từ cache hoặc portal.
     */
    private suspend fun getDomain(): String {
        cachedDomain?.let { return it }

        try {
            val res = app.get(PORTAL_URL, timeout = 5)
            val finalUrl = res.url
            if (finalUrl.isNotEmpty() && finalUrl.startsWith("http")) {
                val domain = finalUrl.trimEnd('/')
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
        "/danh-sach/phim-moi-cap-nhat/" to "🔥 Anime Mới Cập Nhật",
        "/danh-sach/list-dang-chieu/" to "📺 Anime Đang Chiếu",
        "/danh-sach/list-tron-bo/" to "🎬 Anime Trọn Bộ",
        "/bang-xep-hang/day.html" to "⭐ Top Anime Hôm Nay",
        "/bang-xep-hang/season.html" to "🌸 Top Anime Mùa Này",
        "/the-loai/hanh-dong/" to "💥 Hành Động & Phiêu Lưu",
        "/the-loai/co-trang/" to "🎎 Cổ Trang & Huyền Huyễn",
        "/the-loai/hai-huoc/" to "🤣 Hài Hước & Đời Thường",
        "/the-loai/dong-tinh-nam/" to "👬 Đam Mỹ & Boys Love"
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

        val html = app.get(url, referer = domain).text
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
        val html = app.get(searchUrl, referer = domain).text

        val items = AnimeVietsubLogic.parseMovieList(html, domain).mapNotNull { item ->
            toSearchResponse(item)
        }

        return newSearchResponseList(items, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return search(query, 1).items
    }

    private fun toSearchResponse(item: MovieItem): SearchResponse {
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
        val html = app.get(targetUrl, referer = domain).text

        val detail = AnimeVietsubLogic.parseMovieDetail(html, domain)

        val episodesList = detail.episodes.map { ep ->
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
        val html = app.get(watchUrl, referer = domain).text

        // 1. Trích xuất link từ script window.PLAYER_DATA hoặc M3U8 trực tiếp
        val videoLinks = AnimeVietsubLogic.extractVideoLinks(html, watchUrl)
        for (link in videoLinks) {
            when (link.type) {
                VideoLink.TYPE_M3U8 -> {
                    callback.invoke(
                        ExtractorLink(
                            source = name,
                            name = link.label ?: "M3U8 Fast",
                            url = link.url,
                            referer = domain,
                            quality = Qualities.P1080.value,
                            type = ExtractorLinkType.M3U8
                        )
                    )
                }
                VideoLink.TYPE_EMBED -> {
                    loadExtractor(link.url, domain, subtitleCallback, callback)
                }
            }
        }

        // 2. Trích xuất server dự phòng (HDX / Abyss Player) qua AJAX API
        try {
            val doc = Jsoup.parse(html)
            val filmId = doc.selectFirst("input#error-film-id")?.attr("value")
                ?: Regex("filmInfo\\.filmID\\s*=\\s*parseInt\\(['\"](\\d+)['\"]\\)").find(html)?.groupValues?.get(1)
            val episodeId = doc.selectFirst("input#error-episode-id")?.attr("value")
                ?: Regex("filmInfo\\.episodeID\\s*=\\s*parseInt\\(['\"](\\d+)['\"]\\)").find(html)?.groupValues?.get(1)

            if (!episodeId.isNullOrBlank()) {
                val ajaxUrl = "$domain/ajax/player"
                val backupRes = app.post(
                    ajaxUrl,
                    headers = mapOf(
                        "X-Requested-With" to "XMLHttpRequest",
                        "Referer" to watchUrl
                    ),
                    data = mapOf(
                        "episodeId" to episodeId,
                        "backup" to "1"
                    )
                ).text

                val backupJson = JSONObject(backupRes)
                if (backupJson.optInt("success", 0) == 1) {
                    val backupHtml = backupJson.optString("html", "")
                    val backupDoc = Jsoup.parse(backupHtml)
                    val serverButtons = backupDoc.select("a[data-href]")

                    for (btn in serverButtons) {
                        val dataHref = btn.attr("data-href")
                        val dataPlay = btn.attr("data-play")
                        val dataId = btn.attr("data-id")
                        val serverTitle = btn.text().trim()

                        if (dataHref.isNotEmpty() && dataId != "0") {
                            try {
                                val sRes = app.post(
                                    ajaxUrl,
                                    headers = mapOf(
                                        "X-Requested-With" to "XMLHttpRequest",
                                        "Referer" to watchUrl
                                    ),
                                    data = mapOf(
                                        "link" to dataHref,
                                        "play" to dataPlay,
                                        "id" to dataId,
                                        "backuplinks" to "1"
                                    )
                                ).text

                                val sJson = JSONObject(sRes)
                                if (sJson.optInt("success", 0) == 1) {
                                    val streamUrl = sJson.optString("link", "")
                                    val playTech = sJson.optString("playTech", "")

                                    if (streamUrl.isNotEmpty()) {
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
                                        } else {
                                            loadExtractor(streamUrl, domain, subtitleCallback, callback)
                                        }
                                    }
                                }
                            } catch (ignored: Exception) {
                            }
                        }
                    }
                }
            }
        } catch (ignored: Exception) {
        }

        return true
    }
}
