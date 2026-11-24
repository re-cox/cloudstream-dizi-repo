package com.dizi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class FilmMakinesi : BaseTurkishProvider() {
    override val siteKey = "FilmMakinesi"
    override var mainUrl = "https://filmmakinesi.to"
    override var name = "FilmMakinesi"
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "/filmler/" to "Son Filmler",
        "/tur/aksiyon/" to "Aksiyon",
        "/tur/bilim-kurgu/" to "Bilim Kurgu",
        "/tur/dram/" to "Dram",
        "/tur/komedi/" to "Komedi",
        "/tur/gerilim/" to "Gerilim"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureLiveDomain()
        val url = pagedUrl(request.data, page, pageSegment = "sayfa")
        val document = getDoc(url)
        val items = document.select("section#film_posts article, div.item-relative")
            .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val link = selectFirst("h6 a, a.title, a") ?: return null
        val title = link.text().trim().takeIf { it.isNotBlank() } ?: return null
        val href = fixUrlNull(link.attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } })
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureLiveDomain()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val document = app.get("${mainUrl}/arama/?s=$encoded", referer = "${mainUrl}/").document
        return document.select("section#film_posts article, div.item-relative")
            .mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        ensureLiveDomain()
        val liveUrl = rewriteToLiveDomain(url)
        val document = app.get(liveUrl, referer = "${mainUrl}/").document

        val title = document.selectFirst("div.content h1, h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.poster img, [property='og:image']")?.let {
            it.attr("data-src").ifBlank { it.attr("src").ifBlank { it.attr("content") } }
        })
        val plot = document.selectFirst("section#film_single p")?.text()?.trim()

        return newMovieLoadResponse(title, liveUrl, TvType.Movie, liveUrl) {
            this.posterUrl = poster
            this.plot = plot
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

        val iframes = document.select("div.player-container iframe, iframe[data-src], iframe[src]")
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
