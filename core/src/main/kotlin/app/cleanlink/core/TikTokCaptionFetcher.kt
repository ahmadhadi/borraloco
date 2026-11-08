package app.cleanlink.core

import java.net.URLEncoder

/**
 * Reads a TikTok video's caption through TikTok's public oEmbed endpoint (no login, no key).
 *
 * - Short links (vm.tiktok.com, tiktok.com/t/…) are followed by hand, only inside TikTok's hosts,
 *   to find the video; the tracking on the shared link is never sent on.
 * - If oEmbed gives nothing, the video page is read as a second chance.
 * - No cookies, no login, no referrer. Must be called off the main thread.
 */
class TikTokCaptionFetcher(
    timeoutMs: Int = 8_000,
    maxBodyBytes: Int = 1_000_000,
    private val baseUrl: String = "https://www.tiktok.com",
    allowedHosts: Set<String> = TikTokCaptions.HOSTS,
    userAgent: String = HttpRedirectResolver.DEFAULT_USER_AGENT,
) {
    private val pages = HttpPages(timeoutMs, maxBodyBytes, allowedHosts, userAgent)
    private val api = HttpPages(timeoutMs, maxBodyBytes, allowedHosts, userAgent, accept = "application/json,*/*;q=0.5")

    fun fetch(link: String): CaptionResult {
        val parsed = TikTokCaptions.parseLink(link) ?: return CaptionResult.NotAPost
        val video = when (parsed) {
            is TikTokLink.Video -> parsed
            is TikTokLink.Short -> resolve(parsed) ?: return CaptionResult.Failed
        }

        val oembed = api.get("$baseUrl/oembed?url=" + URLEncoder.encode(video.canonicalUrl, "UTF-8"))
        if (oembed is Page.Ok) {
            val r = TikTokCaptions.parseOembed(oembed.body)
            if (r !is CaptionResult.Failed) return r
        }

        val path = video.canonicalUrl.removePrefix("https://www.tiktok.com")
        val page = pages.get("$baseUrl$path")
        return if (page is Page.Ok) TikTokCaptions.parsePage(page.body) else CaptionResult.Failed
    }

    /** Follows a short link to the video it names. */
    private fun resolve(short: TikTokLink.Short): TikTokLink.Video? {
        val start = if (baseUrl == "https://www.tiktok.com") short.url else baseUrl + (ParsedUrl.parse(short.url)?.path ?: return null)
        val page = pages.get(start, maxRedirects = 4)
        val finalUrl = (page as? Page.Ok)?.finalUrl ?: return null
        return TikTokCaptions.parseLink(finalUrl) as? TikTokLink.Video
            ?: TikTokCaptions.parseLink(rewriteHost(finalUrl)) as? TikTokLink.Video
    }

    /** Only used with a test server: pretend its host is tiktok.com so the path can be recognised. */
    private fun rewriteHost(url: String): String {
        val p = ParsedUrl.parse(url) ?: return url
        return "https://www.tiktok.com${p.path}"
    }
}
