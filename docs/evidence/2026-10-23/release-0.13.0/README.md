# BERT v0.13.0: Play, and Tools › Style

**LIVE 24 October 2026** (owner: "run it"): `git -C /opt/berthalla merge --ff-only release-0.13` took the site from `6ce943d` to `dd49f44`; `check-page.cjs public` passes (1440/768/390/320, no overflow, 6 downloads, 0.13.0), and the live `bert-widget.apk` is `a07cb4f6…`. Rollback: `git -C /opt/berthalla reset --hard 6ce943d`. GitHub `main` = `5d94274` (squashed onto `76f21e5`, scrub scan clean). Play: the S25 already runs 0.13.0 (29) from Play. Staged 23 October 2026. App source `b6e2f8f` (branch `v013-nav`, on `v0121-trail`, so it carries
0.12.1's Lost Trail fair jumps and auto-run controls).

- **APK 0.13.0 (29):** 6,950,778 bytes (the same length as 0.12.1 by alignment padding; contents differ),
  SHA-256 `a07cb4f6d21739cfb42f427207ee2332714013f97f0ed2116539476c87202913`. Certificate `f911463d…`.
- **Play bundle:** SHA-256 `562305f065d8de41fbd825c118d2ffa2f22df6a345975701e6974b8718de1799`, with `mapping-0.13.0.txt`.
  All in `/opt/bert-widget-qa/release-0.13/staged/` with `release.json`.
- **Website:** branch `release-0.13` at `dd49f44` in `/opt/bert-widget-site-release`, on the live head `6ce943d`
  (owner: "stage the website"). To go live (owner says "run it"): `git -C /opt/berthalla merge --ff-only release-0.13`,
  then `node docs/evidence/2026-10-23/release-0.13.0/check-page.cjs public`. Rollback: `git -C /opt/berthalla reset
  --hard 6ce943d` (`/opt/bert-widget-qa/release-0.13/previous-berthalla-head.txt`). The 0.12.1 site branch (`faadd51`)
  was never published and is superseded.
- **Play kit:** `bert-0.13.0.aab` and `mapping-0.13.0.txt` copied to the PC's `BERT-Play\` (owner OK; hashes match).
- **Device install refused, by design:** the S25's BERT 0.12.0 is the Play install (installer `com.android.vending`,
  split APKs, Play app-signing key `825518e2…`), so `adb install -r` of the release-key APK fails with
  INSTALL_FAILED_UPDATE_INCOMPATIBLE and nothing changed. The phone gets 0.13.0 through Play once the AAB is uploaded.

## Change

From Gemini's proposal (`docs/PROPOSAL-2026-10-23-UNIFIED-VISION.md`), module A only, owner "take the lead":
- Explore → **Play**: The Lost Trail (the same picture and progress as Home), Flappy Bert with a live tournament
  under it, Bert Music. The pack's links (official BERT, Berthalla on X, berthalla.io, the community, Woofhub) moved to
  an **Around the pack** sheet at the foot of Home (owner: keep Bert Music under Play).
- Create = Art | Saved. Personalise → **Tools › Style** (app colours, My Bert, widgets, wallpapers); four Tools pills fit
  at 360dp; a sideways-scrolled pinned row brings the chosen pill into view. The checklist's widget step opens Style.

## Gates

- `tools/check-mobile-game.sh` passes at `b6e2f8f` (unit tests, lint, Lost Trail browser check).
- `lintRelease`, `assembleRelease`, `bundleRelease` pass (`/opt/bert-widget-qa/release-0.13/release-build-b6e2f8f.log`).
- `check-apk.py` passes (`check-apk.txt`); against the 0.12.1 APK it fails. 0.13 checks: Play and pack-sheet strings,
  the Explore screen gone, Lost Trail art, versionCode 29.
- `verify-release.mjs … 28` passes.
- New host tests that fail on the old code: four Tools pills inside the margins at 360dp (fails at 16dp padding: Style
  at 310–380dp), the chosen pill scrolled into view at 320dp/2x (fails: Style at 414–506dp), Play without the pack
  links and the sheet on Home.
- `check-page.cjs staged` passes at 1440, 768, 390 and 320: no overflow, 6 downloads, 0.13.0 throughout.
- The website suite passes: 956 passed, 39 skipped (`website-pytest.txt`; the pre-commit hook ran it again).
- By eye: the four tab cards on /app/ at 1440 (`/opt/bert-widget-qa/release-0.13/inside-1440.png`), Play second.

## Device

- Preview `ef4ac31` installed on the S25 (owner: "i kinda like it"); preview `b6e2f8f` installed (owner: "looks good").
  The scripted on-device screenshot pass stopped at its guard (WhatsApp in front) before any tap.
- The signed 0.13.0 has not been installed over the real app yet.

## Independent review

Two Opus review agents. Round 1 (`36e8776..ef4ac31`): no must-fix; should-fix (selected pill off-screen; docs naming
Explore/Personalise) fixed in `ef4ac31`. Round 2 (`b6e2f8f`): nothing significant. Nits left for later: the Around the
pack row's ripple is unclipped and full-width; `AroundThePackLink` lives in PlayScreen.kt; host captures can't show
bottom sheets (separate window).

## Play listing

Screenshots regenerated (`4-explore.png` → `4-play.png`); `LISTING.md` names Play and Tools › Style. The owner pastes
the description and uploads `bert-0.13.0.aab`.
