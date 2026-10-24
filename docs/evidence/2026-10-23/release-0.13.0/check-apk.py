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
assert 'versionCode' in manifest and '(type 0x10)0x1d' in manifest.split('versionCode',1)[1][:40], 'versionCode 29'
print('0.8.2+ deep links, About links, feed agent, singleTop present')
# 0.9.1: the living Bert, wordmark and widget previews survive resource shrinking; shortcuts point at this package.
for name in ['drawable/bert_f10','drawable/bert_walk','drawable/bert_wordmark','drawable/widget_preview_compact','drawable/widget_preview_market','drawable/widget_preview_bert']:
    assert name in resources, name
# Resource shrinking renames res/ paths, so find the shortcuts file through the resource table.
import re
paths = subprocess.check_output(['/opt/android-sdk/build-tools/36.0.0/aapt2','dump','resources',str(apk)],text=True)
spath = re.search(r'xml/shortcuts[^\n]*\n\s*\(\) \(file\) (\S+)', paths).group(1)
shortcuts = subprocess.check_output(['/opt/android-sdk/build-tools/36.0.0/aapt','dump','xmltree',str(apk),spath],text=True)
assert shortcuts.count('global.bert.widget') >= 3 and 'global.bert.widget.preview' not in shortcuts, shortcuts
print('0.9.1 art, previews and shortcut package present')
# 0.10: My Bert reaches only its three hosts and survives R8; the gate banner and mood stills survive shrinking.
for needle in [b'api-mainnet.magiceden.dev', b'https://api.mainnet-beta.solana.com', b'green-advanced-mule-774.mypinata.cloud',
               b'AYh4yso4fZUVHEMMpS3wXxwZt1TgYnbFPb5c4y5LDFFS', b'getProgramAccounts', b'getAssetBatch']:
    assert needle in dex, needle
assert b'magiceden.io/marketplace' not in dex, 'no marketplace link in 0.10'
for name in ['drawable/bert_mood_content','drawable/bert_mood_giddy','drawable/bert_mood_melancholy']:
    assert name in resources, name
assert 'android:allowBackup(0x01010280)=(type 0x12)0x0' in manifest, 'backup stays off'
print('0.10 My Bert hosts, no marketplace link, mood stills, backup off')
# 0.11: BERT Violet survives shrinking: the five Outfit weights, the kit wordmark, the violet pack, and the font licence.
for name in ['font/outfit_regular','font/outfit_medium','font/outfit_semibold','font/outfit_bold','font/outfit_extrabold',
             'drawable/bert_wordmark_brand','raw/wallpaper_bert_violet_home','raw/wallpaper_bert_violet_lock','drawable/preview_bert_violet']:
    assert name in resources, name
assert 'font/outfit_variable' not in resources, 'the Thin-default variable font is gone'
with zipfile.ZipFile(apk) as z:
    assert b'SIL OPEN FONT LICENSE' in z.read('assets/licences/OFL-Outfit.txt').upper()
assert b'bert-violet' in dex and b'berthalla-navy' in dex, 'scheme and pack ids'
print('0.11 BERT Violet fonts, wordmark, pack and OFL present')
# 0.12: Bert's garden survives shrinking: every time of day (square and wide), the nap and pat poses, the garden
# widget and its preview, Gochi Hand and its licence.
# Home draws the wide bake; the widget uses both. The window-off night is Home's only, so only its wide bake ships.
for t in ['dawn','day','golden','dusk','night']:
    for prefix in ['drawable/place_','drawable/place_wide_']:
        assert prefix+t in resources, prefix+t
assert 'drawable/place_wide_nightoff' in resources
for name in ['drawable/bert_mood_asleep','drawable/bert_pat','drawable/widget_preview_place','font/gochi_hand']:
    assert name in resources, name
with zipfile.ZipFile(apk) as z:
    assert b'SIL OPEN FONT LICENSE' in z.read('assets/licences/OFL-GochiHand.txt').upper()
assert 'global.bert.widget.widget.BERTPlaceWidgetReceiver' in manifest
assert b'you woke the mayor.' in dex or b'you woke the mayor' in dex, 'the night-window line'
print('0.12 garden scenes, poses, garden widget, Gochi Hand and OFL present')
# 0.12 replaced Home's garden banner with Bert's place; the unused banner art is rightly shrunk away.
assert 'drawable/today_garden' not in resources, 'the old garden banner should be gone'
# 0.13: Explore became Play (games and music) and its links moved to Home's "Around the pack" sheet; Personalise is
# Tools › Style. The Lost Trail picture both screens use survives shrinking.
assert b'Off the lead.' in dex and b'Around the pack' in dex and b'around the pack' in dex, 'Play and the pack sheet'
assert b'Follow your nose.' not in dex, 'the Explore screen is gone'
assert 'drawable/lost_trail' in resources
print('0.13 Play, Around the pack, no Explore, Lost Trail art present')
