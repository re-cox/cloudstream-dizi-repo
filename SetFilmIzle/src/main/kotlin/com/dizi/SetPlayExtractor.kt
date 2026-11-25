package com.dizi

import android.util.Base64
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import java.net.URI

/** Resolves the public AJAX -> SetPlay bridge -> FastPlay manifest chain. */
class SetPlayExtractor : ExtractorApi() {
    override val name = "SetPlay"
    override val mainUrl = "https://setplay.sbs"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val bridge = app.get(url, referer = referer).text
        val encoded = Regex("""SPG\.cerceve\([^,]+,\s*("(?:[^"\\]|\\.)*")\s*,\s*("(?:[^"\\]|\\.)*")""")
            .find(bridge) ?: return
        fun unquote(value: String) = JSONObject("{\"v\":$value}").getString("v")
        val bytes = Base64.decode(unquote(encoded.groupValues[1]), Base64.DEFAULT)
        val key = Base64.decode(unquote(encoded.groupValues[2]), Base64.DEFAULT)
        if (key.isEmpty()) return
        val frame = bytes.mapIndexed { i, byte -> (byte.toInt() xor key[i % key.size].toInt()).toByte() }
            .toByteArray().toString(Charsets.UTF_8).substringBefore('|')
        if (!frame.startsWith("https://")) return
        val page = app.get(frame, referer = url).text
        val src = Regex("""\bsrc:\s*"([^"\n]+/master\.(?:txt|m3u8)[^"\n]*)"""")
            .find(page)?.groupValues?.get(1) ?: return
        val origin = URI(frame).let { "${it.scheme}://${it.rawAuthority}" }
        val manifest = URI(frame).resolve(src).toString()
        Regex(""""file"\s*:\s*"([^"]+\.vtt[^"\s]*)"\s*,\s*"label"\s*:\s*"([^"]+)"""")
            .findAll(page).forEach { match -> subtitleCallback(SubtitleFile(match.groupValues[2], match.groupValues[1])) }
        callback(newExtractorLink(name, name, manifest, ExtractorLinkType.M3U8) {
            this.referer = "$origin/"
            this.headers = mapOf("Origin" to origin)
            this.quality = Qualities.Unknown.value
        })
    }
}
