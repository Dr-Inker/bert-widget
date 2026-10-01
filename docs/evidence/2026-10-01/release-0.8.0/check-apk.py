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
