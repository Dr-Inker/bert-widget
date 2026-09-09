# Deployment and release runbook

## Production topology

Rollout note (2026-09-09): `/app/` and homepage links are live. The exact legacy landing redirects below are prepared in the routing template but await explicit approval to modify the Berthalla nginx vhost and reload the shared service. Read `docs/NEXT-SESSION.md` before applying them.

`bert-widget.service` runs the dependency-free Node quote service as `www-data` on `127.0.0.1:8787`. nginx exposes the landing page and proxies the public quote routes:

- `/app/` — canonical BERT Android app landing page
- `/widget`, `/widget/`, `/widget/index.html` — permanent redirects to `/app/`
- `/widget/api/quote` — canonical production endpoint
- `/widget/v1/bert/quote` — compatibility quote endpoint
- `/widget/download/bert-widget.apk` — signed Android release

The landing HTML lives at `website/app/index.html` in the separate Berthalla website repository. Shared assets, release metadata and APK downloads remain under `website/widget/`; installed-app API and download URLs do not change. The page links back to the public source repository and release history; keep those transparency links current whenever the repository or release process changes.

### Landing route changes

Stage and review the landing HTML, homepage links, canonical/Open Graph metadata and the scoped routing diff before deployment. Apply the `/app/` location and exact legacy landing redirects from `deploy/nginx-widget.conf` to `/etc/nginx/sites-available/berthalla.io`. Preserve query strings with `$is_args$args`; never redirect the entire `/widget/` subtree because it contains APKs, release metadata and API routes. Keep a backup, validate the complete candidate configuration with `nginx -t`, publish the staged static files, validate the installed configuration and reload nginx. Restore the backup if validation fails.

Run the Berthalla repo's `.claude/test-gate.sh` at the deployed website SHA. Verify public HTTP 301 redirects for `/widget`, `/widget/`, `/widget/index.html` and `/app`, HTTP 200 at `/app/`, canonical metadata, all page resources and download controls, and the existing quote/release/APK paths. The old physical `website/widget/index.html` is only a static-preview redirect fallback; update release links in `website/app/index.html`.

## Service deployment

1. Install Node.js 22 or newer.
2. Copy `deploy/bert-widget.service` to `/etc/systemd/system/`.
3. Merge `deploy/nginx-widget.conf` into the Berthalla TLS server block.
4. Validate nginx and enable the service.

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now bert-widget.service
sudo nginx -t
sudo systemctl reload nginx
```

## Health checks

```bash
systemctl is-active bert-widget.service
curl -fsS http://127.0.0.1:8787/healthz
curl -fsS https://berthalla.io/widget/api/quote
curl -fsS https://berthalla.io/widget/v1/bert/quote
```

Treat the public checks as required, not optional. The Android client preserves its last valid quote when a
refresh fails, so a public routing failure can look like a frozen price even while the service and upstream
provider are healthy. Confirm that the public response is HTTP 200, `meta.freshness` is `fresh`, and
`source.observedAt` continues to advance.

### Cloudflare DNS guardrail and recovery

The production apex has exactly one proxied `A` record:

```text
berthalla.io  A  89.167.123.104  proxied
```

Do not set the record content to an address returned by a public lookup of the proxied hostname. Addresses
in Cloudflare ranges such as `104.16.0.0/13`, `172.64.0.0/13` or `2606:4700::/32` are edge addresses, not
the origin. Using them as the record content causes Cloudflare Error 1000 (`DNS points to prohibited IP`)
and blocks the quote API before requests reach nginx.

When the widget price appears frozen:

1. Request the public API with an Android-style client and inspect the status and body.
2. Compare it with `http://127.0.0.1:8787/v1/bert/quote` and the DEX Screener token-pairs endpoint.
3. If the origin is fresh but Cloudflare returns Error 1000, inspect the unflattened record content in the
   Cloudflare dashboard or API; ordinary `dig` output only shows proxied edge addresses.
4. Restore the single apex `A` record above and remove conflicting apex `A` or `AAAA` records.
5. Verify the public endpoint returns a fresh quote through Cloudflare before closing the incident.

The 2026-07-30 freeze was caused by four proxied apex records whose content was set to Cloudflare's own edge
addresses. The Node service, nginx, origin TLS certificate and DEX Screener feed remained healthy. Replacing
those records with the verified origin immediately restored Android requests from HTTP 403 to HTTP 200.

After using a temporary Cloudflare API token for recovery, remove it from the shell and revoke it. Tokens
should be scoped to `Zone / DNS / Edit` for this zone and restricted to the operator's expected source IPs.

## Android release signing

Production releases must use the same signing identity so Android accepts upgrades. The server currently stores the key and password outside this repository:

```text
/etc/bert-widget/bert-widget-release.jks
/etc/bert-widget/keystore.pass
```

Release tasks fail closed when either file is absent. Alternate protected paths can be supplied with the
`BERT_RELEASE_KEYSTORE` and `BERT_RELEASE_PASSWORD_FILE` Gradle properties.

After generating `release.json`, verify the exact publication pair before deployment:

```bash
npm run verify:release -- android/app/build/outputs/apk/release/app-release.apk /path/to/release.json PREVIOUS_VERSION_CODE
```

This gate verifies the APK signature, pinned signing certificate, package, increasing version code, version
name, SHA-256, manifest consistency, effective cleartext-network setting and backup prohibition.

Install `deploy/nginx-widget-security.conf` as `/etc/nginx/snippets/bert-widget-security.conf`, include it in
every widget location, then run `nginx -t` before reloading Nginx. The include must be repeated in locations
that set `Cache-Control`, because Nginx does not inherit parent `add_header` directives once a child location
defines one of its own.

Install `deploy/nginx-widget-rate-limit.conf` under `/etc/nginx/conf.d/` so its zones load in the `http`
context. Both public quote routes should use the request and connection zones and return HTTP 429 when a
client exceeds them. The Berthalla server must continue loading the Cloudflare real-IP configuration so the
limit key is the visitor IP rather than a Cloudflare edge address.

Serve the complete `/widget/download/` directory through the dedicated APK location. Do not add one rule per
version: every current and future APK must receive the Android package MIME type, attachment disposition,
`nosniff`, the shared security policy and bounded cache lifetime.

Never commit either file. Keep an encrypted off-server backup of both. Losing the keystore prevents publishing an upgrade that can replace the installed app.

Verify a published APK with Android build tools:

```bash
apksigner verify --verbose --print-certs bert-widget.apk
sha256sum bert-widget.apk
```

The expected signing-certificate SHA-256 digest is:

```text
f911463d6c89bf6927ac1ef9230412ba73ef2791ddb97c4ee4d457c082da995c
```

## v0.3.5 Android release record

- Package: `global.bert.widget`
- Version code: `9`
- Minimum Android: API 26
- APK SHA-256: `c2bec7205da024c6272fe803996bf1353d9655bc15012704601e5840ba6e2745`
- Versioned download: <https://berthalla.io/widget/download/bert-widget-0.3.5.apk>
- Stable download: <https://berthalla.io/widget/download/bert-widget.apk>

## v0.3.6 Android release record

- Package: `global.bert.widget`
- Version code: `10`
- Minimum Android: API 26
- APK SHA-256: `20d5de5ce124d886b2b13fe8200168e94281dd616448af349030108e462f9934`
- Versioned download: <https://berthalla.io/widget/download/bert-widget-0.3.6.apk>
- Stable download: <https://berthalla.io/widget/download/bert-widget.apk>
- Security: backups and device transfer disabled; HTTPS-only release traffic; fail-closed release verification

## v0.3.7 Android release record

- Package: `global.bert.widget`
- Version code: `11`
- Minimum Android: API 26
- APK SHA-256: `744204ad0b6360f87c8af37d6806702f0322015037d0bc702d77cb9e3909e969`
- Versioned download: <https://berthalla.io/widget/download/bert-widget-0.3.7.apk?sha=744204ad>
- Stable download: <https://berthalla.io/widget/download/bert-widget.apk?sha=744204ad>
- UI: refreshed Compact and Market widgets with movement, live/delayed state, and position hierarchy

## v0.3.8 Android release record

- Package: `global.bert.widget`
- Version code: `12`
- Minimum Android: API 26
- APK SHA-256: `ee1f2acb94e7364a5b584c3c5a95f9b4e222e10cdc2bc05dd0cf6e1bcf84dcc2`
- Versioned download: <https://berthalla.io/widget/download/bert-widget-0.3.8.apk?sha=ee1f2acb>
- Stable download: <https://berthalla.io/widget/download/bert-widget.apk?sha=ee1f2acb>
- UI: height-aware 4×2 layout with expanded market hierarchy, metric panels, and source/freshness footer

## v0.3.9 Android release record

- Package: `global.bert.widget`
- Version code: `13`
- Minimum Android: API 26
- APK SHA-256: `4ff7cd14214fb43565dda02c0b653a14bb1385d17390d25d13bccae897cb1926`
- Versioned download: <https://berthalla.io/widget/download/bert-widget-0.3.9.apk?sha=4ff7cd14>
- Stable download: <https://berthalla.io/widget/download/bert-widget.apk?sha=4ff7cd14>
- UI: real rolling 24-hour Compact-widget sparkline built from private on-device quote history

## v0.4.0 Android release record

- Package: `global.bert.widget`
- Version code: `14`
- Minimum Android: API 26
- APK SHA-256: `a5da30d29069168eea5273dd0139eaca1e0c84faa6504d2274cd938894b13388`
- Versioned download: <https://berthalla.io/widget/download/bert-widget-0.4.0.apk?sha=a5da30d2>
- Stable download: <https://berthalla.io/widget/download/bert-widget.apk?sha=a5da30d2>
- UI: Theme Studio with paired Home and Lock artwork for Mayor Purple, Woofhub Night, and Berthalla Nights; automatically matched widget palettes

## v0.5.0 Android release record — 2026-09-09

- Owner explicitly authorized deployment in the session after the native-validation limitation was disclosed.
- Package: `global.bert.widget`; version code `15`; minimum Android API 26; APK size 29,744,436 bytes.
- App source: `938d9d30a85025bf10caad01bcb3f4cbf72f23ae`; website commit: `302a21cdee26cfa028e47b1b2476ebcc54ca5923`.
- APK SHA-256: `c47fbee48bc37db5d0974fa453825f34288d9d9983a603033edafcc5040df4e0`. Existing pinned signing certificate retained.
- [Versioned download](https://berthalla.io/widget/download/bert-widget-0.5.0.apk?sha=c47fbee4), [stable download](https://berthalla.io/widget/download/bert-widget.apk?sha=c47fbee4).
- Home / Explore / Create / Tools, saved caption artwork, observed chart ranges, local holdings/cost tools, large-text navigation and explicit wallpaper outcomes.
- [Executable checks, public measurements and captures](docs/evidence/2026-09-09/release-0.5.0/README.md). Both public APKs were downloaded and passed the release verifier; both quote routes returned fresh data with advancing observations. Physical-device journeys remain UNVERIFIED.
- Publication updated only `/opt/berthalla/website/widget` files; no nginx/service changes or GitHub publication. Every previous versioned APK remains available. Rollback copies of the previous stable APK and page/manifest/assets are under `/opt/bert-widget-qa/release-0.5.0/previous`.

Use content hashes in updated JavaScript/CSS query parameters. The initial reused `widget.js?v=8` URL returned an older Cloudflare-cached script; `widget.js?v=050-5186ce4a` and `widget.css?v=050-bab24fb1` were verified to return the published bytes. Verify public asset bytes, not only origin files or HTTP 200.
