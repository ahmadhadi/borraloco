# BorraLoco

An Android app that removes tracking from links before you share them: X/Twitter, Spotify,
Instagram, Threads, Snapchat, TikTok, YouTube, Temu, SHEIN, AliExpress, Amazon, Facebook,
Reddit, LinkedIn, Pinterest and more, plus `utm_*`, `fbclid`, `gclid` and other common
trackers on every website.

**Download:** grab the latest APK from [Releases](../../releases/latest) and open it on your
phone (allow "Install unknown apps" for your browser or file manager when asked).

```
https://x.com/NASA/status/1834567890123456789?s=46&t=AbCdEfGhIjKlMnOp
→ https://x.com/NASA/status/1834567890123456789

https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=1a2b3c4d5e6f4a7b
→ https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT

https://www.temu.com/earbuds-g-601099527781234.html?_x_sessn_id=…&refer_page_name=…
→ https://www.temu.com/earbuds-g-601099527781234.html
```

## Ways to use it

| Entry point | How it works |
|---|---|
| **Share menu** | In any app tap *Share → Clean link*. A sheet shows the clean link with Copy / Share / Open. |
| **Text selection** | Select a link, choose *Clean link*. In an editable text box the link is replaced in place. |
| **Quick Settings tile** | Copy a link, tap the tile: the clean link replaces it on the clipboard. |
| **Clean every link I open** (optional) | BorraLoco registers as a browser. Set it as the default browser and every tapped link is cleaned, then forwarded to the real browser, or to the platform's own app. |
| **In the app** | Paste any text; every link in it is cleaned. |

Short links (`t.co`, `temu.to`, `vm.tiktok.com`, `spotify.link`, `snapchat.com/t/…`,
`amzn.to`, `bit.ly`…) hide their trackers, so with **Expand short links** on (the default) the
app follows the redirect to find the real address, then cleans it. That request goes to the
short link's server; it uses HEAD where possible and sends no cookies. Turn it off in Settings
to keep the app fully offline.

## Open and run

1. Install **Android Studio** (current stable).
2. *File → Open* and select this `BorraLoco` folder. Let Gradle sync.
   If Studio offers to upgrade the Android Gradle Plugin or library versions, accept.
3. Plug in a phone with USB debugging on (or start an emulator) and press **Run ▶**.

Requirements: Android 8.0 (API 26) or newer on the phone.

## Tests

The cleaning engine is a plain Kotlin module (`:core`) with no Android dependency, so its tests
run in about a second:

```
./gradlew :core:test
```

`LinkCleanerTest` holds real-shaped links from each platform as *dirty → expected clean* pairs.
When a platform changes its share links, paste a new one there first.

## Project layout

```
core/                         Pure Kotlin – the cleaning engine
  ParsedUrl.kt                Lenient URL parser that never re-encodes what it keeps
  Rules.kt                    Rule model: site rules, redirect wrappers, shorteners
  BuiltInRules.kt             ← all platform rules live here
  LinkCleaner.kt              unwrap → expand → strip pipeline; cleans links inside text
  RedirectResolver.kt         Follows short-link redirects (HEAD, then GET / meta refresh)
  UrlExtractor.kt             Finds links in free text, trims trailing punctuation
app/                          Android app (Jetpack Compose, Material 3)
  CleanActivity.kt            Share / text-selection / link-interception entry point
  ClipboardCleanActivity.kt   Clipboard cleaning (opened by the tile)
  CleanClipboardTileService   Quick Settings tile
  MainActivity.kt             Home screen: manual cleaning, how-to, settings
  LinkActions.kt              Copy / share / open (never loops back into the app)
  AppSettings.kt              Preferences; toggles the browser alias
```

## Adding or fixing a platform

Edit `core/.../BuiltInRules.kt`:

- **Tracking parameters** → add a `SiteRule` with `removeParams` (wildcards allowed: `"pf_rd_*"`).
  For platforms whose links never need parameters, use `keepOnly = setOf(...)` instead:
  everything not listed is dropped (this is how Spotify, TikTok and Temu are handled).
- **Tracking in the path** (Amazon's `/ref=…`) → `pathRewrites`.
- **Redirect wrappers** (`l.facebook.com/l.php?u=…`) → `RedirectRule`.
- **Short links** → `ShortenerRule("host", "optional path regex")`.

Then add a test case and run `./gradlew :core:test`.

For much broader coverage you can import the community-maintained
[ClearURLs rules](https://github.com/ClearURLs/Rules) (`data.min.json`) and map them onto these
classes. Check its licence (LGPL-3.0) fits how you plan to distribute the app.

## Releases

GitHub Actions builds the APK on every push to `main`. When `versionName` in
`app/build.gradle.kts` has no release yet, it publishes release `v<versionName>` with the APK
attached. To ship an update, bump `versionCode` and `versionName`, then push.

By default the APK is signed
with a throwaway debug key, so a new release won't install over an old one without uninstalling
first. To sign every release with the same key, create a keystore once
(*Build → Generate Signed App Bundle → Create new*) and add these repository secrets:
`CLEANLINK_KEYSTORE_BASE64` (the .jks file, base64-encoded), `CLEANLINK_KEYSTORE_PASSWORD`,
`CLEANLINK_KEY_ALIAS`, `CLEANLINK_KEY_PASSWORD`. Keep a backup of that keystore: losing it means
users can't update.

## Publishing on Google Play

- **Google Play**: one-time $25 developer account, a privacy policy URL, and the Data safety form.
  The app collects no data; the only network use is optional short-link expansion.
- **F-Droid** is an alternative if you open-source the code.

## Known limits

- Links that open directly in an installed app through verified App Links (e.g. a tapped x.com
  link that opens the X app) bypass the default-browser mode; use Share instead.
- Android 10+ only lets the focused app read the clipboard, which is why the tile briefly opens
  an invisible screen. There is no way to clean the clipboard silently in the background.
- Platforms change their parameters over time; keep the rules and tests up to date.
