package com.dizi

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.nodes.Document
import java.net.URI
import java.util.concurrent.CancellationException
import org.jsoup.Jsoup

/**
 * Shared base class for all Turkish providers in this repository.
 * Automatically resolves live domains via [DomainResolver], supports
 * [CloudflareKiller] for CF-protected sites, and rewrites bookmarked URLs.
 */
abstract class BaseTurkishProvider : MainAPI() {
    private class SiteRequestException(val retryDomain: Boolean, message: String) : Exception(message)
    abstract val siteKey: String

    override var lang = "tr"
    override val hasMainPage = true

    protected val cfKiller by lazy { CloudflareKiller() }

    protected suspend fun ensureLiveDomain() {
        mainUrl = DomainResolver.resolve(siteKey, mainUrl)
    }

    protected suspend fun getDoc(
        url: String,
        referer: String = "${mainUrl}/",
        headers: Map<String, String> = emptyMap(),
        useCf: Boolean = false,
        params: Map<String, String> = emptyMap()
    ): Document {
        val query = params.entries.joinToString("&") { (key, value) ->
            java.net.URLEncoder.encode(key, "UTF-8") + "=" + java.net.URLEncoder.encode(value, "UTF-8")
        }
        val requestUrl = if (query.isEmpty()) url else url + (if (url.contains("?")) "&" else "?") + query
        val isSiteRequest = URI(url.replace(" ", "%20")).host == URI(mainUrl).host
        val text = getText(requestUrl, referer, headers, useCf)
        val base = if (isSiteRequest) rewriteToLiveDomain(requestUrl) else requestUrl
        return Jsoup.parse(text, base)
    }

    protected suspend fun getText(
        url: String,
        referer: String = "${mainUrl}/",
        headers: Map<String, String> = emptyMap(),
        useCf: Boolean = false
    ): String {
        val originalBase = mainUrl
        val isSiteRequest = URI(url.replace(" ", "%20")).host == URI(originalBase).host
        suspend fun fetch(target: String, ref: String): String {
            val first = if (useCf) {
                app.get(target, referer = ref, headers = headers, interceptor = cfKiller, timeout = 12)
            } else app.get(target, referer = ref, headers = headers, timeout = 12)
            val response = if (!useCf && (first.code == 403 || first.code == 503)) {
                app.get(target, referer = ref, headers = headers, interceptor = cfKiller, timeout = 12)
            } else first
            val blocked = DomainResolver.isBlockedPage(response.text)
            if (!response.isSuccessful || blocked) {
                throw SiteRequestException(
                    blocked || response.code == 403 || response.code == 408 || response.code == 429 || response.code >= 500,
                    "$name: HTTP ${response.code} or blocked page at $target"
                )
            }
            return response.text
        }
        return try {
            fetch(url, referer)
        } catch (error: Exception) {
            if (error is CancellationException || !isSiteRequest) throw error
            if (error is SiteRequestException && !error.retryDomain) throw error
            val replacement = DomainResolver.failover(siteKey, originalBase)
            if (replacement == originalBase.trimEnd('/')) throw error
            mainUrl = replacement
            val retryReferer = if (URI(referer.replace(" ", "%20")).host == URI(originalBase).host) rewriteToLiveDomain(referer) else referer
            fetch(rewriteToLiveDomain(url), retryReferer)
        }
    }

    protected fun pagedUrl(path: String, page: Int, pageSegment: String = "page"): String {
        if (page <= 1) return "${mainUrl}${path}"
        val parts = path.split("?", limit = 2)
        val base = parts[0].trimEnd('/')
        val query = if (parts.size == 2) "?${parts[1]}" else ""
        return "${mainUrl}$base/$pageSegment/$page/$query"
    }

    protected fun rewriteToLiveDomain(url: String): String {
        return try {
            val uri = URI(url.replace(" ", "%20"))
            val pathAndQuery = buildString {
                append(uri.rawPath ?: "")
                if (!uri.rawQuery.isNullOrBlank()) {
                    append("?").append(uri.rawQuery)
                }
            }
            "${mainUrl.trimEnd('/')}$pathAndQuery"
        } catch (_: Exception) {
            url
        }
    }
}
