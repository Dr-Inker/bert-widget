# Android app and widget

Native Android companion app and Jetpack Glance home-screen widget.

The development `local.properties` points at `/opt/android-sdk` on this server and is ignored by Git. Other machines should create it with their Android SDK location.

Debug builds use `http://10.0.2.2:8787/v1/bert/quote`, allowing an Android emulator to reach the local quote service. Release builds use `https://berthalla.io/widget/api/quote`.

The widget schedules connected-network refresh work every 15 minutes, validates the Solana mint before caching, and continues showing the last valid quote when updates fail.

## Build

```bash
./gradlew :app:assembleDebug
```

The resulting development APK is written to `app/build/outputs/apk/debug/app-debug.apk`. It is debug-signed and configured for an emulator that can reach the quote service at `10.0.2.2:8787`; it is not a production release artifact.

## Release

Release builds use the explicit production endpoint `https://berthalla.io/widget/api/quote`. Signing credentials are intentionally kept outside Git. See [`../DEPLOYMENT.md`](../DEPLOYMENT.md) before producing or replacing a release APK.

The published v0.1.0 package is `global.bert.widget`, requires Android 8.0 or newer, and is distributed from [berthalla.io/widget](https://berthalla.io/widget/).
