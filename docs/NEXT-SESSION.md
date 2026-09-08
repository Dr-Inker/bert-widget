# Next session — BERT app

Updated 2026-09-08. This repo had README/RESEARCH/DEPLOYMENT documentation but no campaign handoff ledger before this pass; keep continuity here and in the dated critique. Do not use the frozen session-graph archive.

## Active implementation — research accepted

The owner accepted the research direction. Current edit lease: Android navigation and content adapter, Home / Explore / Create / Tools, native creation/personalization entry points, focused regression checks, and these repo ledgers. Base: `24a8cc30b880ff835e25e0a320f76fe9b21bc498`. No live deployment or external-service mutation. The source and verification sections below describe the prior iteration until this pass is committed and checked.

## Owner direction

This is a **general BERT app**. Widgets are one feature, alongside market tools, personal holdings, themes, and future BERT experiences. The current work is Android source development. Published v0.4.0 and the unshipped iOS scaffold retain their existing status.

## Read first

- `docs/RESEARCH-2026-09-08.md`: latest ecosystem findings, proposed navigation, feature priorities and validation plan. Recommendations are not implementation commitments.
- `docs/CRITIQUE-2026-09-08.md`: baseline defects, fixes, product gaps and evidence.
- `RESEARCH.md`: incumbent architecture and the September product-direction decision.
- `android/README.md`: source behavior versus published release.
- `DEPLOYMENT.md`: production/release requirements; no deployment is authorized by this handoff.

## Current implementation

- Home / Explore / Create / Tools navigation, saved state per section, Android Back to Home.
- BERT home with cached dispatch/mood/event data and independent quote/activity refresh.
- Direct curated ecosystem entry points with explicit browser/Telegram/X labels.
- Create / Art includes web drawing and local caption cards; Personalize contains existing widget/theme setup.
- Real observed-price chart ranges; honest collection and gap states.
- Offline-accessible holdings editing, optional total cost and estimated gain/loss.
- Age-aware quote freshness, foreground/resume refresh and cancellation handling.
- Theme previews separated from applying widget palettes; existing widgets/artwork retained.

## Verification

The current general-app iteration is awaiting its committed-source development gate. Previous source `30007889bd9948142090b8afdf692a9e4ab5626e` has a development result recorded with command/log in the critique; that result does not verify these changes. Prior native UI verification did **not** pass: the software emulator timed out in package removal before installing that candidate. No candidate UI capture or physical-device verification is available yet. `tools/check-android.sh` runs Node checks, Android JVM tests, lintDebug and assembleDebug. It is a **development** gate, not the release-signature/publication gate in DEPLOYMENT.md.

`tools/qa/android-companion.py` exercises a disposable AVD with labelled quote fixtures and captures native screenshots/UI trees. Read its arguments; never point this workflow at a personal/live device. Fixture market values are not real quotes. This host lacks `/dev/kvm`; software emulation is slow. Run one heavy job at a time.

On this host, build caches and Android preferences for this pass are under `/opt/bert-widget-qa`, keeping tool writes out of `/root`. No production signing material is copied into that directory.

## Next implementation priorities

The owner accepted the research and the first implementation now uses Home / Explore / Create / Tools. Start validation from the report's user tasks and current native QA script.

1. Validate the current implementation on a functioning native device/AVD, especially state restoration, caption preview/export and unavailable activity. No visual score without captures.
2. Confirm long-term source ownership for `https://berthalla.io/status.json`. The adapter handles its current shape but it is not a chronological feed or documented native API. Do not scrape stale homepage fallback cards.
3. Verify external phone journeys for Flappy Bert, web drawing, music and Woofhub. Official Playground labels Farmhalla and Hold The Line coming soon; they are excluded from the playable catalog. Custom Tabs and persistent saved creations are possible follow-ups.
4. Add favorites only after confirming repeated discovery use. Explore a non-price widget after the corresponding app content works.
5. Validate widget placement and Home/Lock wallpaper application on physical launchers; improve the inherited partial-apply failure path. Bring iOS to parity only as an explicit separate macOS/Xcode effort.

Research evidence: `docs/evidence/2026-09-08/ecosystem-research.json`. Authenticated flows, web generation, gameplay and mobile behavior remain unverified. Research results are separate from the current implementation checks.

## Handoff contract

TO / TASK / CWD / BASE / READ / WRITE (edit lease) / DO_NOT_TOUCH / ACCEPT / SIDE_EFFECTS / RETURN

TO: Next BERT app session
TASK: Continue the general BERT app direction, starting from the research, critique and verified state
CWD: /opt/bert-widget
BASE: General-app implementation follows 24a8cc30b880ff835e25e0a320f76fe9b21bc498; final source SHA and gate result pending this pass
READ: docs/NEXT-SESSION.md, docs/RESEARCH-2026-09-08.md, docs/CRITIQUE-2026-09-08.md, RESEARCH.md, android/README.md, DEPLOYMENT.md
WRITE (edit lease): No active lease after this session; scope the next change under the owner's authorization
DO_NOT_TOUCH: /root/.codex/session-graphs; live /var/www; signing credentials; unrelated repos
ACCEPT: Executable development gate at the actual source SHA plus native evidence for visual claims; no unverified release claim
SIDE_EFFECTS: No deployment, public push, external messages, or release publication
RETURN: Changes, command + tested SHA, capture paths, and explicit unverified work
