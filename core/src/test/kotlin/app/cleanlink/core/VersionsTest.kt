package app.cleanlink.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test fun newer() {
        assertTrue(Versions.isNewer("v1.4", "1.3"))
        assertTrue(Versions.isNewer("1.10", "1.9"))
        assertTrue(Versions.isNewer("2.0", "1.99"))
        assertTrue(Versions.isNewer("v1.3.1", "1.3"))
    }

    @Test fun notNewer() {
        assertFalse(Versions.isNewer("v1.3", "1.3"))
        assertFalse(Versions.isNewer("1.3.0", "1.3"))
        assertFalse(Versions.isNewer("1.2", "1.3"))
        assertFalse(Versions.isNewer("garbage", "1.3"))
        assertFalse(Versions.isNewer("", "1.3"))
    }
}
