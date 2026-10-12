# BERT v0.8.2 — Play readiness

From the [2026-10-12 research](../../../RESEARCH-2026-10-12.md). App source `c11c1f5` (branch `v080`), on top of
`4802ee2` (feature) and its independent-review fixes.

- APK 0.8.2 (21): 5,339,924 bytes, SHA-256 `221c7fc788541918b5f616bf1fddf3fc8ac28877ab9563cd5956656b4847b6d3`.
- Play bundle: 8,212,470 bytes, SHA-256 `53764bd408cda35a5e9761a14a9df2c1c764a6655e7190c4aacbf3cc31d9032f`.
- Both signed with the release key, certificate `f911463d…995c` (the Play upload key).
- Staged at `/opt/bert-widget-qa/release-0.8.2/staged/`, with `mapping-0.8.2.txt` and six 1080×1920 Play screenshots.
- Website: privacy page update on `/opt/bert-widget-site-release` branch `release-0.8.2` (`f5d99f7`). It covers server
  logs (14-day rotation, confirmed in `/etc/logrotate.d/nginx`), Cloudflare and opt-in crash reports. Not deployed.

## Changes since 0.8.1

- Widget taps open the right screen: price and market widgets go to Market, the Bert widget to Home. Notification
  taps do the same: price alerts to Market, updates and tournaments to Home. `MainActivity` is `singleTop` and
  ignores Recents replays.
- About sheet, reached from "About, privacy & feedback" in every screen's footer: privacy policy, feedback on GitHub,
  Telegram community, source code and open-source licences.
- Opt-in crash/freeze report from Android's `ApplicationExitInfo` (API 30+). The first run only sets a baseline. The
  report opens a pre-filled GitHub issue, which the person reviews before posting.
- "Get to know BERT" checklist on Home: widget, game, card and alert. It ticks itself from local state, can be hidden,
  and stops polling once hidden or done.
- The Bert widget shows "UPDATED 14:05", or gold "SAVED · …", wherever Bert's words keep two lines. It re-renders when
  the feed goes stale and at local midnight.
- Refresh worker: each step stands alone, so a failed quote no longer blocks feed alerts or the Bert widget. At most
  two retries, so the 15-minute cadence is never replaced by 5-hour backoff. One feed fetch per run. Cancellation
  propagates.
- Feed requests send `BERT-Android`, not the platform agent that names the phone model. Notification channels exist
  as soon as an alert is switched on.
- Dark launch window, and the Android 12+ splash on navy. The Lost Trail survives split-screen resizes
  (`screenLayout`). WorkManager 2.12.0.

## Gates

- `tools/check-mobile-game.sh` PASS at `4802ee2` and again at `c11c1f5`: 117 Android tests, lint 0 errors, debug
  build, bundled game byte-check `ecaa65d3…`. The new Bert-widget render check failed first at font 1.3 / 110dp
  (time line ellipsized), and the layout was fixed before release.
- Independent code review (fresh reviewer, opus): no high-severity issues. Medium: the periodic retry backoff, fixed.
  Low: five fixed, and one kept. A widget tap while The Lost Trail is open stacks a second Home; that predates this
  release, and clearing it would close the game.
- Release lint 0 errors. `check-apk.py` PASS (game provenance, production endpoints, 0.8.2 strings survive R8,
  `singleTop`, versionCode 21). The APK and AAB certificate is `f911463d…995c`.

## Physical device (Samsung S25 Ultra, Android 16), 12 October

- The owner's real app was upgraded in place from 0.8.1 (20) to 0.8.2 (21) with `adb install -r`, and data was kept.
  The placed Compact and Market widgets re-rendered straight away ("AS OF 12:35").
- Cold start: `LaunchState: COLD`, TotalTime 189 ms. The screen recording shows a navy first frame with no light
  flash (`/opt/bert-widget-qa/release-0.8.2/device/`, kept off the public repo because the owner's picture-in-picture video shows in the captures). The checklist shows "1 OF 4", with the widget step ticked from the real
  placement.
- Deep links: notification-shaped intents (`NEW_TASK|CLEAR_TOP`) reached the running instance by `onNewIntent`. MARKET
  opened Tools → Market, HOME opened Home, and a URL value was ignored. A real tap on the Compact widget from the
  launcher opened Market in the same task (`sz=1`).
- The About sheet opened from the footer: version 0.8.2 (21), links and the licence text.
- Empty crash buffer, process running. Errors in logcat were system noise: a JobService binding died at the upgrade
  kill, and Samsung's `libpenguin`.
- Not seen on device: the Bert widget's new time line, because the owner has no Bert widget placed. The host render
  tests cover it (`bertWidgetShowsWhenItsUpdateIsFrom`, every size at font 1.0 and 1.3).

## Publication

- Website `c5e50fe` (branch `release-0.8.2` in `/opt/bert-widget-site-release`, based on live `58ab3fe`). App page
  stamped 0.8.2 / 21 / 5.1 MB / `221c7fc7`, `release.json` updated, APK at `widget/download/bert-widget-0.8.2.apk` and
  the stable alias. Privacy page at `f5d99f7`. `verify:release … 20` PASS. `check-page.cjs staged` PASS: 1440, 768, 390
  and 320 wide, zero overflow, six download links.
- **The live fast-forward of `/opt/berthalla` to `release-0.8.2` was blocked by the auto-mode classifier
  [Production Deploy]. It awaits the owner.** Afterwards, run `check-public.py` and `check-page.cjs public`.
  Rollback: `/opt/bert-widget-qa/release-0.8.2/previous/` (0.8.1 page, privacy page, release.json, stable APK and
  `berthalla-head.txt` = `58ab3fe`).
- Play kit is on the drinkerlabs PC at `C:\Users\Sjpch\BERT-Play\`: `bert-0.8.2.aab` (hash verified on the PC),
  `mapping-0.8.2.txt`, `SHA256SUMS.txt`, 1080×1920 screenshots and `LISTING.md`. The 0.8.1 files are in
  `superseded-0.8.1\`.
