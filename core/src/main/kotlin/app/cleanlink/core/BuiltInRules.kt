package app.cleanlink.core

/**
 * The rules that ship with the app.
 *
 * To support a new platform, add a [SiteRule] (and a [ShortenerRule] if it has its own short
 * links), then add a test case in `LinkCleanerTest` with a real link shared from that app.
 */
object BuiltInRules {

    /** Tracking parameters that never carry page content, on any site. */
    private val GLOBAL = listOf(
        // Campaign tags
        "utm_*", "mtm_*", "pk_campaign", "pk_kwd", "pk_source", "pk_medium", "pk_content",
        "hsa_*", "itm_*",
        // Ad click IDs
        "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "msclkid", "yclid", "ttclid",
        "twclid", "li_fat_id", "epik", "ScCid", "rdt_cid", "irclickid", "_kx",
        // Social share IDs that leak onto other sites
        "igshid", "igsh", "mibextid",
        // Email / marketing automation
        "mc_cid", "mc_eid", "mkt_tok", "_hsenc", "_hsmi", "__hssc", "__hstc", "__hsfp",
        "hsCtaTracking", "oly_anon_id", "oly_enc_id", "vero_id", "vero_conv", "rb_clickid",
        "_bta_tid", "_bta_c", "trk_contact", "trk_msg", "trk_module", "trk_sid", "wickedid",
        // Analytics cross-domain IDs
        "_ga", "_gl", "_openstat", "s_cid", "spm_id_from",
    )

    private val SITES = listOf(
        SiteRule(
            "X / Twitter",
            hosts = listOf("x.com", "twitter.com", "fxtwitter.com", "vxtwitter.com", "fixupx.com"),
            removeParams = listOf("a", "s", "t", "ref_src", "ref_url", "src", "cn", "cxt", "refsrc"),
        ),
        SiteRule(
            "Spotify",
            hosts = listOf("open.spotify.com", "play.spotify.com"),
            keepOnly = setOf("highlight", "nd"),
        ),
        SiteRule(
            "Instagram",
            hosts = listOf("instagram.com"),
            keepOnly = setOf("img_index", "hl"),
        ),
        SiteRule(
            "Threads",
            hosts = listOf("threads.net", "threads.com"),
            keepOnly = setOf("hl"),
        ),
        SiteRule(
            "Facebook",
            hosts = listOf("facebook.com", "fb.watch", "fb.com"),
            removeParams = listOf(
                "mibextid", "rdid", "share_url", "sfnsn", "extid", "ref", "refid", "fref", "hc_ref",
                "__tn__", "__cft__*", "__xts__*", "eav", "paipv", "_rdr", "_rdc", "notif_id",
                "notif_t", "acontext", "aref", "comment_tracking", "dti", "hc_location",
                "_ft_", "mds", "wtsid", "privacy_mutation_token",
            ),
        ),
        SiteRule(
            "Messenger",
            hosts = listOf("messenger.com", "m.me"),
            removeParams = listOf("ref", "source"),
        ),
        SiteRule(
            "Snapchat",
            hosts = listOf("snapchat.com"),
            removeParams = listOf(
                "share_id", "sid", "locale", "sender_web_id", "device_type", "device_id",
                "is_copy_url", "link_type", "ref", "web_client_id", "lang",
            ),
        ),
        SiteRule(
            "TikTok",
            hosts = listOf("tiktok.com"),
            keepOnly = setOf("q", "lang"),
        ),
        SiteRule(
            "YouTube",
            hosts = listOf("youtube.com", "youtu.be", "youtube-nocookie.com"),
            removeParams = listOf(
                "si", "feature", "pp", "ab_channel", "app", "kw", "embeds_referring_euri",
                "embeds_referring_origin", "source_ve_path", "attr_tag", "gclid", "pbjreload",
                "cbrd", "ucbcb", "persist_app", "noapp", "has_verified",
            ),
        ),
        // Temu adds a different set of share/session tags almost every release, so it uses an
        // allow-list: only the parameters below survive. Notes on what we've seen:
        // docs/tracking-parameters.md
        SiteRule(
            "Temu",
            hosts = listOf("temu.com"),
            keepOnly = setOf("goods_id", "search_key", "mall_id", "page_sn", "sku_id"),
        ),
        SiteRule(
            "SHEIN",
            hosts = listOf("shein.com"),
            removeParams = listOf(
                "src_identifier", "src_module", "src_tab_page_id", "scici", "ici", "share_from",
                "url_from", "onelink", "requestId", "pageListType", "share_type", "share_source",
                "sharelink", "ref", "lang_source", "mallCode", "imgRatio", "shc",
            ),
        ),
        // Notes: docs/tracking-parameters.md. Keeps merchantId/boutiqueId (they pick the seller
        // and campaign whose price is shown) and search/filter parameters.
        SiteRule(
            "Trendyol",
            hosts = listOf("trendyol.*"),
            removeParams = listOf(
                "link_*",        // link_userID = the sharer's account ID, link_contentid
                "adjust_*",      // Adjust app-install attribution
                "timestamp",     // when the share was made
                "sav", "pi", "fromDeeplink", "deeplink_source", "channelId",
            ),
        ),
        SiteRule(
            "AliExpress",
            hosts = listOf("aliexpress.com", "aliexpress.us", "aliexpress.ru"),
            removeParams = listOf(
                "spm", "scm", "scm_id", "scm-url", "pvid", "algo_*", "aff_*", "sk", "terminal_id",
                "gatewayAdapt", "_randl_*", "gps-id", "utparam*", "pdp_*", "btsid", "ws_ab_test",
                "curPageLogUid", "srcSns", "spreadType", "bizType", "social_params", "tt",
                "aff_fcid", "aff_fsk", "aff_platform", "aff_trace_key", "businessType", "afSmartRedirect",
                "_t", "mb", "invitationCode", "shareId", "image", "spreadCode", "platform", "channel", "sourceType",
            ),
        ),
        SiteRule(
            "Amazon",
            hosts = listOf("amazon.*"),
            removeParams = listOf(
                "tag", "ref", "ref_", "pf_rd_*", "pd_rd_*", "qid", "sr", "keywords", "crid",
                "sprefix", "content-id", "linkCode", "linkId", "_encoding", "smid", "spIA",
                "sp_csd", "dib", "dib_tag", "creative", "creativeASIN", "ascsubtag", "camp",
                "social_share", "starsLeft", "ie", "dchild", "sbo", "trk", "asc_*",
                "asin", "revisionId", "store_ref", "nodl", "s", "rnid", "dplnkId", "srs",
            ),
            pathRewrites = listOf(Regex("/ref=[^/?#]*") to ""),
        ),
        SiteRule(
            "Reddit",
            hosts = listOf("reddit.com", "redd.it"),
            removeParams = listOf(
                "share_id", "rdt", "ref", "ref_source", "ref_campaign", "\$deep_link", "\$3p",
                "_branch_*", "%24deep_link", "post_fullname", "post_index", "correlation_id",
            ),
        ),
        SiteRule(
            "LinkedIn",
            hosts = listOf("linkedin.com"),
            removeParams = listOf(
                "trk", "trkInfo", "trackingId", "lipi", "licu", "refId", "midToken", "midSig",
                "eid", "otpToken", "originalSubdomain", "rcm", "trkEmail", "ssid", "lgCta", "lgTemp",
                "contextUrn", "src", "veh", "ref",
            ),
        ),
        SiteRule(
            "Pinterest",
            hosts = listOf("pinterest.*"),
            removeParams = listOf(
                "invite_code", "sender", "sfo", "mweb_unauth_id", "nic_v*", "utm_*", "ref",
            ),
        ),
        SiteRule(
            "Telegram",
            hosts = listOf("t.me", "telegram.me"),
            removeParams = listOf("ref"),
        ),
        SiteRule(
            "Twitch",
            hosts = listOf("twitch.tv"),
            removeParams = listOf("tt_medium", "tt_content", "sr", "tt_campaign"),
        ),
        SiteRule(
            "SoundCloud",
            hosts = listOf("soundcloud.com"),
            removeParams = listOf("si", "ref", "p", "c"),
        ),
        SiteRule(
            "Apple Music / App Store",
            hosts = listOf("music.apple.com", "apps.apple.com", "itunes.apple.com", "podcasts.apple.com"),
            removeParams = listOf("ls", "at", "ct", "itscg", "itsct", "uo", "app", "pt", "mt"),
        ),
        SiteRule(
            "Netflix",
            hosts = listOf("netflix.com"),
            removeParams = listOf("trkid", "tctx", "trackId", "s", "vlang", "trg"),
        ),
        SiteRule(
            "Google Search",
            hosts = listOf("google.*"),
            removeParams = listOf(
                "ved", "ei", "sa", "usg", "oq", "gs_lcp", "gs_lp", "gs_lcrp", "gs_ssp", "sclient",
                "sxsrf", "uact", "source", "sourceid", "ie", "aqs", "bih", "biw", "dpr", "client",
                "rlz", "sca_esv", "sca_upv", "iflsig", "oi", "fbs", "uds", "stick", "cshid",
                "sei", "prmd", "spell", "cd", "cad", "rct", "esrc", "opi", "atyp", "zx", "no_sw_cr",
            ),
        ),
        SiteRule(
            "eBay",
            hosts = listOf("ebay.*"),
            removeParams = listOf(
                "_trkparms", "_trksid", "hash", "itmmeta", "mkcid", "mkevt", "mkrid", "campid",
                "toolid", "customid", "amdata", "_from",
            ),
        ),
        SiteRule(
            "Medium",
            hosts = listOf("medium.com"),
            removeParams = listOf("source", "sk"),
        ),
        SiteRule(
            "Bilibili",
            hosts = listOf("bilibili.com"),
            removeParams = listOf(
                "spm_id_from", "vd_source", "from_spmid", "share_source", "share_medium",
                "share_plat", "share_session_id", "share_tag", "unique_k", "bbid", "ts", "from",
                "seid", "buvid", "up_id", "timestamp",
            ),
        ),
        SiteRule(
            "Discord / Steam / misc. referrals",
            hosts = listOf("discord.com", "steampowered.com", "steamcommunity.com", "imdb.com"),
            removeParams = listOf("ref_", "ref", "snr", "pf_rd_*"),
        ),
        SiteRule(
            "Booking",
            hosts = listOf("booking.com"),
            removeParams = listOf("aid", "label", "sid", "srpvid", "srepoch", "ucfs", "matching_block_id", "all_sr_blocks", "highlighted_blocks", "hpos", "hapos", "sr_order"),
        ),
    )

    private val REDIRECTS = listOf(
        RedirectRule("Facebook", listOf("l.facebook.com", "lm.facebook.com"), "/l\\.php", listOf("u")),
        RedirectRule("Messenger", listOf("l.messenger.com"), "/l\\.php", listOf("u")),
        RedirectRule("Instagram", listOf("l.instagram.com"), "/?.*", listOf("u")),
        RedirectRule("Threads", listOf("l.threads.net", "l.threads.com"), "/?.*", listOf("u")),
        RedirectRule("Google", listOf("google.*"), "/url", listOf("q", "url")),
        RedirectRule("YouTube", listOf("youtube.com"), "/redirect", listOf("q")),
        RedirectRule("Reddit", listOf("out.reddit.com"), "/.*", listOf("url")),
        RedirectRule("LinkedIn", listOf("linkedin.com"), "/redir/redirect/?", listOf("url")),
        RedirectRule("Tumblr", listOf("t.umblr.com"), "/redirect", listOf("z")),
        RedirectRule("VK", listOf("vk.com", "away.vk.com"), "/away\\.php", listOf("to")),
        RedirectRule("Steam", listOf("steamcommunity.com"), "/linkfilter/?", listOf("url", "u")),
        RedirectRule("Slack", listOf("slack-redir.net"), "/link", listOf("url")),
        RedirectRule("DuckDuckGo", listOf("duckduckgo.com"), "/l/?", listOf("uddg")),
        RedirectRule("Disqus", listOf("disq.us"), "/url", listOf("url")),
        RedirectRule("Pinterest", listOf("pinterest.*"), "/offsite/?", listOf("url")),
        RedirectRule("Snapchat", listOf("snapchat.com"), "/redirect/?", listOf("url", "u")),
        RedirectRule("TikTok", listOf("tiktok.com"), "/link/v2", listOf("target")),
        RedirectRule("Yandex", listOf("yandex.*"), "/clck/.*", listOf("url")),
        // Adjust (app-install attribution) tracker links, used by Trendyol and many other apps.
        // The real web page is in adjust_redirect; adjust_deeplink is the in-app version.
        RedirectRule("Adjust", listOf("adj.st"), "/.*", listOf("adjust_redirect", "adjust_fallback")),
        RedirectRule("Adjust", listOf("app.adjust.com"), "/.*", listOf("redirect", "fallback")),
    )

    private val SHORTENERS = listOf(
        // Platform shorteners
        ShortenerRule("t.co"),
        ShortenerRule("spotify.link"),
        ShortenerRule("spoti.fi"),
        ShortenerRule("vm.tiktok.com"),
        ShortenerRule("vt.tiktok.com"),
        ShortenerRule("tiktok.com", "/t/.+"),
        ShortenerRule("temu.to"),
        ShortenerRule("share.temu.com"),
        ShortenerRule("t.snapchat.com"),
        ShortenerRule("snapchat.com", "/t/.+"),
        ShortenerRule("fb.me"),
        ShortenerRule("facebook.com", "/share/.+"),
        ShortenerRule("instagram.com", "/share/.+"),
        ShortenerRule("reddit.com", "/r/[^/]+/s/.+"),
        ShortenerRule("amzn.to"),
        ShortenerRule("amzn.eu"),
        ShortenerRule("amzn.asia"),
        ShortenerRule("a.co"),
        ShortenerRule("lnkd.in"),
        ShortenerRule("pin.it"),
        ShortenerRule("on.soundcloud.com"),
        ShortenerRule("s.click.aliexpress.com"),
        ShortenerRule("a.aliexpress.com"),
        ShortenerRule("click.aliexpress.com"),
        ShortenerRule("ty.gl"),           // Trendyol
        ShortenerRule("shein.top"),
        ShortenerRule("onelink.shein.com"),
        ShortenerRule("api-shein.shein.com", "/h5/sharejump/.*"),
        ShortenerRule("ebay.us"),
        ShortenerRule("youtube.com", "/shorts_redirect.*"),
        ShortenerRule("threads.net", "/share/.+"),
        ShortenerRule("app.link"),        // Branch.io deep links
        ShortenerRule("page.link"),       // Firebase dynamic links
        ShortenerRule("onelink.me"),      // AppsFlyer
        // Generic shorteners
        ShortenerRule("bit.ly"),
        ShortenerRule("bitly.com"),
        ShortenerRule("tinyurl.com"),
        ShortenerRule("goo.gl"),
        ShortenerRule("ow.ly"),
        ShortenerRule("buff.ly"),
        ShortenerRule("is.gd"),
        ShortenerRule("rebrand.ly"),
        ShortenerRule("cutt.ly"),
        ShortenerRule("shorturl.at"),
        ShortenerRule("tiny.cc"),
        ShortenerRule("trib.al"),
        ShortenerRule("dlvr.it"),
    )

    val ruleSet = RuleSet(GLOBAL, SITES, REDIRECTS, SHORTENERS)
}
