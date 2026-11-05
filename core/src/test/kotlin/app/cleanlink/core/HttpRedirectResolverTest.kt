package app.cleanlink.core

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/** Runs the real HTTP resolver against a tiny local server (JVM-only test). */
class HttpRedirectResolverTest {

    private lateinit var server: HttpServer
    private lateinit var base: String
    private val resolver = HttpRedirectResolver(timeoutMs = 3_000, allowPrivateHosts = true)

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/short") { ex ->
            ex.responseHeaders.add("Location", "/final?utm_source=short")
            ex.sendResponseHeaders(301, -1)
            ex.close()
        }
        server.createContext("/no-head") { ex ->
            if (ex.requestMethod == "HEAD") {
                ex.sendResponseHeaders(405, -1)
            } else {
                ex.responseHeaders.add("Location", "https://example.com/target")
                ex.sendResponseHeaders(302, -1)
            }
            ex.close()
        }
        server.createContext("/meta") { ex ->
            val body = if (ex.requestMethod == "HEAD") {
                ByteArray(0)
            } else {
                """<html><head><meta http-equiv="refresh" content="0; url=https://example.com/m?a=1&amp;b=2"></head></html>""".toByteArray()
            }
            ex.sendResponseHeaders(if (ex.requestMethod == "HEAD") 403 else 200, if (body.isEmpty()) -1 else body.size.toLong())
            if (body.isNotEmpty()) ex.responseBody.use { it.write(body) }
            ex.close()
        }
        server.createContext("/final") { ex ->
            ex.sendResponseHeaders(200, -1)
            ex.close()
        }
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
    }

    @After fun stop() = server.stop(0)

    @Test fun followsLocationHeaderAndResolvesRelativeUrls() {
        assertEquals("$base/final?utm_source=short", resolver.nextHop("$base/short"))
    }

    @Test fun fallsBackToGetWhenHeadIsRefused() {
        assertEquals("https://example.com/target", resolver.nextHop("$base/no-head"))
    }

    @Test fun readsMetaRefresh() {
        assertEquals("https://example.com/m?a=1&b=2", resolver.nextHop("$base/meta"))
    }

    @Test fun returnsNullAtFinalPage() {
        assertNull(resolver.nextHop("$base/final"))
    }

    @Test fun returnsNullWhenUnreachable() {
        assertNull(HttpRedirectResolver(timeoutMs = 500, allowPrivateHosts = true).nextHop("http://127.0.0.1:1/x"))
    }

    @Test fun refusesToRequestLocalAddressesByDefault() {
        // The server below is reachable and would redirect, but by default it must not even be asked.
        assertNull(HttpRedirectResolver(timeoutMs = 3_000).nextHop("$base/short"))
        assertNull(HttpRedirectResolver().nextHop("http://192.168.1.1/admin"))
        assertNull(HttpRedirectResolver().nextHop("http://localhost:8080/x"))
    }
}
