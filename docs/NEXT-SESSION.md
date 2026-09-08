# Next session — BERT app

Updated 2026-09-08. This repo had README/RESEARCH/DEPLOYMENT documentation but no campaign handoff ledger before this pass; keep continuity here and in the dated critique. Do not use the frozen session-graph archive.

## Owner direction

This is a **general BERT app**. Widgets are one feature, alongside market tools, personal holdings, themes, and future BERT experiences. The current work is Android source development. Published v0.4.0 and the unshipped iOS scaffold retain their existing status.

## Read first

- `docs/CRITIQUE-2026-09-08.md`: baseline defects, fixes, product gaps and evidence.
- `RESEARCH.md`: incumbent architecture and the September product-direction decision.
- `android/README.md`: source behavior versus published release.
- `DEPLOYMENT.md`: production/release requirements; no deployment is authorized by this handoff.

## Current implementation

- Home / Market / Holdings / Studio navigation, persistent screen state, Android Back to Home.
- BERT home and explicit browser links to existing ecosystem destinations.
- Real observed-price chart ranges; honest collection and gap states.
- Offline-accessible holdings editing, optional total cost and estimated gain/loss.
- Age-aware quote freshness, foreground/resume refresh and cancellation handling.
- Theme previews separated from applying widget palettes; existing widgets/artwork retained.

## Verification

Application source is `30007889bd9948142090b8afdf692a9e4ab5626e`. The development gate passed at that SHA; the exact command and log are recorded in the critique. Native UI verification did **not** pass: the software emulator timed out in package removal before installing the candidate. No candidate UI capture or physical-device verification is available. The next step before release is native QA on a functioning emulator or device. `tools/check-android.sh` runs Node checks, Android JVM tests, lintDebug and assembleDebug. It is a **development** gate, not the release-signature/publication gate in DEPLOYMENT.md.

`tools/qa/android-companion.py` exercises a disposable AVD with labelled quote fixtures and captures native screenshots/UI trees. Read its arguments; never point this workflow at a personal/live device. Fixture market values are not real quotes. This host lacks `/dev/kvm`; software emulation is slow. Run one heavy job at a time.

On this host, build caches and Android preferences for this pass are under `/opt/bert-widget-qa`, keeping tool writes out of `/root`. No production signing material is copied into that directory.

## Next implementation priorities

1. Decide and curate the actual BERT games/experiences for Explore; verify canonical destinations and mobile behavior.
2. Identify the owner/source for a real updates feed before adding feed UI.
3. Add favorites/recent activity once discovery has enough content to warrant it.
4. Validate widget placement and Home/Lock wallpaper application on physical launchers; improve the inherited partial-apply failure path.
5. Bring iOS to parity only as an explicit separate macOS/Xcode effort.

## Handoff contract

TO / TASK / CWD / BASE / READ / WRITE (edit lease) / DO_NOT_TOUCH / ACCEPT / SIDE_EFFECTS / RETURN

TO: Next BERT app session
TASK: Continue the general BERT app direction, starting from the critique and verified state
CWD: /opt/bert-widget
BASE: Application source 30007889bd9948142090b8afdf692a9e4ab5626e, followed by documentation/evidence only; read the gate command in the critique
READ: docs/NEXT-SESSION.md, docs/CRITIQUE-2026-09-08.md, RESEARCH.md, android/README.md, DEPLOYMENT.md
WRITE (edit lease): No active lease after this session; scope the next change under the owner's authorization
DO_NOT_TOUCH: /root/.codex/session-graphs; live /var/www; signing credentials; unrelated repos
ACCEPT: Executable development gate at the actual source SHA plus native evidence for visual claims; no unverified release claim
SIDE_EFFECTS: No deployment, public push, external messages, or release publication
RETURN: Changes, command + tested SHA, capture paths, and explicit unverified work
