package com.dizi

import org.json.JSONArray

/** Reads JSON containers from streamed Next.js data without executing page scripts. */
object PageJson {
    fun rsc(html: String): String = Regex("""self\.__next_f\.push\(\[1,\s*("(?:[^"\\]|\\.)*")""")
        .findAll(html).joinToString("") { match ->
            runCatching { JSONArray("[${match.groupValues[1]}]").getString(0) }.getOrDefault("")
        }

    fun arrays(text: String, key: String): List<JSONArray> =
        Regex("\"${Regex.escape(key)}\"\\s*:").findAll(text).mapNotNull { match ->
            container(text, match.range.last + 1)?.takeIf { it.startsWith('[') }
                ?.let { runCatching { JSONArray(it) }.getOrNull() }
        }.toList()

    fun container(text: String, from: Int): String? {
        var start = from
        while (start < text.length && text[start].isWhitespace()) start++
        if (start >= text.length || (text[start] != '[' && text[start] != '{')) return null
        var depth = 0
        var quoted = false
        var escaped = false
        for (i in start until text.length) {
            val char = text[i]
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '[', '{' -> depth++
                ']', '}' -> if (--depth == 0) return text.substring(start, i + 1)
            }
        }
        return null
    }
}
