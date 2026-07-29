# Deployment and release runbook

## Production topology

`bert-widget.service` runs the dependency-free Node quote service as `www-data` on `127.0.0.1:8787`. nginx exposes the landing page and proxies the public quote routes:

- `/widget/api/quote` — canonical production endpoint
- `/widget/v1/bert/quote` — compatibility quote endpoint
- `/widget/download/bert-widget.apk` — signed Android release

The public landing-page files live under `website/widget/` in the separate Berthalla website repository and are not duplicated here. The page links back to the public source repository and release history; keep those transparency links current whenever the repository or release process changes.

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
