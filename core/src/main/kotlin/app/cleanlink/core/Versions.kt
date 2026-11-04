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
        return false
    }

    private fun parts(version: String): List<Int>? {
        val core = version.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
        if (core.isEmpty()) return null
        return core.split('.').map { it.toIntOrNull() ?: return null }
    }
}
