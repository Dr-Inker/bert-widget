from pathlib import Path
import hashlib, json, re, urllib.request, subprocess, sys
sys.excepthook = sys.__excepthook__

root = Path(__file__).resolve().parent
site = Path('/opt/berthalla/website')
manifest = json.loads((root/'publish-manifest.json').read_text())
def get(path):
    with urllib.request.urlopen(urllib.request.Request('https://berthalla.io'+path,headers={'User-Agent':'Mozilla/5.0'}), timeout=45) as response:
        return response.read(), {k.lower():v for k,v in response.headers.items()}

results = []
html = (site/'index.html').read_text()+(site/'widget/index.html').read_text()
for name, expected in manifest.items():
    if name.endswith('.md'):
        continue
    url = '/'+name
    if name == 'index.html':
        url = '/'
    elif name == 'widget/index.html':
        url = '/widget/'
    elif name.endswith('.css') or 'social-' in name:
        match = re.search(re.escape('/'+name)+r'\?v=[^"\s]+', html)
        assert match, name
        url = match[0]
    body, headers = get(url)
    actual = hashlib.sha256(body).hexdigest()
    assert actual == expected['next'], (name, actual, expected['next'])
    results.append({'path':url,'sha256':actual,'bytes':len(body),'contentType':headers.get('content-type'), 'cache':headers.get('cf-cache-status')})

body, _ = get('/widget/release.json?v=050')
release = json.loads(body)
assert release['version'] == '0.5.0' and release['versionCode'] == 15
assert release['sha256'] == 'c47fbee48bc37db5d0974fa453825f34288d9d9983a603033edafcc5040df4e0'
apk, headers = get('/widget/download/bert-widget-0.5.0.apk?sha=c47fbee4')
assert hashlib.sha256(apk).hexdigest() == release['sha256']
assert len(apk) == release['sizeBytes']
assert 'application/vnd.android.package-archive' in headers.get('content-type','')
assert 'attachment' in headers.get('content-disposition','')
sha = subprocess.check_output(['git','-c','safe.directory=/opt/berthalla','-C','/opt/berthalla','rev-parse','HEAD'],text=True).strip()
result = {'websiteSha':sha,'assets':results,'release':release,'apkSha256':release['sha256'],'apkBytes':len(apk)}
(root/'public-assets.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({'websiteSha':sha,'matchingPublicAssets':len(results),'version':release['version'],'apkBytes':len(apk),'apkSha256':release['sha256']}))
