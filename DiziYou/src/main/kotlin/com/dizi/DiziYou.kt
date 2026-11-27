package com.dizi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class DiziYou : BaseTurkishProvider() {
    override val siteKey = "DiziYou"
    override var mainUrl = "https://www.diziyou.love"
    override var name = "DiziYou"
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "/dizi-arsivi/?filtrele=tarih" to "Yeni Eklenen Diziler",
        "/dizi-arsivi/?filtrele=imdb" to "IMDb Puanına Göre",
        "/dizi-arsivi/?tur=Aksiyon" to "Aksiyon",
        "/dizi-arsivi/?tur=Bilim+Kurgu" to "Bilim Kurgu",
        "/dizi-arsivi/?tur=Dram" to "Dram",
        "/dizi-arsivi/?tur=Komedi" to "Komedi"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureLiveDomain()
        val url = pagedUrl(request.data, page)
        val document = getDoc(url)
        val items = document.select("div.single-item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("div#categorytitle a, div.title a")?.text()?.trim() ?: return null
        val href = fixUrlNull(selectFirst("div#categorytitle a, div.title a, a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } })
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureLiveDomain()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val document = getDoc("${mainUrl}/?s=$encoded", referer = "${mainUrl}/")
        return document.select("div.incontent div#list-series, div.incontent div.list-series")
            .mapNotNull { it.toSearchResult() }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        ensureLiveDomain()
        val liveUrl = rewriteToLiveDomain(url)
        val document = getDoc(liveUrl, referer = "${mainUrl}/")

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.category_image img, [property='og:image']")?.let {
            it.attr("data-src").ifBlank { it.attr("src").ifBlank { it.attr("content") } }
        })
        val plot = document.selectFirst("div.diziyou_desc")?.text()?.trim()

        val episodes = document.select("div.bolumust").mapNotNull { ep ->
            val href = fixUrlNull(ep.closest("a")?.attr("href") ?: ep.selectFirst("a")?.attr("href"))
                ?: return@mapNotNull null
            val epName = ep.selectFirst("div.bolumismi")?.text()?.trim() ?: ep.text().trim()
            val season = Regex("""(\d+)-sezon""").find(href)?.groupValues?.get(1)?.toIntOrNull()
            val episode = Regex("""(\d+)-bolum""").find(href)?.groupValues?.get(1)?.toIntOrNull()
            newEpisode(href) {
                this.name = epName
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
        val itemId = document.selectFirst("iframe#diziyouPlayer")?.attr("src")
            ?.substringAfterLast("/")?.substringBefore(".html")

        if (!itemId.isNullOrBlank()) {
            val host = java.net.URI(mainUrl).host.removePrefix("www.")
            val storageUrl = "https://storage.$host"
            val options = document.select("span.diziyouOption").map { it.id() }.toSet()
            var found = false
            for ((_, suffix, label) in listOf(
                Triple("turkceAltyazili", "", "Altyazılı"),
                Triple("ingilizceAltyazili", "", "Orijinal"),
                Triple("turkceDublaj", "_tr", "Türkçe Dublaj")
            ).filter { it.first in options }.distinctBy { it.second }) {
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "$name ($label)",
                        url = "$storageUrl/episodes/$itemId$suffix/play.m3u8",
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "${mainUrl}/"
                        this.quality = Qualities.Unknown.value
                    }
                )
                found = true
            }
            if ("turkceAltyazili" in options) subtitleCallback.invoke(SubtitleFile("Türkçe", "$storageUrl/subtitles/$itemId/tr.vtt"))
            if ("ingilizceAltyazili" in options) subtitleCallback.invoke(SubtitleFile("English", "$storageUrl/subtitles/$itemId/en.vtt"))
            return found
        }

        val iframe = document.selectFirst("iframe[src]:not([src*=youtube])")?.attr("src") ?: return false
        return loadExtractor(fixUrl(iframe), "${mainUrl}/", subtitleCallback, callback)
    }
}
