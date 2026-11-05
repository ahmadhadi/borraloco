package app.cleanlink.core

enum class HopKind {
    /** The link as given. */
    START,

    /** Reached by following the server's HTTP redirect. */
    REDIRECT,

    /** Reached by reading the destination out of a wrapper link (`l.facebook.com/l.php?u=…`). */
    UNWRAPPED,
}

data class ChainHop(val url: String, val kind: HopKind)

enum class StopReason {
    /** What was given isn't an http(s) link, so there is nothing to follow. */
    NOT_A_WEB_LINK,

    /** The redirects lead back to a link already seen. */
    LOOP,

    /** More redirects than [LinkInspector.maxHops]. */
    TOO_MANY_HOPS,

    /** The next hop is an address on the user's own network; it was not requested. */
    PRIVATE_ADDRESS,
}

data class InspectResult(
    val original: String,
    /** Every address on the way, starting with [original]. */
    val hops: List<ChainHop>,
    /** Where the link ends up. */
    val finalUrl: String,
    /** [finalUrl] with trackers removed. */
    val cleanedFinal: CleanResult,
    /** Why following stopped early; null when the chain simply ended. */
    val stopped: StopReason?,
) {
    val redirected: Boolean get() = hops.size > 1
}

/**
 * Shows where a link really goes: follows every redirect (and unwraps redirect wrappers) and keeps
 * the whole chain, instead of only the end like [LinkCleaner] does.
 *
 * Makes network requests through [resolver], so call it off the main thread.
 */
class LinkInspector(
    private val rules: RuleSet = BuiltInRules.ruleSet,
    private val resolver: RedirectResolver,
    val maxHops: Int = 10,
) {
    fun inspect(url: String): InspectResult {
        val start = url.trim()
        val hops = mutableListOf(ChainHop(start, HopKind.START))
        val seen = mutableSetOf(start)
        var current = start
        var stopped: StopReason? = null
        var steps = 0

        while (true) {
            val parsed = ParsedUrl.parse(current)
            if (parsed == null) {
                if (hops.size == 1) stopped = StopReason.NOT_A_WEB_LINK
                break
            }
            if (steps++ >= maxHops) {
                stopped = StopReason.TOO_MANY_HOPS
                break
            }

            val inner = rules.redirects.firstNotNullOfOrNull { it.unwrap(parsed) }
            val next: String
            val kind: HopKind
            if (inner != null) {
                next = inner
                kind = HopKind.UNWRAPPED
            } else {
                if (PrivateHosts.isPrivate(parsed.host)) {
                    stopped = StopReason.PRIVATE_ADDRESS
                    break
                }
                next = resolver.nextHop(current) ?: break
                kind = HopKind.REDIRECT
            }

            if (!seen.add(next)) {
                stopped = StopReason.LOOP
                break
            }
            hops += ChainHop(next, kind)
            current = next
        }

        return InspectResult(start, hops, current, LinkCleaner(rules).clean(current), stopped)
    }
}
