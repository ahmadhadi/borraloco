package app.cleanlink.core

/** Compares dotted version names such as "1.3", "v1.10" or "2.0.1-beta". */
object Versions {

    /** True when [remote] is a higher version than [local]. Unparseable input is never newer. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = parts(remote) ?: return false
        val l = parts(local) ?: return false
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        // Same numbers: a final release is newer than its own pre-release ("1.10" > "1.10-beta1"),
        // and a later pre-release is newer than an earlier one ("1.10-beta2" > "1.10-beta1").
        val rPre = preRelease(remote)
        val lPre = preRelease(local)
        return when {
            lPre != null && rPre == null -> true
            lPre != null && rPre != null -> (trailingNumber(rPre) ?: 0) > (trailingNumber(lPre) ?: 0)
            else -> false
        }
    }

    private fun preRelease(version: String): String? =
        version.trim().substringBefore('+').substringAfter('-', "").ifEmpty { null }

    private fun trailingNumber(s: String): Int? = Regex("""(\d+)$""").find(s)?.groupValues?.get(1)?.toIntOrNull()

    private fun parts(version: String): List<Int>? {
        val core = version.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
        if (core.isEmpty()) return null
        return core.split('.').map { it.toIntOrNull() ?: return null }
    }
}
