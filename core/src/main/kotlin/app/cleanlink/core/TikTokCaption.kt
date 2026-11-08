package app.cleanlink.core

/** A TikTok link worth reading a caption from: a video page, or a short link that leads to one. */
sealed interface TikTokLink {
    /** `https://www.tiktok.com/@user/video/123`: the video page, never with tracking. */
    data class Video(val canonicalUrl: String) : TikTokLink
    /** vm.tiktok.com / vt.tiktok.com / tiktok.com/t/…: must be followed to find the video. */
    data class Short(val url: String) : TikTokLink
}

object TikTokCaptions {

    val HOSTS = setOf("tiktok.com", "www.tiktok.com", "m.tiktok.com", "vm.tiktok.com", "vt.tiktok.com")

    private val VIDEO_PATH = Regex("""^/@([A-Za-z0-9._]+)/(video|photo)/(\d{6,})/?$""")
    private val SHORT_PATH = Regex("""^/t/[A-Za-z0-9]{5,}/?$""")

    fun parseLink(link: String): TikTokLink? {
        val url = ParsedUrl.parse(link.trim()) ?: return null
        if (url.host !in HOSTS) return null
        VIDEO_PATH.matchEntire(url.path)?.let { m ->
            return TikTokLink.Video("https://www.tiktok.com/@${m.groupValues[1]}/${m.groupValues[2]}/${m.groupValues[3]}")
        }
        val shortHost = url.host == "vm.tiktok.com" || url.host == "vt.tiktok.com"
        if (shortHost && url.path.trim('/').matches(Regex("[A-Za-z0-9]{5,}"))) return TikTokLink.Short("https://${url.host}/${url.path.trim('/')}/")
        if (!shortHost && SHORT_PATH.matches(url.path)) return TikTokLink.Short("https://www.tiktok.com${url.path.trimEnd('/')}/")
        return null
    }

    /** oEmbed answer: `{"title": "<the caption>", "author_name": "…", …}`. */
    fun parseOembed(json: String): CaptionResult {
        val title = jsonString(json, "title") ?: return CaptionResult.Failed
        val caption = title.trim()
        if (caption.isEmpty()) return CaptionResult.NoCaption
        return CaptionResult.Found(caption, jsonString(json, "author_name")?.trim()?.ifEmpty { null }, truncatedMaybe = false)
    }

    /** Fallback: the page's embedded data holds the caption as `"desc"` inside `itemStruct`. */
    fun parsePage(html: String): CaptionResult {
        val item = html.indexOf("\"itemStruct\"")
        if (item < 0) return CaptionResult.Failed
        val tail = html.substring(item)
        val desc = jsonString(tail, "desc") ?: return CaptionResult.Failed
        if (desc.isBlank()) return CaptionResult.NoCaption
        return CaptionResult.Found(desc.trim(), jsonString(tail, "uniqueId"), truncatedMaybe = false)
    }

    /** First `"key":"value"` string in [json], with JSON escapes decoded. Flat lookups only. */
    internal fun jsonString(json: String, key: String): String? {
        val m = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json) ?: return null
        return unescapeJson(m.groupValues[1])
    }

    internal fun unescapeJson(s: String): String {
        if (!s.contains('\\')) return s
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\' || i + 1 >= s.length) { out.append(c); i++; continue }
            when (val n = s[i + 1]) {
                'n' -> out.append('\n'); 'r' -> out.append('\r'); 't' -> out.append('\t')
                'b' -> out.append('\b'); 'f' -> out.append('\u000C')
                '"', '\\', '/' -> out.append(n)
                'u' -> {
                    val hex = s.substring(i + 2, minOf(i + 6, s.length))
                    val cp = hex.takeIf { it.length == 4 }?.toIntOrNull(16)
                    if (cp == null) { out.append("\\u"); i += 2; continue }
                    out.append(cp.toChar()); i += 6; continue
                }
                else -> out.append(n)
            }
            i += 2
        }
        return out.toString()
    }
}
