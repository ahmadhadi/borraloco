package app.cleanlink

import android.content.Context
import app.cleanlink.core.BuiltInRules
import app.cleanlink.core.HttpRedirectResolver
import app.cleanlink.core.LinkCleaner
import app.cleanlink.core.TextCleanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** App-wide access to the cleaning engine. */
object Cleaner {
    private val engine = LinkCleaner(BuiltInRules.ruleSet, HttpRedirectResolver())

    val supportedPlatforms: List<String> = BuiltInRules.ruleSet.sites.map { it.name }

    /** Cleans every link in [text]. Runs on the IO dispatcher because expansion uses the network. */
    suspend fun cleanText(context: Context, text: String): TextCleanResult {
        val expand = AppSettings(context).expandShortLinks
        return withContext(Dispatchers.IO) { engine.cleanText(text, expandShortLinks = expand) }
    }

    /** One-line summary for toasts, e.g. "Removed 3 trackers". */
    fun summary(context: Context, result: TextCleanResult): String {
        val removed = result.links.sumOf { it.removed.size }
        return when {
            !result.hasLinks -> context.getString(R.string.no_link_found)
            removed > 0 -> context.resources.getQuantityString(R.plurals.removed_trackers, removed, removed)
            result.changed -> context.getString(R.string.link_cleaned)
            else -> context.getString(R.string.already_clean)
        }
    }
}
