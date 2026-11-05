package app.cleanlink.core

import java.net.IDN

enum class Severity { LOW, MEDIUM, HIGH }

enum class Verdict { NO_WARNINGS, CAUTION, DANGER }

/** One kind of warning sign. Each code has a fixed severity; the app shows a message per code. */
enum class FindingCode(val severity: Severity) {
    NOT_A_LINK(Severity.LOW),
    DANGEROUS_SCHEME(Severity.HIGH),
    INTENT_LINK(Severity.MEDIUM),
    APP_LINK(Severity.LOW),
    NO_HTTPS(Severity.LOW),
    USERINFO_IN_LINK(Severity.HIGH),
    IP_ADDRESS(Severity.MEDIUM),
    OBFUSCATED_IP(Severity.HIGH),
    PRIVATE_ADDRESS(Severity.MEDIUM),
    ENCODED_HOST(Severity.MEDIUM),
    LOOKALIKE_CHARACTERS(Severity.HIGH),
    INTERNATIONAL_DOMAIN(Severity.MEDIUM),
    BRAND_LOOKALIKE(Severity.MEDIUM),
    BRAND_PHISHING_WORDS(Severity.HIGH),
    BRAND_TYPO(Severity.HIGH),
    SCAM_TLD(Severity.LOW),
    FILE_TLD(Severity.MEDIUM),
    TOO_MANY_SUBDOMAINS(Severity.LOW),
    LONG_HOST(Severity.LOW),
    MANY_HYPHENS(Severity.LOW),
    ODD_PORT(Severity.LOW),
    PROGRAM_DOWNLOAD(Severity.MEDIUM),
    SHORT_LINK(Severity.LOW),
    REDIRECT_WRAPPER(Severity.LOW),
    DNS_BLOCKED(Severity.HIGH),
}

/** [detail] fills in the message: the brand, file type, port, scheme or ending involved. */
data class Finding(val code: FindingCode, val detail: String = "") {
    val severity: Severity get() = code.severity
}

data class SafetyReport(val url: String, val findings: List<Finding>) {
    /**
     * Any HIGH sign → danger. Any MEDIUM, or two LOW signs together → caution.
     * [Verdict.NO_WARNINGS] never means "safe": only that nothing in the ADDRESS looked wrong.
     */
    val verdict: Verdict
        get() = when {
            findings.any { it.severity == Severity.HIGH } -> Verdict.DANGER
            findings.any { it.severity == Severity.MEDIUM } ||
                findings.count { it.severity == Severity.LOW } >= 2 -> Verdict.CAUTION
            else -> Verdict.NO_WARNINGS
        }
}

/**
 * Looks for warning signs in a link's ADDRESS. Entirely offline: no request is made and nothing is
 * sent anywhere. It cannot see the page behind the link, so a clean result is not a guarantee.
 */
class SafetyChecker(private val rules: RuleSet = BuiltInRules.ruleSet) {

    private class Findings {
        val map = linkedMapOf<FindingCode, Finding>()
        fun add(code: FindingCode, detail: String = "") {
            map.putIfAbsent(code, Finding(code, detail))
        }
    }

    /** [blockedByDns] = an outside blocklist (see [DnsBlocklist]) flagged the site name. */
    fun check(url: String, blockedByDns: Boolean = false): SafetyReport {
        val text = url.trim()
        val f = Findings()
        val parsed = ParsedUrl.parse(text)
        if (parsed == null) {
            val scheme = SCHEME.find(text)?.groupValues?.get(1)?.lowercase()
            when {
                scheme == null -> f.add(FindingCode.NOT_A_LINK)
                scheme in DANGEROUS_SCHEMES -> f.add(FindingCode.DANGEROUS_SCHEME, scheme)
                scheme == "intent" -> f.add(FindingCode.INTENT_LINK)
                else -> f.add(FindingCode.APP_LINK, scheme)
            }
        } else {
            checkWebLink(parsed, f)
        }
        if (blockedByDns) f.add(FindingCode.DNS_BLOCKED)
        return SafetyReport(text, f.map.values.toList())
    }

    private fun checkWebLink(p: ParsedUrl, f: Findings) {
        if (!p.scheme.equals("https", ignoreCase = true)) f.add(FindingCode.NO_HTTPS)
        if ('@' in p.authority) f.add(FindingCode.USERINFO_IN_LINK)

        val host = p.host
        if ('%' in host) f.add(FindingCode.ENCODED_HOST)

        val hostPort = p.authority.substringAfterLast('@')
        val port = if (hostPort.startsWith("[")) hostPort.substringAfter("]:", "") else hostPort.substringAfter(':', "")
        if (port.isNotEmpty() && port != "80" && port != "443") f.add(FindingCode.ODD_PORT, port)

        val v4 = PrivateHosts.parseIpv4(host)
        if (v4 != null || host.startsWith("[")) {
            f.add(if (v4 != null && !DOTTED_QUAD.matches(host)) FindingCode.OBFUSCATED_IP else FindingCode.IP_ADDRESS)
            if (PrivateHosts.isPrivate(host)) f.add(FindingCode.PRIVATE_ADDRESS)
        } else {
            if (PrivateHosts.isPrivate(host)) f.add(FindingCode.PRIVATE_ADDRESS)
            checkName(host, p, f)
        }

        val path = p.path.lowercase().trimEnd('/')
        PROGRAM_EXTENSIONS.firstOrNull { path.endsWith(it) }?.let { f.add(FindingCode.PROGRAM_DOWNLOAD, it) }

        if (rules.isShortLink(p)) f.add(FindingCode.SHORT_LINK)
        if (rules.redirects.any { it.unwrap(p) != null }) f.add(FindingCode.REDIRECT_WRAPPER)
    }

    /** Checks that apply to a website NAME (not an IP address). */
    private fun checkName(host: String, p: ParsedUrl, f: Findings) {
        val labels = host.split('.')
        val tld = labels.last()

        // Lookalike characters: a name written with letters from other alphabets.
        val unicode = if (host.any { it.code > 127 } || labels.any { it.startsWith("xn--") }) {
            runCatching { IDN.toUnicode(host) }.getOrDefault(host)
        } else {
            host
        }
        if (unicode.any { it.code > 127 }) {
            val mixed = unicode.split('.').any { scriptsOf(it).size > 1 }
            f.add(if (mixed) FindingCode.LOOKALIKE_CHARACTERS else FindingCode.INTERNATIONAL_DOMAIN)
        }

        // Pretending to be a well-known brand.
        if (BRANDS.none { it.owns(host) }) {
            val hostTokens = host.split('.', '-').filter { it.isNotEmpty() }
            val pathTokens = p.path.lowercase().split(NON_ALNUM).filter { it.isNotEmpty() }
            var match: Brand? = null
            var typo = false
            for (brand in BRANDS) {
                if (hostTokens.any { it in brand.tokens }) {
                    match = brand
                    break
                }
                if (hostTokens.any { token -> typoVariants(token).any { it in brand.tokens } }) {
                    match = brand
                    typo = true
                    break
                }
            }
            if (match != null) {
                val words = (hostTokens + pathTokens).any { it in PHISHING_WORDS }
                when {
                    typo -> f.add(FindingCode.BRAND_TYPO, match.name)
                    words -> f.add(FindingCode.BRAND_PHISHING_WORDS, match.name)
                    else -> f.add(FindingCode.BRAND_LOOKALIKE, match.name)
                }
            }
        }

        when (tld) {
            "zip", "mov" -> f.add(FindingCode.FILE_TLD, tld)
            in SCAM_TLDS -> f.add(FindingCode.SCAM_TLD, ".$tld")
        }

        val registrableLabels =
            if (labels.size >= 3 && tld.length == 2 && labels[labels.size - 2] in SECOND_LEVELS) 3 else 2
        if (labels.size - registrableLabels >= 4) f.add(FindingCode.TOO_MANY_SUBDOMAINS)
        if (host.length > 60) f.add(FindingCode.LONG_HOST)
        if (labels.size >= registrableLabels && labels[labels.size - registrableLabels].count { it == '-' } >= 3) {
            f.add(FindingCode.MANY_HYPHENS)
        }
    }

    private fun scriptsOf(label: String): Set<Character.UnicodeScript> {
        val scripts = mutableSetOf<Character.UnicodeScript>()
        var i = 0
        while (i < label.length) {
            val cp = label.codePointAt(i)
            if (Character.isLetter(cp)) {
                val script = Character.UnicodeScript.of(cp)
                if (script != Character.UnicodeScript.COMMON && script != Character.UnicodeScript.INHERITED) scripts += script
            }
            i += Character.charCount(cp)
        }
        return scripts
    }

    /** Spellings a token could be a disguise for: `paypa1` → `paypal`, `rnicrosoft` → `microsoft`. */
    private fun typoVariants(token: String): List<String> {
        if (token.none { it in "0135" } && "rn" !in token && "vv" !in token) return emptyList()
        val base = token.replace("rn", "m").replace("vv", "w").replace('0', 'o').replace('3', 'e').replace('5', 's')
        return listOf(base.replace('1', 'l'), base.replace('1', 'i')).filter { it != token }
    }

    private class Brand(val name: String, val tokens: Set<String>, domains: List<String>) {
        private val exact = domains.filterNot { it.endsWith(".*") }
        private val wildcard = domains.filter { it.endsWith(".*") }.map {
            // "amazon.*": amazon.com, amazon.de, amazon.co.uk, amazon.com.au, but NOT amazon.top or amazon.evil.com
            Regex("^(?:.+\\.)?" + Regex.escape(it.removeSuffix(".*")) + "\\.(?:com|[a-z]{2}|(?:co|com|org|net|gov|edu|ac|or|ne|go|gob)\\.[a-z]{2})$")
        }

        fun owns(host: String): Boolean {
            if (exact.any { host == it || host.endsWith(".$it") }) return true
            val match = wildcard.any { it.matches(host) }
            return match && host.substringAfterLast('.') !in ABUSED_CC_TLDS
        }
    }

    private companion object {
        val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")
        val DOTTED_QUAD = Regex("\\d{1,3}(\\.\\d{1,3}){3}")
        val NON_ALNUM = Regex("[^a-z0-9]+")
        val DANGEROUS_SCHEMES = setOf("javascript", "data", "vbscript", "file", "blob")
        val SECOND_LEVELS = setOf("co", "com", "org", "net", "gov", "edu", "ac", "or", "ne", "go", "gob", "mil")
        val SCAM_TLDS = setOf("tk", "ml", "ga", "cf", "gq", "icu", "cyou", "cfd", "sbs", "rest", "click", "buzz", "monster")
        val ABUSED_CC_TLDS = setOf("tk", "ml", "ga", "cf", "gq", "cc", "ws", "pw")
        val PROGRAM_EXTENSIONS = listOf(".apk", ".apks", ".xapk", ".exe", ".msi", ".scr", ".bat", ".cmd", ".jar", ".vbs", ".ps1", ".dmg")

        /** Words that show up in fake login, delivery and prize pages. */
        val PHISHING_WORDS = setOf(
            "login", "signin", "verify", "verification", "secure", "security", "account", "update", "confirm",
            "wallet", "password", "unlock", "suspended", "support", "billing", "payment", "recover", "recovery",
            "validate", "auth", "kyc", "claim", "gift", "prize", "reward", "bonus", "track", "tracking",
            "parcel", "delivery", "customs", "shipment",
        )

        /** Well-known names that scams imitate, and the sites that really belong to them. */
        val BRANDS = listOf(
            Brand("PayPal", setOf("paypal"), listOf("paypal.com", "paypal.me", "paypalobjects.com")),
            Brand("Apple", setOf("apple", "icloud", "appleid"), listOf("apple.com", "icloud.com", "me.com", "mzstatic.com")),
            Brand(
                "Google", setOf("google", "gmail"),
                listOf("google.*", "gmail.com", "goo.gl", "gstatic.com", "googleapis.com", "googleusercontent.com", "youtube.com", "youtu.be"),
            ),
            Brand(
                "Microsoft", setOf("microsoft", "outlook", "onedrive", "office365"),
                listOf(
                    "microsoft.com", "live.com", "outlook.com", "office.com", "office365.com", "microsoftonline.com",
                    "windows.com", "msn.com", "bing.com", "xbox.com", "skype.com", "sharepoint.com", "onedrive.com",
                ),
            ),
            Brand("Amazon", setOf("amazon"), listOf("amazon.*", "amzn.to", "amzn.eu", "amzn.asia", "a.co", "amazonaws.com", "primevideo.com", "audible.com")),
            Brand("Netflix", setOf("netflix"), listOf("netflix.com", "nflxso.net", "nflximg.net")),
            Brand("Facebook", setOf("facebook"), listOf("facebook.com", "fb.com", "fb.me", "fb.watch", "messenger.com", "meta.com", "fbcdn.net")),
            Brand("Instagram", setOf("instagram"), listOf("instagram.com", "cdninstagram.com")),
            Brand("WhatsApp", setOf("whatsapp"), listOf("whatsapp.com", "whatsapp.net", "wa.me")),
            Brand("TikTok", setOf("tiktok"), listOf("tiktok.com", "tiktokcdn.com", "tiktokv.com")),
            Brand("X / Twitter", setOf("twitter"), listOf("twitter.com", "x.com", "t.co", "twimg.com")),
            Brand("LinkedIn", setOf("linkedin"), listOf("linkedin.com", "lnkd.in", "licdn.com")),
            Brand("Snapchat", setOf("snapchat"), listOf("snapchat.com", "snap.com")),
            Brand("Spotify", setOf("spotify"), listOf("spotify.com", "spoti.fi", "spotify.link", "scdn.co")),
            Brand("Telegram", setOf("telegram"), listOf("telegram.org", "t.me", "telegram.me", "telegra.ph")),
            Brand("Binance", setOf("binance"), listOf("binance.*")),
            Brand("Coinbase", setOf("coinbase"), listOf("coinbase.com")),
            Brand("DHL", setOf("dhl"), listOf("dhl.*")),
            Brand("FedEx", setOf("fedex"), listOf("fedex.com")),
            Brand("Aramex", setOf("aramex"), listOf("aramex.com")),
            Brand("AliExpress", setOf("aliexpress"), listOf("aliexpress.*", "alibaba.com", "alicdn.com")),
            Brand("Temu", setOf("temu"), listOf("temu.com", "temu.to", "kwcdn.com")),
            Brand("SHEIN", setOf("shein"), listOf("shein.*", "shein.top", "ltwebstatic.com")),
            Brand("Trendyol", setOf("trendyol"), listOf("trendyol.*", "ty.gl", "dsmcdn.com")),
            Brand("Absher", setOf("absher"), listOf("absher.sa", "moi.gov.sa")),
            Brand("Al Rajhi Bank", setOf("alrajhi", "alrajhibank"), listOf("alrajhibank.com.sa")),
            Brand("stc", setOf("stc", "stcpay"), listOf("stc.com.sa", "stcpay.com.sa")),
        )
    }
}
