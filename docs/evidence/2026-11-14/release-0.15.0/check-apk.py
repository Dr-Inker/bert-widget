import sys
sys.excepthook = sys.__excepthook__
import pathlib, zipfile, hashlib, json, subprocess
apk=pathlib.Path(sys.argv[1]);root=pathlib.Path(__file__).resolve().parents[4]

def check_014(apk):
    """0.14.0's features, inherited: the bark, the You page, the bubble, the ranges, the labels, CoinMarketCap."""
    tools = '/opt/android-sdk/build-tools/36.0.0/'
    with zipfile.ZipFile(apk) as z:
        names = set(z.namelist())
        dex = b''.join(z.read(n) for n in names if n.endswith('.dex'))
        game = z.read('assets/games/lost-trail/index.html')
        provenance = json.loads(z.read('assets/games/lost-trail/provenance.json'))
        licence = z.read('assets/licences/CC0-Bark-Freesound-163459.txt') if 'assets/licences/CC0-Bark-Freesound-163459.txt' in names else b''
    checks = {
        'provenance names the Freesound 163459 bark': 'Freesound 163459' in provenance.get('bark', ''),
        'game embeds the bark MP3s (BARK=["SUQz…", ID3)': b'BARK=["SUQz' in game and b'barkBuffers' in game,
        'CC0 licence for the bark is bundled': b'CC0' in licence and b'163459' in licence,
        # The You page, its one stored item and its way out.
        'You page strings': all(n in dex for n in [b'What should Bert call you?', b'Forget my name', b'Everything here stays on this phone.', b'bert_you']),
        # Home's speech bubble announces Bert's line.
        'speech bubble line': b'Bert says: ' in dex,
        # Market: the three longer ranges, the change line, and no "All". 0.14.2 builds the change line from parts
        # ("Change " + "over the past …" or "since …"), so the parts are what the dex holds.
        'Market 1W/1M/1Y ranges': all(n in dex for n in [b'a week of prices', b'a month of prices', b'a year of prices', b'over the past ', b'since ']),
        # A guard, not a 0.14 discriminator (0.13.2 has no All either): 6314171 had an All range, whose strings were
        # "all time" and "all available history"; GeckoTerminal's free API cannot serve it, so it must not come back.
        'no All range': b'all available history' not in dex and b'all time' not in dex.lower(),
        # 0.15: the garden is for Bert, not a menu. The 0.14 label pills and their spoken names must be gone.
        'no garden label pills (0.15)': all(n not in dex for n in ['Price ›'.encode(), 'Play ›'.encode(), b'Price: chalkboard', b'Play: the garden gate to The Lost Trail']),
        # CoinMarketCap is the main market link.
        'CoinMarketCap link': b'https://coinmarketcap.com/currencies/bertram-the-pomeranian/' in dex and b'View on CoinMarketCap' in dex,
    }
    for name, ok in checks.items():
        print(('PASS ' if ok else 'FAIL ') + name)
    failed = [n for n, ok in checks.items() if not ok]
    print('0.14.0 checks: ' + ('all pass' if not failed else f'{len(failed)} of {len(checks)} FAIL'))
    return 1 if failed else 0

def check_0142(apk):
    """0.14.1 and 0.14.2, inherited (0.14.2's version and board-row checks moved on with 0.15)."""
    tools = '/opt/android-sdk/build-tools/36.0.0/'
    badging = subprocess.check_output([tools + 'aapt', 'dump', 'badging', str(apk)], text=True)
    with zipfile.ZipFile(apk) as z:
        names = set(z.namelist())
        dex = b''.join(z.read(n) for n in names if n.endswith('.dex'))
        provenance = json.loads(z.read('assets/games/lost-trail/provenance.json'))
    checks = {
        # 0.14.1: The Lost Trail is the platformer build that stays steady under reduced motion (c594245).
        'game pinned to platformer c594245': provenance.get('sourceSha') == 'c5942455b71d5e3366a6430ecfd20faca3866d0e',
        # Meet Bert says what BERT is. (0.14.2's "On the board: " row became the paper note in 0.15.)
        'Meet Bert line': b'BERT is a community coin on Solana, named after him.' in dex,
        # The Market widget can leave the position out.
        'Market widget holdings switch': all(n in dex for n in [b'Show my holdings on the Market widget', 'When off, the widget shows BERT’s price and market only.'.encode()]),
        # A website install hears of updates: the file it reads and the line it shows.
        'update notice reads release.json': all(n in dex for n in [b'https://berthalla.io/widget/release.json', b' is out', b'Get it from the BERT page on berthalla.io.']),
    }
    for name, ok in checks.items():
        print(('PASS ' if ok else 'FAIL ') + name)
    failed = [n for n, ok in checks.items() if not ok]
    print('0.14.2 checks: ' + ('all pass' if not failed else f'{len(failed)} of {len(checks)} FAIL'))
    return 1 if failed else 0

def check_015(apk):
    """0.15.0: each check is reported on its own, so running with --only-0.15.0 against the 0.14.2 APK shows every one fail."""
    tools = '/opt/android-sdk/build-tools/36.0.0/'
    badging = subprocess.check_output([tools + 'aapt', 'dump', 'badging', str(apk)], text=True)
    resources = subprocess.check_output([tools + 'aapt', 'dump', 'resources', str(apk)], text=True)
    with zipfile.ZipFile(apk) as z:
        dex = b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex'))
    checks = {
        # The version this record ships, read from the APK's own badging.
        'versionCode 35, versionName 0.15.0': "versionCode='35'" in badging and "versionName='0.15.0'" in badging,
        # Home under the garden: the price on a slate, Berthalla's note on paper.
        'Home slate and paper note': all(n in dex for n in [b'ON THE BOARD', b'Read the board', b'Read the noticeboard', b'price unavailable']),
        # The garden is for Bert: his remarks, the pat count and the heart's home.
        'garden remarks and pat count': all(n in dex for n in [b'chalked it myself. paws are not ideal for this.', b'pats_ever', 'something new. it’s pinned up just below.'.encode()]),
        # The daily streak: its store, what TalkBack says for the chalkboard, the You page line.
        'daily streak': all(n in dex for n in [b'streak_days', b'streak_last_day', b'Opening streak: ', b'three days running. people will talk.']),
        # Bert's day is the paper note: its paper, panel and bitmap layouts survive resource shrinking; the photo is gone.
        "Bert's day note resources": all(n in resources for n in ['drawable/bert_note_paper', 'drawable/bert_note_panel', 'layout/widget_note_words', 'layout/widget_note_art'])
            and all(n not in resources for n in ['drawable/bert_widget_portrait', 'drawable/bert_widget_banner', 'drawable/bert_widget_glow']),
    }
    for name, ok in checks.items():
        print(('PASS ' if ok else 'FAIL ') + name)
    failed = [n for n, ok in checks.items() if not ok]
    print('0.15.0 checks: ' + ('all pass' if not failed else f'{len(failed)} of {len(checks)} FAIL'))
    return 1 if failed else 0

if '--only-0.15.0' in sys.argv:
    sys.exit(check_015(apk))
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
# 0.8.1's Bert widget art (portrait, banner, glow) was retired in 0.15 with the paper note (see check_015).
resources=subprocess.check_output(['/opt/android-sdk/build-tools/36.0.0/aapt','dump','resources',str(apk)],text=True)
# 0.8.2: deep links, About links and the fixed feed agent survive R8; the launcher activity is singleTop.
for needle in [b'global.bert.widget.ENTRY', b'BERT-Android', b'https://berthalla.io/app/privacy/', b'https://github.com/Dr-Inker/bert-widget/issues']:
    assert needle in dex, needle
main=manifest.split('global.bert.widget.MainActivity',1)[1].split('E: activity',1)[0]
assert 'android:launchMode(0x0101001d)=(type 0x10)0x1' in main, main
assert 'versionCode' in manifest and '(type 0x10)0x23' in manifest.split('versionCode',1)[1][:40], 'versionCode 35'
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
# 0.13.1: the new BERT Navy and BERT Violet wallpapers ship byte for byte (raw resources are stored, not recompressed),
# and the picker previews survive shrinking.
with zipfile.ZipFile(apk) as z:
    for name in ['wallpaper_bert_navy_home','wallpaper_bert_navy_lock','wallpaper_bert_violet_home','wallpaper_bert_violet_lock']:
        rpath = re.search(r'raw/' + name + r'[^\n]*\n\s*\(\) \(file\) (\S+)', paths).group(1)
        assert z.read(rpath) == (root/f'android/app/src/main/res/raw/{name}.webp').read_bytes(), name
for name in ['drawable/preview_bert_navy','drawable/preview_bert_violet']:
    assert name in resources, name
print('0.13.1 Navy and Violet wallpapers match the source byte for byte; previews present')
# 0.13.2: Mayor Purple's home ships without the dock-band wordmark (byte for byte), every pack has a Home preview for
# the picker's Home screen tab, and Home keeps its labelled way to the noticeboard.
with zipfile.ZipFile(apk) as z:
    rpath = re.search(r'raw/wallpaper_mayor_purple_home[^\n]*\n\s*\(\) \(file\) (\S+)', paths).group(1)
    assert z.read(rpath) == (root/'android/app/src/main/res/raw/wallpaper_mayor_purple_home.webp').read_bytes(), 'mayor purple home'
for name in ['bert_violet','bert_navy','mayor_purple','woofhub_night','berthalla_nights']:
    assert f'drawable/preview_{name}_home' in resources, name
for needle in [b'Read the noticeboard', b'Home screen', b'Lock screen']:
    assert needle in dex, needle
print('0.13.2 Mayor Purple home, five Home previews, Lock/Home switch and the noticeboard link present')
# 0.14.0's features (check_014), 0.14.1 and 0.14.2 (check_0142), then 0.15.0 (check_015).
sys.exit(check_014(apk) or check_0142(apk) or check_015(apk))
