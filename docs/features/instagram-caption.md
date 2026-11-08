# Copy Instagram caption

Share an Instagram post or reel to **Copy Instagram caption** (it appears in the Android Share menu next to "Clean link"). The app reads the post's public page, copies the caption and shows it, with Copy and Share buttons.

## How it works
- `InstagramCaptions.parsePost` accepts only `/p/`, `/reel/`, `/reels/`, `/tv/` links on instagram.com / instagr.am. Anything else is not requested.
- `InstagramCaptionFetcher` requests only `https://www.instagram.com/p/<shortcode>/`. The `igsh`/`utm_*` tracking of the shared link is never sent. No cookies, no login, no referrer. Redirects are followed by hand and never leave Instagram's hosts.
- The caption is read from the page header (`og:description` / `og:title`), and from `/p/<shortcode>/embed/captioned/` when the header has none.
- If Instagram answers with a login page or 401/403, the app says so. It does **not** try to get around that.

## Limits (read before relying on it)
- **Public posts only.** Private accounts and anything Instagram hides behind a login cannot be read.
- **Not verified against live Instagram.** The build environment cannot reach instagram.com, so the parsers are tested against pages written by hand in the known format (`InstagramCaptionTest`, `InstagramCaptionFetcherTest`). Instagram changes its pages and may refuse anonymous requests at any time; when it does, the app shows "couldn't read" or "login required" rather than guessing.
- The header preview can shorten long captions; the embed page usually has the full text.
- The request identifies itself with a link-preview crawler user agent (`facebookexternalhit/1.1`), the way chat apps fetch previews, because that is the form in which Instagram publishes captions in its page header.
- Contacting instagram.com means Instagram sees the request from the phone's IP address.
