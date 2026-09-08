# Android app and widget

Native Android BERT app and Jetpack Glance home-screen widgets.

## Next iteration (source only; unpublished)

The app opens at a BERT home with feature entry points and external ecosystem destinations. Home, Market, Holdings, and Studio have persistent navigation; Android Back returns to Home. Studio contains widget installation and phone themes.

Market displays selectable 1H/6H/24H windows of actual on-device observations. It labels collection gaps and incomplete history, with no invented samples. Quotes refresh on foreground entry and about every minute while visible; a failed refresh preserves the saved price and shows a delayed state. Cached quotes age out of fresh status after 30 minutes.

Holdings are accessible without a network connection. The existing stored amount remains compatible with installed widgets; an optional total cost enables estimated unrealized gain/loss. Amounts and cost stay on the device. Theme browsing previews a pack; applying its widget palette now requires a separate action.

The release metadata below still describes the published v0.4.0 APK. No version bump or signed release has been produced for this iteration.

The development `local.properties` points at `/opt/android-sdk` on this server and is ignored by Git. Other machines should create it with their Android SDK location.

Debug builds use `http://10.0.2.2:8787/v1/bert/quote`, allowing an Android emulator to reach the local quote service. Release builds use `https://berthalla.io/widget/api/quote`.

The widget schedules connected-network refresh work every 15 minutes, validates the Solana mint before caching, and continues showing the last valid quote when updates fail. The app exposes Compact 2×2 and Market 4×2 widgets. The v0.4.0 Compact layout retains the real rolling 24-hour sparkline introduced in v0.3.9, recording validated quote observations locally after two distinct samples are available. The history is capped at 192 samples and never leaves private app storage. Users can optionally enter a BERT amount that remains in private app storage and is used locally to display its live USD value in the Market widget.

## Theme Studio

The companion app includes three coordinated phone-personalization packs:

- Mayor Purple, derived from the official `bert.global` campaign art
- Woofhub Night, derived from the official `woofhub.com` product art
- Berthalla Nights, derived from the official `berthalla.io` ecosystem art

Each pack includes separate 1440×3200 Home and Lock Screen artwork, a lightweight in-app preview, and a matching palette for both Glance widgets. Users can apply either surface independently or install the coordinated pair. The selected pack is stored privately on the device and no wallpaper or preference data is transmitted.

Editable wallpaper compositions and source provenance live in [`../design/theme-studio`](../design/theme-studio).

## Build

```bash
./gradlew :app:assembleDebug
```

The resulting development APK is written to `app/build/outputs/apk/debug/app-debug.apk`. It is debug-signed and configured for an emulator that can reach the quote service at `10.0.2.2:8787`; it is not a production release artifact.

## Release

Release builds use the explicit production endpoint `https://berthalla.io/widget/api/quote`. Signing credentials are intentionally kept outside Git. See [`../DEPLOYMENT.md`](../DEPLOYMENT.md) before producing or replacing a release APK.

The published v0.4.0 package is `global.bert.widget` (`versionCode` 14), requires Android 8.0 or newer, and is distributed from [berthalla.io/widget](https://berthalla.io/widget/).
