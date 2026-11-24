package com.dizi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class DiziBox : BaseTurkishProvider() {
    override val siteKey = "DiziBox"
    override var mainUrl = "https://www.dizibox.live"
    override var name = "DiziBox"
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "/dizi-arsivi/page/1/?orderby=date" to "Yeni Eklenen Diziler",
        "/dizi-arsivi/page/1/?orderby=views" to "Popüler Diziler",
        "/tur/aksiyon/page/1/" to "Aksiyon",
        "/tur/bilim-kurgu/page/1/" to "Bilim Kurgu",
        "/tur/dram/page/1/" to "Dram",
        "/tur/komedi/page/1/" to "Komedi",
        "/tur/gerilim/page/1/" to "Gerilim"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureLiveDomain()
        val url = "${mainUrl}${request.data.replace("/page/1/", "/page/$page/")}"
        val document = app.get(url, referer = "${mainUrl}/").document
        val home = document.select("article.detailed-article, div.article-wrapper article")
            .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val link = selectFirst("h3 a, a.post-title, a") ?: return null
        val title = link.text().trim().takeIf { it.isNotBlank() } ?: return null
        val href = fixUrlNull(link.attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } })
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureLiveDomain()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val document = app.get("${mainUrl}/?s=$encoded", referer = "${mainUrl}/").document
        return document.select("article.detailed-article, div.search-results article")
            .mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        ensureLiveDomain()
        val liveUrl = rewriteToLiveDomain(url)
        val document = app.get(liveUrl, referer = "${mainUrl}/").document

        val title = document.selectFirst("div.tv-overview h1, h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.tv-poster img, [property='og:image']")?.let {
            it.attr("data-src").ifBlank { it.attr("src").ifBlank { it.attr("content") } }
        })
        val plot = document.selectFirst("div.tv-story p")?.text()?.trim()
        val tags = document.select("a[rel='tag'], div.terms a").map { it.text().trim() }

        val episodes = mutableListOf<Episode>()
        val seasonLinks = document.select("div#seasons-list a").mapNotNull { fixUrlNull(it.attr("href")) }
        val pagesToScan = if (seasonLinks.isNotEmpty()) seasonLinks else listOf(liveUrl)

        for (seasonUrl in pagesToScan) {
            val sDoc = if (seasonUrl == liveUrl) document else app.get(seasonUrl, referer = liveUrl).document
            sDoc.select("article.season-episode, div.season-episode").forEach { epEl ->
                val epLink = epEl.selectFirst("a.season-episode-title, a") ?: return@forEach
                val epHref = fixUrlNull(epLink.attr("href")) ?: return@forEach
                val epName = epLink.text().trim()
                val season = Regex("""(\d+)-sezon""").find(epHref)?.groupValues?.get(1)?.toIntOrNull()
                val episode = Regex("""(\d+)-bolum""").find(epHref)?.groupValues?.get(1)?.toIntOrNull()
                episodes.add(
                    newEpisode(epHref) {
                        this.name = epName
                        this.season = season
                        this.episode = episode
                    }
                )
            }
        }

        return newTvSeriesLoadResponse(title, liveUrl, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = plot
            this.tags = tags
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

        val playerLinks = mutableListOf<String>()
        document.select("div#video-area iframe, div.video-wrapper iframe").forEach {
            val src = it.attr("data-src").ifBlank { it.attr("src") }
            if (src.isNotBlank()) playerLinks.add(fixUrl(src))
        }
        document.select("div.video-options option[value]").forEach { opt ->
            val v = opt.attr("value")
            if (v.startsWith("http")) playerLinks.add(v)
        }

        var found = false
        for (link in playerLinks.distinct()) {
            if (loadExtractor(link, "${mainUrl}/", subtitleCallback, callback)) {
                found = true
            }
        }
        return found
    }
}
