package app.cleanlink.core

import java.net.URLDecoder

/**
 * One `key=value` pair from a query string, kept exactly as it appeared (still percent-encoded),
 * so that the parameters we keep are written back byte-for-byte.
 */
class QueryParam(val raw: String) {
    val rawKey: String = raw.substringBefore('=')
    val rawValue: String? = if ('=' in raw) raw.substringAfter('=') else null

    /** Decoded key, used for matching against rules. */
    val key: String by lazy { percentDecode(rawKey) }

    /** Decoded value, or null when the parameter has no `=`. */
    val value: String? by lazy { rawValue?.let(::percentDecode) }

    override fun toString(): String = raw
}

/**
 * A small, forgiving http(s) URL parser.
 *
 * `java.net.URI` rejects many real-world shared links (unencoded `|`, spaces, `[]`, emoji…),
 * so this splits the URL into its parts with a regex and never re-encodes anything.
 */
data class ParsedUrl(
    val scheme: String,
    val authority: String,
    val path: String,
    /** null = the URL had no `?` at all. */
    val query: List<QueryParam>?,
    /** null = the URL had no `#` at all. */
    val fragment: String?,
) {
    /** Lower-cased host without user-info, port or trailing dot. */
    val host: String by lazy {
        val hostAndPort = authority.substringAfterLast('@')
        val h = if (hostAndPort.startsWith("[")) {
            hostAndPort.substringBefore(']') + "]" // IPv6 literal
        } else {
            hostAndPort.substringBefore(':')
        }
        h.lowercase().trimEnd('.')
    }

    fun param(name: String): QueryParam? = query?.firstOrNull { it.key.equals(name, ignoreCase = true) }

    fun toUrl(): String = buildString {
        append(scheme).append("://").append(authority).append(path)
        if (!query.isNullOrEmpty()) {
            append('?')
            query.joinTo(this, "&") { it.raw }
        }
        if (fragment != null) append('#').append(fragment)
    }

    override fun toString(): String = toUrl()

    companion object {
        private val URL_REGEX = Regex(
            "^([a-zA-Z][a-zA-Z0-9+.-]*)://([^/?#]*)([^?#]*)(?:\\?([^#]*))?(?:#(.*))?$",
            RegexOption.DOT_MATCHES_ALL,
        )

        /** Parses an http(s) URL. Returns null for anything else (mailto:, intent:, garbage…). */
        fun parse(input: String): ParsedUrl? {
            val m = URL_REGEX.matchEntire(input.trim()) ?: return null
            val scheme = m.groupValues[1]
            if (!scheme.equals("http", true) && !scheme.equals("https", true)) return null
            val authority = m.groupValues[2]
            if (authority.isEmpty()) return null
            val queryGroup = m.groups[4]
            val query = queryGroup?.value
                ?.split('&')
                ?.filter { it.isNotEmpty() }
                ?.map(::QueryParam)
            return ParsedUrl(
                scheme = scheme,
                authority = authority,
                path = m.groupValues[3],
                query = query,
                fragment = m.groups[5]?.value,
            )
        }
    }
}

/** Percent-decodes [s] as UTF-8, treating `+` as a space. Never throws. */
internal fun percentDecode(s: String): String {
    if ('%' !in s && '+' !in s) return s
    return try {
        URLDecoder.decode(s, Charsets.UTF_8.name())
    } catch (e: IllegalArgumentException) {
        s // malformed escape such as "%zz" – leave as-is
    }
}
