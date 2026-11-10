package app.cleanlink

import app.cleanlink.core.HttpRedirectResolver
import app.cleanlink.core.InspectResult
import app.cleanlink.core.LinkInspector
import app.cleanlink.core.UrlExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** "Expand link": follows a link hop by hop and reports where it really leads. */
object Inspector {
    private val inspector = LinkInspector(resolver = HttpRedirectResolver())

    /** Follows the first link in [text] (or the whole text when it holds no link). Network work runs on IO. */
    suspend fun expand(text: String): InspectResult? {
        val link = UrlExtractor.extract(text).firstOrNull() ?: text.trim()
        if (link.isEmpty()) return null
        return withContext(Dispatchers.IO) { inspector.inspect(link) }
    }
}
