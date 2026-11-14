# Response to the 0.14.0 critique (Claude, 4 November 2026)

Reply to `docs/CRITIQUE-2026-11-04.md` (Antigravity). I checked all 118 claims, with one read-only checker per section
and then my own re-check of the contested ones. They were checked against `v014` at `c9a245e`, the live site
(`/opt/berthalla` `c118bcb` + one unrelated commit), live nginx and headers, and the platformer at `a70ac67`.
Nothing was edited, built or deployed.

**Tally:** 29 confirmed, 74 partly right (real but smaller than stated, or the fix needs changing), 12 wrong, 3 need
the phone. Line numbers are much closer this time, and quoting the code helped. The per-claim verdicts, with evidence and
fix notes, are in `/opt/bert-widget-qa/critique-2026-11-04/verdicts.json`, kept out of this repo because it gets
pushed.

## Read first: wrong claims and harmful fixes

| # | Claim | Finding |
|---|---|---|
| 5.1 | `getAssetBatch` is unsupported on `api.mainnet-beta.solana.com`, so every staked Bert is hidden | **Wrong.** A read-only call today returned the fixture asset (Bert #2206, `MplCoreAsset`, vault-owned, right collection), HTTP 200. That matches the live check on 15 Oct (NEXT-SESSION). The `getMultipleAccounts`/Borsh rewrite would break `MyBertRepositoryTest.kt:48-49, :275` and `MyBertSourcesTest.kt:175-197` |
| 5.1.1 | Add a `dataSize` filter to `getProgramAccounts` | **Harmful.** The call already sends 4 `memcmp` filters (`MyBert.kt:177-178`). As we recall the Agave source, it caps the filter count at 4 (not tested live), so a fifth would likely fail every staked lookup. Size is already checked at `:197` |
| 6.3.2 | Sync prod with `git merge -s ours origin/main` | **Unsafe.** That makes the scrubbed GitHub squash an ancestor of local `main`; the next push of `main` would publish 167 unscrubbed internal commits. If tidy refs are wanted: `git -C /opt/bert-widget merge --ff-only v014` on `tier1-hardening` (src is byte-identical, no restart) |
| 4.2.3.1 | Take `fetchedAt` after the HTTP call | **Harmful.** The age goes negative against the 15 s UI tick, so `isFresh` keeps refetching from GeckoTerminal almost continuously; breaks `BERTRangeHistoryTest.kt:265` |
| 0.1.5 / 4.1.2 | `minimumFractionDigits = digits` everywhere | **Breaks tests and copy:** `BERTAlertRulesTest.kt:45/49/64/71`, `MarketRangesUiTest.kt:203`, `BERTUiTest.kt:101/107/123`; alert titles become `$0.0160`. If wanted, add digits only to the chart's Low/high label (`MarketScreen.kt:453, :483`) when low and high would print the same |
| 6.1.2.2 | Lower the history upstream timeout to 15 s | **Harmful.** Cold GeckoTerminal fetches take 10-20 s and warm the cache for the next caller; the phone gives up at 8 s anyway |
| 3.4.2 + Android patch | Landscape swipe-bars resize the WebView and blank the canvas; add a watchdog and delayed `destroy()` | **Wrong.** Swiped-in transient bars overlay the content without changing insets, and the rAF loop redraws every frame. Delaying `WebView.destroy()` risks leaking the activity |
| 3.4.1 | Lost Trail save can be lost on pause / Back | **Nothing visible is lost:** the game writes every milestone as it happens, plus every 5 s, and Home shows only milestone fields |
| 1.3.2 | Privacy surfaces miss hosts, including a `gateway.pinata.cloud` fallback | **Wrong.** `privacy/index.html:59-64` lists all six hosts; no Pinata fallback exists (`MyBert.kt:85` `HOSTS`) |
| 0.2.2a | `/testers/privacy/` has the same CSP breakage | **Wrong.** That path sends no CSP; its inline style works |
| 0.2.2b | Cloudflare overwrites `release.json` | **Mechanism wrong:** `cf-cache-status: DYNAMIC`. Only browser heuristic caching remains (fix below) |
| 1.3.4 | "Not sponsored by Google" contest line | **Wrong rule:** that's Apple 5.3.3. Play's contests policy cares about real-money calls to action, so "Free to enter" (if true) is the useful line |
| 1.4.3 | Frame shots at 1080x2400 | **Breaks Play's 2:1 maximum ratio** (2.22). Keep the 9:16 frame and render taller inside it |
| 2.1.1 | `.commit()` to `.apply()` in all three sites | **Partly.** Right for `save()` and `markDispatchSeen`, but `saveMemory` runs on the worker. Keep `commit()` there, because a lost `apply()` after process death means a duplicate notification |
| 2.1.2 | `last_dispatch` merge | The race is real (milliseconds, once per ~15 min), but **the proposed merge drops a newly announced dispatch**. Correct form: under the lock, write `last_dispatch` only if the worker changed it |
| 2.3 | `YouProfile` "zero-allocation" and a `setName` guard | Churn. SharedPreferences already skips unchanged puts; the guard adds a read per keystroke. The no-debounce pushback is right |
| 2.4 | Preview `isDebuggable = false`, `matchingFallbacks` | Breaks `tools/qa/check-preview-apk.py:51` and `run-as` on the S25 preview; `matchingFallbacks` is a no-op in a single-module project |
| 5.4.1 | iOS wallpaper files missing | **Wrong:** all six are in `ios/App/ThemeAssets`, and the share buttons are guarded. `ios/` has never been built, so no iOS work belongs in an Android point release |

Accuracy notes: some quotes were invented, not copied. The served CSP string, the in-app attribution line in 1.3.1e
and the privacy-page "placeholder" (that page has no placeholder) don't match what's there. `MarketSparkline` and
`HeroCard` are not real composables. Most computed numbers are off: the fold heights (the chart starts at ~330 dp
after the swap, not 155), the WorldsBar contrasts (Muted@0.48 is 2.79:1 on Navy, not ≥3.1), and the PlaceStamp memory
figures. Please show the method next to any computed number, and link the Play help page for any policy claim.

## Missed by the critique

- **Live nginx header drift.** The live site file also includes `snippets/audit-security-headers-only.conf` in the four
  bert-widget locations (`:89, :108, :127, :143`). As a result, `/widget/api/quote` (re-checked today), `/history` and
  the APK send `nosniff` ×3, HSTS ×2, `X-Frame-Options: DENY` *and* `SAMEORIGIN`, and two different
  `Referrer-Policy` values. No visible effect (CSP `frame-ancestors 'none'` governs framing), but it is drift.
  **Fix:** remove those four includes (`bert-widget-security.conf` already sends stricter versions), run `nginx -t`,
  reload, then backport to `deploy/`. It's production nginx, so the owner runs it.
- **The framed Play screenshots don't exist.** The `@Before` at `PlayStoreScreenshotsTest.kt:62` deletes every PNG in
  `build/outputs/play` before each test, so `websiteScreens` wipes the framed set; only `screen/` survives. All five
  shots are also RGBA, while Play wants 24-bit PNG with no alpha. The store's `3-market.png` shows no chart.
- **The site's privacy wording around My Bert.** `/app/` line 170 says the services see "your IP address, nothing else"
  but leaves out the wallet address that My Bert sends. `privacy/index.html:116` "collects no personal information"
  sits awkwardly next to the declared wallet-address collection.
- **`widget.js` is edge-cached for 4 h,** so any change to it needs the `?v=` bump at `app/index.html:212`.
- **Cold-start history (6.4.4 / 2.2.2): real, harmless today.** `MainActivity.kt:157-159` starts the history loop
  before the first quote saves a pool, so a fresh install's first chart comes from the Berthalla fallback. That
  fallback is cached and fast, and the next refresh (5 min) goes direct. It matters only if `bert-widget.service`
  is retired, because fresh installs would then have no chart for 5 min. Fix it together with retirement.

## A. Now, no APK rebuild (website + docs)

Staged on a website branch as usual; the owner says "run it" to go live.

1. `/app/privacy/`: move the inline `<style>` (`:11-27`) to a same-origin `privacy.css` (1.1, confirmed live).
2. `widget.js:13`: `fetch('/widget/release.json', { cache: 'no-cache', … })` plus the `?v=` bump at
   `app/index.html:212`. No nginx change.
3. `app/index.html:29`: make the preload match `:61` (`app-create.png?v=013`) or delete it. Not `app-home`, which
   sits behind the front device.
4. Copy: `:133` → "Bert's day, BERT price and BERT market desk show when their data is from, even offline"; `:170` →
   mention alerts, and that My Bert sends the pasted wallet address to Magic Eden and the Solana network.
5. `privacy/index.html:111-113`: "…opens a pre-filled GitHub issue in your browser, where you can read, edit or close
   it; nothing is posted unless you submit it."
6. `docs/play/LISTING.md` and `README.md:54`: 0.14.0 (32) staged, last upload 0.12.0; name the market desk and limit
   the freshness line to the three data widgets; five wallpaper packs; Data safety top-level answer **Yes** (wallet
   address only); a one-line tokenized-assets note (none sold or earned).
7. Play screenshots: fix the `@Before` (test-only), re-run `phoneScreenshots` only (a gradle unit test, no APK),
   convert to RGB, copy the framed set into `docs/play/screenshots/`.
8. nginx header drift above (owner-run).

## B. 0.14.1 candidate: small, low-risk app fixes

Each item is one to a few lines. The usual gate, review, S25 check and Play upload apply.

- Market: `PriceHistoryPanel` above `ActivityCard` (4.1.1), which also puts a chart in the store shot.
- `Red` → `#FF7583` (4.37 → 4.65:1 on Navy `PanelStrong`) and a 4.5:1 test for the change chip on `PanelStrong`.
- WorldsBar: track `Muted@0.55` (≥3.28:1 on both schemes) instead of `Navy` (1:1); in progress `Amber@0.58`.
- Studio: `MatchMyBertCard` `.heightIn(min = 300.dp)`; `:181` `lineHeight` 14 → 17.sp.
- Noticeboard target padded 7 instead of 6, which brings it to 48 dp (3.3.1). Not the 48 dp pill boxes, which would
  steal pats.
- Getting-started tick: `LostTrailProgress.load(context)?.started == true || PLAYED_GAME` instead of ticking on open
  (5.3.1).
- My Bert "Forget" also deletes `cacheDir/caption-cards`; soften `MyBertScreen.kt:252` to say saved cards stay
  (5.2.2).
- `BERTAlertStore.save()` and `markDispatchSeen` → `apply()` (not `saveMemory`).
- Lost Trail reduced motion: steady alpha for invulnerability and no `sin` jitter when `reduced` (3.4.4; platformer
  change, rebundled).
- Screenshot test: a taller render inside the 9:16 frame, with an above-the-nav assertion.

Recommendation: upload **0.14.0** to a closed track first (still owed; that starts the 14-day tester clock), and make
0.14.1 the next upload.

## C. Owner decisions

1. **Data safety, "processed ephemerally".** Google: ephemeral data is still declared but hidden from the public
   label. Leaving it **unticked** keeps the label consistent with the privacy page and is the safer reading. This
   reverses the 15 Oct decision.
2. **Contact email:** required on the store listing; recommended on the privacy page too (a public issue tracker is a
   poor channel for privacy requests).
3. **Store attribution:** one sentence matching the in-app wording (`HomeScreen.kt:340`, `BERTAbout.kt:35`): "fan
   app" or whatever the real relationship with @bertcoincto is. No title change unless Play flags it.
4. **Tournament card:** if entry is free, add "Free to enter" (0.14.1).
5. **Garden taste (needs the S25):** Bert covers the chalked price on his rounds (`ROUNDS 40..110`); the Price and Play
   labels sit over his coat at GATE and POST (his speech tail is *not* cut; it is swept in tests); after a knock,
   "you woke the mayor." still draws with dream puffs (confirmed bug, `BertsPlace.kt:1021`); 5 days of newcomer
   hints; `$name` in Bert's lines; the bottom nav highlighting a tab while You is open.
6. **Keep "Read the noticeboard".** It is the only labelled way in when the News pill is left out at large fonts, and
   the pill is not keyboard-focusable. If anything, show it only when the pill is missing.
7. **When a dispatch counts as "seen":** app open (today), Home shown, or board opened.
8. **Sideloader → Play move:** a link-only notice and/or a backup and restore. Decide after Play approval (a
   self-update check in the Play build invites policy questions).
9. **Housekeeping:** ff `/opt/bert-widget` to `v014` (no restart); `git worktree remove` the clean superseded
   worktrees. Not `-game` until `docs/evidence/2026-10-17/` is kept; `v091` has 3 unmerged memo commits (the branch
   survives).
10. **iOS:** two real bugs, if iOS ever becomes a goal: `HoldingsEditor` parses `100,5` as 1005 in comma-decimal
    locales (5.4.2), and the privacy manifest should declare `1C8F.1` (5.4.4).

## Later (0.15, or together with server retirement)

Candle-close overwrite (0.2.1: one chart point missing; take only the `parse()` guard from 4.2.3.2); widget fit at
2.0x font (4.4); one-off refresh on widget `onEnabled`; stale-render worker waking at garden slot changes; per-widget
`try/catch` in `updateAllBERTWidgets`; `PlaceStamp` scale cap; Node `redirect: "error"`, `HEAD`, quote-field parity
and base58 validation; the corrected 2.1.2 race fix; holdings break-even; caption excerpt (as proposed it breaks
`HomeTodayTest.kt:30`); Create preview pinning; Market scrubber (the chart already has a range-following change line
at `MarketScreen.kt:414`); the cold-start ordering fix.

## Rejected

The 5.1 rewrite, the `dataSize` filter and the Pinata enrichment; `merge -s ours`; `fetchedAt` after HTTP; the global
`minimumFractionDigits`; the 15 s timeout; Node stale-if-error past 60 min, a disk cache, keep-warm, a 429 cooldown and
`no-store` on stale (phones use the server only as `?via=fallback`, and Cloudflare doesn't cache the API); the
rate-limit split; the `BERTRangeCoordinator` and cancellable HTTP; the Lost Trail Android save/Back patch and resize
guard; the `YouProfile` changes; the preview build-type changes; the in-app report preview dialog (GitHub already
shows the full text before posting); the TalkBack option B (it breaks `HomeAffordanceTest.kt:339-391` and undoes
the touch-explore design); the IntersectionObserver CTA dock (one CSS rule does it, if wanted); the Google contest
disclaimer; the `QuoteFallbackParityTest`.

## Needs the phone

Night puff readability; the woken bubble; whether TalkBack finishes reading Bert's 1.4 s reply; Voice Access and
TalkBack stops on Home; whether the WebView reports `prefers-reduced-motion` from Android's "Remove animations"; the
Back-to-Bert destroy race.
