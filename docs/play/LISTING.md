# Google Play listing — BERT

Everything to paste into the Play Console. Graphics are in this folder. Upload artifact: the v0.8.0 app bundle
(`/opt/bert-widget-qa/release-0.8.0/staged/bert-0.8.0.aab`, also copied to the drinkerlabs PC at
`C:\\Users\\Sjpch\\BERT-Play\\`), signed with the existing release key, which Play App Signing uses as the upload key.
Upload `mapping-0.8.0.txt` alongside it so crash reports are readable.

## Store listing

- **App name (≤30):** `BERT: Bert's World`
- **Short description (≤80):** `Bert's updates, a pixel adventure, caption cards, alerts and home-screen widgets.`
- **Full description:**

> Bertram the Pomeranian — Bert to his friends — now lives on your phone.
>
> **Home** — Bert's latest update and mood, plus the live Flappy Bert tournament: countdown, prize pool and the top five.
>
> **Super Bert World: The Lost Trail** — a hand-made pixel platformer, playable offline. Six worlds, eighteen keepsakes, touch controls and saved lantern checkpoints.
>
> **Create** — make caption cards with Bert in five artworks, as a note or a poster, square or story-sized. Save them on your phone and share them anywhere.
>
> **Widgets** — Bert himself (mood, update, tournament countdown), or the BERT price at a glance with a 24-hour chart. Every widget shows when its data is from, even offline.
>
> **Tools** — the BERT market price and 24-hour chart, an optional private holdings record, and opt-in alerts for tournaments, Bert's updates and price levels.
>
> **Themes** — three matching Home and Lock screen wallpaper packs.
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
| Phone screenshots | `screenshots/1-home.png` … `6-create.png` | 720×1280 (9:16); regenerate with `PlayStoreScreenshotsTest` |

Screenshots use review fixtures (no live quotes, no real holdings).

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
- **Encryption in transit:** yes (HTTPS only; cleartext is disabled in release builds).
- **Deletion:** uninstalling or clearing app data deletes everything; there is no server-side data to delete.

## Permissions (as built)

`INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS` (requested only when an alert is switched on), `SET_WALLPAPER`,
`WAKE_LOCK` and `RECEIVE_BOOT_COMPLETED` (WorkManager scheduling). No foreground services, location, contacts, camera
or storage permissions.
