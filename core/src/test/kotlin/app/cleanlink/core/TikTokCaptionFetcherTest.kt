package app.cleanlink.core

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URLDecoder

/** The real fetcher against a local server standing in for tiktok.com (JVM-only test). */
class TikTokCaptionFetcherTest {

    private lateinit var server: HttpServer
    private lateinit var fetcher: TikTokCaptionFetcher
    private val seen = mutableListOf<String>()

    private fun reply(ex: HttpExchange, code: Int, body: String = "", location: String? = null) {
        seen += ex.requestURI.toString() + " cookie=" + ex.requestHeaders.getFirst("Cookie") + " ref=" + ex.requestHeaders.getFirst("Referer")
        if (location != null) ex.responseHeaders.add("Location", location)
        val bytes = body.toByteArray()
        ex.sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) }
        ex.close()
    }

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/oembed") { ex ->
            val asked = URLDecoder.decode(ex.requestURI.rawQuery.removePrefix("url="), "UTF-8")
            when {
                asked.endsWith("/video/1111111111") -> reply(ex, 200, """{"title":"Nice dance #fyp","author_name":"Dancer"}""")
                asked.endsWith("/video/2222222222") -> reply(ex, 200, """{"title":""}""")
                asked.endsWith("/video/3333333333") -> reply(ex, 400, """{"code":"x"}""")
                else -> reply(ex, 404)
            }
        }
        server.createContext("/@page/video/3333333333") { ex ->
            reply(ex, 200, """<script>{"itemStruct":{"desc":"From the page","author":{"uniqueId":"page"}}}</script>""")
        }
        server.createContext("/ZMshort1/") { ex -> reply(ex, 301, location = "/@dancer/video/1111111111?is_copy_url=1&tracking=abc") }
        server.createContext("/@dancer/video/1111111111") { ex -> reply(ex, 200, "<html></html>") }
        server.createContext("/ZMoffsite") { ex -> reply(ex, 302, location = "http://localhost:1/steal") }
        server.start()
        fetcher = TikTokCaptionFetcher(timeoutMs = 3_000, baseUrl = "http://127.0.0.1:${server.address.port}", allowedHosts = setOf("127.0.0.1"))
    }

    @After fun stop() = server.stop(0)

    @Test fun readsCaptionFromOembedAndSendsOnlyTheCleanVideoAddress() {
        val r = fetcher.fetch("https://www.tiktok.com/@dancer/video/1111111111?is_from_webapp=1&_t=SECRET&sender_device=pc")
        assertEquals("Nice dance #fyp", (r as CaptionResult.Found).caption)
        assertEquals("Dancer", r.author)
        assertEquals(1, seen.size)
        assertFalse(seen.joinToString().contains("SECRET"))
        assertTrue(seen.first().endsWith("cookie=null ref=null"))
    }

    @Test fun followsShortLinksToTheVideo() {
        val r = fetcher.fetch("https://vm.tiktok.com/ZMshort1/")
        assertEquals("Nice dance #fyp", (r as CaptionResult.Found).caption)
        assertFalse(seen.joinToString().contains("tracking=abc") && seen.any { it.startsWith("/oembed") && it.contains("tracking") })
    }

    @Test fun videoWithoutCaption() {
        assertEquals(CaptionResult.NoCaption, fetcher.fetch("https://www.tiktok.com/@dancer/video/2222222222"))
    }

    @Test fun fallsBackToThePageWhenOembedFails() {
        val r = fetcher.fetch("https://www.tiktok.com/@page/video/3333333333")
        assertEquals("From the page", (r as CaptionResult.Found).caption)
    }

    @Test fun failuresAreFailures() {
        assertEquals(CaptionResult.Failed, fetcher.fetch("https://www.tiktok.com/@dancer/video/9999999999"))
        assertEquals(CaptionResult.Failed, fetcher.fetch("https://vm.tiktok.com/ZMoffsite/"))
        assertEquals(CaptionResult.Failed, TikTokCaptionFetcher(timeoutMs = 500, baseUrl = "http://127.0.0.1:1", allowedHosts = setOf("127.0.0.1")).fetch("https://www.tiktok.com/@a/video/1111111111"))
        assertFalse(seen.any { it.contains("steal") })
    }

    @Test fun otherLinksAreNotRequested() {
        seen.clear()
        assertEquals(CaptionResult.NotAPost, fetcher.fetch("https://www.tiktok.com/@dancer"))
        assertEquals(CaptionResult.NotAPost, fetcher.fetch("https://example.com/@a/video/1111111111"))
        assertTrue(seen.isEmpty())
    }
}
