#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
git diff --exit-code HEAD -- android tools
tools/check-android.sh
node tools/qa/lost-trail-browser.mjs
