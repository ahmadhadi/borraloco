package app.cleanlink.core

/** An Instagram post or reel, reduced to the part that identifies it (the shortcode). */
data class InstagramPost(val shortcode: String) {
    /** The address to ask for: just the post, none of the tracking that came with the shared link. */
    val canonicalPath: String get() = "/p/$shortcode/"
}

sealed interface CaptionResult {
    /** [truncatedMaybe] is true when the text came from a preview that Instagram may have shortened. */
    data class Found(val caption: String, val author: String?, val truncatedMaybe: Boolean) : CaptionResult
    data object NoCaption : CaptionResult
    /** Instagram asked for a login (private post, or it refuses anonymous requests). Never worked around. */
    data object LoginRequired : CaptionResult
    data object NotAPost : CaptionResult
    data object Failed : CaptionResult
}

/** Pure parsing for "copy the caption": recognises post links and reads captions out of Instagram's public pages. */
object InstagramCaptions {

    val HOSTS = setOf("instagram.com", "www.instagram.com", "instagr.am", "www.instagr.am", "m.instagram.com")

    private val POST_PATH = Regex("""^(?:/[A-Za-z0-9._]+)?/(?:p|reel|reels|tv)/([A-Za-z0-9_-]{5,})/?$""")

    /** Returns the post a shared link points to, or null for anything else (profiles, stories, other sites). */
    fun parsePost(link: String): InstagramPost? {
        val url = ParsedUrl.parse(link.trim()) ?: return null
        if (url.host !in HOSTS) return null
        val code = POST_PATH.matchEntire(url.path)?.groupValues?.get(1) ?: return null
        return InstagramPost(code)
    }

    /** Reads the caption out of a post page (or its /embed/captioned/ page). */
    fun parsePage(html: String): CaptionResult {
        val title = metaContent(html, "og:title")
        val description = metaContent(html, "og:description")

        if (isLoginWall(html, title, description)) return CaptionResult.LoginRequired

        if (description != null) {
            captionFromPreview(description)?.let { (caption, author) -> return CaptionResult.Found(caption, author, truncatedMaybe = true) }
        }
        if (title != null) {
            captionFromPreview(title)?.let { (caption, author) -> return CaptionResult.Found(caption, author, truncatedMaybe = true) }
        }
        embeddedCaption(html)?.let { (caption, author) -> return CaptionResult.Found(caption, author, truncatedMaybe = false) }

        // A real post page with a preview but nothing quoted in it: the post has no caption.
        return if (description != null || title != null) CaptionResult.NoCaption else CaptionResult.Failed
    }

    private fun isLoginWall(html: String, title: String?, description: String?): Boolean {
        if (title != null && Regex("""^\s*log\s?in\b""", RegexOption.IGNORE_CASE).containsMatchIn(title)) return true
        if (description != null && Regex("""log\s?(?:in|into) to instagram|create an account or log in""", RegexOption.IGNORE_CASE).containsMatchIn(description)) return true
        if (title == null && description == null && Regex("""/accounts/login""").containsMatchIn(html)) return true
        return false
    }

    /**
     * Previews look like:
     *   1,234 likes, 56 comments - user on March 3, 2024: "the caption".
     *   user on Instagram: "the caption"
     * The caption is what sits between the first `: "` and the last quote mark (it may contain quotes itself).
     */
    private fun captionFromPreview(text: String): Pair<String, String?>? {
        val q = text.indexOf(": \"")
        if (q < 0) return null
        val end = text.lastIndexOf('"')
        if (end <= q + 3) return null
        val caption = text.substring(q + 3, end).trim()
        if (caption.isEmpty()) return null
        val author = Regex("""(?:^|\s-\s)([A-Za-z0-9._]+) on [^:"]*$""").find(text.substring(0, q))?.groupValues?.get(1)
        return caption to author
    }

    /** The /embed/captioned/ page carries the whole caption in `<div class="Caption">`. */
    private fun embeddedCaption(html: String): Pair<String, String?>? {
        val start = html.indexOf("class=\"Caption\"")
        if (start < 0) return null
        val open = html.indexOf('>', start)
        if (open < 0) return null
        val nested = html.indexOf("<div", open)
        var block = html.substring(open + 1, if (nested < 0) html.length else nested)
        val end = block.indexOf("</div>")
        if (end >= 0) block = block.substring(0, end)
        val author = Regex("""<a[^>]*class="CaptionUsername"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).find(block)
            ?.groupValues?.get(1)?.let { decodeEntities(it.replace(Regex("<[^>]*>"), "")).trim() }
        val text = block
            .replace(Regex("""<a[^>]*class="CaptionUsername"[^>]*>.*?</a>""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]*>"), "")
        val caption = decodeEntities(text).lines().joinToString("\n") { it.trimEnd() }.trim()
        return if (caption.isEmpty()) null else caption to author?.ifEmpty { null }
    }

    /** `content` of `<meta property="[name]" …>` (or name=), HTML-entities decoded. */
    internal fun metaContent(html: String, name: String): String? {
        val tag = Regex("""<meta\b(?:[^>"']|"[^"]*"|'[^']*')*>""", RegexOption.IGNORE_CASE)
        val key = Regex("""\b(?:property|name)\s*=\s*["']${Regex.escape(name)}["']""", RegexOption.IGNORE_CASE)
        val content = Regex("""\bcontent\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
        for (m in tag.findAll(html)) {
            if (!key.containsMatchIn(m.value)) continue
            val c = content.find(m.value) ?: continue
            return decodeEntities(c.groupValues[1].ifEmpty { c.groupValues[2] })
        }
        return null
    }

    private val NAMED = mapOf("quot" to "\"", "amp" to "&", "lt" to "<", "gt" to ">", "apos" to "'", "nbsp" to " ")

    internal fun decodeEntities(s: String): String {
        if (!s.contains('&')) return s
        return Regex("""&(#x[0-9a-fA-F]+|#[0-9]+|[A-Za-z]+);""").replace(s) { m ->
            val body = m.groupValues[1]
            when {
                body.startsWith("#x") || body.startsWith("#X") -> codePoint(body.substring(2).toIntOrNull(16), m.value)
                body.startsWith("#") -> codePoint(body.substring(1).toIntOrNull(), m.value)
                else -> NAMED[body.lowercase()] ?: m.value
            }
        }
    }

    private fun codePoint(cp: Int?, original: String): String =
        if (cp != null && Character.isValidCodePoint(cp)) String(Character.toChars(cp)) else original
}
