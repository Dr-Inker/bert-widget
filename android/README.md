# Android app and widget

Native Android BERT app and Jetpack Glance home-screen widgets.

## Next iteration (source only; unpublished)

The app opens at a BERT home with Bert's latest dispatch, mood and Flappy tournament status. Home / Explore / Create / Tools have persistent navigation; Android Back returns to Home. Explore links directly to games, music and community experiences. Create separates Art, Saved and Personalize; Tools groups Market and Holdings. Each section preserves its draft and scroll state when switching destinations. At large text sizes, navigation adapts to two rows so its labels remain readable. Long dispatches can be expanded in Home.

The activity adapter reads the public Berthalla status document independently of market quotes. It validates and bounds responses, caches the last valid document, refreshes on foreground entry and every five minutes while visible, and marks source data stale after 30 minutes. Its timestamp is labelled as a source refresh, not the dispatch publication date. Expired events never remain open; failed/stale updates leave future tournament status unconfirmed. Remote activity URLs and financial fields are not used.

Art leads with a local caption-card creator with three palettes and also offers an external Draw Bert entry. Preview and PNG export share the same 1080×1080 raster. Sharing uses a private cache directory and a FileProvider with temporary read permission for the chosen image; files older than 24 hours are cleaned up on a subsequent export. Drafts stay in saved screen state. An explicit Save card action stores the original PNG and metadata privately; Saved lists up to 40 cards with reopen, share and confirmed deletion. Saving publishes a card only after both files are written. Uninstalling or clearing app data removes the collection; sharing is the way to keep a copy elsewhere. Generation in web Studio and delivery to a share recipient are separate external actions. Personalize contains the existing widget setup and phone themes.

Market displays selectable 1H/6H/24H windows of actual on-device observations. It labels collection gaps and incomplete history, with no invented samples. Quotes refresh on foreground entry and about every minute while visible; a failed refresh preserves the saved price and shows a delayed state. Cached quotes age out of fresh status after 30 minutes.

Holdings are accessible without a network connection. The existing stored amount remains compatible with installed widgets; an optional total cost enables estimated unrealized gain/loss. Amounts and cost stay on the device. Theme browsing previews a pack; applying its widget palette now requires a separate action.

The release metadata below still describes the published v0.4.0 APK. No version bump or signed release has been produced for this iteration.

The development `local.properties` points at `/opt/android-sdk` on this server and is ignored by Git. Other machines should create it with their Android SDK location.

Debug builds use `http://10.0.2.2:8787/v1/bert/quote` and `http://10.0.2.2:8787/v1/bert/activity`. The native QA script supplies labelled fixtures for both paths; the regular Node service currently serves only the quote route, so activity is unavailable when using that service alone. Release builds use `https://berthalla.io/widget/api/quote` and `https://berthalla.io/status.json` respectively. The public activity document has no native API stability guarantee; parsing failure preserves the saved update.

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

## Host UI review

`BERTUiTest` renders the actual Compose app shell using deterministic offline/activity fixtures under Robolectric 4.16.1 / API 35, at 360×800 dp and normal/2x simulated text scale. It captures screens to `app/build/outputs/host-ui`, checks navigation line bounds, exercises saved-card creation/open/deletion and preserves drafts across destinations. `CaptionLibraryTest` verifies exact saved/shared PNG pixels and FileProvider read grants. JVM tests run serially with a bounded heap and repo-local temporary directory.

These are host tests, not emulator/device tests: they do not establish real share-recipient delivery, launcher placement, wallpaper success, API 36 system bars, TalkBack or physical-device performance. `tools/qa/android-companion.py` remains the device/emulator workflow; its earlier attempt never reached app checks. See the dated critique for source SHAs, commands, captures and remaining work.
