package app.cleanlink.core

/**
 * Reads the public caption of an Instagram post.
 *
 * - Only the clean post address is requested: the tracking that came with a shared link is never sent.
 * - No cookies, no login, no referrer. If Instagram answers with a login page the result is
 *   [CaptionResult.LoginRequired]; nothing tries to get around that.
 * - Asks the way link-preview crawlers do, because that is the form in which Instagram publishes a
 *   post's caption in its page header. The `/embed/captioned/` page is tried when that has no caption.
 * - Redirects are followed by hand and only inside [allowedHosts].
 * - Must be called off the main thread.
 */
class InstagramCaptionFetcher(
    timeoutMs: Int = 8_000,
    maxBodyBytes: Int = 1_000_000,
    private val baseUrl: String = "https://www.instagram.com",
    allowedHosts: Set<String> = InstagramCaptions.HOSTS,
    userAgent: String = PREVIEW_USER_AGENT,
) {
    private val http = HttpPages(timeoutMs, maxBodyBytes, allowedHosts, userAgent)

    fun fetch(link: String): CaptionResult {
        val post = InstagramCaptions.parsePost(link) ?: return CaptionResult.NotAPost

        val main = http.get("$baseUrl${post.canonicalPath}")
        val parsed = when (main) {
            is Page.Ok -> InstagramCaptions.parsePage(main.body)
            Page.Login -> CaptionResult.LoginRequired
            Page.Failed -> CaptionResult.Failed
        }
        if (parsed is CaptionResult.Found) return parsed

        // Second chance: the embed page (full caption), unless the first page already said "private".
        if (parsed !is CaptionResult.LoginRequired) {
            val embed = http.get("$baseUrl${post.canonicalPath}embed/captioned/")
            if (embed is Page.Ok) {
                val e = InstagramCaptions.parsePage(embed.body)
                if (e is CaptionResult.Found) return e
            }
        }
        return parsed
    }

    companion object {
        const val PREVIEW_USER_AGENT = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uagent.php)"
    }
}
