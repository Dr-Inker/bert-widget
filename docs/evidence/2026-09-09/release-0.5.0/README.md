# BERT v0.5.0 deployment evidence — 2026-09-09

The owner requested deployment after being told the latest Android implementation was local and device validation remained outstanding. The signed Android APK, release manifest and landing page are published at https://berthalla.io/widget/.

- App source: `938d9d30a85025bf10caad01bcb3f4cbf72f23ae` (v0.5.0, version code 15).
- Website source: `302a21cdee26cfa028e47b1b2476ebcc54ca5923` in `/opt/berthalla`.
- APK SHA-256: `c47fbee48bc37db5d0974fa453825f34288d9d9983a603033edafcc5040df4e0`; 29,744,436 bytes.
- Certificate SHA-256: `f911463d6c89bf6927ac1ef9230412ba73ef2791ddb97c4ee4d457c082da995c`, unchanged from v0.4.0.
- Versioned URL: https://berthalla.io/widget/download/bert-widget-0.5.0.apk?sha=c47fbee4
- Stable URL: https://berthalla.io/widget/download/bert-widget.apk?sha=c47fbee4

## App gates

From `/opt/bert-widget`, all commands below passed at app source `938d9d30a85025bf10caad01bcb3f4cbf72f23ae`:

```bash
export GRADLE_USER_HOME=/opt/bert-widget-qa/gradle
export ANDROID_USER_HOME=/opt/bert-widget-qa/android-home
export JAVA_TOOL_OPTIONS=-Duser.home=/opt/bert-widget-qa
tools/check-android.sh
android/gradlew -p android :app:lintRelease :app:assembleRelease --console=plain
npm run verify:release -- android/app/build/outputs/apk/release/app-release.apk /opt/bert-widget-qa/release-0.5.0/staged/release.json 14
```

[Development log](development-gate.txt), [signed build log](signed-build.txt), [signature/publication-pair log](release-verification.txt). Eight Node tests and 60 Android JVM tests, zero failures/skips. The committed-source development invocation reused Gradle's up-to-date Android results from the identical application tree built immediately before it; it did not re-execute those test bodies. Ten original JUnit reports are retained under `junit/`, with [counts](unit-tests.json). Debug and release lint each have zero errors and 35 warnings ([counts](lint-counts.json)).

The APK inspection additionally confirmed the release is not debuggable, contains both production HTTPS endpoints and contains no emulator endpoint. [Inspection](package-inspection.json). The release verifier checks package/version increase, pinned certificate, APK/manifest hash consistency, HTTPS-only traffic and backup prohibition. No physical install or native journey was run for this release.

## Public gates

At app source `938d9d30a85025bf10caad01bcb3f4cbf72f23ae` and website SHA `302a21cdee26cfa028e47b1b2476ebcc54ca5923`, these commands passed:

```bash
python3 /opt/bert-widget-qa/release-0.5.0/check-public.py
node /opt/bert-widget-qa/release-0.5.0/check-page.cjs public
```

Copies of the executable runners are [check-public.py](check-public.py) and [check-page.cjs](check-page.cjs). They use the retained staging/download scratch directory `/opt/bert-widget-qa/release-0.5.0`; large APKs and temporary downloads are kept there, outside these committed evidence files.

[Public verification log](public-verification.txt) and [results](public-checks.json): both public APK URLs downloaded successfully and passed `npm run verify:release` against the downloaded public manifest with previous code 14. Both match the SHA-256 and size above. APK MIME type, attachment disposition, nosniff, HSTS and CSP were present. Public HTML, JavaScript and CSS bytes matched the staged files. Both public quote routes returned `meta.freshness=fresh`; `source.observedAt` advanced from `2026-09-09T08:25:24.287Z` to `2026-09-09T08:34:38.311Z`.

[Public browser results](public-page.json): all six download links target the versioned v0.5.0 APK; version/code/date/hash agree with the release manifest; navigation, source-section and footer source links remain present; the download CTA is at least 44 px high and horizontally inside the viewport; no JavaScript page errors were observed. Document overflow is 0 px at all four required widths. Actual captures: [1440](public-1440.png), [768](public-768.png), [390](public-390.png), [320](public-320.png).

The existing live page at website base `32a70bd` had a 420 px document at a 390 px viewport ([measurement](overflow-baseline.json), [capture](overflow-baseline.png)). The hero's decorative rings and fixed-width phone also widened the narrow grid. The scoped CSS correction makes the grid/phone fit and bounds the decorative rings; it does not hide document overflow. The first reused numeric script URL returned older Cloudflare-cached bytes, so final HTML uses content-specific JavaScript/CSS keys. The final public checks above validate those exact bytes.

From `/opt/berthalla`, the full incumbent website gate passed at `302a21cdee26cfa028e47b1b2476ebcc54ca5923`:

```bash
GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=safe.directory GIT_CONFIG_VALUE_0=/opt/berthalla PYTHONDONTWRITEBYTECODE=1 PYTEST_ADDOPTS='-p no:cacheprovider' .claude/test-gate.sh
```

[Log](website-gate.txt): 577 passed, 33 skipped. The incumbent script clears `DATABASE_URL`; this run did not authorize live database changes. The same suite also ran through the repository commit hook before both website commits. Read-only runbook health checks `systemctl is-active bert-widget.service` and `curl -fsS http://127.0.0.1:8787/healthz` returned `active` and status `ok`; the public checks independently verify routing/freshness.

## Scope and remaining limits

The source/release and website commits are local; no GitHub push or GitHub release occurred. Unrelated homepage/CSS modifications, music files and older untracked APKs in `/opt/berthalla` were preserved. No systemd/nginx configuration changed. Production signing files were read by the build and were not changed or copied into the repository. All previous versioned APKs remain available; the previous stable APK/page/manifest/assets are retained under `/opt/bert-widget-qa/release-0.5.0/previous` for recovery. Publication used per-file atomic replacements, with the verified APKs installed before the page/manifest pointed to them.

All physical-device journeys remain **UNVERIFIED**: update installation, launcher placement, wallpaper outcomes, actual sharing, process-death restoration, accessibility, system bars and performance. Host tests and publication are not S-tier acceptance. Resume from [the device contract](../../../DEVICE-REVIEW.md) when functioning native access is available.
