# BERT v0.15.1: the post from the Berthalla X bot as a printed clipping, honest card labels, typed prices

**LIVE 16 November 2026.** The owner saw the preview on the S25 through this session (Home clipping, two old saved
cards relabelled, a comma price, the Bert's day widget) and, asked whether to release, said "yes lets go pls".
`/opt/berthalla` fast-forwarded `e12f9e7` → `a2db38f` and deployed with `scripts/deploy_website_vultr.sh` (dry-run
first: the 0.15.0 APK deleted, the 0.15.1 one added, the pages, three pictures and `release.json` updated).
`check-page.cjs public` passes (1440/768/390/320, no overflow, 6 downloads, 0.15.1). Both `bert-widget-0.15.1.apk`
and the unversioned `bert-widget.apk` serve `4fd0ba70…`; live `release.json` says 0.15.1 (36); the 0.15.0 link is
404; the live privacy page no longer says "fan project". The AAB and mapping are on the PC in
`Android Builds\BERT-Play\` (hashes re-checked there), with the six screenshots in `screenshots-0.15.1\` and
`LISTING-0.15.1.md`.

Rollback: `git -C /opt/berthalla revert a2db38f`, then the deploy script. Do NOT `reset --hard` there: another
session has uncommitted work in that tree (`berthalla/safety/topics.py`, `tests/test_topics.py`).

**GitHub:** pushed on 16 November at the owner's word ("push to github please"): squashed onto `main` after a scrub
scan of the added lines (no email addresses, keys or server addresses; `github-push.txt`, `github-added-lines.txt`).

**Owed by the owner:** the Play upload (text below), and telling the community once (0.14.0 installs have no update
check; 0.14.2 and 0.15.0 website installs hear of it by themselves within a day).

Built from `5531f8e` (branch `v0151-clipping`, worktree `/opt/bert-widget-v0151`; the code is `17a0830`, and
`17a0830..5531f8e` touches only `docs/`). Website `a2db38f` (branch `release-0.15.1` in
`/opt/bert-widget-site-release`, on the live `e12f9e7`).

- **APK 0.15.1 (36):** 7,399,446 bytes, SHA-256 `4fd0ba70933f28d29b741f33eea2819c39be01a703174c2d0c24699f65530cf3`.
  Certificate `f911463d…`, the same as 0.15.0's. Permissions are identical to 0.15.0's (7).
- **Play bundle:** SHA-256 `470aafaa18b908942c1b70acbb7c3b7a9cbd788271f9da1567967453412b5aae` (10,646,717 bytes),
  signed by the same certificate (`cert-aab.txt`), with `mapping-0.15.1.txt` (`3502893d…`). All are in
  `/opt/bert-widget-qa/release-0.15.1/staged/` with `release.json`.
- **Website (`a2db38f`):** the APK under both names (0.15.0's versioned file is replaced), `release.json`, and every
  version, size, date, checksum and link on `/` and `/app/`. `/app/` says what is new in 0.15.1 and no longer calls
  the post a note from Bert or "in his hand"; Home and Create are the capture tests' own pictures
  (`app-home.png?v=0151`, `app-create.png?v=0151`; Play and Tools are byte-identical). The homepage's Create print
  (`app-create.webp?v=4`) is the current tab; the old one still showed "A NOTE FROM BERT" and the Explore tab. The
  privacy page says "from Berthalla (berthalla.io)" where it said "made by Berthalla, a fan project at berthalla.io";
  nothing about what the app stores or sends changed, so its date stays 14 November.

## What changed in the app

The owner's rules behind it (16 November): Berthalla is a place, like Valhalla; the bot is "the Berthalla X bot",
run by the BERT team, never a "fan bot" or "fan project"; the app is "an app for Bertlievers, forged in Berthalla".

- The post from the Berthalla X bot is a printed clipping, not Bert's handwriting: newsprint, pinned, typed, headed
  "BERTHALLA ON X · <the post's own date>", byline "the BERT team's X bot, posting as Bert". Bert's day widget is headed
  "BERTHALLA X BOT · time". The feed carries the post's date and X id (`/opt/berthalla` `8f64c5a`, `e12f9e7`).
- Cards: "FROM THE BERTHALLA X BOT" on an unedited post, "MADE WITH THE BERT APP" on anything a holder typed. Old
  saved cards have only the label repainted.
- Meet Bert says Mayor of Solana; About opens "An app for Bertlievers, forged in Berthalla."
- Typed numbers are read in the phone's own format first, then the other habit where it can mean only one thing
  ("0,015" was saved as 15 by 0.15.0); Alerts shows the level it will save.

## What's new (Play, 500 characters at most; this is 401)

> Posts from the Berthalla X bot now arrive as a printed clipping with their own date, on Home and on the Bert’s day widget, so they are never mistaken for Bert’s handwriting. Cards say where their words came from, and your saved cards keep their pictures. A price typed with a comma is read as you meant it, and Alerts shows the level it will save. Meet Bert, Mayor of Solana. Smaller fixes throughout.

## Gates (each exit code written to a file and read back)

Logs and exit files are in `/opt/bert-widget-qa/release-0.15.1/` (the build's gates and renders are in
`/opt/bert-widget-qa/look-0.15.1/`).

- **Host gate:** `npm test` and `tools/check-mobile-game.sh` PASS at `17a0830` (Node 15/15, 531 Android unit tests,
  0 failures, lint, debug build, Lost Trail browser check; `look-0.15.1/gate-17a0830.log`).
- **Reviews** (Opus, high, fresh context each time): round one, 1 must-fix (cards made from a post still said "A NOTE
  FROM BERT"); round two, 2 must-fix (Arabic and Persian phones could not type a price; old saved cards kept the old
  label); round three, 2 must-fix (the relabel replaced a My Bert card's picture; Arabic marks read as a literal
  point and comma); round four, verdict "ship", 1 should-fix, fixed in `7335e14`. Round five (`cc0d423`, `17a0830`)
  is the owner's wording only: gated, not separately reviewed.
- **S25 (en-GB), preview `4d3cea6` over the 0.15.0 preview:** `look-0.15.1/device/`. Home clipping; two 0.15.0 cards
  relabelled with only the label box changed (0.5% of pixels); "0,015" read as $0.015; Meet Bert and About; the 4×2
  Bert's day widget on One UI. Not seen on a phone: the widget at 2×2, night, TalkBack, and the release APK itself
  (the preview is the same code, debug-signed and with the `.preview` id).
- **Release build:** `lintRelease assembleRelease bundleRelease` at `5531f8e`, exit 0 (`release-build-5531f8e.log`).
- **`stage.py`:** exit 0 (`stage.txt`).
- **`check-apk.py`:**

  | APK | Mode | Exit | Result | Output |
  |---|---|---|---|---|
  | 0.15.1 | full | 0 | all checks pass | `check-apk.txt` |
  | 0.15.0 | full | 1 | fails (versionCode 35) | `check-apk-on-0.15.0.txt` |
  | 0.15.0 | `--only-0.15.1` | 1 | 6 of 6 fail | `check-apk-0151-only-on-0.15.0.txt` |

  The six: versionCode 36; the Home clipping's header, byline and the two feed fields, with "ON THE BOARD" gone; the
  widget header, with "FROM BERT · " gone; the two card labels, with "A NOTE FROM BERT" gone; Mayor of Solana, forged
  in Berthalla, no "fan"; the alert level shown back. Inherited check that changed: 0.15.0's "ON THE BOARD" eyebrow is
  no longer required.
- **`verify-release.mjs`:** `node tools/verify-release.mjs <apk> <release.json> 32` exits 0 (`verify-release.txt`).
- **Website suite:** 965 passed, 39 skipped, exit 0 (`website-pytest.txt`); the pre-commit hook gave the same counts.
- **`check-page.cjs staged`** and **`public`:** exit 0 at 1440/768/390/320: no overflow, 6 downloads, 0.15.1
  throughout (`staged-*.png`, `public-*.png`).
- **Live:** `live-merge.txt`, `live-dryrun.txt`, `live-deploy.txt`.
