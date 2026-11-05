package app.cleanlink.core

/**
 * Recognises addresses that belong to the user's own device or network (localhost, 192.168.x.x…).
 *
 * A malicious link can redirect to such an address to make the phone poke a router or another
 * device on the local network, so redirect-following code refuses to request them.
 * Also understands the disguised IPv4 spellings browsers accept (`2130706433`, `0x7f.1`, `0177.0.0.1`).
 */
object PrivateHosts {
    private val SUFFIXES = listOf(".localhost", ".local", ".internal", ".localdomain", ".lan", ".home.arpa", ".intranet")
    private val DEC = Regex("[0-9]+")
    private val HEX = Regex("0[xX][0-9a-fA-F]+")
    private val OCT = Regex("0[0-7]+")

    fun isPrivate(host: String): Boolean {
        val h = host.trim().trim('[', ']').lowercase().trimEnd('.').substringBefore('%')
        if (h.isEmpty()) return false
        if (h == "localhost" || SUFFIXES.any { h.endsWith(it) }) return true
        if (':' in h) return privateV6(h)
        return parseIpv4(h)?.let(::privateV4) ?: false
    }

    /**
     * Parses [host] the way `inet_aton` does: 1 to 4 dot-separated parts, each decimal, `0x` hex or
     * `0` octal, the last part filling the remaining bytes. Returns the address as a 32-bit value,
     * or null when [host] is not an IPv4 address in any of those spellings.
     */
    fun parseIpv4(host: String): Long? {
        val parts = host.split('.')
        if (parts.size !in 1..4 || parts.any { it.isEmpty() }) return null
        val nums = parts.map { parsePart(it) ?: return null }
        var value = 0L
        for (i in 0 until nums.size - 1) {
            if (nums[i] > 255) return null
            value = value or (nums[i] shl (24 - 8 * i))
        }
        val lastBits = 32 - 8 * (nums.size - 1)
        if (nums.last() >= (1L shl lastBits)) return null
        return value or nums.last()
    }

    private fun parsePart(s: String): Long? = when {
        HEX.matches(s) -> s.substring(2).toLongOrNull(16)
        s.length > 1 && OCT.matches(s) -> s.substring(1).toLongOrNull(8)
        DEC.matches(s) -> s.toLongOrNull(10)
        else -> null
    }

    private fun privateV4(v: Long): Boolean {
        val a = ((v shr 24) and 0xFF).toInt()
        val b = ((v shr 16) and 0xFF).toInt()
        return a == 0 || a == 10 || a == 127 ||
            (a == 169 && b == 254) ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 100 && b in 64..127) || // carrier-grade NAT
            a >= 224 // multicast and reserved
    }

    private fun privateV6(h: String): Boolean {
        if (h == "::" || h == "::1") return true
        if (h.startsWith("::ffff:")) { // IPv4-mapped
            val tail = h.removePrefix("::ffff:")
            if (':' in tail) {
                val words = tail.split(':').map { it.toIntOrNull(16) ?: return false }
                if (words.size != 2) return false
                return privateV4((words[0].toLong() shl 16) or words[1].toLong())
            }
            return parseIpv4(tail)?.let(::privateV4) ?: false
        }
        val first = h.substringBefore(':').ifEmpty { "0" }.toIntOrNull(16) ?: return false
        return (first and 0xFE00) == 0xFC00 || // fc00::/7 unique local
            (first and 0xFFC0) == 0xFE80 // fe80::/10 link-local
    }
}
