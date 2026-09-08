# Next session — BERT app

Updated 2026-09-08. This repo had README/RESEARCH/DEPLOYMENT documentation but no campaign handoff ledger before this pass; keep continuity here and in the dated critique. Do not use the frozen session-graph archive.

## Latest implementation — research accepted

The owner authorized a repeated premium-quality improvement loop. Latest verified source: `afb9003eb1ac5f6a9b97284492940b43e383404e`; changes include native creation and a private saved-card collection, measured large-text navigation, expandable updates and a consistent icon/color system. A separately installable BERT Preview APK and packaged-artifact gate now support practical device review. Current root lease covers Android source, host UI verification, QA tools and repo ledgers. No other agents are authorized. No deployment or external-service mutation occurred.

Iteration 6 is verified: large-text wallpaper/widget actions stack without splitting their labels at 320 dp; Mayor Purple widget negative-change contrast improves from 4.38:1 to 4.66:1. The strengthened checks failed on the baseline and pass on the fixed source. The audit covers 34 declared text/surface pairs, not every UI state or a physical display.

Iteration 7 is verified: ordinary over-limit captions remain editable with clear shortening guidance, Unicode character counting agrees with saved-card validation, and oversized pastes receive visible feedback without losing the draft. The notice fits y=370–498 dp inside a y=217–498 dp viewport at 320×640 dp / 2x text. An earlier attempt ended with exit 143 after tests; the final full gate passed at `e56060495166b4ce9df5933da489c5adc9715dc9`.

Iteration 8 is verified at the latest source: a full collection automatically reveals its 40-card limit and Manage collection action. Cancelling deletion keeps all cards; deleting one then returning to Art preserves the draft and palette and allows saving. Rejection preserves all 80 files byte-for-byte. At 320×640 dp / 2x text, message and action labels fit y=254.5–490 dp inside the y=217–498 dp viewport without another scroll.

Iteration 9 is verified at the latest source: collection and card detail have independent scroll positions. Both Back paths, collection saved-state recreation, and detail recreation/tab changes preserve measured positions exactly at normal and 2x text. The first candidate stopped at a test that clicked Delete before the asynchronous card load; the final gate waits for the loaded image and passes. All 24 prior captures are byte-identical to the preceding verified build.

## Owner direction

This is a **general BERT app**. Widgets are one feature, alongside market tools, personal holdings, themes, and future BERT experiences. The current work is Android source development. Published v0.4.0 and the unshipped iOS scaffold retain their existing status.

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

**Development and preview gates passed at `afb9003eb1ac5f6a9b97284492940b43e383404e`:**

```bash
GRADLE_USER_HOME=/opt/bert-widget-qa/gradle \
ANDROID_USER_HOME=/opt/bert-widget-qa/android-home \
JAVA_TOOL_OPTIONS=-Duser.home=/opt/bert-widget-qa \
tools/check-android-preview.sh
```

8 Node tests, 55 Android JVM tests (16 Compose UI + 3 caption-text + 1 contrast audit + 6 library/PNG/provider + 6 wallpaper-result tests), no failures. LintDebug and lintPreview: 35 warnings each, zero errors. AssembleDebug, assemblePreview and packaged APK inspection passed. [Output](evidence/2026-09-08/host-afb9003/preview-gate.txt), [results and capture hashes](evidence/2026-09-08/host-afb9003/results.json). The follow-up commit contains documentation/evidence only.

**Host UI evidence now exists.** Thirty-two actual Compose app-shell captures at 360×800 dp and 320×640 dp include offline Home, Explore, Create, collection, reopened card and normal/2x text. The full creation preview fits y=211–531 dp above navigation at 720 dp. Navigation labels fit one line at 2x text with >=48 dp targets; saved/shared PNGs preserve exact pixels. Draft switching, long-update expansion, offline save/open/keep/delete and full-collection recovery are exercised. Capacity/navigation screenshots use seeded cards. Both back paths and saved-state recreation preserve the same collection anchor; detail scroll state survives recreation and tab switching. The 24 captures predating the navigation fix are byte-identical to the preceding verified build. This is narrower than a device test.

**Device journeys remain unverified.** The API 30 software-AVD attempt installed the earlier `7a54a05` preview and rendered Home, but Android displayed a blocking “System UI isn’t responding” dialog. No app navigation journey passed. [Attempt results](evidence/2026-09-08/native-preview-7d5939a/results.json), [actual native capture](evidence/2026-09-08/native-preview-7d5939a/startup-observation.png), [observation/provenance](evidence/2026-09-08/native-preview-7d5939a/observation.json). Boot took 208.82 s and installation 106.22 s. The previous 90 s install allowance was insufficient, but increasing it did not produce a usable native test environment. Runner and emulator are terminal; do not treat old locks or logs as live processes. Do not infer real recipient delivery, OS process-death restoration, TalkBack, API 36 system bars, launcher/wallpaper success or physical performance from these results. No release-signature/publication gate was run.

`tools/qa/android-companion.py` exercises a disposable AVD with labelled quote fixtures and captures native screenshots/UI trees. Read its arguments; never point this workflow at a personal/live device. Fixture market values are not real quotes. This host lacks `/dev/kvm`; software emulation is slow. Run one heavy job at a time.

`tools/qa/android-preview-smoke.py` exercises the isolated preview in a read-only AVD overlay. It records runner SHA separately from APK source/hash, uses a 300 s install allowance and disables Wi-Fi/mobile data before first launch. Its follow-up diagnostics preserve the last UI tree, capture a failure screen/logcat, keep tool temp files under the chosen output path and disable the host `/var/crash` exception hook. It now defaults to automatic acceleration; `--accel off` is an explicit software-emulation diagnostic, not the default for a functioning test host. Those diagnostic changes have not been exercised in another native run. Do not repeat an identical failing emulator attempt; the next native pass needs a functioning environment or a specific new diagnostic hypothesis.

On this host, build caches and Android preferences for this pass are under `/opt/bert-widget-qa`, keeping tool writes out of `/root`. No production signing material is copied into that directory.

## Review artifacts

- [Interactive visual review](evidence/2026-09-08/review.html): current screens, before/after creation, exact source and measurement links. Host renders, not phone screenshots.
- Stable local APK: `android/app/build/outputs/review/BERT-preview-afb9003.apk` (ignored build artifact). SHA-256 `e4788d9c381ecd3a0671f91a20908767e55e02743e9725c419dbb57f4a7b1c6d`.
- Package `global.bert.widget.preview`, Android debug signature, labels BERT Preview, independent app/provider identity. It uses public HTTPS endpoints; emulator fixtures remain debug-only. Installation of this updated APK is unverified. Only the earlier `7a54a05` preview installed on the API 30 test AVD. Coexistence with the published app and real-device journeys remain unverified.
- [Device review contract](DEVICE-REVIEW.md). The owner was asked asynchronously for the primary phone model/Android version; no answer was available when this handoff was written. The supported range remains API 26–36; do not invent a tested device.

## Next implementation priorities

The owner accepted the research and the first implementation now uses Home / Explore / Create / Tools. Start validation from the report's user tasks and current native QA script.

1. Continue the premium-quality loop from the critique and host captures. Iteration 6 fixes measured compact-action and widget-contrast defects. Iteration 5 installed/rendered an earlier APK but hit a System UI ANR. A functioning device/AVD is needed for the native contract. Iteration 7 completes the focused caption-input review with an actionable limit, Unicode counting, bounded storage and measured error visibility. Iteration 8 verifies the 40-card boundary and fixes the missing visible recovery. Iteration 9 fixes measured position loss on return and verifies restoration. The next bounded audit should cover populated Market/Holdings screens: the current app-shell test helper always supplies an unavailable quote, empty history and an empty position, so their populated visual/interaction states are not yet exercised. Use clearly labelled deterministic quote/history/position fixtures, measure compact large-text readability, range selection and stale-data feedback, and fix only reproduced defects. Do not present fixture prices as current market data. Device acceptance remains the main unresolved requirement. Do not turn host checks into a native pass or add features just to keep the loop moving.
2. Confirm long-term source ownership for `https://berthalla.io/status.json`. The adapter handles its current shape but it is not a chronological feed or documented native API. Do not scrape stale homepage fallback cards.
3. Verify external phone journeys for Flappy Bert, web drawing, music and Woofhub. Official Playground labels Farmhalla and Hold The Line coming soon; they are excluded from the playable catalog. Custom Tabs are a possible follow-up; persistent saved creations are implemented in iteration 2.
4. Add favorites only after confirming repeated discovery use. Explore a non-price widget after the corresponding app content works.
5. Validate widget placement and Home/Lock wallpaper application on physical launchers; confirm the now-explicit partial-apply outcomes. Bring iOS to parity only as an explicit separate macOS/Xcode effort.

Research evidence: `docs/evidence/2026-09-08/ecosystem-research.json`. Authenticated flows, web generation, gameplay and mobile behavior remain unverified. Research results are separate from the current implementation checks.

## Handoff contract

TO / TASK / CWD / BASE / READ / WRITE (edit lease) / DO_NOT_TOUCH / ACCEPT / SIDE_EFFECTS / RETURN

TO: Next BERT app session
TASK: Continue the general BERT app direction, starting from the research, critique and verified state
CWD: /opt/bert-widget
BASE: Fully gated source afb9003eb1ac5f6a9b97284492940b43e383404e; follow-up changes are evidence/documentation only. Native attempt runner 7d5939a072f409c5c756bcdaca43dfb6eb97dec5 failed at a blocking System UI ANR; no app journey gate passed.
READ: docs/DEVICE-REVIEW.md, docs/NEXT-SESSION.md, docs/RESEARCH-2026-09-08.md, docs/CRITIQUE-2026-09-08.md, RESEARCH.md, android/README.md, DEPLOYMENT.md
WRITE (edit lease): Root Android source, QA and repo ledgers under the owner's ongoing loop authorization; no other agents
DO_NOT_TOUCH: /root/.codex/session-graphs; live /var/www; signing credentials; unrelated repos
ACCEPT: Executable development gate at the actual source SHA plus native evidence for visual claims; no unverified release claim
SIDE_EFFECTS: No deployment, public push, external messages, or release publication
RETURN: Changes, command + tested SHA, capture paths, and explicit unverified work
