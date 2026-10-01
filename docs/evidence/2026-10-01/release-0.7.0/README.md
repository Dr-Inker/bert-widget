# BERT v0.7.0 — new Lost Trail title, 5 MB APK, phone-direct market data

Published on October 1, 2026 at https://berthalla.io/app/.

## Artifact and publication

- Application source: `08ee234e350f5e02609fc23905a12445271502fb` (branch `direct-sources`).
- Game source: `084dcb02dec8608a48cedd7634af2ef952ac42e1` (bert-platformer `master`); bundled HTML 567,844 bytes,
  SHA-256 `ccd7cbcd8a4f8966c59d3873a13fa0d768de0ec83632af379c07b2b84f60d155`.
- Website deployed source: `fdf802e`, fast-forwarded from `6ef9a97` in `/opt/berthalla`; only the six release
  files changed and the live homepage/style/Music files are byte-identical (`public-checks.json`).
- Package `global.bert.widget`, version 0.7.0, code 17, Android 8.0+. APK 5,117,036 bytes (0.6.0: 30,090,889),
  SHA-256 `15bcaffe10fb127602a7074af7fb57518be1a7a9d9fb4aa665c3582cef916a06`, production certificate
  `f911463d6c89bf6927ac1ef9230412ba73ef2791ddb97c4ee4d457c082da995c`. R8 mapping kept at
  `/opt/bert-widget-qa/release-0.7.0/staged/mapping-0.7.0.txt`.
- Rollback: `/opt/bert-widget-qa/release-0.7.0/previous` (page, release.json, screenshots, stable APK);
  versioned 0.6.0 APK remains published.

## What changed

Widget freshness ("AS OF HH:mm", offline stale re-render), background refresh only while a widget is placed,
WebP wallpapers streamed to WallpaperManager, R8/resource shrinking, renderer-death handling, 24h server/direct
price history, quotes and history fetched directly from DEX Screener and GeckoTerminal with the Berthalla
routes as `?via=fallback`, and The Lost Trail's new title screen.

## Executed gates

- Platformer `tools/deploy-gate.sh` PASS at `084dcb0` (13 engine tests, art gate, six-world browser run,
  in-app title at 412x800/412x732/390x724/360x640; the previous build fails three of those sizes).
- `tools/check-mobile-game.sh` PASS at `08ee234`: 15 Node tests, 83 Android tests (0 failures), debug lint
  0 errors, bundled game byte-identical to the platformer's committed build, offline browser journey.
- `:app:lintRelease :app:assembleRelease` PASS (release lint 0 errors); `npm run verify:release -- … 16` PASS;
  `check-apk.py` PASS (exact game asset, non-exported game Activity, direct and fallback endpoints, no emulator host).
- Website pre-commit gate: 577 passed, 33 skipped. `check-page.cjs staged` and `public` PASS at
  1440/768/390/320px (no overflow, six download links); `check-public.py` PASS.

## Physical device (Samsung S25 Ultra, Android 16 / API 36)

Signed 0.7.0 installed as an in-place upgrade over the published 0.6.0 with a saved holdings amount: the amount
survived, Tools showed a fresh quote and a 204-point 24h chart, and the server saw no quote requests from 0.7.0
(only `status.json`), so market data came directly. The title screen fits above the menu on the device
(`device/s25-title.png`; the first candidate covered Bert's feet and was rebuilt). A minified preview build passed
Home/Tools/Holdings/Create/Saved/game/Back journeys, both widgets ("AS OF" time, refresh and stale-render jobs
scheduled) and Home+Lock wallpaper application. No app errors or crashes were logged.
