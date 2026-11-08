# Copy caption (Instagram, TikTok)

Share an Instagram post or reel, or a TikTok video, to **Copy caption** (it appears in the Android Share menu next to "Clean link", and as its own Direct Share tile once the app has been opened). The app reads the post's public page, copies the caption and shows it, with Copy and Share buttons.

## How it works
- `InstagramCaptions.parsePost` accepts only `/p/`, `/reel/`, `/reels/`, `/tv/` links on instagram.com / instagr.am. Anything else is not requested.
- `InstagramCaptionFetcher` requests only `https://www.instagram.com/p/<shortcode>/`. The `igsh`/`utm_*` tracking of the shared link is never sent. No cookies, no login, no referrer. Redirects are followed by hand and never leave Instagram's hosts.
- The caption is read from the page header (`og:description` / `og:title`), and from `/p/<shortcode>/embed/captioned/` when the header has none.
- If Instagram answers with a login page or 401/403, the app says so. It does **not** try to get around that.

## TikTok
- `TikTokCaptions.parseLink` accepts `@user/video/<id>` (and `/photo/`) links and the short forms `vm.tiktok.com`, `vt.tiktok.com`, `tiktok.com/t/…`. Short links are followed by hand, only inside TikTok's hosts, to find the video.
- The caption is read from TikTok's public **oEmbed** endpoint (`https://www.tiktok.com/oembed?url=<video>`, documented by TikTok; the caption is the `title` field). Only the clean video address is sent. If oEmbed gives nothing, the video page's embedded data is read as a fallback.
- Not verified against live TikTok from the build environment (the oEmbed response shape is checked against TikTok's own documented example).

## Share menu
The "Copy caption" entry has its own name and icon and is also published as a sharing shortcut (`CaptionShortcut`, `res/xml/shortcuts.xml`) so phones that support Direct Share can show it as a separate tile. Android decides what the share sheet shows; some phones group an app's entries together.

## Limits (read before relying on it)
- **Public posts only.** Private accounts and anything Instagram hides behind a login cannot be read.
- **Instagram: tested by hand on a phone (works for public posts), automated tests use hand-written pages.** The build environment cannot reach instagram.com, so the parsers are tested against pages written by hand in the known format (`InstagramCaptionTest`, `InstagramCaptionFetcherTest`). Instagram changes its pages and may refuse anonymous requests at any time; when it does, the app shows "couldn't read" or "login required" rather than guessing.
- The header preview can shorten long captions; the embed page usually has the full text.
- The request identifies itself with a link-preview crawler user agent (`facebookexternalhit/1.1`), the way chat apps fetch previews, because that is the form in which Instagram publishes captions in its page header.
- Contacting instagram.com means Instagram sees the request from the phone's IP address.
