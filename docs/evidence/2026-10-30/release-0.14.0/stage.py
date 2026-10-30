#!/usr/bin/env python3
"""Stage a signed BERT 0.14.0 build: the QA folder and the website release worktree.

Rerunnable: after a rebuild (rc2, ...), run it again and only the hashes, size, date and source revision change.
It never touches /opt/berthalla (live) and never commits; check the website diff and commit by hand.

  python3 stage.py [--date 2026-10-30]

Inputs are the Gradle outputs in /opt/bert-widget-v014/android/app/build/outputs (lintRelease, assembleRelease,
bundleRelease from a clean tree at the revision to ship).
"""
import argparse, hashlib, json, pathlib, re, shutil, subprocess, sys, datetime

VERSION, CODE = '0.14.0', 32
PREVIOUS = '0.13.2'  # staged, never published; 0.14.0 ships in its place
CERT = 'f911463d6c89bf6927ac1ef9230412ba73ef2791ddb97c4ee4d457c082da995c'
APP = pathlib.Path('/opt/bert-widget-v014')
OUT = APP / 'android/app/build/outputs'
QA = pathlib.Path('/opt/bert-widget-qa/release-0.14.0/staged')
SITE = pathlib.Path('/opt/bert-widget-site-release/website')
TOOLS = '/opt/android-sdk/build-tools/36.0.0/'

ap = argparse.ArgumentParser()
ap.add_argument('--date', default=datetime.date.today().isoformat())
args = ap.parse_args()
assert pathlib.Path('/opt/berthalla').resolve() not in SITE.resolve().parents, 'refusing to stage into the live site'

source = subprocess.check_output(['git', '-C', str(APP), 'rev-parse', 'HEAD'], text=True).strip()
dirty = subprocess.check_output(['git', '-C', str(APP), 'status', '--porcelain', '--', 'android', 'src'], text=True)
assert not dirty.strip(), 'app tree has uncommitted changes under android/ or src/:\n' + dirty
apk_src, aab_src = OUT / 'apk/release/app-release.apk', OUT / 'bundle/release/app-release.aab'
mapping_src = OUT / 'mapping/release/mapping.txt'
for f in [apk_src, aab_src, mapping_src]:
    assert f.is_file(), f

badging = subprocess.check_output([TOOLS + 'aapt', 'dump', 'badging', str(apk_src)], text=True)
assert f"versionCode='{CODE}'" in badging and f"versionName='{VERSION}'" in badging, 'APK is not ' + VERSION
signers = subprocess.check_output([TOOLS + 'apksigner', 'verify', '--print-certs', str(apk_src)], text=True)
cert = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})', signers).group(1)
assert cert == CERT, f'unexpected signing certificate {cert}'
permissions = re.findall(r"uses-permission: name='([^']+)'", badging)

QA.mkdir(parents=True, exist_ok=True)
apk = QA / f'bert-{VERSION}.apk'
for src, dst in [(apk_src, apk), (aab_src, QA / f'bert-{VERSION}.aab'), (mapping_src, QA / f'mapping-{VERSION}.txt')]:
    shutil.copyfile(src, dst)
data = apk.read_bytes()
sha = hashlib.sha256(data).hexdigest()
release = {
    'version': VERSION, 'versionCode': CODE, 'minimumAndroid': '8.0', 'releaseDate': args.date,
    'sizeBytes': len(data), 'sha256': sha, 'signingCertificateSha256': cert, 'permissions': permissions,
    'download': f'https://berthalla.io/widget/download/bert-widget-{VERSION}.apk?sha={sha[:8]}',
    'sourceRevision': source,
}
text = json.dumps(release, indent=2) + '\n'
(QA / 'release.json').write_text(text)

# Website: the APK under both names, release.json, and every version/size/checksum/link on / and /app/.
download = SITE / 'widget/download'
# 0.14.0 replaces 0.13.2's versioned file, as 0.13.2 replaced 0.13.1's; older versioned APKs stay where they are.
superseded = download / f'bert-widget-{PREVIOUS}.apk'
if superseded.exists():
    subprocess.check_call(['git', '-C', str(SITE), 'rm', '-q', str(superseded)])
shutil.copyfile(apk, download / f'bert-widget-{VERSION}.apk')
shutil.copyfile(apk, download / 'bert-widget.apk')
(SITE / 'widget/release.json').write_text(text)

day = datetime.date.fromisoformat(args.date)
human_date = f'{day.day} {day.strftime("%b %Y")}'
size = f'{len(data) / 1048576:.1f} MB'
page = SITE / 'app/index.html'
html = page.read_text()
subs = [
    (r'/widget/download/bert-widget-[0-9.]+\.apk\?sha=[0-9a-f]{8}', f'/widget/download/bert-widget-{VERSION}.apk?sha={sha[:8]}'),
    (r'(<span data-release-version>)[0-9.]+(</span>)', rf'\g<1>{VERSION}\g<2>'),
    (r'(<span data-version-code>)\d+(</span>)', rf'\g<1>{CODE}\g<2>'),
    (r'(<span data-release-size>)[^<]*(</span>)', rf'\g<1>{size}\g<2>'),
    (r'(<code data-checksum>)[0-9a-f]{64}(</code>)', rf'\g<1>{sha}\g<2>'),
    (r'<time data-release-date datetime="[0-9-]+">[^<]*</time>', f'<time data-release-date datetime="{args.date}">{human_date}</time>'),
    (r'INCLUDED IN v[0-9.]+', f'INCLUDED IN v{VERSION}'),
]
for pattern, repl in subs:
    html, n = re.subn(pattern, repl, html)
    assert n, f'no match on /app/ for {pattern}'
page.write_text(html)
home = SITE / 'index.html'
h, n = re.subn(r'Version [0-9.]+ for Android 8 and up', f'Version {VERSION} for Android 8 and up', home.read_text())
assert n == 1, 'homepage version line'
home.write_text(h)

print(json.dumps({'apk': str(apk), 'sizeBytes': len(data), 'sha256': sha, 'certificate': cert, 'source': source,
                  'aabSha256': hashlib.sha256((QA / f'bert-{VERSION}.aab').read_bytes()).hexdigest(),
                  'mappingSha256': hashlib.sha256((QA / f'mapping-{VERSION}.txt').read_bytes()).hexdigest()}, indent=2))
