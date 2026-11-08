package app.cleanlink.core

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/** The real fetcher against a tiny local server that stands in for instagram.com (JVM-only test). */
class InstagramCaptionFetcherTest {

    private lateinit var server: HttpServer
    private lateinit var fetcher: InstagramCaptionFetcher
    private val seen = mutableListOf<String>()
    private val agents = mutableListOf<String?>()

    private fun html(body: String) = "<html><head>$body</head></html>".toByteArray()

    private fun reply(ex: com.sun.net.httpserver.HttpExchange, code: Int, body: ByteArray = ByteArray(0), location: String? = null) {
        seen += ex.requestURI.toString() + " cookie=" + ex.requestHeaders.getFirst("Cookie") + " ref=" + ex.requestHeaders.getFirst("Referer")
        agents += ex.requestHeaders.getFirst("User-Agent")
        if (location != null) ex.responseHeaders.add("Location", location)
        ex.sendResponseHeaders(code, if (body.isEmpty()) -1 else body.size.toLong())
        if (body.isNotEmpty()) ex.responseBody.use { it.write(body) }
        ex.close()
    }

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/p/GoodPost1/") { ex ->
            if (ex.requestURI.path.endsWith("embed/captioned/")) reply(ex, 404)
            else reply(ex, 200, html("""<meta property="og:description" content="1 like - nasa on May 5, 2024: &quot;Nice view&quot;." />"""))
        }
        server.createContext("/p/EmbedOnly1/") { ex ->
            if (ex.requestURI.path.endsWith("embed/captioned/")) {
                reply(ex, 200, """<div class="Caption"><a class="CaptionUsername">nasa</a> Full caption here<br />second line</div>""".toByteArray())
            } else reply(ex, 200, html("""<meta property="og:description" content="1 like - nasa on May 5, 2024" />"""))
        }
        server.createContext("/p/Private01/") { ex -> reply(ex, 302, location = "/accounts/login/?next=/p/Private01/") }
        server.createContext("/p/Blocked01/") { ex -> reply(ex, 403) }
        server.createContext("/p/Missing01/") { ex -> reply(ex, 404) }
        server.createContext("/p/Offsite01/") { ex -> reply(ex, 302, location = "http://localhost:1/steal") }
        server.createContext("/p/Loop00001/") { ex -> reply(ex, 302, location = "/p/Loop00001/") }
        server.start()
        val base = "http://127.0.0.1:${server.address.port}"
        fetcher = InstagramCaptionFetcher(timeoutMs = 3_000, baseUrl = base, allowedHosts = setOf("127.0.0.1"))
    }

    @After fun stop() = server.stop(0)

    private fun link(code: String) = "https://www.instagram.com/p/$code/?igsh=TRACKINGTRACKING&utm_source=ig_web_copy_link"

    @Test fun fetchesCaptionAndSendsOnlyTheCleanPostAddress() {
        val r = fetcher.fetch(link("GoodPost1"))
        assertEquals("Nice view", (r as CaptionResult.Found).caption)
        assertEquals("/p/GoodPost1/ cookie=null ref=null", seen.first()) // no tracking, cookies or referrer
        assertTrue(agents.first()!!.startsWith("facebookexternalhit"))
    }

    @Test fun fallsBackToTheEmbedPageWhenThePreviewHasNoCaption() {
        val r = fetcher.fetch(link("EmbedOnly1"))
        assertEquals("Full caption here\nsecond line", (r as CaptionResult.Found).caption)
        assertEquals(2, seen.size)
    }

    @Test fun reportsLoginInsteadOfWorkingAroundIt() {
        assertEquals(CaptionResult.LoginRequired, fetcher.fetch(link("Private01")))
        assertEquals(CaptionResult.LoginRequired, fetcher.fetch(link("Blocked01")))
        assertEquals(1, seen.count { it.startsWith("/p/Private01/") }) // did not retry via other pages
    }

    @Test fun failuresAreFailures() {
        assertEquals(CaptionResult.Failed, fetcher.fetch(link("Missing01")))
        assertEquals(CaptionResult.Failed, fetcher.fetch(link("Loop00001")))
        assertEquals(CaptionResult.Failed, InstagramCaptionFetcher(timeoutMs = 500, baseUrl = "http://127.0.0.1:1", allowedHosts = setOf("127.0.0.1")).fetch(link("GoodPost1")))
    }

    @Test fun neverFollowsARedirectToAnotherHost() {
        assertEquals(CaptionResult.Failed, fetcher.fetch(link("Offsite01")))
        assertFalse(seen.any { it.contains("steal") })
    }

    @Test fun nonPostLinksAreNotRequestedAtAll() {
        assertEquals(CaptionResult.NotAPost, fetcher.fetch("https://www.instagram.com/someone/"))
        assertEquals(CaptionResult.NotAPost, fetcher.fetch("https://example.com/p/GoodPost1/"))
        assertTrue(seen.isEmpty())
    }
}
