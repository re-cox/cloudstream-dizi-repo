package com.dizi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Dizilla : BaseTurkishProvider() {
    override val siteKey = "Dizilla"
    override var mainUrl = "https://dizilla.now"
    override var name = "Dizilla"
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "/tum-bolumler" to "Son Eklenen Bölümler",
        "/dizi-turu/aksiyon" to "Aksiyon",
        "/dizi-turu/bilim-kurgu" to "Bilim Kurgu",
        "/dizi-turu/dram" to "Dram",
        "/dizi-turu/komedi" to "Komedi",
        "/dizi-turu/gerilim" to "Gerilim"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureLiveDomain()
        val url = if (page <= 1) "${mainUrl}${request.data}" else "${mainUrl}${request.data}/$page"
        val document = app.get(url, referer = "${mainUrl}/").document
        val items = document.select("div.grid a[href*='/dizi/'], span.watchlistitem-card a, div.single-item a")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h2, span.font-normal, div.title")?.text()?.trim()
            ?: attr("title").trim().takeIf { it.isNotBlank() }
            ?: return null
        val href = fixUrlNull(attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } })
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureLiveDomain()
        val response = app.post(
            "${mainUrl}/bg/searchcontent",
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
            referer = "${mainUrl}/",
            data = mapOf("query" to query)
        ).text
        val doc = org.jsoup.Jsoup.parse(response)
        return doc.select("a[href*='/dizi/']").mapNotNull { it.toSearchResult() }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        ensureLiveDomain()
        val liveUrl = rewriteToLiveDomain(url)
        val document = app.get(liveUrl, referer = "${mainUrl}/").document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("img.poster, [property='og:image']")?.let {
            it.attr("data-src").ifBlank { it.attr("src").ifBlank { it.attr("content") } }
        })
        val plot = document.selectFirst("div.mt-2 p, p.overview")?.text()?.trim()

        val episodes = document.select("div.episodes-list a, a[href*='-sezon-'][href*='-bolum']").mapNotNull { ep ->
            val href = fixUrlNull(ep.attr("href")) ?: return@mapNotNull null
            val name = ep.text().trim()
            val season = Regex("""(\d+)-sezon""").find(href)?.groupValues?.get(1)?.toIntOrNull()
            val episode = Regex("""(\d+)-bolum""").find(href)?.groupValues?.get(1)?.toIntOrNull()
            newEpisode(href) {
                this.name = name
                this.season = season
                this.episode = episode
            }
        }.distinctBy { it.data }

        return newTvSeriesLoadResponse(title, liveUrl, TvType.TvSeries, episodes) {
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
