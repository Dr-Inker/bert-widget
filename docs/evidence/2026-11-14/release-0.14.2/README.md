# BERT v0.14.2: the 12 November critique fixes and the update notice

**LIVE 14 November 2026.** The owner saw the preview on the S25 ("the preview is good") and handed over the lead.
`/opt/berthalla` fast-forwarded `05f172b` → `e023e2d` and deployed with `scripts/deploy_website_vultr.sh` (dry-run
first: the 0.14.0 APK deleted, the 0.14.2 one added, the pages, pictures and `release.json` updated). `check-page.cjs
public` passes (1440/768/390/320, no overflow, 6 downloads, 0.14.2). Both `bert-widget-0.14.2.apk` and the unversioned
`bert-widget.apk` (Cloudflare MISS) serve `dc360b7d…`; live `release.json` says 0.14.2 (34); the 0.14.0 link is 404.
The AAB and mapping are on the PC in `Android Builds\BERT-Play\` (hashes re-checked there), with the six screenshots
in `screenshots-0.14.2\` and `LISTING-0.14.2.md`. Platformer `fair-jumps` fast-forwarded to `4c519d1`. Rollback:
`git -C /opt/berthalla reset --hard 05f172b`, then the deploy script.

**Owed:** the owner's Play upload (text below); telling the community once (0.14.0 has no update check); and the
GitHub squash (done with 0.15.0; the 12 November critique is kept out of the public repository).

Built from `7a9b3a6` (branch `v0141`). Website `e023e2d` (branch `release-0.14.2`, on `site-0.14.2-privacy` `51c6b80`).
0.14.1 (33) was built and never published, and its changes ship here.

- **APK 0.14.2 (34):** 7,615,765 bytes, SHA-256 `dc360b7dcf5f6492155f409d2fd2f612a1bf80bf2b764610c9023fb1a50052f4`.
  Certificate `f911463d…`, the same as 0.14.0's. Permissions are identical to 0.14.0's (7).
- **Play bundle:** SHA-256 `2aeb1c1b15942c3221a504d2c1937dc9a3977e31811d446744ab901603b9ec8e` (10,838,826 bytes),
  signed by the same certificate (`keytool -printcert -jarfile`), with `mapping-0.14.2.txt` (`14ac72f0…`). All are in
  `/opt/bert-widget-qa/release-0.14.2/staged/` with `release.json`. Copy to the PC's `Android Builds\BERT-Play\` at
  go-live.
- **Website (`e023e2d`):** the APK under both names (0.14.0's versioned file is replaced), `release.json`, and every
  version, size, date, checksum and link on `/` and `/app/`. `/app/` says what is new in 0.14.2, names the update
  check in the trust paragraph and in "How do updates work?", and carries fresh Home, Create and Tools screens from
  the gate's `websiteScreens` output (Play's is byte-identical, so it keeps its file and cache key). The homepage
  Workshop print is the gate's `1-today.png` at 540×960 (`app-home.webp?v=8`). The privacy line for the update check
  is `51c6b80`, underneath.

## To go live (owner says "run it")

1. `git -C /opt/berthalla merge --ff-only release-0.14.2`, then `scripts/deploy_website_vultr.sh --dry-run`, read the
   list (the 0.14.0 APK is deleted, the 0.14.2 one added), then without the flag. Since 6 November the live site is the
   Vultr server; the merge alone publishes nothing.
2. `node docs/evidence/2026-11-14/release-0.14.2/check-page.cjs public`, and compare the live
   `bert-widget-0.14.2.apk` with `dc360b7d…` (the unversioned `bert-widget.apk` URL is Cloudflare-cached for up to 4 h).
3. Copy the AAB and mapping to the PC; the owner uploads to Play with the six screenshots in `docs/play/screenshots/`
   and the text below.
4. GitHub squash onto `main` (scrub scan first; never `git merge -s ours origin/main`).
5. Platformer: fast-forward `fair-jumps` to `reduced-motion-flicker` (the bundled game's pin, `c594245`).
6. Tell the community once: 0.14.0 has no update check, so its installs will not hear of 0.14.2 by themselves.

Rollback: `git -C /opt/berthalla reset --hard 05f172b` (after checking nothing else has landed), then the deploy script.

## What's new (Play, 500 characters at most)

> Home now writes out the BERT price with its time, and the first line of the latest note from the board. Style
> opens with the home-screen widgets. The Market widget fits at large text and can leave out your holdings. The price
> chart sits above buys and sells, and the 24-hour figure matches the chart. The Lost Trail is steadier with
> animations removed. Smaller fixes throughout.

## Rebuild and restage

As 0.14.0's record (`docs/evidence/2026-10-30/release-0.14.0/README.md`), with this folder's `stage.py`,
`check-apk.py` and `check-page.cjs`. `stage.py --date <day>` sets the release date shown on the page: rerun it if the
go-live day is not 14 November, then rerun the checks and amend the site commit.

## Gates (each exit code written to a file and read back)

Logs and exit files are in `/opt/bert-widget-qa/release-0.14.2/`.

- **Host gate:** `tools/check-mobile-game.sh` PASS at `4bf8db9` (13 November: Node 15/15, 416 unit tests, lint,
  debug build, Lost Trail browser check; `gate-4bf8db9.log`). `7a9b3a6` adds only `docs/` to it (the critique, the
  Play screenshots, the memo), so the gate was not run again.
- **Release build:** `lintRelease assembleRelease bundleRelease` at `7a9b3a6`, exit 0 (`release-build-7a9b3a6.log`).
  Release lint: 0 errors, 89 warnings.
- **`stage.py`:** exit 0 (`stage.txt`).
- **`check-apk.py`:**

  | APK | Mode | Exit | Result | Output |
  |---|---|---|---|---|
  | 0.14.2 | full | 0 | all checks pass | `check-apk.txt` |
  | 0.14.0 | full | 1 | the first inherited check fails (the bundled game differs) | `check-apk-on-0.14.0.txt` |
  | 0.14.0 | `--only-0.14.2` | 1 | 5 of 5 fail | `check-apk-0142-only-on-0.14.0.txt` |

  The five: versionCode 34, the `c594245` game pin, Home's board row and Meet Bert line, the Market widget's holdings
  switch, and the update notice's `release.json` address and wording. One inherited 0.14.0 check changed: the Market
  change line is now built from parts, so it looks for "over the past " and "since " instead of the whole phrases.
- **`verify-release.mjs`:** `node tools/verify-release.mjs <apk> <release.json> 32` exits 0 (`verify-release.txt`).
  Against the 0.14.0 APK it exits 1 (version code 32 vs 34).
- **Website suite:** 959 passed, 39 skipped, exit 0 (`website-pytest.txt`); the pre-commit hook gave the same counts.
- **`check-page.cjs staged`:** exit 0 at 1440/768/390/320: no overflow, 6 downloads, 0.14.2 throughout
  (`staged-*.png`, `staged-page.json`).

Not checked: anything on a phone. The preview has not been opened on the S25.
