# BERT v0.12.0: Bert's day

**LIVE 15 October 2026** (the owner said "run it"; berthalla `ec45301..b317269`). Public checks:
- `check-page.cjs public` passes at 1440, 768, 390 and 320: no overflow, 6 downloads, 0.12.0.
- Both APK URLs serve `284ed160…` as `application/vnd.android.package-archive`; release.json reports 0.12.0 (27).
- The live `/` and `/app/` bytes match the repo; the garden widget image, new app screens and Workshop print return 200.

App source `e5d4eb4` (branch `v012-place`); website branch `release-0.12` at `b317269`, on top of `release-0.11`.

- **APK 0.12.0 (27):** 6,949,806 bytes, SHA-256 `284ed160e738d90432ca720e62a152ff829e986ef3b88c0701e942113bfa68c6`.
  Pinned certificate `f911463d…`.
- **Play bundle:** SHA-256 `2eaaf5a4bf444313011b238951af8b2d0d529d47fb83d01733769f8053c15033`, with
  `mapping-0.12.0.txt`. Both are in `/opt/bert-widget-qa/release-0.12/staged/` and on the PC in `BERT-Play\` (hashes
  checked there). The Play screenshots in `docs/play/screenshots/` are regenerated (only `1-today` changed).
- **Rollback:** berthalla was at `ec45301` before this release
  (`/opt/bert-widget-qa/release-0.12/previous-berthalla-head.txt`); `git -C /opt/berthalla reset --hard ec45301`.

- **Social preview (same evening):** /app/ now shares `bert-app-social-012-1200x630.png` (Bert's garden, made by
  `/opt/bert-widget-qa/release-0.12/social-card.py`); berthalla `fa5a772`, live and checked. The homepage
  switched to the same card at the owner's request (`a54d1de`, live, bytes match).

## Change

Home opens onto Bert's garden at the phone's time of day (concept, critiques and decisions in
`docs/CONCEPT-0.12-BERTS-PLACE.md`):
- six relit scenes and a routine by the clock (gate, rounds, sun-nap, waiting on the post, the walk home, asleep
  indoors), his pose from the mood;
- a pat slips his hat; at night the window can be knocked dark; the price is chalked on a board (tap: Market); the
  noticeboard holds Berthalla's note and the earlier ones; the gate leads to The Lost Trail; paw prints count visits;
- his lines in Gochi Hand (OFL, licence shipped and credited in About);
- a new "Bert's garden" widget.

## Gates

- `tools/check-mobile-game.sh` passes at `e5d4eb4` (`/opt/bert-widget-qa/release-0.12/gate-e5d4eb4.log`): 238 unit
  tests, lint, Lost Trail browser check.
- `lintRelease`, `assembleRelease` and `bundleRelease` pass.
- `check-apk.py` passes; run against the 0.11 APK it fails. Its 0.12 checks: every scene (square and wide), the nap
  and pat poses, the garden widget and preview, Gochi Hand and its OFL, the widget receiver, the night-window line,
  versionCode 27; the old garden banner and the square window-off night are rightly shrunk away.
- `verify-release.mjs … 26` passes.
- `check-page.cjs staged` passes at 1440, 768, 390 and 320: no overflow, 6 downloads, 0.12.0 throughout.
- The website suite passes: 956 passed.
- By eye: the /app/ hero, the four tab features (all four screens now BERT Violet, from `websiteScreens`), the
  garden widget card at 1440 and 390.

## Device

- The preview (same code before the review fixes) was checked on the S25, 16:42–18:03: golden and dusk slots, the
  pat, chalkboard, noticeboard, gate, the scrolled status bar. Three bugs found there were fixed in `5222127` and
  `bca42a6` and re-checked on the phone. The owner: "preview feels good".
- The review fixes were checked in host renders at 1080 and 1280 px wide and in landscape.
- The signed 0.12.0 installed over 0.11 on the S25 in place (versionCode 27). Cold start 122 ms, no entries in the
  crash buffer; at 19:47 (evening slot) Home showed the dusk scene with Bert on his walk home and the lit window.

## Independent review

An Opus code-review agent found no blockers. Its four should-fix items (Bert cropped on 1220–1344 px phones; the
widget missing Bert in tall shapes; the refresh spinner under the status bar; landscape opening on sky) and six nits
were fixed in `e5d4eb4`, the widget one with a test that fails without the fix.
