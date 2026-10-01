import sys
sys.excepthook = sys.__excepthook__
import datetime, hashlib, json, pathlib, subprocess, re
REPO=str(pathlib.Path(__file__).resolve().parents[4])
root=pathlib.Path('/opt/bert-widget-qa/release-0.8.0');output=root/'public';output.mkdir(exist_ok=True)
expected=json.loads((root/'staged/release.json').read_text())
def fetch(url,name):
    target=output/name;headers=output/(name+'.headers')
    subprocess.run(['curl','-fsS','--max-time','60','-D',str(headers),url,'-o',str(target)],check=True)
    return target,headers.read_text().lower()
manifest,_=fetch('https://berthalla.io/widget/release.json?v=080','release.json')
assert json.loads(manifest.read_text())==expected
apks=[]
for label,url in [('versioned',expected['download']),('stable','https://berthalla.io/widget/download/bert-widget.apk?sha='+expected['sha256'][:8])]:
    apk,headers=fetch(url,label+'.apk');digest=hashlib.sha256(apk.read_bytes()).hexdigest()
    assert digest==expected['sha256'];assert apk.stat().st_size==expected['sizeBytes']
    for h in ['content-type: application/vnd.android.package-archive','content-disposition: attachment','x-content-type-options: nosniff','strict-transport-security:','content-security-policy:']:assert h in headers,(label,h)
    subprocess.run(['npm','run','verify:release','--',str(apk),str(manifest),'18'],cwd=REPO,check=True)
    subprocess.run(['python3',str(pathlib.Path(__file__).with_name('check-apk.py')),str(apk)],cwd=REPO,check=True)
    apks.append({'url':url,'sha256':digest,'sizeBytes':apk.stat().st_size})
page,_=fetch('https://berthalla.io/app/','index.html');assert page.read_bytes()==(root/'staged/index.html').read_bytes()
for pattern,name in [(r'src="([^"]*widget.js[^\"]*)"','widget.js'),(r'href="([^"]*widget.css[^\"]*)"','widget.css'),(r'src="([^"]*app-explore.png[^\"]*)"','app-explore.png'),(r'src="([^"]*app-tools.png[^\"]*)"','app-tools.png')]:
    url=re.search(pattern,page.read_text())[1];asset,_=fetch('https://berthalla.io'+url,name);assert asset.read_bytes()==(root/'staged'/name).read_bytes(),name
quotes=[];before=json.loads((root/'quote-before.json').read_text())
for route in ['api/quote','v1/bert/quote']:
    file,_=fetch('https://berthalla.io/widget/'+route,route.replace('/','-')+'.json');q=json.loads(file.read_text())
    assert q['meta']['freshness']=='fresh';assert q['source']['observedAt']>before['source']['observedAt']
    quotes.append({'route':route,'observedAt':q['source']['observedAt'],'meta':q['meta']})
protected=json.loads((root/'previous/preserved-hashes.json').read_text())
for name,digest in protected.items():assert hashlib.sha256((pathlib.Path('/opt/berthalla/website')/name).read_bytes()).hexdigest()==digest,name
result={'checkedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'sourceSha':(root/'source-sha.txt').read_text().strip(),'websiteSha':subprocess.check_output(['git','-c','safe.directory=/opt/berthalla','-C','/opt/berthalla','rev-parse','HEAD'],text=True).strip(),'release':expected,'downloads':apks,'quotes':quotes,'protectedFiles':len(protected)}
(root/'public-checks.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
