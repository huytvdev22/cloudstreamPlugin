package com.motchill

import com.cloudstream.core.model.MovieItem
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor

/**
 * MotchillProvider - CloudStream Provider cho trang web motchillw.sh.
 *
 * Áp dụng mô hình kiến trúc Hybrid Pattern:
 * - Provider Kotlin chỉ đóng vai trò Adapter mỏng kết nối với CloudStream framework (Metadata, Network, Mapping).
 * - Toàn bộ logic bóc tách Jsoup DOM, Schema.org JSON-LD, Next.js RSC và phân giải link video được ủy nhiệm cho [MotchillLogic] (Java Core).
 */
class MotchillProvider : MainAPI() {
    override var mainUrl = MotchillLogic.DEFAULT_BASE_URL
    override var name = "Motchill"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    override var lang = "vi"
    override val hasMainPage = true

    companion object {
        const val PORTAL_URL = MotchillLogic.PORTAL_URL
        private var cachedDomain: String? = null
    }

    /**
     * Tự động kiểm tra và lấy domain đang hoạt động từ motchillw.sh.
     * Caching lại domain hợp lệ để tránh gửi request dư thừa.
     */
    private suspend fun getDomain(): String {
        cachedDomain?.let { return it }

        try {
            val html = app.get(PORTAL_URL, timeout = 5).text
            val domain = MotchillLogic.parseDomain(html)
            if (domain.isNotEmpty()) {
                cachedDomain = domain
                mainUrl = domain
                return domain
            }
        } catch (e: Exception) {
        }

        return mainUrl
    }

    // ==========================================
    // 1. CẤU HÌNH DANH MỤC TRANG CHỦ
    // ==========================================

    override val mainPage: List<MainPageData> = mainPageOf(
        "/" to "🔥 Phim Mới Cập Nhật",
        "/danh-sach/phim-bo" to "📺 Phim Bộ Mới Nhất",
        "/danh-sach/phim-le" to "🎬 Phim Lẻ Mới Nhất",
        "/the-loai/hoat-hinh" to "⛩️ Phim Hoạt Hình",
        "/the-loai/hanh-dong" to "💥 Phim Hành Động",
        "/the-loai/tinh-cam" to "💖 Phim Tình Cảm",
        "/the-loai/co-trang" to "🎎 Phim Cổ Trang",
        "/the-loai/kinh-di" to "👻 Phim Kinh Dị",
        "/quoc-gia/trung-quoc" to "🐉 Phim Trung Quốc",
        "/quoc-gia/han-quoc" to "🌸 Phim Hàn Quốc",
        "/quoc-gia/au-my" to "🗽 Phim Âu Mỹ"
    )

    /**
     * Tải danh sách phim cho từng mục trang chủ.
     */
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val currentBase = getDomain()
        val rawPath = request.data
        val cleanPath = if (rawPath.startsWith("http")) {
            rawPath
        } else {
            val p = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
            "$currentBase$p"
        }

        val connector = if (cleanPath.contains("?")) "&" else "?"
        val url = if (page > 1) "$cleanPath${connector}page=$page" else cleanPath

        val responseText = app.get(url).text

        // Ủy nhiệm việc parse danh sách phim cho MotchillLogic (Java)
        val items = MotchillLogic.parseMovieList(responseText, currentBase).mapNotNull { item ->
            toSearchResponse(item)
        }

        return newHomePageResponse(request, items, hasNext = items.size >= 12)
    }

    // ==========================================
    // 2. TÌM KIẾM THÔNG MINH (SEARCH)
    // ==========================================

    /**
     * Tìm kiếm phim theo từ khóa.
     */
    override suspend fun search(query: String, page: Int): SearchResponseList {
        val currentBase = getDomain()
        val searchUrl = MotchillLogic.buildSearchUrl(currentBase, query, page)
        val responseText = app.get(searchUrl).text

        val items = MotchillLogic.parseMovieList(responseText, currentBase).mapNotNull { item ->
            toSearchResponse(item)
        }

        return newSearchResponseList(items, hasNext = items.size >= 12)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return search(query, 1).items
    }

    /**
     * Chuyển đổi MovieItem từ tầng Java sang SearchResponse của CloudStream:
     * - Badges: Tự động gán nhãn Vietsub, Thuyết Minh, Lồng Tiếng, Chất lượng, Số tập
     * - DubStatus: Bật biểu tượng SUB / DUB tương ứng trên poster
     */
    private fun toSearchResponse(item: MovieItem): SearchResponse? {
        if (item.title.isNullOrBlank() || item.href.isNullOrBlank() || item.posterUrl.isNullOrBlank()) {
            return null
        }

        val hasDub = item.tags.any { tag ->
            tag.contains("Thuyết minh", ignoreCase = true) ||
            tag.contains("Lồng tiếng", ignoreCase = true) ||
            tag.contains("Song ngữ", ignoreCase = true)
        }
        val hasSub = item.tags.any { tag ->
            tag.contains("Vietsub", ignoreCase = true) ||
            tag.contains("Song ngữ", ignoreCase = true)
        }

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

    // ==========================================
    // 3. CHI TIẾT BỘ PHIM (LOAD MOVIE DETAIL)
    // ==========================================

    /**
     * Tải thông tin chi tiết phim và danh sách các tập.
     */
    override suspend fun load(url: String): LoadResponse {
        val currentBase = getDomain()
        val content = app.get(url).text

        // Ủy nhiệm parse cho MotchillLogic (Java)
        val detail = MotchillLogic.parseMovieDetail(content, currentBase)

        // Chuyển EpisodeItem (Java) sang Episode (CloudStream)
        val episodesList = detail.episodes.map { ep ->
            newEpisode(ep.href) {
                this.name = ep.name
                this.episode = ep.episodeNum
            }
        }

        val tvType = if (episodesList.size > 1) TvType.TvSeries else TvType.Movie

        return if (tvType == TvType.TvSeries) {
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
    // 4. TRÍCH XUẤT LINK PHÁT VIDEO (LOAD LINKS)
    // ==========================================

    /**
     * Trích xuất các link phát video đa máy chủ.
     * data: href của tập phim, kèm fragment servers payload nếu có.
     */
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val currentBase = getDomain()

        // 1. Thử trích xuất trực tiếp từ data fragment (không cần request mạng)
        var videoLinks = MotchillLogic.extractVideoLinks("", data)

        // 2. Nếu chưa có link, fetch trang HTML xem tập để bóc tách
        if (videoLinks.isEmpty()) {
            val content = app.get(data).text
            videoLinks = MotchillLogic.extractVideoLinks(content, data)
        }

        for (link in videoLinks) {
            val serverSource = if (!link.serverName.isNullOrBlank()) link.serverName else name
            val streamName = if (!link.langName.isNullOrBlank()) "${link.serverName} (${link.langName})" else link.label

            when (link.type) {
                MotchillLogic.VideoLink.TYPE_M3U8 -> {
                    callback.invoke(
                        ExtractorLink(
                            source = serverSource,
                            name = streamName,
                            url = link.url,
                            referer = "$currentBase/",
                            quality = Qualities.P1080.value,
                            type = ExtractorLinkType.M3U8
                        )
                    )
                }
                MotchillLogic.VideoLink.TYPE_EMBED -> {
                    loadExtractor(link.url, "$currentBase/", subtitleCallback) { extractedLink ->
                        callback.invoke(
                            ExtractorLink(
                                source = serverSource,
                                name = if (!link.langName.isNullOrBlank()) "${link.langName} (${extractedLink.name})" else extractedLink.name,
                                url = extractedLink.url,
                                referer = extractedLink.referer,
                                quality = extractedLink.quality,
                                type = extractedLink.type,
                                headers = extractedLink.headers
                            )
                        )
                    }
                }
            }
        }

        return videoLinks.isNotEmpty()
    }
}
