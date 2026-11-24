package com.dizi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class SezonlukDizi : BaseTurkishProvider() {
    override val siteKey = "SezonlukDizi"
    override var mainUrl = "https://sezonlukdizi.cc"
    override var name = "SezonlukDizi"
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "/diziler.asp?siralama_tipi=id&s=1" to "Yeni Eklenen Diziler",
        "/diziler.asp?siralama_tipi=imdb&s=1" to "IMDb Puanına Göre",
        "/diziler.asp?siralama_tipi=id&kat=Aksiyon&s=1" to "Aksiyon",
        "/diziler.asp?siralama_tipi=id&kat=Bilim+Kurgu&s=1" to "Bilim Kurgu",
        "/diziler.asp?siralama_tipi=id&kat=Dram&s=1" to "Dram",
        "/diziler.asp?siralama_tipi=id&kat=Komedi&s=1" to "Komedi"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureLiveDomain()
        val url = "${mainUrl}${request.data.replace("&s=1", "&s=$page")}"
        val document = app.get(url, referer = "${mainUrl}/").document
        val items = document.select("div.afis a").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("div.description")?.text()?.trim() ?: return null
        val href = fixUrlNull(attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } })
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureLiveDomain()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val document = app.get("${mainUrl}/diziler.asp?adi=$encoded", referer = "${mainUrl}/").document
        return document.select("div.afis a").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        ensureLiveDomain()
        val liveUrl = rewriteToLiveDomain(url)
        val document = app.get(liveUrl, referer = "${mainUrl}/").document

        val title = document.selectFirst("div.header")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.image img, [property='og:image']")?.let {
            it.attr("data-src").ifBlank { it.attr("src").ifBlank { it.attr("content") } }
        })
        val plot = document.selectFirst("span#tartisma_ozet, div.description p")?.text()?.trim()

        val episodesUrl = liveUrl.replace("/diziler/", "/bolumler/")
        val epDoc = app.get(episodesUrl, referer = liveUrl).document
        val episodes = epDoc.select("table.unstackable tbody tr").mapNotNull { row ->
            val link = row.selectFirst("td:nth-of-type(4) a, a") ?: return@mapNotNull null
            val href = fixUrlNull(link.attr("href")) ?: return@mapNotNull null
            val epName = link.text().trim()
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
        val bid = document.selectFirst("div.bid")?.attr("data-id")

        var found = false
        if (!bid.isNullOrBlank()) {
            for (dil in listOf("1", "0")) {
                val resp = app.post(
                    "${mainUrl}/ajax/dataAlternatif3.asp",
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                    referer = liveUrl,
                    data = mapOf("bid" to bid, "dil" to dil)
                ).text
                val ids = Regex(""""id"\s*:\s*(\d+)""").findAll(resp).map { it.groupValues[1] }.toList()
                for (id in ids) {
                    val embedResp = app.post(
                        "${mainUrl}/ajax/dataEmbed.asp",
                        headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                        referer = liveUrl,
                        data = mapOf("id" to id)
                    ).document
                    val iframe = embedResp.selectFirst("iframe")?.attr("src")
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
