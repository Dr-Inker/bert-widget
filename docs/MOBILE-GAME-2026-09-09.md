# The Lost Trail inside BERT

Owner request: turn bert-platformer into a mobile game and add it to the BERT app,
using Figma MCP. Android is the currently published app; iOS remains a separate,
unshipped scaffold requiring macOS and signing access.

Base app: 1b8f6b27dca066074df941cfadd2b23b3059463a.
Base game source: 412c4f6c7591b71e3ca2d618230d2c3c96a898dd; ledger HEAD 0ce65a8.

Implementation contract:
- Native Explore card launches a private game Activity, retaining Explore on return.
- Bundle the complete HTML/art in the APK. Serve only the packaged game from a
  private HTTPS WebView origin; disable file/content access and all remote requests.
  No JavaScript-to-native object bridge or additional device permissions.
- Dark portrait/landscape layouts, large thumb targets, safe system insets, native
  exit, Android Back pauses first, backgrounding clears input and pauses audio.
- Save each run's lantern, collected items and opened gates locally; reopening
  resumes safely at the lantern. World records stay compatible with the web game.
- Figma mobile layouts in existing Bert file oTxUhbR4F1d3zDj8WlJOqh; use real game
  artwork/captures. No Code Connect declarations or local components/tokens exist
  in the current app/file; discover available library matches before authoring.

Acceptance: full platformer deploy gate on committed source; touch/layout/save/back
checks on the exact bundled HTML; full Android development gate, release lint/build
and pinned-signature artifact verification on committed app source. Native host
tests cover Activity policy/lifecycle/navigation, not physical WebView performance.
Do not repeat the failed software AVD without a new diagnostic hypothesis.

TO / TASK / CWD / BASE / READ / WRITE (edit lease) / DO_NOT_TOUCH / ACCEPT / SIDE_EFFECTS / RETURN

TO: Continuing root session
TASK: Implement, critique and verify the offline mobile game inside BERT
CWD: /opt/bert-widget (game source /opt/bert-platformer)
BASE: App 1b8f6b2; game 0ce65a8
READ: docs/NEXT-SESSION.md; android/README.md; DEPLOYMENT.md; this contract; game CLAUDE.md
WRITE (edit lease): Root only, authorized app/game source, tests, assets, design and ledgers
DO_NOT_TOUCH: Frozen session graphs; secrets; unrelated Music/home edits; iOS distribution
ACCEPT: Executable commands + actual SHAs, exact bundled bytes, capture paths and stated limits
SIDE_EFFECTS: Figma mobile design; local APK builds; established scoped release publication
RETURN: App/game outcome, install/design links, verification evidence and remaining limitations

## Outcome — published

App source `569f9ca232098a4f8419d590d65273fba364157a` is published as signed Android v0.6.0 / code 16
at https://berthalla.io/app/. Website source `6ef9a97822615d8720a96b5ba8d0f74be190d456` passed its full
`.claude/test-gate.sh` before and after publication; exact commands/environments and test counts
are in [release evidence](evidence/2026-09-09/release-0.6.0/README.md). That record includes the
full app/game gates, signed/public APK verification, real captures and Figma node provenance.

Critique round one rejected lost in-progress runs and website-style framing inside the app:
added validated lantern recovery, a private offline host and direct Explore launch. Round two
rejected a 40.4% portrait playfield, a ground-obscuring toast and wrapping Run label: changed
portrait framing to 53.9%, moved the toast above the path, and checked at least 48px targets
with real two-thumb input. No aesthetic score is claimed without device evidence.

Remaining acceptance is physical-phone validation (installation, OS process death, WebView
performance, native insets and accessibility), plus separately scoped iOS distribution.
The published Android feature and website deployment are complete; no approval is pending.
