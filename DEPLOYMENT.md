# Deployment and release runbook

## Production topology

`bert-widget.service` runs the dependency-free Node quote service as `www-data` on `127.0.0.1:8787`. nginx exposes the landing page and proxies the public quote routes:

- `/widget/api/quote` — canonical production endpoint
- `/widget/v1/bert/quote` — compatibility quote endpoint
- `/widget/download/bert-widget.apk` — signed Android release

The public landing-page files live in the separate Berthalla website repository and are not duplicated here.

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
name, SHA-256, manifest consistency and effective cleartext-network setting.

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
