# Canonical app route — September 9, 2026

**Complete and live:** <https://berthalla.io/app/>. All five homepage app links point directly there; canonical and Open Graph metadata match. `/widget`, `/widget/` and `/widget/index.html` now return HTTP 301 to `/app/`, preserving query strings. `/app` normalizes to `/app/`. Shared assets, release metadata, APK/download aliases and installed-app quote APIs keep their existing URLs.

Website source: **`9d906ba3d5d27ce7f4340aa235d014c7e1f3496c`**. The owner explicitly authorized the Berthalla nginx vhost update and shared-service reload after the earlier automatic approval rejection. The action was then approved and completed at app checkout **`f82fb49da9e3a1e52f181a7ca60c5a47de8f6495`**, whose routing template is unchanged from `0f5f223ee121b26bbd0c9e24a63d3e3b9bf1432e`.

## Applied change and boundaries

The reviewed [nginx routing diff](nginx-routing.diff) was installed in `/etc/nginx/sites-available/berthalla.io`. The complete candidate and installed configuration validated, nginx reloaded successfully and service status was `active`. Installed vhost SHA-256: `5652e9a00131940d076b6c00d08ddddcb9957204b81c7dd7a78de11909459f1a`. Existing TLS-option warnings for other domains remained warnings; those vhosts were unchanged.

Publication used atomic copies and concurrent-change guards. The two already-published files were required to match their recorded new hashes; all remaining files and the vhost were required to match the recorded originals. Candidate configuration was checked before writes, and the installed configuration was checked before reload. The remaining website redirect fallback and migration documentation were also applied. [Approved deployment and file hashes](approved-deployment.json).

Only the pre-existing Music changes in homepage HTML/CSS remain uncommitted in the website repository; unrelated untracked files remain untouched. The exact deployed homepage is the cited website SHA plus [preserved-music.diff](preserved-music.diff). Backup/staged files remain under `/opt/bert-widget-qa/app-route-migration`. There is no pending approval or migration step. APK binaries, signing material, backend code and quote-service configuration were unchanged.

## Executable verification

These commands passed with website source `9d906ba3d5d27ce7f4340aa235d014c7e1f3496c` and app checkout `f82fb49da9e3a1e52f181a7ca60c5a47de8f6495`:

```bash
# /opt/bert-widget-qa/app-route-migration — candidate before installation
nginx -t -p /etc/nginx/ -c /opt/bert-widget-qa/app-route-migration/nginx-test.conf

# Installed configuration and approved reload
nginx -t
systemctl reload nginx
systemctl is-active nginx
# exit 0; active

# /opt/berthalla — full incumbent deploy gate
GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=safe.directory GIT_CONFIG_VALUE_0=/opt/berthalla PYTHONDONTWRITEBYTECODE=1 PYTEST_ADDOPTS='-p no:cacheprovider' .claude/test-gate.sh
# 577 passed, 33 skipped, exit 0, 2.78 s

# /opt/bert-widget-qa/app-route-migration — public endpoints through the CDN
python3 check-routes.py
APP_ROUTE_EVIDENCE=/opt/bert-widget-qa/app-route-migration/approved node check-page.cjs
```

The runners are copied into this directory. `check-page.cjs` uses installed Playwright at `/opt/facelift/node_modules/playwright` and defaults to the original QA output directory; `APP_ROUTE_EVIDENCE` selects another existing output directory. `check-routes.py` writes its result beside itself.

The full route check required **eight HTTP 301 cases**, including query preservation, and checked **15 requests** overall. `/app/` and homepage bytes/links matched deployed files; release metadata remained v0.5.0; both quote endpoints returned fresh data; both APK aliases returned HTTP 200 with the correct Android MIME, attachment disposition and 29,744,436-byte size. [Approved route results](approved-route-results.json). APK SHA-256 was verified in the preceding app-site-refresh evidence; this routing pass checked compatibility with HEAD requests.

The browser check covered `/app/` at **1440, 768, 390 and 320 pixels** after the nginx change: HTTP 200, correct URL/canonical/Open Graph metadata, all images loaded, zero page errors, **zero document overflow**, all six correct download links and the JavaScript-disabled download fallback. [Approved measurements](approved-page-results.json), [approved mobile capture](approved-app-390.png). This verifies the website; native-device acceptance remains outside this check.

## Earlier approval boundary

Before the owner authorized the host target and reload, only the new app page and homepage links were published. No host change or client-side redirect workaround occurred during that phase. `deployment-state.json`, `live-overlay.diff`, `static-route-results.json`, `page-results.json` and `app-390.png` preserve that earlier partial-deployment record and are superseded by the approved artifacts above. The original candidate check initially needed a scratch symlink to the existing nginx snippets directory to resolve relative includes; this affected the validation harness only.
