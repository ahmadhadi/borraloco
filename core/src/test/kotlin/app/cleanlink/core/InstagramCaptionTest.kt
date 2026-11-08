package app.cleanlink.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pages below are hand-written in the shape Instagram's public pages are known to use.
 * They have NOT been compared with live Instagram pages (the build sandbox cannot reach instagram.com).
 */
class InstagramCaptionTest {

    private fun page(vararg metas: String) = "<html><head>${metas.joinToString("")}</head><body></body></html>"
    private fun og(name: String, content: String) = """<meta property="$name" content="$content" />"""

    private fun found(r: CaptionResult) = r as CaptionResult.Found

    // ---- Links ----------------------------------------------------------------------------

    @Test fun recognisesPostAndReelLinksWhateverTheTracking() {
        listOf(
            "https://www.instagram.com/p/C9AbCdEfGhI/?igsh=MWQ1ZGUxMzBkMA==",
            "https://instagram.com/reel/C9AbCdEfGhI/",
            "https://www.instagram.com/reels/C9AbCdEfGhI/?utm_source=ig_web_copy_link",
            "https://www.instagram.com/someone/reel/C9AbCdEfGhI/?igsh=abc",
            "https://www.instagram.com/tv/C9AbCdEfGhI",
            "https://instagr.am/p/C9AbCdEfGhI/",
            "http://www.instagram.com/p/C9AbCdEfGhI",
        ).forEach {
            val post = InstagramCaptions.parsePost(it)
            assertEquals(it, "C9AbCdEfGhI", post?.shortcode)
            assertEquals("/p/C9AbCdEfGhI/", post?.canonicalPath)
        }
    }

    @Test fun ignoresEverythingThatIsNotAPost() {
        listOf(
            "https://www.instagram.com/someone/",
            "https://www.instagram.com/stories/someone/3456/",
            "https://www.instagram.com/explore/tags/cats/",
            "https://www.instagram.com/p/",
            "https://evil.example/p/C9AbCdEfGhI/",
            "https://instagram.com.evil.example/p/C9AbCdEfGhI/",
            "https://www.instagram.com@evil.example/p/C9AbCdEfGhI/",
            "mailto:a@b.com",
            "not a link",
        ).forEach { assertNull(it, InstagramCaptions.parsePost(it)) }
    }

    // ---- Captions in the page header ----------------------------------------------------------

    @Test fun readsCaptionFromPostPreview() {
        val r = found(
            InstagramCaptions.parsePage(
                page(og("og:description", "1,234 likes, 56 comments - nasa on March 3, 2024: &quot;Hello from orbit! 🚀 #space&quot;.")),
            ),
        )
        assertEquals("Hello from orbit! 🚀 #space", r.caption)
        assertEquals("nasa", r.author)
        assertTrue(r.truncatedMaybe)
    }

    @Test fun readsCaptionFromReelTitleForm() {
        val r = found(InstagramCaptions.parsePage(page(og("og:title", "someone.else on Instagram: &quot;Sunday vibes&quot;"))))
        assertEquals("Sunday vibes", r.caption)
        assertEquals("someone.else", r.author)
    }

    @Test fun keepsQuotesInsideTheCaptionAndDecodesEntities() {
        val r = found(
            InstagramCaptions.parsePage(
                page(og("og:description", "9 likes, 0 comments - ali on May 1, 2024: &quot;He said &quot;wow&quot; &amp; left &#064;home &#x1F525; &lt;3&quot;.")),
            ),
        )
        assertEquals("He said \"wow\" & left @home 🔥 <3", r.caption)
    }

    @Test fun worksWhateverOrderTheMetaAttributesAreIn() {
        val html = """<meta content="5 likes - x on Jan 1, 2024: &quot;Reversed&quot;." property="og:description">"""
        assertEquals("Reversed", found(InstagramCaptions.parsePage(html)).caption)
        val single = "<meta name='og:description' content='5 likes - x on Jan 1, 2024: \"Single quoted\".'>"
        assertEquals("Single quoted", found(InstagramCaptions.parsePage(single)).caption)
    }

    @Test fun keepsArabicAndLineBreaks() {
        val r = found(InstagramCaptions.parsePage(page(og("og:description", "3 likes - sara on Feb 2, 2024: &quot;صباح الخير&#10;سطر ثانٍ&quot;."))))
        assertEquals("صباح الخير\nسطر ثانٍ", r.caption)
    }

    @Test fun postWithoutCaption() {
        assertEquals(CaptionResult.NoCaption, InstagramCaptions.parsePage(page(og("og:description", "1,234 likes, 56 comments - nasa on March 3, 2024"))))
    }

    // ---- Embed page ----------------------------------------------------------------------------

    @Test fun readsFullCaptionFromEmbedPage() {
        val html = """<div class="Caption"><a class="CaptionUsername" href="/nasa/">nasa</a> First line &amp; more<br />Second line #tag<br /><div class="CaptionComments"><a>View all 5 comments</a></div></div>"""
        val r = found(InstagramCaptions.parsePage(html))
        assertEquals("First line & more\nSecond line #tag", r.caption)
        assertEquals("nasa", r.author)
        assertEquals(false, r.truncatedMaybe)
    }

    // ---- When Instagram refuses ----------------------------------------------------------------

    @Test fun loginWallsAreReportedNotWorkedAround() {
        assertEquals(CaptionResult.LoginRequired, InstagramCaptions.parsePage(page("<title>Login • Instagram</title>", og("og:title", "Login • Instagram"))))
        assertEquals(
            CaptionResult.LoginRequired,
            InstagramCaptions.parsePage(page(og("og:description", "Create an account or log in to Instagram - Share what you're into with the people who matter most to you."))),
        )
        assertEquals(CaptionResult.LoginRequired, InstagramCaptions.parsePage("<html><script>window.location='/accounts/login/?next=/p/x/'</script></html>"))
    }

    @Test fun unrecognisedPageIsAFailureNotAGuess() {
        assertEquals(CaptionResult.Failed, InstagramCaptions.parsePage("<html><body>Something else</body></html>"))
        assertEquals(CaptionResult.Failed, InstagramCaptions.parsePage(""))
    }

    @Test fun decodesEntities() {
        assertEquals("a & b \"c\" ' <d> é 😀", InstagramCaptions.decodeEntities("a &amp; b &quot;c&quot; &#39; &lt;d&gt; &#xE9; &#128512;"))
        assertEquals("&bogus; &#99999999999;", InstagramCaptions.decodeEntities("&bogus; &#99999999999;"))
    }
}
