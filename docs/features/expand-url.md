# Expand link (beta)

Paste a link into the app and tap **Expand**. `LinkInspector` follows it one hop at a time (HTTP redirects, plus redirect wrappers such as `l.facebook.com/l.php?u=…` and Adjust `adj.st`, which are read offline without a request), lists every step, and shows the final address with trackers removed.

- Contacts the link's own servers (HEAD, then GET for `<meta refresh>`), no cookies, no referrer.
- **Never requests addresses on your own network** (`PrivateHosts`): localhost, 10/8, 172.16/12, 192.168/16, link-local, CGNAT, IPv6 local ranges, including disguised spellings such as `2130706433` or `0x7f.1`. A link cannot make the phone probe your router.
- Stops on loops and after 10 hops.
- No Share-menu entry (kept to a single "Clean link" there); it lives in the app.

Shipped as a **pre-release** (`1.10-beta1`), so the in-app updater does not offer it to people on the stable version. The safety check ("is this link dangerous?") is a separate feature, not in this beta (branch `feature/expand-and-safety`).
