package app.cleanlink.core

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Result of one page request made by the caption readers. */
internal sealed interface Page {
    data class Ok(val body: String, val finalUrl: String) : Page
    /** A redirect to a login page, or HTTP 401/403. */
    data object Login : Page
    data object Failed : Page
}

/**
 * Plain GET with no cookies, no referrer and no login. Redirects are followed by hand and never
 * leave [allowedHosts]. Must be called off the main thread.
 */
internal class HttpPages(
    private val timeoutMs: Int,
    private val maxBodyBytes: Int,
    private val allowedHosts: Set<String>,
    private val userAgent: String,
    private val accept: String = "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8",
) {
    @Suppress("DEPRECATION")
    fun get(start: String, maxRedirects: Int = 3): Page {
        var url = start
        repeat(maxRedirects + 1) {
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
                conn.setRequestProperty("Accept", accept)
                conn.setRequestProperty("Accept-Language", "en")
                val code = conn.responseCode
                if (code in 300..399) {
                    val next = conn.getHeaderField("Location")?.trim().orEmpty()
                    val resolved = try { URL(URL(url), next).toString() } catch (e: Exception) { return Page.Failed }
                    if (ParsedUrl.parse(resolved)?.path.orEmpty().startsWith("/accounts/login")) return Page.Login
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
                return Page.Ok(body, url)
            } catch (e: IOException) {
                return Page.Failed
            } finally {
                conn.disconnect()
            }
        }
        return Page.Failed
    }
}
