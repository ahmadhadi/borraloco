package app.cleanlink.core

import java.util.zip.CRC32

/** What kind of hidden mark [TextCleaner] found. */
enum class MarkKind {
    /** A C2PA provenance manifest hidden in the text (invisible suffix, `BEGIN C2PA MANIFEST` block, HTML tag). */
    C2PA_MANIFEST,

    /** A `VSRMark` soft binding: a payload spread over the text as invisible variation selectors. */
    VSR_MARK,

    /** A message written in invisible Unicode tag characters. */
    HIDDEN_MESSAGE,

    /** Zero-width characters, variation selectors, private-use characters and other invisible codepoints. */
    INVISIBLE_CHARS,

    /** Unusual space characters (thin space, em space, …) that can carry a pattern; replaced by a normal space. */
    ODD_SPACES,

    /** Chatbot citation markers such as ChatGPT's private-use citations or Gemini's `[cite: 1]`. */
    CHAT_CITATIONS,
}

data class MarkFinding(
    val kind: MarkKind,
    /** How many pieces were found (characters, blocks or markers). */
    val count: Int,
    /** A decoded payload when there is one (VSRMark payload, hidden message), shortened. */
    val detail: String? = null,
)

/** What [TextCleaner.clean] did to a text. */
data class TextScrubResult(
    val original: String,
    val cleaned: String,
    val findings: List<MarkFinding>,
) {
    val changed: Boolean get() = original != cleaned
}

/** Which kinds of marks to remove. */
enum class ScrubMode {
    /** Invisible marks: provenance blocks, invisible characters, hidden messages, odd spaces. Visible words stay as they are. */
    HIDDEN,

    /** Visible chatbot citation markers only. */
    CITATIONS,
}

/**
 * Finds and removes hidden marks in text: C2PA text provenance, `VSRMark`, Unicode-tag messages,
 * zero-width / variation-selector / private-use characters and unusual spaces; plus, separately,
 * chatbot citation markers.
 *
 * Everything is exact and offline. It does NOT touch statistical word-choice watermarks (SynthID-Text and
 * similar): those live in the words themselves and cannot be removed without rewriting the text.
 *
 * Characters that look invisible but belong to the text are kept: emoji selectors and joiners,
 * subdivision flags, joining-script (Arabic, Persian, Indic, …) joiners, bidirectional marks in
 * right-to-left text, and ideographic variation selectors. A *run* of joiners/marks is never legitimate
 * and is removed.
 */
object TextCleaner {

    /** Scans [text] without changing it. */
    fun inspect(text: String): List<MarkFinding> =
        (scrub(text, ScrubMode.HIDDEN).findings + scrub(text, ScrubMode.CITATIONS).findings)

    fun clean(text: String, mode: ScrubMode = ScrubMode.HIDDEN): TextScrubResult = scrub(text, mode)

    // ------------------------------------------------------------------------------------------

    private fun scrub(text: String, mode: ScrubMode): TextScrubResult {
        if (text.isEmpty()) return TextScrubResult(text, text, emptyList())
        return when (mode) {
            ScrubMode.CITATIONS -> scrubCitations(text)
            ScrubMode.HIDDEN -> scrubHidden(text)
        }
    }

    // ---- citations --------------------------------------------------------------------------

    private val citationRegex = Regex(
        "\uE200[^\uE200\uE201]{0,160}\uE201" +
            "|\\bcite(?:turn\\d+(?:search|news|view|image|file|fetch|forecast|sports|finance|academia|product|map)\\d+)+\\b" +
            "|\\[cite(?:_start|_end|:\\s*\\d+(?:\\s*,\\s*\\d+)*)\\]" +
            "|\\[span_\\d+\\](?:\\((?:start|end)_span\\))?" +
            "|\\bgrok_render_citation_card_json\\b(?:=\\{[^}\\n]*\\})?",
    )

    private fun scrubCitations(text: String): TextScrubResult {
        var count = 0
        val cleaned = citationRegex.replace(text) { count++; "" }
        val findings = if (count > 0) listOf(MarkFinding(MarkKind.CHAT_CITATIONS, count)) else emptyList()
        return TextScrubResult(text, cleaned, findings)
    }

    // ---- provenance blocks ------------------------------------------------------------------

    private val asciiArmorRegex = Regex(
        "(?:<!--\\s*)?-----BEGIN C2PA MANIFEST-----[\\s\\S]*?-----END C2PA MANIFEST-----(?:\\s*-->)?",
    )
    private val htmlScriptRegex = Regex(
        "<script\\b[^>]*\\btype\\s*=\\s*['\"]application/c2pa['\"][^>]*>[\\s\\S]*?</script\\s*>",
        RegexOption.IGNORE_CASE,
    )
    private val htmlLinkRegex = Regex(
        "<link\\b[^>]*\\brel\\s*=\\s*['\"]c2pa-manifest['\"][^>]*>",
        RegexOption.IGNORE_CASE,
    )

    private val c2paTxtMagic = "C2PATXT\u0000".toByteArray(Charsets.ISO_8859_1)

    /** Removes C2PA wrappers; returns the text and how many were found. */
    private fun stripC2pa(text: String): Pair<String, Int> {
        var out = text
        var found = 0

        // Appendix A.8: U+FEFF followed by variation selectors to the end of the text.
        val feff = out.lastIndexOf('\uFEFF')
        if (feff >= 0) {
            val tail = out.substring(feff + 1)
            if (tail.isNotEmpty() && tail.codePoints().allMatch { isVariationSelectorByte(it) }) {
                val bytes = decodeVariationSelectors(tail)
                if (bytes.size >= c2paTxtMagic.size && bytes.copyOfRange(0, c2paTxtMagic.size).contentEquals(c2paTxtMagic)) {
                    out = out.substring(0, feff)
                    found++
                }
            }
        }
        for (regex in listOf(asciiArmorRegex, htmlScriptRegex, htmlLinkRegex)) {
            out = regex.replace(out) { found++; "" }
        }
        return out to found
    }

    // ---- VSRMark ----------------------------------------------------------------------------

    private val vsrMagic = byteArrayOf('V'.code.toByte(), 'S'.code.toByte(), 'R'.code.toByte(), '1'.code.toByte())

    /** Decodes a VSRMark payload from the variation selectors in [text], or null when there is none (CRC checked). */
    private fun detectVsrMark(text: String): String? {
        val selectors = StringBuilder()
        text.codePoints().forEach { if (isVariationSelectorByte(it)) selectors.appendCodePoint(it) }
        if (selectors.isEmpty()) return null
        val bytes = decodeVariationSelectors(selectors.toString())
        if (bytes.size < 11) return null
        for (i in vsrMagic.indices) if (bytes[i] != vsrMagic[i]) return null
        if (bytes[4].toInt() != 1) return null
        val length = ((bytes[5].toInt() and 0xFF) shl 8) or (bytes[6].toInt() and 0xFF)
        if (bytes.size < 7 + length + 4) return null
        val crc = CRC32().apply { update(bytes, 0, 7 + length) }.value
        var stored = 0L
        for (i in 0 until 4) stored = (stored shl 8) or (bytes[7 + length + i].toLong() and 0xFF)
        if (crc != stored) return null
        return String(bytes, 7, length, Charsets.UTF_8)
    }

    private fun isVariationSelectorByte(cp: Int) = cp in 0xFE00..0xFE0F || cp in 0xE0100..0xE01EF

    private fun decodeVariationSelectors(chars: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        chars.codePoints().forEach { cp ->
            when (cp) {
                in 0xFE00..0xFE0F -> out.write(cp - 0xFE00)
                in 0xE0100..0xE01EF -> out.write(cp - 0xE0100 + 16)
            }
        }
        return out.toByteArray()
    }

    // ---- invisible characters ---------------------------------------------------------------

    private fun scrubHidden(original: String): TextScrubResult {
        val findings = mutableListOf<MarkFinding>()

        val (afterC2pa, c2paCount) = stripC2pa(original)
        if (c2paCount > 0) findings += MarkFinding(MarkKind.C2PA_MANIFEST, c2paCount)

        val vsr = detectVsrMark(afterC2pa)
        if (vsr != null) findings += MarkFinding(MarkKind.VSR_MARK, 1, vsr.shorten())

        val cps = afterC2pa.codePoints().toArray()
        val keepFlagTags = subdivisionFlagTags(cps)
        val hasRtl = cps.any { isRtl(it) }
        val out = StringBuilder(afterC2pa.length)
        val tagMessage = StringBuilder()
        var invisible = 0
        var oddSpaces = 0

        for (i in cps.indices) {
            val cp = cps[i]
            when {
                cp in 0xE0000..0xE007F -> {
                    if (i in keepFlagTags) {
                        out.appendCodePoint(cp)
                    } else {
                        invisible++
                        if (cp in 0xE0020..0xE007E) tagMessage.append((cp - 0xE0000).toChar())
                    }
                }
                isOddSpace(cp) -> {
                    if (keepSpace(cps, i)) out.appendCodePoint(cp) else { out.append(' '); oddSpaces++ }
                }
                isInvisibleCandidate(cp) -> {
                    if (isLegitimate(cps, i, hasRtl)) out.appendCodePoint(cp) else invisible++
                }
                else -> out.appendCodePoint(cp)
            }
        }

        if (tagMessage.isNotBlank()) findings += MarkFinding(MarkKind.HIDDEN_MESSAGE, 1, tagMessage.toString().shorten())
        if (invisible > 0) findings += MarkFinding(MarkKind.INVISIBLE_CHARS, invisible)
        if (oddSpaces > 0) findings += MarkFinding(MarkKind.ODD_SPACES, oddSpaces)
        // A VSRMark's selectors are counted as invisible characters too; keep the report to one line for it.
        return TextScrubResult(original, out.toString(), findings)
    }

    private fun String.shorten(max: Int = 160) = if (length <= max) this else take(max) + "…"

    private fun isInvisibleCandidate(cp: Int): Boolean =
        cp in zeroWidthCodepoints ||
            cp in 0xFE00..0xFE0F ||
            cp in 0xE0100..0xE01EF ||
            cp in 0xE000..0xF8FF ||
            cp in 0xF0000..0xFFFFD ||
            cp in 0x100000..0x10FFFD ||
            cp in 0xFFF0..0xFFF8

    private val zeroWidthCodepoints: Set<Int> = setOf(
        0x00AD, 0x034F, 0x061C, 0x115F, 0x1160, 0x17B4, 0x17B5, 0x180B, 0x180C, 0x180D, 0x180E, 0x180F,
        0x200B, 0x200C, 0x200D, 0x200E, 0x200F, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E,
        0x2060, 0x2061, 0x2062, 0x2063, 0x2064, 0x2065, 0x2066, 0x2067, 0x2068, 0x2069,
        0x206A, 0x206B, 0x206C, 0x206D, 0x206E, 0x206F, 0x3164, 0xFEFF, 0xFFA0, 0xFFF9, 0xFFFA, 0xFFFB,
        0x1BCA0, 0x1BCA1, 0x1BCA2, 0x1BCA3, 0x1D159, 0x1D173, 0x1D174, 0x1D175, 0x1D176, 0x1D177, 0x1D178,
        0x1D179, 0x1D17A,
    )

    private fun isInvisibleAt(cps: IntArray, i: Int) =
        i in cps.indices && cps[i] != 0xFE0E && cps[i] != 0xFE0F &&
            (isInvisibleCandidate(cps[i]) || cps[i] in 0xE0000..0xE007F)

    /** True when the invisible-looking character at [i] belongs to the text and must stay. */
    private fun isLegitimate(cps: IntArray, i: Int, hasRtl: Boolean): Boolean {
        val cp = cps[i]
        // A neighbour that is also invisible means a run, and a run is never ordinary text.
        val isolated = !isInvisibleAt(cps, i - 1) && !isInvisibleAt(cps, i + 1)
        val prev = if (i > 0) cps[i - 1] else -1
        val next = if (i + 1 < cps.size) cps[i + 1] else -1

        return when (cp) {
            // Emoji presentation selectors.
            0xFE0E, 0xFE0F -> {
                val keycap = (prev == '#'.code || prev == '*'.code || prev in '0'.code..'9'.code) && next == 0x20E3
                (isEmojiBase(prev) || keycap) && !isVariationSelectorByte(next)
            }
            // Other variation selectors: only an ideographic variation selector right after a CJK ideograph.
            in 0xFE00..0xFE0D, in 0xE0100..0xE01EF -> isCjkIdeograph(prev) && !isVariationSelectorByte(next)
            0x200D -> isolated && isZwjLegit(cps, i)
            0x200C -> isolated && isJoiningScript(prev) && isJoiningScript(next)
            // Bidirectional controls matter only in right-to-left text.
            0x200E, 0x200F, 0x061C, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E, 0x2066, 0x2067, 0x2068, 0x2069 ->
                hasRtl && isolated
            0x180B, 0x180C, 0x180D, 0x180F -> isolated && prev in 0x1800..0x18AF
            0x17B4, 0x17B5 -> isolated && (prev in 0x1780..0x17FF || next in 0x1780..0x17FF)
            0x034F -> isolated && (isHebrewOrArabic(prev) || isHebrewOrArabic(next))
            else -> false
        }
    }

    /** A ZWJ stays when it joins two emoji (skipping selectors and skin-tone modifiers) or two joining-script letters. */
    private fun isZwjLegit(cps: IntArray, i: Int): Boolean {
        var l = i - 1
        while (l >= 0 && (cps[l] == 0xFE0E || cps[l] == 0xFE0F || cps[l] in 0x1F3FB..0x1F3FF)) l--
        var r = i + 1
        while (r < cps.size && (cps[r] == 0xFE0E || cps[r] == 0xFE0F || cps[r] in 0x1F3FB..0x1F3FF)) r++
        val left = if (l >= 0) cps[l] else -1
        val right = if (r < cps.size) cps[r] else -1
        if (isEmojiBase(left) && isEmojiBase(right)) return true
        val prev = if (i > 0) cps[i - 1] else -1
        val next = if (i + 1 < cps.size) cps[i + 1] else -1
        return isJoiningScript(prev) && isJoiningScript(next)
    }

    /** Valid subdivision flags (🏴 + tag letters + cancel tag): indices of their tag characters. */
    private fun subdivisionFlagTags(cps: IntArray): Set<Int> {
        val keep = HashSet<Int>()
        var i = 0
        while (i < cps.size) {
            if (cps[i] == 0x1F3F4) {
                var j = i + 1
                while (j < cps.size && cps[j] in 0xE0020..0xE007E) j++
                if (j > i + 1 && j < cps.size && cps[j] == 0xE007F) {
                    for (k in i + 1..j) keep += k
                    i = j
                }
            }
            i++
        }
        return keep
    }

    // ---- character classes ------------------------------------------------------------------

    private fun isEmojiBase(cp: Int): Boolean = when {
        cp < 0 -> false
        cp in 0x1F000..0x1FAFF -> true
        cp in 0x2190..0x21FF -> true
        cp in 0x2300..0x23FF -> true
        cp in 0x2460..0x24FF -> cp == 0x24C2
        cp in 0x25A0..0x25FF -> true
        cp in 0x2600..0x27BF -> true
        cp in 0x2900..0x297F -> cp == 0x2934 || cp == 0x2935
        cp in 0x2B00..0x2BFF -> true
        else -> cp == 0xA9 || cp == 0xAE || cp == 0x203C || cp == 0x2049 || cp == 0x2122 || cp == 0x2139 ||
            cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299
    }

    private fun isCjkIdeograph(cp: Int) =
        cp in 0x4E00..0x9FFF || cp in 0x3400..0x4DBF || cp in 0xF900..0xFAFF || cp in 0x20000..0x2FFFF

    private fun isRtl(cp: Int) =
        cp in 0x0590..0x08FF || cp in 0xFB1D..0xFDFF || cp in 0xFE70..0xFEFC

    private fun isHebrewOrArabic(cp: Int) = cp in 0x0590..0x06FF

    /** Letters of scripts where ZWNJ/ZWJ change how neighbouring letters join (Arabic, Persian, Indic, …). */
    private fun isJoiningScript(cp: Int): Boolean =
        cp in 0x0600..0x06FF || cp in 0x0700..0x074F || cp in 0x0750..0x077F || cp in 0x08A0..0x08FF ||
            cp in 0xFB50..0xFDFF || cp in 0xFE70..0xFEFC || cp in 0x0900..0x0DFF || cp in 0x0F00..0x0FFF ||
            cp in 0x1000..0x109F || cp in 0x1780..0x17FF || cp in 0x1800..0x18AF

    // ---- spaces -----------------------------------------------------------------------------

    private fun isOddSpace(cp: Int) =
        cp == 0x00A0 || cp == 0x1680 || cp in 0x2000..0x200A || cp == 0x202F || cp == 0x205F || cp == 0x3000

    /** Typographic non-breaking spaces (next to numbers or French-style punctuation) and CJK spaces stay. */
    private fun keepSpace(cps: IntArray, i: Int): Boolean {
        val cp = cps[i]
        val left = if (i > 0) cps[i - 1] else -1
        val right = if (i + 1 < cps.size) cps[i + 1] else -1
        if (cp == 0x3000) return isCjkOrFullwidth(left) || isCjkOrFullwidth(right)
        if (cp == 0x00A0 || cp == 0x202F || cp == 0x2007 || cp == 0x2009) {
            if ((left >= 0 && Character.isDigit(left)) || (right >= 0 && Character.isDigit(right))) return true
            return left == '«'.code || left == 0x2039 || right == '»'.code || right == 0x203A ||
                right == ':'.code || right == ';'.code || right == '!'.code || right == '?'.code
        }
        return false
    }

    private fun isCjkOrFullwidth(cp: Int) =
        cp in 0x3000..0x9FFF || cp in 0xFF00..0xFFEF || cp in 0xAC00..0xD7AF
}
