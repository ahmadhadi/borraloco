# Clean text (hidden marks)

In the app, paste any text. Nothing is shown until the text contains something to act on:

- links → **Clean links** (and **Copy caption** for Instagram/TikTok posts);
- hidden marks → a card listing what was found, with **Remove hidden marks**;
- chatbot citation markers → **Remove citation markers** (separate, because they are visible text).

Everything runs on the phone; nothing is sent anywhere. The Share menu is unchanged.

## What is found and removed

| Mark | What it is |
|---|---|
| C2PA text provenance | `C2PATXT` suffix (U+FEFF + variation selectors), `-----BEGIN C2PA MANIFEST-----` blocks (and an enclosing `<!-- -->`), HTML `<script type="application/c2pa">` and `<link rel="c2pa-manifest">` |
| VSRMark | A payload spread over the text as variation selectors (`VSR1` header + CRC32 checked); the payload is shown |
| Hidden message | ASCII written in Unicode tag characters (U+E0000–E007F); the message is shown |
| Invisible characters | Zero-width space/joiners, word joiner, soft hyphen, invisible operators, fillers, deprecated format controls, variation selectors, private-use characters, bidi marks outside right-to-left text |
| Unusual spaces | Thin/em/en/figure/hair/medium-math spaces, ideographic space, no-break spaces → a normal space |
| Chatbot citations | ChatGPT private-use citations, `[cite: 1]`, `[span_1]`, Grok citation cards |

## What is deliberately kept

Characters that look invisible but belong to the text stay: emoji selectors and joiners (❤️, 👨‍👩‍👧, 1️⃣),
subdivision flags (🏴󠁧󠁢󠁥󠁮󠁧󠁿), ZWNJ/ZWJ inside Arabic, Persian and Indic text, bidirectional marks in
right-to-left text, ideographic variation selectors after CJK characters, no-break spaces next to numbers
and French-style punctuation, ideographic spaces in CJK text. **Two or more invisible characters in a row
are never treated as legitimate** and are removed.

Kept characters are **listed by code point** (for example `U+200F ×12`) so you can see exactly what is
there, with a **Remove these too** button for people who want everything invisible gone. It removes
bidirectional marks, no-break and CJK spaces; it still keeps emoji sequences, flags and joiners between
Arabic/Persian/Indic letters, because removing those visibly breaks the text.

## What this does not do

- **Word-choice watermarks** (Google SynthID-Text, KGW and similar) live in which words were chosen.
  Only rewriting the text changes them, and a rewrite changes the meaning and needs an AI model, so the app does not attempt it.
- A mark hidden in a way that looks like legitimate text (for example one ZWNJ between Persian letters)
  cannot be told apart from the real thing and stays.
- HTML entities that spell invisible characters are plain text here and are not decoded.
- Private-use characters are removed, including icon-font symbols; they would show as empty boxes elsewhere.

Removing AI labels or provenance data is restricted in some countries; use this on text you wrote or
generated yourself. Design adapted from the text layers of
[wiltodelta/remove-ai-watermarks](https://github.com/wiltodelta/remove-ai-watermarks) (Apache-2.0); this is an independent Kotlin implementation.
