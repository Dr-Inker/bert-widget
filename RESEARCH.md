# BERT phone-widget research

Research date: 2026-07-27

## Token identity

The token must be identified by mint address, never by ticker alone. The working mint is:

`HgBRWfYxEfvPhtqkaeymCQtHCrKE46qQ43pKe8HCpump`

Solscan resolves that mint to **Bertram The Pomeranian**, and the live DEX Screener response identifies it as symbol `Bert`. DEX Screener currently reports several pools, with the deepest observed pool being the Raydium BERT/SOL pair `BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY`.

Do not hard-code current price, market cap, volume, or liquidity: those values are volatile.

## Recommended product

Start with an information-only widget and companion app. The companion app is required for installation, configuration, attribution/legal copy, cached state, and a full chart view.

### Small widget

- Logo, `$BERT`, USD price
- 24-hour change with both color and `+`/`-` text
- Last-updated timestamp

### Medium widget

- Everything in Small
- Market cap, 24-hour volume, and liquidity
- Compact sparkline if a historical-price source is added

### Interaction

- Tap the body to open the companion app's BERT detail screen
- Optional explicit refresh action where supported
- Never imply that a displayed quote is executable

## Platform findings

### iOS

Use a native SwiftUI app plus a WidgetKit extension. WidgetKit asks a `TimelineProvider` for snapshots and timeline entries, and the system controls when the extension runs. A requested refresh time is not a promise of real-time execution. The provider should fetch quickly, cache the last successful payload in an App Group, and return cached data with a stale marker on failure.

Recommended initial policy: request a new timeline roughly every 15 minutes, accept that iOS may throttle it, and refresh the timeline when the main app obtains newer data. A continuously moving crypto ticker is not a realistic ordinary home-screen-widget promise.

### Android

Use Kotlin with Jetpack Glance. Store the last successful payload locally; use WorkManager for network work, then call `GlanceAppWidget.updateAll()`. Android documents periodic widget updates up to every 30 minutes through `updatePeriodMillis`, or examples around 15 minutes with WorkManager, and explicitly warns against minute-by-minute background refreshes.

### Cross-platform choice

The native widget surfaces are different enough that native widget code is the lowest-risk choice:

- iOS: SwiftUI + WidgetKit
- Android: Kotlin + Jetpack Glance + WorkManager
- Optional shared backend: a tiny normalized quote endpoint

React Native or Flutter can be used for the companion app later, but they do not remove the need for platform-specific widget extensions and lifecycle handling.

## Market-data design

### Fastest MVP: direct DEX Screener reads

Use:

`GET https://api.dexscreener.com/token-pairs/v1/solana/HgBRWfYxEfvPhtqkaeymCQtHCrKE46qQ43pKe8HCpump`

DEX Screener documents a 300-request-per-minute limit for this endpoint. Select only pairs where the BERT mint is the base token, require a numeric USD price, and choose the pair with the greatest USD liquidity. This avoids mistakenly using unrelated assets from pools where BERT is only the quote token.

Advantages: no API key, quick prototype, price/change/volume/liquidity/market-cap fields in one response.

Risks: third-party schema/availability, per-device traffic, inconsistent values across pools, and uncertain production redistribution/attribution terms. Review the provider's terms before release.

### Recommended production path: normalized backend

Have the phone request one small, stable payload from a service we control. The service can validate the mint, select/aggregate pools, cache upstream responses, apply fallback providers, and keep data-source changes out of released apps.

Proposed response:

```json
{
  "asset": {
    "chain": "solana",
    "mint": "HgBRWfYxEfvPhtqkaeymCQtHCrKE46qQ43pKe8HCpump",
    "name": "Bertram The Pomeranian",
    "symbol": "Bert"
  },
  "quote": {
    "priceUsd": 0.0,
    "change24hPct": 0.0,
    "marketCapUsd": 0.0,
    "volume24hUsd": 0.0,
    "liquidityUsd": 0.0
  },
  "source": {
    "name": "dexscreener",
    "pairAddress": "BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY",
    "observedAt": "2026-07-27T00:00:00Z"
  }
}
```

Do not infer a trustworthy sparkline from a single quote response. Add a historical endpoint/provider or store timestamped quotes on the backend.

## Reliability and safety requirements

- Pin the Solana mint in code and validate every response against it.
- Keep the last successful payload and show `Updated Xm ago`.
- Mark data stale after a defined threshold, initially 30 minutes.
- Use timeouts, bounded retries, and exponential backoff.
- Preserve the last quote on network failure instead of showing zero.
- Use accessible positive/negative indicators; do not rely on red/green alone.
- Label market data as informational and disclose its source.
- No seed phrases, wallet keys, signing, or trading in the MVP.
- Add analytics only with explicit privacy review and minimal collection.

## Implementation status

The normalized backend and native Android path described above reached v0.3.6 on 2026-07-27. It is distributed directly from `berthalla.io/widget`, outside the Play Store, with Compact 2×2 and Market 4×2 widgets plus a locally stored holdings-value feature. The source repository is public and linked from the landing page alongside the signed release manifest and APK checksum. The iOS source is still unshipped because WidgetKit compilation, device validation, signing, and TestFlight distribution require macOS and an Apple Developer team.

The production backend pins the mint, chooses the highest-liquidity valid base-token pool, caches fresh quotes for one minute, and can serve the last valid quote for up to 30 minutes during upstream failure. The v0.3.6 security pass also added bounded upstream responses, strict external-link validation, HTTPS-only release traffic, disabled Android backup/device transfer, fail-closed release verification, hardened web headers, API rate limiting, and secure APK response handling.

## Original implementation sequence

1. Confirm brand assets and permission to use them.
2. Build and test a small normalized quote service or a local data-client prototype.
3. Implement one platform first, preferably iOS WidgetKit if the target audience is iPhone-heavy.
4. Add stale/error/preview states and test throttled/offline behavior.
5. Implement the second native widget using the same normalized contract.
6. Add historical data and a sparkline only after the quote MVP is reliable.

## Open product decisions

- Ship iOS first, Android first, or both together?
- Is a backend already available, or should this project include one?
- Which visual brand assets are approved for the app stores and widget gallery?
- Should tapping open an in-app chart, the official BERT site, or DEX Screener?
- Is portfolio balance tracking wanted later? If so, it should be read-only by public wallet address.

## Sources

- [Solscan token page](https://solscan.io/token/HgBRWfYxEfvPhtqkaeymCQtHCrKE46qQ43pKe8HCpump)
- [DEX Screener API reference](https://docs.dexscreener.com/api/reference)
- [Apple: Creating a widget extension](https://developer.apple.com/documentation/WidgetKit/Creating-a-Widget-Extension)
- [Apple: TimelineProvider](https://developer.apple.com/documentation/widgetkit/timelineprovider)
- [Apple: Keeping a widget up to date](https://developer.apple.com/documentation/widgetkit/keeping-a-widget-up-to-date)
- [Android: App widgets overview](https://developer.android.com/develop/ui/views/appwidgets/overview)
- [Android: Create an app widget with Glance](https://developer.android.com/develop/ui/compose/glance/create-app-widget)
- [Android: Manage and update GlanceAppWidget](https://developer.android.com/develop/ui/compose/glance/glance-app-widget)
