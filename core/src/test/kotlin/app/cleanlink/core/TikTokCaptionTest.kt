package app.cleanlink.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The oEmbed example is the one in TikTok's own documentation; the page example is hand-written. */
class TikTokCaptionTest {

    @Test fun recognisesVideoLinksAndDropsTracking() {
        val r = TikTokCaptions.parseLink("https://www.tiktok.com/@scout2015/video/6718335390845095173?is_from_webapp=1&sender_device=pc&_r=1&_t=8oAbCd")
        assertEquals(TikTokLink.Video("https://www.tiktok.com/@scout2015/video/6718335390845095173"), r)
        assertEquals(TikTokLink.Video("https://www.tiktok.com/@a.b_c/photo/6718335390845095173"), TikTokCaptions.parseLink("https://m.tiktok.com/@a.b_c/photo/6718335390845095173/"))
    }

    @Test fun recognisesShortLinks() {
        assertEquals(TikTokLink.Short("https://vm.tiktok.com/ZMabc123/"), TikTokCaptions.parseLink("https://vm.tiktok.com/ZMabc123/"))
        assertEquals(TikTokLink.Short("https://vt.tiktok.com/ZSabc123/"), TikTokCaptions.parseLink("https://vt.tiktok.com/ZSabc123"))
        assertEquals(TikTokLink.Short("https://www.tiktok.com/t/ZTabc123/"), TikTokCaptions.parseLink("https://www.tiktok.com/t/ZTabc123/?x=1"))
    }

    @Test fun ignoresEverythingElse() {
        listOf(
            "https://www.tiktok.com/@scout2015", "https://www.tiktok.com/discover", "https://www.tiktok.com/@a/video/12",
            "https://evil.example/@a/video/6718335390845095173", "https://tiktok.com.evil.example/@a/video/6718335390845095173",
            "https://www.tiktok.com@evil.example/@a/video/6718335390845095173", "https://vm.tiktok.com/", "not a link",
        ).forEach { assertNull(it, TikTokCaptions.parseLink(it)) }
    }

    @Test fun readsOembedTitleAsCaption() {
        val json = """{"version":"1.0","type":"video","title":"Scramble up ur name & I'll try to guess it😍❤️ #foryoupage #petsoftiktok","author_url":"https://www.tiktok.com/@scout2015","author_name":"Scout & Suki","width":"100%"}"""
        val r = TikTokCaptions.parseOembed(json) as CaptionResult.Found
        assertEquals("Scramble up ur name & I'll try to guess it😍❤️ #foryoupage #petsoftiktok", r.caption)
        assertEquals("Scout & Suki", r.author)
        assertEquals(false, r.truncatedMaybe)
    }

    @Test fun decodesJsonEscapes() {
        val r = TikTokCaptions.parseOembed("""{"title":"line1\nline2 \"quoted\" \\ back \/ slash مرحبا"}""") as CaptionResult.Found
        assertEquals("line1\nline2 \"quoted\" \\ back / slash مرحبا", r.caption)
    }

    @Test fun emptyOrMissingTitle() {
        assertEquals(CaptionResult.NoCaption, TikTokCaptions.parseOembed("""{"title":"","author_name":"x"}"""))
        assertEquals(CaptionResult.Failed, TikTokCaptions.parseOembed("""{"code":"invalid_url"}"""))
        assertEquals(CaptionResult.Failed, TikTokCaptions.parseOembed("<html>nope</html>"))
    }

    @Test fun fallbackReadsDescFromPageData() {
        val html = """<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">{"__DEFAULT_SCOPE__":{"webapp.video-detail":{"itemInfo":{"itemStruct":{"id":"1","desc":"Hello ❤ #tag","author":{"uniqueId":"scout2015"}}}}}}</script>"""
        val r = TikTokCaptions.parsePage(html) as CaptionResult.Found
        assertEquals("Hello ❤ #tag", r.caption)
        assertEquals(CaptionResult.Failed, TikTokCaptions.parsePage("<html></html>"))
        assertTrue(TikTokCaptions.parsePage("""{"itemStruct":{"desc":""}}""") is CaptionResult.NoCaption)
    }
}
