#!/usr/bin/env bash
# Build and inspect a separately installable local review artifact; does not install or publish.
set -euo pipefail
cd "$(dirname "$0")/.."
tools/check-android.sh
android/gradlew -p android :app:lintPreview :app:assemblePreview --console=plain
python3 tools/qa/check-preview-apk.py android/app/build/outputs/apk/preview/app-preview.apk
