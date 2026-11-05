# Inspect link: expand URL + safety check (unreleased)

Status: **on branch `feature/expand-and-safety`, not released.** No version bump. It ships in a later feature release.

## What it does
Share a link to **Inspect link** (or press **Inspect** in the app). It:
1. Follows the link one hop at a time (short links, Adjust/Trendyol-style chains, redirect wrappers) and lists every hop.
2. Cleans the final destination (the normal tracker removal).
3. Runs offline warning-sign checks on the start and end of the chain, and shows a verdict: *No warning signs found*, *Be careful*, or *Looks dangerous*.

## Checks (all offline, in `core`)
Text before `@` hiding the real host, number / disguised-number addresses, local-network addresses, mixed alphabets (e.g. Cyrillic `а` in `аpple.com`), brand names on someone else's site, brand typos (`paypa1`), brand + login/verify words, throwaway endings, `.zip`/`.mov` endings, odd ports, program downloads (`.apk`, `.exe`…), dangerous schemes (`javascript:`, `data:`), long or hyphen-heavy names.

## Privacy
- Following redirects contacts the link's own servers (same as expanding short links today). No cookies, no referrer.
- **Optional, off by default:** ask Cloudflare's malware-blocking DNS (`security.cloudflare-dns.com`) about the site name. Only the host name is sent, never the full link, and never for IPs or local names.
- The app never says a link is "safe"; only "no warning signs found".

## Safety of the feature itself
The resolver refuses to request localhost, LAN, link-local and similar addresses (including disguised spellings like `2130706433` or `0x7f.1`), so a link cannot make the phone probe your home network.

## Known limits
- The Cloudflare response parsing is tested against hand-written examples only; **not verified against the live service** (the build sandbox cannot reach it). Check it on a phone before releasing.
- The Android screens are compile-checked by CI only; not tried on a phone.
- The brand list is small (~27 brands, incl. Absher, Al Rajhi, stc) and heuristic: it will miss attacks and can occasionally warn about a harmless link.
