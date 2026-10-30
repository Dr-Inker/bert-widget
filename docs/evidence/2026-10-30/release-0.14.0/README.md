# BERT v0.14.0: speech bubble, You page, Home labels, real bark, longer charts

**STAGED, not live (30 October 2026): rc2, from `30847cb`.** That is `v014` with `v014-afford` merged (last commit
`770a3d1`). The website is staged at `c118bcb`. rc2 is built on the staged, never-published 0.13.2 (`1d78ed9`).
**0.14.0 supersedes 0.13.2:** 0.13.2 never goes live and never goes to Play. **rc1** (`fe58cfd`, before the Home labels;
APK `58bf9670…`) is **superseded**. Its files and logs are kept in `/opt/bert-widget-qa/release-0.14.0/rc1/`.

- **APK 0.14.0 (32):** 7,615,749 bytes, SHA-256 `fee68fcadf10d996a72a0f7e121c12a08561e8a6ede7da59a3b2344a92a4e8c4`.
  Certificate `f911463d…`, the same as 0.13.2's (`apksigner verify --print-certs` on both). The AAB's signer
  (`keytool -printcert -jarfile`) is the same. It is about 73 KB larger than 0.13.2: the bark clips, the You page, the
  range history and the labels.
- **Play bundle:** SHA-256 `b420efb84950b5960cc0b607d2acd3d5c2d29ae853447300afcdd58a040f87df` (10,809,297 bytes), with
  `mapping-0.14.0.txt` (`187723ef…`). All are in `/opt/bert-widget-qa/release-0.14.0/staged/` with `release.json`.
  **Not** copied to the PC's `BERT-Play\`.
- **Preview for the S25:** `/opt/bert-widget-qa/preview-0.14/bert-0.14.0-preview-30847cb.apk`, SHA-256
  `184e761913f599200a4516fcdb85053d9a7c26a9e76a24edc608ae25d2603841`. It is `global.bert.widget.preview` 0.14.0-preview
  (32), built from `30847cb` with `assemblePreview` and re-signed with `apksigner` using
  `/opt/bert-widget-qa/android-home/debug.keystore` (signer `5c1f4ef8…`, as the earlier previews). It passes every
  0.14 check except the release versionName.
- **Website:** branch `release-0.14.0` at `c118bcb` (rc2 swap) on `7477229` (rc1 staging) in
  `/opt/bert-widget-site-release`. That sits on `release-0.13.2` (`3da6003`), so 0.13.2's five-pack Theme Studio is
  kept, which in turn sits on the live head `253c2c0` (0.13.1).
  To go live (owner says "run it"): `git -C /opt/berthalla merge --ff-only release-0.14.0`, then
  `node docs/evidence/2026-10-30/release-0.14.0/check-page.cjs public`. Then compare the live versioned APK with
  `fee68fca…` (the unversioned `bert-widget.apk` URL is Cloudflare-cached for up to 4 h). Rollback:
  `git -C /opt/berthalla reset --hard 253c2c0`.
- **Platformer, at release:** the bundled Lost Trail is pinned to platformer `6c48410`, which so far lives only on
  `real-bark` (`a70ac67` = `6c48410` plus its gate record). `fair-jumps` (`d3c70db`) is checked out in
  `/opt/bert-platformer` and can fast-forward: `git -C /opt/bert-platformer merge --ff-only real-bark`.

## Change

0.14 answers an Android tester's feedback; details are in `docs/NEXT-SESSION.md`.

1. **Home:** Bert's line is a pixel speech bubble whose tail follows him (dream puffs at night). The scene steps down
   into the page; newcomers get hints and glints until they pat him.
2. **Home labels (rc2, `v014-afford`):** what can be tapped says so, in the You pill's style. There is a **You** pill
   at the top, and **Price ›** (chalkboard), **Play ›** (garden gate) and **News** (noticeboard) labels on the scene.
   One placement rule sets the scene's targets and labels, swept across phone sizes and through Bert's whole day, so a
   visible label is what you tap. Each pill is a stock clickable. Each spoken name leads with its label's word
   ("Price: chalkboard", "Play: the garden gate to The Lost Trail", "News: today's note from Berthalla"), so Voice
   Access "tap Price" works, and TalkBack reads the picture in place. The owner approved the labels on the S25.
   Review rounds, as recorded in the commits:
   - tap cues: review fixes (`b5b4a82`), then round 5 folded into `0b54752`;
   - final review of `0b54752..a2edb10`: a must-fix, because labels drawn over Bert patted him (fixed in `47934bb`);
   - review of `47934bb`: the hand-rolled pointer handler was replaced by a stock clickable (`5445c00`);
   - whole-branch review of `fe58cfd..5445c00`: two accessibility should-fixes, label-in-name (`b217002`);
   - review of `b217002`: a must-fix, because TalkBack reached the scene only after the whole page (`c5de056`);
   - last round: nits only, comments (`770a3d1`).
3. **You page** (phone only, no account): an optional name (24 code points, cut on grapheme boundaries), My Bert,
   Style, visits, The Lost Trail, cards, Draw Bert and holdings.
4. **Market:** 1H 6H 24H 1W 1M 1Y, and CoinMarketCap as the main link. No "All": GeckoTerminal's free API refuses
   history older than about 183 days.
5. **The Lost Trail:** a recorded CC0 bark (Freesound 163459) replaces the synth tones.
6. **Website:**
   - /app/ has a "New in 0.14" line that names the labels, Home copy about the You pill, and Home and Tools screens
     from the gate's `PlayStoreScreenshotsTest` output (Home re-taken at `30847cb` with the labels).
   - The homepage Workshop print `app-home.webp` is the gate's `1-today.png` resized to 540×960 (`?v=7`). It last
     changed for 0.12.
   - The privacy page (effective 30 October 2026) adds the name to "What stays on your phone" and a short "The You
     page (optional)" section. It also says price history runs "from the last hour to the last year" and adds
     CoinMarketCap to the links. All of it follows `PRIVACY-MYBERT-DRAFT.md`, `docs/play/LISTING.md` and
     `BERTAbout.kt`.

## Rebuild and restage

Everything is rerunnable; only hashes, size, date and source revision change.

1. From a clean `v014`, under the lock:
   `GRADLE_USER_HOME=/opt/bert-widget-qa/gradle ANDROID_USER_HOME=/opt/bert-widget-qa/android-home
   JAVA_TOOL_OPTIONS=-Duser.home=/opt/bert-widget-qa flock /tmp/bert-gradle.lock android/gradlew -p android
   :app:lintRelease :app:assembleRelease :app:bundleRelease --console=plain`.
2. Run `python3 docs/evidence/2026-10-30/release-0.14.0/stage.py --date <day>`.
   - It refuses a dirty app tree, a version other than 0.14.0 (32), or a different certificate.
   - It copies the APK, AAB and mapping into the QA `staged/` folder and writes `release.json` (permissions read from
     the APK).
   - It updates the website worktree: both APK names, `widget/release.json`, and every version, code, size, date,
     checksum and download link on `/` and `/app/`.
   - It never commits and never touches `/opt/berthalla`. Used for rc1 and for rc2.
3. Rerun `check-apk.py` (both ways), `verify-release.mjs … 31`, the website suite and `check-page.cjs staged`. Then
   commit on `release-0.14.0` and update the hashes here.

## Gates (rc2; each exit code written to a file and read back)

Logs and exit files are in `/opt/bert-widget-qa/release-0.14.0/`.

- **Host gate:** `tools/check-mobile-game.sh` at `30847cb` under the lock, exit 0 (`gate-30847cb.log`,
  `gate-30847cb.exit`). It ran 367 host tests (0 failures, 0 errors, 0 skipped, from the JUnit XML), 15 Node tests, and
  the Lost Trail browser check (PASS, game `b63fc04e…`). Its screens are kept in `screens-30847cb/`.
- **Release build:** `lintRelease assembleRelease bundleRelease` exit 0 (`release-build-30847cb.log`). Release lint:
  0 errors, 87 warnings.
- **`stage.py`:** exit 0 (`stage.txt`). Permissions are identical to 0.13.2's.
- **`check-apk.py`:**

  | APK | Mode | Exit | Result | Output |
  |---|---|---|---|---|
  | rc2 | full | 0 | all checks pass | `check-apk.txt` |
  | 0.13.2 | full | 1 | the first inherited check fails (the bundled game differs) | `check-apk-on-0.13.2.txt` |
  | 0.13.2 | `--only-0.14` | 1 | 10 of 11 fail | `check-apk-014-only-on-0.13.2.txt` |
  | rc1 | `--only-0.14` | 1 | 1 of 11 fails: the new Home labels check | `check-apk-014-only-on-rc1.txt` |

  - **The 10 checks that fail on 0.13.2:** versionCode 32, the `6c48410` pin, the Freesound 163459 provenance, the
    embedded bark MP3s, the CC0 bark licence, the You page strings, the speech-bubble line, the 1W/1M/1Y strings, the
    CoinMarketCap link and the Home labels.
  - **"no All range"** is a guard, not a discriminator, since 0.13.2 has no All either. It looks for the strings
    `6314171`'s All range used.
  - **The labels check** needs `Price ›`, `Play ›`, `Price: chalkboard`, `Play: the garden gate to The Lost Trail` and
    `News: today’s note from Berthalla` in the dex. Each one is absent from rc1 and present in rc2.
- **`verify-release.mjs`:** `node tools/verify-release.mjs <apk> <release.json> 31` exits 0 (`verify-release.txt`).
  Against the 0.13.2 APK it exits 1 (version code 31 vs 32).
- **Website suite:** 956 passed, 39 skipped, exit 0 (`website-pytest.txt`); the pre-commit hook gave the same counts.
- **`check-page.cjs staged`:** exit 0 at 1440/768/390/320: no overflow, 6 downloads, 0.14.0 throughout
  (`check-page-staged.txt`). An extra check served `/app/privacy/` and `/app/` from the worktree at 1440/390/320: no
  overflow, and the new copy present (`privacy-and-copy-check.txt`).

## Device

- **rc1's code** passed on the S25 on 30 October 2026 with `bert-0.14.0-preview-97a1cd6.apk`. Checked: the You
  button, the speech-bubble tail, the glints, Market 1W and 1Y, and the You page. The owner confirmed the bark by ear
  ("bark sounds good").
- **The labels** were approved by the owner on the S25 during the `v014-afford` work.
- **Exact release code:** `bert-0.14.0-preview-30847cb.apk` is built from the release code, for a final look if
  wanted.
- Captures are in `/opt/bert-widget-qa/preview-0.14/device/` and are never copied into the repo.

## Play "What's new" (362 characters, limit 500)

```
• Bert talks in a speech bubble on Home.
• Home shows what you can tap: Price, Play, News and You.
• Your own You page: the name Bert calls you, your Bert, visits, game and cards. It all stays on your phone.
• A real recorded bark in The Lost Trail.
• Price charts now go back a week, a month or a year (1W, 1M, 1Y).
• Tools › Market opens BERT on CoinMarketCap.
```

## Owed, not done here

- The owner's "run it" (the `/opt/berthalla` fast-forward above).
- The Play upload of `bert-0.14.0.aab` and its mapping (copy them to the PC's `BERT-Play\` first).
- The GitHub squash onto `0ebbd95`.
- The platformer fast-forward.

Nothing has been pushed, copied to the PC or installed on the phone.
