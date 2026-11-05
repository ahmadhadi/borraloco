package app.cleanlink

import android.content.Context
import app.cleanlink.core.CloudflareMalwareDns
import app.cleanlink.core.HttpRedirectResolver
import app.cleanlink.core.InspectResult
import app.cleanlink.core.LinkInspector
import app.cleanlink.core.ParsedUrl
import app.cleanlink.core.SafetyChecker
import app.cleanlink.core.SafetyReport
import app.cleanlink.core.UrlExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What "Inspect" found out about one link. */
data class InspectOutcome(
    val chain: InspectResult,
    val safety: SafetyReport,
    /** True when the site names were also looked up with the opt-in online check. */
    val checkedOnline: Boolean,
)

/** Expands a link hop by hop and runs the safety checks on it. */
object Inspector {
    private val inspector = LinkInspector(resolver = HttpRedirectResolver())
    private val checker = SafetyChecker()
    private val dns = CloudflareMalwareDns()

    /** Looks at the first link in [text] (or at the whole text when it holds no link). Network work runs on IO. */
    suspend fun inspect(context: Context, text: String): InspectOutcome? {
        val link = UrlExtractor.extract(text).firstOrNull() ?: text.trim()
        if (link.isEmpty()) return null
        val online = AppSettings(context).onlineSafetyCheck
        return withContext(Dispatchers.IO) {
            val chain = inspector.inspect(link)
            val start = chain.hops.first().url
            val blocked = online && listOf(start, chain.finalUrl).distinct().any { url ->
                ParsedUrl.parse(url)?.host?.let { dns.isBlocked(it) } == true
            }
            val findings = (checker.check(start, blocked).findings + checker.check(chain.finalUrl, blocked).findings)
                .distinctBy { it.code }
            InspectOutcome(chain, SafetyReport(chain.finalUrl, findings), online)
        }
    }
}
