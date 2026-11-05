package app.cleanlink.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser is checked against hand-written fixtures in the DNS-over-HTTPS JSON shape.
 * It has NOT been checked against Cloudflare's live service.
 */
class DnsBlocklistTest {

    @Test fun sinkholedAnswerIsBlocked() {
        assertTrue(CloudflareMalwareDns.parseBlocked("""{"Status":0,"Answer":[{"name":"bad.example","type":1,"TTL":60,"data":"0.0.0.0"}]}"""))
        assertTrue(CloudflareMalwareDns.parseBlocked("""{"Status":0,"Answer":[{"name":"bad.example","type":28,"TTL":60,"data":"::"}]}"""))
    }

    @Test fun extendedDnsErrorIsBlocked() {
        assertTrue(CloudflareMalwareDns.parseBlocked("""{"Status":0,"Comment":["EDE(15): Blocked"]}"""))
        assertTrue(CloudflareMalwareDns.parseBlocked("""{"Status":0,"Comment":["EDE(17): Filtered"]}"""))
    }

    @Test fun normalAnswersAreNotBlocked() {
        assertFalse(CloudflareMalwareDns.parseBlocked("""{"Status":0,"Answer":[{"name":"example.com","type":1,"TTL":300,"data":"93.184.216.34"}]}"""))
        assertFalse(CloudflareMalwareDns.parseBlocked("""{"Status":3}""")) // nonexistent name: unknown, not "malware"
        assertFalse(CloudflareMalwareDns.parseBlocked("""{"Status":0,"Comment":["EDE(22): No Reachable Authority"]}"""))
    }

    @Test fun onlyPublicWebsiteNamesAreSent() {
        assertEquals("example.com", CloudflareMalwareDns.queryName("Example.com."))
        assertEquals("xn--bcher-kva.example", CloudflareMalwareDns.queryName("bücher.example"))
        listOf("localhost", "192.168.1.1", "8.8.8.8", "[::1]", "intranet", "nas.local", "2130706433")
            .forEach { assertNull(it, CloudflareMalwareDns.queryName(it)) }
    }
}
