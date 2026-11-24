package com.dizi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class SelcukFlix : BaseTurkishProvider() {
    override val siteKey = "SelcukFlix"
    override var mainUrl = "https://selcukflix.app"
    override var name = "SelcukFlix"
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "/diziler" to "Yeni Diziler",
        "/filmler" to "Yeni Filmler",
        "/tur/aksiyon" to "Aksiyon",
        "/tur/bilim-kurgu" to "Bilim Kurgu",
        "/tur/dram" to "Dram",
        "/tur/komedi" to "Komedi"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureLiveDomain()
        val url = pagedUrl(request.data, page)
        val document = getDoc(url)
        val items = document.select("div.poster, article.card, div.movie-card")
            .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val link = selectFirst("a") ?: return null
        val title = selectFirst("h2, h3, span.title")?.text()?.trim()
            ?: link.attr("title").trim().takeIf { it.isNotBlank() }
            ?: return null
        val href = fixUrlNull(link.attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } })
        val isMovie = href.contains("/film/")
        return if (isMovie) {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureLiveDomain()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val document = app.get("${mainUrl}/arama?q=$encoded", referer = "${mainUrl}/").document
        return document.select("div.poster, article.card, div.movie-card")
            .mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        ensureLiveDomain()
        val liveUrl = rewriteToLiveDomain(url)
        val document = app.get(liveUrl, referer = "${mainUrl}/").document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val plot = document.selectFirst("div.summary p, p.description")?.text()?.trim()

        val epEls = document.select("div.episodes a, a[href*='sezon'][href*='bolum']")
        return if (epEls.isNotEmpty()) {
            val episodes = epEls.mapNotNull { ep ->
                val href = fixUrlNull(ep.attr("href")) ?: return@mapNotNull null
                val season = Regex("""sezon[-/](\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                val episode = Regex("""bolum[-/](\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                newEpisode(href) {
                    this.name = ep.text().trim()
                    this.season = season
                    this.episode = episode
                }
            }.distinctBy { it.data }
            newTvSeriesLoadResponse(title, liveUrl, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
            }
        } else {
            newMovieLoadResponse(title, liveUrl, TvType.Movie, liveUrl) {
                this.posterUrl = poster
                this.plot = plot
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        ensureLiveDomain()
        val liveUrl = rewriteToLiveDomain(data)
        val document = app.get(liveUrl, referer = "${mainUrl}/").document
        val iframes = document.select("iframe[data-src], iframe[src]")
            .mapNotNull { it.attr("data-src").ifBlank { it.attr("src") }.takeIf { s -> s.isNotBlank() } }

        var found = false
        for (iframe in iframes.distinct()) {
            if (loadExtractor(fixUrl(iframe), "${mainUrl}/", subtitleCallback, callback)) {
                found = true
            }
        }
        return found
    }
}
