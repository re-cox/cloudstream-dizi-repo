package com.dizi

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
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
    val requiredMarkers: List<String> = emptyList(),
    val allowedRedirectHosts: List<String> = emptyList(),
    val healthCheckText: String? = null
)

/**
 * Fast, Android-native (zero Jackson reflection) domain resolver:
 * 1. Caches `domains.json` for 30 minutes.
 * 2. Immediately returns `currentUrl` from `domains.json` without blocking startup
 *    on slow mirror probes. Homepage health is tracked separately from playback.
 * 3. Provides `failover()` only when a real request fails, interleaving mirrors and sequential candidates under a time budget.
 */
object DomainResolver {
    private const val DOMAINS_JSON_URL =
        "https://raw.githubusercontent.com/re-cox/cloudstream-dizi-repo/main/domains.json"

    private const val CACHE_TTL_MS = 30 * 60 * 1000L

    @Volatile
    private var registryCache: Map<String, SiteDomainConfig> = emptyMap()

    @Volatile
    private var registryFetchedAt: Long = 0L

    private val registryMutex = Mutex()
    private var preferences: SharedPreferences? = null
    private var registryAvailable = false
    fun initialize(context: Context) {
        preferences = context.applicationContext.getSharedPreferences("dizi-domain-cache-v2", Context.MODE_PRIVATE)
    }

    private val activeUrlOverrides = ConcurrentHashMap<String, Pair<String, Long>>()

    suspend fun resolve(siteKey: String, fallbackUrl: String): String {
        activeUrlOverrides[siteKey]?.let { (url, savedAt) ->
            if (System.currentTimeMillis() - savedAt < CACHE_TTL_MS) return url
            activeUrlOverrides.remove(siteKey)
        }

        val config = getSiteConfig(siteKey)
        val saved = preferences?.getString(siteKey, null)
        val savedAt = preferences?.getLong(siteKey + ".at", 0L) ?: 0L
        if (!registryAvailable && saved != null && validOrigin(saved) != null && System.currentTimeMillis() - savedAt < 7 * 24 * 60 * 60 * 1000L && savedAt > 0) return saved
        val url = config?.currentUrl?.takeIf { validOrigin(it) != null } ?: fallbackUrl
        return url.trimEnd('/')
    }

    suspend fun failover(siteKey: String, failedUrl: String): String {
        activeUrlOverrides.remove(siteKey)
        val config = getSiteConfig(siteKey) ?: return failedUrl.trimEnd('/')
        val cleanFailed = failedUrl.trimEnd('/')

        val candidates = candidateUrls(config, cleanFailed)
        val live = withTimeoutOrNull(DomainPolicy.runtimeBudgetMs) {
            candidates.distinct().firstNotNullOfOrNull { candidate -> probeOrigin(candidate, config) }
        }
        if (live != null) {
            activeUrlOverrides[siteKey] = live to System.currentTimeMillis()
            preferences?.edit()?.putString(siteKey, live)?.putLong(siteKey + ".at", System.currentTimeMillis())?.apply()
            return live
        }
        return cleanFailed
    }

    internal fun candidateUrls(config: SiteDomainConfig, cleanFailed: String): List<String> {
        val candidates = mutableListOf<String>()
        config.nextUrl?.trimEnd('/')?.takeIf { it.isNotBlank() && it != cleanFailed }?.let {
            candidates.add(it)
        }
        val sequential = mutableListOf<String>()
        val currentNum = config.currentNumber ?: extractNumber(cleanFailed)
        if (!config.prefix.isNullOrBlank() && !config.suffix.isNullOrBlank() && currentNum != null) {
            for (offset in 1..3) {
                val seq = "${config.prefix}${currentNum + offset}${config.suffix}".trimEnd('/')
                if (seq != cleanFailed && seq !in candidates) {
                    sequential.add(seq)
                }
            }
        }

        for (i in 0 until maxOf(config.mirrors.size, sequential.size)) {
            config.mirrors.getOrNull(i)?.let { if (it != cleanFailed) candidates.add(it) }
            sequential.getOrNull(i)?.let { candidates.add(it) }
        }
        return candidates.distinct()
    }

    private suspend fun getSiteConfig(siteKey: String): SiteDomainConfig? = registryMutex.withLock {
        val now = System.currentTimeMillis()
        if (registryCache.isNotEmpty() && now - registryFetchedAt < CACHE_TTL_MS) {
            return@withLock registryCache[siteKey]
        }

        try {
            val text = try { app.get(DOMAINS_JSON_URL, timeout = 6).text.also { registryAvailable = true } } catch (error: Exception) {
                if (error is CancellationException) throw error
                registryAvailable = false
                preferences?.getString("registry", null) ?: throw error
            }
            val root = JSONObject(text)
            val sitesObj = root.optJSONObject("sites") ?: return@withLock registryCache[siteKey]
            val parsed = mutableMapOf<String, SiteDomainConfig>()
            fun strings(obj: JSONObject, key: String): List<String> {
                val a = obj.optJSONArray(key) ?: return emptyList()
                return (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
            }
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
                    currentUrl = validOrigin(obj.optString("currentUrl", "")) ?: continue,
                    nextUrl = obj.optString("nextUrl", "").takeIf { it.isNotBlank() && it != "null" },
                    prefix = obj.optString("prefix", "").takeIf { it.isNotBlank() && it != "null" },
                    suffix = obj.optString("suffix", "").takeIf { it.isNotBlank() && it != "null" },
                    currentNumber = if (obj.has("currentNumber") && !obj.isNull("currentNumber")) {
                        obj.optInt("currentNumber")
                    } else null,
                    mirrors = mirrorsList.mapNotNull { validOrigin(it) },
                    requiredMarkers = strings(obj, "requiredMarkers"),
                    allowedRedirectHosts = strings(obj, "allowedRedirectHosts"),
                    healthCheckText = obj.optString("healthCheckText", "").takeIf { it.isNotBlank() && it != "null" }
                )
            }
            if (parsed.isNotEmpty()) {
                registryCache = parsed
                registryFetchedAt = now
                preferences?.edit()?.putString("registry", text)?.apply()
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Keep existing cache or fallback
        }

        return@withLock registryCache[siteKey]
    }

    fun isBlockedPage(text: String): Boolean {
        val body = text.lowercase()
        return DomainPolicy.blockedMarkers.any { body.contains(it) }
    }

    fun validOrigin(url: String): String? = try {
        val uri = URI(url)
        val host = uri.host?.lowercase() ?: ""
        if (uri.scheme !in listOf("http", "https") || uri.userInfo != null || uri.port != -1 ||
            !host.contains('.') || host.matches(Regex("[0-9.]+")) || host.endsWith(".local") || host.endsWith(".internal")) null
        else "${uri.scheme}://$host"
    } catch (_: Exception) { null }

    private suspend fun probeOrigin(url: String, config: SiteDomainConfig): String? {
        return try {
            if (validOrigin(url) == null) return null
            val response = app.get(url, timeout = DomainPolicy.probeTimeoutSeconds.toLong())
            if (!response.isSuccessful) return null
            val body = response.text.lowercase()
            if (body.length < DomainPolicy.minBodyLength || isBlockedPage(body)) return null
            if (!config.healthCheckText.isNullOrBlank() && !body.contains(config.healthCheckText.lowercase())) return null
            if (config.requiredMarkers.any { !body.contains(it.lowercase()) }) return null
            val finalUri = URI(response.url)
            if (finalUri.rawQuery != null || finalUri.rawFragment != null || !finalUri.path.isNullOrBlank() && finalUri.path != "/") return null
            val origin = validOrigin(response.url) ?: return null
            val known = listOfNotNull(url, config.currentUrl, config.nextUrl) + config.mirrors
            if (known.none { validOrigin(it) == origin } && finalUri.host !in config.allowedRedirectHosts) return null
            origin
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            null
        }
    }

    private fun extractNumber(url: String): Int? {
        return Regex("""(\d+)\.[a-zA-Z]{2,}""").find(url)?.groupValues?.get(1)?.toIntOrNull()
    }
}
