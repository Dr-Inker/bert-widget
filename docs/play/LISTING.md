# Google Play listing — BERT

Everything to paste into the Play Console. Graphics are in this folder. Current upload: the v0.12.0 app bundle
(`/opt/bert-widget-qa/release-0.12/staged/bert-0.12.0.aab`, also on the drinkerlabs PC at
`C:\\Users\\Sjpch\\BERT-Play\\`), signed with the existing release key, which Play App Signing uses as the upload key,
with `mapping-0.12.0.txt` so crash reports are readable. **The owner uploaded it on 15 October 2026.** The listing
text below was updated for 0.12 after that upload: paste the short and full descriptions into the console again.

## Store listing

- **App name (≤30):** `BERT: Bert's World`
- **Short description (≤80):** `Bert's garden keeps your clock. A pixel adventure, caption cards and widgets.`
- **Full description:**

> Bertram the Pomeranian — Bert to his friends — now lives on your phone.
>
> **Home** — Bert's garden, at your time of day. He's out at the gate at dawn, does his rounds, naps after lunch, waits on the post and walks home at dusk; at night the lamp is on. Give him a pat. The price is chalked on the board, Berthalla's updates are pinned to the noticeboard, and the live Flappy Bert tournament is just below.
>
> **Super Bert World: The Lost Trail** — a hand-made pixel platformer, playable offline. Six worlds, eighteen keepsakes, touch controls and saved lantern checkpoints.
>
> **Create** — make caption cards with Bert in five artworks, as a note or a poster, square or story-sized. Save them on your phone and share them anywhere.
>
> **Widgets** — Bert's garden (a small window onto Home, wherever his day has got to), Bert's day (his photo, mood, latest update and tournament countdown), or the BERT price at a glance with a 24-hour chart. Every widget shows when its data is from, even offline.
>
> **Tools** — the BERT market price and 24-hour chart, an optional private holdings record, and opt-in alerts for tournaments, Berthalla's daily updates, price levels and big moves.
>
> **Themes** — four matching Home and Lock screen wallpaper packs, and the app in BERT Violet or the original Berthalla Navy.
>
> No account. No ads. No tracking. No wallet connection — BERT never holds keys or trades. Market data is informational.

- **Category:** Entertainment. **Tags:** Pets, Comics/Cartoons (pick the closest available).
- **Contact email:** owner to supply (Play requires one). **Website:** https://berthalla.io/app/
- **Privacy policy:** https://berthalla.io/app/privacy/

## Graphics

| Asset | File | Spec |
| --- | --- | --- |
| App icon | `icon-512.png` | 512×512 PNG, full-bleed (Play applies the mask) |
| Feature graphic | `feature-graphic-1024x500.png` | 1024×500 |
| Phone screenshots | `screenshots/1-today.png` … `5-alerts.png` | 1080×1920 (9:16), each with a caption band; regenerate with `PlayStoreScreenshotsTest` (output in `android/app/build/outputs/play/`) |

Screenshots use review fixtures: no live quotes, no real holdings, fictional player names, and a Monday 09:10 clock.
Upload order: gameplay and a home-screen widget first (both captured on a real device; not generated here), then
Today, Create, Market, Explore and Alerts. Captions: "Bert’s day, every day", "Make it a card", "Know the market",
"Play the Lost Trail", "Alerts that don’t nag".

## App content answers

- **Ads:** No ads.
- **App access:** All functionality is available without login.
- **Target audience:** 13+ (not designed for children; it shows a cryptocurrency price).
- **Content rating questionnaire:** no violence beyond cartoon platforming (stomping cartoon creatures), no user-to-user
  communication, no purchases, no gambling. The Flappy Bert tournament is an external Telegram game with prizes run by
  its organiser; the app only shows its public leaderboard and links out.
- **Financial features declaration:** the app provides none of the listed regulated financial features. It shows public
  market data for the BERT token and a private, on-device holdings note; it is not a wallet, exchange or trading app and
  holds no keys.
- **News app:** No.
- **Government app:** No.

## Data safety

- **Data collected:** none. **Data shared:** none.
- The app makes requests to DEX Screener, GeckoTerminal and berthalla.io for public data; these services see the
  device's IP address as any web request does, and no identifiers, holdings or account data are sent. Holdings, cards,
  alert settings and game progress stay in private app storage; Android backup is disabled.
- **My Bert (0.10, optional, off until used):** a person can paste a public Solana wallet address to show the Bert NFT
  they hold. On their tap, the app sends that address to Magic Eden
  (`api-mainnet.magiceden.dev`, held and listed Berts) and to the public Solana RPC (`api.mainnet-beta.solana.com`,
  staked Berts), and downloads the matching pictures from the collection's Pinata gateway
  (`green-advanced-mule-774.mypinata.cloud`). The address is never sent to berthalla.io or to the developer. It is kept
  only in private app storage (excluded from backup, like everything else), with the short list of Berts and the chosen
  picture; "Forget this address" deletes all three. Nothing is signed and no keys are asked for or stored; the app
  cannot move or spend anything. **Declare it (decided 15 Oct 2026):** Play counts any data
  "transmitted off the device" as collected, even when it goes straight to a third party, so declare *Financial info →
  Other financial info* (wallet address): collected, not shared, optional, processed ephemerally, purpose App
  functionality. The privacy page says so. The empty state carries no marketplace link (Play's blockchain-content
  rules); there are no prices, values or rewards anywhere in My Bert.
- **Encryption in transit:** yes (HTTPS only; cleartext is disabled in release builds).
- **Deletion:** uninstalling or clearing app data deletes everything; there is no server-side data to delete.

## Permissions (as built)

`INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS` (requested only when an alert is switched on), `SET_WALLPAPER`,
`WAKE_LOCK` and `RECEIVE_BOOT_COMPLETED` (WorkManager scheduling). No foreground services, location, contacts, camera
or storage permissions.
