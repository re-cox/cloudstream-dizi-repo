package com.dizi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class WebteIzle : BaseTurkishProvider() {
    override val siteKey = "WebteIzle"
    override var mainUrl = "https://webteizle.info"
    override var name = "WebteIzle"
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "/film-robotu?s=1" to "Güncel Filmler",
        "/film-robotu?dil=1&s=1" to "Türkçe Dublaj",
        "/film-robotu?dil=2&s=1" to "Türkçe Altyazılı",
        "/film-robotu?tur=Aksiyon&s=1" to "Aksiyon",
        "/film-robotu?tur=Bilim+Kurgu&s=1" to "Bilim Kurgu",
        "/film-robotu?tur=Dram&s=1" to "Dram"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureLiveDomain()
        val url = "${mainUrl}${request.data.replace("s=1", "s=$page")}"
        val document = getDoc(url)
        val items = document.select("div.golgever").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("div.filmname")?.text()?.trim() ?: return null
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } })
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureLiveDomain()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val document = app.get("${mainUrl}/film-robotu?q=$encoded", referer = "${mainUrl}/").document
        return document.select("div.golgever").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        ensureLiveDomain()
        val liveUrl = rewriteToLiveDomain(url)
        val document = app.get(liveUrl, referer = "${mainUrl}/").document

        val title = document.selectFirst("h1.page-title, h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.card img, [property='og:image']")?.let {
            it.attr("data-src").ifBlank { it.attr("src").ifBlank { it.attr("content") } }
        })
        val plot = document.selectFirst("blockquote p")?.text()?.trim()

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
        val filmId = document.selectFirst("button#wip")?.attr("data-id")

        var found = false
        if (!filmId.isNullOrBlank()) {
            for (dil in listOf("0", "1")) {
                val resp = app.post(
                    "${mainUrl}/ajax/dataAlternatif3.asp",
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                    referer = liveUrl,
                    data = mapOf("filmid" to filmId, "dil" to dil)
                ).text
                val ids = Regex(""""id"\s*:\s*(\d+)""").findAll(resp).map { it.groupValues[1] }.toList()
                for (id in ids) {
                    val embedDoc = app.post(
                        "${mainUrl}/ajax/dataEmbed.asp",
                        headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                        referer = liveUrl,
                        data = mapOf("id" to id)
                    ).document
                    val iframe = embedDoc.selectFirst("iframe")?.attr("src")
                    if (!iframe.isNullOrBlank()) {
                        if (loadExtractor(fixUrl(iframe), "${mainUrl}/", subtitleCallback, callback)) {
                            found = true
                        }
                    }
                }
            }
        }
        return found
    }
}
