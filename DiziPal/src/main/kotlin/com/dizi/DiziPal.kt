package com.dizi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class DiziPal : BaseTurkishProvider() {
    override val siteKey = "DiziPal"
    override var mainUrl = "https://dizipal2150.com"
    override var name = "DiziPal"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    private val mapper = jacksonObjectMapper()

    override val mainPage = mainPageOf(
        "/diziler/son-bolumler" to "Son Bölümler",
        "/diziler" to "Yeni Diziler",
        "/filmler" to "Yeni Filmler",
        "/koleksiyon/netflix" to "Netflix",
        "/koleksiyon/exxen" to "Exxen",
        "/koleksiyon/blutv" to "BluTV",
        "/koleksiyon/disney" to "Disney+",
        "/koleksiyon/amazon-prime" to "Amazon Prime",
        "/tur/aksiyon" to "Aksiyon",
        "/tur/bilim-kurgu" to "Bilim Kurgu",
        "/tur/dram" to "Dram",
        "/tur/komedi" to "Komedi",
        "/tur/gerilim" to "Gerilim"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureLiveDomain()
        val url = if (page <= 1) {
            "${mainUrl}${request.data}"
        } else {
            "${mainUrl}${request.data}?page=$page"
        }

        val document = app.get(url).document
        val home = if (request.data.contains("/diziler/son-bolumler")) {
            document.select("div.episode-item").mapNotNull { it.toLatestEpisodeResult() }
        } else {
            document.select("article.type2 ul li").mapNotNull { it.toSearchResult() }
        }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("span.title")?.text()?.trim() ?: return null
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.attr("src"))
        val isMovie = href.contains("/film/")

        return if (isMovie) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }

    private fun Element.toLatestEpisodeResult(): SearchResponse? {
        val name = selectFirst("div.name")?.text()?.trim() ?: return null
        val episode = selectFirst("div.episode")?.text()?.trim().orEmpty()
        val title = if (episode.isNotBlank()) "$name - $episode" else name

        val rawHref = selectFirst("a")?.attr("href") ?: return null
        // Convert episode URL back to series URL when possible
        val seriesHref = fixUrlNull(
            rawHref.replace(Regex("""/sezon-\d+/bolum-\d+.*$"""), "")
        ) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.attr("src"))

        return newTvSeriesSearchResponse(title, seriesHref, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AutoCompleteItem(
        @JsonProperty("title") val title: String = "",
        @JsonProperty("url") val url: String = "",
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("type") val type: String? = null
    )

    override suspend fun search(query: String): List<SearchResponse> {
        ensureLiveDomain()
        val response = app.post(
            "${mainUrl}/api/search-autocomplete",
            headers = mapOf(
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest"
            ),
            referer = "${mainUrl}/",
            data = mapOf("query" to query)
        )

        val itemsMap = try {
            mapper.readValue<Map<String, AutoCompleteItem>>(response.text)
        } catch (_: Exception) {
            return emptyList()
        }

        return itemsMap.values.mapNotNull { item ->
            if (item.title.isBlank() || item.url.isBlank()) return@mapNotNull null
            val fullUrl = fixUrl(item.url)
            val isMovie = item.type == "movies" || fullUrl.contains("/film/")
            if (isMovie) {
                newMovieSearchResponse(item.title, fullUrl, TvType.Movie) {
                    this.posterUrl = item.poster?.let { fixUrl(it) }
                }
            } else {
                newTvSeriesSearchResponse(item.title, fullUrl, TvType.TvSeries) {
                    this.posterUrl = item.poster?.let { fixUrl(it) }
                }
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        ensureLiveDomain()
        // Rewrite old domains in saved bookmarks/history to the live domain
        val normalizedUrl = rewriteToLiveDomain(url)
        val document = app.get(normalizedUrl).document

        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val year = document.selectXpath("//div[text()='Yapım Yılı']//following-sibling::div")
            .text().trim().toIntOrNull()
        val description = document.selectFirst("div.summary p")?.text()?.trim()
        val tags = document.selectXpath("//div[text()='Türler']//following-sibling::div")
            .text().trim().split(" ").filter { it.isNotBlank() }
        val rating = document.selectXpath("//div[text()='IMDB Puanı']//following-sibling::div")
            .text().trim().toRatingInt()

        if (normalizedUrl.contains("/dizi/")) {
            val title = document.selectFirst("div.cover h5")?.text()?.trim()
                ?: document.selectFirst("h1")?.text()?.trim()
                ?: return null

            val episodes = document.select("div.episode-item").mapNotNull { ep ->
                val epName = ep.selectFirst("div.name")?.text()?.trim() ?: return@mapNotNull null
                val epHref = fixUrlNull(ep.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                val epText = ep.selectFirst("div.episode")?.text()?.trim().orEmpty()
                val seasonNum = Regex("""(\d+)\.\s*Sezon""").find(epText)?.groupValues?.get(1)?.toIntOrNull()
                val episodeNum = Regex("""(\d+)\.\s*Bölüm""").find(epText)?.groupValues?.get(1)?.toIntOrNull()

                newEpisode(epHref) {
                    this.name = epName
                    this.season = seasonNum
                    this.episode = episodeNum
                }
            }

            return newTvSeriesLoadResponse(title, normalizedUrl, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
            }
        } else {
            val title = document.selectFirst("div.g-title div")?.text()?.trim()
                ?: document.selectFirst("h1")?.text()?.trim()
                ?: return null

            return newMovieLoadResponse(title, normalizedUrl, TvType.Movie, normalizedUrl) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        ensureLiveDomain()
        val normalizedUrl = rewriteToLiveDomain(data)
        val document = app.get(normalizedUrl).document

        val iframe = document.selectFirst(".series-player-container iframe")?.attr("src")
            ?: document.selectFirst("div#vast_new iframe")?.attr("src")
            ?: document.selectFirst("iframe")?.attr("src")
            ?: return false

        val iframeUrl = fixUrl(iframe)
        val iSource = app.get(iframeUrl, referer = "${mainUrl}/").text

        // Direct m3u8 extraction from player script
        val m3uLink = Regex("""file\s*:\s*["']([^"']+\.m3u8[^"']*)["']""")
            .find(iSource)?.groupValues?.get(1)

        if (m3uLink != null) {
            // Parse subtitles if embedded in the player config
            val subtitleRaw = Regex(""""subtitle"\s*:\s*"([^"]+)"""")
                .find(iSource)?.groupValues?.get(1)
            subtitleRaw?.split(",")?.forEach { entry ->
                val langMatch = Regex("""\[([^\]]+)](https?://\S+)""").find(entry.trim())
                if (langMatch != null) {
                    val subLang = langMatch.groupValues[1]
                    val subUrl = langMatch.groupValues[2]
                    subtitleCallback.invoke(SubtitleFile(subLang, subUrl))
                }
            }

            callback.invoke(
                ExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = m3uLink,
                    referer = "${mainUrl}/",
                    quality = Qualities.Unknown.value,
                    isM3u8 = true
                )
            )
            return true
        }

        // Fallback to CloudStream's built-in extractors
        return loadExtractor(iframeUrl, "${mainUrl}/", subtitleCallback, callback)
    }
}
