# BERT v0.8.0 — alerts, the Bert widget, caption cards v2, Play readiness

Published on October 1, 2026 at https://berthalla.io/app/ (privacy policy: https://berthalla.io/app/privacy/).

- Application source `3d563539acae649100915d3bd80c46b3575c3181`; game `35c37dbf7dcd2d8ed802fcf24874fd4c68d9b52b`; website `1d6a689`
  (fast-forwarded from `09d70cf`; homepage/style/Music byte-identical).
- APK 0.8.0 (19): 5,152,908 bytes, SHA-256 `51818fa0536196336fd0f13c5ddb09279e39933a966efcb98d532eef24ade18d`.
- Play app bundle: `/opt/bert-widget-qa/release-0.8.0/staged/bert-0.8.0.aab`, 7,966,086 bytes, SHA-256 `45ac27c3f2a3b53c71817890f3a53897bc8d2132ab02a937a567794b765806b2`, signed with the
  existing release key (certificate `f911463d…995c`), which serves as the Play upload key. Store kit: `docs/play/`.
- Mapping: `/opt/bert-widget-qa/release-0.8.0/staged/mapping-0.8.0.txt`. Rollback: `/opt/bert-widget-qa/release-0.8.0/previous`.
- Server: production checkout fast-forwarded to `3d56353` and `bert-widget.service` restarted (API errors are now
  `no-store`); nginx's duplicate `Cache-Control` removed from both quote routes (backup
  `/root/berthalla.io.bak.20261001-205707-before-cache-dedupe`). Every API route now sends one header.

## Changes since 0.7.1

Opt-in alerts (tournament ending, Bert's updates, price above/below) with permission requested only on opt-in; the Bert
widget (mood, dispatch, tournament countdown); caption cards v2 (five artworks, note/poster, square/story; saved-card
format v2 with v1 compatibility); orange accent #FF9433 replacing salmon (owner request); fullscreen landscape game;
grouped holdings echo; server-ahead history dedupe; foreground-service declarations removed; Play listing, graphics,
9:16 screenshots and a privacy policy; iOS fractional-second date fix (uncompiled).

## Gates

- `tools/check-mobile-game.sh` PASS at `3d56353`: 15 Node, 106 Android tests (incl. widget renders at 16 sizes/states,
  alert rules and delivery, caption card combinations, label accessibility audit proven red), lint 0 errors.
- Release lint 0 errors; `verify:release … 18` PASS; `check-apk.py` PASS (POST_NOTIFICATIONS present, no
  FOREGROUND_SERVICE/SystemForegroundService, Bert widget receiver, exact game asset, direct + fallback endpoints).
- Website gate 577 passed / 33 skipped; staged + public page checks PASS; `check-public.py` PASS.

## Physical device (Samsung S25 Ultra, Android 16)

Minified 0.8.0 preview: notification permission granted on opt-in; a $0.01 price-above alert fired from the scheduled
background job as "BERT is above $0.01 · Now $0.0156 · +5.56% today" on the Price alerts channel; the Bert widget placed
and rendered live data; a Moonlight / Poster / Story card saved and reopened at 9:16; landscape play fullscreen after the
fix; Home and Tools readable at font scale 1.6 (settings restored to 1.0 / auto-rotate on). Signed 0.8.0 installed as an
in-place upgrade over 0.7.1 (code 19, cold start 145 ms, no app errors); the owner was using the phone, so the post-upgrade
screens were not re-captured.
