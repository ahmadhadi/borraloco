package app.cleanlink.core

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Finds where a short link points to, one redirect hop at a time. */
fun interface RedirectResolver {
    /** Returns the URL [url] redirects to, or null if it doesn't redirect (or the request failed). */
    fun nextHop(url: String): String?
}

/**
 * Follows HTTP redirects with plain `HttpURLConnection` (available on Android and the JVM).
 *
 * Privacy notes:
 * - Sends a HEAD request first, so no page body is downloaded; falls back to GET when a
 *   server refuses HEAD, and then reads at most [maxBodyBytes] to look for a `<meta refresh>`.
 * - No cookies are stored or sent, and no referrer is sent.
 * - Must be called off the main thread.
 */
class HttpRedirectResolver(
    private val timeoutMs: Int = 6_000,
    private val maxBodyBytes: Int = 64 * 1024,
    private val userAgent: String = DEFAULT_USER_AGENT,
    /** Off by default: a link must not be able to make the phone request localhost or a LAN device. */
    private val allowPrivateHosts: Boolean = false,
) : RedirectResolver {

    override fun nextHop(url: String): String? {
        if (!allowPrivateHosts) {
            val host = ParsedUrl.parse(url)?.host ?: return null
            if (PrivateHosts.isPrivate(host)) return null
        }
        val viaHead = request(url, "HEAD")
        return when {
            viaHead is Hop.Redirect -> viaHead.location
            viaHead is Hop.Final && viaHead.code !in HEAD_UNSUPPORTED -> null
            else -> (request(url, "GET") as? Hop.Redirect)?.location
        }
    }

    private sealed interface Hop {
        data class Redirect(val location: String) : Hop
        data class Final(val code: Int) : Hop
        data object Failed : Hop
    }

    @Suppress("DEPRECATION") // URL(String) is the only parser lenient enough for shared links
    private fun request(url: String, method: String): Hop {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return Hop.Failed
        }
        return try {
            conn.instanceFollowRedirects = false
            conn.requestMethod = method
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.useCaches = false
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
            conn.setRequestProperty("Accept-Language", "en")

            val code = conn.responseCode
            val location = conn.getHeaderField("Location")
            if (code in 300..399 && !location.isNullOrBlank()) {
                return absolute(url, location)?.let { Hop.Redirect(it) } ?: Hop.Final(code)
            }
            if (method == "GET" && code in 200..299) {
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
                metaRefresh(body)?.let { target -> absolute(url, target)?.let { return Hop.Redirect(it) } }
            }
            Hop.Final(code)
        } catch (e: IOException) {
            Hop.Failed
        } finally {
            conn.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun absolute(base: String, location: String): String? = try {
        val resolved = URL(URL(base), location.trim()).toString()
        if (ParsedUrl.parse(resolved) != null) resolved else null
    } catch (e: Exception) {
        null
    }

    companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Mobile Safari/537.36"

        /** Status codes some servers return for HEAD while GET would work. */
        private val HEAD_UNSUPPORTED = setOf(400, 403, 404, 405, 500, 501)

        private val META_REFRESH = Regex(
            "<meta[^>]+http-equiv\\s*=\\s*[\"']?refresh[\"']?[^>]*content\\s*=\\s*[\"']?\\s*\\d*\\s*;?\\s*url\\s*=\\s*['\"]?([^\"'>\\s]+)",
            RegexOption.IGNORE_CASE,
        )

        internal fun metaRefresh(html: String): String? =
            META_REFRESH.find(html)?.groupValues?.get(1)?.replace("&amp;", "&")
    }
}
