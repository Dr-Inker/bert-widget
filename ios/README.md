# iOS app and widget

This folder contains an unshipped native SwiftUI companion app and WidgetKit extension scaffold for iOS 17+. It must be generated, signed, and tested on macOS before release; the currently published product is Android-only.

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
4. Set `BERT_API_BASE_URL` to the deployed HTTPS quote service.
5. Add approved BERT artwork to the asset catalog; the current paw icon is a deliberate placeholder.

The default API address is `http://127.0.0.1:8787` for local simulator development. A physical device cannot use that address to reach a development computer.
