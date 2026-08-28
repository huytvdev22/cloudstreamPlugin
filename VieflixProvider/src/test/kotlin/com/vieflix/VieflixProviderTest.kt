package com.vieflix

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit Test Kotlin cho VieflixProvider và kiểm thử tương thích giữa Kotlin <-> Java (VieflixLogic).
 */
class VieflixProviderTest {

    private val provider = VieflixProvider()
    private val mainUrl = provider.mainUrl
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    @Test
    fun testProviderMetadata() {
        assertEquals("Vieflix", provider.name)
        assertEquals("https://vieflix.top", provider.mainUrl)
        assertEquals("vi", provider.lang)
        assertTrue(provider.hasMainPage)
        assertFalse(provider.mainPage.isEmpty())
        println("✅ Provider Metadata OK: ${provider.name} - ${provider.mainUrl}")
    }

    @Test
    fun testKotlinCallToJavaParseMovieList() {
        val url = "$mainUrl/duyet-tim?sortField=year&page=1"
        val html = Jsoup.connect(url).userAgent(userAgent).get().html()

        // Gọi trực tiếp method Java từ Kotlin
        val items: List<VieflixLogic.MovieItem> = VieflixLogic.parseMovieList(html, mainUrl)

        assertNotNull(items)
        assertFalse("Danh sách phim không được rỗng", items.isEmpty())

        val first = items.first()
        println("✅ Kotlin -> Java MovieItem: ${first.title} -> ${first.href}")
        assertNotNull(first.title)
        assertNotNull(first.href)
    }

    @Test
    fun testKotlinCallToJavaParseMovieDetail() {
        val listUrl = "$mainUrl/duyet-tim?sortField=year&page=1"
        val listHtml = Jsoup.connect(listUrl).userAgent(userAgent).get().html()
        val items = VieflixLogic.parseMovieList(listHtml, mainUrl)
        assertFalse(items.isEmpty())

        val movieUrl = items.first().href
        val detailHtml = Jsoup.connect(movieUrl).userAgent(userAgent).get().html()

        // Gọi method Java parseMovieDetail
        val detail: VieflixLogic.MovieDetail = VieflixLogic.parseMovieDetail(detailHtml, mainUrl)

        assertNotNull(detail)
        assertNotNull(detail.title)
        assertNotNull(detail.episodes)
        assertFalse("Episodes không được rỗng", detail.episodes.isEmpty())

        println("✅ Kotlin -> Java MovieDetail: ${detail.title} (${detail.episodes.size} tập)")
    }

    @Test
    fun testKotlinCallToJavaExtractVideoLinks() {
        val listUrl = "$mainUrl/duyet-tim?sortField=year&page=1"
        val listHtml = Jsoup.connect(listUrl).userAgent(userAgent).get().html()
        val items = VieflixLogic.parseMovieList(listHtml, mainUrl)
        assertFalse(items.isEmpty())

        val movieUrl = items.first().href
        val detailHtml = Jsoup.connect(movieUrl).userAgent(userAgent).get().html()
        val detail = VieflixLogic.parseMovieDetail(detailHtml, mainUrl)
        assertFalse(detail.episodes.isEmpty())

        val firstEp = detail.episodes.first()
        val rawSlug = firstEp.href.substringAfterLast("/tap-", "").substringBefore("?")
        val slug = if (rawSlug.isNotBlank()) "tap-$rawSlug" else "tap-full"

        // Gọi method Java extractVideoLinks
        val videoLinks: List<VieflixLogic.VideoLink> = VieflixLogic.extractVideoLinks(detailHtml, slug)

        assertNotNull(videoLinks)
        assertFalse("Danh sách link không được rỗng", videoLinks.isEmpty())

        for (link in videoLinks) {
            println("✅ Kotlin -> Java VideoLink: [${link.type}] ${link.label} -> ${link.url}")
            assertNotNull(link.url)
        }
    }
}
