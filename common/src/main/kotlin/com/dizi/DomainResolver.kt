package com.dizi

import com.lagradost.cloudstream3.app
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CancellationException

data class SiteDomainConfig(
    val currentUrl: String = "",
    val nextUrl: String? = null,
    val prefix: String? = null,
    val suffix: String? = null,
    val currentNumber: Int? = null,
    val mirrors: List<String> = emptyList(),
    val healthCheckText: String? = null
)

/**
 * Fast, Android-native (zero Jackson reflection) domain resolver:
 * 1. Caches `domains.json` for 30 minutes.
 * 2. Immediately returns `currentUrl` from `domains.json` without blocking startup
 *    on slow mirror probes (GitHub Actions already verified the live domain).
 * 3. Provides `failover()` only when a real request fails, trying `nextUrl` -> `mirrors` -> `N+1..N+3`.
 */
object DomainResolver {
    private const val DOMAINS_JSON_URL =
        "https://raw.githubusercontent.com/re-cox/cloudstream-dizi-repo/main/domains.json"

    private const val CACHE_TTL_MS = 30 * 60 * 1000L

    @Volatile
    private var registryCache: Map<String, SiteDomainConfig> = emptyMap()

    @Volatile
    private var registryFetchedAt: Long = 0L

    private val activeUrlOverrides = ConcurrentHashMap<String, Pair<String, Long>>()

    suspend fun resolve(siteKey: String, fallbackUrl: String): String {
        activeUrlOverrides[siteKey]?.let { (url, savedAt) ->
            if (System.currentTimeMillis() - savedAt < CACHE_TTL_MS) return url
            activeUrlOverrides.remove(siteKey)
        }

        val config = getSiteConfig(siteKey)
        val url = config?.currentUrl?.takeIf { it.isNotBlank() } ?: fallbackUrl
        return url.trimEnd('/')
    }

    suspend fun failover(siteKey: String, failedUrl: String): String {
        activeUrlOverrides.remove(siteKey)
        val config = getSiteConfig(siteKey) ?: return failedUrl.trimEnd('/')
        val cleanFailed = failedUrl.trimEnd('/')

        val candidates = mutableListOf<String>()
        config.nextUrl?.trimEnd('/')?.takeIf { it.isNotBlank() && it != cleanFailed }?.let {
            candidates.add(it)
        }
        config.mirrors.forEach { m ->
            val clean = m.trimEnd('/')
            if (clean.isNotBlank() && clean != cleanFailed && clean !in candidates) {
                candidates.add(clean)
            }
        }
        val currentNum = config.currentNumber ?: extractNumber(cleanFailed)
        if (!config.prefix.isNullOrBlank() && !config.suffix.isNullOrBlank() && currentNum != null) {
            for (offset in 1..3) {
                val seq = "${config.prefix}${currentNum + offset}${config.suffix}".trimEnd('/')
                if (seq != cleanFailed && seq !in candidates) {
                    candidates.add(seq)
                }
            }
        }

        for (candidate in candidates.take(4)) {
            val live = probeOrigin(candidate, config.healthCheckText)
            if (live != null) {
                activeUrlOverrides[siteKey] = live to System.currentTimeMillis()
                return live
            }
        }
        return cleanFailed
    }

    private suspend fun getSiteConfig(siteKey: String): SiteDomainConfig? {
        val now = System.currentTimeMillis()
        if (registryCache.isNotEmpty() && now - registryFetchedAt < CACHE_TTL_MS) {
            return registryCache[siteKey]
        }

        try {
            val text = app.get(DOMAINS_JSON_URL, timeout = 6).text
            val root = JSONObject(text)
            val sitesObj = root.optJSONObject("sites") ?: return registryCache[siteKey]
            val parsed = mutableMapOf<String, SiteDomainConfig>()
            val keys = sitesObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = sitesObj.optJSONObject(key) ?: continue
                val mirrorsList = mutableListOf<String>()
                val mirrorsArr = obj.optJSONArray("mirrors")
                if (mirrorsArr != null) {
                    for (i in 0 until mirrorsArr.length()) {
                        val item = mirrorsArr.optString(i)
                        if (item.isNotBlank()) mirrorsList.add(item)
                    }
                }
                parsed[key] = SiteDomainConfig(
                    currentUrl = obj.optString("currentUrl", ""),
                    nextUrl = obj.optString("nextUrl", "").takeIf { it.isNotBlank() && it != "null" },
                    prefix = obj.optString("prefix", "").takeIf { it.isNotBlank() && it != "null" },
                    suffix = obj.optString("suffix", "").takeIf { it.isNotBlank() && it != "null" },
                    currentNumber = if (obj.has("currentNumber") && !obj.isNull("currentNumber")) {
                        obj.optInt("currentNumber")
                    } else null,
                    mirrors = mirrorsList,
                    healthCheckText = obj.optString("healthCheckText", "").takeIf { it.isNotBlank() && it != "null" }
                )
            }
            if (parsed.isNotEmpty()) {
                registryCache = parsed
                registryFetchedAt = now
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Keep existing cache or fallback
        }

        return registryCache[siteKey]
    }

    fun isBlockedPage(text: String): Boolean {
        val body = text.lowercase()
        return listOf(
            "erişime engellenmiştir", "5651 sayılı kanun", "sedoparking",
            "buy this domain", "domain is for sale", "cf-chl-",
            "<title>just a moment", "<title>access denied"
        ).any { body.contains(it) }
    }

    private suspend fun probeOrigin(url: String, healthCheckText: String?): String? {
        return try {
            val response = app.get(url, timeout = 4)
            if (!response.isSuccessful) return null
            val body = response.text.lowercase()
            if (body.length < 1500 || isBlockedPage(body)) return null
            if (!healthCheckText.isNullOrBlank() && !body.contains(healthCheckText.lowercase())) return null
            val finalUri = URI(response.url)
            "${finalUri.scheme}://${finalUri.rawAuthority}"
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            null
        }
    }

    private fun extractNumber(url: String): Int? {
        return Regex("""(\d+)\.[a-zA-Z]{2,}""").find(url)?.groupValues?.get(1)?.toIntOrNull()
    }
}
