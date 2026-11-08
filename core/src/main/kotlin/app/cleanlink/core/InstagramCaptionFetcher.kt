package app.cleanlink.core

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

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
    private val timeoutMs: Int = 8_000,
    private val maxBodyBytes: Int = 1_000_000,
    private val baseUrl: String = "https://www.instagram.com",
    private val allowedHosts: Set<String> = InstagramCaptions.HOSTS,
    private val userAgent: String = PREVIEW_USER_AGENT,
) {
    fun fetch(link: String): CaptionResult {
        val post = InstagramCaptions.parsePost(link) ?: return CaptionResult.NotAPost

        val main = get("$baseUrl${post.canonicalPath}")
        val parsed = when (main) {
            is Page.Ok -> InstagramCaptions.parsePage(main.html)
            Page.Login -> CaptionResult.LoginRequired
            Page.Failed -> CaptionResult.Failed
        }
        if (parsed is CaptionResult.Found) return parsed

        // Second chance: the embed page (full caption), unless the first page already said "private".
        if (parsed !is CaptionResult.LoginRequired) {
            val embed = get("$baseUrl${post.canonicalPath}embed/captioned/")
            if (embed is Page.Ok) {
                val e = InstagramCaptions.parsePage(embed.html)
                if (e is CaptionResult.Found) return e
            }
        }
        return parsed
    }

    private sealed interface Page {
        data class Ok(val html: String) : Page
        data object Login : Page
        data object Failed : Page
    }

    @Suppress("DEPRECATION")
    private fun get(start: String): Page {
        var url = start
        repeat(MAX_REDIRECTS + 1) {
            val host = ParsedUrl.parse(url)?.host ?: return Page.Failed
            if (host !in allowedHosts) return Page.Failed
            val conn = try {
                URL(url).openConnection() as HttpURLConnection
            } catch (e: Exception) {
                return Page.Failed
            }
            try {
                conn.instanceFollowRedirects = false
                conn.requestMethod = "GET"
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.useCaches = false
                conn.setRequestProperty("User-Agent", userAgent)
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                conn.setRequestProperty("Accept-Language", "en")
                val code = conn.responseCode
                if (code in 300..399) {
                    val next = conn.getHeaderField("Location")?.trim().orEmpty()
                    val resolved = try { URL(URL(url), next).toString() } catch (e: Exception) { return Page.Failed }
                    if (Regex("""^/accounts/login""").containsMatchIn(ParsedUrl.parse(resolved)?.path.orEmpty())) return Page.Login
                    url = resolved
                    return@repeat
                }
                if (code == 401 || code == 403) return Page.Login
                if (code !in 200..299) return Page.Failed
                val body = conn.inputStream.use { input ->
                    val buf = ByteArray(maxBodyBytes)
                    var read = 0
                    while (read < buf.size) {
                        val n = input.read(buf, read, buf.size - read)
                        if (n < 0) break
                        read += n
                    }
                    String(buf, 0, read, Charsets.UTF_8)
                }
                return Page.Ok(body)
            } catch (e: IOException) {
                return Page.Failed
            } finally {
                conn.disconnect()
            }
        }
        return Page.Failed
    }

    companion object {
        const val PREVIEW_USER_AGENT = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uagent.php)"
        private const val MAX_REDIRECTS = 3
    }
}
