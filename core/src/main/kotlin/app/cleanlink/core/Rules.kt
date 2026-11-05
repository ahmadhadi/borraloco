package app.cleanlink.core

/**
 * Matches a host name.
 *
 * - `"x.com"` matches `x.com` and every subdomain (`mobile.x.com`).
 * - `"amazon.*"` matches the name under any public suffix: `amazon.de`, `www.amazon.co.uk`, …
 */
class HostPattern(val pattern: String) {
    private val p = pattern.lowercase()
    private val anyTld = p.endsWith(".*")
    private val tldRegex = if (anyTld) {
        Regex("(^|\\.)" + Regex.escape(p.removeSuffix(".*")) + "\\.[a-z0-9-]+(\\.[a-z]{2,3})?$")
    } else {
        null
    }

    fun matches(host: String): Boolean =
        if (tldRegex != null) tldRegex.containsMatchIn(host) else host == p || host.endsWith(".$p")

    override fun toString() = pattern
}

/**
 * Matches a query-parameter name, case-insensitively.
 * `*` is a wildcard: `"utm_*"`, `"pf_rd_*"`, `"__cft__*"`.
 */
class ParamPattern(val pattern: String) {
    private val exact = if ('*' in pattern) null else pattern
    private val regex = if ('*' in pattern) {
        Regex(pattern.split('*').joinToString(".*") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
    } else {
        null
    }

    fun matches(name: String): Boolean =
        exact?.equals(name, ignoreCase = true) ?: regex!!.matches(name)

    override fun toString() = pattern
}

/** Tracking rules for one platform. */
class SiteRule(
    val name: String,
    hosts: List<String>,
    /** Parameters always removed on these hosts. */
    removeParams: List<String> = emptyList(),
    /**
     * Allow-list mode: when set, every parameter NOT in this set is removed.
     * Used for platforms whose links never need extra parameters (Spotify, TikTok, Temu…).
     */
    keepOnly: Set<String>? = null,
    /** Regex replacements applied to the path, e.g. Amazon's `/ref=sr_1_1`. */
    val pathRewrites: List<Pair<Regex, String>> = emptyList(),
    /** When set, the rule only applies to URLs whose path matches (e.g. product pages only). */
    val onlyOnPaths: Regex? = null,
) {
    val hosts = hosts.map(::HostPattern)
    val removeParams = removeParams.map(::ParamPattern)
    val keepOnly: Set<String>? = keepOnly?.map { it.lowercase() }?.toSet()

    fun appliesTo(host: String, path: String = "") =
        hosts.any { it.matches(host) } && (onlyOnPaths == null || onlyOnPaths.containsMatchIn(path))

    fun removes(param: String): Boolean =
        (keepOnly != null && param.lowercase() !in keepOnly) || removeParams.any { it.matches(param) }
}

/**
 * A redirect wrapper such as `l.facebook.com/l.php?u=<real url>`.
 * The real destination is taken from the first of [params] that holds an http(s) URL.
 */
class RedirectRule(
    val name: String,
    hosts: List<String>,
    pathRegex: String,
    val params: List<String>,
) {
    val hosts = hosts.map(::HostPattern)
    val path = Regex(pathRegex, RegexOption.IGNORE_CASE)

    fun unwrap(url: ParsedUrl): String? {
        if (hosts.none { it.matches(url.host) } || !path.matches(url.path)) return null
        for (name in params) {
            val target = url.param(name)?.value?.trim() ?: continue
            if (ParsedUrl.parse(target) != null) return target
        }
        return null
    }
}

/**
 * A link shortener whose real destination can only be learned by following its HTTP redirect.
 * [pathRegex] restricts it to certain paths (e.g. `snapchat.com/t/…` but not `snapchat.com/add/…`).
 */
class ShortenerRule(host: String, pathRegex: String = ".*") {
    val host = HostPattern(host)
    val path = Regex(pathRegex, RegexOption.IGNORE_CASE)

    fun matches(url: ParsedUrl) = host.matches(url.host) && path.matches(url.path)
}

class RuleSet(
    /** Parameters that are tracking everywhere (`utm_*`, `fbclid`, …). */
    globalParams: List<String>,
    val sites: List<SiteRule>,
    val redirects: List<RedirectRule>,
    val shorteners: List<ShortenerRule>,
) {
    val globalParams = globalParams.map(::ParamPattern)

    fun sitesFor(host: String, path: String = "") = sites.filter { it.appliesTo(host, path) }
    fun isShortLink(url: ParsedUrl) = shorteners.any { it.matches(url) }
    fun isKnownSite(url: ParsedUrl) = sites.any { it.appliesTo(url.host, url.path) }
}
