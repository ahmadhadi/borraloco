package app.cleanlink.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkInspectorTest {

    private fun inspector(hops: Map<String, String>, calls: MutableList<String>? = null) =
        LinkInspector(resolver = RedirectResolver { url -> calls?.add(url); hops[url] })

    @Test fun followsEveryHopAndCleansTheEnd() {
        val r = inspector(
            mapOf(
                "https://t.co/a" to "https://bit.ly/b",
                "https://bit.ly/b" to "https://example.com/final?utm_source=x&id=7",
            ),
        ).inspect("https://t.co/a")

        assertEquals(listOf("https://t.co/a", "https://bit.ly/b", "https://example.com/final?utm_source=x&id=7"), r.hops.map { it.url })
        assertEquals(listOf(HopKind.START, HopKind.REDIRECT, HopKind.REDIRECT), r.hops.map { it.kind })
        assertEquals("https://example.com/final?utm_source=x&id=7", r.finalUrl)
        assertEquals("https://example.com/final?id=7", r.cleanedFinal.cleaned)
        assertNull(r.stopped)
        assertTrue(r.redirected)
    }

    @Test fun linkWithoutRedirectsIsASingleHop() {
        val r = inspector(emptyMap()).inspect("  https://example.com/page  ")
        assertEquals(1, r.hops.size)
        assertEquals("https://example.com/page", r.finalUrl)
        assertFalse(r.redirected)
        assertNull(r.stopped)
    }

    @Test fun unwrapsRedirectWrappersWithoutRequestingThem() {
        val calls = mutableListOf<String>()
        val r = inspector(emptyMap(), calls)
            .inspect("https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.org%2Fpost%3Ffbclid%3D1&h=AT0")
        assertEquals(listOf(HopKind.START, HopKind.UNWRAPPED), r.hops.map { it.kind })
        assertEquals("https://example.org/post?fbclid=1", r.finalUrl)
        assertEquals("https://example.org/post", r.cleanedFinal.cleaned)
        assertEquals(listOf("https://example.org/post?fbclid=1"), calls) // only the real destination was asked
    }

    @Test fun trendyolShortLinkThroughAdjustToProductPage() {
        val adjust = "https://p8zh.adj.st/abc?adjust_redirect=" +
            "https%3A%2F%2Fwww.trendyol.sa%2Fx%2Fitem-p-1%3FboutiqueId%3D61%26merchantId%3D2%26link_userID%3D9"
        val r = inspector(mapOf("https://ty.gl/q" to adjust)).inspect("https://ty.gl/q")
        assertEquals(listOf(HopKind.START, HopKind.REDIRECT, HopKind.UNWRAPPED), r.hops.map { it.kind })
        assertEquals("https://www.trendyol.sa/x/item-p-1", r.cleanedFinal.cleaned)
    }

    @Test fun stopsOnRedirectLoops() {
        val r = inspector(mapOf("https://a.example/" to "https://b.example/", "https://b.example/" to "https://a.example/"))
            .inspect("https://a.example/")
        assertEquals(StopReason.LOOP, r.stopped)
        assertEquals(2, r.hops.size)
    }

    @Test fun stopsAfterTooManyHops() {
        val endless = LinkInspector(resolver = RedirectResolver { it + "x" })
        val r = endless.inspect("https://a.example/")
        assertEquals(StopReason.TOO_MANY_HOPS, r.stopped)
        assertEquals(endless.maxHops + 1, r.hops.size)
    }

    @Test fun neverRequestsAddressesOnTheUsersNetwork() {
        val calls = mutableListOf<String>()
        val r = inspector(
            mapOf("https://t.co/a" to "http://192.168.1.1/admin/reboot", "http://192.168.1.1/admin/reboot" to "https://evil.example/"),
            calls,
        ).inspect("https://t.co/a")
        assertEquals(StopReason.PRIVATE_ADDRESS, r.stopped)
        assertEquals("http://192.168.1.1/admin/reboot", r.finalUrl)
        assertEquals(listOf("https://t.co/a"), calls)
    }

    @Test fun notAWebLink() {
        val r = inspector(emptyMap()).inspect("mailto:a@b.com")
        assertEquals(StopReason.NOT_A_WEB_LINK, r.stopped)
        assertEquals(1, r.hops.size)
    }
}
