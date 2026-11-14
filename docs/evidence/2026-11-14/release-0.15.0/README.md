# BERT v0.15.0: slate and paper, the garden for Bert, a daily streak

**LIVE 14 November 2026.** The owner tried the preview on the phone ("just downloaded it and its pretty good") and,
asked whether to go live, said "Run it and push". `/opt/berthalla` fast-forwarded `e023e2d` → `c4ceb5b` and deployed
with `scripts/deploy_website_vultr.sh` (dry-run first: the 0.14.2 APK deleted, the 0.15.0 one added, the pages,
pictures and `release.json` updated). `check-page.cjs public` passes (1440/768/390/320, no overflow, 6 downloads,
0.15.0). Both `bert-widget-0.15.0.apk` and the unversioned `bert-widget.apk` serve `6fe4c8b2…`; live `release.json`
says 0.15.0 (35); the 0.14.2 link is 404; the live privacy page has the new line. The AAB and mapping are on the PC in
`Android Builds\BERT-Play\` (hashes re-checked there), with the six screenshots in `screenshots-0.15.0\` and
`LISTING-0.15.0.md`. Rollback: `git -C /opt/berthalla reset --hard e023e2d`, then the deploy script.

**GitHub:** squashed onto `main` after a scrub scan of the added lines. The 12 November critique is kept out of the
public repository (it is at `/opt/bert-widget-qa/private/CRITIQUE-2026-11-12.md` and in local history): the owner
said "push" without saying whether it should go, so the cautious reading was taken. Say the word and it goes in.

**Owed:** the owner's Play upload (text below; the first since 0.12.0), and telling the community once (0.14.0 has no
update check; 0.14.2 website installs hear of it by themselves within a day).

Built from `e135215` (branch `v015`; the build tree is `/opt/bert-widget-v015-quiet`, fast-forwarded to the same
commit). Website `c4ceb5b` (branch `release-0.15.0` in `/opt/bert-widget-site-release`, on the live `e023e2d`).

- **APK 0.15.0 (35):** 7,395,614 bytes, SHA-256 `6fe4c8b29e38c00785b2d6d7fada64767d8cab11d25d74cb018f25c4817e9aca`.
  Certificate `f911463d…`, the same as 0.14.2's. Permissions are identical to 0.14.2's (7).
- **Play bundle:** SHA-256 `9ce41d4bd08fa7a0975d936f6c1c78f344a1ab57cd4f2ba00a0ef8c524ffbf01` (10,636,586 bytes),
  signed by the same certificate (`cert-aab.txt`), with `mapping-0.15.0.txt` (`dd716ee7…`). All are in
  `/opt/bert-widget-qa/release-0.15.0/staged/` with `release.json`. Copy to the PC's `Android Builds\BERT-Play\` at
  go-live.
- **Website (`c4ceb5b`):** the APK under both names (0.14.2's versioned file is replaced), `release.json`, and every
  version, size, date, checksum and link on `/` and `/app/`. `/app/` says what is new in 0.15, shows the new Home
  (`app-home.png?v=015`, the gate's `web-home.png`; Create, Tools and Play are byte-identical and keep their files),
  no longer says Bert's day shows his photo or follows the theme packs, and the homepage Workshop print is the gate's
  `1-today.png` at 540×960 (`app-home.webp?v=9`). The privacy page lists the days the app was opened and the pat
  count under "What stays on your phone" and is dated 14 November.

## What going live was

1. `git -C /opt/berthalla merge --ff-only release-0.15.0`, `scripts/deploy_website_vultr.sh --dry-run`, then without
   the flag (`live-merge.txt`, `live-dryrun.txt`, `live-deploy.txt`).
2. `check-page.cjs public` (`check-page-public.txt`), the live APK under both names against `6fe4c8b2…`, the live
   `release.json`, the 0.14.2 link (404) and the live privacy page.
3. The AAB, mapping, screenshots and listing copied to the PC.
4. GitHub squash onto `main` (scrub scan first; never `git merge -s ours origin/main`).

If a restage is ever needed: rerun `stage.py --date <day>`, the checks, and amend the site commit (and the privacy
page's date).

## What's new (Play, 500 characters at most; this is 392)

> Home has a new look: the BERT price is chalked on a slate and Berthalla’s latest note is pinned up on paper. The garden is now Bert’s: pat him for a heart, tap the gate, the cottage or the blossom for a word from him, and his chalkboard counts the days in a row you’ve opened the app. The Bert’s day widget is now a note from Bert, with pixel Bert in the day’s mood. Smaller fixes throughout.

## Gates (each exit code written to a file and read back)

Logs and exit files are in `/opt/bert-widget-qa/release-0.15.0/` (the build's gates are in
`/opt/bert-widget-qa/look-0.15/`).

- **Host gate:** `tools/check-mobile-game.sh` PASS at `e135215` (490 unit tests, 0 failures, lint, debug build, Lost
  Trail browser check; `look-0.15/gate-e135215.log`), the commit the release was built from.
- **Reviews** (Opus, xhigh, fresh context each time): the slate-and-paper round, 0 must-fix; the garden and streak
  round, 1 must-fix and 9 should-fix, fixed; a verifier on that fix round, 0 must-fix and 4 should-fix, fixed in
  `6e71e0b`; a verifier on `6e71e0b`, 0 must-fix and 2 should-fix, fixed in `e135215` (the day's word waits from
  Home's first frame; tests for the store and the game). `e135215` has the gate and its own tests behind it and no
  further reviewer.
- **Release build:** `lintRelease assembleRelease bundleRelease` at `e135215`, exit 0 (`release-build-e135215.log`).
  Release lint: 0 errors, 92 warnings.
- **`stage.py`:** exit 0 (`stage.txt`).
- **`check-apk.py`:**

  | APK | Mode | Exit | Result | Output |
  |---|---|---|---|---|
  | 0.15.0 | full | 0 | all checks pass | `check-apk.txt` |
  | 0.14.2 | full | 1 | fails (it still has the garden pills) | `check-apk-on-0.14.2.txt` |
  | 0.14.2 | `--only-0.15.0` | 1 | 5 of 5 fail | `check-apk-015-only-on-0.14.2.txt` |

  The five: versionCode 35; Home's slate and paper note; the garden remarks and pat count; the daily streak (its
  store, the "Opening streak" wording, Bert's day-three line); Bert's day note resources present and the old photo,
  banner and glow gone. Inherited checks that changed: the 0.14 "Home labels" check now requires the pills to be
  absent; 0.14.2's "On the board: " string and the 0.8.1 widget art are no longer required.
- **`verify-release.mjs`:** `node tools/verify-release.mjs <apk> <release.json> 32` exits 0 (`verify-release.txt`).
- **Website suite:** 959 passed, 39 skipped, exit 0 (`website-pytest.txt`); the pre-commit hook gave the same counts.
- **`check-page.cjs staged`:** exit 0 at 1440/768/390/320: no overflow, 6 downloads, 0.15.0 throughout
  (`staged-*.png`, `staged-page.json`).

Not checked: anything on a phone by us. The owner has run the preview on theirs; the S25 was never on adb, so there
is no device record of TalkBack, One UI widget cells or large text.
