package com.dizi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

/** Current public HTML and XHR search format at the registry's redirected domain. */
class DiziPal : BaseTurkishProvider() {
    override val siteKey = "DiziPal"
    override var mainUrl = "https://dizipal2161.com"
    override var name = "DiziPal"
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)
    override val mainPage = mainPageOf("/diziler" to "Diziler", "/filmler" to "Filmler", "/anime" to "Anime")

    private fun Element.card(): SearchResponse? {
        val href = fixUrlNull(attr("href")) ?: return null
        val title = selectFirst("img")?.attr("alt")?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val poster = fixUrlNull(selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } })
        return if (href.contains("/film/")) newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
            else newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = poster }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureLiveDomain()
        val document = getDoc("$mainUrl${request.data}" + if (page > 1) "?page=$page" else "")
        val items = document.select("a[href*='/dizi/'], a[href*='/film/'], a[href*='/anime/']")
            .mapNotNull { it.card() }.distinctBy { it.url }
        return newHomePageResponse(request.name, items, hasNext = document.selectFirst("a[href*='page=${page + 1}']") != null)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureLiveDomain()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return getDoc("$mainUrl/arama?q=$encoded")
            .select("a.card-link[href*='/dizi/'], a.card-link[href*='/film/']")
            .mapNotNull { it.card() }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        ensureLiveDomain()
        val live = rewriteToLiveDomain(url)
        val doc = getDoc(live)
        val title = doc.selectFirst("h1")?.text()?.removeSuffix(" izle")?.trim() ?: return null
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val plot = doc.selectFirst("meta[name=description]")?.attr("content")
        if (live.contains("/film/")) return newMovieLoadResponse(title, live, TvType.Movie, live) { posterUrl = poster; this.plot = plot }
        val episodes = doc.select("a.detail-episode-item, a.episode-card-link").mapNotNull { a ->
            val href = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            newEpisode(href) {
                this.name = a.text().trim()
                season = Regex("""[-/](\d+)-sezon[-/]""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                episode = Regex("""[-/](\d+)-bolum""").find(href)?.groupValues?.get(1)?.toIntOrNull()
            }
        }.distinctBy { it.data }
        return newTvSeriesLoadResponse(title, live, TvType.TvSeries, episodes) { posterUrl = poster; this.plot = plot }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        var emittedContent = false
        loadContentLinks(data, isCasting, subtitleCallback) { link ->
            if (!AdPolicy.isAdvertisingUrl(link.url)) {
                emittedContent = true
                callback(link)
            }
        }
        return emittedContent
    }

    private suspend fun loadContentLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        ensureLiveDomain()
        val doc = getDoc(rewriteToLiveDomain(data))
        var found = false
        val collect: (ExtractorLink) -> Unit = { link -> found = true; callback(link) }
        for (iframe in doc.select("iframe[src]").mapNotNull { fixUrlNull(it.attr("src")) }.distinct()) {
            if (iframe.contains("youtube")) continue
            runCatching {
                if (iframe.contains("mntplayer.com")) {
                    val response = app.get(iframe, referer = "$mainUrl/", timeout = 10)
                    val contentType = response.headers.toString().lowercase()
                    if (response.isSuccessful && (contentType.contains("video/") || contentType.contains("mpegurl"))) {
                        collect(newExtractorLink(name, name, response.url, if (contentType.contains("mpegurl")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) { referer = "$mainUrl/" })
                    }
                } else if (VidMolyNeo.uygunMu(iframe)) VidMolyNeo().getUrl(iframe, "$mainUrl/", subtitleCallback, collect)
                else loadExtractor(iframe, "$mainUrl/", subtitleCallback, collect)
            }
        }
        return found
    }
}
