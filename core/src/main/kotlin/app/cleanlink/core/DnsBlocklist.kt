package app.cleanlink.core

import java.io.IOException
import java.net.HttpURLConnection
import java.net.IDN
import java.net.URL
import java.net.URLEncoder

/** Asks an outside service whether a website name is known to be dangerous. */
fun interface DnsBlocklist {
    /** True = flagged, false = not flagged, null = couldn't find out (offline, error, not a name). */
    fun isBlocked(host: String): Boolean?
}

/**
 * Asks Cloudflare's malware-blocking DNS (`security.cloudflare-dns.com`) about a website name.
 * Only the site NAME is sent, never the path or query of the link. Cloudflare still sees the
 * request come from the user's connection, which is why the app makes this opt-in.
 *
 * Cloudflare answers `0.0.0.0` (or an "Extended DNS Error": blocked / censored / filtered) for names
 * on its malware list. Not being on that list says nothing about a site being safe.
 */
class CloudflareMalwareDns(
    private val endpoint: String = "https://security.cloudflare-dns.com/dns-query",
    private val timeoutMs: Int = 6_000,
) : DnsBlocklist {

    override fun isBlocked(host: String): Boolean? {
        val name = queryName(host) ?: return null
        val body = fetch("$endpoint?name=${URLEncoder.encode(name, "UTF-8")}&type=A") ?: return null
        return parseBlocked(body)
    }

    @Suppress("DEPRECATION") // URL(String): same lenient parsing used elsewhere
    private fun fetch(url: String): String? {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return null
        }
        return try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/dns-json")
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            null
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** The name to ask about, or null for things that are not public website names (IPs, localhost…). */
        internal fun queryName(host: String): String? {
            val h = host.trim().trimEnd('.').lowercase()
            if ('.' !in h || ':' in h || h.startsWith("[")) return null
            if (PrivateHosts.isPrivate(h) || PrivateHosts.parseIpv4(h) != null) return null
            return try {
                IDN.toASCII(h)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        private val SINKHOLE = Regex("\"data\"\\s*:\\s*\"(0\\.0\\.0\\.0|::)\"")
        // RFC 8914 Extended DNS Errors: 15 Blocked, 16 Censored, 17 Filtered.
        private val EDE_BLOCKED = Regex("EDE\\((15|16|17)\\)")

        /** Reads a DNS-over-HTTPS JSON answer: true when the name was blocked. */
        internal fun parseBlocked(json: String): Boolean =
            SINKHOLE.containsMatchIn(json) || EDE_BLOCKED.containsMatchIn(json)
    }
}
