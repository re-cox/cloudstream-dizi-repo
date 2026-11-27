package com.dizi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class SetFilmIzle : BaseTurkishProvider() {
    override val siteKey = "SetFilmIzle"
    override var mainUrl = "https://www.setfilmizle.ltd"
    override var name = "SetFilmIzle"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "/film/" to "Son Filmler",
        "/tur/bilim-kurgu/page/1/" to "Bilim Kurgu",
        "/tur/dram/page/1/" to "Dram",
        "/tur/komedi/page/1/" to "Komedi",
        "/tur/gerilim/page/1/" to "Gerilim",
        "/dizi/" to "Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureLiveDomain()
        val path = request.data.replace("/page/1/", "/")
        val url = "${mainUrl}${path}" + if (page > 1) "page/$page/" else ""
        val document = getDoc(url, referer = "${mainUrl}/")
        val items = document.select("a.card-link").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val link = if (tagName() == "a") this else selectFirst("a") ?: return null
        val title = selectFirst("h2.card-ad, h3, .hcard-title")?.text()?.trim()
            ?: selectFirst("img")?.attr("alt")?.takeIf { it.isNotBlank() } ?: return null
        val href = fixUrlNull(link.attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } })
        val isSeries = href.contains("/dizi/")
        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureLiveDomain()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val document = getDoc("${mainUrl}/?s=$encoded", referer = "${mainUrl}/")
        return document.select("a.card-link")
            .mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        ensureLiveDomain()
        val liveUrl = rewriteToLiveDomain(url)
        val document = getDoc(liveUrl, referer = "${mainUrl}/")

        val title = document.selectFirst("div.sheader h1, h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.poster img, [property='og:image']")?.let {
            it.attr("data-src").ifBlank { it.attr("src").ifBlank { it.attr("content") } }
        })
        val plot = document.selectFirst("meta[name=description]")?.attr("content")

        val epEls = document.select("a[href*='/bolum/']")
        return if (epEls.isNotEmpty()) {
            val episodes = epEls.mapNotNull { ep ->
                val href = fixUrlNull(ep.attr("href")) ?: return@mapNotNull null
                val season = Regex("""(\d+)-sezon""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                val episode = Regex("""(\d+)-bolum""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                newEpisode(href) {
                    this.name = ep.text().trim()
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
        var emittedContent = false
        loadContentLinks(data, isCasting, subtitleCallback) { link ->
            if (!AdPolicy.isAdvertisingUrl(link.url)) {
                emittedContent = true
                callback(link)
            }
        }
        return emittedContent
    }

    private suspend fun loadContentLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        ensureLiveDomain()
        val liveUrl = rewriteToLiveDomain(data)
        val document = getDoc(liveUrl, referer = "${mainUrl}/")
        val iframes = document.select("div.playex iframe, iframe[data-src], iframe[src]")
            .mapNotNull { it.attr("data-src").ifBlank { it.attr("src") }.takeIf { s -> s.isNotBlank() } }

        var found = false
        val postId = document.selectFirst("#stfPlayer")?.attr("data-post-id")
        val nonce = Regex("""video:\s*"([^"]+)"""").find(document.html())?.groupValues?.get(1)
        if (!postId.isNullOrBlank() && !nonce.isNullOrBlank()) {
            for (source in document.select("button[data-player-name]")) {
                val response = app.post("$mainUrl/wp-admin/admin-ajax.php", referer = liveUrl,
                    data = mapOf("action" to "get_video_url", "nonce" to nonce, "post_id" to postId,
                        "player_name" to source.attr("data-player-name"), "part_key" to source.attr("data-part-key"))).text
                val stream = org.json.JSONObject(response).optJSONObject("data")?.optJSONObject("stream") ?: continue
                val embed = stream.optString("url")
                if (embed.startsWith("http")) {
                    SetPlayExtractor().getUrl(embed, "$mainUrl/", subtitleCallback) { link -> found = true; callback(link) }
                }
            }
        }
        for (iframe in iframes.distinct()) {
            if (loadExtractor(fixUrl(iframe), "${mainUrl}/", subtitleCallback, callback)) {
                found = true
            }
        }
        return found
    }
}
