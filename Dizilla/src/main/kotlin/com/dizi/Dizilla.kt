package com.dizi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Current public /api/mj and streamed RSC format, observed November 2026. */
class Dizilla : BaseTurkishProvider() {
    override val siteKey = "Dizilla"
    override var mainUrl = "https://dizilla.la"
    override var name = "Dizilla"
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Anime)
    override val mainPage = mainPageOf("dizi" to "Diziler", "anime" to "Anime")

    private suspend fun api(action: String, params: List<Any?>): JSONObject {
        val body = JSONObject().put("action", action).put("params", JSONArray(params)).toString()
        return JSONObject(app.post("$mainUrl/api/mj", referer = "$mainUrl/",
            headers = mapOf("Origin" to mainUrl),
            requestBody = body.toRequestBody("application/json".toMediaType())).text)
    }

    private fun items(array: JSONArray?): List<SearchResponse> = (0 until (array?.length() ?: 0)).mapNotNull { i ->
        val item = array?.optJSONObject(i) ?: return@mapNotNull null
        val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val href = item.optString("url").ifBlank { "/" + item.optString("slug") }
        if (href == "/") return@mapNotNull null
        newTvSeriesSearchResponse(title, fixUrl(href), TvType.TvSeries) {
            this.posterUrl = fixUrlNull(item.optString("poster_url"))
            this.year = item.optInt("year").takeIf { it > 0 }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureLiveDomain()
        val result = api("getBrowseTitles", listOf("", request.data, null, null, null, null, null, "rec", page, 24))
            .optJSONObject("result")
        return newHomePageResponse(request.name, items(result?.optJSONArray("items")), hasNext = result?.optBoolean("has_more") == true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureLiveDomain()
        val result = api("getSearchSuggest", listOf(query, 20)).optJSONObject("result")
        return items(result?.optJSONArray("items"))
    }

    override suspend fun load(url: String): LoadResponse? {
        ensureLiveDomain()
        val live = rewriteToLiveDomain(url)
        val document = getDoc(live)
        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val rsc = PageJson.rsc(document.html())
        val episodes = PageJson.arrays(rsc, "episodes").flatMap { array ->
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val href = item.optString("url").takeIf { it.contains("-bolum") } ?: return@mapNotNull null
                newEpisode(fixUrl(href)) {
                    this.name = item.optString("title")
                    this.season = item.optInt("s").takeIf { it > 0 }
                    this.episode = item.optInt("n").takeIf { it > 0 }
                    this.posterUrl = fixUrlNull(item.optString("still"))
                }
            }
        }.distinctBy { it.data }
        return newTvSeriesLoadResponse(title, live, TvType.TvSeries, episodes) {
            this.posterUrl = document.selectFirst("meta[property=og:image]")?.attr("content")
            this.plot = document.selectFirst("meta[name=description]")?.attr("content")
        }
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
        val document = getDoc(rewriteToLiveDomain(data))
        val rsc = PageJson.rsc(document.html())
        val sources = PageJson.arrays(rsc, "sources").flatMap { array ->
            (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.optString("src")?.takeIf { it.startsWith("http") } }
        }.distinct()
        var found = false
        for (source in sources) {
            val collect: (ExtractorLink) -> Unit = { link -> found = true; callback(link) }
            runCatching {
                if (ContentXGenel.uygunMu(source)) ContentXGenel().getUrl(source, "$mainUrl/", subtitleCallback, collect)
                else loadExtractor(source, "$mainUrl/", subtitleCallback, collect)
            }
        }
        return found
    }
}
