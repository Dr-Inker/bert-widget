# BERT preview device review

The preview is a local review artifact, not the published release. Build from a clean committed source using `tools/check-android-preview.sh`; record the exact command, source SHA and APK SHA-256. Use `android/app/build/outputs/apk/preview/preview-inspection.json` to confirm package `global.bert.widget.preview`. The app/widget picker labels say BERT Preview. Install it on a disposable review device or device the owner has authorized for testing. This document does not authorize deployment or actions on an unrelated device.

Host evidence covers layout, saved state, private storage, PNG/provider access and operation contracts. All device rows below start **UNVERIFIED**. A passing build cannot change their status.

Latest native attempt: the API 30 software AVD installed the preview, then a System UI ANR blocked the journeys. [Result and exact provenance](evidence/2026-09-08/native-preview-7d5939a/observation.json). Installation success is recorded separately from the unverified journey rows below.

| Journey | Observable acceptance and evidence |
| --- | --- |
| Cold start and public data | Record model, Android version, display/font settings and network. Capture first useful frame and elapsed time; Home offers Explore/Create without a quote. Activity and quote update independently. Record source timestamps, without treating them as dispatch publication dates. |
| Offline use | Launch offline with and without a prior cache. Cached content names its age, unavailable content has retry, Explore and creation remain reachable. A past tournament never appears open. Record captures and exact actions. |
| Keyboard and large text | At default and maximum supported font scale, edit a 96-character caption and holdings. Focused field and save/share actions can be reached above the keyboard. Create/Tools sections and all four destinations stay reachable; no essential label clips. Capture screen bounds and font settings. |
| Saved artwork | Offline, create a distinctive caption/palette, save, leave/relaunch and reopen. Compare exported PNG dimensions (1080×1080) and pixel hash with the saved PNG. Cancel deletion and retain it; confirm deletion and remove only that card. |
| State restoration | Leave an unsaved caption, selected palette and section, background the app, trigger process reclamation on the review device, and return through Recents. Record commands and before/after captures. Distinguish saved-state restoration from an intentional force-stop or data wipe. |
| Actual sharing | Choose an authorized local recipient or save-to-files destination through Android's chooser. Verify the receiving app opens the PNG and retains the complete caption. Opening the chooser alone does not pass. Do not send anyone a message without authorization. |
| Widget placement | Pin each preview widget; verify resize, readable current/stale states, saved holdings and tap-to-open. Record launcher/version, widget dimensions and captures. Preview labels distinguish them from published widgets. |
| Wallpaper outcomes | On an authorized review device, exercise Home only, Lock only and both with all three packs. Capture resulting surfaces/crops and persistent app result. If a surface is restricted, the result names the partial success accurately. A host injected failure does not prove launcher behavior. |
| System UI and accessibility | On API 36, verify status/navigation/gesture insets, Back behavior and edge-to-edge interaction. With TalkBack, verify reading order, selected tabs, field errors, saved-card names, confirmation dialogs and wallpaper result announcements. Record screen-reader/version and observations. |
| Performance and reliability | Record cold/warm startup and scrolling frame data on named hardware; repeat a create/save/reopen/share journey while collecting crashes/ANRs. State the tool, run count and measurements. Avoid claiming “smooth” from a screenshot. |

Save machine-readable results, capture paths and logs under the dated repo evidence directory. Update `docs/NEXT-SESSION.md` and the critique with each executable command + tested SHA. Mark unresolved rows UNVERIFIED and report defects with a reproducible action sequence. No S-tier acceptance while core device journeys remain unverified.
