# BERT v0.6.0 — offline Lost Trail release

Published on September 9, 2026 at https://berthalla.io/app/. Install/update BERT, then open
**Explore → The Lost Trail**. Six courses and all game art are bundled, with portrait and
landscape touch layouts, pause/Back handling and device-local saved lantern runs.

## Artifact and publication

- Application source: `569f9ca232098a4f8419d590d65273fba364157a`.
- Game source: `36ec959f2a4ac9b200def91687c892a030e73e5e`.
- Website deployed source: `6ef9a97822615d8720a96b5ba8d0f74be190d456`.
- Package `global.bert.widget`, version 0.6.0, code 16, Android 8.0+.
- APK: 30,090,889 bytes; SHA-256 `3a683083da772cbff5efb0c2decef740e88c3ae2d865a43ff7011888a2387d30`.
- Production signing certificate retained: `f911463d6c89bf6927ac1ef9230412ba73ef2791ddb97c4ee4d457c082da995c`.
- Bundled HTML: 314,143 bytes; SHA-256 `965c2cbefe715bd9e3181a7ca5bebed237ba30343b48a131a8897a4d39ad0f20`.
- [Versioned APK](https://berthalla.io/widget/download/bert-widget-0.6.0.apk?sha=3a683083),
  [stable APK](https://berthalla.io/widget/download/bert-widget.apk?sha=3a683083).

The authorized static publication fast-forwarded the live website from `1d76a9d` to
`6ef9a97`, after verifying the exact release diff, previous live bytes and preserved
homepage/Music hashes. Full website tests passed before and after the fast-forward.
No service/configuration changes, GitHub push or external messages were performed.
Rollback copies: `/opt/bert-widget-qa/release-0.6.0/previous`. Previous versioned APKs remain.

## Executed gates

All app commands below ran from `/opt/bert-widget` at app source `569f9ca232098a4f8419d590d65273fba364157a`.
Evidence/checker-only additions follow that source; they are not changes to the APK.

```bash
GRADLE_USER_HOME=/opt/bert-widget-qa/gradle \
ANDROID_USER_HOME=/opt/bert-widget-qa/android-home \
JAVA_TOOL_OPTIONS=-Duser.home=/opt/bert-widget-qa \
EVIDENCE_DIR=/tmp/bert-mobile-game-release tools/check-mobile-game.sh

GRADLE_USER_HOME=/opt/bert-widget-qa/gradle \
ANDROID_USER_HOME=/opt/bert-widget-qa/android-home \
JAVA_TOOL_OPTIONS=-Duser.home=/opt/bert-widget-qa \
android/gradlew -p android :app:lintRelease :app:assembleRelease --console=plain

npm run verify:release -- android/app/build/outputs/apk/release/app-release.apk \
 /opt/bert-widget-qa/release-0.6.0/staged/release.json 15

python3 docs/evidence/2026-09-09/release-0.6.0/check-apk.py \
 android/app/build/outputs/apk/release/app-release.apk

node docs/evidence/2026-09-09/release-0.6.0/check-page.cjs staged
python3 docs/evidence/2026-09-09/release-0.6.0/check-public.py
node docs/evidence/2026-09-09/release-0.6.0/check-page.cjs public
```

Results: 8 Node tests and 62 Android JVM/Robolectric tests, zero failures/errors/skips;
debug/release lint each zero errors and 35 warnings; debug/release assembly successful.
The composed mobile gate rejects uncommitted source changes and checks asset provenance
against platformer git source. Package checks verify the signed artifact itself, exact
bundled HTML/provenance, private game Activity, production origin, and no emulator endpoint.
See `mobile-gate.txt`, `signed-build.txt`, `release-verification.txt`, `package-inspection.json`,
`junit/`, `unit-tests.json` and `lint-*.xml`.

Website gate at `6ef9a97822615d8720a96b5ba8d0f74be190d456`, from both
`/opt/bert-widget-site-release` (staged) and `/opt/berthalla` (deployed):

```bash
# The staged worktree uses the incumbent /opt/berthalla/venv through a local symlink.
PYTHONDONTWRITEBYTECODE=1 PYTEST_ADDOPTS='-p no:cacheprovider' .claude/test-gate.sh

# Deployed checkout requires Git's existing ownership exception.
GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=safe.directory GIT_CONFIG_VALUE_0=/opt/berthalla \
PYTHONDONTWRITEBYTECODE=1 PYTEST_ADDOPTS='-p no:cacheprovider' .claude/test-gate.sh
```

Both passed: 577 tests, 33 skipped (live-database lanes intentionally disabled by the
incumbent gate). See `website-staged-gate.txt` and `website-deployed-gate.txt`.

Public checkers passed against website `6ef9a97`: both APKs downloaded and passed pinned
signature/hash verification, page/JS/CSS/Explore PNG matched staged bytes, both quote routes
returned fresh advancing observations, protected root/Music files were unchanged. Browser
checks passed 1440/768/390/320px with zero horizontal overflow, six correct APK links,
readable download controls and no page errors. See `public-checks.json`, `public-page.json`,
`staged-page.json` and `captures/public-390.png` / `captures/public-1440.png`.
The public runners use the retained staging/rollback files under `/opt/bert-widget-qa/release-0.6.0`.

Platformer full gate, from `/opt/bert-platformer`, passed at game source
`36ec959f2a4ac9b200def91687c892a030e73e5e`:

```bash
EVIDENCE_DIR=/tmp/bert-platformer-mobile-final tools/deploy-gate.sh
```

Thirteen engine tests, all six zero-retry browser courses, completed PixelLab art audit,
input and persistence checks. Public HTML was copied using the game's documented procedure.
`EVIDENCE_DIR=/tmp/bert-platformer-mobile-public node tools/qa/public-smoke.mjs` passed at
ledger SHA `71411f4388c1fd1c31df67ffd28cc7e8f1475749` (game source unchanged from `36ec959`):
exact production HTML hash, ordinary URL without QA hooks, keyboard play, pause and touch.
Evidence is committed in that repo under `docs/verification/2026-09-09/mobile/`.

## Measured mobile critique

Initial portrait gameplay occupied only 40.4% of the available 390×724 view, while a bottom
toast obscured the ground. Reworked the portrait canvas to 1:1 and moved the toast above
Bert's path. Final gameplay fraction is 53.9% at 390×724 and 61.5% at 320×520. The 844×294
landscape layout keeps a 16:9 playfield with side controls. Every measured game touch target
is at least 48px and inside the available view; RUN ON stays on one line and tap flash is removed.

`game/results.json` records actual bounds, real CDP two-thumb movement/jump, Run toggle,
input cancellation, Back/pause freeze, reload recovery and an earned lantern/gate/coin save
round-trip driven through normal controls. Normal production mode made no remote requests
and exposed no QA hooks. `game/playing-390.png`, `game/playing-844.png`, `game/title-320.png`
and `game/lantern-resumed.png` are actual game captures. `captures/explore.png` is the native
Compose host rendering with the real game artwork and explicit in-app launch action.

[Figma mobile layouts](https://www.figma.com/design/oTxUhbR4F1d3zDj8WlJOqh?node-id=58-325)
use native header components plus current game captures. Portrait frame `58:325`, landscape
`58:326`. `figma.json` records dimensions, component/font and raster capture provenance.
The gameplay raster represents the game's canvas; the native header remains editable.

## Limits

Physical-phone installation, Android WebView performance, actual OS process death, system
bars/cutouts, battery use and accessibility remain UNVERIFIED. The host has no usable
accelerated emulator; the known failed software attempt was not repeated. Host tests verify
native Activity policy/lifecycle, while Chromium verifies game JavaScript under its native
CSP; neither is a production APK running on a phone. This release is Android only; iOS remains
an unshipped scaffold. Saves are device-local and independent of web browser saves.
