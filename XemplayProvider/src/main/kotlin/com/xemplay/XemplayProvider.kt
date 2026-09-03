package com.xemplay

import com.cloudstream.core.model.MovieItem
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor

/**
 * XemplayProvider - CloudStream Provider cho trang web xemplay.uk.
 *
 * Áp dụng mô hình kiến trúc Hybrid Pattern:
 * - Provider Kotlin chỉ đóng vai trò Adapter mỏng kết nối với CloudStream framework (Metadata, Network, Mapping).
 * - Toàn bộ logic bóc tách Jsoup DOM, Schema.org JSON-LD, Next.js RSC và phân giải link video được ủy nhiệm cho [XemplayLogic] (Java Core).
 */
class XemplayProvider : MainAPI() {
    override var mainUrl = XemplayLogic.DEFAULT_BASE_URL
    override var name = "XemPlay"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    override var lang = "vi"
    override val hasMainPage = true

    companion object {
        const val PORTAL_URL = XemplayLogic.PORTAL_URL
        private var cachedDomain: String? = null
    }

    /**
     * Tự động kiểm tra và lấy domain đang hoạt động từ portal xemplay.com.
     * Caching lại domain hợp lệ để tránh gửi request dư thừa.
     */
    private suspend fun getDomain(): String {
        cachedDomain?.let { return it }

        try {
            val html = app.get(PORTAL_URL, timeout = 5).text
            val domain = XemplayLogic.parseDomain(html)
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
    // 1. CẤU HÌNH DANH MỤC TRANG CHỦ
    // ==========================================

    override val mainPage: List<MainPageData> = mainPageOf(
        "/browse?type=phim-moi-cap-nhat" to "🔥 Phim Mới Cập Nhật",
        "/browse?type=phim-bo" to "📺 Phim Bộ Mới Nhất",
        "/browse?type=phim-le" to "🎬 Phim Lẻ Mới Nhất",
        "/browse?type=hoat-hinh" to "⛩️ Hoạt Hình / Anime",
        "/browse?type=tv-shows" to "🎤 TV Shows Truyền Hình",
        "/short-drama" to "📱 Short Drama Hot",
        "/browse?category=hanh-dong" to "💥 Phim Hành Động",
        "/browse?category=tinh-cam" to "💖 Phim Tình Cảm",
        "/browse?category=kinh-di" to "👻 Phim Kinh Dị",
        "/browse?category=hai-huoc" to "🤣 Phim Hài Hước",
        "/browse?category=co-trang" to "🎎 Phim Cổ Trang",
        "/browse?category=vien-tuong" to "🚀 Phim Viễn Tưởng",
        "/browse?country=han-quoc" to "🌸 Phim Hàn Quốc",
        "/browse?country=trung-quoc" to "🐉 Phim Trung Quốc",
        "/browse?country=au-my" to "🗽 Phim Âu Mỹ"
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
        val url = "$cleanPath${connector}page=$page"

        val responseText = app.get(url).text

        // Ủy nhiệm việc parse danh sách phim cho XemplayLogic (Java)
        val items = XemplayLogic.parseMovieList(responseText, currentBase).mapNotNull { item ->
            toSearchResponse(item)
        }

        return newHomePageResponse(request, items, hasNext = items.size >= 15)
    }

    // ==========================================
    // 2. TÌM KIẾM THÔNG MINH (SEARCH)
    // ==========================================

    /**
     * Tìm kiếm phim theo từ khóa kèm bộ lọc thông minh (Smart Search).
     */
    override suspend fun search(query: String, page: Int): SearchResponseList {
        val currentBase = getDomain()
        val searchUrl = XemplayLogic.buildSearchUrl(currentBase, query, page)
        val responseText = app.get(searchUrl).text

        val items = XemplayLogic.parseMovieList(responseText, currentBase).mapNotNull { item ->
            toSearchResponse(item)
        }

        return newSearchResponseList(items, hasNext = items.size >= 15)
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

        // Ủy nhiệm parse cho XemplayLogic (Java)
        val detail = XemplayLogic.parseMovieDetail(content, currentBase)

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
     * data: href của tập phim, ví dụ: https://xemplay.uk/phim/keo-ngot-tinh-yeu/tap-01
     */
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val currentBase = getDomain()
        val content = app.get(data).text

        // Ủy nhiệm trích xuất link video cho XemplayLogic (Java)
        val videoLinks = XemplayLogic.extractVideoLinks(content, data)

        for (link in videoLinks) {
            val serverSource = if (!link.serverName.isNullOrBlank()) link.serverName else name
            val streamName = if (!link.langName.isNullOrBlank()) "${link.serverName} (${link.langName})" else link.label

            when (link.type) {
                XemplayLogic.VideoLink.TYPE_M3U8 -> {
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
                XemplayLogic.VideoLink.TYPE_EMBED -> {
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
