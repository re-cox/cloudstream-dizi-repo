package com.dizi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class FullHDFilmizlesene : BaseTurkishProvider() {
    override val siteKey = "FullHDFilmizlesene"
    override var mainUrl = "https://www.fullhdfilmizlesene.now"
    override var name = "FullHDFilmizlesene"
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "/en-cok-izlenen-filmler-izle-hd/" to "En Çok İzlenen Filmler",
        "/filmizle/turkce-dublaj-film-izle/" to "Türkçe Dublaj",
        "/filmizle/turkce-altyazili-film-izle/" to "Türkçe Altyazılı",
        "/filmizle/aksiyon-filmleri-hd-izle/" to "Aksiyon",
        "/filmizle/bilim-kurgu-filmleri-izle-2/" to "Bilim Kurgu",
        "/filmizle/dram-filmleri/" to "Dram",
        "/filmizle/komedi-filmleri-izle/" to "Komedi"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureLiveDomain()
        val url = if (page <= 1) "${mainUrl}${request.data}" else "${mainUrl}${request.data}$page"
        val document = getDoc(url)
        val items = document.select("li.film").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("span.film-title")?.text()?.trim() ?: return null
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } })
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureLiveDomain()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val document = app.get("${mainUrl}/arama/$encoded", referer = "${mainUrl}/").document
        return document.select("li.film").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        ensureLiveDomain()
        val liveUrl = rewriteToLiveDomain(url)
        val document = app.get(liveUrl, referer = "${mainUrl}/").document

        val title = document.selectFirst("div.izle-titles h1, h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.afis img, [property='og:image']")?.let {
            it.attr("data-src").ifBlank { it.attr("src").ifBlank { it.attr("content") } }
        })
        val plot = document.selectFirst("div.ozet-ic p")?.text()?.trim()
        val year = document.selectFirst("div.dd a[href*='/yil/']")?.text()?.trim()?.toIntOrNull()

        return newMovieLoadResponse(title, liveUrl, TvType.Movie, liveUrl) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
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

        val iframes = document.select("div#plx iframe, iframe[data-src], iframe[src]")
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
