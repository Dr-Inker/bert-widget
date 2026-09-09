import sys
sys.excepthook = sys.__excepthook__
import datetime, hashlib, json, pathlib, subprocess
root = pathlib.Path('/opt/bert-widget-qa/release-0.5.0')
output = root / 'public'
output.mkdir(exist_ok=True)
expected = json.loads((root/'staged/release.json').read_text())
def fetch(url, name):
    target = output / name
    headers = output / (name+'.headers')
    subprocess.run(['curl','-fsS','--max-time','60','-D',str(headers),url,'-o',str(target)],check=True)
    return target, headers.read_text()
manifest,_ = fetch('https://berthalla.io/widget/release.json?v=050','release.json')
assert json.loads(manifest.read_text()) == expected
apks = []
for label,url in [('versioned',expected['download']),('stable','https://berthalla.io/widget/download/bert-widget.apk?sha='+expected['sha256'][:8])]:
    apk, headers = fetch(url, label+'.apk')
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    assert digest == expected['sha256'], (label, digest)
    assert apk.stat().st_size == expected['sizeBytes']
    for header in ['content-type: application/vnd.android.package-archive','content-disposition: attachment','x-content-type-options: nosniff','strict-transport-security:','content-security-policy:']:
        assert header in headers.lower(),(label,header)
    subprocess.run(['npm','run','verify:release','--',str(apk),str(manifest),'14'],cwd='/opt/bert-widget',check=True)
    apks.append({'url':url,'sha256':digest,'sizeBytes':apk.stat().st_size})
quotes=[]
before=json.loads((root/'quote-before.json').read_text())
for route in ['api/quote','v1/bert/quote']:
    file,_ = fetch('https://berthalla.io/widget/'+route,route.replace('/','-')+'.json')
    quote=json.loads(file.read_text())
    assert quote['meta']['freshness']=='fresh',quote['meta']
    assert quote['source']['observedAt'] > before['source']['observedAt']
    quotes.append({'route':route,'meta':quote['meta'],'observedAt':quote['source']['observedAt']})
page,_ = fetch('https://berthalla.io/widget/','index.html')
assert page.read_bytes() == (root/'staged/index.html').read_bytes()
script,_=fetch('https://berthalla.io/widget/widget.js?v=050-5186ce4a','widget.js')
assert script.read_bytes() == (root/'staged/widget.js').read_bytes()
css,_=fetch('https://berthalla.io/widget/widget.css?v=050-bab24fb1','widget.css')
assert css.read_bytes() == (root/'staged/widget.css').read_bytes()
result={'checkedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'sourceSha':(root/'source-sha.txt').read_text().strip(),'release':expected,'downloads':apks,'quotes':quotes,'priorObservedAt':before['source']['observedAt']}
(root/'public-checks.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
