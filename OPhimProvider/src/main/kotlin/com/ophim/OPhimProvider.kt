package com.ophim

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities

/**
 * Provider khai thác dữ liệu từ nguồn OPhim (REST API)
 */
class OPhimProvider : MainAPI() {
    override var mainUrl = "https://ophim1.com"
    override var name = "Ổ Phim"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    override var lang = "vi"
    override val hasMainPage = true

    // ==========================================
    // 1. CẤU HÌNH CÁC MỤC TRANG CHỦ
    // ==========================================
    override val mainPage = mainPageOf(
        "$mainUrl/danh-sach/phim-moi-cap-nhat?page=" to "Phim Mới Cập Nhật",
        "$mainUrl/v1/api/danh-sach/phim-bo?page=" to "Phim Bộ",
        "$mainUrl/v1/api/danh-sach/phim-le?page=" to "Phim Lẻ",
        "$mainUrl/v1/api/danh-sach/hoat-hinh?page=" to "Hoạt Hình / Anime"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}$page"
        val response = app.get(url).text
        val parsed = tryParseJson<OPhimListResponse>(response)

        val homeItems = parsed?.items?.map { item ->
            val posterUrl = if (item.posterUrl?.startsWith("http") == true) {
                item.posterUrl
            } else {
                "https://img.ophim.live/uploads/movies/${item.posterUrl ?: item.thumbUrl}"
            }

            newMovieSearchResponse(
                name = item.name ?: "",
                url = "$mainUrl/phim/${item.slug}",
                type = TvType.Movie
            ) {
                this.posterUrl = posterUrl
            }
        } ?: emptyList()

        return newHomePageResponse(request.name, homeItems)
    }

    // ==========================================
    // 2. TÌM KIẾM PHIM
    // ==========================================
    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/v1/api/tim-kiem?keyword=$query"
        val response = app.get(searchUrl).text
        val parsed = tryParseJson<OPhimSearchResponse>(response)

        return parsed?.data?.items?.map { item ->
            val posterUrl = "https://img.ophim.live/uploads/movies/${item.posterUrl ?: item.thumbUrl}"
            newMovieSearchResponse(
                name = item.name ?: "",
                url = "$mainUrl/phim/${item.slug}",
                type = TvType.Movie
            ) {
                this.posterUrl = posterUrl
            }
        } ?: emptyList()
    }

    // ==========================================
    // 3. XEM CHI TIẾT & DANH SÁCH TẬP
    // ==========================================
    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url).text
        val parsed = tryParseJson<OPhimDetailResponse>(response)
            ?: throw ErrorLoadingException("Không thể tải thông tin phim từ $url")

        val movie = parsed.movie ?: throw ErrorLoadingException("Dữ liệu phim rỗng")
        val episodesList = mutableListOf<Episode>()

        parsed.episodes?.forEach { server ->
            server.serverData?.forEach { ep ->
                episodesList.add(
                    newEpisode(ep.linkM3u8 ?: ep.linkEmbed ?: "") {
                        this.name = "${server.serverName ?: "Server"}: ${ep.name ?: "Tập"}"
                    }
                )
            }
        }

        val posterUrl = "https://img.ophim.live/uploads/movies/${movie.posterUrl ?: movie.thumbUrl}"

        return if (movie.type == "series" || (episodesList.size > 1)) {
            newTvSeriesLoadResponse(
                name = movie.name ?: "",
                url = url,
                type = TvType.TvSeries,
                episodes = episodesList
            ) {
                this.posterUrl = posterUrl
                this.plot = movie.content
                this.year = movie.year
            }
        } else {
            newMovieLoadResponse(
                name = movie.name ?: "",
                url = url,
                type = TvType.Movie,
                dataUrl = episodesList.firstOrNull()?.data ?: ""
            ) {
                this.posterUrl = posterUrl
                this.plot = movie.content
                this.year = movie.year
            }
        }
    }

    // ==========================================
    // 4. TRÍCH XUẤT LINK PHÁT VIDEO
    // ==========================================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isEmpty()) return false

        // Nếu data là link trực tiếp định dạng HLS .m3u8
        if (data.endsWith(".m3u8") || data.contains(".m3u8")) {
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "Ổ Phim HLS",
                    url = data,
                    referer = mainUrl,
                    quality = Qualities.P1080.value,
                    type = ExtractorLinkType.M3U8
                )
            )
            return true
        }

        // Hoặc trường hợp link embed cần bóc tách
        return true
    }
}

// ==========================================
// DATA MODELS CHO OPHIM JSON
// ==========================================
data class OPhimListResponse(
    @JsonProperty("items") val items: List<OPhimItem>? = null
)

data class OPhimSearchResponse(
    @JsonProperty("data") val data: OPhimDataSearch? = null
)

data class OPhimDataSearch(
    @JsonProperty("items") val items: List<OPhimItem>? = null
)

data class OPhimItem(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("thumb_url") val thumbUrl: String? = null,
    @JsonProperty("poster_url") val posterUrl: String? = null
)

data class OPhimDetailResponse(
    @JsonProperty("movie") val movie: OPhimMovieDetail? = null,
    @JsonProperty("episodes") val episodes: List<OPhimServer>? = null
)

data class OPhimMovieDetail(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("content") val content: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("thumb_url") val thumbUrl: String? = null,
    @JsonProperty("poster_url") val posterUrl: String? = null,
    @JsonProperty("year") val year: Int? = null
)

data class OPhimServer(
    @JsonProperty("server_name") val serverName: String? = null,
    @JsonProperty("server_data") val serverData: List<OPhimEpisodeData>? = null
)

data class OPhimEpisodeData(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("link_embed") val linkEmbed: String? = null,
    @JsonProperty("link_m3u8") val linkM3u8: String? = null
)
