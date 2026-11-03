package app.cleanlink.core

/** Finds http(s) links inside free text such as "Check this out! https://x.com/… 🔥". */
object UrlExtractor {
    private val CANDIDATE = Regex("https?://[^\\s<>\"'`]+", RegexOption.IGNORE_CASE)
    private const val TRAILING_PUNCT = ".,;:!?'\"…»”’"

    /** Index ranges of every link in [text], with trailing punctuation left out. */
    fun find(text: String): List<IntRange> =
        CANDIDATE.findAll(text).mapNotNull { match ->
            val end = trimmedEnd(match.value)
            if (end <= "https://".length - 1) null else match.range.first until match.range.first + end
        }.toList()

    fun extract(text: String): List<String> = find(text).map { text.substring(it) }

    /** Length of [url] after dropping trailing punctuation and unbalanced closing brackets. */
    private fun trimmedEnd(url: String): Int {
        var end = url.length
        while (end > 0) {
            val c = url[end - 1]
            val sub = url.substring(0, end)
            val drop = when (c) {
                in TRAILING_PUNCT -> true
                ')' -> sub.count { it == '(' } < sub.count { it == ')' }
                ']' -> sub.count { it == '[' } < sub.count { it == ']' }
                '}' -> sub.count { it == '{' } < sub.count { it == '}' }
                else -> false
            }
            if (!drop) break
            end--
        }
        return end
    }
}
