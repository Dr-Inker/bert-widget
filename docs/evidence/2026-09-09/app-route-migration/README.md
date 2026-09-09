# Canonical app route — September 9, 2026

**Live:** <https://berthalla.io/app/>. All five homepage app links now point directly there. The page has matching canonical and Open Graph URLs. Its appearance, six v0.5.0 download controls, shared assets and installed-app APIs are unchanged.

**Pending approval:** production HTTP redirects from `/widget`, `/widget/` and `/widget/index.html`. Automatic approval review rejected modifying `/etc/nginx/sites-available/berthalla.io` and reloading nginx because the transcript did not explicitly authorize that host-level target and service side effect. The user has been asked for that specific authorization. The shared nginx service and its configuration remain unchanged. No client-side redirect workaround was published.

Website prepared source: `9d906ba3d5d27ce7f4340aa235d014c7e1f3496c`. Routing template/runbook source: `0f5f223ee121b26bbd0c9e24a63d3e3b9bf1432e` in this repo. Only the new `website/app/index.html` and homepage links were published. The old landing file and migration documentation in the website commit remain unapplied pending completion. [Deployment state](deployment-state.json) lists the exact boundary; [live overlay](live-overlay.diff) records it together with pre-existing Music edits. Do not mistake those pending changes for unrelated user deletions or claim the entire website commit is deployed.

## Validation

At website SHA `9d906ba3d5d27ce7f4340aa235d014c7e1f3496c`, with the recorded live overlay, these commands passed:

```bash
# /opt/berthalla
GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=safe.directory GIT_CONFIG_VALUE_0=/opt/berthalla \
PYTHONDONTWRITEBYTECODE=1 PYTEST_ADDOPTS='-p no:cacheprovider' .claude/test-gate.sh
# 577 passed, 33 skipped, exit 0, 2.73 s.

# /opt/bert-widget-qa/app-route-migration
node check-page.cjs
python3 check-routes.py --static-only
```

The executable runners are copied into this directory. `check-page.cjs` defaults to the original QA output directory; set `APP_ROUTE_EVIDENCE` to choose another. `check-routes.py` writes its result beside itself. Browser verification covered `/app/` at 1440, 768, 390 and 320 pixels: HTTP 200, correct URL/canonical/Open Graph, all images loaded, no page errors, zero document overflow, all six correct download links and a JavaScript-disabled download fallback. [Measurements](page-results.json), [390px capture](app-390.png).

The route check covered 15 requests: `/app` normalization including a query string, new page and homepage bytes/links, release metadata, both fresh quote APIs, both APK aliases' successful status/MIME/attachment/size. Legacy page responses were recorded, **not accepted as redirects** in `--static-only` mode. [Results](static-route-results.json). The unchanged APK was verified by hash in the preceding app-site-refresh evidence; this pass checked route compatibility with HEAD requests.

The complete candidate nginx configuration passed:

```bash
nginx -t -p /etc/nginx/ -c /opt/bert-widget-qa/app-route-migration/nginx-test.conf
```

This was a pre-commit syntax check of the exact candidate [routing diff](nginx-routing.diff), subsequently recorded in app source `0f5f223ee121b26bbd0c9e24a63d3e3b9bf1432e`. It is not a claim that the host configuration was applied. Existing TLS-option warnings for other domains were reported. The first candidate check failed because a relative snippets directory was resolved against the scratch config location; a scratch symlink to the existing `/etc/nginx/snippets` fixed only that test harness path.

## Resume after explicit approval

Review the exact `nginx-routing.diff`, current config hash in `deployment-state.json`, and staged files under `/opt/bert-widget-qa/app-route-migration`. The original `publish.py` expects all static files at their old hashes; two files have now been published, so update its guards to accept their verified `after` hashes before resuming. Do not skip concurrent-change guards. Apply the remaining prepared static/doc files and the reviewed nginx diff, run `nginx -t`, reload nginx and verify service status. Then run `python3 check-routes.py` **without** `--static-only` to require all eight redirect cases, followed by the necessary page/gate checks at the final SHAs. Record completion in the incumbent ledger. Do not apply or indirectly emulate the denied host action before explicit approval arrives.
