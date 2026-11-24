package com.dizi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class FilmModu : BaseTurkishProvider() {
    override val siteKey = "FilmModu"
    override var mainUrl = "https://filmmodu.news"
    override var name = "FilmModu"
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "/hd-film-izle" to "Yeni Filmler",
        "/turkce-dublaj-izle" to "Türkçe Dublaj",
        "/turkce-altyazili-izle" to "Türkçe Altyazılı",
        "/kategori/aksiyon" to "Aksiyon",
        "/kategori/bilim-kurgu" to "Bilim Kurgu",
        "/kategori/dram" to "Dram",
        "/kategori/komedi" to "Komedi"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureLiveDomain()
        val url = if (page <= 1) "${mainUrl}${request.data}" else "${mainUrl}${request.data}?page=$page"
        val document = app.get(url, referer = "${mainUrl}/").document
        val items = document.select("div.movie").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("a.original-title, a")?.text()?.trim() ?: return null
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } })
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureLiveDomain()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val document = app.get("${mainUrl}/film-ara?term=$encoded", referer = "${mainUrl}/").document
        return document.select("div.movie").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        ensureLiveDomain()
        val liveUrl = rewriteToLiveDomain(url)
        val document = app.get(liveUrl, referer = "${mainUrl}/").document

        val title = document.selectFirst("div.titles h1, h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("img.img-responsive, [property='og:image']")?.let {
            it.attr("data-src").ifBlank { it.attr("src").ifBlank { it.attr("content") } }
        })
        val plot = document.selectFirst("p[itemprop='description']")?.text()?.trim()
        val tags = document.select("div.description a[href*='/kategori/']").map { it.text().trim() }

        return newMovieLoadResponse(title, liveUrl, TvType.Movie, liveUrl) {
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

        var found = false
        val altLinks = document.select("div.alternates a[href]")
            .mapNotNull { fixUrlNull(it.attr("href")) }
            .ifEmpty { listOf(liveUrl) }

        for (altUrl in altLinks.distinct()) {
            val altDoc = if (altUrl == liveUrl) document else app.get(altUrl, referer = liveUrl).document
            val videoId = Regex("""var\s+videoId\s*=\s*['"](\d+)['"]""").find(altDoc.html())?.groupValues?.get(1)
            val videoType = Regex("""var\s+videoType\s*=\s*['"]([^'"]+)['"]""").find(altDoc.html())?.groupValues?.get(1) ?: "en"

            if (!videoId.isNullOrBlank()) {
                val srcText = app.get(
                    "${mainUrl}/get-source?movie_id=$videoId&type=$videoType",
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                    referer = altUrl
                ).text
                Regex(""""src"\s*:\s*"([^"]+)"""").findAll(srcText).forEach { m ->
                    val streamUrl = m.groupValues[1].replace("\\/", "/")
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = "$name ($videoType)",
                            url = streamUrl,
                            type = if (streamUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.referer = "${mainUrl}/"
                            this.quality = Qualities.Unknown.value
                        }
                    )
                    found = true
                }
                Regex(""""subtitle"\s*:\s*"([^"]+)"""").find(srcText)?.groupValues?.get(1)?.let { sub ->
                    subtitleCallback.invoke(SubtitleFile("Türkçe", fixUrl(sub.replace("\\/", "/"))))
                }
            }
        }
        return found
    }
}
