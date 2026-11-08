package app.cleanlink.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptionFetcherTest {
    @Test fun picksThePlatformFromTheLink() {
        assertEquals(CaptionPlatform.INSTAGRAM, CaptionFetcher.platformOf("https://www.instagram.com/reel/C9AbCdEfGhI/?igsh=x"))
        assertEquals(CaptionPlatform.TIKTOK, CaptionFetcher.platformOf("https://www.tiktok.com/@a/video/6718335390845095173"))
        assertEquals(CaptionPlatform.TIKTOK, CaptionFetcher.platformOf("https://vm.tiktok.com/ZMabc123/"))
        assertNull(CaptionFetcher.platformOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertNull(CaptionFetcher.platformOf("https://www.instagram.com/someone/"))
    }

    @Test fun otherLinksAreNotAPost() {
        assertEquals(CaptionResult.NotAPost, CaptionFetcher().fetch("https://example.com/"))
    }
}
