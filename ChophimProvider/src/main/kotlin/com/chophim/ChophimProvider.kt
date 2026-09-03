package com.chophim

import com.cloudstream.core.model.MovieItem
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor

/**
 * ChophimProvider - CloudStream Provider cho trang web ChoPhim.app.
 *
 * Áp dụng mô hình kiến trúc Hybrid Pattern:
 * - Provider Kotlin chỉ đảm nhiệm làm Adapter mỏng kết nối với CloudStream framework (Metadata, Network, Mapping).
 * - Toàn bộ logic bóc tách, chuẩn hóa dữ liệu, bóc tách link đa máy chủ được ủy nhiệm cho [ChophimLogic] (Java Core).
 */
class ChophimProvider : MainAPI() {
    override var mainUrl = ChophimLogic.DEFAULT_BASE_URL
    override var name = "ChoPhim"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    override var lang = "vi"
    override val hasMainPage = true

    // ==========================================
    // 1. CẤU HÌNH DANH MỤC TRANG CHỦ
    // ==========================================

    override val mainPage: List<MainPageData> = mainPageOf(
        "/api/film/danh-sach/phim-moi" to "🔥 Phim Mới Cập Nhật",
        "/api/film/danh-sach/phim-bo" to "📺 Phim Bộ Mới Nhất",
        "/api/film/danh-sach/phim-le" to "🎬 Phim Lẻ Mới Nhất",
        "/api/film/danh-sach/hoat-hinh" to "⛩️ Hoạt Hình / Anime",
        "/api/film/danh-sach/tv-shows" to "🎤 TV Shows Truyền Hình",
        "/api/film/the-loai/co-trang" to "🎎 Phim Cổ Trang",
        "/api/film/the-loai/hanh-dong" to "💥 Phim Hành Động",
        "/api/film/the-loai/tinh-cam" to "💖 Phim Tình Cảm",
        "/api/film/the-loai/kinh-di" to "👻 Phim Kinh Dị",
        "/api/film/the-loai/hai-huoc" to "🤣 Phim Hài Hước",
        "/api/film/the-loai/vien-tuong" to "🚀 Phim Viễn Tưởng",
        "/api/film/quoc-gia/han-quoc" to "🌸 Phim Hàn Quốc",
        "/api/film/quoc-gia/trung-quoc" to "🐉 Phim Trung Quốc",
        "/api/film/quoc-gia/au-my" to "🗽 Phim Âu Mỹ",
        "/api/film/quoc-gia/nhat-ban" to "🌸 Phim Nhật Bản",
        "/api/film/quoc-gia/thai-lan" to "🐘 Phim Thái Lan"
    )

    /**
     * Tải danh sách phim cho từng mục trang chủ.
     * Sử dụng trực tiếp JSON API của chophim.app cho tốc độ phản hồi tức thì.
     */
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val rawPath = request.data
        val cleanPath = if (rawPath.startsWith("http")) {
            rawPath
        } else {
            val p = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
            "$mainUrl$p"
        }

        val connector = if (cleanPath.contains("?")) "&" else "?"
        val url = "$cleanPath${connector}page=$page"

        val responseText = app.get(url).text

        // Ủy nhiệm việc parse danh sách phim cho ChophimLogic (Java)
        val items = ChophimLogic.parseMovieList(responseText, mainUrl).mapNotNull { item ->
            toSearchResponse(item)
        }

        // Mỗi trang chophim.app trả về 16 phim
        return newHomePageResponse(request, items, hasNext = items.size >= 16)
    }

    // ==========================================
    // 2. TÌM KIẾM THÔNG MINH (SEARCH)
    // ==========================================

    /**
     * Tìm kiếm phim theo từ khóa kèm bộ lọc thông minh (Smart Search).
     */
    override suspend fun search(query: String, page: Int): SearchResponseList {
        val searchUrl = ChophimLogic.buildSearchUrl(mainUrl, query, page)
        val responseText = app.get(searchUrl).text

        val items = ChophimLogic.parseMovieList(responseText, mainUrl).mapNotNull { item ->
            toSearchResponse(item)
        }

        return newSearchResponseList(items, hasNext = items.size >= 16)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return search(query, 1).items
    }

    /**
     * Chuyển đổi MovieItem từ tầng Java sang SearchResponse của CloudStream:
     * - Badges: Tự động gán nhãn Vietsub, Thuyết Minh, Lồng Tiếng, Chất lượng, Số tập
     * - DubStatus: Bật biểu tượng SUB / DUB tương ứng trên poster
     */
    private fun toSearchResponse(item: MovieItem): SearchResponse {
        val hasDub = item.tags.any { it == "LT" || it == "TM" || it == "SN" }
        val hasSub = item.tags.any { it == "VS" || it == "SN" }

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
     * Ưu tiên truy vấn qua JSON API /api/film/phim/{slug}, tự động fallback sang cào HTML nếu cần.
     */
    override suspend fun load(url: String): LoadResponse {
        val slug = url.trimEnd('/').substringAfterLast("/").substringBefore("?")
        val apiUrl = "$mainUrl/api/film/phim/$slug"

        val content = try {
            app.get(apiUrl, timeout = 10).text
        } catch (e: Exception) {
            app.get(url).text
        }

        // Ủy nhiệm parse cho ChophimLogic
        val detail = ChophimLogic.parseMovieDetail(content, mainUrl)

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
     * Trích xuất các link phát video đa máy chủ (Multi-server).
     *
     * data: href của tập phim, ví dụ: https://chophim.app/phim/bay-vao-trai-tim-anh?ep=tap-01
     * Luôn truy vấn API gốc của bộ phim để lấy danh sách link của TẤT CẢ các server cho tập đó.
     */
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val epSlug = if (data.contains("?ep=")) {
            data.substringAfter("?ep=").substringBefore("&")
        } else {
            data.substringAfterLast("/").substringBefore("?")
        }

        val rawMovieUrl = data.substringBefore("?ep=")
        val movieSlug = rawMovieUrl.trimEnd('/').substringAfterLast("/")
        val apiUrl = "$mainUrl/api/film/phim/$movieSlug"

        val content = try {
            app.get(apiUrl, timeout = 10).text
        } catch (e: Exception) {
            app.get(rawMovieUrl).text
        }

        // Ủy nhiệm trích xuất link video cho ChophimLogic
        val videoLinks = ChophimLogic.extractVideoLinks(content, epSlug)

        for (link in videoLinks) {
            val serverSource = if (!link.serverName.isNullOrBlank()) link.serverName else name
            val streamName = if (!link.langName.isNullOrBlank()) "${link.serverName} (${link.langName})" else link.label

            when (link.type) {
                ChophimLogic.VideoLink.TYPE_M3U8 -> {
                    callback.invoke(
                        ExtractorLink(
                            source = serverSource,
                            name = streamName,
                            url = link.url,
                            referer = mainUrl,
                            quality = Qualities.P1080.value,
                            type = ExtractorLinkType.M3U8
                        )
                    )
                }
                ChophimLogic.VideoLink.TYPE_EMBED -> {
                    loadExtractor(link.url, mainUrl, subtitleCallback) { extractedLink ->
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

        return true
    }
}
