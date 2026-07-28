# BERT Widget

Native home-screen widgets for tracking **Bertram The Pomeranian ($BERT)** on Solana.

The signed Android v0.3.8 release is live at [berthalla.io/widget](https://berthalla.io/widget/). This public repository is the source of record for the Android app, widget, production quote service, deployment templates, release-security controls, and an unshipped iOS WidgetKit codebase.

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

- BERT logo and current USD price
- Prominent 24-hour percentage change
- Market cap, 24-hour volume, and liquidity
- Prominent live, delayed, and last-updated status
- Optional, locally stored BERT holdings in a dedicated position panel
- Compact 2×2 and Market 4×2 home-screen widgets
- Tap-through to the native market desk and widget setup

The v0.3.8 layout makes better use of tall 4×2 launcher allocations with a larger market hierarchy, substantial metric panels, and a source/freshness footer. Compact and shorter layouts retain their existing density. The widget remains informational only: it does not hold keys, connect a wallet, transmit the holdings amount, or execute trades. The signed v0.3.8 APK supports Android 8.0 and newer.

## iOS client

The initial SwiftUI app and WidgetKit extension live in [`ios/`](./ios). They share the API model and App Group cache, support small and medium widgets, request 15-minute timeline refreshes, and retain the last valid quote when an update fails. They are not signed, tested, or distributed yet. See [`ios/README.md`](./ios/README.md) for the remaining macOS, Xcode, signing, and TestFlight work.

## Android client

The native Android companion app and Jetpack Glance widget live in [`android/`](./android). WorkManager requests connected-network refreshes every 15 minutes and updates placed widgets from a validated cache. See [`android/README.md`](./android/README.md).

## Production

- Landing page: <https://berthalla.io/widget/>
- Public source: <https://github.com/Dr-Inker/bert-widget>
- Quote API: <https://berthalla.io/widget/api/quote>
- Health endpoint: `GET /healthz` on the private service
- Android package: `global.bert.widget`
- Current Android version: `0.3.8` (`versionCode` 12)
- Minimum Android: 8.0 / API 26

Production runs the Node service on loopback behind nginx. The checked-in unit and nginx fragments are in [`deploy/`](./deploy); see [`DEPLOYMENT.md`](./DEPLOYMENT.md) for the release and recovery runbook.

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
