import sys
sys.excepthook = sys.__excepthook__
import hashlib,json,os,pathlib,shutil,subprocess
root=pathlib.Path('/opt/bert-widget-qa/release-0.5.0')
live=pathlib.Path('/opt/berthalla/website/widget')
stage=root/'staged'
release=json.loads((stage/'release.json').read_text())
apk=pathlib.Path('/opt/bert-widget/android/app/build/outputs/apk/release/app-release.apk')
assert release['version']=='0.5.0' and release['versionCode']==15
assert hashlib.sha256(apk.read_bytes()).hexdigest()==release['sha256']
assert (root/'staged-page.json').is_file()
for name in ['index.html','widget.js','widget.css','release.json','README.md']:
    assert (live/name).read_bytes()==(root/'previous'/name).read_bytes(),name+' changed during preparation'
assert (live/'download/bert-widget.apk').read_bytes()==(root/'previous/bert-widget.apk').read_bytes()
subprocess.run(['npm','run','verify:release','--',str(apk),str(stage/'release.json'),'14'],cwd='/opt/bert-widget',check=True)
def replace(source,target):
    temp=target.with_name(target.name+'.v050.tmp')
    shutil.copyfile(source,temp)
    os.chmod(temp,0o644)
    os.replace(temp,target)
replace(apk,live/'download/bert-widget-0.5.0.apk')
replace(apk,live/'download/bert-widget.apk')
for name in ['release.json','widget.js','widget.css','README.md','index.html']:
    replace(stage/name,live/name)
print('Published v0.5.0 APKs, metadata and landing page; retained all previous versioned downloads.')
