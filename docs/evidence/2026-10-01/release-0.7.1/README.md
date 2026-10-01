# BERT v0.7.1 — live tournament on Home, widgets that fit, full-height portrait game

Published on October 1, 2026 at https://berthalla.io/app/.

- Application source: `5497b2e80ceadd8c456bd40540520f7790e34605`; game source `35c37dbf7dcd2d8ed802fcf24874fd4c68d9b52b`
  (bundled HTML SHA-256 `ecaa65d36117d5adf1deac170563a1c5b35e92fbb2a60aa1653b80c4fc209eee`).
- Website `09d70cf`, fast-forwarded from `fdf802e`; homepage/style/Music byte-identical (`public-checks.json`).
- Package `global.bert.widget` 0.7.1, code 18. APK 5,133,728 bytes, SHA-256
  `37f38a98f649671a45391a31ef7831938cd11956851ddad094a8cffcf8bf54d4`, production certificate retained. Mapping:
  `/opt/bert-widget-qa/release-0.7.1/staged/mapping-0.7.1.txt`. Rollback: `/opt/bert-widget-qa/release-0.7.1/previous`.

## Changes

Home tournament card (countdown, prize pool, top 5, final standings; untrusted names sanitised). Both widgets fit at
every launcher size, pinned by `BERTWidgetRenderTest` (host Glance → RemoteViews rendering); widget data reloads inside
the composition on every update; sparkline colour follows the 24h change. The Lost Trail portrait play fills the screen
(56% → 76% of a 412x732 view), larger HUD, single Pause.

## Gates

- Platformer `tools/deploy-gate.sh` PASS at `35c37db`.
- `tools/check-mobile-game.sh` PASS at `5497b2e`: 15 Node, 91 Android tests, debug lint 0 errors, bundled game check.
- Release lint 0 errors; `verify:release … 17` PASS; `check-apk.py` PASS; website gate 577 passed / 33 skipped;
  `check-page.cjs` staged + public PASS (1440/768/390/320, no overflow); `check-public.py` PASS.

## Physical device (Samsung S25 Ultra, Android 16)

Minified preview of this source over the existing preview with both widgets placed: two holdings saves about a minute
apart (2,000,000 then 3,000,000) and the Market widget showed 3,000,000 BERT / $46,620.00, so the in-session reload
works on a real launcher (the old build was not re-installed to demonstrate the previous failure). Live tournament card
and portrait play captured in `device/`. Signed 0.7.1 then installed as an in-place upgrade over 0.7.0: code 18, Home
showed the live tournament; only vendor GPU driver log lines (Adreno/libpenguin), no app errors or crashes.
