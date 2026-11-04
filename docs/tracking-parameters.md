# Tracking parameters: field notes

What we've seen in real shared links, what each parameter does, and how BorraLoco treats it.
Add a section (and a test in `LinkCleanerTest`) whenever a new link teaches us something.

## Temu

Rule: **allow-list**. Only `goods_id`, `search_key`, `mall_id`, `page_sn` and `sku_id` are kept;
everything else is removed, so new Temu tags are dropped without a rule change.

Example ("Copy link" from the Temu app, Saudi store, 2026-11-04):

```
https://www.temu.com/sa-en/goods.html?_bg_fs=1&goods_id=601100218100590
  &share_img=…&top_gallery_url=…&refer_share_id=6089wl9yqx4chjd3db8qmudhe7nv60gp
  &refer_share_channel=copy_link&_oak_page_source=417&_oak_region=174&locale_override=174~en~SAR
→ https://www.temu.com/sa-en/goods.html?goods_id=601100218100590
```

| Parameter | What it is | Action |
|---|---|---|
| `goods_id` | The product. The page doesn't open without it. | **keep** |
| `sku_id` | A specific size/colour of the product. | keep |
| `mall_id` | A specific shop on Temu. | keep |
| `search_key` | The search terms on a search page. | keep |
| `page_sn` | Which kind of page to show. | keep |
| `refer_share_id` | **Unique ID of this share: ties every visitor back to the person who shared.** The main tracker. | remove |
| `refer_share_channel` | How it was shared (`copy_link`, `whatsapp`, …). | remove |
| `share_img`, `top_gallery_url` | The product image used for the share preview. Not needed: the page loads its own images. | remove |
| `_oak_page_source` | Code for the screen the share came from (e.g. `417`). | remove |
| `_oak_region` | Sharer's region code (`174` = Saudi Arabia). | remove |
| `locale_override` | Sharer's region, language and currency (`174~en~SAR`). Recipient's own settings apply instead. | remove |
| `_bg_fs` | Internal display/session flag. | remove |
| `_x_sessn_id`, `_x_ads_*`, `refer_page_*`, `_oak_mp_inf`, `_oak_freesia_scene`, `freesia_scene`, `spec_gallery_id`, `_p_rfs`, `refer_source` | Session, ad and referring-page tracking seen in other Temu links. | remove |

Not removed: the `/sa-en/` part of the path. It's the store and language (Saudi, English), not
a personal tracker, and removing it would send recipients to a different store.

Short links: `temu.to/…` and `share.temu.com/…` are expanded first (they hide all of the above),
then cleaned.

## Trendyol

Hosts: `trendyol.com`, `trendyol.sa`, `trendyol.ae` and other country stores. Short links: `ty.gl`.

Example (share from the Trendyol app, Saudi store, 2026-11-04):

```
https://www.trendyol.sa/redtag/men-grey-5-pocket-straight-fit-denim-jeans-p-1157982162
  ?boutiqueId=61&merchantId=1184610&adjust_t=25l4ri4o_25q1bx32&adjust_tracker_limit=999999999
  &adjust_creative=all&utm_campaign=1157982162&timestamp=1793787818&link_contentid=1157982162
  &adjust_campaign=product_free_2026114_SA&utm_term=208888978&utm_source=share
  &adjust_adgroup=all&utm_medium=product_free&link_userID=208888978
→ https://www.trendyol.sa/redtag/men-grey-5-pocket-straight-fit-denim-jeans-p-1157982162?boutiqueId=61&merchantId=1184610
```

| Parameter | What it is | Action |
|---|---|---|
| `merchantId` | The seller whose offer is shown (same product can have several sellers, at different prices). | **keep** |
| `boutiqueId` | The campaign "boutique" the product is listed under (`61` = the default one). | keep |
| `q`, `qt`, `st`, `os` and filter parameters | Search terms and filters on search pages. | keep |
| `link_userID` | **The sharer's Trendyol account ID.** | remove |
| `utm_term` | Here it's the same account ID again (`208888978`). | remove |
| `link_contentid` | The product ID, repeated (already in the path). | remove |
| `timestamp` | When the link was shared (Unix time). | remove |
| `adjust_t`, `adjust_campaign`, `adjust_adgroup`, `adjust_creative`, `adjust_tracker_limit` | Adjust: app-install attribution. `adjust_campaign` includes the share date and country. | remove |
| `utm_source`, `utm_medium`, `utm_campaign` | Marketing tags: "shared, free product link, for product X". | remove |

### Trendyol as an Adjust tracker link (`*.adj.st`)

The Trendyol app can also share a link on Adjust's tracking domain instead of `trendyol.sa`:

```
https://p8zh.adj.st/25l4ri4o_25q1bx32?adjust_t=…&Page=Product&ContentId=1157982162&MerchantId=1184610
  &link_userID=208888978&timestamp=1793788420&adjust_deeplink=ty%3A%2F%2F…&adjust_redirect=https%3A%2F%2Fwww.trendyol.sa%2FREDTAG%2F…
```

It is a redirect wrapper holding the same tracking twice more:

| Parameter | What it is | Action |
|---|---|---|
| `adjust_redirect` | **The real product page** on the website (itself full of tags). | unwrap, then clean as usual |
| `adjust_deeplink` | The same product as an in-app `ty://` link. | dropped with the wrapper |
| `Page`, `CampaignId`, `BoutiqueId`, `ContentId`, `MerchantId`, `PdpSource`, `FilterOverPriceListings`, `Channel`, `Culture` | Instructions for the app to open the product (and `Culture` = sharer's country/language). | dropped with the wrapper |
| `adjust_t`, `adjust_*`, `link_userID`, `timestamp`, `link_contentid` | Same trackers as above, in the wrapper. | dropped with the wrapper |

BorraLoco unwraps `adjust_redirect`, then cleans the Trendyol page, so the result is the same
plain `trendyol.sa` link as above. The path in the tracker link (`/25l4ri4o_25q1bx32`) is Adjust's
campaign tracker ID, shared by everyone who gets a link from the same share campaign.

## AliExpress

| Parameter | What it is | Action |
|---|---|---|
| `spreadCode` | Sharer's referral/share code. | remove |
| `platform` | App platform marker (`AE`). | remove |
| `channel` | Marketing channel / campaign (e.g. `nnGlobal`). | remove |
| `sourceType` | Code for where in the app the link was made (e.g. `570`). | remove |

## SHEIN

| Parameter | What it is | Action |
|---|---|---|
| `shc` | Sharer's share code, on `onelink.shein.com` short links. | remove |

`onelink.shein.com/…` is a short link; its path is likely unique per share too, so expanding it
(the default) is what fully removes the trace.

## X / Twitter

| Parameter | What it is | Action |
|---|---|---|
| `a`, `s`, `t` | Share-attribution codes; `t` encodes the sharer's account. | remove |
