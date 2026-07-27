# iOS app and widget

This folder contains an unshipped native SwiftUI companion app and WidgetKit extension codebase for iOS 17+. Shared quote models, caching, network access, official token artwork, local holdings storage, privacy metadata, and small/medium widget views exist, but there is no downloadable iOS build yet. It must be generated, signed, and tested on macOS before TestFlight or App Store distribution; the currently published product is Android-only.

## Generate the Xcode project

On macOS with Xcode and [XcodeGen](https://github.com/yonaskolb/XcodeGen):

```bash
cd ios
xcodegen generate
open BERTWidget.xcodeproj
```

Before signing or shipping:

1. Change the bundle IDs and App Group from the placeholder `global.bert` values.
2. Select an Apple development team for both targets.
3. Create the matching App Group in the Apple Developer portal.
4. Confirm the Release `BERT_QUOTE_URL` is `https://berthalla.io/widget/api/quote`.
5. Confirm the official BERT token artwork is present in both targets' generated asset catalogs.

Debug builds use `http://127.0.0.1:8787/v1/bert/quote` for local simulator development. A physical device cannot use that address to reach a development computer. Release builds use `https://berthalla.io/widget/api/quote` directly.

The app and widget share the last valid quote and optional holdings amount through `group.global.bert.widget`. Holdings never leave the shared App Group container; the medium widget calculates the USD value locally from the public quote.

## Distribution status

No IPA, TestFlight build, or App Store listing has been produced. Apple Developer team access, final bundle identifiers, App Group registration, signing profiles, and a macOS/Xcode validation pass are still required.
