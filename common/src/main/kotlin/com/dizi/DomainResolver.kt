package com.dizi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.app

@JsonIgnoreProperties(ignoreUnknown = true)
data class DomainRegistry(
    @JsonProperty("updatedAt") val updatedAt: String? = null,
    @JsonProperty("sites") val sites: Map<String, SiteDomainConfig> = emptyMap()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SiteDomainConfig(
    @JsonProperty("currentUrl") val currentUrl: String = "",
    @JsonProperty("nextUrl") val nextUrl: String? = null,
    @JsonProperty("strategy") val strategy: String? = null,
    @JsonProperty("prefix") val prefix: String? = null,
    @JsonProperty("suffix") val suffix: String? = null,
    @JsonProperty("currentNumber") val currentNumber: Int? = null,
    @JsonProperty("mirrors") val mirrors: List<String> = emptyList(),
    @JsonProperty("healthCheckText") val healthCheckText: String? = null
)

/**
 * Resolves live site domains dynamically at runtime:
 * 1. Fetches `domains.json` from the GitHub repository (cached in memory for 30 min).
 * 2. Verifies `currentUrl` is alive (not BTK-blocked or parked).
 * 3. If unreachable, fails over in order:
 *    a) `nextUrl` (e.g. announced "Sonraki adresimiz ...")
 *    b) `mirrors` (known TLD rotations like .nl / .now / .la / .to)
 *    c) sequential numbers (`currentNumber + 1` .. `currentNumber + 5`)
 */
object DomainResolver {
    // Replace KULLANICI_ADIN/cloudstream-dizi-repo with your own GitHub username & repo
    private const val DOMAINS_JSON_URL =
        "https://raw.githubusercontent.com/re-cox/cloudstream-dizi-repo/main/domains.json"

    private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes
    private val mapper = jacksonObjectMapper()

    private val blockAndParkingMarkers = listOf(
        "bilgi teknolojileri ve iletişim kurumu",
        "erişime engellenmiştir",
        "5651 sayılı kanun",
        "buy this domain",
        "domain is for sale",
        "sedoparking",
        "dan.com",
        "afternic",
        "godaddy"
    )

    private val resolvedCache = mutableMapOf<String, Pair<String, Long>>()

    suspend fun resolve(siteKey: String, fallbackUrl: String): String {
        val now = System.currentTimeMillis()
        resolvedCache[siteKey]?.let { (cachedUrl, timestamp) ->
            if (now - timestamp < CACHE_TTL_MS) {
                return cachedUrl
            }
        }

        val config = fetchSiteConfig(siteKey)
        val primaryUrl = config?.currentUrl?.takeIf { it.isNotBlank() } ?: fallbackUrl

        // Fast path: check if primary URL is alive
        if (isAlive(primaryUrl, config?.healthCheckText)) {
            val clean = primaryUrl.trimEnd('/')
            resolvedCache[siteKey] = clean to now
            return clean
        }

        // Failover 1: check announced nextUrl
        val nextUrl = config?.nextUrl?.takeIf { it.isNotBlank() }
        if (nextUrl != null && isAlive(nextUrl, config.healthCheckText)) {
            val clean = nextUrl.trimEnd('/')
            resolvedCache[siteKey] = clean to now
            return clean
        }

        // Failover 2: check known mirrors / TLD rotations
        config?.mirrors?.forEach { mirror ->
            val candidate = mirror.trimEnd('/')
            if (candidate.isNotBlank() && candidate != primaryUrl.trimEnd('/') && candidate != nextUrl?.trimEnd('/')) {
                if (isAlive(candidate, config.healthCheckText)) {
                    resolvedCache[siteKey] = candidate to now
                    return candidate
                }
            }
        }

        // Failover 3: sequential probing (N+1 .. N+5) if site uses numbered domains
        val prefix = config?.prefix
        val suffix = config?.suffix
        val currentNum = config?.currentNumber ?: extractNumber(primaryUrl)
        if (!prefix.isNullOrBlank() && !suffix.isNullOrBlank() && currentNum != null) {
            for (offset in 1..5) {
                val candidate = "${prefix}${currentNum + offset}${suffix}".trimEnd('/')
                if (candidate == nextUrl?.trimEnd('/')) continue
                if (isAlive(candidate, config.healthCheckText)) {
                    resolvedCache[siteKey] = candidate to now
                    return candidate
                }
            }
        }

        val cleanFallback = primaryUrl.trimEnd('/')
        resolvedCache[siteKey] = cleanFallback to now
        return cleanFallback
    }

    fun invalidate(siteKey: String) {
        resolvedCache.remove(siteKey)
    }

    private suspend fun fetchSiteConfig(siteKey: String): SiteDomainConfig? {
        return try {
            val text = app.get(DOMAINS_JSON_URL, timeout = 10).text
            val registry = mapper.readValue<DomainRegistry>(text)
            registry.sites[siteKey]
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun isAlive(url: String, healthCheckText: String?): Boolean {
        return try {
            val response = app.get(url, timeout = 8)
            if (!response.isSuccessful) return false
            val body = response.text.lowercase()
            if (body.length < 1500) return false
            if (blockAndParkingMarkers.any { body.contains(it) }) {
                return false
            }
            if (!healthCheckText.isNullOrBlank()) {
                body.contains(healthCheckText.lowercase())
            } else {
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun extractNumber(url: String): Int? {
        return Regex("""(\d+)\.[a-zA-Z]{2,}""").find(url)?.groupValues?.get(1)?.toIntOrNull()
    }
}
