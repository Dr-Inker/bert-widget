# BERT

A native BERT app: Bert's garden on Home, an offline platform game, caption cards, market tools, personal holdings, phone themes and home-screen widgets.

Home opens onto **Bert's garden** at the phone's time of day (since 0.12): Bert keeps a routine by the clock, a pat slips his hat, the price is chalked on a board, Berthalla's notes are pinned to a noticeboard and the gate leads to the game. See [the concept](docs/CONCEPT-0.12-BERTS-PLACE.md). Home / Play / Create / Tools (since 0.13) also bring the Flappy Bert tournament, ecosystem links, a local caption-card creator and private saved collection, and app colours, widgets and wallpapers under Tools › Style. Play opens **Super Bert World: The Lost Trail** inside the app: six worlds, touch controls, pause and saved lantern checkpoints, playable offline. See [the mobile integration](docs/MOBILE-GAME-2026-09-09.md) and [session state](docs/NEXT-SESSION.md).

A separately installable **BERT Preview** can be built with `tools/check-android-preview.sh`. See [the visual review](docs/evidence/2026-09-08/review.html) and [device acceptance journeys](docs/DEVICE-REVIEW.md). This preview is local and debug-signed.

The signed Android v0.12.0 release is live at [berthalla.io/app](https://berthalla.io/app/) and uploaded to Google Play. This repository contains the Android app, widgets, production quote service, deployment templates, release-security controls, and an unshipped iOS WidgetKit codebase. See [release evidence](docs/evidence/2026-10-15/release-0.12.0/README.md); earlier releases have their own records under `docs/evidence/`.

The token identity is pinned by mint rather than ticker:

- Name: Bertram The Pomeranian
- Symbol: `Bert`
- Network: Solana
- Mint: `HgBRWfYxEfvPhtqkaeymCQtHCrKE46qQ43pKe8HCpump`

Optional holdings and cached quotes remain in private application storage. Android cloud backup and
device-to-device transfer are disabled, with explicit extraction exclusions as defense in depth.

See [RESEARCH.md](./RESEARCH.md) for the architecture, refresh constraints, and MVP plan.

## Quote service

Requires Node.js 22 or newer and has no runtime dependencies.

```bash
npm test
npm start
curl http://localhost:8787/v1/bert/quote
```

Endpoints:

- `GET /healthz`
- `GET /v1/bert/quote`

Configuration is available through `PORT`, `UPSTREAM_TIMEOUT_MS`, `FRESH_TTL_MS`, and `STALE_TTL_MS` environment variables. The service caches successful responses for one minute and can serve the last valid quote as stale for up to 30 minutes when DEX Screener is unavailable.

## Shipped Android release

The Android companion app and Jetpack Glance widget provide:

- Bert's garden on Home: six times of day, his routine, the pat, the chalked price and the noticeboard (0.12)
- The BERT brand kit as the app's look (BERT Violet, Outfit, the gradient wordmark), with the original Berthalla
  Navy as an alternative (0.11); Bert's lines in Gochi Hand
- My Bert: show the Bert NFT you hold from a pasted public address, never a wallet connection (0.10)
- Super Bert World: The Lost Trail, playable offline
- Current USD price, 24-hour change, 1H/6H activity, market cap, volume and liquidity, with live, delayed and
  last-updated status; opt-in alerts
- Optional, locally stored BERT holdings in a dedicated position panel
- Four home-screen widgets: Bert's garden, Bert's day, BERT price (Compact 2×2) and BERT market desk (4×2)
- A Theme Studio with four phone-wallpaper packs and matching widget palettes (BERT Violet, Mayor Purple, Woofhub
  Night, Berthalla Nights)

Each theme pack includes distinct Home and Lock Screen artwork plus a matching widget palette. The Compact widget still fills its lower panel with a genuine price sparkline assembled from validated observations stored on the device. It displays a collecting-history state until two distinct samples exist, retains at most 24 hours and 192 samples, and never invents missing market data. The widget remains informational only: it does not hold keys, connect a wallet, transmit local history or holdings, or execute trades. The signed APK supports Android 8.0 and newer.

Market data comes straight from the public DEX Screener (quote) and GeckoTerminal (24-hour history) APIs, so those services see the phone's IP address when it refreshes. The Berthalla quote service is used only when a direct request fails or no longer validates; those requests carry `?via=fallback`. No holdings, history or identifiers are sent anywhere.

Background refresh runs only while at least one widget is placed; without widgets, the app refreshes while it is open. Widgets show an absolute "as of" time and switch to delayed when a quote passes 30 minutes, including offline.

## iOS client

The initial SwiftUI app and WidgetKit extension live in [`ios/`](./ios). They share the API model and App Group cache, support small and medium widgets, request 15-minute timeline refreshes, and retain the last valid quote when an update fails. They are not signed, tested, or distributed yet. See [`ios/README.md`](./ios/README.md) for the remaining macOS, Xcode, signing, and TestFlight work.

## Android client

The native Android companion app and Jetpack Glance widget live in [`android/`](./android). WorkManager requests connected-network refreshes every 15 minutes and updates placed widgets from a validated cache. See [`android/README.md`](./android/README.md).

## Production

- Landing page: <https://berthalla.io/app/>
- Public source: <https://github.com/Dr-Inker/bert-widget>
- Quote API: <https://berthalla.io/widget/api/quote>
- Health endpoint: `GET /healthz` on the private service
- Android package: `global.bert.widget`
- Current Android version: `0.12.0` (`versionCode` 27)
- Minimum Android: 8.0 / API 26

Production runs the Node service on loopback behind nginx. The checked-in unit and nginx fragments are in [`deploy/`](./deploy); see [`DEPLOYMENT.md`](./DEPLOYMENT.md) for the release and recovery runbook.

The public quote URL is part of the production health boundary. A healthy loopback service does not prove
that installed widgets can refresh: DNS, Cloudflare and nginx must also return a current `200` response.
Production DNS must contain one proxied `A` record for `berthalla.io` pointing to the origin server; it must
never point to Cloudflare edge addresses, which creates an Error 1000 proxy loop and leaves clients showing
their last cached quote.

The landing page links directly to this repository and its release history so users can inspect the implementation, permissions, data flow, security controls, and published changes before installing the APK.

Landing-page widget mockups use a stable, clearly labelled illustrative market snapshot so short-term price movement does not distort the product presentation. This affects marketing previews only: the installed widgets and production quote API continue to display validated current data.

## Repository layout

```text
android/   Kotlin companion app and Jetpack Glance widget
ios/       SwiftUI and WidgetKit scaffold
src/       normalized BERT quote service
test/      Node service tests
deploy/    systemd and nginx templates
```

## Verification

```bash
npm test
npm run check
cd android
./gradlew :app:assembleDebug
```
