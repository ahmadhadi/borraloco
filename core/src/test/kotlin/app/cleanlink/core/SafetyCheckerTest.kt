package app.cleanlink.core

import app.cleanlink.core.FindingCode.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyCheckerTest {

    private val checker = SafetyChecker()

    private fun codes(url: String, dns: Boolean = false) = checker.check(url, dns).findings.map { it.code }
    private fun verdict(url: String) = checker.check(url).verdict

    // ---- No false alarms on ordinary links ---------------------------------------------------

    @Test fun ordinaryLinksHaveNoWarnings() {
        listOf(
            "https://www.paypal.com/signin",
            "https://www.amazon.co.uk/dp/B09B8V1LZ3",
            "https://www.amazon.sa/-/en/dp/B0ABCDE123",
            "https://github.com/square/okhttp",
            "https://accounts.google.com/signin",
            "https://www.google.com.sa/search?q=cats",
            "https://en.wikipedia.org/wiki/URL",
            "https://x.com/NASA/status/1",
            "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://support.apple.com/en-us/HT201994",
            "https://appleinsider.com/articles/1",
            "https://www.trendyol.sa/redtag/jeans-p-1157982162",
            "https://www.temu.com/sa-en/goods.html?goods_id=601100218100590",
            "https://www.snapchat.com/add/someone",
            "https://www.linkedin.com/posts/someone_activity-7212345678901234567-AbCd",
            "https://www.dhl.de/en/privatkunden.html",
            "https://mail.google.com/mail/u/0/",
        ).forEach {
            assertEquals(it, Verdict.NO_WARNINGS, verdict(it))
            assertTrue(it, codes(it).none { c -> c.severity != Severity.LOW })
        }
    }

    // ---- Things that should raise an alarm ---------------------------------------------------

    @Test fun textBeforeAtSignHidesTheRealSite() {
        val r = checker.check("https://paypal.com@evil.example/login")
        assertTrue(USERINFO_IN_LINK in r.findings.map { it.code })
        assertEquals(Verdict.DANGER, r.verdict)
    }

    @Test fun fakeLoginPageOnAnotherSite() {
        listOf(
            "https://paypal-login-verify.example.com/",
            "https://apple.com.id-check.xyz/login",
            "https://amazon.top/signin",
            "https://www.stc-pay.example.org/verify",
            "https://aramex-tracking.example.net/parcel",
        ).forEach {
            assertTrue(it, BRAND_PHISHING_WORDS in codes(it))
            assertEquals(it, Verdict.DANGER, verdict(it))
        }
    }

    @Test fun misspelledBrandNames() {
        listOf("https://paypa1.com/", "https://www.g00gle.com/", "https://rnicrosoft.com/account", "https://netfl1x.example.com/")
            .forEach {
                assertTrue(it, BRAND_TYPO in codes(it))
                assertEquals(it, Verdict.DANGER, verdict(it))
            }
    }

    @Test fun brandNameOnSomeoneElsesSiteIsACaution() {
        val r = checker.check("https://google.evil.com/")
        assertEquals(listOf(BRAND_LOOKALIKE), r.findings.map { it.code })
        assertEquals("Google", r.findings.single().detail)
        assertEquals(Verdict.CAUTION, r.verdict)
    }

    @Test fun lookalikeAlphabets() {
        // "аpple.com" with a Cyrillic "а" mixed into Latin letters.
        val r = checker.check("https://аpple.com/")
        assertTrue(LOOKALIKE_CHARACTERS in r.findings.map { it.code })
        assertEquals(Verdict.DANGER, r.verdict)
        // The same name as punycode is decoded and caught too.
        assertTrue(LOOKALIKE_CHARACTERS in codes("https://xn--pple-43d.com/"))
        // A name entirely in one other alphabet is only a caution.
        val arabic = checker.check("https://موقع.example/")
        assertEquals(listOf(INTERNATIONAL_DOMAIN), arabic.findings.map { it.code })
        assertEquals(Verdict.CAUTION, arabic.verdict)
    }

    @Test fun bareAndDisguisedIpAddresses() {
        assertTrue(IP_ADDRESS in codes("https://1.2.3.4/login"))
        assertEquals(Verdict.CAUTION, verdict("https://1.2.3.4/login"))

        val hidden = codes("http://3232235777/")
        assertTrue(OBFUSCATED_IP in hidden)
        assertTrue(PRIVATE_ADDRESS in hidden)
        assertEquals(Verdict.DANGER, verdict("http://3232235777/"))

        assertTrue(PRIVATE_ADDRESS in codes("http://[::1]:8080/"))
        assertTrue(PRIVATE_ADDRESS in codes("http://localhost/admin"))
    }

    @Test fun programDownloads() {
        val r = checker.check("https://files.example.com/update/app.apk")
        assertEquals(PROGRAM_DOWNLOAD, r.findings.single().code)
        assertEquals(".apk", r.findings.single().detail)
        assertEquals(Verdict.CAUTION, r.verdict)
        assertTrue(PROGRAM_DOWNLOAD in codes("https://example.com/setup.EXE"))
    }

    @Test fun fileLikeEndings() {
        assertTrue(FILE_TLD in codes("https://invoice.zip/"))
        assertEquals(Verdict.CAUTION, verdict("https://invoice.zip/"))
    }

    @Test fun weakSignsAddUpButAreNotAlarmingAlone() {
        assertEquals(Verdict.NO_WARNINGS, verdict("http://example.com/"))
        assertEquals(Verdict.NO_WARNINGS, verdict("https://example.tk/"))
        val several = checker.check("http://example.tk:8080/")
        assertEquals(setOf(NO_HTTPS, SCAM_TLD, ODD_PORT), several.findings.map { it.code }.toSet())
        assertEquals(Verdict.CAUTION, several.verdict)
        assertTrue(TOO_MANY_SUBDOMAINS in codes("https://a.b.c.d.e.example.com/"))
        assertTrue(MANY_HYPHENS in codes("https://my-very-own-shop-site.example/"))
        assertTrue(LONG_HOST in codes("https://" + "a".repeat(40) + "." + "b".repeat(30) + ".example.com/"))
    }

    @Test fun shortLinksAndWrappersAreNoted() {
        assertEquals(listOf(SHORT_LINK), codes("https://bit.ly/abc"))
        assertEquals(listOf(SHORT_LINK), codes("https://ty.gl/abc123"))
        assertTrue(REDIRECT_WRAPPER in codes("https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.org%2F"))
        assertEquals(Verdict.NO_WARNINGS, verdict("https://bit.ly/abc"))
    }

    @Test fun notWebLinks() {
        val js = checker.check("javascript:alert(1)")
        assertEquals(DANGEROUS_SCHEME, js.findings.single().code)
        assertEquals("javascript", js.findings.single().detail)
        assertEquals(Verdict.DANGER, js.verdict)
        assertEquals(Verdict.DANGER, verdict("data:text/html;base64,PHNjcmlwdD4="))
        assertEquals(listOf(INTENT_LINK), codes("intent://scan/#Intent;scheme=zxing;end"))
        assertEquals(listOf(APP_LINK), codes("tg://resolve?domain=durov"))
        assertEquals(listOf(NOT_A_LINK), codes("just some words"))
    }

    @Test fun outsideBlocklistHitIsDanger() {
        val r = checker.check("https://example.com/", blockedByDns = true)
        assertEquals(listOf(DNS_BLOCKED), r.findings.map { it.code })
        assertEquals(Verdict.DANGER, r.verdict)
        assertFalse(DNS_BLOCKED in codes("https://example.com/"))
    }

    @Test fun eachWarningIsReportedOnce() {
        val r = checker.check("http://paypal-login.example.com@paypal-verify.example.net/login")
        assertEquals(r.findings.size, r.findings.map { it.code }.toSet().size)
    }
}
