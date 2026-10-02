# BERT v0.8.1 — the Bert widget redesign

Published on October 2, 2026 at https://berthalla.io/app/.

- Application source `ef28193f36584d5ced638f31f5dd3fe261c9ee71`; website `ce6e639` (fast-forwarded from `1d6a689`;
  homepage/style/Music byte-identical to the 0.8.0 record).
- APK 0.8.1 (20): 5,252,940 bytes, SHA-256 `137b49e270117a1fdb2867685463b0c2ee58ce1ef013b01694594c3fc94df905`.
- Play app bundle: `/opt/bert-widget-qa/release-0.8.1/staged/bert-0.8.1.aab`, 8,071,689 bytes, SHA-256
  `98e6c593140c001e4736e3d947954235965df132b6b5242307e028ee1c45e503`, signed with the release key (certificate
  `f911463d…995c`, the Play upload key). Copied with `mapping-0.8.1.txt`, `SHA256SUMS.txt` and `LISTING.md` to the
  drinkerlabs PC at `C:\Users\Sjpch\BERT-Play\`; the 0.8.0 bundle moved to `superseded-0.8.0\` there.
- Rollback: `/opt/bert-widget-qa/release-0.8.1/previous` (0.8.0 `release.json`, stable APK, `/app/` page). No server
  change: `src/` is untouched since 0.8.0.

## Changes since 0.8.0

Owner on the S25 at 4×2: the Bert widget "looks empty and weird" (fixed 14sp text over a void, 30dp avatar, purple pill).

- Bert widget: from 200dp wide, Bert's photo runs full height on the left; tall narrow sizes (the S25's 2×2) show a
  banner of his face with the mood pill over it; the update is sized to fill the space (12–22sp, whole lines, centred);
  orange mood pill; rounded gold footer that says FLAPPY when the event name does not fit; faint orange glow. Picker
  preview matches.
- Price widget shows its chart from 140dp tall (150×150 had a 29% empty band); market widget at 165–199dp tall uses
  boxed metrics and the source line (340×180 had a 34% gap).

## Gates

- `tools/check-mobile-game.sh` PASS at `ef28193` (109 Android tests). New render checks, each proven red first:
  no vertical band over 25% without content in any widget (old Bert widget: 59% at 360×200), Bert's art ≥ 18% of
  the widget from 200dp wide or 150dp tall (old: 1%), a font-scale 1.3 pass over every Bert size, and WCAG AA for
  text, muted, gold-on-panel and the pill in every theme. Two test-harness bugs fixed: positions were 0,0 off-window
  (the "outside widget" check could never fire), and "clipped" counted wrapped lines' trailing space.
- Release lint 0 errors; `verify:release … 19` PASS; `check-apk.py` PASS (plus widget art survives shrinking).
- Website gate 577 passed / 33 skipped; `check-page.cjs` staged + public PASS at 1440/768/390/320 (no overflow, six
  download links, 0.8.1 / 20 / checksum); `check-public.py` PASS (both APK URLs: hash, size, headers, verifier; page
  bytes; one Cache-Control on the quote API).

## Physical device (Samsung S25 Ultra, Android 16)

Minified preview: Bert widget placed at 4×2 and 2×2 (`device/`), live post and tournament, rounded portrait/banner and
pill render on One UI; 2×2 is One UI's minimum for this widget (minResizeHeight 110dp exceeds one row). Font scale 1.3
re-rendered and fitted at 2×2 (restored to 1.0). The previous BERT Preview had an unknown debug signer, so it was
uninstalled and reinstalled. Signed 0.8.1 installed as an in-place upgrade over 0.8.0 (code 19 → 20); warm start
100 ms, empty crash buffer, process running.
