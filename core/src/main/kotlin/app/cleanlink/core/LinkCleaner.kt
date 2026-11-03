package app.cleanlink.core

/** The outcome of cleaning one link. */
data class CleanResult(
    val original: String,
    val cleaned: String,
    /** Names of removed parameters, plus removed path pieces such as `/ref=sr_1_1`. */
    val removed: List<String> = emptyList(),
    /** True when a redirect wrapper such as `l.facebook.com/l.php?u=…` was unwrapped. */
    val unwrapped: Boolean = false,
    /** True when a short link was expanded over the network. */
    val expanded: Boolean = false,
    /** True when the link is a short link that could not be expanded (offline, or expansion off). */
    val unexpandedShortLink: Boolean = false,
) {
    val changed: Boolean get() = original != cleaned
}

/** The outcome of cleaning every link inside a piece of text (a share message, say). */
data class TextCleanResult(
    val originalText: String,
    val cleanedText: String,
    val links: List<CleanResult>,
) {
    val changed: Boolean get() = originalText != cleanedText
    val hasLinks: Boolean get() = links.isNotEmpty()
}

class LinkCleaner(
    private val rules: RuleSet = BuiltInRules.ruleSet,
    private val resolver: RedirectResolver? = null,
) {

    fun isShortLink(url: String): Boolean = ParsedUrl.parse(url)?.let(rules::isShortLink) ?: false

    /**
     * Cleans one URL.
     *
     * Order: unwrap redirect wrappers → expand short links (only if [expandShortLinks] and a
     * resolver was given; this makes network requests, so call it off the main thread) →
     * strip tracking parameters and path pieces. Repeats while a step changes the link,
     * because a wrapper can contain a short link and vice versa.
     */
    fun clean(url: String, expandShortLinks: Boolean = false): CleanResult {
        val original = url.trim()
        var current = original
        val removed = mutableListOf<String>()
        var unwrapped = false
        var expanded = false
        val seen = mutableSetOf<String>()

        for (round in 0 until MAX_ROUNDS) {
            if (!seen.add(current)) break
            val parsed = ParsedUrl.parse(current) ?: break

            val inner = rules.redirects.firstNotNullOfOrNull { it.unwrap(parsed) }
            if (inner != null) {
                current = inner
                unwrapped = true
                continue
            }

            if (expandShortLinks && resolver != null && rules.isShortLink(parsed)) {
                val target = expand(current)
                if (target != current) {
                    current = target
                    expanded = true
                    continue
                }
            }
            break
        }

        val parsed = ParsedUrl.parse(current)
            ?: return CleanResult(original, current, removed, unwrapped, expanded)
        val cleaned = strip(parsed, removed)
        return CleanResult(
            original = original,
            cleaned = cleaned.toUrl(),
            removed = removed,
            unwrapped = unwrapped,
            expanded = expanded,
            unexpandedShortLink = rules.isShortLink(cleaned),
        )
    }

    /** Cleans every http(s) link inside [text], leaving the rest of the text untouched. */
    fun cleanText(text: String, expandShortLinks: Boolean = false): TextCleanResult {
        val links = mutableListOf<CleanResult>()
        val out = StringBuilder()
        var last = 0
        for (range in UrlExtractor.find(text)) {
            val result = clean(text.substring(range), expandShortLinks)
            links += result
            out.append(text, last, range.first).append(result.cleaned)
            last = range.last + 1
        }
        out.append(text, last, text.length)
        return TextCleanResult(text, out.toString(), links)
    }

    /** Follows redirects until the link stops redirecting or lands on a platform we know. */
    private fun expand(url: String): String {
        val resolver = resolver ?: return url
        var current = url
        val visited = mutableSetOf(url)
        repeat(MAX_HOPS) {
            val next = resolver.nextHop(current) ?: return current
            if (!visited.add(next)) return current
            current = next
            val parsed = ParsedUrl.parse(next) ?: return current
            // Stop on a known platform page: following further can land on a login wall.
            if (!rules.isShortLink(parsed) && rules.isKnownSite(parsed)) return current
            // Stop on a redirect wrapper: we can unwrap it offline.
            if (rules.redirects.any { it.unwrap(parsed) != null }) return current
        }
        return current
    }

    private fun strip(url: ParsedUrl, removed: MutableList<String>): ParsedUrl {
        val sites = rules.sitesFor(url.host)

        var path = url.path
        for (site in sites) {
            for ((regex, replacement) in site.pathRewrites) {
                regex.findAll(path).forEach { removed += it.value }
                path = regex.replace(path, replacement)
            }
        }

        val kept = url.query?.filter { param ->
            val key = param.key
            val drop = key.isEmpty() ||
                rules.globalParams.any { it.matches(key) } ||
                sites.any { it.removes(key) }
            if (drop && key.isNotEmpty()) removed += key
            !drop
        }

        // An empty `#` carries nothing; drop it.
        val fragment = url.fragment?.takeIf { it.isNotEmpty() }
        return url.copy(path = path, query = kept, fragment = fragment)
    }

    private companion object {
        const val MAX_ROUNDS = 8
        const val MAX_HOPS = 8
    }
}
