# BERT v0.9.1 — Bert's day, with a living pixel Bert

Published on 12 October 2026 at https://berthalla.io/app/. 0.9.0 was never published on its own; 0.9.1 replaces it.

- **App source:** `6738a91`, branch `v091` in `/opt/bert-widget-direct`.
- **Website:** `fe866a6`, fast-forwarded from `c5e50fe`.
- **APK 0.9.1 (23):** 5,880,805 bytes, SHA-256 `703c3a51ca51e9dafd15e8c4c2a4959c7ef9b2f3308970639555f44db7fa278f`. This is
  byte-identical to rc3, which was installed and checked on the owner's S25.
- **Play bundle:** SHA-256 `91fd5ace74dc0e836ac80e90b668f9c1e2ffb06c794a711cedf351c87a7fa952`. Release key `f911463d…995c`.
  The kit is on the drinkerlabs PC at `C:\Users\Sjpch\BERT-Play\`, and the 0.8.2 kit moved to `superseded-0.8.2\`.
- **Rollback:** `/opt/bert-widget-qa/release-0.9.1/previous/` holds the 0.8.2 page, release.json, the stable APK and
  `berthalla-head.txt`.

## What's in it

The research (`docs/RESEARCH-2026-10-12.md`) and the max-effort critique (`docs/CRITIQUE-2026-10-12.md`) set the scope.

- **Home:**
  - A garden banner with the F/10 pixel Bert (Bertians R2, owner-approved). He idles, walks in and woofs when tapped,
    and paces during pull-to-refresh.
  - Meet Bert, with character-first copy.
  - Today shows "first seen" times, a New tag, "Day N with Bert" and "N new from Bert".
  - A price strip.
  - A full-width Lost Trail card with world progress.
  - The journal.
  - Official chips for @bertcoincto and bert.global, kept apart from "From Berthalla".
- **Brand:** the official BERT wordmark is in the header and on the widgets.
- **Create:** art thumbnails, Share first, a crossfading preview, and exported cards signed "BERT · berthalla.io/app".
  Telegram and X are offered first in the share sheet.
- **Widgets:**
  - Real picker previews.
  - Mood is never truncated.
  - A 10sp text floor.
  - BERT Navy is the default for fresh installs. Upgraders keep Woofhub Night.
- **Launcher shortcuts:** Continue the Lost Trail, BERT price and Make a card.
- **Market:** an activity card with 1H/6H/24H changes, buys and sells.
- **Alerts:** price alerts re-arm after a 3% retreat. A new Big move alert has a 6-hour cooldown.
- **Copy:** UK spelling and curly quotes, enforced by a test.

## Gates

- `tools/check-mobile-game.sh` passes at `6f36775` with 184 tests. `6738a91` only adds Play screenshots.
- Independent reviews:
  - 0.9.0: no high-severity issues; 9 low or medium findings, all fixed.
  - Merged 0.9.1: 1 high (the static shortcut package), 2 medium and 3 low. Five were fixed. The sixth, a shortcut
    resetting the task, is platform behaviour and was accepted.
- `check-apk.py` passes. Art, previews and the shortcut package survive shrinking, and the shortcuts are found
  through the resource table because R8 renames the resource paths.
- `verify:release … 21` passes.
- `check-page.cjs staged` passes at 1440, 768, 390 and 320 wide, with no overflow and six download links.
- Live: release.json reads 0.9.1 (23); both APK URLs hash to `703c3a51…`; the content-type and disposition headers are
  correct; the page bytes match the repo.
- Device (S25): rc3 installed in place, and the crash buffer is empty. The owner tapped all three launcher shortcuts
  and confirmed they work. Device captures are kept off the public repo in `/opt/bert-widget-qa/` because they include
  the owner's picture-in-picture stream and holdings.
