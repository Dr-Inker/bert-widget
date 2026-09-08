# Next session — BERT app

Updated 2026-09-08. This repo had README/RESEARCH/DEPLOYMENT documentation but no campaign handoff ledger before this pass; keep continuity here and in the dated critique. Do not use the frozen session-graph archive.

## Latest implementation — research accepted

The owner accepted the research direction. The first implementation is committed as `086d8e5d0af2a40dd3ba40fcd965f0081706f297`, following research base `24a8cc30b880ff835e25e0a320f76fe9b21bc498`. Active loop authorized by the owner: improve the Android app toward premium quality. Current lease covers Android source, host UI verification, QA tools and repo ledgers. Base d30ecf5; no other agent leases. Start with host Compose captures, then fix measured usability defects and complete the native creation journey. No live deployment or external-service mutation occurred.

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

**Development gate passed at `086d8e5d0af2a40dd3ba40fcd965f0081706f297`:**

```bash
GRADLE_USER_HOME=/opt/bert-widget-qa/gradle \
ANDROID_USER_HOME=/opt/bert-widget-qa/android-home \
JAVA_TOOL_OPTIONS=-Duser.home=/opt/bert-widget-qa \
tools/check-android.sh
```

8 Node tests, 23 Android JVM tests, lintDebug (35 warnings, zero errors), assembleDebug. [Output](evidence/2026-09-08/development-gate-086d8e5.txt). This is a **development** gate, not the release-signature/publication gate in DEPLOYMENT.md.

**Native UI remains unverified.** The existing AVD was locked; a fresh disposable `bert-general-review` booted, but candidate installation timed out after 90 seconds. No native app checks ran and no candidate UI captures exist. [Attempt record](evidence/2026-09-08/native-attempt-086d8e5.json). The exact command and limitations are in the critique's general-app verification section. Do not claim verified caption export, state restoration, visual quality, large-text behavior or physical wallpaper/widget placement from the development gate. Both native attempts recorded a clean source tree and the same APK digest.

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
BASE: Application source 086d8e5d0af2a40dd3ba40fcd965f0081706f297, followed by verification documentation/evidence only; read the exact gate command above
READ: docs/NEXT-SESSION.md, docs/RESEARCH-2026-09-08.md, docs/CRITIQUE-2026-09-08.md, RESEARCH.md, android/README.md, DEPLOYMENT.md
WRITE (edit lease): No active lease after this session; scope the next change under the owner's authorization
DO_NOT_TOUCH: /root/.codex/session-graphs; live /var/www; signing credentials; unrelated repos
ACCEPT: Executable development gate at the actual source SHA plus native evidence for visual claims; no unverified release claim
SIDE_EFFECTS: No deployment, public push, external messages, or release publication
RETURN: Changes, command + tested SHA, capture paths, and explicit unverified work
