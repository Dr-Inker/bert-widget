#!/usr/bin/env python3
"""Native UI checks on a disposable AVD and labelled quote fixtures; never a live device."""
import argparse
import datetime
import http.server
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

sys.excepthook = sys.__excepthook__

parser = argparse.ArgumentParser()
parser.add_argument('--avd', required=True)
parser.add_argument('--apk', required=True)
parser.add_argument('--baseline-apk')
parser.add_argument('--output', required=True)
parser.add_argument('--sdk', default='/opt/android-sdk')
parser.add_argument('--boot-timeout', type=int, default=480)
args = parser.parse_args()
out = Path(args.output).resolve()
out.mkdir(parents=True, exist_ok=True)
adb = [str(Path(args.sdk) / 'platform-tools/adb'), '-P', '5040', '-s', 'emulator-5580']
package = 'global.bert.widget'
checks = []
completed = False


def run(command, **kwargs):
    return subprocess.run(command, check=True, capture_output=True, timeout=90, **kwargs).stdout


def shell(*command):
    return run(adb + ['shell', *command]).decode().strip()


def check(name, condition):
    checks.append({'check': name, 'passed': bool(condition)})
    print(('PASS ' if condition else 'FAIL ') + name, flush=True)
    assert condition, name


class QuoteServer(http.server.BaseHTTPRequestHandler):
    online = True
    protocol_version = 'HTTP/1.1'

    def do_CONNECT(self):
        # Emulator-level proxy: only the debug quote connection gets a fixture tunnel.
        if self.path not in ('10.0.2.2:8787', '127.0.0.1:8787', 'localhost:8787'):
            self.send_error(403)
            return
        self.send_response(200)
        self.end_headers()
        self.close_connection = False

    def do_GET(self):
        quote = {
            'asset': {'chain': 'solana', 'mint': 'HgBRWfYxEfvPhtqkaeymCQtHCrKE46qQ43pKe8HCpump'},
            'quote': {'priceUsd': 0.005, 'change24hPct': 4.25, 'marketCapUsd': 5000000, 'volume24hUsd': 125000, 'liquidityUsd': 750000},
            'source': {'name': 'dexscreener', 'dex': 'raydium', 'pairUrl': 'https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY',
                       'observedAt': datetime.datetime.now(datetime.timezone.utc).isoformat()},
            'meta': {'freshness': 'fresh'},
        }
        body = json.dumps(quote if self.online else {'error': 'fixture_offline'}).encode()
        self.send_response(200 if self.online else 503)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *unused):
        pass


def tree():
    shell('uiautomator', 'dump', '/sdcard/bert-window.xml')
    raw = run(adb + ['exec-out', 'cat', '/sdcard/bert-window.xml'])
    return ET.fromstring(raw), raw


def wait_text(text, timeout=75):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        root, raw = tree()
        if text in raw.decode():
            return root
        time.sleep(2)
    raise AssertionError('UI did not contain ' + text)


def tap_node(node):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.attrib['bounds']))
    shell('input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1)


def tap(text):
    root = wait_text(text)
    nodes = [n for n in root.iter('node') if n.attrib.get('text') == text or n.attrib.get('content-desc') == text]
    assert nodes, 'No node for ' + text
    tap_node(nodes[-1])


def screenshot(name):
    root, raw = tree()
    (out / (name + '.xml')).write_bytes(raw)
    (out / (name + '.png')).write_bytes(run(adb + ['exec-out', 'screencap', '-p']))
    return raw.decode()


def launch():
    shell('am', 'start', '-W', '-n', package + '/.MainActivity')
    time.sleep(3)


def resume():
    shell('input', 'keyevent', 'KEYCODE_HOME')
    launch()


def scroll():
    shell('input', 'swipe', '360', '1000', '360', '360', '350')
    time.sleep(1)


def prefs(name, values):
    encoded = '<map>' + ''.join('<string name="' + k + '">' + escape(v) + '</string>' for k, v in values.items()) + '</map>'
    shell('run-as', package, 'mkdir', '-p', 'shared_prefs')
    run(adb + ['shell', 'run-as', package, 'tee', 'shared_prefs/' + name + '.xml'], input=encoded.encode())


server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), QuoteServer)
threading.Thread(target=server.serve_forever, daemon=True).start()
emu_env = dict(os.environ, ANDROID_ADB_SERVER_PORT='5040')
log = (out / 'emulator.log').open('w')
emulator = subprocess.Popen([
    str(Path(args.sdk) / 'emulator/emulator'), '-avd', args.avd, '-accel', 'off', '-no-window',
    '-no-audio', '-no-boot-anim', '-no-snapshot', '-gpu', 'swiftshader_indirect',
    '-memory', '2048', '-cores', '2', '-port', '5580', '-no-metrics',
    '-http-proxy', 'http://127.0.0.1:' + str(server.server_port),
], stdout=log, stderr=subprocess.STDOUT, env=emu_env)
try:
    started = time.monotonic()
    run(adb[:-2] + ['start-server'])
    while time.monotonic() - started < args.boot_timeout:
        if emulator.poll() is not None:
            raise RuntimeError('Emulator exited; inspect emulator.log')
        try:
            if shell('getprop', 'sys.boot_completed') == '1':
                break
        except subprocess.SubprocessError:
            pass
        print('Waiting for Android boot: ' + str(int(time.monotonic() - started)) + 's', flush=True)
        time.sleep(10)
    else:
        raise TimeoutError('Software emulator did not boot within budget')
    shell('wm', 'size', '720x1280')
    shell('wm', 'density', '320')
    for setting in ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']:
        shell('settings', 'put', 'global', setting, '0')
    shell('input', 'keyevent', 'KEYCODE_WAKEUP')
    shell('wm', 'dismiss-keyguard')
    print('Android ready; installing test APK.', flush=True)
    subprocess.run(adb + ['uninstall', package], capture_output=True, timeout=90)
    if args.baseline_apk:
        run(adb + ['install', '-r', args.baseline_apk])
        launch()
        wait_text('MARKET ONLINE')
        baseline = screenshot('before-home')
        checks.append({'check': 'baseline first viewport', 'price_visible': '$0.005' in baseline, 'theme_visible': 'BERT THEME STUDIO' in baseline})
        run(adb + ['uninstall', package])
    print('Installing candidate.', flush=True)
    run(adb + ['install', '-r', args.apk])
    prefs('bert_theme', {'active_theme': 'woofhub-night'})
    launch()
    wait_text('MARKET AT A GLANCE')
    print('Capturing Home.', flush=True)
    home = screenshot('after-home')
    check('BERT home and all destinations visible', all(x in home for x in ['WELCOME TO BERT', 'Market', 'Holdings', 'Studio']))
    tap('Market')
    wait_text('$0.005')
    market = screenshot('after-market-collecting')
    check('Market shows price before scrolling', '$0.005' in market)
    check('Fresh quote has no false online label', 'MARKET ONLINE' not in market and 'Delayed' not in market)
    tap('Holdings')
    wait_text('BERT amount')
    fields = [n for n in tree()[0].iter('node') if n.attrib.get('class') == 'android.widget.EditText']
    tap_node(fields[0])
    shell('input', 'text', '250000')
    shell('input', 'keyevent', 'KEYCODE_BACK')
    fields = [n for n in tree()[0].iter('node') if n.attrib.get('class') == 'android.widget.EditText']
    tap_node(fields[1])
    shell('input', 'text', '1000')
    shell('input', 'keyevent', 'KEYCODE_BACK')
    tap('Save holdings')
    wait_text('$1,250.00')
    holdings = screenshot('after-holdings')
    check('Known position value and gain match independent fixture', '$1,250.00' in holdings and '$250.00' in holdings and '+25.00%' in holdings)
    shell('am', 'force-stop', package)
    launch()
    tap('Holdings')
    check('Position survives restart', '$1,250.00' in screenshot('after-holdings-restart'))
    QuoteServer.online = False
    resume()
    wait_text('Delayed')
    delayed = screenshot('after-holdings-delayed')
    check('Failed refresh preserves value and marks delayed', 'Delayed' in delayed and '$1,250.00' in delayed)
    shell('am', 'force-stop', package)
    now = int(time.time() * 1000)
    prefs('bert_price_history', {'samples': json.dumps([{'t': now - i * 15 * 60_000, 'p': p} for i, p in [(5, .0045), (4, .0048), (3, .0046), (2, .0051), (1, .005)]])})
    launch()
    tap('Market')
    wait_text('observations')
    screenshot('after-market-history')
    tap('1H')
    screenshot('after-market-1h')
    root = tree()[0]
    selected = [n for n in root.iter('node') if n.attrib.get('selected') == 'true']
    check('One-hour chart range is selected', any('1H' in ET.tostring(n).decode() for n in selected))
    tap('Studio')
    studio = screenshot('after-studio')
    check('Widget setup is reachable', 'HOME-SCREEN WIDGETS' in studio and 'Compact' in studio)
    scroll()
    screenshot('after-studio-themes')
    # Merely previewing a different theme must not change persisted widget state.
    before_theme = shell('run-as', package, 'cat', 'shared_prefs/bert_theme.xml')
    for _ in range(4):
        if 'Mayor Purple' in tree()[1].decode():
            break
        scroll()
    tap('Mayor Purple')
    after_theme = shell('run-as', package, 'cat', 'shared_prefs/bert_theme.xml')
    check('Previewing a theme leaves the saved widget palette alone', before_theme == after_theme and 'woofhub-night' in after_theme)
    for _ in range(4):
        if 'Use palette on widgets' in tree()[1].decode():
            break
        scroll()
    tap('Use palette on widgets')
    check('Explicit palette action persists the chosen theme', 'mayor-purple' in shell('run-as', package, 'cat', 'shared_prefs/bert_theme.xml'))
    shell('settings', 'put', 'system', 'font_scale', '1.5')
    tap('Market')
    large = screenshot('after-market-large-text')
    check('Large text keeps market price and navigation available', '$0.005' in large and 'Holdings' in large and 'Studio' in large)
    shell('settings', 'put', 'system', 'font_scale', '1.0')
    shell('am', 'force-stop', package)
    prefs('bert_quote', {})
    launch()
    wait_text('Market unavailable')
    unavailable = screenshot('after-home-unavailable')
    check('Home remains usable with no quote', 'Studio' in unavailable and 'MARKET ONLINE' not in unavailable)
    tap('Holdings')
    no_quote = screenshot('after-holdings-no-quote')
    check('Missing quote never shows a zero position value', 'A quote is needed' in no_quote and '$0.00' not in no_quote)
    check('No native app crash', 'FATAL EXCEPTION' not in shell('logcat', '-d', '-s', 'AndroidRuntime:E'))
    completed = True
    print('Native UI checks complete.', flush=True)
finally:
    report = {'source_sha': run(['git', 'rev-parse', 'HEAD']).decode().strip(), 'fixture': True, 'completed': completed,
              'working_tree_dirty': bool(run(['git', 'status', '--porcelain']).strip()),
              'apk_sha256': hashlib.sha256(Path(args.apk).read_bytes()).hexdigest(),
              'device': 'Android 11 / API 30, 360x640 dp, software emulation', 'checks': checks}
    (out / 'results.json').write_text(json.dumps(report, indent=2) + '\n')
    emulator.terminate()
    try:
        emulator.wait(timeout=15)
    except subprocess.TimeoutExpired:
        emulator.kill()
    server.server_close()
    log.close()
