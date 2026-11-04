package app.cleanlink.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkCleanerTest {

    private val cleaner = LinkCleaner()

    private fun assertCleans(dirty: String, expected: String) {
        val result = cleaner.clean(dirty)
        assertEquals("cleaning $dirty", expected, result.cleaned)
        // Cleaning must be idempotent.
        assertEquals("re-cleaning $expected", expected, cleaner.clean(result.cleaned).cleaned)
    }

    private fun assertUnchanged(url: String) = assertCleans(url, url)

    // ---- Platforms -------------------------------------------------------------------------

    @Test fun x() {
        assertCleans(
            "https://x.com/NASA/status/1834567890123456789?s=46&t=AbCdEfGhIjKlMnOp",
            "https://x.com/NASA/status/1834567890123456789",
        )
        assertCleans(
            "https://twitter.com/NASA/status/1?ref_src=twsrc%5Etfw%7Ctwcamp%5Etweetembed&s=20",
            "https://twitter.com/NASA/status/1",
        )
        assertCleans("https://mobile.twitter.com/a/status/1?s=19", "https://mobile.twitter.com/a/status/1")
        assertCleans(
            "https://x.com/paolino/status/2117667400485048364?a=UFSpH1v-fiW6xy_mCpg",
            "https://x.com/paolino/status/2117667400485048364",
        )
        assertUnchanged("https://x.com/search?q=android&f=live")
    }

    @Test fun spotify() {
        assertCleans(
            "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=1a2b3c4d5e6f4a7b&context=spotify%3Aplaylist%3A37i9dQZF1DXcBWIGoYBM5M",
            "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT",
        )
        assertCleans(
            "https://open.spotify.com/album/1A2GTWGtFfWp7KSQTwWOyo?si=xyz&highlight=spotify:track:abc",
            "https://open.spotify.com/album/1A2GTWGtFfWp7KSQTwWOyo?highlight=spotify:track:abc",
        )
    }

    @Test fun instagram() {
        assertCleans(
            "https://www.instagram.com/reel/C9AbCdEfGhI/?igsh=MWQ1ZGUxMzBkMA==",
            "https://www.instagram.com/reel/C9AbCdEfGhI/",
        )
        assertCleans(
            "https://www.instagram.com/p/C9AbCdEfGhI/?utm_source=ig_web_copy_link&igshid=MzRlODBiNWFlZA==&img_index=3",
            "https://www.instagram.com/p/C9AbCdEfGhI/?img_index=3",
        )
    }

    @Test fun threads() {
        assertCleans(
            "https://www.threads.net/@zuck/post/C9AbC?xmt=AQGzabc&slof=1",
            "https://www.threads.net/@zuck/post/C9AbC",
        )
    }

    @Test fun snapchat() {
        assertCleans(
            "https://www.snapchat.com/spotlight/W7_EDlXWTBiXAEEniNoMPwAAYZnZqY29udGVu?share_id=QjdCRDk3&locale=en-US&sid=abc123",
            "https://www.snapchat.com/spotlight/W7_EDlXWTBiXAEEniNoMPwAAYZnZqY29udGVu",
        )
        assertCleans(
            "https://www.snapchat.com/add/someone?share_id=abc&locale=ar-SA",
            "https://www.snapchat.com/add/someone",
        )
    }

    @Test fun tiktok() {
        assertCleans(
            "https://www.tiktok.com/@user/video/7391234567890123456?is_from_webapp=1&sender_device=pc&web_id=7391234567890123457&_r=1&_t=8oAbCdEf",
            "https://www.tiktok.com/@user/video/7391234567890123456",
        )
        assertUnchanged("https://www.tiktok.com/search?q=cats")
    }

    @Test fun youtube() {
        assertCleans(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=AbCdEfGh123&t=42&feature=shared",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42",
        )
        assertCleans("https://youtu.be/dQw4w9WgXcQ?si=AbCdEfGh123", "https://youtu.be/dQw4w9WgXcQ")
        assertCleans(
            "https://youtube.com/shorts/AbCdEfGhIjK?si=xyz&feature=share",
            "https://youtube.com/shorts/AbCdEfGhIjK",
        )
        assertUnchanged("https://www.youtube.com/playlist?list=PL1234567890")
    }

    @Test fun temu() {
        assertCleans(
            "https://www.temu.com/wireless-earbuds-g-601099527781234.html?_oak_mp_inf=ELK5x&top_gallery_url=https%3A%2F%2Fimg.kwcdn.com%2Fa.jpg&spec_gallery_id=123&refer_page_sn=10032&refer_source=0&freesia_scene=2&_oak_freesia_scene=2&_x_sessn_id=fopx2s1k&refer_page_name=home&refer_page_id=10005_1700_xyz",
            "https://www.temu.com/wireless-earbuds-g-601099527781234.html",
        )
        assertCleans(
            "https://www.temu.com/goods.html?goods_id=601099527781234&_bg_fs=1&_p_rfs=1&_x_ads_channel=share",
            "https://www.temu.com/goods.html?goods_id=601099527781234",
        )
    }

    @Test fun amazon() {
        assertCleans(
            "https://www.amazon.com/Echo-Dot-5th-Gen/dp/B09B8V1LZ3/ref=sr_1_3?crid=2X3ABC&keywords=echo+dot&qid=1700000000&sprefix=echo%2Caps%2C200&sr=8-3&th=1",
            "https://www.amazon.com/Echo-Dot-5th-Gen/dp/B09B8V1LZ3?th=1",
        )
        assertCleans(
            "https://www.amazon.co.uk/dp/B09B8V1LZ3/?tag=someaffiliate-21&linkCode=ll1&ref_=as_li_ss_tl",
            "https://www.amazon.co.uk/dp/B09B8V1LZ3/",
        )
        assertCleans(
            "https://www.amazon.sa/-/en/dp/B0ABCDE123?pd_rd_w=abc&pf_rd_p=def&pd_rd_r=ghi&psc=1",
            "https://www.amazon.sa/-/en/dp/B0ABCDE123?psc=1",
        )
    }

    @Test fun facebook() {
        assertCleans(
            "https://www.facebook.com/watch/?v=123456789&mibextid=abc123&rdid=XyZ",
            "https://www.facebook.com/watch/?v=123456789",
        )
        assertCleans(
            "https://www.facebook.com/photo/?fbid=1&set=a.2&__cft__[0]=AZW&__tn__=EH-R",
            "https://www.facebook.com/photo/?fbid=1&set=a.2",
        )
    }

    @Test fun linkedinRedditPinterest() {
        assertCleans(
            "https://www.linkedin.com/posts/someone_activity-7212345678901234567-AbCd?utm_source=share&utm_medium=member_android&rcm=ACoAAA",
            "https://www.linkedin.com/posts/someone_activity-7212345678901234567-AbCd",
        )
        assertCleans(
            "https://www.reddit.com/r/androiddev/comments/1abcde/some_title/?share_id=AbC123&utm_content=1&utm_medium=android_app&utm_name=androidcss&utm_source=share&utm_term=1",
            "https://www.reddit.com/r/androiddev/comments/1abcde/some_title/",
        )
        assertCleans(
            "https://www.pinterest.com/pin/123456789/?invite_code=abc&sender=987&sfo=1",
            "https://www.pinterest.com/pin/123456789/",
        )
    }

    @Test fun aliexpressAndShein() {
        assertCleans(
            "https://www.aliexpress.com/item/1005006123456789.html?spm=a2g0o.home.0.0.650c&gps-id=pcJustForYou&scm=1007.13562&scm_id=1007.13562&scm-url=1007&pvid=abc&_t=gps-id",
            "https://www.aliexpress.com/item/1005006123456789.html",
        )
        assertCleans(
            "https://aliexpress.com/item/1005011924447121.html?spreadCode=TCs3RGhHOWVTTjdib3BNN3BJUTdFOTFqMlM3NEJwNS9Qcm8xWGZmODdjTWpmdlBzNkVmWTlBPT0&platform=AE",
            "https://aliexpress.com/item/1005011924447121.html",
        )
        assertCleans(
            "https://ar.shein.com/Dress-p-12345678.html?src_identifier=on%3DIMAGE&src_module=ad&src_tab_page_id=page_home&mallCode=1&scici=abc",
            "https://ar.shein.com/Dress-p-12345678.html",
        )
    }

    // ---- Redirect wrappers -----------------------------------------------------------------

    @Test fun unwrapsFacebookRedirect() {
        val r = cleaner.clean(
            "https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2Farticle%3Futm_source%3Dfacebook%26id%3D7%26fbclid%3DIwAR0abc&h=AT0abc",
        )
        assertEquals("https://example.com/article?id=7", r.cleaned)
        assertTrue(r.unwrapped)
    }

    @Test fun unwrapsOtherRedirects() {
        assertCleans(
            "https://www.google.com/url?q=https://example.org/page&sa=D&source=editors&ust=1700&usg=AOvVaw",
            "https://example.org/page",
        )
        assertCleans(
            "https://l.instagram.com/?u=https%3A%2F%2Fshop.example.com%2Fp%3Fid%3D3%26utm_source%3Dig&e=AT1",
            "https://shop.example.com/p?id=3",
        )
        assertCleans(
            "https://www.youtube.com/redirect?event=video_description&redir_token=QUFF&q=https%3A%2F%2Fexample.com%2F",
            "https://example.com/",
        )
    }

    @Test fun malformedRedirectDoesNotCrash() {
        val url = "https://l.facebook.com/l.php?u=%zz%E0&h=1"
        assertEquals(url, cleaner.clean(url).cleaned)
    }

    // ---- Generic behaviour -----------------------------------------------------------------

    @Test fun globalTrackersOnAnySite() {
        assertCleans(
            "https://shop.example.com/p?q=a%20b&utm_source=newsletter&page=2&fbclid=IwAR0&gclid=Cj0",
            "https://shop.example.com/p?q=a%20b&page=2",
        )
    }

    @Test fun keepsFragment() {
        assertCleans("https://example.com/a?utm_source=x#section-2", "https://example.com/a#section-2")
    }

    @Test fun leavesCleanAndFunctionalLinksAlone() {
        assertUnchanged("https://github.com/square/okhttp/blob/master/README.md?ref=main")
        assertUnchanged("https://en.wikipedia.org/wiki/URL")
        assertUnchanged("https://example.com/search?q=kotlin&page=3")
        assertUnchanged("https://maps.google.com/?q=24.7136,46.6753")
    }

    @Test fun ignoresNonHttpText() {
        assertEquals("mailto:a@b.com", cleaner.clean("mailto:a@b.com").cleaned)
        assertEquals("not a url", cleaner.clean("not a url").cleaned)
        assertFalse(cleaner.clean("https://example.com/").changed)
    }

    @Test fun reportsRemovedParams() {
        val r = cleaner.clean("https://open.spotify.com/track/1?si=abc&utm_source=copy-link")
        assertEquals(listOf("si", "utm_source"), r.removed)
        val a = cleaner.clean("https://www.amazon.de/dp/B0/ref=cm_sw_r_cp?tag=x")
        assertEquals(listOf("/ref=cm_sw_r_cp", "tag"), a.removed)
    }

    // ---- Text ------------------------------------------------------------------------------

    @Test fun cleansLinksInsideText() {
        val r = cleaner.cleanText(
            "Check this out! https://x.com/a/status/1?s=46&t=abc 🔥 and (https://example.com/x?utm_source=a).",
        )
        assertEquals(
            "Check this out! https://x.com/a/status/1 🔥 and (https://example.com/x).",
            r.cleanedText,
        )
        assertEquals(2, r.links.size)
    }

    @Test fun textWithoutLinksIsUnchanged() {
        val r = cleaner.cleanText("hello world")
        assertFalse(r.hasLinks)
        assertFalse(r.changed)
    }

    // ---- Short links -----------------------------------------------------------------------

    @Test fun detectsShortLinks() {
        listOf(
            "https://t.co/AbCdEf1234",
            "https://temu.to/k/abc123",
            "https://share.temu.com/AbC",
            "https://vm.tiktok.com/ZMabc123/",
            "https://www.tiktok.com/t/ZTabc123/",
            "https://spotify.link/AbCdEf",
            "https://t.snapchat.com/AbCd",
            "https://www.snapchat.com/t/AbCd",
            "https://amzn.to/3abcd",
            "https://a.co/d/abc",
            "https://www.reddit.com/r/androiddev/s/AbCdEf",
            "https://www.facebook.com/share/r/AbCd/",
            "https://www.instagram.com/share/AbCd",
            "https://pin.it/abc",
            "https://bit.ly/abc",
        ).forEach { assertTrue(it, cleaner.isShortLink(it)) }

        assertFalse(cleaner.isShortLink("https://www.snapchat.com/add/someone"))
        assertFalse(cleaner.isShortLink("https://x.com/a/status/1"))
    }

    @Test fun expandsShortLinksThenCleans() {
        val hops = mapOf(
            "https://t.co/abc" to "https://x.com/a/status/1?s=20&t=xyz",
            "https://temu.to/k/abc" to "https://share.temu.com/xyz?_x_share=1",
            "https://share.temu.com/xyz?_x_share=1" to "https://www.temu.com/item-g-601.html?_x_sessn_id=1&refer_page_name=share",
            "https://bit.ly/x" to "https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2F%3Ffbclid%3D1",
        )
        val expanding = LinkCleaner(resolver = RedirectResolver { hops[it] })

        val tco = expanding.clean("https://t.co/abc", expandShortLinks = true)
        assertEquals("https://x.com/a/status/1", tco.cleaned)
        assertTrue(tco.expanded)

        assertEquals(
            "https://www.temu.com/item-g-601.html",
            expanding.clean("https://temu.to/k/abc", expandShortLinks = true).cleaned,
        )
        assertEquals(
            "https://example.com/",
            expanding.clean("https://bit.ly/x", expandShortLinks = true).cleaned,
        )
    }

    @Test fun shortLinkLeftAloneWhenExpansionOffOrFails() {
        val failing = LinkCleaner(resolver = RedirectResolver { null })
        val r = failing.clean("https://t.co/abc", expandShortLinks = true)
        assertEquals("https://t.co/abc", r.cleaned)
        assertTrue(r.unexpandedShortLink)

        val off = LinkCleaner(resolver = RedirectResolver { error("must not be called") })
        assertEquals("https://t.co/abc", off.clean("https://t.co/abc", expandShortLinks = false).cleaned)
    }

    @Test fun redirectLoopsTerminate() {
        val loop = LinkCleaner(resolver = RedirectResolver { if (it.endsWith("a")) "https://bit.ly/b" else "https://bit.ly/a" })
        // Must return instead of spinning forever.
        loop.clean("https://bit.ly/a", expandShortLinks = true)
    }
}
