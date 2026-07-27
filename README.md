# BERT Widget

Native home-screen widgets for tracking **Bertram The Pomeranian ($BERT)** on Solana.

The signed Android v0.3.5 release is live at [berthalla.io/widget](https://berthalla.io/widget/). The repository also contains the production quote service, deployment templates, and an unshipped iOS WidgetKit codebase.

The token identity is pinned by mint rather than ticker:

- Name: Bertram The Pomeranian
- Symbol: `Bert`
- Network: Solana
- Mint: `HgBRWfYxEfvPhtqkaeymCQtHCrKE46qQ43pKe8HCpump`

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
- 24-hour percentage change
- Market cap, 24-hour volume, and liquidity
- Last-updated time and a stale-data indicator
- Optional, locally stored BERT holdings with a live USD value
- Compact 2×2 and Market 4×2 home-screen widgets
- Tap-through to the native market desk and widget setup

The widget is informational only. It does not hold keys, connect a wallet, transmit the holdings amount, or execute trades. The signed v0.3.5 APK supports Android 8.0 and newer.

## iOS client

The initial SwiftUI app and WidgetKit extension live in [`ios/`](./ios). They share the API model and App Group cache, support small and medium widgets, request 15-minute timeline refreshes, and retain the last valid quote when an update fails. They are not signed, tested, or distributed yet. See [`ios/README.md`](./ios/README.md) for the remaining macOS, Xcode, signing, and TestFlight work.

## Android client

The native Android companion app and Jetpack Glance widget live in [`android/`](./android). WorkManager requests connected-network refreshes every 15 minutes and updates placed widgets from a validated cache. See [`android/README.md`](./android/README.md).

## Production

- Landing page: <https://berthalla.io/widget/>
- Quote API: <https://berthalla.io/widget/api/quote>
- Health endpoint: `GET /healthz` on the private service
- Android package: `global.bert.widget`
- Current Android version: `0.3.5` (`versionCode` 9)
- Minimum Android: 8.0 / API 26

Production runs the Node service on loopback behind nginx. The checked-in unit and nginx fragments are in [`deploy/`](./deploy); see [`DEPLOYMENT.md`](./DEPLOYMENT.md) for the release and recovery runbook.

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
