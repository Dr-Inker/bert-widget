# BERT v0.9.2 — Bert walks with his pack

Published 12 October 2026 at https://berthalla.io/app/. App source `562e880`; website `ccafc5a` (from `c5034b7`).

- **APK 0.9.2 (24):** 5,873,549 bytes, SHA-256 `21be50e45affbea565f29d9ad6a0ceee94e18b131b61fa95eb38fc2763376919`.
  Byte-identical to rc1, which the owner checked on the S25.
- **Play bundle:** SHA-256 `cc708c895b8a7cb7c4ae4e3094219f2ba1e8d07568d295f2048b3bdae7521c74`.
- **PC kit:** in `BERT-Play\`; the 0.9.1 kit moved to `superseded-0.9.1\`.
- **Rollback:** `/opt/bert-widget-qa/release-0.9.2/previous/`.

## Change

The owner reported "the wall doesn't look right" and "he's floating over a fence".

**Cause:** the 0.9.1 banner was a crop of the R2 scene over its pond, with the ground cut off.

**Fix:**

1. Re-rendered the R2 Godot scene with its own Bert hidden. Run on the Shadow PC, in a scratch copy of the scene
   (`--nobert=1`), inside the exclusive Godot slot. The output is pixel-identical to the original outside Bert's
   box (224–284 × 270–356).
2. Cropped the kennel hillock and the pup pack, which sit on continuous ground.
3. Placed the app's Bert in the scene's own spot.

`TodayGardenGroundTest` fails when his feet are not on grass or soil. It fails 4 of 4 stance columns on the old
banner.

## Gates

- `tools/check-mobile-game.sh` passes at `e60ddae`: 186 tests.
- `check-apk.py` passes.
- `verify:release … 23` passes.
- `check-page.cjs staged` passes at 1440, 768, 390 and 320, with no overflow and 6 downloads.
- Live:
  - release.json reports 0.9.2 (24).
  - Both APK URLs give `21be50e4…`.
  - The app page and homepage bytes match the repo.
