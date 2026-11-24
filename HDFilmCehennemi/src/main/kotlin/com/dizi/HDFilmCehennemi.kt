package com.dizi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class HDFilmCehennemi : BaseTurkishProvider() {
    override val siteKey = "HDFilmCehennemi"
    override var mainUrl = "https://www.hdfilmcehennemi.nl"
    override var name = "HDFilmCehennemi"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val mapper = jacksonObjectMapper()

    override val mainPage = mainPageOf(
        "/load/page/1/home/" to "Yeni Eklenen Filmler",
        "/load/page/1/categories/yabanci-dizi-izle/" to "Yabancı Diziler",
        "/load/page/1/genres/aksiyon/" to "Aksiyon",
        "/load/page/1/genres/bilim-kurgu/" to "Bilim Kurgu",
        "/load/page/1/genres/dram/" to "Dram",
        "/load/page/1/genres/komedi/" to "Komedi",
        "/load/page/1/genres/gerilim/" to "Gerilim",
        "/load/page/1/genres/korku/" to "Korku"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureLiveDomain()
        val pagedPath = request.data.replace("/page/1/", "/page/$page/")
        val document = app.get(
            "${mainUrl}$pagedPath",
            headers = mapOf("X-Requested-With" to "fetch"),
            referer = "${mainUrl}/"
        ).document

        val items = document.select("a.poster, div.poster a").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("strong.poster-title, h2.title")?.text()?.trim()
            ?: attr("title").trim().takeIf { it.isNotBlank() }
            ?: return null
        val href = fixUrlNull(attr("href")) ?: return null
        val posterUrl = fixUrlNull(
            selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } }
        )
        val isSeries = href.contains("/dizi/")
        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SearchPayload(
        @JsonProperty("results") val results: List<String> = emptyList()
    )

    override suspend fun search(query: String): List<SearchResponse> {
        ensureLiveDomain()
        val response = app.get(
            "${mainUrl}/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}",
            headers = mapOf("X-Requested-With" to "fetch"),
            referer = "${mainUrl}/"
        ).text

        return try {
            val payload = mapper.readValue<SearchPayload>(response)
            payload.results.mapNotNull { html ->
                val doc = org.jsoup.Jsoup.parse(html)
                val link = doc.selectFirst("a") ?: return@mapNotNull null
                val title = doc.selectFirst("h4.title, span.title")?.text()?.trim()
                    ?: link.text().trim()
                val href = fixUrlNull(link.attr("href")) ?: return@mapNotNull null
                val poster = fixUrlNull(doc.selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } })
                if (href.contains("/dizi/")) {
                    newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster }
                } else {
                    newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        ensureLiveDomain()
        val liveUrl = rewriteToLiveDomain(url)
        val document = app.get(liveUrl, referer = "${mainUrl}/").document

        val title = document.selectFirst("h1.section-title, h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("aside.post-info-poster img, [property='og:image']")?.let {
            it.attr("data-src").ifBlank { it.attr("src").ifBlank { it.attr("content") } }
        })
        val plot = document.selectFirst("article.post-info-content p, div.post-info-content")?.text()?.trim()
        val year = document.selectFirst("a[href*='/yil/']")?.text()?.trim()?.toIntOrNull()
        val tags = document.select("div.post-info-genres a").map { it.text().trim() }

        val episodeEls = document.select("div.seasons-tab-content a, div.bolumler a")
        return if (episodeEls.isNotEmpty() || liveUrl.contains("/dizi/")) {
            val episodes = episodeEls.mapNotNull { ep ->
                val href = fixUrlNull(ep.attr("href")) ?: return@mapNotNull null
                val epName = ep.text().trim()
                val season = Regex("""sezon-(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                val episode = Regex("""bolum-(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                newEpisode(href) {
                    this.name = epName
                    this.season = season
                    this.episode = episode
                }
            }
            newTvSeriesLoadResponse(title, liveUrl, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
            }
        } else {
            newMovieLoadResponse(title, liveUrl, TvType.Movie, liveUrl) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
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

        val videoIds = document.select("div.alternative-links button[data-video]")
            .mapNotNull { it.attr("data-video").takeIf { id -> id.isNotBlank() } }

        var found = false
        for (videoId in videoIds) {
            val resp = app.get(
                "${mainUrl}/video/$videoId/",
                headers = mapOf("X-Requested-With" to "fetch"),
                referer = liveUrl
            ).text
            val iframeSrc = Regex("""data-src=\\?"([^"\\]+)""").find(resp)?.groupValues?.get(1)
                ?.replace("\\/", "/")
                ?: Regex("""src=\\?"(https?://[^"\\]+)""").find(resp)?.groupValues?.get(1)
                    ?.replace("\\/", "/")
            if (!iframeSrc.isNullOrBlank()) {
                if (loadExtractor(fixUrl(iframeSrc), "${mainUrl}/", subtitleCallback, callback)) {
                    found = true
                }
            }
        }

        if (!found) {
            val directIframe = document.selectFirst("iframe[data-src], iframe[src]")?.let {
                it.attr("data-src").ifBlank { it.attr("src") }
            }
            if (!directIframe.isNullOrBlank()) {
                found = loadExtractor(fixUrl(directIframe), "${mainUrl}/", subtitleCallback, callback)
            }
        }
        return found
    }
}
