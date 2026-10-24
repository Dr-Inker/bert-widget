# Next session — BERT app

## START HERE (memo 24 October 2026): new Navy and Violet wallpapers, ready for the release after 0.13.0

Antigravity redrew BERT Navy and BERT Violet (home, lock, picker previews): big Bert on a sunburst behind a ledge.
Owner: "they look great", then "take the lead on sorting everything". Branch `wallpapers-next` (`dea6dd5` art,
`67deaca` home fix, `c059d70` lock fix), **not** in the staged 0.13.0; `v013-nav` source is unchanged.
- **S25 check done:** home wordmark sat behind the dock (owner spotted it) → removed from both home masters. Lock
  tagline underline was struck through by One UI's charging line → removed from both lock masters. Bert's face sits
  behind the bottom icon row on the owner's main page, as Berthalla Nights already does; owner fine with it.
- **Sources:** `/opt/bert-widget-qa/wallpaper-sources-navy-violet/` (HTML masters, assets, `render.cjs`, README with
  the encode recipe and S25 layout bands). Not in the repo because one asset is from the private brand kit. Renders
  reproduce the committed WebPs byte for byte.
- **Tests on `c059d70`:** 242 host tests pass. One earlier full run failed
  `BERTUiTest.widgetStepOpensPersonaliseInTools` (scrollTo found the checklist text only in the unmerged tree); it
  passes alone on both `v013-nav` and `wallpapers-next` and in a full re-run, so it is a flake, not this change.
- **Phone:** restored to Berthalla Nights (seed colours match the pre-test dump). BERT Preview on the S25 is now
  `bert-0.13.0-preview-wallpapers-fix1.apk` (before the lock fix). Leftover `/sdcard/bertui.xml` (a UI dump of BERT
  Preview) to delete next time it's on adb.
- **To ship:** after 0.13.0 is live, merge `wallpapers-next` into the main line and include it in the next version;
  retake the Play screenshot that shows Tools › Style if it shows Navy or Violet.
- Accepted by the owner, don't re-raise: Violet resembles Mayor Purple; no pack is plain any more.
- Review page: https://claude.ai/artifact/A6DLpVsGegxtpNgYeLkEHk

## START HERE (memo 23 October 2026, later): 0.13.0 STAGED, awaiting "run it"

0.13.0 (29) from `b6e2f8f` (Play + Around the pack sheet + Tools › Style, and 0.12.1's Lost Trail fixes) is staged:
record `docs/evidence/2026-10-23/release-0.13.0/README.md`; files in `/opt/bert-widget-qa/release-0.13/staged/`;
website `release-0.13` at `dd49f44`. Owed by the owner: "run it" for the live ff; approval to install the signed build
over the real app on the S25 and to copy the AAB to `BERT-Play\` (both blocked by the classifier as deploys); the Play
upload and listing paste. After going live: merge `v013-nav` into the main line and push GitHub via the squash recipe.

## START HERE (memo 23 October 2026): 0.13.0 preview, Play + Tools › Style

Gemini left `docs/PROPOSAL-2026-10-23-UNIFIED-VISION.md` (untracked, in /opt/bert-widget and /opt/bert-widget-game).
Owner: "take the lead, make a preview of the changes you think are beneficial". Verdict per module:
- **Taken (A, navigation):** Explore → **Play** (Lost Trail picture + progress as on Home, Flappy Bert with a live
  tournament under it, Bert Music, then the pack's links). Create = Art | Saved. Personalise moved to Tools as
  **Style** (Android's "Wallpaper & style"); Tools pills 10dp so four fit at 360dp; the chosen pill scrolls into view.
  The checklist's widget step opens Tools › Style.
- **Not taken:** B (ViewModel; its code didn't compile and dropped the market-history loop), C (smooth soil gradient
  reverses critique round 2's dithered steps; night Bert is indoors; dusk Bert checked on the 0.12.1 render and reads
  fine), D/E (owner product/art calls; "two directions approved by the owner" has no record).

State: branch `v013-nav`, worktree `/opt/bert-widget-nav`, built on `v0121-trail` (0.12.1 is still unpublished).
Gate PASS at `ef4ac31`; independent Opus review: no must-fix, its two should-fixes applied. Preview APK (0.13.0 (29),
`global.bert.widget.preview`, re-signed with the S25 preview key) at
`/opt/bert-widget-qa/preview-0.13/bert-0.13.0-preview-ef4ac31.apk` (sha256 `88c979eb…`), copied to the PC at
`C:\Users\Sjpch\BERT-Preview\`. Not yet installed: the S25 wasn't on adb.
On release: the website's `/app/` tab image is now `web-play.png` (was `web-explore.png`) and the Play listing
screenshot `4-play.png` (was `4-explore.png`); update `docs/play/LISTING.md` and the site to match.

## START HERE (memo 15 October 2026, night)

**0.12.0 "Bert's day" IS LIVE (15 Oct, public checks pass).** berthalla is at `a54d1de` (`b317269` the release,
then a new garden link-preview card for `/` and `/app/`, made by `/opt/bert-widget-qa/release-0.12/social-card.py`).
Record: `docs/evidence/2026-10-15/release-0.12.0/README.md`. Rollback of the release:
`git -C /opt/berthalla reset --hard ec45301`. The signed 0.12.0 is installed on the S25 (checked). The owner uploaded
0.12.0 to Google Play (15 Oct); the listing text in `docs/play/LISTING.md` was updated for 0.12 afterwards and needs
pasting into the console. GitHub main = `4ce1488` (0.12 squashed onto `ba0e0ec`); the docs commits after it on
`v012-place` are not pushed.

**Next ideas (none started):** round 2's two open art notes (Bert outlined in an unoutlined scene; three boundary
materials in one view); the Play closed-testing clock (12+ testers for 14 days, check the console); the homepage card
says "the Android app" while the homepage is all of Berthalla (offered a homepage-specific card).

**How a release goes (0.12, from scratch to live):** gate (`tools/check-mobile-game.sh`), independent review agent,
`lintRelease assembleRelease bundleRelease`, `check-apk.py` from the release's evidence folder (copy the last one,
add the release's checks), stage into `/opt/bert-widget-qa/release-0.N/staged/`, website branch `release-0.N` in
`/opt/bert-widget-site-release` (APK rename + copy, release.json, versions in `/` and `/app/`, Workshop prints from
`android/app/build/outputs/play/screen/`, `/app/` tab images from `PlayStoreScreenshotsTest.websiteScreens`),
`verify-release.mjs`, `check-page.cjs staged`, the website pytest, then the owner says "run it" for
`git -C /opt/berthalla merge --ff-only release-0.N` and `check-page.cjs public`. GitHub: `git commit-tree HEAD^{tree} -p
origin/main` after a scrub scan of the added lines, then push that commit to main.

## START HERE (memo 15 October 2026, evening)

**Live:** 0.11.0 (BERT Violet). GitHub `main` = `ba0e0ec` (squashed). The owner owes the Play upload of
`bert-0.11.0.aab`.

**0.12.0 (27) "Bert's day"** is on branch `v012-place` in `/opt/bert-widget-brand`; the gate passes at `3571a2c`.
The concept, both critique rounds and the decisions are in `docs/CONCEPT-0.12-BERTS-PLACE.md`.

Home opens onto Bert's garden at the phone's time of day:
- **Scene and routine:** six relit Godot renders. Bert keeps a routine (gate, rounds, sun-nap, gate duty, evening,
  asleep indoors), and his pose follows the mood.
- **Interactions:**
  - A pat slips his hat.
  - At night, knocking on the window turns it off.
  - The chalkboard shows the price in cents.
  - The noticeboard opens a sheet with the update and the earlier ones.
  - Paw prints mark visits.
- **Widget:** a new "Bert's garden" widget.
- **Lines:** in Gochi Hand.

Art pipeline (in `/opt/bert-widget-qa/art-0.12/`):
- `place/bake2.py` builds `res/drawable-nodpi/place_*.png`. Its inputs are the Godot renders in the scratchpad r6 and
  PixelLab parts (gate b0, chalkboard c3, noticeboard c12, asleep pose c5 remapped to F/10, and the hat-slip pose made
  from content).
- Godot `main.gd`, `run_tod.sh` and the r6 renders are saved in `/opt/bert-widget-qa/art-0.12/godot/`.
- Godot scene copy on Shadow: `D:\dev\bert-app-0.12-tod-2026-10-15` (`--tod`, `--nogate`; shared slot). The scratchpad
  was temporary; everything bake2.py needs is now under art-0.12.

Next:
1. Fable round 2: applied in `ec3e030`.
2. Device check on the S25 (15 Oct, 16:42-17:11, golden/POST slot): scene, pat, chalkboard -> Market, noticeboard
   sheet, gate post -> The Lost Trail all checked. It found three bugs, fixed in `5222127` (237 tests, lint clean):
   the afternoon pat opened The Lost Trail (Bert's target was under the gate's), the board sheet's notes ran under
   the nav bar (now opens fully), and scrolled Home left dark status icons over the page (now a page-colour scrim
   and light icons). All three were checked on the phone. The scrim's first version drew nothing on device:
   the Scaffold consumes the status-bar inset, so `windowInsetsTopHeight(statusBars)` is 0 inside Home; it is now
   sized from the raw inset (host tests can't see this; Robolectric has no status bar). Dusk (18:03) also eyeballed.
   Installed on the S25: `place-preview-fix4-qa.apk`.
   Preview signing: the S25's "BERT Preview" is signed with `/opt/bert-widget-qa/android-home/debug.keystore`
   (alias androiddebugkey, pass android). `assemblePreview` here signs with ~/.android's key, so re-sign with
   apksigner before `adb install -r` or the install is refused (INSTALL_FAILED_UPDATE_INCOMPATIBLE).
3. Owner look: done ("preview feels good").
4. Release 0.12: done (see the night memo above).
## START HERE (memo 15 October 2026, afternoon)

**0.11.0 IS LIVE (15 Oct, public checks pass). It includes 0.10. Published with**
`git -C /opt/berthalla merge --ff-only release-0.11` (website `ec45301`). **Record:**
`docs/evidence/2026-10-15/release-0.11.0/README.md`. **Then** run `node
docs/evidence/2026-10-15/release-0.11.0/check-page.cjs public` and check both APK URLs against `23750b9c…`. The Play
kit is on the PC (`bert-0.11.0.aab` and mapping). GitHub main = ba0e0ec (squashed, scrubbed, 15 Oct). Still owed: the owner uploads to Play. (Old note: squash recipe; scrub
first), and upload to Play (the owner).

~~0.10.0 is STAGED to publish but not live.~~ The website branch `release-0.10` (`ad34b77`) is in
`/opt/bert-widget-site-release`. The owner runs `git -C /opt/berthalla merge --ff-only release-0.10`, because the
classifier blocks it as a production deploy. Then run `node docs/evidence/2026-10-15/release-0.10.0/check-page.cjs
public` (from the `v010` worktree) and check both APK URLs against `c056882f…`. The record is in
`docs/evidence/2026-10-15/release-0.10.0/README.md` on branch `v010`. Play: `bert-0.10.0.aab` and the mapping are on
the PC; moving the 0.9.2 files to `superseded-0.9.2\` was blocked by the classifier.

**0.11.0 (26) "BERT Violet"** is on branch `v011-brand` in the worktree `/opt/bert-widget-brand`, at `eec097c`.
- Gate: PASS, 228 tests.
- The preview build is installed on the S25 as "BERT Preview".
- What it does, from the owner's request on 15 October:
  - The official BERT brand kit is the app default: kit surfaces, Outfit (static weights), the gradient wordmark,
    glass buttons, and solid violet only for each screen's main action.
  - The old navy and orange is "Berthalla Navy" in Create → Personalise → App colours.
  - A new "BERT Violet" widget and wallpaper pack is the widget default for new installs.
- An independent review (no blockers) was fixed in `eec097c`.
- Details, kit rules and the bert.global comparison: `docs/BRAND-KIT.md`.
- Next:
  1. Owner look on the phone.
  2. Merge `v011-brand` after `v010` is published (it is built on `v010`).
  3. Regenerate the Play screenshots in violet: `PlayStoreScreenshotsTest` still frames them in navy.
  4. Regenerate the widget picker previews (`widget_preview_*.png`, still navy).
  5. Release recipe.
- Candidates not adopted: kit radii (20/24/32), 1px card borders, and cartoon Bertram art.

## Earlier memo (15 October 2026, morning)

**State:** 0.9.2 is LIVE. **0.10.0 (code 25) rc1 is built and staged, not installed or published.**
- Source: branch `v010` in the worktree `/opt/bert-widget-v010`, at `6bfc76f`. That is `v010-mybert` (My Bert,
  finished) merged with `v010-art` (the garden-gate banner and mood stills), plus the version bump, review fixes and
  a lint fix.
- Gate `tools/check-mobile-game.sh` PASS at `6bfc76f`: 227 unit tests, lint, debug build and the Lost Trail browser
  check. One earlier run failed the Lost Trail `vx<.1` check right after a heavy Gradle run; it passed 3 of 3 on its
  own, so it was a load flake.
- `lintRelease`, `assembleRelease` and `bundleRelease` all pass.
- Staged in `/opt/bert-widget-qa/release-0.10/candidate/`: `bert-0.10.0-rc1.apk` (sha `c056882f…`, 5.9 MB, pinned
  cert `f911463d…`), `bert-0.10.0-rc1.aab` (`3cf55e4c…`) and the mapping.
- **Live services checked 15 Oct** with real holder and staker wallets: Magic Eden wallet tokens, the RPC
  `getProgramAccounts` staking positions, DAS `getAssetBatch` on the public RPC (works without a key) and the Pinata
  original and WebP thumbnail. All match the parsers' field assumptions, and name and image numbers agree.
- **Independent review (Opus):** no blockers. Five should-fix items were all fixed in `39912dc`:
  - Forget, classic or Cancel now win over a lookup, choice or thumbnail in flight. Store epochs under one lock do
    this, and a race test was mutation-checked.
  - The widget refresh runs in an app scope.
  - The empty state has no marketplace link.
- Decided: Play Data safety declares the wallet address (Financial info → Other, optional, ephemeral). A chosen Bert
  is not re-checked against later lookups.
- Privacy page with the My Bert section: staged on website branch `release-0.10` in `/opt/bert-widget-site-release`.
  It is not committed yet; commit it with the app page stamp at release.

**Next:**
1. Device check rc1 on the S25 (ask the owner to put the phone down first). Upgrade from 0.9.2 in place. Then:
   - My Bert with a real holder address: find, use a Bert, then Home, widget and card.
   - Forget.
   - The garden-gate banner.
   - Widgets at 2×2 and 4×2.
2. Release recipe (DEPLOYMENT.md): `verify:release`, stamp the site (app page, `release.json`, APK, privacy page,
   Workshop version), public checks. **The live ff of `/opt/berthalla` needs the owner's OK.**
3. Play: upload the AAB and mapping to the PC BERT-Play folder; Data safety per `docs/play/LISTING.md`.
4. GitHub: DONE 15 Oct, main = ba0e0ec (squashed, scrubbed).

## Earlier memo (12 October 2026, late)

**State:**
- 0.8.2 is LIVE on berthalla.io/app (site `c5e50fe`).
- **0.9.1 (code 23)** is LIVE; source is branch `v091` in `/opt/bert-widget-direct`.
  - It is built from three merged tracks (`v091-home`, `v091-create`, `v091-widgets`) on top of 0.9.0.
  - rc3 (sha `703c3a51…`) is installed on the owner's S25.
  - Gate passes: 184 tests. An independent review found 6 issues; 5 were fixed and the 6th (static shortcuts
    resetting the task) was accepted as platform behaviour.
- 0.9.0 was never published separately; 0.9.1 supersedes it.

**What 0.9.1 is:** see docs/CRITIQUE-2026-10-12.md, items 1–10.
- The Home garden banner with the living F/10 pixel Bert (Bertians R2 art, owner-approved;
  `/opt/bert-widget-qa/art-bertians-r2/PROVENANCE.md`).
- Pull-to-refresh, Meet Bert, Day N, "N new", and first-seen times.
- Official chips. The app is a clear split from Berthalla: "From Berthalla" on updates; @bertcoincto and
  bert.global marked Official.
- Wordmark, art thumbnails, Share-first, and signed cards.
- Widget previews, 10sp floor, and mood never cut.
- BERT Navy is the default for fresh installs only.
- Launcher shortcuts.
- Market activity and smarter alerts.
- UK spelling and curly quotes, enforced by `CopyStyleTest`.

**0.9.1 PUBLISHED 12 Oct** on berthalla.io/app (site `fe866a6`, then the Workshop update at `c5034b7`). The owner
confirmed all 3 launcher shortcuts work. The Play kit is on the PC (`bert-0.9.1.aab`). Record:
`docs/evidence/2026-10-12/release-0.9.1/README.md`.

**Owed:**
- Owner: upload 0.9.1 to Play, check App integrity, create the Closed track, pick a contact email.
- Me, when the owner OKs it: push `v091` to GitHub `main` (still at 0.8.1).

**0.10 plan:**
- "My Bert" NFTs: `docs/NFT-RESEARCH-2026-10-12.md`. It needs a privacy-page update and the team's OK on art
  display.
- Display typeface for Bert's voice; one palette across the app.
- PixelLab mood portraits in the F/10 style.
- Glance 1.2 previews.
- A native Draw Bert. It needs a server endpoint first: `docs/ART-INVENTORY-2026-10-12.md`.
- Copy about the real dog stays character-first.

## Earlier memo (12 October 2026, 0.8.2)

**State:** v0.8.2 (code 21), the Play-readiness release, is built, gated, independently reviewed and
device-checked on the owner's S25, which now runs it. Source is `c11c1f5` on branch `v080` in the worktree
`/opt/bert-widget-direct` (not pushed, and not fast-forwarded into the `/opt/bert-widget` production checkout). The
record is in [release 0.8.2](evidence/2026-10-12/release-0.8.2/README.md), and the research behind it is in
[RESEARCH-2026-10-12](RESEARCH-2026-10-12.md).

**Blocked on the owner:**
1. **Website go-live.** The auto-mode classifier denied the production deploy, so it needs the owner's approval. The command is
   `git -C /opt/berthalla merge --ff-only release-0.8.2` (`58ab3fe` → `c5e50fe`: app page, release.json, APK and
   privacy page). Then run `python3 docs/evidence/2026-10-12/release-0.8.2/check-public.py` and
   `node docs/evidence/2026-10-12/release-0.8.2/check-page.cjs public`. Rollback: `/opt/bert-widget-qa/release-0.8.2/previous/`.
2. **Play Console.** Upload `C:\Users\Sjpch\BERT-Play\bert-0.8.2.aab` and its mapping. Check App integrity: is
   the app-signing key Google-generated? If it is, switch to the existing release key before open testing so
   sideloaders keep their data. Create the **Closed testing** track, because internal testing doesn't start the
   12-tester / 14-day clock, then repoint the berthalla tester form. Choose a contact email.
3. **Push.** GitHub `main` and `v080` stay at 0.8.1 until the owner OKs a push.

**Next build: 0.9.0 "Bert's day"** (research items 1–5, 7 and 8): price strip on Home; Lost Trail "Continue" card
and shortcut (read `bert-lost-trail-v1` from localStorage via evaluateJavascript); Today with Bert and a local
journal; dispatch → card → share with an excerpt picker (captions cap at 96 graphemes); real Bert photos rotated
**by date** (decided 10-12: mood is price-derived in `berthalla/mood/state.py`, so it stays a text label); buy/sell
counts and 1h/6h moves from `txns`/`priceChange`; better alerts. Then 0.9.x: Glance 1.2.0 generated previews,
widget refresh button, the polish pass, and an update nudge for sideloaders.

## Earlier memo (2 October 2026)

**State:** v0.8.1 (code 20) is live at https://berthalla.io/app/ and on the owner's S25 (in-place upgrade from 0.8.0).
GitHub `main` = this release. The Bert widget redesign shipped (owner's "looks empty and weird" fixed and checked on the
S25 at 4×2 and 2×2), and the price and market widgets no longer leave empty bands. Nothing from the critiques is open
except retiring the price server.

**Testers (2 October, evening):** the app is in Google Play **internal testing**; its opt-in link
`https://play.google.com/apps/internaltest/4701060804022183943` is published on berthalla.io's tester form
(`#testers`, "Form 7-B"). Sign-ups land in the berthalla tester backend and ping the owner on Telegram; export
them for Play Console with `cd /opt/berthalla && venv/bin/python -m berthalla.testers export --app bertapp --format comma`
and add them to the internal-test email list. The homepage Workshop shows v0.8.1 with the 1 October Play
screenshots; update `/opt/berthalla/website/index.html` when a new version ships. See `/opt/berthalla/deploy/TESTERS.md`.

**Waiting on the owner:**
1. ~~Play Console upload~~ done: in internal testing as of 2 October (see Testers above). Originally: upload from `C:\Users\Sjpch\BERT-Play\` on the drinkerlabs PC (`bert-0.8.1.aab`, `mapping-0.8.1.txt`,
   graphics, `LISTING.md`; 0.8.0 moved to `superseded-0.8.0\`). Offer to walk through it.
2. A contact email for the Play listing (not chosen yet; do not use the owner's personal address without asking).
3. ~~APK vs Play~~ **Decided (owner, 2 October):** berthalla.io/app keeps serving the signed APK until the app is
   through Play testing and fully approved, then points at the Play Store link instead.

**Play upload in progress (owner, 2 October):** app name `BERT: Bert's World`, package `global.bert.widget`, release
name `0.8.1`, en-GB release notes drafted in chat (first release, five feature bullets); 85 "localisations" are AndroidX
translations, not countries; Nigeria wanted (Closed testing → Manage track → Countries / regions). Expect the 12-tester /
14-day closed test for a new personal account. Offered, not done: a home-screen widget Play screenshot; trimming locales
to English (`androidResources.localeFilters`) in a later release.

**For me to do:**
- **When the owner says Play approved:** switch every download link on berthalla.io/app (six `a[download]` plus the nav
  and dock) to the Play listing URL; keep the last APK reachable for existing installs; add a short note that
  sideloaded installs must uninstall the APK and install from Play to get updates (Play re-signs the app, so the two
  cannot update each other; that wipes on-device holdings and saved cards). Update `check-page.cjs` to expect the Play
  link, `release.json` and the `/app/` FAQ.
- ~15 October 2026: count `/widget/api/quote` + `/widget/api/history` requests with vs without `?via=fallback` in
  `/var/log/nginx/access.log`; propose retiring `bert-widget.service` if untagged (pre-0.7) traffic has faded.
- After Play review: fix anything Play flags; keep `docs/play/LISTING.md` in step.
- iOS fractional-date fix is uncompiled; needs Xcode.
- The BERT Preview on the S25 is signed with `/opt/bert-widget-qa/android-home/debug.keystore` (see 0.12 Next, item 2).

**Working setup:**
- Repo `/opt/bert-widget` is the production checkout (the quote/history service runs from it). Develop in the
  worktree `/opt/bert-widget-direct` (branch `v080`, = `main`), gate with `tools/check-mobile-game.sh`, then
  fast-forward. Website releases stage in `/opt/bert-widget-site-release` and fast-forward `/opt/berthalla`
  (preserve the uncommitted homepage/Music edits there; check their hashes).
- Game: `/opt/bert-platformer` (`master`, private GitHub). App-only; bundle builds into
  `android/app/src/main/assets/games/lost-trail/`.
- Device: S25 via `ssh pc` + `C:\Users\Sjpch\platform-tools\adb.exe -s R5CY13S5TRD`. The owner often watches this
  session on that phone: ask them to unlock/put it down, guard taps to BERT's package, restore any settings changed.
- Release recipe: see the v0.8.1 evidence README (scripts alongside it) and `DEPLOYMENT.md` (version bump → gate → signed APK + AAB →
  verify + check-apk → stamp site → device upgrade → publish → public checks → records → push).

## v0.8.1 published — October 2: the Bert widget redesign

Full record: [release 0.8.1](evidence/2026-10-02/release-0.8.1/README.md). Render tests now fail on empty bands in any
widget, on a too-small Bert picture, at font scale 1.3 and on theme contrast.

## v0.8.0 published — October 1; Google Play next

Live at https://berthalla.io/app/ (code 19). Everything from the October 1 improvement list is done except retiring
the price server (measure first). Play kit: `docs/play/` and the drinkerlabs PC at `C:\Users\Sjpch\BERT-Play\`
(app bundle, mapping, icon, feature graphic, 9:16 screenshots, `LISTING.md` with data-safety and declaration answers).

- **Owner, for Play:** create the app in Play Console, upload `bert-0.8.0.aab` (+ `mapping-0.8.0.txt`), paste the
  listing, set the contact email, and answer the declarations per `LISTING.md`. The existing release key is the upload
  key; Play App Signing will hold the app-signing key.
- **Sideload users:** berthalla.io/app keeps serving signed APKs. A Play install and a sideloaded APK share the package
  name; once Play re-signs with its own app-signing key, the two cannot update each other — decide whether the website
  should point to Play after launch.
- **Price server retirement:** count `/widget/api/quote` and `/widget/api/history` hits with and without `?via=fallback`
  in `/var/log/nginx/access.log` over a week or two before stopping `bert-widget.service`.
- **iOS:** date fix is uncompiled; build in Xcode before trusting it.

## v0.7.1 published — October 1 (0.7.0 earlier the same day)

Live at https://berthalla.io/app/ (APK 5,117,036 bytes, SHA-256 `15bcaffe…`, code 17). Release source is
`main` (fast-forwarded from the `direct-sources` branch). Full record:
[release evidence](evidence/2026-10-01/release-0.7.0/README.md) and `DEPLOYMENT.md`.

- **Phone-direct data:** quotes from DEX Screener and 24h history from GeckoTerminal; the Berthalla routes
  (`/widget/api/quote`, `/widget/api/history`) are now fallback, tagged `?via=fallback` in nginx logs. Old
  installs (≤0.6.0) still poll the server untagged. Retire `bert-widget.service` only after untagged traffic
  has faded and tagged fallback stays rare.
- **Production checkout:** `/opt/bert-widget` runs the quote/history service from its working tree
  (`WorkingDirectory=/opt/bert-widget`). Do server work in a git worktree and fast-forward only after tests.
- **Device testing:** owner's Samsung S25 Ultra (Android 16) via adb on the drinkerlabs PC
  (`ssh pc`, `C:\Users\Sjpch\platform-tools\adb.exe -s R5CY13S5TRD`). The owner often watches the session on
  that phone; ask them to put it down/unlock before driving it, and never apply wallpapers unasked. A
  minified `preview` build (`:app:assemblePreview`) installs beside the real app.
- **Game:** The Lost Trail is app-only (browser copy retired 2026-10-01). New title screen at platformer
  `084dcb0`; bundle provenance is checked byte for byte by `tools/qa/lost-trail-browser.mjs`.

### Tier 2 — RELEASED as v0.7.1 (app `5497b2e`, website `09d70cf`)

- Home tournament card (countdown, prize pool, top 5, final standings; untrusted names sanitised).
- Widgets fit at every launcher size; `BERTWidgetRenderTest` renders the real Glance widgets to RemoteViews on the
  host and fails on wrapped/ellipsized/clipped text (it caught the 240x120 market price split, 120x120 compact
  header/footer and 360x200 market footer). Widget data loads inside the composition keyed on a revision that
  `updateAllBERTWidgets` bumps; sparkline colour follows the 24h change.
- The Lost Trail portrait play fills the screen (platformer `35c37db`), larger HUD, single Pause.
- Device-checked on the S25 before release (widget in-session reload, tournament card, portrait play, 0.7.0 → 0.7.1 upgrade).

### Original Tier 2 list (October 1 critique)

1. Market widget overflows at its 240dp minimum (`BERTWidget.kt` fixed 142dp holdings capsule) and the compact
   layout clips its footer at the 120dp minimum height. Needs widget rendering tests.
2. Widget data is loaded outside `provideContent` (possible stale recomposition in a live Glance session).
3. Live tournament card on Home from `status.json` (`flappy.top`, `pool`, countdown).
4. The Lost Trail in portrait: playfield letterboxed (~40% empty), HUD text ~9px, duplicate Pause controls.

## Mobile Lost Trail — v0.6.0 published, September 9

The owner's request to add bert-platformer to the Bert app with Figma MCP is complete for
Android. Update at https://berthalla.io/app/, then **Explore → The Lost Trail**. All six worlds
work offline with portrait/landscape touch controls, Run toggle, pause/Back behavior and
saved lanterns/items/gates. Returning retains Explore. Figma portrait/landscape frames are
`58:325` / `58:326` in `oTxUhbR4F1d3zDj8WlJOqh`, using actual gameplay captures.

Published app source: `569f9ca232098a4f8419d590d65273fba364157a`, game source:
`36ec959f2a4ac9b200def91687c892a030e73e5e`, website:
`6ef9a97822615d8720a96b5ba8d0f74be190d456`. Version 0.6.0 / code 16; signed APK hash
`3a683083da772cbff5efb0c2decef740e88c3ae2d865a43ff7011888a2387d30`, 30,090,889 bytes.

At the app source SHA,
`GRADLE_USER_HOME=/opt/bert-widget-qa/gradle ANDROID_USER_HOME=/opt/bert-widget-qa/android-home JAVA_TOOL_OPTIONS=-Duser.home=/opt/bert-widget-qa EVIDENCE_DIR=/tmp/bert-mobile-game-release tools/check-mobile-game.sh`
passed: 8 Node tests, 62 Android tests, debug lint/build and exact bundled-game browser gate.
With the same Gradle environment, `android/gradlew -p android :app:lintRelease :app:assembleRelease --console=plain`
passed; debug/release lint each has zero errors and 35 warnings. `npm run verify:release -- android/app/build/outputs/apk/release/app-release.apk /opt/bert-widget-qa/release-0.6.0/staged/release.json 15`
passed signature/package/version/hash verification. `EVIDENCE_DIR=/tmp/bert-platformer-mobile-final tools/deploy-gate.sh`
passed at game source `36ec959` (13 engine cases, six browser courses, art/input/save checks).

At website `6ef9a97`, `GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=safe.directory GIT_CONFIG_VALUE_0=/opt/berthalla PYTHONDONTWRITEBYTECODE=1 PYTEST_ADDOPTS='-p no:cacheprovider' .claude/test-gate.sh`
passed after the authorized fast-forward (577 passed, 33 skipped), following the same full gate
in staging. `python3 docs/evidence/2026-09-09/release-0.6.0/check-public.py` and
`node docs/evidence/2026-09-09/release-0.6.0/check-page.cjs public` passed against that website
and app source `569f9ca`: both downloaded APKs match, current page/assets match, fresh quote
observations advance, four responsive widths have zero overflow. No service/nginx changes
or GitHub push. Root homepage/Music overlays remain as before. Rollback:
`/opt/bert-widget-qa/release-0.6.0/previous`.

The initial 40.4% portrait playfield and ground-obscuring toast were rejected. Final portrait
playfield is 53.9% at 390×724, targets are at least 48px, and the toast sits above the path.
[Full evidence, captures, commands and limits](evidence/2026-09-09/release-0.6.0/README.md).
Physical-phone install/performance, OS process-death/system bars and accessibility remain
UNVERIFIED; no accelerated emulator/device is available. iOS is unshipped. Do not repeat the
known failed software AVD attempt without new evidence. Native acceptance is not inferred
from host/browser tests. The older v0.5.0 release statements below are historical.

## Homepage preview correction — September 9

Owner feedback requested dark layouts and current Flappy Bert artwork. The preview at
https://berthalla.io/preview/home/ now uses deep navy, warm text and orange actions, plus an
unedited screenshot captured today from a real run of the live Season 5 game. Website source
`0c0cb6be1b5dc6cbf4702b60c0dc1fa5828c7fc1`; the following checker-only commit is
`c0ec1e68f85bb73656f1a35a08d4333299b81420`. Both Figma hero concepts now use the dark palette.
This supersedes the cream design and outdated portfolio screenshot in the earlier preview.

At the website source SHA, `HOME_PREVIEW_URL=https://berthalla.io/preview/home/
HOME_EVIDENCE=/opt/berthalla-home-design/docs/evidence/2026-09-09-home-dark node
docs/evidence/2026-09-09-home-design/check-home.cjs` passed at 1440/768/390/320px with zero
overflow, dark surface/text contrast assertions and existing functional checks. The documented
`.claude/test-gate.sh` command passed before and after deployment there (577 passed, 33 skipped),
and again at the merged checker SHA. `python3 docs/evidence/2026-09-09-home-dark/check-public.py`
passed at the checker SHA: all public preview bytes match and homepage/Music hashes are preserved.
Exact gate environments, captures and live game provenance are in the website repo's
`docs/evidence/2026-09-09-home-dark/README.md` and `docs/RESEARCH_LOG.md`.

Owner review continues at the preview URL; the root homepage and `/app/` were not changed in
this correction. This does not change native app release/acceptance status.

## Homepage design preview — September 9

The owner accepted a less formulaic homepage direction. A complete responsive preview is now
published at https://berthalla.io/preview/home/; the original homepage, `/app/`, signed APK and
pre-existing Music work remain unchanged. Implementation lives in the Berthalla repository,
`website/preview/home/`, at source `11a0a2db17deb1d96f37e930a4a6d27e5a185a3d`. The isolated
working checkout is `/opt/berthalla-home-design`. The website's incumbent `docs/RESEARCH_LOG.md`
and `docs/evidence/2026-09-09-home-design/README.md` carry the design decisions and public evidence.

At that website SHA, `.claude/test-gate.sh` (with `PYTHONDONTWRITEBYTECODE=1` and
`PYTEST_ADDOPTS='-p no:cacheprovider'`) passed before and after the deployment fast-forward
(577 passed, 33 skipped). `HOME_PREVIEW_URL=https://berthalla.io/preview/home/ node
docs/evidence/2026-09-09-home-design/check-home.cjs` passed at the same SHA for 1440/768/390/320px,
including live/stale/unavailable data, untrusted player text, keyboard skip navigation and
JavaScript-disabled fallbacks. The 390px page is 4253px tall versus the original 9249px.
Figma hero direction: desktop `46:2`, mobile `46:15`, file `oTxUhbR4F1d3zDj8WlJOqh`.
Next step is owner feedback on the working preview; the preview does not replace `/`.

## Canonical `/app/` route — deployed and verified

The owner explicitly authorized updating `/etc/nginx/sites-available/berthalla.io` and reloading nginx after automatic approval review requested that scope. **The migration is complete:** `https://berthalla.io/app/` is canonical, all five homepage app links point there, and `/widget`, `/widget/`, `/widget/index.html` permanently redirect with query strings preserved. Website source `9d906ba3d5d27ce7f4340aa235d014c7e1f3496c` is deployed; the pre-existing Music overlay remains live and uncommitted. The previously pending redirect fallback and migration docs are applied. APK, metadata, shared assets and installed-app API paths remain intact.

The complete candidate and installed configuration passed `nginx -t`; `systemctl reload nginx` and `systemctl is-active nginx` succeeded at app checkout `f82fb49da9e3a1e52f181a7ca60c5a47de8f6495` (routing template unchanged from `0f5f223ee121b26bbd0c9e24a63d3e3b9bf1432e`). Installed vhost SHA-256: `5652e9a00131940d076b6c00d08ddddcb9957204b81c7dd7a78de11909459f1a`. Hash guards accepted only the two already-published static files at their expected new hashes and the remaining files/config at their recorded original hashes.

At website SHA `9d906ba3d5d27ce7f4340aa235d014c7e1f3496c`, `GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=safe.directory GIT_CONFIG_VALUE_0=/opt/berthalla PYTHONDONTWRITEBYTECODE=1 PYTEST_ADDOPTS='-p no:cacheprovider' .claude/test-gate.sh` passed (577 passed, 33 skipped). From `/opt/bert-widget-qa/app-route-migration`, `python3 check-routes.py` passed all 15 public requests, including eight required redirect cases, both fresh quote APIs and both APK aliases. `APP_ROUTE_EVIDENCE=/opt/bert-widget-qa/app-route-migration/approved node check-page.cjs` passed 1440/768/390/320px with zero overflow, six correct downloads and a JavaScript-disabled download fallback. [Approved deployment, checks and capture](evidence/2026-09-09/app-route-migration/README.md). No further approval or migration work remains; earlier pending-state files in that directory are historical records. Native app acceptance is still outside this website verification.

## App website refresh — 2026-09-09

The owner requested that berthalla.io and the widget page reflect BERT as a general app. **Both pages are updated and live** at website source `86a370e493fd8da1be700c702e7873524793780b`: app navigation and homepage chapter, Home / Explore / Create / Tools previews, caption-card and local-collection copy, external destination labels, installation/privacy guidance and a new social card. Widgets and all three themes remain app features. The homepage keeps its character identity, X-first hero/video/sound and Music chapter. `/widget/`, signed APK names and package/service routes are stable. v0.5.0 and its binary are unchanged.

At that website SHA, `GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=safe.directory GIT_CONFIG_VALUE_0=/opt/berthalla PYTHONDONTWRITEBYTECODE=1 PYTEST_ADDOPTS='-p no:cacheprovider' .claude/test-gate.sh` passed from `/opt/berthalla` (577 passed, 33 skipped). From this repo, `APP_SITE_EVIDENCE=/opt/bert-widget/docs/evidence/2026-09-09/app-site-refresh node docs/evidence/2026-09-09/app-site-refresh/check-site.cjs public` passed for both pages at 1440/768/390/320px, all with zero document overflow. `python3 docs/evidence/2026-09-09/app-site-refresh/check-public-assets.py` verified nine public files and the unchanged signed v0.5.0 APK. [Commands, captures, hashes and limits](evidence/2026-09-09/app-site-refresh/README.md).

Pre-existing Music edits remain uncommitted and live; the website commit includes only this pass's edits. The evidence records the preserved Music overlay and actual deployed file hashes so the exact checked homepage is reproducible. An existing 11px Arcade overflow at 320px was fixed. No application/backend/configuration changes or GitHub publication occurred. The old Figma frames predate this app refresh; code and browser evidence are the current design record. Device acceptance remains UNVERIFIED.

## Release deployment — 2026-09-09

The owner explicitly requested deployment to berthalla.io after being told that the September improvements were local and native validation remained outstanding. **v0.5.0 (version code 15) is now published** under `/opt/berthalla/website/widget`, with the same production package and signing certificate. App source: `938d9d30a85025bf10caad01bcb3f4cbf72f23ae`; website: `302a21cdee26cfa028e47b1b2476ebcc54ca5923`; APK SHA-256: `c47fbee48bc37db5d0974fa453825f34288d9d9983a603033edafcc5040df4e0`.

`GRADLE_USER_HOME=/opt/bert-widget-qa/gradle ANDROID_USER_HOME=/opt/bert-widget-qa/android-home JAVA_TOOL_OPTIONS=-Duser.home=/opt/bert-widget-qa tools/check-android.sh` passed at app source `938d9d3` (8 Node tests, 60 Android tests). With the same environment, `android/gradlew -p android :app:lintRelease :app:assembleRelease --console=plain` passed there; debug/release lint each reports zero errors and 35 warnings. `npm run verify:release -- android/app/build/outputs/apk/release/app-release.apk /opt/bert-widget-qa/release-0.5.0/staged/release.json 14` passed there. `python3 /opt/bert-widget-qa/release-0.5.0/check-public.py` and `node /opt/bert-widget-qa/release-0.5.0/check-page.cjs public` passed against website `302a21c` and app source `938d9d3`: both public APKs match the signature/hash, quote routes are fresh with advancing observations, all six download links target v0.5.0, and measured page overflow is zero at 1440/768/390/320 px. [Logs, commands, captures and limits](evidence/2026-09-09/release-0.5.0/README.md).

The landing page now describes the general app and correct Personalize route. Narrow-screen hero decorations/phone sizing no longer widen the document; asset URLs use content hashes after a reused numeric query served stale Cloudflare content. Previous versioned APKs remain available. Rollback files are under `/opt/bert-widget-qa/release-0.5.0/previous`; production signing files and service/nginx configuration were unchanged. Unrelated website work was preserved. No GitHub push or GitHub release was performed. Device journeys remain UNVERIFIED; deployment does not establish S-tier acceptance. The owner's deployment instruction superseded the older no-deployment restriction for this release.

Updated 2026-09-09. This repo had README/RESEARCH/DEPLOYMENT documentation but no campaign handoff ledger before this pass; keep continuity here and in the dated critique. Do not use the frozen session-graph archive.

## September 8 implementation history — research accepted

Iteration 12 audits acceptance and finds the next substantive work blocked on native test access. All six campaign criteria have an explicit evidence boundary in the latest critique; all ten device journeys remain UNVERIFIED. The host has no connected device or usable emulator acceleration, and the previous software AVD hit a blocking System UI ANR. The owner has been asked for test access or a manual preview/checklist run; no answer is available. No S-tier score or completion is claimed. Do not add speculative features or repeat the unchanged AVD attempt to prolong the loop.

Iteration 11 is verified: compact Tools content grows from 281 to 408 dp and shows price plus complete quote age on first entry; Create gains 53 dp. Section labels retain 16 sp, full visibility and 66 dp-high targets. The repeated pinned heading is omitted at larger text settings; normal-size screens stay unchanged.

Iteration 10 is verified: Market labels stack at larger text settings, and the holdings headline keeps the tested $105,000.00 amount on one line. Extremely long values retain every digit at a readable floor. Populated layout, range selection/restoration and local holdings edit flows now have host evidence. The former “linear 2x” description is corrected below: the actual host font scaling is nonlinear.

The owner authorized a repeated premium-quality improvement loop. Latest verified application source: `f62c961c39c011bbecec02dc4d9b2232e5ba83d4`; changes include native creation and a private saved-card collection, measured large-text navigation, expandable updates and a consistent icon/color system. A separately installable BERT Preview APK and packaged-artifact gate now support practical device review. Current root lease covers Android source, host UI verification, QA tools and repo ledgers. No other agents are authorized. No deployment or external-service mutation occurred.

Iteration 6 is verified: large-text wallpaper/widget actions stack without splitting their labels at 320 dp; Mayor Purple widget negative-change contrast improves from 4.38:1 to 4.66:1. The strengthened checks failed on the baseline and pass on the fixed source. The audit covers 34 declared text/surface pairs, not every UI state or a physical display.

Iteration 7 is verified: ordinary over-limit captions remain editable with clear shortening guidance, Unicode character counting agrees with saved-card validation, and oversized pastes receive visible feedback without losing the draft. The notice fits y=370–498 dp inside the current y=164–498 dp Create viewport at 320×640 dp / 2x text. An earlier attempt ended with exit 143 after tests; the final full gate passed at `e56060495166b4ce9df5933da489c5adc9715dc9`.

Iteration 8 is verified at the latest source: a full collection automatically reveals its 40-card limit and Manage collection action. Cancelling deletion keeps all cards; deleting one then returning to Art preserves the draft and palette and allows saving. Rejection preserves all 80 files byte-for-byte. At 320×640 dp / 2x text, message and action labels fit y=254.5–490 dp inside the current y=164–498 dp Create viewport without another scroll.

Iteration 9 is verified at the latest source: collection and card detail have independent scroll positions. Both Back paths, collection saved-state recreation, and detail recreation/tab changes preserve measured positions exactly at normal and 2x text. The first candidate stopped at a test that clicked Delete before the asynchronous card load; the final gate waits for the loaded image and passes. All 24 prior captures are byte-identical to the preceding verified build.

## Owner direction

This is a **general BERT app**. Widgets are one feature, alongside market tools, personal holdings, themes, and future BERT experiences. Android v0.5.0 is published; native acceptance remains outstanding. The iOS scaffold remains unshipped.

## Read first

- `docs/RESEARCH-2026-09-08.md`: latest ecosystem findings, proposed navigation, feature priorities and validation plan. Recommendations are not implementation commitments.
- `docs/CRITIQUE-2026-09-08.md`: baseline defects, fixes, product gaps and evidence.
- `RESEARCH.md`: incumbent architecture and the September product-direction decision.
- `android/README.md`: source behavior versus published release.
- `DEPLOYMENT.md`: production/release requirements; no deployment is authorized by this handoff.

## Current implementation

- Home / Explore / Create / Tools navigation, adaptive large-text layout, saved state per section, Android Back to Home. Expanded updates and a consistent native icon/color system. Pinned Create/Tools section tabs and verified host saved-state restoration.
- BERT home with cached dispatch/mood/event data and independent quote/activity refresh.
- Direct curated ecosystem entry points with explicit browser/Telegram/X labels.
- Create / Art leads with local caption cards; Saved adds a private PNG collection with reopen, share and confirmed deletion. Web drawing is secondary; Personalize contains existing widget/theme setup.
- Real observed-price chart ranges; honest collection and gap states.
- Offline-accessible holdings editing, optional total cost and estimated gain/loss.
- Age-aware quote freshness, foreground/resume refresh and cancellation handling.
- Theme previews separated from applying widget palettes; persistent Home/Lock wallpaper result reporting, including partial success and zero-ID failure.

## Verification

**Development and preview gates passed at `f62c961c39c011bbecec02dc4d9b2232e5ba83d4`:**

```bash
GRADLE_USER_HOME=/opt/bert-widget-qa/gradle \
ANDROID_USER_HOME=/opt/bert-widget-qa/android-home \
JAVA_TOOL_OPTIONS=-Duser.home=/opt/bert-widget-qa \
tools/check-android-preview.sh
```

8 Node tests, 60 Android JVM tests (21 Compose UI + 3 caption-text + 1 contrast audit + 6 library/PNG/provider + 6 wallpaper-result tests), no failures. LintDebug and lintPreview: 35 warnings each, zero errors. AssembleDebug, assemblePreview and packaged APK inspection passed. [Output](evidence/2026-09-08/host-f62c961/preview-gate.txt), [results and capture hashes](evidence/2026-09-08/host-f62c961/results.json). The follow-up commit contains documentation/evidence only.

**Provenance audit passed at `a2ca8e0a9b22971bc206b752c02ffbc6cb28d0bf`:** `python3 docs/evidence/2026-09-08/acceptance-audit/verify.py`. All 74 recorded evidence files and the preview APK match; ten JUnit XML files total 60 tests with no failures/errors/skips. Application/build/gate sources remain identical to `f62c961`. This checks existing evidence, not app behavior. [Audit result and exact native diagnostics](evidence/2026-09-08/acceptance-audit/results.json): no `/dev/kvm`, acceleration check exit 3, ADB exit 0 with no devices. All native journeys and S-tier acceptance remain UNVERIFIED.

**Host UI evidence now exists.** Fifty-one actual Compose app-shell captures at 360×800 dp and 320×640 dp include offline Home, Explore, Create, collection, reopened cards and populated Tools at font-scale settings 1 and 2. Market and holdings values are deterministic review fixtures, not current data or user holdings. The full creation preview fits y=211–531 dp above navigation at 720 dp. Navigation labels fit one line at 2x text with >=48 dp targets; saved/shared PNGs preserve exact pixels. Draft switching, long-update expansion, offline save/open/keep/delete and full-collection recovery are exercised. Capacity/navigation screenshots use seeded cards. Both back paths and saved-state recreation preserve the same collection anchor; detail scroll state survives recreation and tab switching. 29 of the prior 49 captures are byte-identical, including every standard-font screen; 20 larger-text section captures reflect the revised pinned navigation. The two new first-entry captures measure Create at y=164–498 dp and Tools at y=90–498 dp, with the entire delayed age at y=333.5–429.5 dp. All section labels remain 16 sp and on one line, with full targets at least 66 dp high and 65.5 dp wide. The chart range controls measure at least 48×48 dp; selected ranges survive saved-state recreation. Local holdings validation/cancel/save/confirmed removal is exercised through the real private store. This is narrower than a device test.

**Scaling provenance correction.** Older reports called the host setting “linear 2x”; that wording was incorrect. At density 2, 14 sp converts from 28 px to 52 px and 38 sp from 76 px to 90.17143 px when fontScale changes from 1 to 2. The renderer uses nonlinear scaling. [Recorded conversions](evidence/2026-09-08/host-f62c961/populated-tools-large.json). Historical hashed evidence is preserved; read its scaling labels with this correction.

**Device journeys remain unverified.** The API 30 software-AVD attempt installed the earlier `7a54a05` preview and rendered Home, but Android displayed a blocking “System UI isn’t responding” dialog. No app navigation journey passed. [Attempt results](evidence/2026-09-08/native-preview-7d5939a/results.json), [actual native capture](evidence/2026-09-08/native-preview-7d5939a/startup-observation.png), [observation/provenance](evidence/2026-09-08/native-preview-7d5939a/observation.json). Boot took 208.82 s and installation 106.22 s. The previous 90 s install allowance was insufficient, but increasing it did not produce a usable native test environment. Runner and emulator are terminal; do not treat old locks or logs as live processes. Do not infer real recipient delivery, OS process-death restoration, TalkBack, API 36 system bars, launcher/wallpaper success or physical performance from these results. No release-signature/publication gate was run.

`tools/qa/android-companion.py` exercises a disposable AVD with labelled quote fixtures and captures native screenshots/UI trees. Read its arguments; never point this workflow at a personal/live device. Fixture market values are not real quotes. This host lacks `/dev/kvm`; software emulation is slow. Run one heavy job at a time.

`tools/qa/android-preview-smoke.py` exercises the isolated preview in a read-only AVD overlay. It records runner SHA separately from APK source/hash, uses a 300 s install allowance and disables Wi-Fi/mobile data before first launch. Its follow-up diagnostics preserve the last UI tree, capture a failure screen/logcat, keep tool temp files under the chosen output path and disable the host `/var/crash` exception hook. It now defaults to automatic acceleration; `--accel off` is an explicit software-emulation diagnostic, not the default for a functioning test host. Those diagnostic changes have not been exercised in another native run. Do not repeat an identical failing emulator attempt; the next native pass needs a functioning environment or a specific new diagnostic hypothesis.

On this host, build caches and Android preferences for this pass are under `/opt/bert-widget-qa`, keeping tool writes out of `/root`. No production signing material is copied into that directory.

## Review artifacts

- [Interactive visual review](evidence/2026-09-08/review.html): current screens, before/after creation, exact source and measurement links. Host renders, not phone screenshots.
- Stable local APK: `android/app/build/outputs/review/BERT-preview-f62c961.apk` (ignored build artifact). SHA-256 `3c6871ca46b22288d138b6d8a7eadc41e11472ec6b673b3a6eff40c9da1077b3`.
- Package `global.bert.widget.preview`, Android debug signature, labels BERT Preview, independent app/provider identity. It uses public HTTPS endpoints; emulator fixtures remain debug-only. Installation of this updated APK is unverified. Only the earlier `7a54a05` preview installed on the API 30 test AVD. Coexistence with the published app and real-device journeys remain unverified.
- [Device review contract](DEVICE-REVIEW.md). The owner was asked asynchronously for the primary phone model/Android version; no answer was available when this handoff was written. The supported range remains API 26–36; do not invent a tested device.

## Next implementation priorities

The owner accepted the research and the first implementation now uses Home / Explore / Create / Tools. Start validation from the report's user tasks and current native QA script.

1. Resume the premium-quality loop when usable native access or concrete device/user findings arrive. Iterations 6–11 fixed measured contrast, input feedback, capacity recovery, return position, populated figures and pinned navigation defects. Iteration 12 audited every acceptance row and rechecked the native barrier; it adds no feature work or quality score. Start the next pass with the current preview and `docs/DEVICE-REVIEW.md`, recording actual model/OS/settings and tested SHA. The existing smoke runner is a narrower first step, not all ten acceptance rows. Do not manufacture progress with extra features or repeat the unchanged failing software AVD. A same-process provider read is not recipient delivery; Compose state restoration is not OS process reclamation.
2. Confirm long-term source ownership for `https://berthalla.io/status.json`. The adapter handles its current shape but it is not a chronological feed or documented native API. Do not scrape stale homepage fallback cards.
3. Verify external phone journeys for Flappy Bert, web drawing, music and Woofhub. Official Playground labels Farmhalla and Hold The Line coming soon; they are excluded from the playable catalog. Custom Tabs are a possible follow-up; persistent saved creations are implemented in iteration 2.
4. Add favorites only after confirming repeated discovery use. Explore a non-price widget after the corresponding app content works.
5. Validate widget placement and Home/Lock wallpaper application on physical launchers; confirm the now-explicit partial-apply outcomes. Bring iOS to parity only as an explicit separate macOS/Xcode effort.

Research evidence: `docs/evidence/2026-09-08/ecosystem-research.json`. Authenticated flows, web generation, gameplay and mobile behavior remain unverified. Research results are separate from the current implementation checks.

## Handoff contract

TO / TASK / CWD / BASE / READ / WRITE (edit lease) / DO_NOT_TOUCH / ACCEPT / SIDE_EFFECTS / RETURN

TO: Next BERT app session
TASK: Resume the general BERT app improvement loop when native test access or concrete device/user findings are available; complete the device contract before any S-tier claim
CWD: /opt/bert-widget
BASE: Published v0.5.0 app source 938d9d30a85025bf10caad01bcb3f4cbf72f23ae; website 302a21cdee26cfa028e47b1b2476ebcc54ca5923. Release commands and evidence above. Native attempt runner 7d5939a072f409c5c756bcdaca43dfb6eb97dec5 failed at a blocking System UI ANR; no app journey gate passed.
READ: docs/DEVICE-REVIEW.md, docs/NEXT-SESSION.md, docs/RESEARCH-2026-09-08.md, docs/CRITIQUE-2026-09-08.md, RESEARCH.md, android/README.md, DEPLOYMENT.md
WRITE (edit lease): Root Android source, QA and repo ledgers under the owner's ongoing loop authorization; no other agents
DO_NOT_TOUCH: /root/.codex/session-graphs; live /var/www; signing credentials; unrelated repos
ACCEPT: Executable development gate at the actual source SHA plus native evidence for visual claims; no unverified release claim
SIDE_EFFECTS: Owner-authorized v0.5.0 website/APK publication completed on 2026-09-09; no public git push or external messages
RETURN: Changes, command + tested SHA, capture paths, and explicit unverified work
