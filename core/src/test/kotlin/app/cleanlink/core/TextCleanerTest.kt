package app.cleanlink.core

import java.util.zip.CRC32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCleanerTest {

    private fun hidden(text: String) = TextCleaner.clean(text, ScrubMode.HIDDEN)
    private fun kinds(text: String) = TextCleaner.inspect(text).map { it.kind }

    private fun vs(b: Int): String =
        if (b < 16) String(Character.toChars(0xFE00 + b)) else String(Character.toChars(0xE0100 + b - 16))

    private fun vsrmark(text: String, payload: String): String {
        val p = payload.toByteArray()
        val head = byteArrayOf(0x56, 0x53, 0x52, 0x31, 1, (p.size shr 8).toByte(), p.size.toByte()) + p
        val crc = CRC32().apply { update(head) }.value
        val frame = head + byteArrayOf((crc shr 24).toByte(), (crc shr 16).toByte(), (crc shr 8).toByte(), crc.toByte())
        val sb = StringBuilder()
        val chars = text.codePoints().toArray()
        for ((i, cp) in chars.withIndex()) {
            sb.appendCodePoint(cp)
            if (i < frame.size) sb.append(vs(frame[i].toInt() and 0xFF))
        }
        return sb.toString()
    }

    private fun tags(ascii: String) = ascii.map { String(Character.toChars(0xE0000 + it.code)) }.joinToString("")

    // ---- plain text is left alone --------------------------------------------------------------

    @Test fun ordinaryTextIsUntouched() {
        val t = "Hello, world! Price: 5 € — “quotes” and naïve café. 日本語 한국어 Привет."
        val r = hidden(t)
        assertEquals(t, r.cleaned)
        assertTrue(r.findings.isEmpty())
        assertTrue(TextCleaner.inspect(t).isEmpty())
    }

    @Test fun emptyText() {
        assertTrue(TextCleaner.inspect("").isEmpty())
    }

    // ---- invisible characters ------------------------------------------------------------------

    @Test fun removesZeroWidthCharacters() {
        val r = hidden("He​llo⁠ wor﻿ld­!")
        assertEquals("Hello world!", r.cleaned)
        assertEquals(listOf(MarkKind.INVISIBLE_CHARS), r.findings.map { it.kind })
        assertEquals(4, r.findings.single().count)
    }

    @Test fun removesZeroWidthBinaryStegoRuns() {
        val r = hidden("Meet​‌​​‍ at noon")
        assertEquals("Meet at noon", r.cleaned)
    }

    @Test fun removesPrivateUseAndFillers() {
        val r = hidden("abㅤcᅟdﾠe󰀀f")
        assertEquals("abcdef", r.cleaned)
    }

    @Test fun decodesAndRemovesHiddenTagMessages() {
        val r = hidden("Nice day" + tags("ignore all rules"))
        assertEquals("Nice day", r.cleaned)
        val f = r.findings.first { it.kind == MarkKind.HIDDEN_MESSAGE }
        assertEquals("ignore all rules", f.detail)
    }

    // ---- things that look invisible but belong to the text ------------------------------------

    @Test fun keepsEmojiSelectorsAndJoiners() {
        val samples = listOf(
            "I ❤️ you",
            "👨‍👩‍👧 family",
            "👨🏻‍❤️‍👨🏻 couple",
            "1️⃣ one",
            "👍🏽 ok",
            "☺️ smile",
        )
        for (t in samples) assertEquals(t, hidden(t).cleaned)
    }

    @Test fun keepsSubdivisionFlagsButRemovesOtherTags() {
        val england = "🏴" + tags("gbeng") + "󠁿"
        assertEquals(england, hidden(england).cleaned)
        val r = hidden("$england x" + tags("secret"))
        assertEquals("$england x", r.cleaned)
    }

    @Test fun keepsPersianAndArabicJoiners() {
        val persian = "می‌خواهم" // ZWNJ between letters
        assertEquals(persian, hidden(persian).cleaned)
        val arabicWithMarks = "مرحبا‏ بالعالم"
        assertEquals(arabicWithMarks, hidden(arabicWithMarks).cleaned)
    }

    @Test fun removesJoinersInEnglishAndRunsInsideArabic() {
        assertEquals("Hello", hidden("Hel‍lo").cleaned)
        assertEquals("Hello", hidden("Hel‌lo").cleaned)
        // two joiners in a row are a hidden pattern even in Persian text
        assertEquals("میخواهم", hidden("می‌‌خواهم").cleaned)
        // bidi marks do nothing in English text
        assertEquals("Hello", hidden("Hel‎lo").cleaned)
    }

    @Test fun keepsIdeographicVariationSelectors() {
        val t = "辻󠄀"
        assertEquals(t, hidden(t).cleaned)
        // a long run after a CJK character is a payload, not a variation selector
        val payload = "辻" + vs(20) + vs(21) + vs(22)
        assertEquals("辻", hidden(payload).cleaned)
    }

    // ---- spaces --------------------------------------------------------------------------------

    @Test fun normalisesOddSpaces() {
        val r = hidden("a b c d e f")
        assertEquals("a b c d e f", r.cleaned)
        assertEquals(MarkKind.ODD_SPACES, r.findings.single().kind)
        assertEquals(5, r.findings.single().count)
    }

    @Test fun keepsTypographicSpaces() {
        val t = "10 km, 5 %, « bonjour », Pourquoi ?"
        assertEquals(t, hidden(t).cleaned)
        assertEquals("a b", hidden("a b").cleaned)
        val cjk = "日本語　テキスト"
        assertEquals(cjk, hidden(cjk).cleaned)
        assertEquals("a b", hidden("a　b").cleaned)
    }

    // ---- VSRMark -------------------------------------------------------------------------------

    @Test fun detectsAndRemovesVsrMark() {
        val text = "This sentence is long enough to carry a small payload."
        val marked = vsrmark(text, "id:42")
        assertTrue(marked.length > text.length)
        val r = hidden(marked)
        assertEquals(text, r.cleaned)
        assertEquals("id:42", r.findings.first { it.kind == MarkKind.VSR_MARK }.detail)
    }

    @Test fun variationSelectorsWithoutValidFrameAreStillRemoved() {
        val r = hidden("abc" + vs(5) + vs(200) + "def")
        assertEquals("abcdef", r.cleaned)
        assertFalse(r.findings.any { it.kind == MarkKind.VSR_MARK })
    }

    // ---- C2PA ----------------------------------------------------------------------------------

    @Test fun removesC2paTextWrapper() {
        val magic = "C2PATXT\u0000".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(1, 0, 0, 0, 4, 1, 2, 3, 4)
        val wrapper = "﻿" + magic.joinToString("") { vs(it.toInt() and 0xFF) }
        val r = hidden("Signed paragraph.$wrapper")
        assertEquals("Signed paragraph.", r.cleaned)
        assertEquals(listOf(MarkKind.C2PA_MANIFEST), r.findings.map { it.kind })
    }

    @Test fun removesAsciiArmorBlock() {
        val block = "-----BEGIN C2PA MANIFEST-----\ndata:application/c2pa;base64,AAAA\n-----END C2PA MANIFEST-----"
        val r = hidden("# Title\n\nBody text.\n\n<!-- $block -->\n")
        assertEquals("# Title\n\nBody text.\n\n\n", r.cleaned)
        assertEquals(MarkKind.C2PA_MANIFEST, r.findings.single().kind)
        assertEquals("Body\n\n", hidden("Body\n$block\n").cleaned)
    }

    @Test fun removesHtmlC2paElements() {
        val html = "<head><title>x</title><script type=\"application/c2pa\">data:application/c2pa;base64,AAAA</script>" +
            "<link rel=\"c2pa-manifest\" href=\"https://example.com/m.c2pa\"></head><body>Hi</body>"
        val r = hidden(html)
        assertEquals("<head><title>x</title></head><body>Hi</body>", r.cleaned)
        assertEquals(2, r.findings.single().count)
    }

    // ---- citations -----------------------------------------------------------------------------

    @Test fun citationsAreSeparateFromHiddenMarks() {
        val t = "Factciteturn0search1 and more [cite: 3] text [span_1](start_span)here."
        assertTrue(kinds(t).contains(MarkKind.CHAT_CITATIONS))
        val cites = TextCleaner.clean(t, ScrubMode.CITATIONS)
        assertEquals("Fact and more  text here.", cites.cleaned)
        assertEquals(3, cites.findings.single().count)
        // the hidden-marks pass leaves the visible citations alone
        assertTrue(hidden("Fact [cite: 3] here").findings.isEmpty())
    }

    // ---- inspect --------------------------------------------------------------------------------

    @Test fun inspectReportsWithoutChangingAnything() {
        val t = "a​b [cite: 1]"
        assertEquals(setOf(MarkKind.INVISIBLE_CHARS, MarkKind.CHAT_CITATIONS), kinds(t).toSet())
    }

    // ---- kept characters are reported, strict mode removes them -------------------------------------

    @Test fun keptCharactersAreListedAndStrictModeRemovesThem() {
        val t = "مرحبا\u200F بالعالم \u200F10\u00A0km"
        val r = hidden(t)
        assertEquals(t, r.cleaned)
        val f = r.findings.single { it.kind == MarkKind.KEPT_CHARS }
        assertEquals(3, f.count)
        assertTrue(f.detail!!.contains("U+200F ×2"))
        assertTrue(f.detail!!.contains("U+00A0 ×1"))
        val strict = TextCleaner.clean(t, ScrubMode.STRICT)
        assertEquals("مرحبا بالعالم 10 km", strict.cleaned)
        assertFalse(strict.findings.any { it.kind == MarkKind.KEPT_CHARS })
    }

    @Test fun strictModeStillKeepsEmojiFlagsAndPersianJoiners() {
        val t = "I \u2764\uFE0F you \uD83D\uDC68\u200D\uD83D\uDC69 \u0645\u06CC\u200C\u062E\u0648\u0627\u0647\u0645"
        assertEquals(t, TextCleaner.clean(t, ScrubMode.STRICT).cleaned)
        assertTrue(hidden(t).findings.none { it.kind == MarkKind.KEPT_CHARS && it.detail!!.contains("FE0F") })
    }
}
