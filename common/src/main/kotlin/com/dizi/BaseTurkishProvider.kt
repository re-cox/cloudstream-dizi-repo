package com.dizi

import com.lagradost.cloudstream3.MainAPI
import java.net.URI

/**
 * Shared base class for all Turkish providers in this repository.
 * Automatically resolves live domains via [DomainResolver] and rewrites
 * bookmarked/historical URLs to the currently active domain.
 */
abstract class BaseTurkishProvider : MainAPI() {
    abstract val siteKey: String

    override var lang = "tr"
    override val hasMainPage = true

    protected suspend fun ensureLiveDomain() {
        mainUrl = DomainResolver.resolve(siteKey, mainUrl)
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
