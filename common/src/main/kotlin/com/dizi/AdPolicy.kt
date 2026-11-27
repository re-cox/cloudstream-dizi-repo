package com.dizi

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/** Explicit ad URLs only. Never classify a signed token, duration or film title as an ad. */
object AdPolicy {
    fun isAdvertisingUrl(url: String): Boolean = runCatching {
        val uri = URI(url.replace("&amp;", "&").replace("\\/", "/"))
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        if (AdPolicyData.hostSuffixes.any { host == it || host.endsWith(".$it") } ||
            host.split('.').any { it in AdPolicyData.hostLabels }) return true
        val path = (uri.path ?: "").lowercase(Locale.ROOT)
        val tokens = path.split(Regex("[/_.-]+"))
        if (AdPolicyData.pathTokens.any { it in tokens || (it.contains('-') && path.split('/').any { p -> p == it || p.startsWith("$it.") }) }) return true
        uri.rawQuery.orEmpty().split('&').any { pair ->
            val parts = pair.split('=', limit = 2)
            val key = URLDecoder.decode(parts[0], "UTF-8").lowercase(Locale.ROOT)
            val value = URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8").lowercase(Locale.ROOT)
            (key in AdPolicyData.queryFlags && value in listOf("1", "true", "yes")) ||
                (key in listOf("type", "media_type", "content_type") && value in AdPolicyData.queryTypes)
        }
    }.getOrDefault(false)
}
