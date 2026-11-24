package com.dizi

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.nodes.Document
import java.net.URI

/**
 * Shared base class for all Turkish providers in this repository.
 * Automatically resolves live domains via [DomainResolver], supports
 * [CloudflareKiller] for CF-protected sites, and rewrites bookmarked URLs.
 */
abstract class BaseTurkishProvider : MainAPI() {
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
        useCf: Boolean = false
    ): Document {
        return if (useCf) {
            app.get(url, referer = referer, headers = headers, interceptor = cfKiller).document
        } else {
            val resp = app.get(url, referer = referer, headers = headers)
            if (resp.code == 403 || resp.code == 503) {
                app.get(url, referer = referer, headers = headers, interceptor = cfKiller).document
            } else {
                resp.document
            }
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
            val uri = URI(url)
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
