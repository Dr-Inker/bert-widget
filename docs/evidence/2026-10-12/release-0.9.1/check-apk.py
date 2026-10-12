import sys
sys.excepthook = sys.__excepthook__
import pathlib, zipfile, hashlib, json, subprocess
apk=pathlib.Path(sys.argv[1]);root=pathlib.Path(__file__).resolve().parents[4]
with zipfile.ZipFile(apk) as z:
    game=z.read('assets/games/lost-trail/index.html')
    provenance=json.loads(z.read('assets/games/lost-trail/provenance.json'))
    assert game==(root/'android/app/src/main/assets/games/lost-trail/index.html').read_bytes()
    assert hashlib.sha256(game).hexdigest()==provenance['sha256']
    dex=b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex'))
    assert b'https://appassets.androidplatform.net/lost-trail/index.html?app' in dex
    assert b'LostTrailActivity' in dex
    assert b'10.0.2.2' not in dex
    # 0.7.0: phones fetch market data directly; the Berthalla routes remain the fallback.
    for endpoint in [b'https://api.dexscreener.com/token-pairs/v1/solana/', b'https://api.geckoterminal.com/api/v2', b'https://berthalla.io/widget/api/quote', b'https://berthalla.io/widget/api/history']:
        assert endpoint in dex, endpoint
manifest=subprocess.check_output(['/opt/android-sdk/build-tools/36.0.0/aapt','dump','xmltree',str(apk),'AndroidManifest.xml'],text=True)
assert 'android:debuggable' not in manifest or 'android:debuggable(0x0101000f)=(type 0x12)0x0' in manifest
# 0.8.0: notifications are opt-in alerts; WorkManager's unused foreground service is removed for Play.
assert 'android.permission.POST_NOTIFICATIONS' in manifest
assert 'android.permission.FOREGROUND_SERVICE' not in manifest and 'SystemForegroundService' not in manifest
assert 'global.bert.widget.widget.BERTDailyWidgetReceiver' in manifest
activity=manifest.split('global.bert.widget.LostTrailActivity',1)[1].split('E: activity',1)[0]
assert 'android:exported(0x01010010)=(type 0x12)0x0' in activity,activity
print(json.dumps({'apkSha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'game':provenance,'isolatedActivity':True,'productionEndpointsOnly':True},indent=2))
# 0.8.1: the redesigned Bert widget's art must survive resource shrinking.
resources=subprocess.check_output(['/opt/android-sdk/build-tools/36.0.0/aapt','dump','resources',str(apk)],text=True)
for name in ['drawable/bert_widget_portrait','drawable/bert_widget_banner','drawable/bert_widget_glow']:
    assert name in resources, name
print('0.8.1 widget art present')
# 0.8.2: deep links, About links and the fixed feed agent survive R8; the launcher activity is singleTop.
for needle in [b'global.bert.widget.ENTRY', b'BERT-Android', b'https://berthalla.io/app/privacy/', b'https://github.com/Dr-Inker/bert-widget/issues']:
    assert needle in dex, needle
main=manifest.split('global.bert.widget.MainActivity',1)[1].split('E: activity',1)[0]
assert 'android:launchMode(0x0101001d)=(type 0x10)0x1' in main, main
assert 'versionCode' in manifest and '(type 0x10)0x17' in manifest.split('versionCode',1)[1][:40], 'versionCode 23'
print('0.8.2+ deep links, About links, feed agent, singleTop present')
# 0.9.1: the living Bert, wordmark and widget previews survive resource shrinking; shortcuts point at this package.
for name in ['drawable/bert_f10','drawable/bert_walk','drawable/today_garden','drawable/bert_wordmark','drawable/widget_preview_compact','drawable/widget_preview_market','drawable/widget_preview_bert']:
    assert name in resources, name
# Resource shrinking renames res/ paths, so find the shortcuts file through the resource table.
import re
paths = subprocess.check_output(['/opt/android-sdk/build-tools/36.0.0/aapt2','dump','resources',str(apk)],text=True)
spath = re.search(r'xml/shortcuts[^\n]*\n\s*\(\) \(file\) (\S+)', paths).group(1)
shortcuts = subprocess.check_output(['/opt/android-sdk/build-tools/36.0.0/aapt','dump','xmltree',str(apk),spath],text=True)
assert shortcuts.count('global.bert.widget') >= 3 and 'global.bert.widget.preview' not in shortcuts, shortcuts
print('0.9.1 art, previews and shortcut package present')
