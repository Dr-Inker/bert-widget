# Google Play listing — BERT

Everything to paste into the Play Console. Graphics are in this folder. **Next upload: 0.15.0 (versionCode 35)**,
`bert-0.15.0.aab` with `mapping-0.15.0.txt` (staged by the release recipe under `/opt/bert-widget-qa/release-0.15.0/staged/`,
then copied to the drinkerlabs PC at `C:\\Users\\Sjpch\\Android Builds\\BERT-Play\\`), signed with the existing release key, which Play
App Signing uses as the upload key. The last upload was 0.12.0, on 15 October 2026; 0.13.0 to 0.14.2 were never
uploaded (0.14.2 went to the website only), so 0.15.0 replaces them. The text below is current for 0.15.0: paste the
short and full descriptions into the console again, and answer Data safety as below (the top-level answer is **Yes**).

## Store listing

- **App name (≤30):** `BERT: Bert's World`
- **Short description (≤80):** `Bert's garden keeps your clock. A pixel adventure, caption cards and widgets.`
- **Full description:**

> Bertram the Pomeranian — Bert to his friends — now lives on your phone.
>
> BERT is a community crypto token on Solana, named after Bert, and this app shows its public market price.
>
> **Home** — Bert's garden, at your time of day. He's out at the gate at dawn, does his rounds, naps after lunch, waits on the post and walks home at dusk; at night the lamp is on. Give him a pat, tap the things in his garden for a word about them, and watch his chalkboard count the days in a row you've come by. Under the garden the BERT price is chalked on a slate and Berthalla's latest note is pinned up on paper, and the live Flappy Bert tournament is just below.
>
> **Play** — Super Bert World: The Lost Trail, a hand-made pixel platformer, playable offline. Six worlds, eighteen keepsakes, touch controls and saved lantern checkpoints. Flappy Bert's arcade and tournaments and Bert Music are on the same tab.
>
> **Create** — make caption cards with Bert in five artworks, as a note or a poster, square or story-sized. Save them on your phone and share them anywhere.
>
> **Widgets** — Bert's garden (a small window onto Home, wherever his day has got to), Bert's day (a note from Bert: his mood, his latest update in his own hand and the tournament countdown), the BERT price at a glance with a 24-hour chart, or the BERT market desk with market figures and your holdings' value. The day, price and market widgets show when their data is from, even offline.
>
> **Tools** — the BERT market price with price charts from an hour to a year (1H, 6H, 24H, 1W, 1M, 1Y) and links to CoinMarketCap and DEX Screener, an optional private holdings record, and opt-in alerts for tournaments, Berthalla's daily updates, price levels and big moves.
>
> **Themes** — five matching Home and Lock screen wallpaper packs, and the app in BERT Violet or the original Berthalla Navy, all under Tools › Style.
>
> No account. No ads. No tracking. No wallet connection — BERT never holds keys or trades. Market data is informational.

- **Category:** Entertainment. **Tags:** Pets, Comics/Cartoons (pick the closest available).
- **Contact email:** owner to supply (Play requires one; also worth adding to the privacy page's contact section). **Website:** https://berthalla.io/app/
- **Privacy policy:** https://berthalla.io/app/privacy/

## Graphics

| Asset | File | Spec |
| --- | --- | --- |
| App icon | `icon-512.png` | 512×512 PNG, full-bleed (Play applies the mask) |
| Feature graphic | `feature-graphic-1024x500.png` | 1024×500 |
| Phone screenshots | `screenshots/0-widgets.png`, `1-today.png` … `5-alerts.png` | 1080×1920 (9:16), each with a caption band; regenerate with `PlayStoreScreenshotsTest` (output in `android/app/build/outputs/play/`) |

Screenshots use review fixtures: no live quotes, no real holdings, fictional player names, and a Monday 09:10 clock
for the app screens. Each app screen is rendered taller than the frame and cut at a gap between sections, so no label,
pill or chart is sliced. `0-widgets.png` is the four real widgets (Bert’s day, the BERT price, Bert’s garden and the market desk), rendered
by the same harness as their launcher previews, on a plain home-screen backdrop at a mid-morning clock.
Upload order: **`0-widgets` first**, then gameplay if one is captured on a real device (not generated here), then
Today, Create, Market, Play and Alerts. Captions: "Bert on your home screen", "Bert’s day, every day", "Make it a card",
"Know the market", "Play the Lost Trail", "Alerts that don’t nag". When copying a new set into `screenshots/`,
copy all six framed files from `android/app/build/outputs/play/` (not `screen/`), converted to RGB.

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
- **Tokenized digital assets (blockchain-based content):** none sold, bought, minted or earned in the app. My Bert
  only displays Bert NFTs that a pasted public wallet already holds, and shows whether a held Bert is listed; no
  prices, sale flows or marketplace links.
- **News app:** No.
- **Government app:** No.

## Data safety

- **Does the app collect or share any of the required user data types? Yes**: one optional item, the public wallet
  address a person pastes into My Bert (details below). **Data shared:** none.
- Otherwise the app requests public data from DEX Screener, GeckoTerminal and berthalla.io; these services see the
  device's IP address as any web request does, and no identifiers, holdings or account data are sent. (0.14.2: an
  install from the website also reads berthalla.io's `widget/release.json` at most once a day to say when a newer
  version is out; a Play install never makes that request, shows no update prompt and nothing is downloaded in the app.) Holdings, cards,
  alert settings, game progress and the optional name Bert calls them on the You page (0.14) stay in private app storage; Android
  backup is disabled.
- **You page (0.14, optional):** a person can type a name for Bert to call them by (up to 24 characters; it shows in the
  noticeboard's heading). It is written only to private app storage, is never sent to any server or to the developer, and
  "Forget my name" on the page deletes it. It is on-device only, so it is not "collected" for this form and nothing
  changes in the answers above; it is not an account, and there is no sign-in. Draw Bert on that page only opens
  berthalla.io/studio in the browser, and sends nothing from the app.
- **My Bert (0.10, optional, off until used):** a person can paste a public Solana wallet address to show the Bert NFT
  they hold. On their tap, the app sends that address to Magic Eden
  (`api-mainnet.magiceden.dev`, held and listed Berts) and to the public Solana RPC (`api.mainnet-beta.solana.com`,
  staked Berts), and downloads the matching pictures from the collection's Pinata gateway
  (`green-advanced-mule-774.mypinata.cloud`). The address is never sent to berthalla.io or to the developer. It is kept
  only in private app storage (excluded from backup, like everything else), with the short list of Berts and the chosen
  picture; "Forget this address" deletes all three. Nothing is signed and no keys are asked for or stored; the app
  cannot move or spend anything. **Declare it:** Play counts any data "transmitted off the device" as collected, even
  when it goes straight to a third party, so declare *Financial info → Other financial info* (wallet address):
  collected, not shared, optional, purpose App functionality, and **leave "processed ephemerally" unticked**: the
  address goes to Magic Eden and the public Solana RPC, third parties whose retention the developer cannot vouch for
  (critique 2026-11-12 POL-4, closing 4 Nov C.1). The privacy page says so. The empty state carries no marketplace link
  (Play's blockchain-content rules); there are no prices, values or rewards anywhere in My Bert, only a read-only
  "Listed" tag on a held Bert that is listed.
- **Encryption in transit:** yes (HTTPS only; cleartext is disabled in release builds).
- **Deletion:** uninstalling or clearing app data deletes everything; there is no server-side data to delete.

## Permissions (as built)

`INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS` (requested only when an alert is switched on), `SET_WALLPAPER`,
`WAKE_LOCK` and `RECEIVE_BOOT_COMPLETED` (WorkManager scheduling). No foreground services, location, contacts, camera
or storage permissions.
