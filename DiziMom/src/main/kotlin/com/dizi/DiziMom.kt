package com.dizi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class DiziMom : BaseTurkishProvider() {
    override val siteKey = "DiziMom"
    override var mainUrl = "https://ddizimom.net"
    override var name = "DiziMom"
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "/tum-bolumler/" to "Son Bölümler",
        "/yerli-dizi-izle/" to "Yerli Diziler",
        "/yabanci-dizi-izle/" to "Yabancı Diziler",
        "/turkce-dublaj-diziler/" to "Türkçe Dublaj Diziler",
        "/film-izle/" to "Filmler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureLiveDomain()
        val url = pagedUrl(request.data, page)
        val document = getDoc(url)
        val items = document.select("div.single-item, div.episode-box").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val link = selectFirst("div.categorytitle a, div.poster-name a, a") ?: return null
        val title = link.text().trim().takeIf { it.isNotBlank() } ?: return null
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
        val document = app.get("${mainUrl}/?s=$encoded", referer = "${mainUrl}/").document
        return document.select("div.single-item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        ensureLiveDomain()
        val liveUrl = rewriteToLiveDomain(url)
        val document = app.get(liveUrl, referer = "${mainUrl}/").document

        val title = document.selectFirst("div.title h1, h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.category_image img, [property='og:image']")?.let {
            it.attr("data-src").ifBlank { it.attr("src").ifBlank { it.attr("content") } }
        })
        val plot = document.selectFirst("div.category_desc p, div.summary")?.text()?.trim()

        val epEls = document.select("div.bolumust a")
        return if (epEls.isNotEmpty()) {
            val episodes = epEls.mapNotNull { ep ->
                val href = fixUrlNull(ep.attr("href")) ?: return@mapNotNull null
                val epName = ep.selectFirst("div.bolumismi")?.text()?.trim() ?: ep.text().trim()
                val season = Regex("""(\d+)-sezon""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                val episode = Regex("""(\d+)-bolum""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                newEpisode(href) {
                    this.name = epName
                    this.season = season
                    this.episode = episode
                }
            }
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

        val iframes = document.select("div.video p iframe, iframe[src], iframe[data-src]")
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
