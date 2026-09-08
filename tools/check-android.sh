#!/usr/bin/env bash
# Local development gate. Release signature/publication verification is a separate runbook.
set -euo pipefail
cd "$(dirname "$0")/.."
printf 'Checking source SHA: '
git rev-parse HEAD
npm run check
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain "$@"
