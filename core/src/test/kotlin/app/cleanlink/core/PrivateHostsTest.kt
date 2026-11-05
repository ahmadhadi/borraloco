package app.cleanlink.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateHostsTest {

    @Test fun privateAddresses() {
        listOf(
            "localhost", "app.localhost", "printer.local", "nas.lan", "127.0.0.1", "127.1", "10.1.2.3",
            "172.16.0.1", "172.31.255.255", "192.168.1.1", "169.254.169.254", "100.64.0.1", "0.0.0.0",
            "224.0.0.1", "[::1]", "::1", "fe80::1", "fd00::1234", "[fc00::1]", "::ffff:127.0.0.1", "::ffff:7f00:1",
        ).forEach { assertTrue(it, PrivateHosts.isPrivate(it)) }
    }

    @Test fun disguisedSpellingsOfLocalAddresses() {
        listOf("2130706433", "0x7f.1", "0x7f000001", "017700000001", "0177.0.0.1", "3232235777")
            .forEach { assertTrue(it, PrivateHosts.isPrivate(it)) }
    }

    @Test fun publicAddresses() {
        listOf(
            "example.com", "www.trendyol.sa", "8.8.8.8", "172.32.0.1", "172.15.0.1", "100.63.0.1", "1.1.1.1",
            "[2606:4700:4700::1111]", "2606:4700::1111", "localhost.example.com", "mylocal.com", "",
        ).forEach { assertFalse(it, PrivateHosts.isPrivate(it)) }
    }

    @Test fun parsesIpv4() {
        assertEquals(0x01020304L, PrivateHosts.parseIpv4("1.2.3.4"))
        assertEquals(0x7F000001L, PrivateHosts.parseIpv4("127.1"))
        assertEquals(0xC0A80101L, PrivateHosts.parseIpv4("3232235777"))
        assertNull(PrivateHosts.parseIpv4("example.com"))
        assertNull(PrivateHosts.parseIpv4("256.1.1.1"))
        assertNull(PrivateHosts.parseIpv4("1.2.3.4.5"))
        assertNull(PrivateHosts.parseIpv4("1..2"))
        assertNull(PrivateHosts.parseIpv4("0x"))
        assertNull(PrivateHosts.parseIpv4("+5"))
    }
}
