# BERT app website refresh — 2026-09-09

Published website source: **`86a370e493fd8da1be700c702e7873524793780b`** in `/opt/berthalla`.
The owner requested that berthalla.io and the widget page reflect the general BERT app after authorizing v0.5.0 deployment. Homepage navigation/chapter/CTA and `/widget/` now introduce Home, Explore, Create and Tools. Actual app previews lead, with widgets and themes retained as features. Both pages have a new 1200×630 app social image, updated installation/privacy wording and the same signed download.

## Executable verification

The following commands passed at website SHA `86a370e493fd8da1be700c702e7873524793780b`:

```bash
# CWD /opt/berthalla — full incumbent deploy gate
GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=safe.directory GIT_CONFIG_VALUE_0=/opt/berthalla \
PYTHONDONTWRITEBYTECODE=1 PYTEST_ADDOPTS='-p no:cacheprovider' .claude/test-gate.sh
# 577 passed, 33 skipped, exit 0 (2.96 s). Database tests intentionally skipped.

# CWD /opt/bert-widget — actual public pages through the CDN
APP_SITE_EVIDENCE=/opt/bert-widget/docs/evidence/2026-09-09/app-site-refresh \
node docs/evidence/2026-09-09/app-site-refresh/check-site.cjs public

python3 docs/evidence/2026-09-09/app-site-refresh/check-public-assets.py
```

The browser runner uses the installed Playwright at `/opt/facelift/node_modules/playwright`, one Chromium process at a time. Both pages were checked at **1440, 768, 390 and 320 pixels**, with **zero horizontal document overflow** at all eight combinations. Checks cover loaded images, anchor targets, six correct app download links, release checksum, source navigation/footer, four app sections, three themes, both widget sizes, CTA bounds and minimum 44-pixel height, FAQ expansion and a JavaScript-disabled download fallback. The homepage retains its video/sound elements, app links and Music chapter, with the mobile hero within its 700–790-pixel specification. See [browser measurements](public-site.json).

Nine fetched public assets (two HTML pages, two stylesheets, four native previews and the social PNG) match the deployed SHA-256 values byte-for-byte. The v0.5.0 APK also matches SHA-256 `c47fbee48bc37db5d0974fa453825f34288d9d9983a603033edafcc5040df4e0`, size 29,744,436 bytes, APK MIME and attachment disposition. See [public hashes and release](public-assets.json) and [publication manifest](publish-manifest.json). The first Python fetch was rejected for its default user agent; the successful run uses a browser user agent and normalizes header names case-insensitively. No origin/CDN settings were changed.

The initial draft browser run identified one pixel of app preview overflow at 390px and the pre-existing Arcade card's 11-pixel overflow at 320px. Final captures/measurements include the sizing fixes. Reduced-motion capture avoids the incumbent looping animation's unstable screenshot bounds; homepage animation and sound source remain unchanged.

## Captures

- App hero: [desktop](public-app-1440-feature.png), [mobile](public-app-390-feature.png).
- Four native sections: [desktop](public-inside-1440.png), [mobile](public-inside-390.png).
- Homepage app chapter: [desktop](public-home-1440-feature.png), [mobile](public-home-390-feature.png).
- Full pages at all four widths are `public-{home,app}-{1440,768,390,320}.png`.

The original app images are unchanged host-rendered Compose evidence from `f62c961c39c011bbecec02dc4d9b2232e5ba83d4`: `home-activity.png`, `explore.png`, `create.png`, `market-populated.png`. App UI is identical in published v0.5.0 source `938d9d30a85025bf10caad01bcb3f4cbf72f23ae`. The site labels example content/data. Browser screenshots verify the marketing pages; neither they nor host Compose previews establish physical-device acceptance.

The social card is rendered from [HTML/CSS](social.html) and unchanged app images with [render-social.cjs](render-social.cjs); the default output is a scratch PNG, never the live file. Existing Figma frames were consulted as historical references. No new Figma frame was published; code and these captures record the refresh.

## Preserved work and deployment boundaries

The website had pre-existing uncommitted Music changes in homepage HTML/CSS and an untracked Music directory. They remain live and uncommitted; this pass did not absorb them into its code commit. Therefore the exact deployed homepage consists of the cited SHA **plus [preserved-music.diff](preserved-music.diff)**. Both the patch and deployed hashes are recorded to make the check reproducible. The Music section was compared byte-for-byte with the pre-task copy; the hero was compared byte-for-byte apart from its app link label. Prior CSS remains an unchanged prefix of the deployed stylesheet. Other untracked website files and historical APKs remain untouched.

Nginx serves `/opt/berthalla/website` directly. Publication used staged, reviewed files with before/after hash guards and atomic replacement, images first and HTML last. Rollback copies are under `/opt/bert-widget-qa/app-site-refresh/previous`; the publication manifest lists every changed public file. No APK, package name, release manifest, backend, nginx, service, signing material, GitHub push or GitHub release changed. `/widget/` and download filenames remain stable for compatibility. Native device acceptance remains outstanding as recorded in the incumbent campaign ledger.
