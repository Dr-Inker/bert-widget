import datetime, hashlib, json, pathlib, subprocess
root = pathlib.Path('/opt/bert-widget-qa/release-0.8.1'); out = root / 'public'
expected = json.loads((root / 'staged/release.json').read_text())
def fetch(url, name):
    target, headers = out / name, out / (name + '.headers')
    subprocess.run(['curl', '-fsS', '--max-time', '60', '-D', str(headers), url, '-o', str(target)], check=True)
    return target, headers.read_text()
manifest, _ = fetch('https://berthalla.io/widget/release.json?v=081', 'release.json')
assert json.loads(manifest.read_text()) == expected
downloads = []
for label, url in [('versioned', expected['download']), ('stable', 'https://berthalla.io/widget/download/bert-widget.apk?sha=' + expected['sha256'][:8])]:
    apk, headers = fetch(url, label + '.apk')
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    assert digest == expected['sha256'] and apk.stat().st_size == expected['sizeBytes'], (label, digest)
    for h in ['content-type: application/vnd.android.package-archive', 'content-disposition: attachment', 'x-content-type-options: nosniff', 'strict-transport-security:', 'content-security-policy:']:
        assert h in headers.lower(), (label, h)
    subprocess.run(['npm', 'run', '-s', 'verify:release', '--', str(apk), str(manifest), '19'], cwd='/opt/bert-widget-direct', check=True)
    downloads.append({'url': url, 'sha256': digest, 'sizeBytes': apk.stat().st_size})
page, _ = fetch('https://berthalla.io/app/', 'index.html')
assert page.read_bytes() == pathlib.Path('/opt/berthalla/website/app/index.html').read_bytes()
quote, qh = fetch('https://berthalla.io/widget/api/quote', 'api-quote.json')
q = json.loads(quote.read_text()); assert q['meta']['freshness'] in ('fresh', 'cached'), q['meta']
assert qh.lower().count('cache-control:') == 1
result = {'checkedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'sourceSha': (root / 'source-sha.txt').read_text().strip(),
          'release': expected, 'downloads': downloads, 'quote': {'meta': q['meta'], 'observedAt': q['source']['observedAt']}}
(root / 'public-checks.json').write_text(json.dumps(result, indent=2) + '\n'); print(json.dumps({k: result[k] for k in ['checkedAt', 'downloads', 'quote']}, indent=1))
