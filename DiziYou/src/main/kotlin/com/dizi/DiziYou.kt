package com.dizi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class DiziYou : BaseTurkishProvider() {
    override val siteKey = "DiziYou"
    override var mainUrl = "https://www.diziyou.one"
    override var name = "DiziYou"
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "/dizi-arsivi/page/1/?filtrele=tarih" to "Yeni Eklenen Diziler",
        "/dizi-arsivi/page/1/?filtrele=imdb" to "IMDb Puanına Göre",
        "/dizi-arsivi/page/1/?tur=Aksiyon" to "Aksiyon",
        "/dizi-arsivi/page/1/?tur=Bilim+Kurgu" to "Bilim Kurgu",
        "/dizi-arsivi/page/1/?tur=Dram" to "Dram",
        "/dizi-arsivi/page/1/?tur=Komedi" to "Komedi"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureLiveDomain()
        val url = "${mainUrl}${request.data.replace("/page/1/", "/page/$page/")}"
        val document = app.get(url, referer = "${mainUrl}/").document
        val items = document.select("div.single-item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("div#categorytitle a, div.title a")?.text()?.trim() ?: return null
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } })
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureLiveDomain()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val document = app.get("${mainUrl}/?s=$encoded", referer = "${mainUrl}/").document
        return document.select("div.incontent div.list-series").mapNotNull { el ->
            val link = el.selectFirst("a") ?: return@mapNotNull null
            val title = el.selectFirst("div.title")?.text()?.trim() ?: link.text().trim()
            val href = fixUrlNull(link.attr("href")) ?: return@mapNotNull null
            val poster = fixUrlNull(el.selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } })
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        ensureLiveDomain()
        val liveUrl = rewriteToLiveDomain(url)
        val document = app.get(liveUrl, referer = "${mainUrl}/").document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.category_image img, [property='og:image']")?.let {
            it.attr("data-src").ifBlank { it.attr("src").ifBlank { it.attr("content") } }
        })
        val plot = document.selectFirst("div.diziyou_desc")?.text()?.trim()

        val episodes = document.select("div.bolumust a").mapNotNull { ep ->
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
        val itemId = document.selectFirst("iframe#diziyouPlayer")?.attr("src")
            ?.substringAfterLast("/")?.substringBefore(".html")

        if (!itemId.isNullOrBlank()) {
            val storageUrl = "https://storage.diziyou.one"
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "$name (Altyazılı)",
                    url = "$storageUrl/episodes/$itemId/play.m3u8",
                    referer = "${mainUrl}/",
                    quality = Qualities.Unknown.value,
                    isM3u8 = true
                )
            )
            subtitleCallback.invoke(SubtitleFile("Türkçe", "$storageUrl/subtitles/$itemId/tr.vtt"))
            subtitleCallback.invoke(SubtitleFile("English", "$storageUrl/subtitles/$itemId/en.vtt"))
            return true
        }

        val iframe = document.selectFirst("iframe[src]")?.attr("src") ?: return false
        return loadExtractor(fixUrl(iframe), "${mainUrl}/", subtitleCallback, callback)
    }
}
