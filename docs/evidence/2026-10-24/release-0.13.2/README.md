# BERT v0.13.2: fixes from Antigravity's 0.13.1 critique

**STAGED, not live (24 October 2026); website staged at `3da6003`.** App source `533fce6` (branch `v0132`, on the live
0.13.1 record `c1e3ea2`). Scope agreed in `docs/CRITIQUE-2026-10-24-RESPONSE.md`.

- **APK 0.13.2 (31):** 7,542,666 bytes, SHA-256 `03bf2cbf9536c570561346da76688dd97808429e744ee09b3e5fbce68d546f97`.
  Certificate `f911463d…`. About 0.2 MB larger than 0.13.1: the five Home previews.
- **Play bundle:** SHA-256 `c0387dce1962abd51c20bd22ed2a21988ee174f8a796f4d607e6b7910f0fbe5a`, with `mapping-0.13.2.txt`
  (`17d3be6f…`). All in `/opt/bert-widget-qa/release-0.13.2/staged/` with `release.json`; both copied to the PC's
  `BERT-Play\` (hashes match).
- **Website:** branch `release-0.13.2` at `3da6003` in `/opt/bert-widget-site-release`, on the live head `253c2c0`.
  To go live (owner says "run it"): `git -C /opt/berthalla merge --ff-only release-0.13.2`, then
  `node docs/evidence/2026-10-24/release-0.13.2/check-page.cjs public`. Rollback:
  `git -C /opt/berthalla reset --hard 253c2c0`.
- **Preview for the S25:** `bert-0.13.2-preview-533fce6.apk` (re-signed with the S25 preview key) in the PC's
  `BERT-Preview\`.

## Change

1. **Navy and Violet home:** the stage spotlight that lit the removed wordmark is gone (only the band below y 2642).
2. **Mayor Purple home:** the `BERT` wordmark in the dock band is erased from the raster master by
   `design/theme-studio/tools/erase-mayor-home-wordmark.py` (gradient + halftone lattice 12.43 × 13.45 px rebuilt;
   Bert's pixels provably unchanged); the WebP replaces only that area of 0.13.1's.
3. **Tools › Style › Phone themes** follows the app colours (was hard-coded violet).
4. **Lock screen | Home screen** switch in Phone themes; every pack has `homePreviewRes` (432×960, Lanczos q90).
5. **Home:** "Read the noticeboard" stays when the board's notes are all read.
6. Review follow-ups: section tabs are one selectable group (TalkBack "1 of 2"); preview descriptions in sentence case.
7. **Website /app/:** "five worlds", a BERT Navy card, three-over-two centred grid above 700px, refreshed thumbnails.

## Gates

- `tools/check-mobile-game.sh` passes at `533fce6` (245 host tests, 15 Node tests, Lost Trail browser check).
  The first gate at `fec7c46` failed `BERTUiTest.widgetStepOpensPersonaliseInTools`, deterministically: the new switch
  used single-row `SectionTabs`, which scrolls its pill into view and dragged the Style page down to Phone themes. Fixed
  in `6c2eedb` (wrapping tabs). Not a flake.
- New host tests fail on the old code: Phone themes' panel pixel (`#120B1B` under Navy), no Home tab, and no
  "Read the noticeboard" once notes are read. Plus a selectable-group check.
- `lintRelease`, `assembleRelease`, `bundleRelease` pass. `check-apk.py` passes (new 0.13.2 checks: Mayor home bytes,
  five Home previews, switch and noticeboard strings); against the 0.13.1 APK it fails.
- `verify-release.mjs … 30` passes. `check-page.cjs staged` passes at 1440/768/390/320 (no overflow, 6 downloads,
  0.13.2). Website suite: 956 passed, 39 skipped (and again in the pre-commit hook).

## Independent review

Opus review over `c1e3ea2..6c2eedb`: **SHIP**, no must-fix. Checked scope (20 files, nothing stray), the noticeboard
guard (the sheet is never empty), contrast in both schemes (text ≥ 6.7:1 on the panel), tab roles and targets, preview
resources. Nits taken: selectable group, sentence case. Not taken: the BERT Violet pack accent as a border/button on
the Navy panel is 2.4:1 (pack identity colour, with a 3dp border and a tick); the switch while Match my Bert is chosen
still changes the pack cards beside it.

## Device

Not yet: the S25 was unplugged. Owed before "run it": install the preview, check Mayor Purple home behind the dock,
the Style page opening at its top, the Lock/Home switch, and "Read the noticeboard".
