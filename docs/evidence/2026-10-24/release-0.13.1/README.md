# BERT v0.13.1: new BERT Navy and BERT Violet wallpapers

**LIVE 24 October 2026** (owner: "run it"): `/opt/berthalla` fast-forwarded `dd49f44` → `253c2c0`; `check-page.cjs public` passes (1440/768/390/320, no overflow, 6 downloads, 0.13.1); `bert-widget-0.13.1.apk` serves `bf78bfbb…`. The unversioned `bert-widget.apk` was still a Cloudflare HIT for 0.13.0 (`max-age=14400`, cached at the 0.13.0 launch; origin already serves `bf78bfbb…`); it expires by itself within 4 h, and no purge tool exists here. Rollback: `git -C /opt/berthalla reset --hard dd49f44`. Staged earlier the same day; App source `54cb48f` (branch
`v0131-wallpapers`, on `v013-nav` at the live 0.13.0 record `35ee3a7`).

- **APK 0.13.1 (30):** 7,356,446 bytes, SHA-256 `bf78bfbb3934b2e62c36ad94964d07c5acb4e4a4618aef79be72389ce1bafc1f`.
  Certificate `f911463d…` (same as 0.13.0). About 0.4 MB larger than 0.13.0: the richer wallpaper art.
- **Play bundle:** SHA-256 `e51a92610b89b7caa35ba2a1fb1da4056a5f635447f73824e2edae02e9651d35`, with `mapping-0.13.1.txt`
  (`cdada20a…`). All in `/opt/bert-widget-qa/release-0.13.1/staged/` with `release.json`.
- **Website:** branch `release-0.13.1` at `253c2c0` in `/opt/bert-widget-site-release`, on the live head `dd49f44`.
  To go live (owner says "run it"): `git -C /opt/berthalla merge --ff-only release-0.13.1`, then
  `node docs/evidence/2026-10-24/release-0.13.1/check-page.cjs public`. Rollback:
  `git -C /opt/berthalla reset --hard dd49f44` (`/opt/bert-widget-qa/release-0.13.1/previous-berthalla-head.txt`).
- **Play kit:** `bert-0.13.1.aab` and `mapping-0.13.1.txt` copied to the PC's `BERT-Play\` (hashes match). The listing
  and screenshots there were refreshed the same day (0.13's `LISTING.md`, `4-play.png`); the 12 October set is in
  `superseded-listing-2026-10-12\`. No Play screenshot shows Navy or Violet, so none change.

## Change

Only the BERT Navy and BERT Violet wallpaper packs (home, lock, picker previews) and the version. Artwork by
Antigravity, owner-approved ("they look great"). Checked on the S25 with a preview build; two fixes from that check:
the home wordmark sat behind the dock (removed), and One UI's charging line struck through the lock tagline's underline
(removed). Masters, assets and the byte-exact render recipe: `/opt/bert-widget-qa/wallpaper-sources-navy-violet/`.

## Gates

- `tools/check-mobile-game.sh` passes at `54cb48f` (unit tests, lint, Node tests, Lost Trail browser check):
  `/opt/bert-widget-qa/release-0.13.1/gate-54cb48f.log`. An earlier full host run on the same art (before the bump)
  saw `BERTUiTest.widgetStepOpensPersonaliseInTools` flake once; it passes alone on 0.13.0 and 0.13.1 and in full runs.
- `lintRelease`, `assembleRelease`, `bundleRelease` pass (`release-build-54cb48f.log`).
- `check-apk.py` passes (`check-apk.txt`). New 0.13.1 check: the four WebPs inside the APK equal the source files byte
  for byte. Against the 0.13.0 APK it fails (versionCode; with that check bypassed, on `wallpaper_bert_navy_home`).
- `verify-release.mjs … 29` passes (`verify-release.txt`).
- `check-page.cjs staged` passes at 1440, 768, 390 and 320: no overflow, 6 downloads, 0.13.1 throughout. The BERT
  Violet theme card on `/app/` shows the new art (`theme-bert-violet-{lock,home}.jpg`, Lanczos 360×800, q88, `?v=0131`).
- The website suite passes: 956 passed, 39 skipped (`website-pytest.txt`; the pre-commit hook ran it again).

## Independent review

One Opus review agent over `35ee3a7..54cb48f`: **SHIP**, no must-fix. Diff is exactly the eight intended files; all
four WebPs decode at 1440×3200 RGB in the same lossy VP8 container as before; previews match the lock masters; home
masters have nothing in the dock band, lock masters have no underline; `BERTThemePack.kt` wiring unchanged; only
`build.gradle.kts` carries the version. Nit (intended): the theme-studio README points to sources outside the repo.

## Device

Preview builds with the new art were applied on the S25 (Navy home and lock, Violet home; fixed Navy home). The lock
underline fix was not seen on the phone (unplugged by then); its render differs from the checked one only in that bar.
The owner's phone was restored to Berthalla Nights. The real app on the S25 is the Play install; it gets 0.13.1 once
the AAB is uploaded.
